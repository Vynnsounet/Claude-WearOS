package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DoseTime
import com.medipote.app.data.HomeSection
import com.medipote.app.data.Look
import com.medipote.app.data.QuietHours
import com.medipote.app.data.Settings
import com.medipote.app.data.Treatment
import com.medipote.app.data.WaterSettings
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.AppointmentPrepper
import com.medipote.app.domain.DoctorReports
import com.medipote.app.domain.ReportBlock
import com.medipote.app.domain.ReportPart
import com.medipote.app.domain.Wellbeing
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 1.19 improvements: journal and symptoms together, simple home, quiet hours, appointment prep, report. */
class V19Test {
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m)
    private fun wellState(vararg symptoms: String) = AppState(
        onboarded = true,
        settings = Settings(wellbeing = WellbeingSettings(enabled = true, symptoms = symptoms.toList())),
    )

    // ── Journal and symptoms ──

    @Test
    fun aSymptomTickedInTheJournalAsksForItsStrengthUntilNoted() {
        var s = wellState("fatigue", "anxiety", "pain").copy(
            journal = listOf(DayEntry(day, physicalSymptoms = listOf("Fatigue"), mentalSymptoms = listOf("Stress"))),
        )
        // Fatigue and anxiety (stress) are in the journal, with no strength; pain is not mentioned.
        assertEquals(listOf("fatigue", "anxiety"), Wellbeing.mentionedInJournal(s, day))
        s = Wellbeing.setIntensity(s, day, "fatigue", 4)
        assertEquals(listOf("anxiety"), Wellbeing.mentionedInJournal(s, day))
        // Another day's journal says nothing about today.
        assertTrue(Wellbeing.mentionedInJournal(s, day.plusDays(1)).isEmpty())
        // A symptom not followed is never asked about.
        assertTrue(Wellbeing.mentionedInJournal(s.copy(settings = Settings()), day).isEmpty())
    }

    // ── Simple home ──

    @Test
    fun theSimpleHomeKeepsTheEssentialsAndTheCompleteOneShowsAll() {
        val simple = Look().simple()
        assertTrue(simple.isSimple)
        assertFalse(simple.isComplete)
        assertEquals(Look.ESSENTIALS, simple.visibleSections.toSet())
        assertTrue(HomeSection.COMPANION in simple.visibleSections && HomeSection.NEXT_DOSE in simple.visibleSections && HomeSection.DOSES in simple.visibleSections)
        assertFalse(HomeSection.APPOINTMENT in simple.visibleSections)
        // Nothing is lost: the order the user chose stays, and "complete" brings everything back.
        val custom = Look(homeOrder = listOf(HomeSection.WATER, HomeSection.DOSES)).simple()
        val back = custom.complete()
        assertTrue(back.isComplete)
        assertEquals(custom.fullOrder, back.fullOrder)
        assertEquals(HomeSection.entries.toSet(), back.visibleSections.toSet())
    }

    // ── Quiet hours ──

    private fun quietState(quiet: QuietHours, now: LocalDateTime): AppState {
        val dose = now.plusHours(1)
        return AppState(
            onboarded = true,
            treatments = listOf(Treatment("t", name = "A", doses = listOf(DoseTime(23 * 60 + 30)), startDate = day.minusDays(2), createdAt = now.minusDays(2))),
            settings = Settings(
                water = WaterSettings(enabled = true, startMinute = 6 * 60, endMinute = 23 * 60, intervalMinutes = 60),
                quiet = quiet,
            ),
        )
    }

    @Test
    fun quietHoursSilenceGentleRemindersButNeverADose() {
        val now = at(21, 0)
        val loud = ReminderPlanner.plan(quietState(QuietHours(), now), now)
        val calm = ReminderPlanner.plan(quietState(QuietHours(enabled = true, startHour = 22, endHour = 8), now), now)
        // Water reminders at 22:00, 23:00 and the next night until 08:00 are skipped.
        val water = calm.filter { it.kind == AlarmKind.WATER }.map { it.time }
        assertTrue(water.none { it.hour >= 22 || it.hour < 8 })
        assertTrue(loud.count { it.kind == AlarmKind.WATER } > water.size)
        // The 23:30 dose still rings, at 23:30.
        assertTrue(calm.any { it.kind == AlarmKind.DOSE && it.time == at(23, 30) })
        assertEquals(loud.filter { it.kind == AlarmKind.DOSE }, calm.filter { it.kind == AlarmKind.DOSE })
    }

    @Test
    fun quietHoursMakeOtherGentleRemindersWaitForTheMorning() {
        val q = QuietHours(enabled = true, startHour = 22, endHour = 8)
        assertTrue(q.contains(at(23, 0)))
        assertTrue(q.contains(at(3, 0)))
        assertFalse(q.contains(at(8, 0)))
        assertFalse(q.contains(at(21, 59)))
        assertEquals(at(8, 0, day.plusDays(1)), q.endAfter(at(23, 0)))
        assertEquals(at(8, 0), q.endAfter(at(3, 0)))
        // A same-evening window (not crossing midnight) works too.
        val noon = QuietHours(enabled = true, startHour = 9, endHour = 11)
        assertTrue(noon.contains(at(10, 0)))
        assertFalse(noon.contains(at(11, 0)))
        // Off: nothing is quiet.
        assertFalse(QuietHours().contains(at(3, 0)))
    }

    // ── Appointment preparation ──

    @Test
    fun theAppointmentPrepGivesTheQuestionsThenTheSummary() {
        var s = wellState("fatigue")
        (0..9).forEach { back ->
            s = Wellbeing.setIntensity(s, day.minusDays(back.toLong()), "fatigue", if (back % 2 == 0) 4 else 2)
        }
        s = s.copy(journal = (0..5).map { DayEntry(day.minusDays(it.toLong()), mental = 3, physical = 4) })
        val appointment = Appointment("a", "Endocrinologue", "Dr Martin", dateTime = at(14, 30, day.plusDays(5)), questions = listOf("Ajuster mon traitement ?", "Faire un bilan sanguin ?"))
        val prep = AppointmentPrepper.build(s, appointment, at(12))
        assertEquals("Endocrinologue · Dr Martin", prep.title)
        assertEquals(2, prep.questions.size)
        val titles = prep.sections.map { it.title }
        assertTrue("Mes symptômes" in titles)
        assertTrue("Mon moral et ma forme" in titles)
        val text = prep.asText()
        assertTrue(text.startsWith("Endocrinologue · Dr Martin"))
        assertTrue(text.contains("Mes questions :"))
        assertTrue(text.contains("- Ajuster mon traitement ?"))
        assertTrue(text.contains("Fatigue : 10 jours notés"))
        // Nothing to say: no empty sections.
        val empty = AppointmentPrepper.build(AppState(), appointment, at(12))
        assertTrue(empty.sections.isEmpty())
        // The questions survive an old save without the field.
        val old = com.medipote.app.data.AppRepository.JSON.decodeFromString<Appointment>("""{"id":"x","title":"Dentiste","dateTime":"2026-11-14T14:30:00"}""")
        assertTrue(old.questions.isEmpty())
        assertEquals(appointment, com.medipote.app.data.AppRepository.JSON.decodeFromString<Appointment>(com.medipote.app.data.AppRepository.JSON.encodeToString(appointment)))
    }

    // ── The doctor's report ──

    @Test
    fun theDoctorReportCanIncludeTheSymptomsFollowed() {
        var s = wellState("fatigue", "anxiety")
        (0..9).forEach { back -> s = Wellbeing.setIntensity(s, day.minusDays(back.toLong()), "fatigue", 4) }
        s = Wellbeing.setIntensity(s, day, "anxiety", 2)
        assertTrue(ReportPart.SYMPTOMS in DoctorReports.availableParts(s))
        assertFalse(ReportPart.SYMPTOMS in DoctorReports.availableParts(wellState("fatigue")))
        val report = DoctorReports.build(s, at(12), 30, setOf(ReportPart.SYMPTOMS))
        val section = report.sections.single()
        assertEquals("Symptômes suivis", section.title)
        val table = section.blocks.filterIsInstance<ReportBlock.Table>().single()
        assertEquals(listOf("Fatigue", "10/30", "4,0/5", "10"), table.rows.first().take(4))
        assertTrue(section.blocks.filterIsInstance<ReportBlock.Paragraph>().any { it.text.contains("pas un diagnostic") })
    }
}
