package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.Awakening
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.SleepSettings
import com.medipote.app.domain.Reports
import com.medipote.app.domain.Rules
import com.medipote.app.domain.Sleep
import com.medipote.app.domain.Tone
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTest {

    private val day = LocalDate.of(2026, 11, 12) // Thursday

    /** A night ending on [date]: bed at [bed] (minutes, evening before when ≥ noon), up at [wake]. */
    private fun night(
        date: LocalDate, bed: Int = 23 * 60, wake: Int = 7 * 60, latency: Int = 15,
        awakenings: List<Pair<Int, Int>> = emptyList(), reason: String = "", tags: List<String> = emptyList(), quality: Int? = null,
    ): SleepNight {
        val w = date.atTime(wake / 60, wake % 60)
        return SleepNight(
            date, Sleep.bedtimeFor(date, bed), latency, w,
            awakenings.map { (at, minutes) -> Awakening(Sleep.inNight(w, at), minutes, reason) }, quality, tags = tags,
        )
    }

    @Test
    fun durationsCountAwakeningsAndFallingAsleep() {
        val n = night(day, bed = 23 * 60 + 30, wake = 7 * 60, latency = 20, awakenings = listOf(3 * 60 to 30, 5 * 60 to 10))
        assertEquals(day.minusDays(1).atTime(23, 30), n.bedtime)
        assertEquals(day.atTime(3, 0), n.awakenings[0].at)
        assertEquals(450, n.inBedMinutes) // 7 h 30
        assertEquals(40, n.awakeMinutes)
        assertEquals(390, n.asleepMinutes) // 6 h 30
        assertEquals(390f / 450, n.efficiency, 0.001f)
        // Asleep 23:50 → 3:00, awake 30 min, asleep 3:30 → 5:00, awake 10 min, asleep 5:10 → 7:00.
        val segments = Sleep.segments(n)
        assertEquals(listOf(false, true, false, true, false, true), segments.map { it.asleep })
        assertEquals(190, Sleep.longestStretch(n))
        assertEquals(n.inBedMinutes, segments.sumOf { it.minutes })
        assertTrue(Sleep.problems(n).isEmpty())
    }

    @Test
    fun afterMidnightBedtimesAndBadInput() {
        val late = night(day, bed = 60 + 30, wake = 9 * 60)
        assertEquals(day.atTime(1, 30), late.bedtime)
        assertEquals(450, late.inBedMinutes)
        // An awakening at 23:45 belongs to the evening before.
        val w = day.atTime(7, 0)
        assertEquals(day.minusDays(1).atTime(23, 45), Sleep.inNight(w, 23 * 60 + 45))

        assertTrue(Sleep.problems(night(day, bed = 8 * 60, wake = 7 * 60)).single().contains("après l'heure du coucher"))
        assertTrue(Sleep.problems(night(day, bed = 23 * 60, wake = 7 * 60, awakenings = listOf(22 * 60 to 10))).single().contains("en dehors de la nuit"))
        assertTrue(Sleep.problems(night(day, latency = 120, awakenings = listOf(2 * 60 to 180, 5 * 60 to 180))).isNotEmpty())
        assertEquals("7 h 05", Sleep.format(425))
        assertEquals("45 min", Sleep.format(45))
        assertEquals("8 h", Sleep.format(480))
    }

    @Test
    fun savingReplacesTheNightAndRewardsOncePerMorning() {
        val now = day.atTime(8, 30)
        var (state, bonus) = Sleep.save(AppState(), night(day), now)
        assertEquals(Rules.SLEEP_BONUS, bonus)
        assertEquals(Rules.SLEEP_BONUS, state.game.points)
        val edited = Sleep.save(state, night(day, wake = 8 * 60), now)
        state = edited.first
        assertEquals(0, edited.second)
        assertEquals(1, state.sleep.size)
        assertEquals(8, state.sleep.single().wakeTime.hour)
        // Logging an older night: no points (only the night of the day counts).
        val older = Sleep.save(state, night(day.minusDays(3)), now)
        assertEquals(0, older.second)
        assertEquals(listOf(day, day.minusDays(3)), older.first.sleep.map { it.date })
        assertEquals(listOf(day.minusDays(3)), Sleep.delete(older.first, day).sleep.map { it.date })
    }

    @Test
    fun newNightUsesTheUsualTimes() {
        val state = AppState(sleep = (1..5).map { night(day.minusDays(it.toLong()), bed = 23 * 60 + 40, wake = 6 * 60 + 50, latency = 30) })
        val n = Sleep.newNight(state, day)
        assertEquals(day.minusDays(1).atTime(23, 40), n.bedtime)
        assertEquals(day.atTime(6, 50), n.wakeTime)
        assertEquals(30, n.latencyMinutes)
        val fresh = Sleep.newNight(AppState(), day)
        assertEquals(day.minusDays(1).atTime(23, 0), fresh.bedtime)
        assertEquals(day.atTime(7, 0), fresh.wakeTime)
    }

    @Test
    fun reportAveragesAndFindsPatterns() {
        // Weeknights: ~6 h with a 3 am awakening for the toilet; weekends: long nights without waking.
        val nights = (0 until 14).map { i ->
            val d = day.minusDays(i.toLong())
            val weekend = d.dayOfWeek.value >= 6
            if (weekend) night(d, bed = 23 * 60, wake = 9 * 60, latency = 10)
            else night(d, bed = if (i % 2 == 0) 23 * 60 + 30 else 60, wake = 6 * 60 + 30, latency = 20,
                awakenings = listOf(3 * 60 to 30), reason = "Toilettes", tags = if (i % 2 == 0) listOf("Écrans avant de dormir") else emptyList())
        }
        val journal = nights.map { n -> DayEntry(n.date, physical = if (n.asleepMinutes >= 7 * 60) 4 else 2, mental = 3) }
        val state = AppState(sleep = nights.sortedByDescending { it.date }, journal = journal, settings = Settings(sleep = SleepSettings(goalMinutes = 8 * 60)))
        val r = Sleep.report(state, day, 14)
        assertEquals(14, r.count)
        assertEquals(4, r.calmNights) // 2 weekends
        assertEquals(10, r.awakeningCount)
        assertEquals(10, r.awakeningHours[3])
        assertEquals(3 * 60, r.typicalAwakening)
        assertEquals("Toilettes" to 10, r.reasons.single())
        assertEquals(9 * 60 + 50, r.weekendAverage) // 10 h in bed − 10 min
        assertTrue(r.weekdayAverage!! < 6 * 60)
        assertTrue(r.goalNights == 4)
        // Bedtimes alternate 23:30 / 1:00 on weeknights, 23:00 on weekends: the mean is around midnight.
        val bed = r.typicalBedtime!!
        assertTrue("$bed", bed >= 23 * 60 + 30 || bed <= 30)
        val texts = r.insights.map { it.text }
        assertTrue(texts.toString(), r.insights.first().tone == Tone.WARN)
        assertTrue(texts.toString(), texts.any { it.contains("de moins que ton objectif") })
        assertTrue(texts.toString(), texts.any { it.contains("le plus souvent vers 3 h 00") })
        assertTrue(texts.toString(), texts.any { it.contains("toilettes") })
        assertTrue(texts.toString(), texts.any { it.contains("Le week-end, tu dors") })
        assertTrue(texts.toString(), texts.any { it.contains("ta forme est notée") })
    }

    @Test
    fun calmRegularNightsAreCelebrated() {
        val nights = (0 until 7).map { night(day.minusDays(it.toLong()), bed = 22 * 60 + 30, wake = 6 * 60 + 45, latency = 10) }
        val r = Sleep.report(AppState(sleep = nights), day, 7)
        assertEquals(8 * 60 + 5, r.averageAsleep)
        assertEquals(0, r.bedtimeSpread)
        val good = r.insights.filter { it.tone == Tone.GOOD }.map { it.text }
        assertTrue(good.toString(), good.any { it.contains("objectif de 8 h atteint") })
        assertTrue(good.toString(), good.any { it.contains("d'une traite") })
        assertTrue(good.toString(), good.any { it.contains("Horaires réguliers") })
        assertNull(r.previousAverageAsleep)
        assertEquals(-60, Reports.circularDiff(23 * 60, 0))
        assertEquals(30, Reports.circularDiff(30, 0))
    }

    @Test
    fun morningReminderOnlyWhileTheNightIsNotLogged() {
        val settings = Settings(sleep = SleepSettings(reminder = true, reminderMinute = 9 * 60))
        val state = AppState(onboarded = true, settings = settings)
        val plan = ReminderPlanner.plan(state, day.atTime(7, 0)).filter { it.kind == AlarmKind.SLEEP }
        assertEquals(listOf(day.atTime(9, 0), day.plusDays(1).atTime(9, 0)), plan.map { it.time })
        val logged = state.copy(sleep = listOf(night(day)))
        assertEquals(listOf(day.plusDays(1).atTime(9, 0)), ReminderPlanner.plan(logged, day.atTime(7, 0)).filter { it.kind == AlarmKind.SLEEP }.map { it.time })
        assertFalse(ReminderPlanner.plan(state.copy(settings = Settings()), day.atTime(7, 0)).any { it.kind == AlarmKind.SLEEP })
    }

    @Test
    fun restfulAndPleasantNights() {
        // Calm nights feel restful, nights with awakenings don't.
        val nights = (0 until 8).map { i ->
            val woken = i % 2 == 1
            night(day.minusDays(i.toLong()), awakenings = if (woken) listOf(3 * 60 to 30) else emptyList())
                .copy(quality = if (woken) 2 else 4, rested = if (woken) 2 else 5, pleasant = 3)
        }
        val r = Sleep.report(AppState(sleep = nights), day, 8)
        assertEquals(3.5f, r.averageRested!!, 0.001f)
        assertEquals(3f, r.averagePleasant!!, 0.001f)
        assertEquals(3f, r.averageQuality!!, 0.001f)
        assertTrue(r.insights.toString(), r.insights.any { it.text == "Les nuits sans réveil sont plus reposantes : 5,0/5 contre 2,0/5." })
        // Mostly unrestful nights: a gentle warning.
        val tired = nights.map { it.copy(rested = 2) }
        assertTrue(Sleep.report(AppState(sleep = tired), day, 8).insights.any { it.tone == Tone.WARN && it.text.startsWith("Tes nuits sont peu reposantes") })
        assertEquals(5, Sleep.RESTED_LABELS.size)
        assertEquals(5, Sleep.PLEASANT_LABELS.size)
    }
}
