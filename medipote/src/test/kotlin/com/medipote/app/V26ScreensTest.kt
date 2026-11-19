package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.medipote.app.data.AppState
import com.medipote.app.data.Look
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.UsageStats
import com.medipote.app.data.WaterSettings
import com.medipote.app.ui.HubCard
import com.medipote.app.ui.HubTools
import com.medipote.app.ui.CardHub
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.settings.SettingsScreen
import com.medipote.app.ui.theme.MediPoteTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi")
class V26ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 19)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun state(settings: Settings) = AppState(profile = Profile(name = "Alex"), onboarded = true, settings = settings)

    @Test
    fun homeByTheHour_theRestWaitsBehindSeeMore() {
        val s = state(Settings(water = WaterSettings(enabled = true), look = Look(moment = true)))
        show { HomeScreen(s, day.atTime(9, 0), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onAllNodesWithText("Hydratation").assertCountEquals(0)
        shot("home_moment")
        compose.onNodeWithText("Voir plus", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Hydratation").assertExists()
        compose.onNodeWithText("Voir moins").assertExists()
    }

    @Test
    fun homeByTheHour_afternoonShowsTheWater() {
        val s = state(Settings(water = WaterSettings(enabled = true), look = Look(moment = true)))
        show { HomeScreen(s, day.atTime(15, 0), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText("Hydratation").assertExists()
    }

    @Test
    fun withoutTheMoment_everythingIsThereAsBefore() {
        val s = state(Settings(water = WaterSettings(enabled = true)))
        show { HomeScreen(s, day.atTime(9, 0), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText("Hydratation").assertExists()
        compose.onAllNodesWithText("Voir plus", substring = true).assertCountEquals(0)
    }

    @Test
    fun pinnedShortcutsAreOneTapAway() {
        var opened: String? = null
        val s = state(Settings(pins = listOf("soins:agenda", "suivi:sleep")))
        show { HomeScreen(s, day.atTime(9, 0), 0, 0, {}, {}, {}, {}, animate = false, onOpenShortcut = { opened = it }) }
        compose.onNodeWithText("Mes raccourcis").assertExists()
        shot("home_shortcuts")
        compose.onNodeWithText("📅  Agenda").performScrollTo().performClick()
        assertEquals("soins:agenda", opened)
        compose.onNodeWithText("🌙  Sommeil").assertExists()
    }

    @Test
    fun aLongPressRemovesAShortcut() {
        var removed: String? = null
        val s = state(Settings(pins = listOf("soins:agenda")))
        show { HomeScreen(s, day.atTime(9, 0), 0, 0, {}, {}, {}, {}, animate = false, onUnpin = { removed = it }) }
        compose.onNodeWithText("📅  Agenda").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Retirer de l'accueil").performClick()
        assertEquals("soins:agenda", removed)
    }

    @Test
    fun noPinsNoShortcutsBlock() {
        show { HomeScreen(state(Settings()), day.atTime(9, 0), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onAllNodesWithText("Mes raccourcis").assertCountEquals(0)
    }

    @Test
    fun soberModeHidesTheStatusLine() {
        show { HomeScreen(state(Settings()), day.atTime(9, 0), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText("🏅 0").assertExists()
    }

    @Test
    fun soberModeHidesTheStatusLine_whenOn() {
        show { HomeScreen(state(Settings(sober = true)), day.atTime(9, 0), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onAllNodesWithText("🏅 0").assertCountEquals(0)
    }

    private val cards = listOf(
        HubCard("soins:treatments", "💊", "Traitements"), HubCard("soins:pill", "🔁", "Pilule"), HubCard("soins:agenda", "📅", "Agenda"),
    )

    @Test
    fun aLongPressOnACardPinsIt() {
        var pinned: String? = null
        show { CardHub("Soins", cards, emptySet(), {}, {}, {}, tools = HubTools(onTogglePin = { pinned = it })) { } }
        compose.onNodeWithText("Agenda").performTouchInput { longClick() }
        compose.onNodeWithText("📌  Épingler sur l'accueil").performClick()
        assertEquals("soins:agenda", pinned)
    }

    @Test
    fun aPinnedCardCanBeUnpinnedTheSameWay() {
        show { CardHub("Soins", cards, emptySet(), {}, {}, {}, tools = HubTools(pins = listOf("soins:agenda"))) { } }
        compose.onNodeWithText("Agenda").performTouchInput { longClick() }
        compose.onNodeWithText("📌  Retirer de l'accueil").assertExists()
    }

    @Test
    fun openingACardIsCounted_andNothingIsOfferedTooEarly() {
        var counted: String? = null
        show { CardHub("Soins", cards, emptySet(), {}, {}, {}, tools = HubTools(onOpened = { counted = it })) { } }
        compose.onAllNodesWithText("Tu n'ouvres jamais", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Pilule").performClick()
        assertEquals("soins:pill", counted)
    }

    @Test
    fun anUnusedCardIsOfferedAfterTwoWeeks_theAnswerIsTheirs() {
        var away: String? = null
        var kept: String? = null
        val usage = UsageStats(opens = mapOf("soins:treatments" to 20, "soins:agenda" to 4), days = 15)
        show { CardHub("Soins", cards, emptySet(), {}, {}, {}, tools = HubTools(usage = usage, onPutAway = { away = it }, onKeep = { kept = it })) { } }
        compose.onNodeWithText("Tu n'ouvres jamais « Pilule » : on la range ?").assertExists()
        compose.onNodeWithText("Garder").performClick()
        assertEquals("soins:pill", kept)
        compose.onNodeWithText("Ranger").performClick()
        assertEquals("soins:pill", away)
    }

    private fun settingsScreen(section: String? = null) = show {
        SettingsScreen(
            Settings(), "Pilou", notificationsAllowed = true, exactAlarmsAllowed = true, dynamicColorAvailable = true,
            onBack = {}, onChange = {}, onRequestNotifications = {}, onRequestExactAlarms = {},
            onRenamePet = {}, onOpenProfile = {}, onReset = {}, openSection = section,
        )
    }

    @Test
    fun settingsAreShortGroupsThatOpenOnATap() {
        settingsScreen()
        compose.onNodeWithText("Rappels et notifications").assertExists()
        shot("settings_groups")
        compose.onNodeWithText("Sommeil").assertExists()
        compose.onAllNodesWithText("Notifications de prise").assertCountEquals(0)
        compose.onNodeWithText("Rappels et notifications").performClick()
        compose.onNodeWithText("Notifications de prise").assertExists()
        // One at a time.
        compose.onNodeWithText("Sommeil").performScrollTo().performClick()
        compose.onAllNodesWithText("Notifications de prise").assertCountEquals(0)
        compose.onNodeWithText("Objectif de sommeil").assertExists()
    }

    @Test
    fun aSearchResultOpensTheRightGroup() {
        settingsScreen("reminders")
        compose.onNodeWithText("Notifications de prise").assertExists()
        compose.onNodeWithText("Pas de rappel doux la nuit").performScrollTo().assertExists()
    }

    @Test
    fun soberModeIsInTheGameGroup() {
        settingsScreen("game")
        compose.onNodeWithText("Plus de points, de série, de boutique", substring = true).assertExists()
    }
}
