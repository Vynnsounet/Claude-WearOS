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
        assertEquals(1, s.entryFor(day).waterGlasses)

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
    }
}
