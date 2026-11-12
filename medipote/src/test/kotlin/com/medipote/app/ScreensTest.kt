package com.medipote.app

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.medipote.app.data.AppState
import com.medipote.app.data.CycleInfo
import com.medipote.app.data.DoseLog
import com.medipote.app.data.DoseTime
import com.medipote.app.data.DoseUnit
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.GameState
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.Sex
import com.medipote.app.data.ThemeMode
import com.medipote.app.data.Treatment
import com.medipote.app.domain.Game
import com.medipote.app.domain.Rules
import com.medipote.app.domain.Schedule
import com.medipote.app.ui.cycle.CycleScreen
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.onboarding.ProfileSetupScreen
import com.medipote.app.ui.onboarding.WelcomeScreen
import com.medipote.app.ui.settings.SettingsScreen
import com.medipote.app.ui.shop.ShopScreen
import com.medipote.app.ui.stats.StatsScreen
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.treatments.DrugSearchScreen
import com.medipote.app.ui.treatments.TreatmentEditorScreen
import com.medipote.app.ui.treatments.TreatmentsScreen
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen on a phone configuration with realistic data, checks key content,
 * and saves PNGs to medipote/build/screenshots for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi")
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)
    private val now = today.atTime(12, 10)

    private val treatments = listOf(
        Treatment("levo", "levothyroxine", "Lévothyroxine", "75 µg", DoseUnit.TABLET, listOf(DoseTime(7 * 60 + 30)),
            startDate = today.minusDays(60), createdAt = today.minusDays(20).atStartOfDay()),
        Treatment("metf", "metformine", "Metformine", "1000 mg", DoseUnit.TABLET, listOf(DoseTime(8 * 60), DoseTime(20 * 60)),
            startDate = today.minusDays(60), withFood = true, createdAt = today.minusDays(20).atStartOfDay()),
        Treatment("aml", "amlodipine", "Amlodipine", "5 mg", DoseUnit.CAPSULE, listOf(DoseTime(12 * 60)),
            startDate = today.minusDays(60), createdAt = today.minusDays(20).atStartOfDay()),
        Treatment("vitd", "vitamine_d", "Vitamine D3", "100 000 UI", DoseUnit.VIAL, listOf(DoseTime(9 * 60)),
            daysOfWeek = setOf(1), startDate = today.minusDays(60), endDate = today.plusDays(40),
            createdAt = today.minusDays(20).atStartOfDay()),
    )

    private val profile = Profile(
        name = "Alex", age = 34, weightKg = 64.5, heightCm = 168, sex = Sex.FEMALE,
        medicalHistory = listOf("Hypothyroïdie", "Diabète de type 2"), allergies = listOf("Pénicilline"),
        menstruating = true,
        cycle = CycleInfo(listOf(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 11, 3))),
    )

    /** Two weeks of history: mostly on time, a few late, a couple forgotten. */
    private fun sampleState(theme: ThemeMode = ThemeMode.LIGHT): AppState {
        var state = AppState(
            profile = profile, treatments = treatments, onboarded = true,
            settings = Settings(themeMode = theme),
            game = GameState(
                points = 0, streak = 6, bestStreak = 12, lastCheckIn = today, petName = "Pilou",
                owned = GameState.DEFAULT_OWNED + setOf("hat_cap", "glasses_round", "neck_scarf", "bg_meadow"),
                equipped = mapOf("COLOR" to "color_mint", "BACKGROUND" to "bg_meadow", "HAT" to "hat_cap"),
            ),
        )
        for (offset in 13 downTo 0) {
            val date = today.minusDays(offset.toLong())
            val logs = state.doseLogs.associateBy { it.key }
            for (occ in Schedule.occurrencesOn(state.treatments, date)) {
                if (occ.scheduled.isAfter(now)) continue
                val k = (offset * 7 + occ.dose.minuteOfDay) % 11
                if (offset == 0 && occ.dose.minuteOfDay == 12 * 60) continue // due right now
                val takenAt: LocalDateTime? = when {
                    offset == 0 && occ.dose.minuteOfDay == 8 * 60 -> occ.scheduled.plusMinutes(75)
                    k == 3 && offset > 0 -> null // forgotten
                    k == 5 -> occ.scheduled.plusMinutes(70)
                    else -> occ.scheduled.plusMinutes((k * 3 - 10).toLong())
                }
                if (takenAt == null || occ.key in logs) continue
                val item = Schedule.itemsOn(state.treatments, logs, date, takenAt).first { it.occurrence.key == occ.key }
                state = Game.give(state, item, takenAt)?.state ?: state
            }
        }
        return state.copy(game = state.game.copy(points = state.game.points + 120))
    }

    @Test
    fun home() {
        render("home") {
            HomeScreen(sampleState(), now, 0, 0, {}, {}, {}, {}, animate = false)
        }
        compose.onNodeWithText("Bonjour Alex 👋").assertExists()
        compose.onNodeWithText("C'est l'heure ! Pilou attend Amlodipine.").assertExists()
    }

    @Test
    fun homeDark() {
        render("home_dark", ThemeMode.DARK) {
            HomeScreen(sampleState(ThemeMode.DARK), now, 0, 0, {}, {}, {}, {}, animate = false)
        }
    }

    @Test
    fun homeSick() {
        // Nothing given today and it's mid-afternoon: the pet is sick.
        val state = sampleState().let { s -> s.copy(doseLogs = s.doseLogs.filter { it.scheduled.toLocalDate() != today }) }
        render("home_sick") {
            HomeScreen(state, today.atTime(15, 0), 0, 0, {}, {}, {}, {}, animate = false)
        }
        compose.onNodeWithText("se sent très mal", substring = true).assertExists()
    }

    @Test
    fun welcome() {
        render("welcome") { WelcomeScreen(onStart = {}, animate = false) }
        compose.onNodeWithText("Bienvenue sur MédiPote").assertExists()
    }

    @Test
    fun profileSetup() {
        render("profile_setup") { ProfileSetupScreen(profile, today, {}, {}) }
        compose.onNodeWithText("IMC : 22,9 (corpulence normale)").assertExists()
    }

    @Test
    fun treatments() {
        render("treatments") { TreatmentsScreen(treatments, today, {}, {}) }
        compose.onNodeWithText("Metformine 1000 mg").assertExists()
    }

    @Test
    fun drugSearch() {
        render("drug_search") { DrugSearchScreen(onBack = {}, onPick = {}, onCustom = {}, autoFocus = false) }
        compose.onNodeWithText("Médicament introuvable ?").assertExists()
    }

    @Test
    fun treatmentEditor() {
        render("editor") {
            TreatmentEditorScreen(
                existing = null, drug = DrugGuide.byId("amoxicilline"), customName = "",
                allergies = profile.allergies, today = today, onSave = {}, onDelete = {}, onBack = {},
            )
        }
        compose.onNodeWithText("Attention : tu as signalé une allergie", substring = true).assertExists()
    }

    @Test
    fun shop() {
        render("shop") { ShopScreen(sampleState().game, {}, {}, animate = false) }
        compose.onNodeWithText("Couronne").assertExists()
    }

    @Test
    fun stats() {
        render("stats") { StatsScreen(sampleState(), now) }
        compose.onNodeWithText("14 derniers jours").assertExists()
    }

    @Test
    fun statsDark() {
        render("stats_dark", ThemeMode.DARK) { StatsScreen(sampleState(ThemeMode.DARK), now) }
    }

    @Test
    fun cycle() {
        render("cycle") { CycleScreen(profile.cycle, today, {}, {}) }
        compose.onNodeWithText("Jour").assertExists()
    }

    @Test
    fun cycleDark() {
        render("cycle_dark", ThemeMode.DARK) { CycleScreen(profile.cycle, today, {}, {}) }
    }

    @Test
    fun settings() {
        render("settings") {
            SettingsScreen(
                Settings(), "Pilou", notificationsAllowed = true, exactAlarmsAllowed = false, dynamicColorAvailable = true,
                onBack = {}, onChange = {}, onRequestNotifications = {}, onRequestExactAlarms = {},
                onRenamePet = {}, onOpenProfile = {}, onReset = {},
            )
        }
        compose.onNodeWithText("Sombre").assertExists()
        compose.onNodeWithText("+${Rules.POINTS_ON_TIME} pts").assertExists()
    }

    private fun render(name: String, theme: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
        compose.setContent {
            MediPoteTheme(themeMode = theme) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
