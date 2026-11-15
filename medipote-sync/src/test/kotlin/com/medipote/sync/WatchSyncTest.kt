package com.medipote.sync

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSyncTest {
    private val day = java.time.LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    private val snapshot = WatchSnapshot(
        generatedAt = at(7).toString(), petName = "Pilou", streak = 6,
        doses = listOf(
            WatchDose("a", "Lévothyroxine 75 µg", "1 comprimé", at(7, 30).toString(), taken = true),
            WatchDose("b", "Metformine 1000 mg", "1 comprimé", at(8).toString()),
            WatchDose("c", "Amlodipine 5 mg", "1 gélule", at(12).toString()),
            WatchDose("d", "Metformine 1000 mg", "1 comprimé", at(8).plusDays(1).toString()),
        ),
        reminders = listOf(
            WatchReminder(WatchReminder.DOSE, at(8).toString(), at(8).toString()),
            WatchReminder(WatchReminder.FOLLOW_UP, at(8, 30).toString(), at(8).toString()),
            WatchReminder(WatchReminder.DOSE, at(12).toString(), at(12).toString()),
            WatchReminder(WatchReminder.WATER, at(11).toString()),
            WatchReminder(WatchReminder.WATER, at(9).plusDays(1).toString()),
        ),
        waterEnabled = true, waterDate = day.toString(), glasses = 7, goal = 8,
        prn = listOf(WatchPrn("ibu", "Ibuprofène 400 mg", "1 comprimé")),
    )

    @Test
    fun snapshotsAndActionsSurviveTheTrip() {
        assertEquals(snapshot, WatchSync.decodeSnapshot(WatchSync.encode(snapshot)))
        val action = WatchAction("id", WatchAction.TAKE, at(8, 2).toString(), doseKeys = listOf("b"))
        assertEquals(action, WatchSync.decodeAction(WatchSync.encode(action)))
        assertNull(WatchSync.decodeSnapshot("not json"))
        // A newer phone app may send fields this watch doesn't know.
        assertEquals("Pilou", WatchSync.decodeSnapshot("""{"generatedAt":"2026-11-12T07:00","petName":"Pilou","future":1}""")?.petName)
    }

    @Test
    fun theWatchRingsOnlyForWhatIsStillToDo() {
        assertEquals(5, WatchPlanner.pending(snapshot, at(7)).size)
        // The 8:00 dose taken on the watch: its reminder and its follow-up go away at once.
        val taken = WatchPlanner.apply(snapshot, WatchAction("1", WatchAction.TAKE, at(7, 50).toString(), doseKeys = listOf("b")))
        assertEquals(listOf(at(11), at(12), at(9).plusDays(1)), WatchPlanner.pending(taken, at(7, 51)).map { it.at })
        assertFalse(WatchPlanner.relevant(taken, snapshot.reminders[0], at(8)))
        assertTrue(WatchPlanner.relevant(snapshot, snapshot.reminders[0], at(8)))
        assertEquals("C'est l'heure 💊" to "Metformine 1000 mg (1 comprimé)", WatchPlanner.message(snapshot, snapshot.reminders[0], at(8)))
        // Water: no reminder once the goal is reached, and the count starts again the next day.
        val full = WatchPlanner.apply(snapshot, WatchAction("2", WatchAction.WATER, at(10).toString(), value = 1.0))
        assertEquals(8, WatchPlanner.glasses(full, day))
        assertFalse(WatchPlanner.relevant(full, snapshot.reminders[3], at(11)))
        assertEquals(0, WatchPlanner.glasses(full, day.plusDays(1)))
        assertTrue(WatchPlanner.relevant(full, snapshot.reminders[4], at(9).plusDays(1)))
        val next = WatchPlanner.apply(full, WatchAction("3", WatchAction.WATER, at(9).plusDays(1).toString(), value = 1.0))
        assertEquals(1, WatchPlanner.glasses(next, day.plusDays(1)))
    }

    @Test
    fun dosesHaveAStateAndANextOne() {
        assertEquals(WatchDoseState.TAKEN, WatchPlanner.state(snapshot.doses[0], at(9)))
        assertEquals(WatchDoseState.UPCOMING, WatchPlanner.state(snapshot.doses[1], at(7)))
        assertEquals(WatchDoseState.DUE, WatchPlanner.state(snapshot.doses[1], at(7, 45)))
        assertEquals(WatchDoseState.LATE, WatchPlanner.state(snapshot.doses[1], at(9)))
        assertEquals("b", WatchPlanner.next(snapshot, at(9))?.key)
        assertEquals(listOf("a", "b", "c"), WatchPlanner.dosesOn(snapshot, day).map { it.key })
        // The next day, yesterday's doses still waiting stay in sight.
        assertEquals(listOf("b", "c", "d"), WatchPlanner.dosesOn(snapshot, day.plusDays(1)).map { it.key })
        assertEquals("il y a 5 min", WatchPlanner.ago(at(7), at(7, 5)))
        assertEquals("il y a 3 h", WatchPlanner.ago(at(7), at(10, 20)))
    }

    @Test
    fun heartRateWaitsForSteadyReadings() {
        assertNull(HeartRate.steady(listOf(70f, 71f, 72f)))
        assertNull(HeartRate.steady(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)))
        assertEquals(72, HeartRate.steady(listOf(0f, 110f, 70f, 71f, 72f, 72f, 73f, 72f, 71f, 74f)))
        // Still moving around: not yet.
        assertNull(HeartRate.steady(listOf(60f, 65f, 70f, 75f, 80f, 85f, 90f, 95f)))
    }
}
