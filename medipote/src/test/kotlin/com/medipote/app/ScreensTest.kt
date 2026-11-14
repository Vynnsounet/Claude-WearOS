package com.medipote.app

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.onNodeWithContentDescription
import com.medipote.app.ui.pet.drawBackground
import com.medipote.app.ui.pet.drawPet
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
        compose.onNodeWithText("Verre d'eau (jusqu'à l'objectif du jour)").assertExists()
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


    @Test
    fun petReactions() {
        // Each reaction frozen mid-animation, for review.
        val reactions = com.medipote.app.domain.PetReaction.entries
        render("pet_reactions") {
            androidx.compose.foundation.layout.Column {
                reactions.chunked(4).forEach { row ->
                    androidx.compose.foundation.layout.Row {
                        row.forEach { r ->
                            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.size(100.dp)) {
                                    drawBackground("bg_meadow")
                                    run {
                                        drawPet(
                                            if (r == com.medipote.app.domain.PetReaction.YAWN) com.medipote.app.domain.PetMood.SLEEPING
                                            else if (r == com.medipote.app.domain.PetReaction.SNEEZE) com.medipote.app.domain.PetMood.UNWELL
                                            else com.medipote.app.domain.PetMood.CONTENT,
                                            mapOf("COLOR" to "color_mint"), 0.5f, 0f, 0.3f, 1f, r,
                                            if (r == com.medipote.app.domain.PetReaction.SNEEZE) 0.55f else 0.5f,
                                            androidx.compose.ui.geometry.Offset(0.8f, -0.4f),
                                        )
                                    }
                                }
                                androidx.compose.material3.Text(r.name, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun tappingThePetMakesItTalk() {
        compose.mainClock.autoAdvance = false
        var cuddles = 0
        compose.setContent {
            MediPoteTheme {
                com.medipote.app.ui.pet.InteractivePet(
                    mood = com.medipote.app.domain.PetMood.CONTENT,
                    equipped = mapOf("COLOR" to "color_mint", "BACKGROUND" to "bg_room"),
                    talk = { r -> "Réaction : ${r.name}" },
                    petName = "Pilou",
                    onCuddle = { cuddles++ },
                    modifier = androidx.compose.ui.Modifier.size(300.dp, 240.dp),
                )
            }
        }
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Réaction : JUMP").assertExists()
        // Long press: a hug, which counts as a cuddle
        compose.onRoot().performTouchInput { longClick(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Réaction : HUG").assertExists()
        // Stroke sideways: petting
        compose.onRoot().performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Réaction : PETTED").assertExists()
        check(cuddles >= 2) { "cuddles=$cuddles" }
        // The bubble disappears on its own
        compose.mainClock.advanceTimeBy(5000)
        compose.onNodeWithText("Réaction : PETTED").assertDoesNotExist()
    }

    @Test
    fun markADoseAsAlreadyTaken() {
        var early: com.medipote.app.domain.DoseItem? = null
        // At 12:10 the 20:00 Metformine dose is still far away.
        render("home_early") { HomeScreen(sampleState(), now, 0, 0, {}, {}, {}, {}, animate = false, onGiveEarly = { early = it }) }
        compose.onNodeWithText("Déjà pris ?").performScrollTo()
        save("home_early_card", compose.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("Déjà pris ?").performClick()
        compose.onNodeWithText("Prise prévue à 20:00 : Metformine 1000 mg", substring = true).assertExists()
        save("home_early_dialog", compose.onNode(androidx.compose.ui.test.isDialog()).captureToImage().asAndroidBitmap())
        compose.onNodeWithText("Oui, c'est fait").performClick()
        check(early?.occurrence?.dose?.minuteOfDay == 20 * 60) { "early=$early" }
    }

    // ── As-needed medications ──

    private val ibu = Treatment(
        "ibu", "ibuprofene", "Ibuprofène", "400 mg", DoseUnit.TABLET, emptyList(),
        startDate = today.minusDays(120), createdAt = today.minusDays(120).atStartOfDay(),
        asNeeded = true, maxPerDay = 3, minIntervalHours = 6, stock = 7.0, packSize = 20,
    )

    /** Three months of as-needed intakes: mostly headaches in the evening, at work or after screens. */
    private fun prnState(): AppState {
        val base = sampleState()
        val reasons = listOf("Mal de tête", "Mal de tête", "Règles douloureuses", "Mal de tête", "Mal de dos")
        val contexts = listOf(listOf("Au travail", "Écrans"), listOf("Stress", "Fatigue"), listOf("Règles"), listOf("Écrans"), listOf("Manque de sommeil"))
        val intakes = (0 until 26).map { i ->
            val day = today.minusDays((i * 3.4).toLong() + 1)
            val hour = listOf(21, 19, 13, 22, 8, 20)[i % 6]
            com.medipote.app.data.PrnIntake(
                "p$i", "ibu", day.atTime(hour, (i * 7) % 60),
                reason = reasons[i % reasons.size], intensity = 4 + i % 5,
                contexts = contexts[i % contexts.size], relief = if (i % 3 == 0) null else 3 + i % 3,
            )
        } + com.medipote.app.data.PrnIntake("today", "ibu", today.atTime(9, 40), reason = "Mal de tête", askRelief = true)
        return base.copy(treatments = base.treatments + ibu, prnIntakes = intakes)
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun homeWithAsNeeded() {
        render("home_prn") { HomeScreen(prnState(), now, 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText("Est-ce que Ibuprofène a fait effet ?").assertExists()
        compose.onNodeWithText("Dernière prise il y a 2 h 30 · 1 prise aujourd'hui").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1200dp-xhdpi")
    fun treatmentsWithAsNeeded() {
        val state = prnState()
        render("treatments_prn") {
            TreatmentsScreen(state.treatments, today, {}, {}, lastIntakes = mapOf("ibu" to today.atTime(9, 40)), now = now)
        }
        compose.onAllNodes(androidx.compose.ui.test.hasText("Si besoin"), useUnmergedTree = true).onFirst().assertExists()
        compose.onNodeWithText("Max 3 / 24 h · 6 h entre deux prises").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun prnLog() {
        render("prn_log") {
            com.medipote.app.ui.prn.PrnLogScreen(prnState(), ibu, null, now, {}, {}, {})
        }
        // The previous intake was 2 h 30 ago, the minimum is 6 h.
        compose.onNodeWithText("Seulement 2 h 30 depuis la prise précédente", substring = true).assertExists()
        compose.onNodeWithText("Me demander dans 1 h si ça a marché").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h3200dp-xhdpi")
    fun prnStats() {
        val state = prnState()
        render("prn_stats") { com.medipote.app.ui.prn.PrnStatsScreen(state, ibu, now, {}, {}, {}, {}) }
        compose.onNodeWithText("À quelle heure ?").assertExists()
        compose.onNodeWithText("Motif le plus fréquent : mal de tête", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h3600dp-xhdpi")
    fun statsFull() {
        render("stats_full") { StatsScreen(prnState(), now) }
        compose.onNodeWithText("Calendrier").assertExists()
        compose.onNodeWithText("Selon le moment").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h3600dp-xhdpi")
    fun statsFullDark() {
        render("stats_full_dark", ThemeMode.DARK) { StatsScreen(prnState().let { it.copy(settings = it.settings.copy(themeMode = ThemeMode.DARK)) }, now) }
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun editorAsNeeded() {
        render("editor_prn") {
            TreatmentEditorScreen(
                existing = ibu, drug = DrugGuide.byId("ibuprofene"), customName = "",
                allergies = emptyList(), today = today, onSave = {}, onDelete = {}, onBack = {},
            )
        }
        compose.onNodeWithText("Nombre maximum de prises").assertExists()
    }

    // ── Sleep ──

    /** Three weeks of nights: short weeknights with awakenings, longer weekends. */
    private fun sleepState(): AppState {
        val reasons = listOf("Toilettes", "Bruit", "Toilettes", "Anxiété", "Chaleur")
        val nights = (0 until 21).map { i ->
            val d = today.minusDays(i.toLong())
            val weekend = d.dayOfWeek.value >= 6
            val wake = d.atTime(if (weekend) 9 else 6, if (weekend) 15 else 45)
            val bed = com.medipote.app.domain.Sleep.bedtimeFor(d, listOf(23 * 60 + 10, 23 * 60 + 45, 30, 22 * 60 + 50)[i % 4])
            val awakenings = (0 until (i * 7 % 3)).map { k ->
                com.medipote.app.data.Awakening(
                    com.medipote.app.domain.Sleep.inNight(wake, listOf(3 * 60, 4 * 60 + 30, 2 * 60 + 15)[(i + k) % 3]),
                    listOf(10, 25, 40)[(i + k) % 3], reasons[(i + k) % reasons.size],
                )
            }
            com.medipote.app.data.SleepNight(
                d, bed, listOf(10, 20, 30)[i % 3], wake, if (weekend) emptyList() else awakenings,
                quality = 2 + (i * 5 % 4), tags = listOfNotNull("Écrans avant de dormir".takeIf { i % 3 == 0 }, "Caféine".takeIf { i % 5 == 1 }),
            )
        }
        return sampleState().copy(sleep = nights)
    }

    @Test
    @Config(qualifiers = "w400dp-h3000dp-xhdpi")
    fun sleepTab() {
        val state = sleepState()
        render("sleep") { com.medipote.app.ui.sleep.SleepScreen(state, now, {}, {}) }
        compose.onNodeWithText("Nuit du 11 au 12 novembre").assertExists()
        compose.onNodeWithText("Réveils dans la nuit").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h3000dp-xhdpi")
    fun sleepTabDark() {
        val state = sleepState().let { it.copy(settings = it.settings.copy(themeMode = ThemeMode.DARK)) }
        render("sleep_dark", ThemeMode.DARK) { com.medipote.app.ui.sleep.SleepScreen(state, now, {}, {}) }
    }

    @Test
    fun sleepTabEmpty() {
        render("sleep_empty") { com.medipote.app.ui.sleep.SleepScreen(sampleState(), now, {}, {}) }
        compose.onNodeWithText("Comment as-tu dormi cette nuit ?").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h2200dp-xhdpi")
    fun sleepEditor() {
        val state = sleepState()
        val night = state.sleep.first { it.awakenings.size >= 2 }
        render("sleep_editor") { com.medipote.app.ui.sleep.SleepEditorScreen(state, night, night.date, today, {}, {}, {}) }
        compose.onNodeWithText("Ajouter un réveil").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("Vers", substring = true)).assertCountEquals(night.awakenings.size)
    }

    @Test
    fun homeAsksAboutTheNight() {
        render("home_sleep_prompt") { HomeScreen(sampleState(), today.atTime(8, 0), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText("Bien dormi ?").performScrollTo().assertExists()
    }

    // ── Version 1.3 ──

    /** Everything at once: measurements shown, a prescription to renew, a grown-up companion. */
    private fun richState(): AppState {
        val base = sleepState().let { s -> s.copy(treatments = s.treatments + ibu, prnIntakes = prnState().prnIntakes) }
        val m = mutableListOf<com.medipote.app.data.Measurement>()
        for (i in 0 until 20) {
            val d = today.minusDays(i.toLong() * 3 / 2)
            val sys = listOf(128, 134, 141, 126, 138, 131, 145, 129)[i % 8].toDouble()
            m += com.medipote.app.data.Measurement("bp$i", com.medipote.app.data.MeasureType.BLOOD_PRESSURE, d.atTime(if (i % 2 == 0) 8 else 20, 5), sys, sys - 50 + i % 5, pulse = 64 + i % 9)
            if (i % 3 == 0) m += com.medipote.app.data.Measurement("w$i", com.medipote.app.data.MeasureType.WEIGHT, d.atTime(7, 30), 66.8 - i * 0.07)
        }
        val ordo = com.medipote.app.data.HealthDocument(
            "ordo", "Ordonnance Dr Martin", com.medipote.app.data.DocCategory.PRESCRIPTION, today.minusMonths(3).plusDays(6),
            "ordo.jpg", "image/jpeg", validUntil = today.plusDays(5), remindDays = 10,
        )
        return base.copy(
            measurements = m.sortedByDescending { it.at },
            documents = listOf(ordo),
            settings = base.settings.copy(measures = setOf(com.medipote.app.data.MeasureType.BLOOD_PRESSURE, com.medipote.app.data.MeasureType.WEIGHT)),
            game = base.game.copy(growth = 230),
        )
    }

    @Test
    @Config(qualifiers = "w400dp-h2600dp-xhdpi")
    fun homeV13() {
        render("home_v13") { HomeScreen(richState(), today.atTime(12, 10), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText("Ordonnance à renouveler").assertExists()
        compose.onNodeWithText("Mes mesures").assertExists()
        compose.onNodeWithText("🌱 Fleur").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun measuresTab() {
        render("measures") { com.medipote.app.ui.measures.MeasuresScreen(richState(), now, {}, {}, {}) }
        compose.onNodeWithText("Tension").assertExists()
        compose.onNodeWithText("Poids").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h2600dp-xhdpi")
    fun measureDetail() {
        render("measure_detail") {
            com.medipote.app.ui.measures.MeasureDetailScreen(richState(), com.medipote.app.data.MeasureType.BLOOD_PRESSURE, now, {}, {}, {})
        }
        compose.onNodeWithText("Dans les repères").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1200dp-xhdpi")
    fun measureEditor() {
        val state = richState()
        val m = state.measurements.first { it.type == com.medipote.app.data.MeasureType.BLOOD_PRESSURE && it.value >= 140 }
        render("measure_editor") { com.medipote.app.ui.measures.MeasureEditorScreen(state, m.type, m, now, {}, {}, {}) }
        compose.onNodeWithText("Au-dessus des repères", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun backupScreen() {
        val settings = Settings(backup = com.medipote.app.data.BackupSettings(auto = true, folderUri = "content://x", folderName = "Documents", encrypted = true, lastAuto = now.minusDays(2), lastManual = now.minusDays(9)))
        render("backup") { com.medipote.app.ui.settings.BackupScreen(settings, now, {}, {}, {}) }
        compose.onNodeWithText("Créer une sauvegarde").assertExists()
        compose.onNodeWithText("Dossier : Documents · changer").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun reportScreenAndPdf() {
        val state = richState()
        render("report") { com.medipote.app.ui.report.ReportScreen(state, now, {}, {}) }
        compose.onNodeWithText("Mesures de santé").assertExists()
        val report = com.medipote.app.domain.DoctorReports.build(state, now, 90, com.medipote.app.domain.DoctorReports.availableParts(state).toSet())
        // Robolectric has no native PDF writer: the same drawing code renders into bitmaps here.
        val pages = com.medipote.app.ui.report.ReportPdf.BitmapPages()
        com.medipote.app.ui.report.ReportPdf.draw(report, pages)
        org.junit.Assert.assertTrue(pages.bitmaps.size in 2..6)
        pages.bitmaps.forEachIndexed { i, b -> save("report_page${i + 1}", b) }
    }

    @Test
    @Config(qualifiers = "w400dp-h2600dp-xhdpi")
    fun editorWithStepsAndRhythm() {
        val t = Treatment(
            "pred", null, "Prednisolone", "20 mg", DoseUnit.TABLET, listOf(DoseTime(8 * 60, 2.0)),
            startDate = today, endDate = today.plusDays(13), everyDays = 2,
            steps = listOf(com.medipote.app.data.DoseStep(today.plusDays(4), 1.0), com.medipote.app.data.DoseStep(today.plusDays(8), 0.5)),
        )
        render("editor_steps") { TreatmentEditorScreen(existing = t, drug = null, customName = "", allergies = emptyList(), today = today, onSave = {}, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("Ajouter un palier").assertExists()
        compose.onNodeWithText("Tous les X jours").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h3400dp-xhdpi")
    fun settingsV13() {
        render("settings_v13") {
            SettingsScreen(
                richState().settings, "Pilou", notificationsAllowed = true, exactAlarmsAllowed = true, dynamicColorAvailable = true,
                onBack = {}, onChange = {}, onRequestNotifications = {}, onRequestExactAlarms = {},
                onRenamePet = {}, onOpenProfile = {}, onReset = {}, quietReminders = 1, lockEnabled = true, lastBackup = now.minusDays(2),
            )
        }
        compose.onNodeWithText("Verrouiller l'appli").assertExists()
        compose.onNodeWithText("Rappels sans pop-up").assertExists()
        compose.onNodeWithText("Mesures de santé").assertExists()
    }

    @Test
    fun petStages() {
        val size = 240
        val bitmap = Bitmap.createBitmap(size * 5, size, Bitmap.Config.ARGB_8888)
        com.medipote.app.domain.Stage.entries.forEachIndexed { i, stage ->
            val one = com.medipote.app.widget.renderPet(com.medipote.app.domain.PetMood.HAPPY, mapOf("COLOR" to "color_mint", "BACKGROUND" to "bg_meadow"), size, size, stage)
            android.graphics.Canvas(bitmap).drawBitmap(one, (i * size).toFloat(), 0f, null)
        }
        save("pet_stages", bitmap)
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
