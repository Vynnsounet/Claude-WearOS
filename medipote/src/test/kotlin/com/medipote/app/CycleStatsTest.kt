package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.CycleInfo
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Flow
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.CyclePhase
import com.medipote.app.domain.CycleStats
import com.medipote.app.domain.Tone
import com.medipote.app.domain.Wellbeing
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleStatsTest {
    private val today = LocalDate.of(2026, 11, 12)
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    private fun state(starts: List<LocalDate>, journal: List<DayEntry> = emptyList(), menstruating: Boolean = true) = AppState(
        profile = Profile("Sam", menstruating = menstruating, cycle = CycleInfo(starts, 28, 5)),
        journal = journal,
    )

    /** Five days of flow from each start. */
    private fun flows(starts: List<LocalDate>, days: Int = 5) = starts.flatMap { s -> (0 until days).map { DayEntry(s.plusDays(it.toLong()), flow = if (it < 2) Flow.MEDIUM else Flow.LIGHT) } }

    @Test
    fun lengthsRegularityAndPeriodDays() {
        val starts = listOf(d(6, 20), d(7, 18), d(8, 17), d(9, 13), d(10, 18))
        val r = CycleStats.report(state(starts, flows(starts)), today)!!
        assertEquals(listOf(28, 30, 27, 35), r.lengths)
        assertEquals(30.0, r.averageLength!!, 0.001)
        assertEquals(27, r.shortest)
        assertEquals(35, r.longest)
        assertEquals(8, r.spread)
        assertEquals(5.0, r.averagePeriodDays!!, 0.001)
        // The cycle in progress has no length yet.
        assertNull(r.records.last().length)
        // A spread over a week is said plainly, without alarm.
        assertTrue(r.insights.any { it.text.contains("varient de 8 jours") && it.tone == Tone.INFO })
        assertTrue(r.insights.none { it.tone == Tone.WARN })
    }

    @Test
    fun regularCyclesAreSaidWithGoodNews() {
        val starts = listOf(d(7, 4), d(8, 1), d(8, 29), d(9, 26), d(10, 24))
        val r = CycleStats.report(state(starts), today)!!
        assertEquals(listOf(28, 28, 28, 28), r.lengths)
        assertTrue(r.insights.any { it.tone == Tone.GOOD && it.text.contains("réguliers") })
        assertNull(r.averagePeriodDays) // no flow noted: nothing invented
    }

    @Test
    fun veryShortOrLongCyclesSuggestTalkingToADoctorOnlyWhenTheyRepeat() {
        val long = listOf(d(3, 1), d(4, 20), d(6, 10), d(7, 30), d(9, 18)) // 50-day cycles
        val r = CycleStats.report(state(long), today)!!
        assertTrue(r.insights.any { it.tone == Tone.WARN && it.text.contains("médecin") })
        // Only two finished cycles: too early to say anything.
        val few = CycleStats.report(state(listOf(d(9, 1), d(10, 30), d(11, 5))), today)!!
        assertTrue(few.insights.isEmpty())
    }

    @Test
    fun phasesAreEstimatedFromTheStarts() {
        val info = CycleInfo(listOf(d(10, 1), d(10, 29)), 28, 5)
        assertEquals(CyclePhase.MENSTRUAL, CycleStats.phaseOn(info, d(10, 1)))
        assertEquals(CyclePhase.MENSTRUAL, CycleStats.phaseOn(info, d(10, 5)))
        assertEquals(CyclePhase.FOLLICULAR, CycleStats.phaseOn(info, d(10, 8)))
        assertEquals(CyclePhase.OVULATION, CycleStats.phaseOn(info, d(10, 14)))
        assertEquals(CyclePhase.LUTEAL, CycleStats.phaseOn(info, d(10, 22)))
        // Before the first start, or long after the last one: no claim.
        assertNull(CycleStats.phaseOn(info, d(9, 20)))
        assertNull(CycleStats.phaseOn(info, d(12, 30)))
    }

    @Test
    fun theMoodByPhaseIsNoticedWhenItClearlyDrops() {
        val starts = listOf(d(7, 4), d(8, 1), d(8, 29), d(9, 26), d(10, 24))
        val info = CycleInfo(starts, 28, 5)
        // Moral at 2 in the luteal phase, 4 elsewhere.
        val journal = generateSequence(d(7, 4)) { it.plusDays(1) }.takeWhile { it <= today }.mapNotNull { date ->
            val phase = CycleStats.phaseOn(info, date) ?: return@mapNotNull null
            DayEntry(date, mental = if (phase == CyclePhase.LUTEAL) 2 else 4)
        }.toList()
        val r = CycleStats.report(state(starts, journal), today)!!
        val luteal = r.byPhase.first { it.phase == CyclePhase.LUTEAL }
        assertEquals(2.0, luteal.averageMood!!, 0.001)
        assertTrue(r.insights.any { it.text.contains("phase lutéale") && it.text.contains("moral") })
    }

    @Test
    fun aFollowedSymptomStrongerInAPhaseIsNoticed() {
        val starts = listOf(d(7, 4), d(8, 1), d(8, 29), d(9, 26), d(10, 24))
        val info = CycleInfo(starts, 28, 5)
        var s = state(starts).copy(settings = Settings(wellbeing = WellbeingSettings(enabled = true, symptoms = listOf("anxiety"))))
        generateSequence(d(7, 4)) { it.plusDays(1) }.takeWhile { it <= today }.forEach { date ->
            val phase = CycleStats.phaseOn(info, date) ?: return@forEach
            if (date.dayOfMonth % 2 == 0) s = Wellbeing.setIntensity(s, date, "anxiety", if (phase == CyclePhase.LUTEAL) 4 else 2)
        }
        val r = CycleStats.report(s, today)!!
        assertTrue(r.insights.any { it.text.contains("anxiété") && it.text.contains("phase lutéale") })
        // No claim when the symptom is not followed.
        val off = CycleStats.report(s.copy(settings = Settings()), today)!!
        assertTrue(off.insights.none { it.text.contains("anxiété") })
    }

    @Test
    fun commonSymptomsAndFlowAreCounted() {
        val starts = listOf(d(10, 18))
        val journal = flows(starts) + listOf(
            DayEntry(d(10, 19), cycleSymptoms = listOf("Crampes", "Fatigue")), DayEntry(d(10, 20), cycleSymptoms = listOf("Crampes")),
        ).map { e -> e.copy(flow = Flow.HEAVY) }
        val r = CycleStats.report(state(starts, journal.groupBy { it.date }.map { (_, v) -> v.last() }), today)!!
        assertEquals("Crampes" to 2, r.symptoms.first())
        assertTrue(r.flowDays.isNotEmpty())
    }

    @Test
    fun noCycleNoReportAndTheTabIsOnlyForThoseWhoHaveOne() {
        assertNull(CycleStats.report(AppState(), today))
        assertNull(CycleStats.report(state(emptyList()), today))
        assertFalse(CycleStats.enabled(state(listOf(d(10, 18)), menstruating = false)))
        assertTrue(CycleStats.enabled(state(listOf(d(10, 18)))))
        assertNotNull(CycleStats.report(state(listOf(d(10, 18))), today))
    }
}
