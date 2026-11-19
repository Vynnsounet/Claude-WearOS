package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.Backup
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DocCategory
import com.medipote.app.data.DoseLog
import com.medipote.app.data.DoseTime
import com.medipote.app.data.GameState
import com.medipote.app.data.HealthDocument
import com.medipote.app.data.Idea
import com.medipote.app.data.ReminderPause
import com.medipote.app.data.Settings
import com.medipote.app.data.SymptomNote
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.data.WaterSettings
import com.medipote.app.domain.AppSearch
import com.medipote.app.domain.Care
import com.medipote.app.domain.DoseDots
import com.medipote.app.domain.DoseOccurrence
import com.medipote.app.domain.Ideas
import com.medipote.app.domain.MonthCalendar
import com.medipote.app.domain.Pause
import com.medipote.app.domain.Renewals
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import com.medipote.app.ui.HubCatalog
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.27: pause, ideas, month calendar, prescription renewed, backup check. */
class V27Test {
    private val day = LocalDate.of(2026, 11, 19)
    private val now = day.atTime(14, 0)
    private val levo = Treatment("levo", name = "Lévothyrox", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(60))

    // ── Pause ──
    @Test
    fun aPauseHasThreeLengths() {
        assertEquals(day.plusDays(1).atTime(8, 0), Pause.until(Pause.Length.TOMORROW, now))
        assertEquals(now.plusDays(3), Pause.until(Pause.Length.THREE_DAYS, now))
        assertEquals(now.plusWeeks(1), Pause.until(Pause.Length.WEEK, now))
        assertTrue(Pause.active(Pause.start(Pause.Length.TOMORROW, false, now), now))
        assertFalse(Pause.active(Pause.start(Pause.Length.TOMORROW, false, now), day.plusDays(2).atStartOfDay()))
        assertFalse(Pause.active(ReminderPause(), now))
    }

    @Test
    fun aPauseSilencesTheSoftRemindersAndNeverTheAppointments() {
        val soft = ReminderPause(now.plusDays(1))
        listOf(AlarmKind.WATER, AlarmKind.STOCK, AlarmKind.SLEEP, AlarmKind.WIND_DOWN, AlarmKind.RENEWAL, AlarmKind.RITUAL, AlarmKind.RECAP)
            .forEach { assertTrue("$it", Pause.silences(soft, it)) }
        listOf(AlarmKind.DOSE, AlarmKind.FOLLOW_UP, AlarmKind.SNOOZE, AlarmKind.APPOINTMENT, AlarmKind.PRODUCT, AlarmKind.SUPPORT)
            .forEach { assertFalse("$it", Pause.silences(soft, it)) }
        // The doses join only on purpose; the appointments never.
        val all = soft.copy(doses = true)
        listOf(AlarmKind.DOSE, AlarmKind.FOLLOW_UP, AlarmKind.SNOOZE).forEach { assertTrue("$it", Pause.silences(all, it)) }
        assertFalse(Pause.silences(all, AlarmKind.APPOINTMENT))
    }

    private fun state(pause: ReminderPause) = AppState(
        onboarded = true, treatments = listOf(levo),
        settings = Settings(water = WaterSettings(enabled = true), pause = pause),
    )

    @Test
    fun theWaterRemindersWaitForTheEndOfThePause_theDosesKeepRinging() {
        val until = day.plusDays(1).atTime(8, 0)
        val plan = ReminderPlanner.plan(state(ReminderPause(until)), now)
        assertTrue(plan.none { it.kind == AlarmKind.WATER && it.time.isBefore(until) })
        assertTrue(plan.any { it.kind == AlarmKind.DOSE })
        // A silent alarm at the end of the pause plans everything again.
        assertEquals(listOf(until), plan.filter { it.kind == AlarmKind.RESUME }.map { it.time })
        // Without a pause: the water rings today.
        assertTrue(ReminderPlanner.plan(state(ReminderPause()), now).any { it.kind == AlarmKind.WATER && it.time.toLocalDate() == day })
    }

    @Test
    fun theDosesAreSilencedOnlyWhenAsked_andComeBackAfterThePause() {
        val until = day.plusDays(1).atTime(12, 0)
        val plan = ReminderPlanner.plan(state(ReminderPause(until, doses = true)), now)
        assertTrue(plan.none { it.kind == AlarmKind.DOSE && it.time.isBefore(until) })
        assertTrue(plan.any { it.kind == AlarmKind.DOSE && !it.time.isBefore(until) })
    }

    @Test
    fun aFinishedPauseChangesNothing() {
        val over = ReminderPause(now.minusHours(1))
        assertEquals(ReminderPlanner.plan(state(ReminderPause()), now), ReminderPlanner.plan(state(over), now))
    }

    // ── Ideas ──
    @Test
    fun ideasAreKeptNewestFirst_andBlankOnesAreIgnored() {
        var list = emptyList<Idea>()
        list = Ideas.add(list, "  Un mode paysage  ", "Accueil", now)
        list = Ideas.add(list, "   ", "Accueil", now)
        list = Ideas.add(list, "Plus de couleurs", "Paramètres", now.plusMinutes(1))
        assertEquals(listOf("Plus de couleurs", "Un mode paysage"), list.map { it.text })
        assertEquals(2, list.map { it.id }.toSet().size)
        assertEquals(listOf("Plus de couleurs"), Ideas.remove(list, list.last().id).map { it.text })
    }

    @Test
    fun theShareTextHoldsTheIdeasAndNothingElse() {
        val list = Ideas.add(Ideas.add(emptyList(), "Un mode paysage", "Accueil", now), "Plus de couleurs", "Paramètres", now.plusDays(1))
        val text = Ideas.shareText(list, "1.27.0")
        assertTrue("1.27.0" in text)
        assertTrue("Un mode paysage" in text && "Plus de couleurs" in text)
        assertTrue("Accueil" in text && "Paramètres" in text)
        assertTrue(text.indexOf("Un mode paysage") < text.indexOf("Plus de couleurs"))
    }

    @Test
    fun theScreenIsNamedInTheWordsOfTheApp() {
        assertEquals("Accueil", Ideas.screenName("home"))
        assertEquals("Soins", Ideas.screenName("treatments?card={card}"))
        assertEquals("Paramètres", Ideas.screenName("settings?section={section}"))
        assertEquals("", Ideas.screenName(null))
        assertEquals("Sleep log", Ideas.screenName("sleep_log"))
    }

    // ── Month ──
    @Test
    fun theMonthIsWholeWeeksStartingOnMonday() {
        val weeks = MonthCalendar.weeks(YearMonth.of(2026, 11))
        assertEquals(LocalDate.of(2026, 10, 26), weeks.first().first())
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(LocalDate.of(2026, 11, 30), weeks.last()[0])
        // February 2027 begins on a Monday and holds exactly four weeks.
        assertEquals(4, MonthCalendar.weeks(YearMonth.of(2027, 2)).size)
    }

    private fun taken(date: LocalDate) = DoseLog(
        DoseOccurrence.keyOf("levo", date, 8 * 60), "levo", date.atTime(8, 0), date.atTime(8, 5), TakeStatus.ON_TIME, 10,
    )

    @Test
    fun theDotsTellWhatWasNoted() {
        val month = YearMonth.of(2026, 11)
        val d1 = day.minusDays(1)
        val s = AppState(
            treatments = listOf(levo), doseLogs = listOf(taken(d1)),
            journal = listOf(DayEntry(d1, mental = 4)),
            symptomLog = listOf(SymptomNote(d1, "fatigue", 3)),
        )
        val a = MonthCalendar.dots(s, d1, month, day)
        assertEquals(DoseDots.ALL, a.doses)
        assertEquals(4, a.mood)
        assertTrue(a.symptoms)
        // Nothing noted a few days ago: neither a dot nor a verdict, just « not noted ».
        val b = MonthCalendar.dots(s, day.minusDays(3), month, day)
        assertEquals(DoseDots.NOT_NOTED, b.doses)
        assertNull(b.mood)
        assertFalse(b.symptoms)
        // Today is not over: nothing is « not noted » yet. The future shows nothing.
        assertEquals(DoseDots.NONE, MonthCalendar.dots(s, day, month, day).doses)
        assertEquals(DoseDots.NONE, MonthCalendar.dots(s, day.plusDays(2), month, day).doses)
        // A day of another month is flagged so it can be dimmed.
        assertFalse(MonthCalendar.dots(s, LocalDate.of(2026, 10, 30), month, day).inMonth)
    }

    @Test
    fun theMonthCannotGoIntoTheFuture() {
        assertTrue(MonthCalendar.canGoNext(YearMonth.of(2026, 10), day))
        assertFalse(MonthCalendar.canGoNext(YearMonth.of(2026, 11), day))
    }

    @Test
    fun theCalendarCardIsInTheCatalogAndTheSearch() {
        assertTrue(HubCatalog.EVERYTHING.any { it.key == "suivi:calendar" })
        assertTrue(AppSearch.query(AppState(), "calendrier").any { it.id == "suivi:calendar" })
        assertTrue(AppSearch.query(AppState(), "vacances").any { it.id == "pause" })
        assertTrue(AppSearch.query(AppState(), "suggestion").any { it.id == "ideas" })
        assertTrue(AppSearch.query(AppState(), "nouveau téléphone").any { it.id == "phone_change" })
        assertTrue(AppSearch.query(AppState(), "ordonnance renouvelée").any { it.id == "soins:docs" })
    }

    // ── Prescription renewed ──
    private val doc = HealthDocument("d", "Ordonnance", DocCategory.PRESCRIPTION, day.minusDays(80), "o.jpg", "image/jpeg", validUntil = day.plusDays(5))

    @Test
    fun aRenewedPrescriptionStopsBeingReminded() {
        val s = AppState(documents = listOf(doc))
        assertEquals(listOf("d"), Renewals.due(s, day).map { it.id })
        assertTrue(Renewals.canRenew(doc))
        val renewed = Renewals.renewed(doc, day)
        assertEquals(day, renewed.renewedOn)
        assertTrue(Renewals.due(s.copy(documents = listOf(renewed)), day).isEmpty())
        assertNull(Renewals.reminderDate(renewed))
        assertFalse(Renewals.canRenew(renewed))
        // No end date, or not a prescription: nothing to renew.
        assertFalse(Renewals.canRenew(doc.copy(validUntil = null)))
        assertFalse(Renewals.canRenew(doc.copy(category = DocCategory.LAB)))
    }

    @Test
    fun renewingPaysOnce_neverTakesAnythingAway() {
        val s = AppState(game = GameState(points = 10))
        val first = Care.renewed(s, "d", doc.validUntil, now)
        assertEquals(Care.RENEWAL_POINTS, first.points)
        assertEquals(15, first.state.game.points)
        val again = Care.renewed(first.state, "d", doc.validUntil, now)
        assertEquals(0, again.points)
        // The next prescription (another end date) is rewarded again.
        assertEquals(Care.RENEWAL_POINTS, Care.renewed(first.state, "d", day.plusMonths(3), now).points)
    }

    // ── Backup check ──
    @Test
    fun theBackupSummaryCountsInGoodFrench() {
        val s = AppState(treatments = listOf(levo), documents = listOf(doc, doc.copy(id = "e")), journal = listOf(DayEntry(day)))
        val text = Backup.summary(Backup.Content(s, emptyMap(), null))
        assertEquals("Elle contient 1 traitement, 0 prise notée, 1 jour de journal, 0 nuit, 0 mesure et 2 documents.", text)
    }

    @Test
    fun aBackupWrittenAndReadBackIsTheSame() {
        val s = AppState(treatments = listOf(levo), onboarded = true, ideas = Ideas.add(emptyList(), "Idée", "Accueil", now))
        val bytes = Backup.encode(s, null, "secret".toCharArray(), now)
        val back = Backup.decode(bytes, "secret".toCharArray())
        assertEquals(s, back.state)
        assertNotNull(back.createdAt)
    }

    @Test
    fun anOldSaveLoadsWithTheNewFields() {
        val json = """{"onboarded":true,"documents":[{"id":"d","title":"O","category":"PRESCRIPTION","date":"2026-01-01","fileName":"a","mimeType":"image/jpeg"}]}"""
        val state = AppRepository.JSON.decodeFromString(AppState.serializer(), json)
        assertTrue(state.ideas.isEmpty())
        assertNull(state.settings.pause.until)
        assertFalse(state.settings.pause.doses)
        assertNull(state.documents.single().renewedOn)
    }
}
