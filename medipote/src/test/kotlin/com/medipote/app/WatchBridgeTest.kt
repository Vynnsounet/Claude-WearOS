package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.MeasureType
import com.medipote.app.data.Settings
import com.medipote.app.data.Treatment
import com.medipote.app.data.WaterSettings
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import com.medipote.app.wear.WatchBridge
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchPlanner
import com.medipote.sync.WatchReminder
import com.medipote.sync.WatchSync
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchBridgeTest {
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m)

    private val state = AppState(
        onboarded = true,
        settings = Settings(water = WaterSettings(enabled = true, goalGlasses = 8)),
        treatments = listOf(
            Treatment("met", "metformine", "Metformine", "1000 mg", doses = listOf(DoseTime(8 * 60), DoseTime(20 * 60)), startDate = day.minusDays(5), withFood = true, stock = 10.0),
            Treatment("ibu", "ibuprofene", "Ibuprofène", "400 mg", doses = emptyList(), startDate = day.minusDays(5), asNeeded = true),
        ),
    )

    @Test
    fun followUpRemindersRingHalfAnHourAfterTheDose() {
        val plan = ReminderPlanner.plan(state, at(7))
        val follow = plan.filter { it.kind == AlarmKind.FOLLOW_UP }
        assertEquals(listOf(at(8, 30), at(20, 30), at(8, 30, day.plusDays(1)), at(20, 30, day.plusDays(1))), follow.map { it.time }.sorted().take(4))
        assertEquals(at(8).toString(), follow.minBy { it.time }.ref)
    }

    @Test
    fun theSnapshotTellsTheWatchWhatToShowAndWhenToRing() {
        val snapshot = WatchBridge.snapshot(state, at(7))
        assertEquals(listOf(at(8), at(20), at(8, 0, day.plusDays(1)), at(20, 0, day.plusDays(1))), snapshot.doses.map { it.at })
        assertEquals("Metformine 1000 mg", snapshot.doses.first().name)
        assertEquals("1 comprimé", snapshot.doses.first().quantity)
        assertTrue(snapshot.doses.first().withFood)
        assertEquals(listOf("ibu"), snapshot.prn.map { it.id })
        assertTrue(snapshot.waterEnabled)
        // Dose reminders, their follow-ups and water: enough for the watch to ring on its own.
        val first = snapshot.reminders.filter { it.kind == WatchReminder.DOSE }.minBy { it.time }
        assertEquals(at(8), first.at)
        assertEquals(snapshot.doses.first().time, first.ref)
        assertTrue(snapshot.reminders.any { it.kind == WatchReminder.FOLLOW_UP && it.at == at(8, 30) && it.ref == at(8).toString() })
        assertTrue(snapshot.reminders.any { it.kind == WatchReminder.WATER })
        assertEquals(1, WatchPlanner.dosesOf(snapshot, first).size)
        // It travels as JSON.
        assertEquals(snapshot, WatchSync.decodeSnapshot(WatchSync.encode(snapshot)))
    }

    @Test
    fun actionsFromTheWatchAreRecordedAtTheTimeTheyWereDone() {
        val key = WatchBridge.snapshot(state, at(7)).doses.first().key
        // Taken at 8:05 on the watch, heard by the phone at 11:00: on time, not late.
        var s = WatchBridge.apply(state, WatchAction("1", WatchAction.TAKE, at(8, 5).toString(), doseKeys = listOf(key)), at(11))
        val log = s.doseLogs.single()
        assertEquals(at(8, 5), log.takenAt)
        assertEquals(com.medipote.app.data.TakeStatus.ON_TIME, log.status)
        assertEquals(9.0, s.treatments.first().stock!!, 0.0)
        assertTrue(WatchBridge.snapshot(s, at(11)).doses.first().taken)
        // Receiving it twice changes nothing.
        assertEquals(s, WatchBridge.apply(s, WatchAction("1", WatchAction.TAKE, at(8, 5).toString(), doseKeys = listOf(key)), at(11)))

        s = WatchBridge.apply(s, WatchAction("2", WatchAction.WATER, at(9).toString(), value = 1.0), at(11))
        assertEquals(250, com.medipote.app.domain.Water.ml(s, day))

        s = WatchBridge.apply(s, WatchAction("3", WatchAction.PRN, at(10).toString(), treatmentId = "ibu"), at(11))
        assertEquals(at(10), s.prnIntakes.single().takenAt)
        assertEquals(1, WatchBridge.apply(s, WatchAction("3", WatchAction.PRN, at(10).toString(), treatmentId = "ibu"), at(11)).prnIntakes.size)

        // The pulse measured on the watch lands in the measurements, and the pulse is turned on.
        assertFalse(MeasureType.HEART_RATE in s.settings.measures)
        s = WatchBridge.apply(s, WatchAction("4", WatchAction.HEART_RATE, at(10, 30).toString(), value = 71.6), at(11))
        val m = s.measurements.single()
        assertEquals(72.0, m.value, 0.0)
        assertEquals(MeasureType.HEART_RATE, m.type)
        assertEquals(WatchBridge.FROM_WATCH, m.notes)
        assertTrue(MeasureType.HEART_RATE in s.settings.measures)
        assertEquals(72, WatchBridge.snapshot(s, at(11)).lastHeartRate)
        // Nonsense values are ignored.
        assertEquals(s, WatchBridge.apply(s, WatchAction("5", WatchAction.HEART_RATE, at(10, 40).toString(), value = 500.0), at(11)))

        // Oxygen saturation typed on the watch: a measurement of its own, turned on in the app too.
        assertFalse(MeasureType.OXYGEN in s.settings.measures)
        s = WatchBridge.apply(s, WatchAction("6", WatchAction.OXYGEN, at(10, 45).toString(), value = 97.0), at(11))
        val o = s.measurements.first { it.type == MeasureType.OXYGEN }
        assertEquals(97.0, o.value, 0.0)
        assertEquals(WatchBridge.FROM_WATCH, o.notes)
        assertTrue(MeasureType.OXYGEN in s.settings.measures)
        assertEquals(97, WatchBridge.snapshot(s, at(11)).lastOxygen)
        assertEquals(s, WatchBridge.apply(s, WatchAction("7", WatchAction.OXYGEN, at(10, 50).toString(), value = 20.0), at(11)))

        // A nap noted on the watch ends at the gesture time and starts that many minutes earlier.
        s = WatchBridge.apply(s, WatchAction("8", WatchAction.NAP, at(15, 0).toString(), value = 30.0), at(15, 5))
        val nap = s.naps.single()
        assertEquals(at(14, 30), nap.start)
        assertEquals(30, nap.minutes)
        assertEquals(30, WatchBridge.snapshot(s, at(15, 5)).napMinutesToday)
        // Timed on the watch: started, then stopped 40 minutes later.
        s = WatchBridge.apply(s, WatchAction("9", WatchAction.NAP_START, at(16, 0).toString()), at(16, 1))
        assertEquals(at(16, 0).toString(), WatchBridge.snapshot(s, at(16, 10)).napSince)
        s = WatchBridge.apply(s, WatchAction("10", WatchAction.NAP_STOP, at(16, 40).toString()), at(16, 41))
        assertEquals(2, s.naps.size)
        assertEquals(40, s.naps.first().minutes)
        assertEquals(null, s.napSince)
        assertEquals(70, WatchBridge.snapshot(s, at(17)).napMinutesToday)
    }

    // ── Symptoms and missions on the watch ──

    private fun well(vararg symptoms: String) = state.copy(
        settings = state.settings.copy(wellbeing = com.medipote.app.data.WellbeingSettings(enabled = true, symptoms = symptoms.toList(), customMissions = listOf("Prendre un bain"))),
    )

    @Test
    fun theWatchOnlyHearsOfSymptomsAndMissionsOnceTheyAreOn() {
        val off = WatchBridge.snapshot(state, at(9))
        assertTrue(off.symptoms.isEmpty() && off.missions.isEmpty())
        assertEquals(0, off.winsToday)
        val noted = com.medipote.app.domain.Wellbeing.setIntensity(well("fatigue", "anxiety"), day, "fatigue", 4)
        val on = WatchBridge.snapshot(noted, at(9))
        assertEquals(listOf("fatigue" to 4, "anxiety" to 0), on.symptoms.map { it.id to it.intensity })
        assertEquals("Fatigue", on.symptoms.first().name)
        // A few gentle missions, the person's own first.
        assertTrue(on.missions.size in 1..3)
        assertEquals("my:Prendre un bain", on.missions.first().id)
        assertEquals(on, WatchSync.decodeSnapshot(WatchSync.encode(on)))
    }

    @Test
    fun aSymptomAndAMissionDoneOnTheWatchAreRecordedAtTheTimeTheyWereDone() {
        var s = well("fatigue")
        s = WatchBridge.apply(s, WatchAction("a", WatchAction.SYMPTOM, at(8).toString(), treatmentId = "fatigue", value = 3.0), at(9))
        assertEquals(3, com.medipote.app.domain.Wellbeing.intensityOn(s, day, "fatigue"))
        // 0 removes it; a symptom not followed is ignored.
        val cleared = WatchBridge.apply(s, WatchAction("b", WatchAction.SYMPTOM, at(8).toString(), treatmentId = "fatigue", value = 0.0), at(9))
        assertEquals(null, com.medipote.app.domain.Wellbeing.intensityOn(cleared, day, "fatigue"))
        assertEquals(s, WatchBridge.apply(s, WatchAction("c", WatchAction.SYMPTOM, at(8).toString(), treatmentId = "pain", value = 3.0), at(9)))
        // A mission pays its points once; the same one again changes nothing.
        val done = WatchBridge.apply(s, WatchAction("d", WatchAction.MISSION, at(8, 30).toString(), treatmentId = "tea"), at(9))
        assertEquals(s.game.points + com.medipote.app.domain.Wellbeing.MISSION_POINTS, done.game.points)
        assertEquals(done, WatchBridge.apply(done, WatchAction("e", WatchAction.MISSION, at(8, 40).toString(), treatmentId = "tea"), at(9)))
        // The person's own mission works too, and an unknown one is ignored.
        val own = WatchBridge.apply(s, WatchAction("f", WatchAction.MISSION, at(8, 30).toString(), treatmentId = "my:Prendre un bain"), at(9))
        assertEquals(1, com.medipote.app.domain.Wellbeing.winsOn(own, day).size)
        assertEquals(s, WatchBridge.apply(s, WatchAction("g", WatchAction.MISSION, at(8).toString(), treatmentId = "nope"), at(9)))
        // Turned off on the phone: the watch cannot turn it on.
        assertEquals(state, WatchBridge.apply(state, WatchAction("h", WatchAction.SYMPTOM, at(8).toString(), treatmentId = "fatigue", value = 3.0), at(9)))
    }
}
