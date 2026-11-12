package com.medipote.app

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.medipote.app.data.AppState
import com.medipote.app.data.Accent
import com.medipote.app.data.Appointment
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Flow
import com.medipote.app.data.Intercourse
import com.medipote.app.data.PillPack
import com.medipote.app.data.WaterSettings
import com.medipote.app.ui.agenda.AgendaScreen
import com.medipote.app.ui.agenda.AppointmentEditorScreen
import com.medipote.app.ui.documents.DocumentsScreen
import com.medipote.app.ui.journal.DayEntryScreen
import com.medipote.app.ui.journal.JournalScreen
import com.medipote.app.ui.pill.PillScreen
import com.medipote.app.ui.pill.PillSetupScreen
import com.medipote.app.ui.Hub
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
import com.medipote.app.domain.Shop
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
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
            startDate = today.minusDays(60), withFood = true, createdAt = today.minusDays(20).atStartOfDay(),
            stock = 9.0, packSize = 60),
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

    private val pill = Treatment(
        "pill", "levonorgestrel_ee", "Leeloo", "", DoseUnit.TABLET, listOf(DoseTime(21 * 60)),
        startDate = today.minusDays(40), createdAt = today.minusDays(20).atStartOfDay(),
        pill = PillPack(21, 7, false, today.minusDays(10)), stock = 50.0, packSize = 21,
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
        state = state.copy(
            game = state.game.copy(points = state.game.points + 120),
            journal = listOf(
                DayEntry(today, physical = 4, mental = 3, mentalSymptoms = listOf("Stress"), waterGlasses = 5,
                    cycleSymptoms = listOf("Ballonnements")),
                DayEntry(today.minusDays(1), physical = 2, mental = 2, physicalSymptoms = listOf("Fatigue", "Maux de tête"),
                    illness = "Rhume", notes = "Nuit agitée, nez bouché."),
                DayEntry(today.minusDays(3), physical = 5, mental = 4, intercourse = Intercourse.PROTECTED),
                DayEntry(today.minusDays(5), physical = 3, mental = 5),
                DayEntry(today.minusDays(8), flow = Flow.MEDIUM, cycleSymptoms = listOf("Crampes"), physical = 2, mental = 3),
            ),
            appointments = listOf(
                Appointment("a1", "Dentiste", "Dr Martin", "12 rue des Lilas, Lyon", today.plusDays(2).atTime(14, 30)),
                Appointment("a2", "Prise de sang", "", "Laboratoire du centre", today.plusDays(9).atTime(8, 0)),
                Appointment("a3", "Médecin généraliste", "Dr Bernard", "", today.minusDays(12).atTime(10, 0)),
            ),
            settings = state.settings.copy(water = WaterSettings(enabled = true)),
        )
        return state
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
        compose.onNodeWithText("Nœud").assertExists()
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
        render("cycle") { CycleScreen(profile.cycle, sampleState().journal, today, {}, {}, {}) }
        compose.onNodeWithText("Jour").assertExists()
        compose.onNodeWithText("Les 4 phases de ton cycle").assertExists()
    }

    @Test
    fun cycleDark() {
        render("cycle_dark", ThemeMode.DARK) { CycleScreen(profile.cycle, sampleState().journal, today, {}, {}, {}) }
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


    @Test
    fun journal() {
        render("journal") { Hub("Suivi", listOf("Journal", "Cycle", "Stats"), 0, {}, {}) { JournalScreen(sampleState().journal, today) {} } }
        compose.onNodeWithText("Mes 14 derniers jours").assertExists()
    }

    @Test
    fun dayEntry() {
        render("day_entry") { DayEntryScreen(sampleState().entryFor(today.minusDays(1)), showCycle = true, onSave = {}, onBack = {}) }
        compose.onNodeWithText("État physique").assertExists()
    }

    @Test
    fun agenda() {
        render("agenda") {
            Hub("Santé", listOf("Agenda", "Documents"), 0, {}, {}) { AgendaScreen(sampleState().appointments, now, {}, {}) }
        }
        compose.onNodeWithText("Dentiste").assertExists()
    }

    @Test
    fun appointmentEditor() {
        render("appointment_editor") { AppointmentEditorScreen(sampleState().appointments.first(), today, {}, {}, {}) }
        compose.onNodeWithText("La veille").assertExists()
    }

    @Test
    fun documentsEmpty() {
        render("documents") { Hub("Santé", listOf("Agenda", "Documents"), 1, {}, {}) { DocumentsScreen(emptyList(), {}, {}, {}) } }
        compose.onNodeWithText("Ton carnet de santé numérique").assertExists()
    }

    /** The pill taken most evenings, once late, once forgotten. */
    private fun withPill(p: Treatment): AppState {
        var state = sampleState().let { it.copy(treatments = it.treatments + p) }
        for (offset in 12 downTo 1) {
            val date = today.minusDays(offset.toLong())
            val logs = state.doseLogs.associateBy { it.key }
            val item = Schedule.itemsOn(listOf(p), logs, date, date.atTime(21, 5)).firstOrNull() ?: continue
            if (offset == 4) continue
            val at = if (offset == 7) date.atTime(22, 40) else date.atTime(21, 5)
            state = Game.give(state, item, at)?.state ?: state
        }
        return state
    }

    @Test
    fun pillTab() {
        val state = withPill(pill)
        render("pill") {
            Hub("Traitements", listOf("Médicaments", "Pilule"), 1, {}, {}) { PillScreen(state, now, {}, {}, {}, animate = false) }
        }
        compose.onNodeWithText("Comprimé 11 sur 21").assertExists()
    }

    @Test
    fun pillBreakWeek() {
        val onBreak = pill.copy(pill = pill.pill!!.copy(packStart = today.minusDays(23)))
        val state = withPill(onBreak)
        render("pill_break", ThemeMode.DARK) {
            Hub("Traitements", listOf("Médicaments", "Pilule"), 1, {}, {}) { PillScreen(state, now, {}, {}, {}, animate = false) }
        }
        compose.onNodeWithText("Semaine de pause 🌙").assertExists()
    }

    @Test
    fun pillSetup() {
        render("pill_setup") { PillSetupScreen(null, today, {}, {}, {}) }
        compose.onNodeWithText("21 jours + 7 jours de pause").assertExists()
    }

    @Test
    fun shopToys() {
        val game = sampleState().game.copy(
            equipped = mapOf("COLOR" to "color_rainbow", "BACKGROUND" to "bg_city", "HAT" to "hat_witch", "TOY" to "toy_balloon", "EFFECT" to "fx_sparkles", "NECK" to "neck_cape"),
            owned = Shop.items.map { it.id }.toSet(),
        )
        render("shop_new", ThemeMode.DARK) { ShopScreen(game, {}, {}, animate = false) }
    }

    @Test
    fun petGallery() {
        // Every new item on one sheet, for review.
        val ids = Shop.items.filter { it.price > 0 }.takeLast(40)
        render("pet_gallery") {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(5)) {
                items(Shop.items.size) { i ->
                    val item = Shop.items[i]
                    androidx.compose.foundation.layout.Column {
                        com.medipote.app.ui.pet.ItemPreview(item.id, item.slot.name, androidx.compose.ui.Modifier.size(78.dp))
                        androidx.compose.material3.Text(item.name, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
        check(ids.isNotEmpty())
    }

    @Test
    fun homeAccent() {
        val state = sampleState().let { it.copy(settings = it.settings.copy(accent = Accent.LAVENDER)) }
        render("home_lavender", accent = Accent.LAVENDER) {
            HomeScreen(state, today.atTime(16, 0), 0, 0, {}, {}, {}, {}, animate = false)
        }
    }

    private fun render(name: String, theme: ThemeMode = ThemeMode.LIGHT, accent: Accent = Accent.MINT, content: @Composable () -> Unit) {
        compose.setContent {
            MediPoteTheme(themeMode = theme, accent = accent) {
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
