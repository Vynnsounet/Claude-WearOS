package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.Nap
import com.medipote.app.data.NapFeeling
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.SleepSettings
import com.medipote.app.domain.NapStop
import com.medipote.app.domain.Naps
import com.medipote.app.domain.Sleep
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NapsTest {
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m)

    private fun night(date: LocalDate, hours: Int = 7) = SleepNight(
        date = date, bedtime = date.minusDays(1).atTime(23, 0), latencyMinutes = 0, wakeTime = date.minusDays(1).atTime(23, 0).plusHours(hours.toLong()),
    )

    @Test
    fun aNapIsNotANight() {
        val state = AppState(sleep = listOf(night(day), night(day.minusDays(1))))
        val withNap = Naps.save(state, Nap("n1", at(14, 10), 30, NapFeeling.FRESH))
        // The night's figures don't move: same report with and without the nap.
        val without = Sleep.report(state, day, 7)
        val with = Sleep.report(withNap, day, 7)
        assertEquals(without.averageAsleep, with.averageAsleep)
        assertEquals(without.averageEfficiency, with.averageEfficiency)
        assertEquals(without.count, with.count)
        assertEquals(without.averageAwakenings, with.averageAwakenings)
        // The nap shows apart, and in the day's total.
        assertEquals(1, with.naps.naps.size)
        assertEquals(30, with.naps.totalMinutes)
        assertEquals(30, with.naps.averageMinutes)
        assertEquals(1, with.naps.daysWithNap)
        assertEquals(7 * 60 + 15, with.averageDailyTotal)
        assertNull(without.averageDailyTotal)
    }

    @Test
    fun theGoalCountsNapsOnlyWhenAsked() {
        val goal = 8 * 60
        val base = AppState(sleep = listOf(night(day, 7)), settings = Settings(sleep = SleepSettings(goalMinutes = goal)))
        val withNap = Naps.save(base, Nap("n1", at(14), 90))
        assertFalse(Naps.reachesGoal(base, base.sleep.first()))
        assertTrue(Naps.reachesGoal(withNap, withNap.sleep.first()))
        assertEquals(1, Sleep.report(withNap, day, 7).goalNights)
        val off = withNap.copy(settings = withNap.settings.copy(sleep = withNap.settings.sleep.copy(napsInGoal = false)))
        assertFalse(Naps.reachesGoal(off, off.sleep.first()))
        assertEquals(0, Sleep.report(off, day, 7).goalNights)
    }

    @Test
    fun theTimerNotesTheNapWhenYouWakeUp() {
        var state = AppState()
        state = Naps.start(state, at(14, 10))
        assertEquals(at(14, 10), Naps.running(state, at(14, 20)))
        // Pressing "I fall asleep" again changes nothing.
        assertEquals(state, Naps.start(state, at(14, 15)))
        val stop = Naps.stop(state, at(14, 40), "id") as NapStop.Saved
        assertEquals(30, stop.nap.minutes)
        assertEquals(at(14, 10), stop.nap.start)
        assertNull(stop.state.napSince)
        assertEquals(listOf("id"), stop.state.naps.map { it.id })
    }

    @Test
    fun aTimerPressedTwiceInARowOrForgottenIsNotNotedAsIs() {
        val started = Naps.start(AppState(), at(14, 10))
        assertTrue(Naps.stop(started, at(14, 10).plusSeconds(20), "x") is NapStop.TooShort)
        assertTrue((Naps.stop(started, at(14, 10).plusSeconds(20), "x") as NapStop.TooShort).state.naps.isEmpty())
        // Forgotten overnight: the screen asks for the duration instead of noting 14 hours.
        val long = Naps.stop(started, at(4, 0, day.plusDays(1)), "x")
        assertTrue(long is NapStop.TooLong)
        assertEquals(at(14, 10), (long as NapStop.TooLong).start)
        assertNull(long.state.napSince)
        assertTrue(long.state.naps.isEmpty())
        // A timer forgotten for half a day is no longer shown as running.
        assertNull(Naps.running(started, at(14, 10).plusHours(13)))
        assertNotNull(Naps.running(started, at(14, 10).plusHours(3)))
        assertNull(Naps.running(Naps.cancel(started), at(14, 20)))
    }

    @Test
    fun napsAreListedPerDayAndSurviveTheBackupFormat() {
        var state = AppState()
        state = Naps.save(state, Nap("a", at(14), 20))
        state = Naps.save(state, Nap("b", at(16), 25))
        state = Naps.save(state, Nap("c", at(15, 0, day.minusDays(1)), 40))
        assertEquals(listOf("a", "b"), Naps.on(state, day).map { it.id })
        assertEquals(45, Naps.minutesOn(state, day))
        // Newest first; editing keeps one copy.
        state = Naps.save(state, Nap("a", at(14), 35))
        assertEquals(listOf("b", "a", "c"), state.naps.map { it.id })
        assertEquals(35, state.naps.first { it.id == "a" }.minutes)
        assertEquals(listOf("b", "c"), Naps.delete(state, "a").naps.map { it.id })
        // Saved and read back with the app's own format; an old save without naps still loads.
        val json = com.medipote.app.data.AppRepository.JSON
        val text = json.encodeToString(AppState.serializer(), state.copy(napSince = at(10)))
        val back = json.decodeFromString(AppState.serializer(), text)
        assertEquals(state.naps, back.naps)
        assertEquals(at(10), back.napSince)
        val old = json.decodeFromString(AppState.serializer(), """{"onboarded":true}""")
        assertTrue(old.naps.isEmpty())
        assertNull(old.napSince)
        assertTrue(old.settings.sleep.napsInGoal)
    }

    @Test
    fun aNapNotedFromTheEndTimeStartsEarlier() {
        val nap = Naps.endingAt("w", at(15, 7).plusSeconds(30), 20)
        assertEquals(at(14, 47), nap.start)
        assertEquals(at(15, 7), nap.end)
    }
}
