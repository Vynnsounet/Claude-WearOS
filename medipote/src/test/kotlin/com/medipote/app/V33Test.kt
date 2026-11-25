package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Intercourse
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.SleepSettings
import com.medipote.app.domain.DoctorReports
import com.medipote.app.domain.ReportBlock
import com.medipote.app.domain.ReportPart
import com.medipote.app.domain.Sleep
import com.medipote.app.domain.SleepFactors
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.33: « Ma soirée » and what goes with the nights. */
class V33Test {
    private val today = LocalDate.of(2026, 11, 25)

    /** A night ending on [date]: [hours] of sleep after 15 minutes to fall asleep. */
    private fun night(date: LocalDate, hours: Double = 8.0, quality: Int? = null, tags: List<String> = emptyList()): SleepNight {
        val bed = date.minusDays(1).atTime(23, 0)
        return SleepNight(date, bed, 15, bed.plusMinutes((hours * 60).toLong() + 15), quality = quality, tags = tags)
    }

    private fun state(nights: List<SleepNight>, evenings: Map<String, List<String>> = emptyMap(), sensitive: Boolean = false, extra: AppState.() -> AppState = { this }) =
        AppState(sleep = nights, evenings = evenings, settings = Settings(sleep = SleepSettings(sensitiveFactors = sensitive))).extra()

    /** Twelve nights, the first [n] of them with [tag] noted the evening before. */
    private fun twelve(tag: String, n: Int, with: Double, without: Double): AppState {
        val nights = (0 until 12).map { i -> night(today.minusDays(i.toLong()), hours = if (i < n) with else without) }
        val evenings = (0 until n).associate { i -> today.minusDays(i.toLong() + 1).toString() to listOf(tag) }
        return state(nights, evenings)
    }

    // ───────────── The list ─────────────
    @Test
    fun chosenFromExclusiveGroupsOnlyOneStays() {
        var s = emptyList<String>()
        s = SleepFactors.toggle(s, "amount_lots"); s = SleepFactors.toggle(s, "meal_late")
        assertEquals(listOf("amount_lots", "meal_late"), s)
        s = SleepFactors.toggle(s, "amount_none")
        assertEquals(listOf("meal_late", "amount_none"), s)
        s = SleepFactors.toggle(s, "meal_early")
        assertEquals(listOf("amount_none", "meal_early"), s)
        s = SleepFactors.toggle(s, "meal_fast"); s = SleepFactors.toggle(s, "meal_healthy")
        assertEquals(listOf("amount_none", "meal_early", "meal_healthy"), s)
        assertEquals(listOf("amount_none", "meal_early"), SleepFactors.toggle(s, "meal_healthy"))
        // two non exclusive ones go together
        assertEquals(listOf("coffee", "alcohol"), SleepFactors.toggle(SleepFactors.toggle(emptyList(), "coffee"), "alcohol"))
    }

    @Test
    fun aFactorTakesAPrecision_andFreeText() {
        var s = SleepFactors.toggle(emptyList(), "coffee")
        s = SleepFactors.choose(s, "coffee", "late")
        assertEquals(listOf("coffee=late"), s)
        assertEquals("Café (après 16 h)", SleepFactors.label(s.single()))
        assertEquals(listOf("coffee"), SleepFactors.choose(s, "coffee", "late")) // again: back to plain
        assertEquals(listOf("coffee=morning"), SleepFactors.choose(s, "coffee", "morning"))
        val drug = SleepFactors.describe(listOf("drug"), "drug", "  tilleul  ")
        assertEquals(listOf("drug=tilleul"), drug)
        assertEquals("Autre substance : tilleul", SleepFactors.label(drug.single()))
        assertEquals(listOf("drug"), SleepFactors.describe(drug, "drug", " "))
        // toggling a chosen factor off removes it with its precision
        assertTrue(SleepFactors.toggle(s, "coffee").isEmpty())
    }

    @Test
    fun theListIsShortWhatsHiddenIsLeftOut_andPrivateThingsAreHiddenByDefault() {
        val default = SleepFactors.visible(SleepSettings())
        assertTrue(default.none { it.group.sensitive })
        assertTrue(default.any { it.id == "coffee" })
        assertTrue(SleepFactors.visible(SleepSettings(sensitiveFactors = true)).any { it.id == "cannabis" && it.group.sensitive })
        assertTrue(SleepFactors.visible(SleepSettings(hiddenFactors = listOf("coffee"))).none { it.id == "coffee" })
    }

    @Test
    fun theOldTagsBecomeFactors_andOwnWordsStay() {
        assertEquals("coffee", SleepFactors.normalize("Caféine"))
        assertEquals("meal_late", SleepFactors.normalize("Repas tardif"))
        assertEquals("Mon truc", SleepFactors.normalize("Mon truc"))
        assertEquals("✏️", SleepFactors.emoji("Mon truc"))
        assertEquals("Mon truc", SleepFactors.label("Mon truc"))
        assertTrue(SleepFactors.isSensitive("drug=tilleul")); assertFalse(SleepFactors.isSensitive("coffee"))
    }

    // ───────────── The evening, kept with the night ─────────────
    @Test
    fun theNightReadsTheEveningBefore() {
        val n = night(today)
        val s = SleepFactors.setEvening(state(listOf(n)), today.minusDays(1), listOf("Caféine", "party", "party"))
        assertEquals(listOf("coffee", "party"), SleepFactors.noted(s, n))
        assertTrue(SleepFactors.setEvening(s, today.minusDays(1), emptyList()).evenings.isEmpty())
        // old notes on the night itself are read too, without doubles
        val old = night(today, tags = listOf("Alcool", "coffee"))
        assertEquals(listOf("coffee", "alcohol"), SleepFactors.noted(s.copy(evenings = emptyMap()), old).sorted().reversed())
    }

    @Test
    fun anEveningOlderThanAYearIsLetGo() {
        var s = SleepFactors.setEvening(AppState(), today.minusDays(500), listOf("coffee"))
        s = SleepFactors.setEvening(s, today, listOf("tea"))
        assertEquals(setOf(today.toString()), s.evenings.keys)
    }

    @Test
    fun sexualActivityOfTheCycleLogCountsWithoutTypingItTwice() {
        val n = night(today)
        val s = state(listOf(n)).copy(journal = listOf(DayEntry(today.minusDays(1), intercourse = Intercourse.PROTECTED)))
        assertEquals(listOf("sex"), SleepFactors.noted(s, n))
        val typed = SleepFactors.setEvening(s, today.minusDays(1), listOf("sex"))
        assertEquals(listOf("sex"), SleepFactors.noted(typed, n))
    }

    @Test
    fun oldSavesLoad() {
        val s = AppRepository.JSON.decodeFromString(AppState.serializer(), """{"onboarded":true}""")
        assertTrue(s.evenings.isEmpty()); assertFalse(s.settings.sleep.sensitiveFactors); assertTrue(s.settings.sleep.hiddenFactors.isEmpty())
        val back = AppRepository.JSON.decodeFromString(AppState.serializer(), AppRepository.JSON.encodeToString(AppState.serializer(), state(emptyList(), mapOf("2026-11-24" to listOf("coffee=late")))))
        assertEquals(listOf("coffee=late"), back.evenings["2026-11-24"])
    }

    // ───────────── What goes with the nights ─────────────
    @Test
    fun aFactorThatGoesWithShorterNightsIsSaid() {
        val s = twelve("coffee", 6, with = 6.5, without = 8.0)
        val e = SleepFactors.effects(s, s.sleep).single()
        assertEquals("coffee", e.key); assertEquals(6, e.withNights); assertEquals(6, e.withoutNights)
        assertEquals(-90, e.asleepDiff); assertFalse(e.good)
        assertTrue(e.sentence, e.sentence.startsWith("Tu dors 1 h 30 de moins") && e.sentence.contains("(6 nuits avec, 6 sans)"))
    }

    @Test
    fun aGoodFactorIsGood() {
        val e = SleepFactors.effects(twelve("sport", 5, with = 8.5, without = 7.5), twelve("sport", 5, 8.5, 7.5).sleep).single()
        assertTrue(e.good); assertTrue(e.sentence, e.sentence.contains("de plus"))
    }

    @Test
    fun tooFewNightsOrTooSmallADifferenceSaysNothing() {
        val few = twelve("coffee", 4, 6.0, 8.0)
        assertTrue(SleepFactors.effects(few, few.sleep).isEmpty())
        val almostAll = twelve("coffee", 8, 6.0, 8.0) // only 4 nights without
        assertTrue(SleepFactors.effects(almostAll, almostAll.sleep).isEmpty())
        val same = twelve("coffee", 6, 8.0, 7.9)
        assertTrue(SleepFactors.effects(same, same.sleep).isEmpty())
    }

    @Test
    fun aPrecisionIsComparedOnItsOwnToo() {
        val nights = (0 until 12).map { i -> night(today.minusDays(i.toLong()), hours = if (i < 6) 6.5 else 8.0) }
        // coffee on every other night, but late only before the short nights
        val evenings = (0 until 12).associate { i -> today.minusDays(i.toLong() + 1).toString() to listOf(if (i < 6) "coffee=late" else "coffee=morning") }
        val keys = SleepFactors.effects(state(nights, evenings), nights).map { it.key }
        assertTrue(keys.toString(), "coffee=late" in keys && "coffee=morning" in keys)
        assertEquals("the plain factor is left out when a precision covers the same nights", 2, keys.size)
        assertTrue("every night had coffee: nothing to compare without it", "coffee" !in keys)
        assertEquals("Café (après 16 h)", SleepFactors.effects(state(nights, evenings), nights).first { it.key == "coffee=late" }.label)
    }

    @Test
    fun whatTheAppKnowsAlreadyCounts() {
        // low mood the day before the short nights
        val nights = (0 until 12).map { i -> night(today.minusDays(i.toLong()), hours = if (i < 6) 6.0 else 8.0) }
        val journal = (0 until 6).map { i -> DayEntry(today.minusDays(i.toLong() + 1), physical = 2, mental = 1) }
        val e = SleepFactors.effects(state(nights).copy(journal = journal), nights).single()
        assertEquals("auto_lowmood", e.key); assertTrue(e.auto); assertEquals("🌧️", e.emoji)
        assertEquals("Journée au moral ou au corps difficile", e.label)
    }

    @Test
    fun privateFactorsStayHiddenUntilTurnedOn() {
        val nights = (0 until 12).map { i -> night(today.minusDays(i.toLong()), hours = if (i < 6) 6.0 else 8.0) }
        val evenings = (0 until 6).associate { i -> today.minusDays(i.toLong() + 1).toString() to listOf("cannabis") }
        val off = state(nights, evenings)
        assertTrue(SleepFactors.effects(off, nights).single().sensitive)
        assertTrue(SleepFactors.shown(off, nights).isEmpty())
        assertEquals(1, SleepFactors.shown(state(nights, evenings, sensitive = true), nights).size)
        // and nothing about them in the insights or the tags of the report while off
        assertTrue(Sleep.report(off, today, 30).tags.isEmpty())
        assertTrue(Sleep.report(off, today, 30).insights.none { it.text.contains("cannabis", ignoreCase = true) })
    }

    @Test
    fun theSleepInsightsMentionTheBestAndTheWorst() {
        val s = twelve("coffee", 6, 6.5, 8.0)
        val insights = Sleep.report(s, today, 30).insights.map { it.text }
        assertTrue(insights.toString(), insights.any { it.startsWith("Les nuits avec « café » : tu dors 1 h 30 de moins") })
    }

    @Test
    fun theReportCountsTheTagsOfTheEvenings() {
        val s = twelve("coffee", 6, 6.5, 8.0)
        assertEquals(listOf("Café" to 6), Sleep.report(s, today, 30).tags)
    }

    // ───────────── The doctor's report ─────────────
    private fun sections(s: AppState, parts: Set<ReportPart>) = DoctorReports.build(s, today.atTime(10, 0), 30, parts).sections

    @Test
    fun theReportGivesTheNotableEffects_butNeverThePrivateOnes() {
        val s = twelve("coffee", 6, 6.5, 8.0)
        val text = sections(s, setOf(ReportPart.SLEEP)).flatMap { it.blocks }.filterIsInstance<ReportBlock.Paragraph>().joinToString("\n") { it.text }
        assertTrue(text, text.contains("• Café : Tu dors 1 h 30 de moins"))
        assertTrue(text, text.contains("pas des causes"))
        val nights = s.sleep
        val priv = state(nights, (0 until 6).associate { i -> today.minusDays(i.toLong() + 1).toString() to listOf("cannabis") }, sensitive = true)
        val standard = sections(priv, setOf(ReportPart.SLEEP)).flatMap { it.blocks }.filterIsInstance<ReportBlock.Paragraph>().joinToString { it.text }
        assertFalse(standard.contains("annabis"))
    }

    @Test
    fun thePrivateFactorsAreAnOptionOfTheReport() {
        val nights = (0 until 12).map { night(today.minusDays(it.toLong())) }
        val evenings = (0 until 4).associate { i -> today.minusDays(i.toLong() + 1).toString() to listOf("cannabis") }
        assertFalse(ReportPart.SLEEP_PRIVATE in DoctorReports.availableParts(state(nights, evenings, sensitive = false)))
        val on = state(nights, evenings, sensitive = true)
        assertTrue(ReportPart.SLEEP_PRIVATE in DoctorReports.availableParts(on))
        val section = sections(on, setOf(ReportPart.SLEEP_PRIVATE)).single()
        val facts = section.blocks.filterIsInstance<ReportBlock.Facts>().single().items
        assertEquals(listOf("Cannabis" to "4 soirs sur 12 nuits notées"), facts)
        assertTrue(sections(on, setOf(ReportPart.SLEEP)).none { it.title == ReportPart.SLEEP_PRIVATE.label })
    }
}
