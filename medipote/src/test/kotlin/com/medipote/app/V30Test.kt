package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import com.medipote.app.data.DocCategory
import com.medipote.app.data.DoseTime
import com.medipote.app.data.HealthDocument
import com.medipote.app.data.LabResult
import com.medipote.app.data.MyCondition
import com.medipote.app.data.Profile
import com.medipote.app.data.RegularCheckup
import com.medipote.app.data.Settings
import com.medipote.app.data.Sex
import com.medipote.app.data.SupportSettings
import com.medipote.app.data.TrustedContact
import com.medipote.app.data.TravelPlan
import com.medipote.app.data.Treatment
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.AppSearch
import com.medipote.app.domain.Checkups
import com.medipote.app.domain.DoctorReports
import com.medipote.app.domain.Emergency
import com.medipote.app.domain.HomeRules
import com.medipote.app.domain.Labs
import com.medipote.app.domain.Pharmacy
import com.medipote.app.domain.ReportBlock
import com.medipote.app.domain.ReportPart
import com.medipote.app.domain.Schedule
import com.medipote.app.domain.Travel
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import com.medipote.app.ui.HubCatalog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.30: analyses, travel, emergency card, after an appointment, regular check-ups, pharmacy list. */
class V30Test {
    private val day = LocalDate.of(2026, 11, 19)
    private val now = day.atTime(10, 0)
    private val levo = Treatment("levo", name = "Lévothyrox", strength = "75 µg", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(200), createdAt = day.minusDays(200).atStartOfDay())

    // ───────────── Pharmacy ─────────────
    @Test
    fun thePharmacyListHoldsWhatRunsLowAndWhatToRenew() {
        val low = levo.copy(stock = 3.0, lowStockDays = 7)
        val ordo = HealthDocument("d", "Ordonnance thyroïde", DocCategory.PRESCRIPTION, day.minusDays(80), "o.jpg", "image/jpeg", validUntil = day.plusDays(5))
        val s = AppState(treatments = listOf(low, levo.copy(id = "b", name = "Vit D", stock = 90.0)), documents = listOf(ordo))
        val items = Pharmacy.list(s, day)
        assertEquals(2, items.size)
        assertTrue(items[0].text, items[0].text.startsWith("Lévothyrox 75 µg"))
        assertTrue(items[1].text, items[1].text.contains("Ordonnance à renouveler : Ordonnance thyroïde"))
        val text = Pharmacy.shareText(items)
        assertTrue(text.startsWith("À la pharmacie :"))
        assertEquals(3, text.lines().size)
        assertTrue(Pharmacy.list(AppState(treatments = listOf(levo)), day).isEmpty())
    }

    // ───────────── Regular check-ups ─────────────
    private val dentist = RegularCheckup("c1", "Dentiste", 12, lastDone = day.minusMonths(11).minusDays(10))

    @Test
    fun aCheckupKnowsWhenItIsDue() {
        assertEquals(day.plusDays(20), Checkups.due(dentist.copy(lastDone = day.minusMonths(12).plusDays(20))))
        assertEquals(Checkups.State.NEVER, Checkups.state(dentist.copy(lastDone = null), day))
        assertEquals(Checkups.State.OK, Checkups.state(dentist.copy(lastDone = day.minusMonths(3)), day))
        assertEquals(Checkups.State.SOON, Checkups.state(dentist, day))
        assertEquals(Checkups.State.LATE, Checkups.state(dentist.copy(lastDone = day.minusMonths(14)), day))
        assertTrue(Checkups.label(dentist.copy(lastDone = day.minusMonths(14)), day).startsWith("À faire depuis"))
        assertTrue(Checkups.label(dentist, day).startsWith("Dans"))
        assertTrue(Checkups.label(dentist.copy(lastDone = null), day).contains("Pas encore fait"))
    }

    @Test
    fun theMostPressingComesFirst_andHomeOnlySpeaksOfWhatIsDue() {
        val late = RegularCheckup("l", "Prise de sang", 12, day.minusMonths(15))
        val ok = RegularCheckup("o", "Ophtalmologue", 24, day.minusMonths(2))
        val never = RegularCheckup("n", "Dermatologue", 12, null)
        val list = Checkups.sorted(listOf(ok, never, dentist, late), day)
        assertEquals(listOf("l", "c1", "o", "n"), list.map { it.id })
        val pending = Checkups.pending(AppState(checkups = listOf(ok, never, dentist, late)), day)
        assertEquals(listOf("l", "c1"), pending.map { it.id })
        assertTrue(Checkups.pending(AppState(checkups = listOf(late.copy(remind = false))), day).isEmpty())
    }

    @Test
    fun doneIsOneTap_addingIsDeduplicated() {
        assertEquals(day, Checkups.done(listOf(dentist), "c1", day).single().lastDone)
        var list = Checkups.add(emptyList(), "  Dentiste ", 6, null, now)
        list = Checkups.add(list, "dentiste", 12, null, now)
        assertEquals(1, list.size)
        assertEquals(6, list.single().everyMonths)
        assertTrue(Checkups.add(list, "   ", 12, null, now) == list)
        assertEquals(120, Checkups.add(emptyList(), "Rappel", 999, null, now).single().everyMonths)
    }

    @Test
    fun aCheckupRemindsOnItsDay_softly() {
        val s = AppState(onboarded = true, checkups = listOf(dentist.copy(lastDone = day.minusMonths(12).plusDays(3))))
        val alarm = ReminderPlanner.plan(s, now).single { it.kind == AlarmKind.CHECKUP }
        assertEquals(day.plusDays(3).atTime(10, 0), alarm.time)
        assertTrue(ReminderPlanner.plan(s.copy(checkups = s.checkups.map { it.copy(remind = false) }), now).none { it.kind == AlarmKind.CHECKUP })
        // A pause silences it, like the other soft reminders.
        val paused = s.copy(settings = Settings(pause = com.medipote.app.data.ReminderPause(day.plusDays(10).atTime(8, 0))))
        assertTrue(ReminderPlanner.plan(paused, now).none { it.kind == AlarmKind.CHECKUP && it.time.isBefore(day.plusDays(10).atTime(8, 0)) })
    }

    // ───────────── After the appointment ─────────────
    @Test
    fun theHomeAsksAboutAnAppointmentOfTheLastDays_once() {
        val a = Appointment("a", "Endocrinologue", dateTime = day.minusDays(1).atTime(14, 0))
        assertEquals("a", HomeRules.followUpDue(AppState(appointments = listOf(a)), now)?.id)
        assertNull(HomeRules.followUpDue(AppState(appointments = listOf(a.copy(afterNotes = "Dose à 88"))), now))
        assertNull(HomeRules.followUpDue(AppState(appointments = listOf(a.copy(attended = true))), now))
        assertNull(HomeRules.followUpDue(AppState(appointments = listOf(a.copy(dateTime = day.minusDays(5).atTime(9, 0)))), now))
        assertNull(HomeRules.followUpDue(AppState(appointments = listOf(a.copy(dateTime = day.plusDays(1).atTime(9, 0)))), now))
    }

    // ───────────── Emergency card ─────────────
    @Test
    fun theEmergencyCardHoldsWhatTheSecoursNeed() {
        val s = AppState(
            profile = Profile("Camille", age = 34, sex = Sex.FEMALE, medicalHistory = listOf("Hashimoto"), allergies = listOf("Pénicilline"), bloodGroup = "O+"),
            treatments = listOf(levo, levo.copy(id = "p", name = "Spasfon", strength = "", asNeeded = true, doses = emptyList())),
            settings = Settings(
                support = SupportSettings(contacts = listOf(TrustedContact("Marie", "0601020304")), professionalName = "Dr Martin", professionalPhone = "0102030405"),
                wellbeing = WellbeingSettings(enabled = true, conditions = listOf(MyCondition("Hashimoto", "hashimoto"), MyCondition("Migraines"))),
            ),
        )
        val c = Emergency.card(s, day)
        assertEquals("34 ans, femme", c.ageSex)
        assertEquals("O+", c.bloodGroup)
        assertEquals(listOf("Pénicilline"), c.allergies)
        assertEquals(listOf("Hashimoto", "Migraines"), c.conditions)
        assertTrue(c.treatments.first().startsWith("Lévothyrox 75 µg — 8 h 00"))
        assertTrue(c.treatments.any { it.contains("Spasfon (si besoin)") })
        assertEquals("Marie", c.contacts.single().name)
        assertEquals("Dr Martin", c.doctor?.name)
        val text = Emergency.shareText(c)
        assertTrue(text.contains("Allergies : Pénicilline") && text.contains("Groupe sanguin : O+") && text.contains("Marie 0601020304"))
        assertFalse(c.isEmpty)
        assertTrue(Emergency.card(AppState(profile = Profile("Alex")), day).isEmpty)
    }

    @Test
    fun theEmergencyCardOnTheLockScreenIsOffByDefault() {
        assertFalse(Settings().emergencyOnLock)
    }

    // ───────────── Analyses ─────────────
    private fun lab(id: String, marker: String, daysAgo: Int, value: Double, low: Double? = null, high: Double? = null) =
        LabResult(id, marker, day.minusDays(daysAgo.toLong()), value, Labs.unit(marker).ifEmpty { "u" }, low, high)

    @Test
    fun aValueIsPlacedAgainstTheRange_andTheReportRangeWins() {
        assertEquals(Labs.Level.NORMAL, Labs.level(lab("a", "tsh", 0, 2.1), null))
        assertEquals(Labs.Level.HIGH, Labs.level(lab("a", "tsh", 0, 6.5), null))
        assertEquals(Labs.Level.LOW, Labs.level(lab("a", "ferritin", 0, 9.0), null))
        // The range printed on the report prevails.
        assertEquals(Labs.Level.NORMAL, Labs.level(lab("a", "tsh", 0, 6.5, low = 0.3, high = 7.0), null))
        // The hemoglobin range follows the sex.
        assertEquals(Labs.Level.LOW, Labs.level(lab("a", "hb", 0, 12.5), Sex.MALE))
        assertEquals(Labs.Level.NORMAL, Labs.level(lab("a", "hb", 0, 12.5), Sex.FEMALE))
        // An analysis of the person's own, without a range, says so.
        assertEquals(Labs.Level.UNKNOWN, Labs.level(lab("a", "Zinc", 0, 90.0), null))
        assertEquals("Repères non renseignés", Labs.levelText(Labs.Level.UNKNOWN))
    }

    @Test
    fun theValuesAreWrittenInFrench() {
        assertEquals("2,1", Labs.format(2.1, "tsh").let { it.trimEnd('0') })
        assertEquals("4", Labs.format(4.0, "tsh").trimEnd(',', '0').ifEmpty { "0" }.let { if (it == "4") "4" else it })
        assertEquals("120", Labs.format(120.0, "ferritin"))
    }

    @Test
    fun theTrendSaysWhereItWent() {
        val up = Labs.trend(listOf(lab("a", "tsh", 60, 1.8), lab("b", "tsh", 0, 2.6)))!!
        assertTrue(up, up.startsWith("en hausse depuis le") && up.contains("→"))
        assertTrue(Labs.trend(listOf(lab("a", "tsh", 60, 2.6), lab("b", "tsh", 0, 1.8)))!!.startsWith("en baisse"))
        assertTrue(Labs.trend(listOf(lab("a", "tsh", 60, 2.0), lab("b", "tsh", 0, 2.01)))!!.startsWith("stable"))
        assertNull(Labs.trend(listOf(lab("a", "tsh", 0, 2.0))))
    }

    @Test
    fun theAnalysesAreListedInTheUsualOrder_ownOnesAfter() {
        val s = AppState(labs = listOf(lab("1", "Zinc", 5, 80.0), lab("2", "ferritin", 5, 40.0), lab("3", "tsh", 5, 2.0), lab("4", "tsh", 90, 3.0)))
        assertEquals(listOf("tsh", "ferritin", "Zinc"), Labs.tracked(s))
        assertEquals(listOf(90, 5), Labs.series(s, "tsh").map { (day.toEpochDay() - it.date.toEpochDay()).toInt() })
        assertEquals(2.0, Labs.latest(s, "tsh")!!.value, 0.0)
        val saved = Labs.save(s, lab("1", "Zinc", 5, 95.0))
        assertEquals(4, saved.labs.size)
        assertEquals(95.0, saved.labs.first { it.id == "1" }.value, 0.0)
        assertEquals(3, Labs.delete(saved, "1").labs.size)
        assertEquals("Hémoglobine", Labs.name("hb"))
        assertEquals("Zinc", Labs.name("Zinc"))
    }

    @Test
    fun aChangeOfDoseIsKept_andShownAgainstTheCurve() {
        val s = AppState(treatments = listOf(levo), onboarded = true)
        val after = Schedule.applyEdit(s, levo.copy(strength = "88 µg"), now)
        val t = after.treatments.single()
        assertEquals(1, t.changes.size)
        assertEquals(day, t.changes.single().date)
        assertTrue(t.changes.single().text, t.changes.single().text.contains("75 µg → 88 µg"))
        // A time that moved is kept too; nothing else is.
        val moved = Schedule.applyEdit(after, t.copy(doses = listOf(DoseTime(9 * 60))), now.plusDays(10))
        assertTrue(moved.treatments.single().changes.any { it.text.contains("horaires") })
        val same = Schedule.applyEdit(moved, moved.treatments.single().copy(notes = "x"), now.plusDays(11))
        assertEquals(moved.treatments.single().changes.size, same.treatments.single().changes.size)
        assertEquals(1, Labs.doseChanges(after, day.minusDays(1), day.plusDays(1)).size)
        assertTrue(Labs.doseChanges(after, day.minusDays(30), day.minusDays(10)).isEmpty())
    }

    @Test
    fun theReportHoldsTheAnalyses() {
        val s = AppState(
            profile = Profile("Alex"), labs = listOf(lab("1", "tsh", 90, 3.1), lab("2", "tsh", 5, 2.2)),
            treatments = listOf(levo.copy(changes = listOf(com.medipote.app.data.DoseChange(day.minusDays(40), "Lévothyrox : 75 µg → 88 µg")))),
        )
        assertTrue(ReportPart.LABS in DoctorReports.availableParts(s))
        assertFalse(ReportPart.LABS in DoctorReports.availableParts(AppState()))
        val section = DoctorReports.build(s, now, 30, setOf(ReportPart.LABS)).sections.single()
        val table = section.blocks.filterIsInstance<ReportBlock.Table>().single()
        assertEquals("TSH", table.rows.single()[0])
        assertTrue(table.rows.single()[4], table.rows.single()[4].contains("en baisse"))
        assertTrue(section.blocks.any { it is ReportBlock.Paragraph && it.text.contains("88 µg") })
    }

    // ───────────── Travel ─────────────
    @Test
    fun theDosesComeBackToTheLocalClockADay() {
        val plan = TravelPlan(arrival = day, offsetHours = 6, stepHours = 1)
        assertEquals(0, Travel.shiftMinutes(plan, day.minusDays(1)))
        assertEquals(360, Travel.shiftMinutes(plan, day))
        assertEquals(300, Travel.shiftMinutes(plan, day.plusDays(1)))
        assertEquals(60, Travel.shiftMinutes(plan, day.plusDays(5)))
        assertEquals(0, Travel.shiftMinutes(plan, day.plusDays(6)))
        assertEquals(0, Travel.shiftMinutes(plan, day.plusDays(30)))
        // Two hours a day: three days.
        assertEquals(listOf(360, 240, 120, 0), (0..3).map { Travel.shiftMinutes(plan.copy(stepHours = 2), day.plusDays(it.toLong())) })
    }

    @Test
    fun aTripWestIsTheSameBackwards() {
        val plan = TravelPlan(day, offsetHours = -5, stepHours = 1)
        assertEquals(listOf(-300, -240, -180, -120, -60, 0), (0..5).map { Travel.shiftMinutes(plan, day.plusDays(it.toLong())) })
    }

    @Test
    fun theWayBackStartsWhereTheBodyIs() {
        // Six hours ahead, three days there (the body moved three hours), then home.
        val plan = TravelPlan(day, 6, 1, returnDate = day.plusDays(3))
        assertEquals(240, Travel.shiftMinutes(plan, day.plusDays(2)))
        assertEquals(-180, Travel.shiftMinutes(plan, day.plusDays(3)))
        assertEquals(-120, Travel.shiftMinutes(plan, day.plusDays(4)))
        assertEquals(0, Travel.shiftMinutes(plan, day.plusDays(6)))
        // A long trip, fully adapted: back home it is the whole difference, then it comes back.
        val long = TravelPlan(day, 6, 2, returnDate = day.plusDays(20))
        assertEquals(0, Travel.shiftMinutes(long, day.plusDays(19)))
        assertEquals(-360, Travel.shiftMinutes(long, day.plusDays(20)))
        assertEquals(-240, Travel.shiftMinutes(long, day.plusDays(21)))
    }

    @Test
    fun noDifferenceNoShift() {
        assertEquals(0, Travel.shiftMinutes(TravelPlan(day, 0), day))
        assertFalse(Travel.active(TravelPlan(day, 0), day))
        assertTrue(Travel.active(TravelPlan(day.plusDays(1), 6), day))
        assertFalse(Travel.active(TravelPlan(day.minusDays(60), 6), day))
        assertNull(Travel.todayText(TravelPlan(day, 6), day.plusDays(30)))
        assertTrue(Travel.todayText(TravelPlan(day, 6), day)!!.contains("+6 h"))
    }

    @Test
    fun thePlanMovesTheDoses_andCancellingBringsThemBack() {
        val s = AppState(onboarded = true, treatments = listOf(levo))
        val traveling = Travel.sync(s.copy(travel = TravelPlan(day, 6, 1)))
        val t = traveling.treatments.single()
        assertEquals(360, t.shiftOn(day))
        assertEquals(300, t.shiftOn(day.plusDays(1)))
        // The dose of that day is at home time shifted: 8:00 + 6 h = 14:00 on the local clock; its key is the same.
        val occurrence = Schedule.occurrencesOn(traveling.treatments, day).single()
        assertEquals(day.atTime(14, 0), occurrence.scheduled)
        assertEquals(Schedule.occurrencesOn(s.treatments, day).single().key, occurrence.key)
        assertEquals(day.plusDays(1).atTime(13, 0), Schedule.occurrencesOn(traveling.treatments, day.plusDays(1)).single().scheduled)
        // The alarms follow.
        assertTrue(ReminderPlanner.plan(traveling.copy(settings = Settings()), day.atTime(6, 0)).any { it.kind == AlarmKind.DOSE && it.time == day.atTime(14, 0) })
        // Cancelled: back to 8:00.
        val back = Travel.sync(traveling.copy(travel = null))
        assertTrue(back.treatments.single().shifts.isEmpty())
        assertEquals(day.atTime(8, 0), Schedule.occurrencesOn(back.treatments, day).single().scheduled)
    }

    @Test
    fun aTreatmentAddedDuringATripFollowsIt() {
        val s = Travel.sync(AppState(treatments = listOf(levo), travel = TravelPlan(day, 4, 1)))
        val added = Travel.sync(s.copy(treatments = s.treatments + levo.copy(id = "n", name = "Vit D")))
        assertTrue(added.treatments.all { it.shiftOn(day) == 240 })
    }

    @Test
    fun theSyncIsQuietWithoutATrip() {
        val s = AppState(treatments = listOf(levo))
        assertTrue(Travel.sync(s) === s)
        val traveling = Travel.sync(s.copy(travel = TravelPlan(day, 3, 1)))
        assertTrue(Travel.sync(traveling) == traveling)
    }

    @Test
    fun theDoseThatCrossesMidnightStaysThatDaysDose() {
        val late = levo.copy(id = "e", doses = listOf(DoseTime(22 * 60)))
        val s = Travel.sync(AppState(treatments = listOf(late), travel = TravelPlan(day, 6, 1)))
        val o = Schedule.occurrencesOn(s.treatments, day).single()
        assertEquals(day.plusDays(1).atTime(4, 0), o.scheduled)
        assertEquals(day, o.date)
        assertTrue(o.key.contains(day.toString()))
    }

    @Test
    fun thePreviewShowsTheFirstDays() {
        val rows = Travel.preview(TravelPlan(day, 6, 2), 8 * 60, 4)
        assertEquals("14 h 00", rows[0].second)
        assertEquals("12 h 00", rows[1].second)
        assertEquals("10 h 00", rows[2].second)
        assertEquals("8 h 00", rows[3].second)
    }

    // ───────────── Wiring ─────────────
    @Test
    fun theNewThingsAreFoundByTheirWords() {
        fun ids(q: String) = AppSearch.query(AppState(), q).map { it.id }
        assertTrue("suivi:labs" in ids("tsh"))
        assertTrue("suivi:labs" in ids("prise de sang"))
        assertTrue("travel" in ids("décalage horaire"))
        assertTrue("emergency" in ids("groupe sanguin"))
        assertTrue("pharmacy" in ids("pharmacie"))
        assertTrue("checkups" in ids("dentiste"))
        assertTrue("after_appointment" in ids("après la consultation"))
        assertTrue(HubCatalog.EVERYTHING.any { it.key == "suivi:labs" })
        HubCatalog.EVERYTHING.forEach { c -> assertTrue(c.key, AppSearch.CATALOG.any { it.id == c.key }) }
    }

    @Test
    fun anOldSaveLoadsWithTheNewFields() {
        val old = """{"onboarded":true,"treatments":[{"id":"t","name":"X","doses":[{"minuteOfDay":480}],"startDate":"2026-01-01"}],"appointments":[{"id":"a","title":"T","dateTime":"2026-11-01T10:00"}]}"""
        val s = AppRepository.JSON.decodeFromString(AppState.serializer(), old)
        assertTrue(s.labs.isEmpty() && s.checkups.isEmpty())
        assertNull(s.travel)
        assertTrue(s.treatments.single().changes.isEmpty() && s.treatments.single().shifts.isEmpty())
        assertEquals("", s.appointments.single().afterNotes)
        assertFalse(s.settings.emergencyOnLock)
        val saved = s.copy(labs = listOf(lab("1", "tsh", 0, 2.0)), checkups = listOf(dentist), travel = TravelPlan(day, 6, 2, day.plusDays(9)))
        val back = AppRepository.JSON.decodeFromString(AppState.serializer(), AppRepository.JSON.encodeToString(AppState.serializer(), saved))
        assertEquals(saved.labs, back.labs)
        assertEquals(saved.checkups, back.checkups)
        assertEquals(saved.travel, back.travel)
    }
}
