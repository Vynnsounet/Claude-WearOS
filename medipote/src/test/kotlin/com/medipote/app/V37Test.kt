package com.medipote.app

import com.medipote.app.data.Appointment
import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DoseChange
import com.medipote.app.data.DoseTime
import com.medipote.app.data.LabResult
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.SymptomNote
import com.medipote.app.data.Treatment
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.AppSearch
import com.medipote.app.domain.Change
import com.medipote.app.domain.EventKind
import com.medipote.app.domain.Timeline
import com.medipote.app.ui.HubCatalog
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.37: « Ma période », one line of time. */
class V37Test {
    private val today = LocalDate.of(2026, 12, 20)
    private fun d(i: Int) = today.minusDays(i.toLong())

    private fun treatment(start: LocalDate = d(60), end: LocalDate? = null, changes: List<DoseChange> = emptyList()) =
        Treatment("levo", name = "Lévothyrox", strength = "75 µg", doses = listOf(DoseTime(8 * 60)), startDate = start, endDate = end, changes = changes, createdAt = start.atStartOfDay())

    private fun night(date: LocalDate, hours: Double, blank: Boolean = false): SleepNight {
        val bed = date.minusDays(1).atTime(23, 0)
        return SleepNight(date, bed, 15, bed.plusMinutes((hours * 60).toLong() + 15), noSleep = blank)
    }

    private fun base() = AppState(onboarded = true, settings = Settings(wellbeing = WellbeingSettings(enabled = true, symptoms = listOf("headache"))))

    // ───────────── The events ─────────────
    @Test
    fun treatmentsLabsAppointmentsAndIllnessesAreOnTheLine() {
        val s = base().copy(
            treatments = listOf(treatment(d(40), changes = listOf(DoseChange(d(20), "Lévothyrox : 75 µg → 88 µg")))),
            labs = listOf(LabResult("1", "tsh", d(10), 2.2, "mUI/L")),
            appointments = listOf(Appointment("a", "Endocrinologue", "Dr Martin", dateTime = d(5).atTime(10, 0)), Appointment("f", "Futur", dateTime = today.plusDays(3).atTime(9, 0))),
            journal = (3 downTo 1).map { DayEntry(d(it), illness = "Grippe") } + DayEntry(d(30), illness = "Rhume"),
        )
        val e = Timeline.events(s, d(89), today)
        assertEquals(listOf(EventKind.ILLNESS, EventKind.APPOINTMENT, EventKind.LAB, EventKind.DOSE_CHANGE, EventKind.ILLNESS, EventKind.TREATMENT_START), e.map { it.kind }.let { k -> k })
        assertEquals(d(3), e.first().date); assertEquals("Grippe", e.first().title); assertEquals("3 jours", e.first().detail)
        assertEquals("Endocrinologue", e[1].title)
        assertEquals("TSH : 2,2 mUI/L", e[2].title)
        assertEquals("Lévothyrox : 75 µg → 88 µg", e[3].title); assertEquals("levo", e[3].ref)
        assertTrue("a future appointment is not an event yet", e.none { it.title == "Futur" })
    }

    @Test
    fun theEndOfATreatmentAndABlankNightAreThere() {
        val s = base().copy(treatments = listOf(treatment(d(80), end = d(10))), sleep = listOf(night(d(4), 0.0, blank = true)))
        val kinds = Timeline.events(s, d(89), today).map { it.kind }
        assertTrue(EventKind.TREATMENT_END in kinds && EventKind.BLANK_NIGHT in kinds && EventKind.TREATMENT_START in kinds)
    }

    @Test
    fun threeHardDaysInARowMakeAHardStretch() {
        val hard = (10 downTo 8).map { DayEntry(d(it), mental = 1) }
        val one = listOf(DayEntry(d(5), mental = 1))
        val strong = (3 downTo 1).map { SymptomNote(d(it), "headache", 5) }
        val e = Timeline.events(base().copy(journal = hard + one, symptomLog = strong), d(30), today)
        val stretches = e.filter { it.kind == EventKind.HARD_STRETCH }
        assertEquals(listOf(d(3), d(10)), stretches.map { it.date }.sortedDescending().let { it })
        assertTrue(stretches.all { it.detail == "3 jours" })
    }

    @Test
    fun eventsOutsideThePeriodAreLeftOut() {
        val s = base().copy(treatments = listOf(treatment(d(200))), labs = listOf(LabResult("1", "tsh", d(120), 2.2, "mUI/L")))
        assertTrue(Timeline.events(s, d(89), today).isEmpty())
        assertEquals("Rien de marquant noté", Timeline.summary(emptyList()))
    }

    @Test
    fun theSummaryCounts() {
        val s = base().copy(
            treatments = listOf(treatment(d(40), changes = listOf(DoseChange(d(20), "x")))),
            labs = listOf(LabResult("1", "tsh", d(10), 2.2, "mUI/L"), LabResult("2", "ferritin", d(9), 20.0, "µg/L")),
        )
        assertEquals("2 changements de traitement · 2 analyses", Timeline.summary(Timeline.events(s, d(89), today)))
    }

    // ───────────── The strip ─────────────
    @Test
    fun theStripHasOnePointPerDay() {
        val s = base().copy(sleep = listOf(night(d(1), 7.0)), journal = listOf(DayEntry(d(1), mental = 4, physical = 3)), symptomLog = listOf(SymptomNote(d(1), "headache", 3)))
        val p = Timeline.series(s, d(6), today)
        assertEquals(7, p.size)
        val y = p.first { it.date == d(1) }
        assertEquals(420, y.sleepMinutes); assertEquals(4, y.mood); assertEquals(3, y.form); assertEquals(3, y.symptom)
        assertNull(p.first { it.date == d(2) }.mood)
    }

    // ───────────── Before and after ─────────────
    private fun change(daysAgo: Int, before: Pair<Double, Int>, after: Pair<Double, Int>): AppState {
        val at = d(daysAgo)
        val nights = (1..14).map { night(at.minusDays(it.toLong()), before.first) } + (0 until 14).filter { !at.plusDays(it.toLong()).isAfter(today) }.map { night(at.plusDays(it.toLong()), after.first) }
        val journal = (1..14).map { DayEntry(at.minusDays(it.toLong()), mental = before.second) } + (0 until 14).filter { !at.plusDays(it.toLong()).isAfter(today) }.map { DayEntry(at.plusDays(it.toLong()), mental = after.second) }
        return base().copy(treatments = listOf(treatment(d(100), changes = listOf(DoseChange(at, "Lévothyrox : 75 µg → 88 µg")))), sleep = nights, journal = journal)
    }

    @Test
    fun twoWeeksBeforeAndAfterAChangeAreSideBySide() {
        val s = change(30, 6.0 to 2, 7.5 to 4)
        val event = Timeline.events(s, d(89), today).first { it.kind == EventKind.DOSE_CHANGE }
        val ba = Timeline.beforeAfter(s, event, today)!!
        assertEquals(14, ba.beforeDays); assertEquals(14, ba.afterDays)
        val sleep = ba.rows.first { it.label == "Sommeil par nuit" }
        assertEquals("6 h", sleep.before); assertEquals("7 h 30", sleep.after); assertEquals(Change.BETTER, sleep.change)
        val mood = ba.rows.first { it.label == "Moral" }
        assertEquals("2,0/5", mood.before); assertEquals("4,0/5", mood.after); assertEquals(Change.BETTER, mood.change)
    }

    @Test
    fun aWorseningIsSaidToo_andAFlatOneIsStable() {
        val worse = change(30, 8.0 to 4, 6.0 to 2)
        val e = Timeline.events(worse, d(89), today).first { it.kind == EventKind.DOSE_CHANGE }
        val rows = Timeline.beforeAfter(worse, e, today)!!.rows.associateBy { it.label }
        assertEquals(Change.WORSE, rows.getValue("Sommeil par nuit").change); assertEquals(Change.WORSE, rows.getValue("Moral").change)
        val flat = change(30, 7.0 to 3, 7.0 to 3)
        val f = Timeline.events(flat, d(89), today).first { it.kind == EventKind.DOSE_CHANGE }
        assertTrue(Timeline.beforeAfter(flat, f, today)!!.rows.all { it.change == Change.SAME })
    }

    @Test
    fun aSymptomThatFadesIsBetter() {
        val at = d(30)
        val log = (1..14).map { SymptomNote(at.minusDays(it.toLong()), "headache", 4) } + (0 until 14).map { SymptomNote(at.plusDays(it.toLong()), "headache", 1) }
        val journal = (1..14).map { DayEntry(at.minusDays(it.toLong()), mental = 3) } + (0 until 14).map { DayEntry(at.plusDays(it.toLong()), mental = 3) }
        val s = base().copy(treatments = listOf(treatment(d(100), changes = listOf(DoseChange(at, "x")))), symptomLog = log, journal = journal)
        val ba = Timeline.beforeAfter(s, Timeline.events(s, d(89), today).first { it.kind == EventKind.DOSE_CHANGE }, today)!!
        val row = ba.rows.first { it.label.contains("", ignoreCase = true) && it.before == "4,0/5" }
        assertEquals("1,0/5", row.after); assertEquals(Change.BETTER, row.change)
    }

    @Test
    fun tooRecentOrTooEmptyGivesNoComparison() {
        val recent = change(3, 6.0 to 2, 7.5 to 4)
        assertNull(Timeline.beforeAfter(recent, Timeline.events(recent, d(89), today).first { it.kind == EventKind.DOSE_CHANGE }, today))
        val empty = base().copy(treatments = listOf(treatment(d(100), changes = listOf(DoseChange(d(30), "x")))))
        assertNull(Timeline.beforeAfter(empty, Timeline.events(empty, d(89), today).first { it.kind == EventKind.DOSE_CHANGE }, today))
        val lab = base().copy(labs = listOf(LabResult("1", "tsh", d(30), 2.2, "mUI/L")))
        assertNull("only treatment changes have one", Timeline.beforeAfter(lab, Timeline.events(lab, d(89), today).first(), today))
    }

    // ───────────── Where it lives ─────────────
    @Test
    fun itIsOneCardOfTheSuiviPage_andFindable() {
        assertTrue(HubCatalog.EVERYTHING.any { it.key == "suivi:timeline" })
        assertTrue(AppSearch.CATALOG.any { it.id == "suivi:timeline" })
        assertTrue(HubCatalog.cards(AppState(profile = Profile("Alex"))).flatMap { it.second }.any { it.key == "suivi:timeline" })
    }
}
