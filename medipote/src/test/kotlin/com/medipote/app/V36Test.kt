package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.SleepSettings
import com.medipote.app.data.SymptomNote
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.DayFactors
import com.medipote.app.domain.Understand
import com.medipote.app.domain.Wellbeing
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.36: what goes with better or worse days: mood, form, energy, symptoms. */
class V36Test {
    private val today = LocalDate.of(2026, 12, 14)
    private fun d(i: Int) = today.minusDays(i.toLong())

    private fun base(sensitive: Boolean = false) = AppState(
        onboarded = true,
        settings = Settings(wellbeing = WellbeingSettings(enabled = true, symptoms = listOf("headache", "fatigue")), sleep = SleepSettings(sensitiveFactors = sensitive)),
    )

    /** Twenty days; the first ten (the "bad" ones) have [badValue] of the headache and little water, the others [okValue] and enough. */
    private fun headacheDays(badValue: Int, okValue: Int = 0): AppState {
        val log = (0 until 20).flatMap { i ->
            listOfNotNull(
                SymptomNote(d(i), "fatigue", 1), // the person noted something every day
                SymptomNote(d(i), "headache", if (i < 10) badValue else okValue).takeIf { it.intensity > 0 },
            )
        }
        val journal = (0 until 20).map { i -> DayEntry(d(i), waterGlasses = if (i < 10) 2 else 8) }
        return base().copy(symptomLog = log, journal = journal)
    }

    private fun night(date: LocalDate, hours: Double) = SleepNight(date, date.minusDays(1).atTime(23, 0), 15, date.minusDays(1).atTime(23, 0).plusMinutes((hours * 60).toLong() + 15))

    @Test
    fun aSymptomStrongerOnDaysOfLittleWaterIsSaid() {
        val effects = DayFactors.effects(headacheDays(4), today)
        val e = effects.first { it.outcome == "symptom:headache" && it.key == "water_low" }
        assertEquals(10, e.withDays); assertEquals(10, e.withoutDays)
        assertEquals(4.0, e.diff, 0.01); assertTrue(e.worse)
        assertEquals("« ${Wellbeing.name("headache")} » : plus intense les jours de peu d'eau (+4,0/5 · 10 jours avec, 10 sans).", e.sentence)
        assertEquals("💧", e.emoji)
    }

    @Test
    fun theSymptomPageGivesIt() {
        val factors = Understand.factors(headacheDays(4), "headache", today)
        val f = factors.first { it.title.startsWith("D'après tes notes") }
        assertEquals("D'après tes notes : les jours de peu d'eau", f.title)
        assertTrue(f.evidence, f.evidence.contains("10 jours avec, 10 sans"))
        assertTrue("another symptom is not concerned", Understand.factors(headacheDays(4), "fatigue", today).none { it.title.startsWith("D'après tes notes") })
    }

    @Test
    fun aLowMoodAfterShortNightsIsSaid_butNotAFlatOne() {
        val nights = (0 until 20).map { i -> night(d(i), if (i < 10) 5.0 else 8.0) }
        val low = base().copy(sleep = nights, journal = (0 until 20).map { i -> DayEntry(d(i), mental = if (i < 10) 2 else 4) })
        val e = DayFactors.effects(low, today).first { it.outcome == "mood" && it.key == "night_short" }
        assertFalse(e.worse.not()); assertEquals(-2.0, e.diff, 0.01)
        assertTrue(e.sentence, e.sentence.startsWith("« Moral » : plus bas après une nuit courte (-2,0/5"))
        val flat = low.copy(journal = (0 until 20).map { i -> DayEntry(d(i), mental = 3) })
        assertTrue(DayFactors.effects(flat, today).none { it.outcome == "mood" })
        val almost = low.copy(journal = (0 until 20).map { i -> DayEntry(d(i), mental = if (i < 10) 3 else 4) })
        assertTrue("one point of five is worth saying", DayFactors.effects(almost, today).any { it.outcome == "mood" })
        val tiny = low.copy(journal = (0 until 20).map { i -> DayEntry(d(i), mental = if (i % 2 == 0) 3 else 3) })
        assertTrue(DayFactors.effects(tiny, today).isEmpty())
    }

    @Test
    fun tooFewDaysSayNothing() {
        val few = (0 until 20).map { i -> night(d(i), if (i < 4) 5.0 else 8.0) }
        val s = base().copy(sleep = few, journal = (0 until 20).map { i -> DayEntry(d(i), mental = if (i < 4) 1 else 5) })
        assertTrue(DayFactors.effects(s, today).none { it.key == "night_short" })
    }

    @Test
    fun energyIsKeptDayByDay() {
        var s = base()
        s = Wellbeing.setEnergy(s, d(1), 1); s = Wellbeing.setEnergy(s, today, 3)
        assertEquals(1, Wellbeing.energyOn(s, d(1))); assertEquals(3, Wellbeing.energyOn(s, today))
        assertEquals(3, s.energy!!.level)
        assertTrue(Wellbeing.setEnergy(Wellbeing.setEnergy(AppState(), today.minusDays(500), 2), today, 1).energyLog.size == 1)
        val nights = (0 until 20).map { i -> night(d(i), if (i < 10) 5.0 else 8.0) }
        var withEnergy = base().copy(sleep = nights)
        (0 until 20).forEach { i -> withEnergy = Wellbeing.setEnergy(withEnergy, d(i), if (i < 10) 1 else 3) }
        val e = DayFactors.effects(withEnergy, today).first { it.outcome == "energy" && it.key == "night_short" }
        assertEquals(-2.0, e.diff, 0.01); assertEquals(3, e.scaleMax)
        assertEquals("« Énergie » : plus bas après une nuit courte (-2,0/3 · 10 jours avec, 10 sans).", e.sentence)
    }

    @Test
    fun aFactorOfTheEveningCountsForTheDayAfter() {
        val nights = (0 until 20).map { i -> night(d(i), 8.0) }
        val evenings = (0 until 20).filter { it < 8 }.associate { i -> d(i + 1).toString() to listOf("coffee=late") } +
            (8 until 20).associate { i -> d(i + 1).toString() to listOf("tea") }
        val s = base().copy(sleep = nights, evenings = evenings, journal = (0 until 20).map { i -> DayEntry(d(i), mental = if (i < 8) 2 else 4) })
        val e = DayFactors.effects(s, today).first { it.outcome == "mood" && it.key == "ev:coffee=late" }
        assertEquals("après un soir avec « Café (après 16 h) »", e.whenText)
        assertTrue(e.worse)
        // « café » alone covers the same days: only the precise one is kept
        assertTrue(DayFactors.effects(s, today).none { it.key == "ev:coffee" })
    }

    @Test
    fun privateFactorsAreHiddenUntilTurnedOn() {
        val nights = (0 until 20).map { i -> night(d(i), 8.0) }
        val evenings = (0 until 8).associate { i -> d(i + 1).toString() to listOf("cannabis") } + (8 until 20).associate { i -> d(i + 1).toString() to listOf("tea") }
        val journal = (0 until 20).map { i -> DayEntry(d(i), mental = if (i < 8) 2 else 4) }
        val off = base().copy(sleep = nights, evenings = evenings, journal = journal)
        assertTrue(DayFactors.effects(off, today).any { it.key == "ev:cannabis" && it.sensitive })
        assertTrue(DayFactors.shown(off, today).none { it.key == "ev:cannabis" })
        assertTrue(DayFactors.shown(off.copy(settings = off.settings.copy(sleep = off.settings.sleep.copy(sensitiveFactors = true))), today).any { it.key == "ev:cannabis" })
    }

    @Test
    fun theStrongestComeFirst_andNothingIsSaidAboutNothing() {
        val e = DayFactors.effects(headacheDays(4), today)
        assertEquals(e.sortedByDescending { it.strength }, e)
        assertTrue(DayFactors.effects(base(), today).isEmpty())
    }

    @Test
    fun oldSavesLoad() {
        val s = AppRepository.JSON.decodeFromString(AppState.serializer(), """{"onboarded":true}""")
        assertTrue(s.energyLog.isEmpty())
        assertNull(Wellbeing.energyOn(s, today))
        assertNotNull(DayFactors.effects(s, today))
    }
}
