package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.Treatment
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Game
import com.medipote.app.domain.Schedule
import com.medipote.app.domain.Stats
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseTimeChangeTest {

    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0, date: LocalDate = day) = date.atTime(h, m)

    private val metformine = Treatment(
        "metf", name = "Metformine", doses = listOf(DoseTime(8 * 60), DoseTime(20 * 60)),
        startDate = day.minusDays(10), createdAt = day.minusDays(10).atStartOfDay(),
    )

    private fun give(state: AppState, minute: Int, date: LocalDate, takenAt: LocalDateTime): AppState {
        val logs = state.doseLogs.associateBy { it.key }
        val item = Schedule.itemsOn(state.treatments, logs, date, takenAt).first { it.occurrence.dose.minuteOfDay == minute }
        return Game.give(state, item, takenAt)!!.state
    }

    private fun items(state: AppState, date: LocalDate, now: LocalDateTime) =
        Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, date, now)

    @Test
    fun aDoseAlreadyGivenFollowsItsNewTime() {
        var state = AppState(treatments = listOf(metformine), onboarded = true)
        state = give(state, 8 * 60, day, at(8, 5))
        val points = state.game.points
        // At 10:00, the morning dose moves from 8:00 to 9:00 (and the evening one from 20:00 to 21:00).
        val edited = metformine.copy(doses = listOf(DoseTime(9 * 60), DoseTime(21 * 60)))
        state = Schedule.applyEdit(state, edited, at(10))
        val today = items(state, day, at(10))
        assertEquals(listOf(9 * 60, 21 * 60), today.map { it.occurrence.dose.minuteOfDay })
        assertEquals(DoseState.TAKEN, today[0].state) // nothing to give again
        assertEquals(at(8, 5), today[0].log!!.takenAt)
        assertEquals(DoseState.UPCOMING, today[1].state)
        assertEquals(points, state.game.points) // no points lost or won twice
    }

    @Test
    fun historyFollowsTooSoPastDaysDontLookForgotten() {
        var state = AppState(treatments = listOf(metformine), onboarded = true)
        for (d in 1..3) {
            val date = day.minusDays(d.toLong())
            state = give(state, 8 * 60, date, date.atTime(8, 0))
            state = give(state, 20 * 60, date, date.atTime(20, 10))
        }
        val before = Stats.daily(state, at(10), 4).take(3).map { it.taken to it.missed }
        state = Schedule.applyEdit(state, metformine.copy(doses = listOf(DoseTime(7 * 60 + 30), DoseTime(19 * 60 + 30))), at(10))
        val after = Stats.daily(state, at(10), 4).take(3).map { it.taken to it.missed }
        assertEquals(before, after)
        assertTrue(after.all { it == (2 to 0) })
    }

    @Test
    fun onlyTheMovedTimeChanges() {
        // The evening dose moves; the morning one, given at 8:00, stays where it is.
        var state = AppState(treatments = listOf(metformine), onboarded = true)
        state = give(state, 8 * 60, day, at(8, 0))
        state = Schedule.applyEdit(state, metformine.copy(doses = listOf(DoseTime(8 * 60), DoseTime(22 * 60))), at(12))
        val today = items(state, day, at(12))
        assertEquals(DoseState.TAKEN, today.first { it.occurrence.dose.minuteOfDay == 8 * 60 }.state)
        assertEquals(DoseState.UPCOMING, today.first { it.occurrence.dose.minuteOfDay == 22 * 60 }.state)
        assertEquals(1, state.doseLogs.size)
    }

    @Test
    fun anAddedTimeIsOnlyOwedFromNowOn() {
        val once = metformine.copy(doses = listOf(DoseTime(8 * 60)))
        var state = AppState(treatments = listOf(once), onboarded = true)
        state = give(state, 8 * 60, day.minusDays(1), at(8, 0, day.minusDays(1)))
        state = give(state, 8 * 60, day, at(8, 0))
        // At 15:00, a noon dose and an evening dose are added.
        state = Schedule.applyEdit(state, once.copy(doses = listOf(DoseTime(8 * 60), DoseTime(12 * 60), DoseTime(20 * 60))), at(15))
        val t = state.treatments.single()
        assertNull(t.doses.first { it.minuteOfDay == 8 * 60 }.since)
        assertEquals(at(15), t.doses.first { it.minuteOfDay == 12 * 60 }.since)
        // Today: noon had already passed when it was added, so it isn't owed; 20:00 is.
        assertEquals(listOf(8 * 60, 20 * 60), items(state, day, at(15)).map { it.occurrence.dose.minuteOfDay })
        // Yesterday stays complete: the new times weren't owed then.
        assertEquals(listOf(DoseState.TAKEN), items(state, day.minusDays(1), at(15)).map { it.state })
        // From tomorrow on, all three are planned.
        assertEquals(3, Schedule.occurrencesOn(state.treatments, day.plusDays(1)).size)
    }

    @Test
    fun fewerTimesKeepTheClosestGivenDose() {
        var state = AppState(treatments = listOf(metformine), onboarded = true)
        state = give(state, 8 * 60, day, at(8, 0))
        // Twice a day becomes once a day at 9:00: the morning dose already given counts for it.
        state = Schedule.applyEdit(state, metformine.copy(doses = listOf(DoseTime(9 * 60))), at(10))
        assertEquals(DoseState.TAKEN, items(state, day, at(10)).single().state)
        assertEquals(mapOf(8 * 60 to 9 * 60), Schedule.pairDoseTimes(listOf(8 * 60, 20 * 60), listOf(9 * 60)))
        assertEquals(mapOf(8 * 60 to 9 * 60, 20 * 60 to 21 * 60), Schedule.pairDoseTimes(listOf(20 * 60, 8 * 60), listOf(21 * 60, 9 * 60)))
    }

    @Test
    fun newTreatmentsAreAddedAsBefore() {
        val state = Schedule.applyEdit(AppState(), metformine, at(10))
        assertEquals(listOf(metformine), state.treatments)
    }
}
