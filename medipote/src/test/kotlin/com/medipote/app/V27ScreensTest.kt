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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DocCategory
import com.medipote.app.data.DoseLog
import com.medipote.app.data.DoseTime
import com.medipote.app.data.HealthDocument
import com.medipote.app.data.Idea
import com.medipote.app.data.Profile
import com.medipote.app.data.ReminderPause
import com.medipote.app.data.Settings
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.domain.DoseOccurrence
import com.medipote.app.ui.CardHub
import com.medipote.app.ui.HubCard
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.ideas.IdeasScreen
import com.medipote.app.ui.journal.MonthScreen
import com.medipote.app.ui.settings.SettingsScreen
import com.medipote.app.ui.theme.MediPoteTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi")
class V27ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 19)
    private val now = day.atTime(14, 0)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val levo = Treatment("levo", name = "Lévothyrox", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(60))
    private fun taken(date: LocalDate) = DoseLog(DoseOccurrence.keyOf("levo", date, 8 * 60), "levo", date.atTime(8, 0), date.atTime(8, 5), TakeStatus.ON_TIME, 10)

    private val state = AppState(
        profile = Profile(name = "Alex"), onboarded = true, treatments = listOf(levo),
        doseLogs = (1..9).filter { it != 4 && it != 5 }.map { taken(day.minusDays(it.toLong())) },
        journal = listOf(2, 3, 6, 8).map { DayEntry(day.minusDays(it.toLong()), mental = 2 + it % 3) },
    )

    // ── Month ──
    @Test
    fun theMonthShowsItsDaysAndOpensOne() {
        var opened: LocalDate? = null
        show { MonthScreen(state, day, onOpenDay = { opened = it }) }
        compose.onNodeWithText("Novembre 2026").assertExists()
        shot("month_calendar")
        compose.onNodeWithContentDescription("12 novembre, prises toutes notées").performClick()
        assertEquals(LocalDate.of(2026, 11, 12), opened)
    }

    @Test
    fun theMonthGoesBackButNotIntoTheFuture() {
        show { MonthScreen(state, day, onOpenDay = {}) }
        compose.onNodeWithContentDescription("Mois suivant").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mois précédent").performClick()
        compose.onNodeWithText("Octobre 2026").assertExists()
        compose.onNodeWithContentDescription("Mois suivant").performClick()
        compose.onNodeWithText("Novembre 2026").assertExists()
    }

    @Test
    fun aFutureDayCannotBeOpened() {
        var opened = 0
        show { MonthScreen(state, day, onOpenDay = { opened++ }) }
        compose.onNodeWithContentDescription("25 novembre").performClick()
        assertEquals(0, opened)
    }

    @Test
    fun theCalendarIsACardOfTheSuiviPage() {
        show { CardHub("Suivi", listOf(HubCard("suivi:journal", "📖", "Journal"), HubCard("suivi:calendar", "🗓️", "Calendrier")), emptySet(), {}, {}, {}) { MonthScreen(state, day, {}) } }
        compose.onNodeWithText("Calendrier").performClick()
        compose.onNodeWithText("Novembre 2026").assertExists()
    }

    // ── Ideas ──
    @Test
    fun anIdeaIsWrittenAddedAndRemoved() {
        var added: String? = null
        var removed: String? = null
        val ideas = listOf(Idea("i1", "Un mode paysage", "Accueil", now))
        show { IdeasScreen(ideas, "Soins", onAdd = { added = it }, onRemove = { removed = it }, onShare = {}, onBack = {}) }
        compose.onNodeWithText("Noté depuis : Soins").assertExists()
        compose.onNodeWithText("Un mode paysage").assertExists()
        shot("ideas")
        compose.onNodeWithText("Ajouter à ma liste").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Plus de couleurs")
        compose.onNodeWithText("Ajouter à ma liste").performClick()
        assertEquals("Plus de couleurs", added)
        compose.onNodeWithContentDescription("Supprimer cette idée").performClick()
        assertEquals("i1", removed)
    }

    @Test
    fun theShareButtonAppearsOnlyWithIdeas() {
        show { IdeasScreen(emptyList(), "", onAdd = {}, onRemove = {}, onShare = {}, onBack = {}) }
        compose.onAllNodesWithText("Rien pour l'instant. C'est très bien aussi.").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Partager mes idées").assertCountEquals(0)
    }

    // ── Pause ──
    private fun settingsScreen(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) = show {
        SettingsScreen(
            settings, "Pilou", notificationsAllowed = true, exactAlarmsAllowed = true, dynamicColorAvailable = false,
            onBack = {}, onChange = onChange, onRequestNotifications = {}, onRequestExactAlarms = {},
            onRenamePet = {}, onOpenProfile = {}, onReset = {},
            nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), openSection = "reminders",
        )
    }

    @Test
    fun aPauseStartsInOneTap_withoutTheDosesByDefault() {
        var applied: Settings? = null
        settingsScreen(Settings()) { applied = it(Settings()) }
        shot("settings_pause")
        compose.onNodeWithText("⏸️  3 jours").performClick()
        assertEquals(now.plusDays(3), applied?.pause?.until)
        assertFalse(applied!!.pause.doses)
    }

    @Test
    fun theDosesJoinThePauseOnlyOnPurpose_withAWarning() {
        var applied: Settings? = null
        settingsScreen(Settings()) { applied = it(Settings()) }
        compose.onNodeWithText("Déconseillé, sauf si tu sais que tu n'en as pas besoin.").assertExists()
        // The first switch of the group is the one of the pause.
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("⚠️ Pendant la pause", substring = true).assertExists()
        compose.onNodeWithText("⏸️  1 semaine").performClick()
        assertTrue(applied!!.pause.doses)
    }

    @Test
    fun aRunningPauseSaysUntilWhen_andCanBeEnded() {
        var applied: Settings? = null
        val s = Settings(pause = ReminderPause(day.plusDays(1).atTime(8, 0)))
        settingsScreen(s) { applied = it(s) }
        compose.onNodeWithText("En pause jusqu'à", substring = true).assertExists()
        compose.onNodeWithText("Reprendre les rappels maintenant").performClick()
        assertNull(applied?.pause?.until)
    }

    @Test
    fun homeRemindsThatTheRemindersSleep() {
        var opened = 0
        val s = state.copy(settings = Settings(pause = ReminderPause(day.plusDays(1).atTime(8, 0))))
        show { HomeScreen(s, now, 0, 0, {}, {}, {}, {}, animate = false, onOpenPause = { opened++ }) }
        compose.onNodeWithText("Rappels en pause").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun noPauseNoNotice() {
        show { HomeScreen(state, now, 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onAllNodesWithText("Rappels en pause").assertCountEquals(0)
    }

    // ── Prescription renewed ──
    @Test
    fun aPrescriptionCanBeMarkedRenewed() {
        var renewed = 0
        val doc = HealthDocument("d", "Ordonnance", DocCategory.PRESCRIPTION, day.minusDays(80), "o.jpg", "image/jpeg", validUntil = day.plusDays(5))
        show { com.medipote.app.ui.documents.DocumentViewerScreen(doc, {}, {}, {}, {}, onRenew = { renewed++ }) }
        compose.onNodeWithText("✅  Ordonnance renouvelée").performClick()
        assertEquals(1, renewed)
    }

    @Test
    fun aRenewedPrescriptionSaysWhen() {
        val doc = HealthDocument("d", "Ordonnance", DocCategory.PRESCRIPTION, day.minusDays(80), "o.jpg", "image/jpeg", validUntil = day.plusDays(5), renewedOn = day)
        show { com.medipote.app.ui.documents.DocumentViewerScreen(doc, {}, {}, {}, {}) }
        compose.onAllNodesWithText("✅  Ordonnance renouvelée").assertCountEquals(0)
        compose.onNodeWithText("Renouvelée le", substring = true).assertExists()
    }

    // ── Changing phone ──
    @Test
    fun theBackupPageGuidesAPhoneChange() {
        show { com.medipote.app.ui.settings.BackupScreen(Settings(), now, {}, {}, {}) }
        compose.onNodeWithText("📱  Je change de téléphone").assertExists()
        compose.onAllNodesWithText("1️⃣", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Voir").performClick()
        compose.onNodeWithText("1️⃣", substring = true).assertExists()
        compose.onNodeWithText("5️⃣", substring = true).assertExists()
        compose.onNodeWithText("🔍  Tester une sauvegarde (sans rien changer)").assertExists()
        shot("backup_phone_change")
    }
}
