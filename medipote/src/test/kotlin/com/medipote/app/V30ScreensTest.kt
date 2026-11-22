package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import com.medipote.app.data.DoseTime
import com.medipote.app.data.LabResult
import com.medipote.app.data.Profile
import com.medipote.app.data.RegularCheckup
import com.medipote.app.data.Settings
import com.medipote.app.data.Sex
import com.medipote.app.data.SupportSettings
import com.medipote.app.data.TravelPlan
import com.medipote.app.data.TrustedContact
import com.medipote.app.data.Treatment
import com.medipote.app.domain.Emergency
import com.medipote.app.domain.PharmacyItem
import com.medipote.app.ui.agenda.AfterAppointmentScreen
import com.medipote.app.ui.agenda.AgendaScreen
import com.medipote.app.ui.emergency.EmergencyScreen
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.labs.LabDetailScreen
import com.medipote.app.ui.labs.LabEditorScreen
import com.medipote.app.ui.labs.LabsScreen
import com.medipote.app.ui.onboarding.ProfileScreen
import com.medipote.app.ui.settings.SettingsScreen
import com.medipote.app.ui.settings.TravelScreen
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.treatments.PharmacyScreen
import com.medipote.app.ui.treatments.TreatmentsScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1800dp-xhdpi")
class V30ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 19)
    private val now = day.atTime(10, 0)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val levo = Treatment("levo", name = "Lévothyrox", strength = "75 µg", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(200), createdAt = day.minusDays(200).atStartOfDay())
    private fun lab(id: String, marker: String, daysAgo: Int, value: Double) = LabResult(id, marker, day.minusDays(daysAgo.toLong()), value, com.medipote.app.domain.Labs.unit(marker))
    private val labsState = AppState(
        profile = Profile("Alex", sex = Sex.FEMALE), onboarded = true,
        labs = listOf(lab("1", "tsh", 180, 4.8), lab("2", "tsh", 90, 3.4), lab("3", "tsh", 5, 2.2), lab("4", "ferritin", 5, 12.0)),
        treatments = listOf(levo.copy(changes = listOf(com.medipote.app.data.DoseChange(day.minusDays(120), "Lévothyrox : 75 µg → 88 µg")))),
    )

    // ───────────── Analyses ─────────────
    @Test
    fun theAnalysesAreListedWithTheirLatestValue() {
        var opened: String? = null
        show { LabsScreen(labsState, onOpen = { opened = it }, onAdd = {}) }
        shot("labs")
        compose.onNodeWithText("🦋  TSH").assertExists()
        compose.onNodeWithText("2,2 mUI/L").assertExists()
        compose.onNodeWithText("Dans les repères", substring = true).assertExists()
        compose.onNodeWithText("Sous les repères", substring = true).assertExists() // the ferritin
        compose.onNodeWithText("🦋  TSH").performClick()
        assertEquals("tsh", opened)
    }

    @Test
    fun anAnalysisShowsItsCurve_itsTrend_andTheTreatmentChange() {
        show { LabDetailScreen(labsState, "tsh", onAdd = {}, onEdit = {}, onBack = {}) }
        shot("lab_detail")
        compose.onNodeWithText("En baisse depuis le", substring = true).assertExists()
        compose.onNodeWithText("Changements de traitement sur cette période").assertExists()
        compose.onNodeWithText("Lévothyrox : 75 µg → 88 µg", substring = true).assertExists()
        compose.onNodeWithText("Repères : 0,4 à 4 mUI/L").assertExists()
    }

    @Test
    fun aValueCanBeEditedFromTheList() {
        var edited: String? = null
        show { LabDetailScreen(labsState, "tsh", onAdd = {}, onEdit = { edited = it }, onBack = {}) }
        // The first is the summary at the top, the second the line of the list.
        compose.onAllNodesWithText("2,2 mUI/L")[1].performScrollTo().performClick()
        assertEquals("3", edited)
    }

    @Test
    fun aValueIsAddedInAFewTaps() {
        var saved: LabResult? = null
        show { LabEditorScreen(null, null, day, onSave = { saved = it }, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("Enregistrer").assertIsNotEnabled()
        compose.onNodeWithText("🦋 TSH").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("2,5")
        compose.onNodeWithText("Enregistrer").performClick()
        assertEquals("tsh", saved?.marker)
        assertEquals(2.5, saved?.value ?: 0.0, 0.0)
        assertEquals("mUI/L", saved?.unit)
        assertEquals(day, saved?.date)
    }

    @Test
    fun anAnalysisOfThePersonsOwnNeedsANameAndAUnit() {
        var saved: LabResult? = null
        show { LabEditorScreen(null, null, day, onSave = { saved = it }, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("Autre…").performClick()
        compose.onNodeWithText("Nom de l'analyse").performTextInput("Zinc")
        compose.onNodeWithText("Unité (ex. mg/L)").performTextInput("µmol/L")
        compose.onNodeWithText("Valeur", substring = true).performTextInput("14")
        compose.onNodeWithText("Enregistrer").performClick()
        assertEquals("Zinc", saved?.marker)
        assertEquals("µmol/L", saved?.unit)
    }

    // ───────────── Voyage ─────────────
    @Test
    fun theTripShowsHowTheDosesMove() {
        var saved: TravelPlan? = null
        show { TravelScreen(null, day, sampleMinute = 8 * 60, onSave = { saved = it }, onCancelTrip = {}, onBack = {}) }
        shot("travel")
        compose.onNodeWithText("Pour une prise de 8 h 00").assertExists()
        compose.onNodeWithText("14 h 00").assertExists()
        compose.onNodeWithText("Pour la pilule, le Lévothyrox", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Enregistrer mon voyage").performScrollTo().performClick()
        assertEquals(6, saved?.offsetHours)
        assertEquals(1, saved?.stepHours)
        assertEquals(day.plusDays(1), saved?.arrival)
    }

    @Test
    fun aTripInProgressCanBeCancelled() {
        var cancelled = 0
        show { TravelScreen(TravelPlan(day, 6, 2), day, 8 * 60, {}, { cancelled++ }, {}) }
        compose.onNodeWithText("Annuler le voyage : tout reprend ses heures").performScrollTo().performClick()
        assertEquals(1, cancelled)
    }

    // ───────────── Fiche d'urgence ─────────────
    private val emergencyState = AppState(
        profile = Profile("Camille", age = 34, sex = Sex.FEMALE, medicalHistory = listOf("Hashimoto"), allergies = listOf("Pénicilline"), bloodGroup = "O+"),
        treatments = listOf(levo), onboarded = true,
        settings = Settings(support = SupportSettings(contacts = listOf(TrustedContact("Marie", "0601020304")))),
    )

    @Test
    fun theEmergencyCardIsReadableAtAGlance() {
        var called: String? = null
        show { EmergencyScreen(Emergency.card(emergencyState, day), onCall = { called = it }, onShare = {}, onEditProfile = {}, onBack = {}) }
        shot("emergency")
        compose.onNodeWithText("Camille").assertExists()
        compose.onNodeWithText("•  Pénicilline").assertExists()
        compose.onNodeWithText("•  Hashimoto").assertExists()
        compose.onNodeWithText("•  Lévothyrox 75 µg — 8 h 00").assertExists()
        compose.onNodeWithText("Marie").assertExists()
        compose.onNodeWithText("Appeler").performClick()
        assertEquals("0601020304", called)
        compose.onNodeWithText("📞  15 (SAMU)").performClick()
        assertEquals("15", called)
    }

    @Test
    fun anEmptyCardInvitesToCompleteTheProfile() {
        show { EmergencyScreen(Emergency.card(AppState(profile = Profile("Alex")), day), {}, {}, {}, {}) }
        compose.onNodeWithText("Rien n'est encore renseigné", substring = true).assertExists()
    }

    @Test
    fun theProfileLeadsToTheEmergencyCard() {
        var opened = 0
        show { ProfileScreen(emergencyState.profile!!, day, {}, {}, onOpenEmergency = { opened++ }) }
        compose.onNodeWithText("Groupe sanguin").assertExists()
        compose.onNodeWithText("🚑  Ma fiche d'urgence").performClick()
        assertEquals(1, opened)
    }

    // ───────────── Après le rendez-vous / suivi régulier ─────────────
    private val past = Appointment("a", "Endocrinologue", "Dr Martin", dateTime = day.minusDays(1).atTime(14, 0))

    @Test
    fun whatWasSaidIsWrittenDown() {
        var saved: String? = null
        var next = 0
        var labs = 0
        show { AfterAppointmentScreen(past, onSaveNotes = { saved = it }, onAttended = {}, onTreatments = {}, onLabs = { labs++ }, onNext = { next++ }, onBack = {}) }
        compose.onNodeWithText("Enregistrer").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Passer à 88 µg, prise de sang dans 6 semaines")
        compose.onNodeWithText("Enregistrer").performClick()
        assertEquals("Passer à 88 µg, prise de sang dans 6 semaines", saved)
        compose.onNodeWithText("📅  Programmer le prochain rendez-vous").performScrollTo().performClick()
        compose.onNodeWithText("🧪  Des résultats d'analyses : mes analyses").performScrollTo().performClick()
        assertEquals(1, next)
        assertEquals(1, labs)
    }

    @Test
    fun theTreatmentsAreOneTapAway() {
        var treatments = 0
        show { AfterAppointmentScreen(past, {}, {}, onTreatments = { treatments++ }, onLabs = {}, onNext = {}, onBack = {}) }
        compose.onNodeWithText("💊  Un traitement a changé : mes traitements").performScrollTo().performClick()
        assertEquals(1, treatments)
    }

    private val dentist = RegularCheckup("c1", "Dentiste", 12, day.minusMonths(11).minusDays(10))

    @Test
    fun aRegularCheckupIsDoneInOneTap() {
        var done: String? = null
        show { AgendaScreen(emptyList(), now, {}, {}, checkups = listOf(dentist), onCheckupDone = { done = it }) }
        shot("agenda_checkups")
        compose.onNodeWithText("Suivi régulier").performScrollTo().assertExists()
        compose.onNodeWithText("Dentiste").performScrollTo().assertExists()
        compose.onNodeWithText("✓  Fait aujourd'hui").performScrollTo().performClick()
        assertEquals("c1", done)
    }

    @Test
    fun aRegularCheckupIsAddedFromTheSuggestions() {
        var added: Triple<String, Int, LocalDate?>? = null
        show { AgendaScreen(emptyList(), now, {}, {}, checkups = emptyList(), onCheckupAdd = { t, m, l -> added = Triple(t, m, l) }) }
        compose.onNodeWithText("➕  Ajouter un suivi régulier").performScrollTo().performClick()
        compose.onNodeWithText("🩸 Prise de sang").performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        assertEquals("Prise de sang", added?.first)
        assertEquals(12, added?.second)
    }

    @Test
    fun theAgendaWithoutCheckupsKeepsItsWay() {
        show { AgendaScreen(emptyList(), now, {}, {}) }
        compose.onAllNodesWithText("Suivi régulier").assertCountEquals(0)
    }

    // ───────────── Pharmacie ─────────────
    @Test
    fun thePharmacyListIsShareable() {
        var shared = 0
        show { PharmacyScreen(listOf(PharmacyItem("💊", "Lévothyrox 75 µg : il reste environ 3 jours")), { shared++ }, {}) }
        compose.onNodeWithText("Lévothyrox 75 µg : il reste environ 3 jours").assertExists()
        compose.onNodeWithContentDescription("Partager la liste").performClick()
        assertEquals(1, shared)
    }

    @Test
    fun anEmptyPharmacyListSaysSo() {
        show { PharmacyScreen(emptyList(), {}, {}) }
        compose.onNodeWithText("Rien à racheter", substring = true).assertExists()
    }

    @Test
    fun theTreatmentsOfferThePharmacyOnlyWhenThereIsSomething() {
        var opened = 0
        show { TreatmentsScreen(listOf(levo), day, onAdd = {}, onEdit = {}, embedded = true, onPharmacy = { opened++ }) }
        compose.onNodeWithText("🛒  Liste pour la pharmacie").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theTreatmentsHaveNoPharmacyButtonWhenNothingIsNeeded() {
        show { TreatmentsScreen(listOf(levo), day, onAdd = {}, onEdit = {}, embedded = true) }
        compose.onAllNodesWithText("🛒  Liste pour la pharmacie").assertCountEquals(0)
    }

    // ───────────── Accueil ─────────────
    private fun home(state: AppState, onOpenAfter: (Appointment) -> Unit = {}, onOpenAgenda: () -> Unit = {}, onOpenTravel: () -> Unit = {}) = show {
        HomeScreen(state, now, 0, 0, {}, {}, {}, {}, animate = false, onOpenAfter = onOpenAfter, onOpenAgenda = onOpenAgenda, onOpenTravel = onOpenTravel)
    }

    @Test
    fun theHomeAsksAboutYesterdaysAppointment() {
        var opened: Appointment? = null
        home(AppState(profile = Profile("Alex"), onboarded = true, appointments = listOf(past)), onOpenAfter = { opened = it })
        compose.onNodeWithText("Rendez-vous « Endocrinologue » : et ensuite ?").performClick()
        assertEquals("a", opened?.id)
    }

    @Test
    fun theHomeSpeaksOfACheckupDue() {
        var opened = 0
        home(AppState(profile = Profile("Alex"), onboarded = true, checkups = listOf(RegularCheckup("c", "Dentiste", 12, day.minusMonths(14)))), onOpenAgenda = { opened++ })
        compose.onNodeWithText("Suivi régulier : dentiste").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theHomeSaysWhenTheDosesAreShifted() {
        var opened = 0
        val s = com.medipote.app.domain.Travel.sync(AppState(profile = Profile("Alex"), onboarded = true, treatments = listOf(levo), travel = TravelPlan(day, 6, 1)))
        home(s, onOpenTravel = { opened++ })
        compose.onNodeWithText("Mode voyage").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theHomeIsQuietWhenNothingIsDue() {
        home(AppState(profile = Profile("Alex"), onboarded = true, checkups = listOf(RegularCheckup("c", "Dentiste", 12, day.minusMonths(2)))))
        compose.onAllNodesWithText("Suivi régulier : dentiste").assertCountEquals(0)
        compose.onAllNodesWithText("Mode voyage").assertCountEquals(0)
    }

    // ───────────── Réglages ─────────────
    @Test
    fun theSettingsPointToTheTripAndTheLockScreenCard() {
        var trip = 0
        var changed: Settings? = null
        show {
            SettingsScreen(
                Settings(), "Pilou", notificationsAllowed = true, exactAlarmsAllowed = true, dynamicColorAvailable = false,
                onBack = {}, onChange = { changed = it(Settings()) }, onRequestNotifications = {}, onRequestExactAlarms = {}, onRenamePet = {}, onOpenProfile = {}, onReset = {},
                onOpenTravel = { trip++ }, openSection = "reminders",
            )
        }
        compose.onNodeWithText("✈️  Préparer un voyage").performScrollTo().performClick()
        assertEquals(1, trip)
    }

    @Test
    fun theLockScreenCardIsAChoiceInThePrivacyGroup() {
        var changed: Settings? = null
        show {
            SettingsScreen(
                Settings(), "Pilou", notificationsAllowed = true, exactAlarmsAllowed = true, dynamicColorAvailable = false,
                onBack = {}, onChange = { changed = it(Settings()) }, onRequestNotifications = {}, onRequestExactAlarms = {}, onRenamePet = {}, onOpenProfile = {}, onReset = {},
                openSection = "privacy",
            )
        }
        compose.onNodeWithText("Fiche d'urgence sur l'écran de verrouillage de l'appli").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.isToggleable())[0].performClick()
        assertNotNull(changed)
        assertTrue(changed!!.emergencyOnLock)
    }
}
