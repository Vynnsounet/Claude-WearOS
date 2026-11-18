package com.medipote.app

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.medipote.app.ui.CardHub
import com.medipote.app.ui.HubCard
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
import androidx.compose.ui.graphics.luminance
import com.medipote.app.ui.theme.withSeasonTint
import com.medipote.app.data.Season
import androidx.compose.foundation.layout.padding
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
        // No BMI: weight and height are shown without any verdict.
        compose.onNodeWithText("IMC", substring = true).assertDoesNotExist()
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
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun treatmentEditor() {
        render("editor") {
            TreatmentEditorScreen(
                existing = null, drug = DrugGuide.byId("amoxicilline"), customName = "",
                allergies = profile.allergies, today = today, onSave = {}, onDelete = {}, onBack = {},
            )
        }
        compose.onNodeWithText("Attention : tu as signalé une allergie", substring = true).assertExists()
        // Secondary fields are folded for a new treatment.
        compose.onNodeWithText("Suivre mon stock").assertDoesNotExist()
        compose.onNodeWithText("Plus d'options").performScrollTo().performClick()
        compose.onNodeWithText("Suivre mon stock").assertExists()
    }

    @Test
    fun shop() {
        render("shop") { ShopScreen(sampleState().game, {}, {}, animate = false) }
        compose.onNodeWithText("Nœud").assertExists()
    }

    private fun shot(name: String) = save(name, compose.onRoot().captureToImage().asAndroidBitmap())

    @Test
    @Config(qualifiers = "w400dp-h2600dp-xhdpi")
    fun stats() {
        // One subject per sub-tab, the overview first; the period is shared.
        render("stats") { StatsScreen(sampleState(), now) }
        compose.onNodeWithText("📊  Vue d'ensemble").assertExists()
        compose.onNodeWithText("💊  Traitements").assertExists()
        compose.onNodeWithText("🌙  Sommeil").assertExists()
        compose.onNodeWithText("😊  Bien-être").assertExists()
        // No symptom tab until the symptoms are turned on.
        compose.onNodeWithText("🌡️  Symptômes").assertDoesNotExist()
        compose.onNodeWithText("Sommeil").assertExists()
        compose.onNodeWithText("Moral").assertExists()
        compose.onNodeWithText("14 derniers jours").assertDoesNotExist() // that is the treatments page
        compose.onNodeWithText("💊  Traitements").performScrollTo().performClick()
        compose.onNodeWithText("14 derniers jours").assertExists()
        shot("stats_treatments")
        compose.onNodeWithText("🌙  Sommeil").performScrollTo().performClick()
        shot("stats_sleep")
        compose.onNodeWithText("😊  Bien-être").performScrollTo().performClick()
        shot("stats_wellbeing")
    }

    @Test
    @Config(qualifiers = "w400dp-h3000dp-xhdpi")
    fun statsSymptomsTab() {
        val state = wellState().let { s ->
            // A little sleep to compare with: short nights go with strong fatigue.
            val nights = (1..8).map { back ->
                val d = today.minusDays(back.toLong())
                com.medipote.app.data.SleepNight(d, d.minusDays(1).atTime(23, 0), 0, d.minusDays(1).atTime(23, 0).plusHours(if (back % 2 == 0) 5 else 8))
            }
            s.copy(sleep = nights)
        }
        var manage = 0
        render("stats_symptoms") { StatsScreen(state, now, onManageSymptoms = { manage++ }) }
        compose.onNodeWithText("Jours difficiles").assertExists()
        compose.onNodeWithText("Petites victoires").assertExists()
        compose.onNodeWithText("🌡️  Symptômes").performScrollTo().performClick()
        compose.onNodeWithText("😴  Fatigue").assertExists()
        compose.onAllNodesWithText("force moyenne").onFirst().assertExists()
        compose.onNodeWithText("Liens avec ton sommeil").assertExists()
        compose.onNodeWithText("moins de 6 h", substring = true).assertExists()
        shot("stats_symptoms_tab")
    }

    @Test
    @Config(qualifiers = "w400dp-h3200dp-xhdpi")
    fun statsCycleTab() {
        val starts = listOf(LocalDate.of(2026, 6, 20), LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17), LocalDate.of(2026, 9, 13), LocalDate.of(2026, 10, 18))
        val info = CycleInfo(starts, 28, 5)
        var state = sampleState().copy(profile = profile.copy(cycle = info))
        // Flow for five days at each start; the moral drops in the luteal phase; a few cycle symptoms.
        val extra = starts.flatMap { s -> (0 until 5).map { com.medipote.app.data.DayEntry(s.plusDays(it.toLong()), flow = if (it < 2) com.medipote.app.data.Flow.HEAVY else com.medipote.app.data.Flow.LIGHT, cycleSymptoms = if (it == 0) listOf("Crampes", "Fatigue") else emptyList()) } }
        val moods = generateSequence(starts.first()) { it.plusDays(1) }.takeWhile { it <= today }.mapNotNull { d ->
            val phase = com.medipote.app.domain.CycleStats.phaseOn(info, d) ?: return@mapNotNull null
            if (extra.any { it.date == d }) null else com.medipote.app.data.DayEntry(d, mental = if (phase == com.medipote.app.domain.CyclePhase.LUTEAL) 2 else 4, physical = 3)
        }.toList()
        state = state.copy(journal = (extra + moods).sortedByDescending { it.date })
        render("stats_cycle") { StatsScreen(state, now) }
        // The overview carries the cycle too; the cycle page ignores the period selector.
        compose.onNodeWithText("Cycle moyen").assertExists()
        compose.onNodeWithText("🩸  Cycle").performScrollTo().performClick()
        compose.onNodeWithText("Durée de tes cycles").assertExists()
        compose.onNodeWithText("Selon la phase").assertExists()
        compose.onNodeWithText("Flux").assertExists()
        compose.onNodeWithText("7 j").assertDoesNotExist()
        compose.onNodeWithText("Indicatif", substring = true).assertExists()
        shot("stats_cycle_tab")
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xhdpi")
    fun statsHasNoCycleTabWithoutACycle() {
        val s = sampleState().let { it.copy(profile = it.profile!!.copy(menstruating = false)) }
        render("stats_no_cycle") { StatsScreen(s, now) }
        compose.onNodeWithText("🩸  Cycle").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xhdpi")
    fun statsSymptomsTabWithNothingFollowed() {
        val on = sampleState().let { it.copy(settings = it.settings.copy(wellbeing = com.medipote.app.data.WellbeingSettings(enabled = true))) }
        var manage = 0
        render("stats_symptoms_none") { StatsScreen(on, now, onManageSymptoms = { manage++ }) }
        compose.onNodeWithText("🌡️  Symptômes").performScrollTo().performClick()
        compose.onNodeWithText("Choisir mes symptômes").performClick()
        org.junit.Assert.assertEquals(1, manage)
    }

    @Test
    @Config(qualifiers = "w400dp-h1800dp-xhdpi")
    fun statsWellbeingShowsSmallWinsWithoutPressure() {
        var s = wellState()
        s = com.medipote.app.domain.Wellbeing.win(s, "tea", "Me préparer une boisson chaude", 5, now).state
        s = com.medipote.app.domain.Wellbeing.win(s, "breath", "Faire 5 respirations lentes", 5, now.minusDays(2)).state
        render("stats_wins") { StatsScreen(s, now) }
        compose.onNodeWithText("😊  Bien-être").performScrollTo().performClick()
        compose.onNodeWithText("💛  Petites victoires").assertExists()
        compose.onNodeWithText("•  Me préparer une boisson chaude").assertExists()
        // Nothing shown as missing.
        compose.onAllNodesWithText("manqu", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("objectif non atteint", substring = true, ignoreCase = true).assertCountEquals(0)
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
        // The scoring rules are folded: one tap shows them.
        compose.onNodeWithText("Verre d'eau (même après l'objectif, jusqu'au double)").assertDoesNotExist()
        compose.onNodeWithText("Points, série et compagnon : le barème").performScrollTo().performClick()
        compose.onNodeWithText("Verre d'eau (même après l'objectif, jusqu'au double)").assertExists()
    }


    @Test
    fun journal() {
        // The Suivi page is a list of airy cards, each with a live line; a card opens in place.
        render("journal") {
            CardHub(
                "Suivi",
                listOf(
                    HubCard("suivi:journal", "📖", "Journal", "Noté aujourd'hui"), HubCard("suivi:sleep", "🌙", "Sommeil", "Cette nuit : 7 h 30"),
                    HubCard("suivi:measures", "🩺", "Mesures", "tension"), HubCard("suivi:cycle", "🩸", "Cycle", "Règles, cycle et protections"),
                ),
                emptySet(), {}, {}, {},
            ) { JournalScreen(sampleState().journal, today, onOpenDay = {}) }
        }
        compose.onNodeWithText("Sommeil").assertExists()
        compose.onNodeWithText("Cette nuit : 7 h 30").assertExists()
        compose.onNodeWithText("Mesures").assertExists()
        compose.onNodeWithText("Journal").performClick()
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
            CardHub("Soins", listOf(HubCard("soins:treatments", "💊", "Traitements"), HubCard("soins:agenda", "📅", "Agenda")), emptySet(), {}, {}, {}) { AgendaScreen(sampleState().appointments, now, {}, {}) }
        }
        compose.onNodeWithText("Agenda").performClick()
        compose.onNodeWithText("Dentiste").assertExists()
    }

    @Test
    fun appointmentEditor() {
        render("appointment_editor") { AppointmentEditorScreen(sampleState().appointments.first(), today, {}, {}, {}) }
        compose.onNodeWithText("La veille").assertExists()
    }

    @Test
    fun documentsEmpty() {
        render("documents") { CardHub("Soins", listOf(HubCard("soins:agenda", "📅", "Agenda"), HubCard("soins:docs", "📂", "Carnet de santé")), emptySet(), {}, {}, {}) { DocumentsScreen(emptyList(), {}, {}, {}) } }
        compose.onNodeWithText("Carnet de santé").performClick()
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
            CardHub("Soins", listOf(HubCard("soins:treatments", "💊", "Traitements"), HubCard("soins:pill", "🔁", "Pilule"), HubCard("soins:agenda", "📅", "Agenda"), HubCard("soins:docs", "📂", "Carnet de santé")), emptySet(), {}, {}, {}) { PillScreen(state, now, {}, {}, {}, animate = false) }
        }
        compose.onNodeWithText("Pilule").performClick()
        compose.onNodeWithText("Comprimé 11 sur 21").assertExists()
    }

    @Test
    fun pillBreakWeek() {
        val onBreak = pill.copy(pill = pill.pill!!.copy(packStart = today.minusDays(23)))
        val state = withPill(onBreak)
        render("pill_break", ThemeMode.DARK) {
            CardHub("Soins", listOf(HubCard("soins:treatments", "💊", "Traitements"), HubCard("soins:pill", "🔁", "Pilule"), HubCard("soins:agenda", "📅", "Agenda"), HubCard("soins:docs", "📂", "Carnet de santé")), emptySet(), {}, {}, {}) { PillScreen(state, now, {}, {}, {}, animate = false) }
        }
        compose.onNodeWithText("Pilule").performClick()
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
        // Cards are compact: the limits show once the card is touched.
        compose.onNodeWithText("Max 3 / 24 h · 6 h entre deux prises").assertDoesNotExist()
        compose.onNodeWithText("Ibuprofène 400 mg").performClick()
        compose.onNodeWithText("Max 3 / 24 h · 6 h entre deux prises").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("Si besoin"), useUnmergedTree = true).onFirst().assertExists()
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
        compose.onNodeWithText("💊  Traitements").performScrollTo().performClick()
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
                quality = 2 + (i * 5 % 4),
                rested = if (weekend) 4 + i % 2 else 2 + (i * 7 % 3), pleasant = 3 + i % 3,
                tags = listOfNotNull("Écrans avant de dormir".takeIf { i % 3 == 0 }, "Caféine".takeIf { i % 5 == 1 }),
            )
        }
        return sampleState().copy(sleep = nights)
    }

    @Test
    fun awakeTimeStandsOutFromSleepWhateverTheAccent() {
        val sleepers = mutableListOf<Pair<String, androidx.compose.material3.ColorScheme>>()
        for (dark in listOf(false, true)) {
            for (accent in Accent.entries) sleepers += "$accent dark=$dark" to com.medipote.app.ui.theme.appColorScheme(accent, com.medipote.app.data.Look(), dark)
            // Violets and other colours picked by hand.
            for (seed in listOf(0xFF7E57C2, 0xFF6750A4, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFFD97757, 0xFF00897B, 0xFFFF9800, 0xFF000000, 0xFFFFFFFF)) {
                sleepers += "custom ${seed.toString(16)} dark=$dark" to com.medipote.app.ui.theme.appColorScheme(Accent.MINT, com.medipote.app.data.Look(customColor = seed), dark)
            }
        }
        sleepers.forEach { (name, scheme) ->
            val awake = com.medipote.app.ui.sleep.distinctFrom(scheme.primary, scheme.tertiary)
            val gap = com.medipote.app.ui.sleep.colorDistance(scheme.primary, awake)
            org.junit.Assert.assertTrue("$name: awake colour too close to sleep ($gap)", gap >= com.medipote.app.ui.sleep.MIN_AWAKE_DISTANCE)
        }
    }

    @Test
    @Config(qualifiers = "w400dp-h700dp-xhdpi")
    fun sleepTimelineInPurple() {
        val night = sleepState().sleep.first { it.awakenings.size >= 2 }
        render("sleep_purple", accent = Accent.LAVENDER) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                com.medipote.app.ui.sleep.NightTimeline(night)
            }
        }
    }

    private fun napState(timer: Boolean = false): AppState {
        val base = sleepState()
        val naps = listOf(
            com.medipote.app.data.Nap("n1", today.atTime(14, 10), 25, com.medipote.app.data.NapFeeling.FRESH),
            com.medipote.app.data.Nap("n2", today.minusDays(1).atTime(15, 30), 40),
            com.medipote.app.data.Nap("n3", today.minusDays(3).atTime(13, 45), 20, com.medipote.app.data.NapFeeling.FOGGY),
        )
        return base.copy(naps = naps, napSince = if (timer) now.minusMinutes(12) else null)
    }

    @Test
    @Config(qualifiers = "w400dp-h2600dp-xhdpi")
    fun sleepTabWithNaps() {
        render("sleep_naps") { com.medipote.app.ui.sleep.SleepScreen(napState(), now, {}, {}) }
        compose.onNodeWithText("😴 Je m'endors").assertExists()
        compose.onNodeWithText("Noter une sieste").assertExists()
        // Today's nap under the night, and the stats apart from the night's figures.
        compose.onNodeWithText("Sieste · 14:10 · 25 min").assertExists()
        compose.onNodeWithText("😴 Siestes").assertExists()
        compose.onNodeWithText("Voir mes siestes").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1000dp-xhdpi")
    fun sleepTabWithARunningNap() {
        var woke = 0
        render("sleep_nap_timer") { com.medipote.app.ui.sleep.SleepScreen(napState(timer = true), now, {}, {}, onNapWake = { woke++ }) }
        compose.onNodeWithText("Sieste en cours").assertExists()
        compose.onNodeWithText("12 min").assertExists()
        compose.onNodeWithText("😴 Je m'endors").assertDoesNotExist()
        compose.onNodeWithText("Je me réveille").performClick()
        org.junit.Assert.assertEquals(1, woke)
    }

    @Test
    @Config(qualifiers = "w400dp-h1800dp-xhdpi")
    fun homeWithARunningNapAndQuickAddNap() {
        val state = napState(timer = true)
        render("home_nap") { HomeScreen(state, now, 0, 0, {}, {}, {}, {}, animate = false, quickAdd = com.medipote.app.ui.home.QuickAddActions()) }
        compose.onNodeWithText("Sieste en cours").assertExists()
        compose.onNodeWithText("Je me réveille").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1100dp-xhdpi")
    fun quickAddOffersNaps() {
        var started = 0
        render("quick_add_nap") {
            com.medipote.app.ui.home.QuickAddContent(
                napState(), now, com.medipote.app.ui.home.QuickAddActions(onNapStart = { started++ }), onDone = {},
                initial = com.medipote.app.ui.home.QuickPage.NAP,
            )
        }
        compose.onNodeWithText("Je m'endors maintenant").performClick()
        org.junit.Assert.assertEquals(1, started)
        compose.onNodeWithText("Noter une sieste déjà faite").assertExists()
    }

    private fun supportState() = sampleState().copy(
        settings = sampleState().settings.copy(
            support = com.medipote.app.data.SupportSettings(
                contacts = listOf(com.medipote.app.data.TrustedContact("Camille", "06 12 34 56 78"), com.medipote.app.data.TrustedContact("Maman", "01 23 45 67 89")),
                professionalName = "Dr Martin", professionalPhone = "04 11 22 33 44",
            ),
        ),
        support = com.medipote.app.data.SupportBox(
            messageToSelf = "Ça finit toujours par passer. Tu as déjà traversé pire.",
            comfort = listOf("Ma playlist douce", "Une marche au bord de l'eau", "Un thé et un plaid"),
            signs = listOf("Je ne dors plus", "Je ne réponds plus aux messages"),
            helps = listOf("Respirer", "Appeler Camille"),
            matters = "Mes proches, mon chat, mes projets.",
            thoughts = listOf(com.medipote.app.data.ThoughtNote("t1", today.atTime(9, 5), "Je me sens dépassé, tout est lourd.")),
        ),
    )

    @Test
    @Config(qualifiers = "w400dp-h2400dp-xhdpi")
    fun supportScreen() {
        var called: String? = null
        var mood: Int? = null
        render("support") {
            com.medipote.app.ui.support.SupportScreen(
                supportState(), now,
                com.medipote.app.ui.support.SupportActions(onCall = { called = it }, onMood = { mood = it }),
            )
        }
        compose.onNodeWithText("Je suis là.").assertExists()
        // The national line is always one tap away, at the top.
        compose.onNodeWithText("📞 Appeler le 3114").performClick()
        org.junit.Assert.assertEquals("3114", called)
        compose.onNodeWithText("Camille").assertExists()
        compose.onNodeWithText("Respirer").assertExists()
        // The "gorge nouée" tips live with the feelings now, not on this screen.
        compose.onNodeWithText("Gorge nouée ?").assertDoesNotExist()
        compose.onNodeWithText("Autres numéros").performScrollTo().performClick()
        compose.onNodeWithText("15 · SAMU").assertExists()
        compose.onNodeWithText("Dr Martin").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1100dp-xhdpi")
    fun breathingScreen() {
        render("support_breathe") { com.medipote.app.ui.support.BreathingScreen(vibrate = false, onBack = {}, animate = false) }
        compose.onNodeWithText("Inspire").assertExists()
        compose.onNodeWithText("Apaisante").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xhdpi")
    fun groundingScreen() {
        var breathe = 0
        render("support_ground") { com.medipote.app.ui.support.GroundingScreen(onBack = {}, onBreathe = { breathe++ }) }
        compose.onNodeWithText("choses que tu vois").assertExists()
        repeat(4) { compose.onNodeWithText("Suivant").performClick() }
        compose.onNodeWithText("J'ai fini").performClick()
        compose.onNodeWithText("Bravo.").assertExists()
        compose.onNodeWithText("🌬️ Respirer un peu").performClick()
        org.junit.Assert.assertEquals(1, breathe)
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun thoughtsScreen() {
        render("support_thoughts") { com.medipote.app.ui.support.ThoughtsScreen(supportState().support, onKeep = {}, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("Garder en privé").assertExists()
        compose.onNodeWithText("Ce que j'ai gardé").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun comfortScreen() {
        render("support_comfort") { com.medipote.app.ui.support.ComfortScreen(supportState().support, onChange = {}, onBack = {}) }
        compose.onNodeWithText("Ma playlist douce").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1900dp-xhdpi")
    fun planScreen() {
        render("support_plan") { com.medipote.app.ui.support.PlanScreen(supportState(), onChange = {}, onContacts = {}, onBack = {}) }
        compose.onNodeWithText("Je ne dors plus").assertExists()
        compose.onNodeWithText("Camille, Maman").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun supportSettingsScreen() {
        render("support_settings") { com.medipote.app.ui.support.SupportSettingsScreen(supportState().settings.support, onChange = {}, onBack = {}) }
        compose.onNodeWithText("Mes personnes de confiance").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h2000dp-xhdpi")
    fun aVeryBadMoodOffersSupportGentlyOnHome() {
        val state = sampleState().let { it.copy(journal = it.journal.filterNot { e -> e.date == today } + com.medipote.app.data.DayEntry(today, mental = 1)) }
        var opened = 0
        render("home_support_nudge") { HomeScreen(state, now, 0, 0, {}, {}, {}, {}, animate = false, onOpenSupport = { opened++ }) }
        compose.onNodeWithText("Ça a l'air dur.").performClick()
        org.junit.Assert.assertEquals(1, opened)
        // The heart in the top bar, too.
        compose.onNodeWithContentDescription("Ça ne va pas : soutien").performClick()
        org.junit.Assert.assertEquals(2, opened)
    }

    @Test
    @Config(qualifiers = "w400dp-h2000dp-xhdpi")
    fun supportEntryPointsCanBeTurnedOff() {
        val base = sampleState().let { it.copy(journal = it.journal.filterNot { e -> e.date == today } + com.medipote.app.data.DayEntry(today, mental = 1)) }
        val off = base.copy(settings = base.settings.copy(support = base.settings.support.copy(suggestOnLowMood = false, homeButton = false)))
        render("home_support_off") { HomeScreen(off, now, 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText("Ça a l'air dur.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Ça ne va pas : soutien").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w400dp-h1000dp-xhdpi")
    fun napEditor() {
        var saved: com.medipote.app.data.Nap? = null
        render("nap_editor") { com.medipote.app.ui.sleep.NapEditorScreen(null, null, null, now, onSave = { saved = it }, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("Noter une sieste").assertExists()
        // 30 minutes by default: the big figure and its shortcut.
        compose.onAllNodesWithText("30 min").assertCountEquals(2)
        compose.onNodeWithText("😊 En forme").performClick()
        // 20 minutes from a start 30 minutes ago: it has ended, so it can be saved.
        compose.onNodeWithText("20 min").performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        org.junit.Assert.assertEquals(20, saved?.minutes)
        org.junit.Assert.assertEquals(com.medipote.app.data.NapFeeling.FRESH, saved?.feeling)
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xhdpi")
    fun napEditorRefusesANapEndingInTheFuture() {
        // Started 10 minutes ago, 60 minutes long: it would end in the future.
        render("nap_editor_future") {
            com.medipote.app.ui.sleep.NapEditorScreen(null, now.minusMinutes(10), 60, now, onSave = {}, onDelete = {}, onBack = {})
        }
        compose.onNodeWithText("Une sieste ne peut pas finir dans le futur", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h3000dp-xhdpi")
    fun sleepTab() {
        val state = sleepState()
        render("sleep") { com.medipote.app.ui.sleep.SleepScreen(state, now, {}, {}) }
        compose.onNodeWithText("Nuit du 11 au 12 novembre").assertExists()
        // Detailed figures and past nights are folded.
        compose.onNodeWithText("Voir mes nuits précédentes").assertExists()
        compose.onNodeWithText("Plus de détails").performScrollTo().performClick()
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
        // In the morning, the ritual asks about the night (no separate "Bien dormi ?" card then).
        compose.onNodeWithText("Rituel du matin").performScrollTo().assertExists()
        compose.onNodeWithText("Bien dormi ?").assertDoesNotExist()
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

    @Test
    @Config(qualifiers = "w400dp-h2400dp-xhdpi")
    fun homeCompanionUnderThePhrase() {
        render("home_order") { HomeScreen(motivationState(), today.atTime(15, 0), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText(com.medipote.app.domain.DailyPhrases.of(today, Season.AUTUMN), substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1400dp-xhdpi")
    fun homeAtNight() {
        render("home_night") { HomeScreen(motivationState(), today.atTime(21, 30), 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText(com.medipote.app.domain.DailyPhrases.of(today, Season.AUTUMN), substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h880dp-xhdpi")
    fun streakTitleIsReadableOnTheDarkThemeWithoutASurfaceAround() {
        // As in the real app, where the celebration is a dialog with no Surface around it.
        val reward = com.medipote.app.domain.CheckInReward(5, 25, 0, 0, 12)
        compose.setContent {
            MediPoteTheme(themeMode = ThemeMode.DARK) {
                com.medipote.app.ui.home.StreakCelebration(reward, "Pilou", today, animate = false) {}
            }
        }
        compose.waitForIdle()
        val title = compose.onNodeWithText("Ça continue", substring = true)
        title.assertExists()
        val shot = title.captureToImage().asAndroidBitmap()
        save("streak_dark_title", shot)
        // The brightest pixel of the words (not the emoji at the end) is clearly light, not black on black.
        var best = 0f
        for (x in 0 until shot.width * 6 / 10 step 2) for (y in 0 until shot.height step 2) {
            val c = shot.getPixel(x, y)
            best = maxOf(best, (android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)) / (3f * 255f))
        }
        org.junit.Assert.assertTrue("title too dark: $best", best > 0.8f)
    }

    /** One picture of the companion for each season and festival, on the meadow and on the room. */
    private fun ambianceSheet(name: String, background: String?, items: List<com.medipote.app.domain.Ambiance>, time: com.medipote.app.domain.TimeOfDay = com.medipote.app.domain.TimeOfDay.DAY) {
        val size = 260
        val bitmap = Bitmap.createBitmap(size * 4, size * ((items.size + 3) / 4), Bitmap.Config.ARGB_8888)
        items.forEachIndexed { i, a ->
            val equipped = mapOf("COLOR" to "color_mint") + (background?.let { mapOf("BACKGROUND" to it) } ?: emptyMap())
            val one = com.medipote.app.widget.renderPet(com.medipote.app.domain.PetMood.HAPPY, equipped, size, size, com.medipote.app.domain.Stage.BUD, a, time)
            android.graphics.Canvas(bitmap).drawBitmap(one, ((i % 4) * size).toFloat(), ((i / 4) * size).toFloat(), null)
        }
        save(name, bitmap)
    }

    @Test
    fun seasonsOnTheMeadow() {
        ambianceSheet("seasons_meadow", "bg_meadow", Season.entries.map { com.medipote.app.domain.Ambiance(it) })
    }

    @Test
    fun festivalsAtNight() {
        ambianceSheet("festivals_night", "bg_meadow", com.medipote.app.data.Festival.entries.map { com.medipote.app.domain.Ambiance(Season.WINTER, it) }, com.medipote.app.domain.TimeOfDay.NIGHT)
    }

    @Test
    fun seasonsInTheRoom() {
        ambianceSheet("seasons_room", null, Season.entries.map { com.medipote.app.domain.Ambiance(it) })
    }

    @Test
    fun festivalsOnTheMeadow() {
        ambianceSheet("festivals_meadow", "bg_meadow", com.medipote.app.data.Festival.entries.map { com.medipote.app.domain.Ambiance(Season.WINTER, it) })
    }

    @Test
    fun festivalsInTheRoom() {
        ambianceSheet("festivals_room", null, com.medipote.app.data.Festival.entries.map { com.medipote.app.domain.Ambiance(Season.AUTUMN, it) })
    }

    @Test
    fun theSeasonalTintStaysFaintAndKeepsTheTextReadable() {
        for (dark in listOf(false, true)) for (season in Season.entries) {
            val base = com.medipote.app.ui.theme.appColorScheme(Accent.MINT, com.medipote.app.data.Look(), dark)
            val tinted = with(base) { withSeasonTint(season, dark) }
            // The backgrounds move by a few percent at most…
            org.junit.Assert.assertTrue(com.medipote.app.ui.sleep.colorDistance(base.background, tinted.background) < 40f)
            // …and the text on them keeps its contrast.
            org.junit.Assert.assertTrue("$season dark=$dark", contrast(tinted.onSurface, tinted.surface) >= 7f)
            org.junit.Assert.assertTrue("$season dark=$dark", contrast(tinted.onSurfaceVariant, tinted.surfaceContainerLow) >= 4.5f)
            org.junit.Assert.assertTrue("$season dark=$dark", contrast(tinted.onBackground, tinted.background) >= 7f)
        }
    }

    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun seasonsSettingsScreen() {
        val settings = com.medipote.app.data.SeasonSettings(birthday = LocalDate.of(1990, 5, 20), petBirthday = LocalDate.of(2025, 3, 3))
        render("seasons_settings") { com.medipote.app.ui.settings.SeasonsScreen(settings, "Pilou", LocalDate.of(2025, 3, 3), today, {}, {}) }
        compose.onAllNodesWithText("Saisons et fêtes").assertCountEquals(2)
        compose.onNodeWithText("Noël").assertExists()
        compose.onNodeWithText("Le 3 mars, le jour où vous vous êtes rencontrés").assertExists()
    }

    @Test
    fun petSkyFollowsTheClock() {
        val size = 300
        val times = com.medipote.app.domain.TimeOfDay.entries
        val bitmap = Bitmap.createBitmap(size * times.size, size * 2, Bitmap.Config.ARGB_8888)
        times.forEachIndexed { i, time ->
            listOf("bg_meadow", "bg_beach").forEachIndexed { row, bg ->
                val image = androidx.compose.ui.graphics.ImageBitmap(size, size)
                androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                    androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(image), androidx.compose.ui.geometry.Size(size.toFloat(), size.toFloat()),
                ) {
                    with(com.medipote.app.ui.pet.PetDrawing) { background(bg, time) }
                    com.medipote.app.ui.pet.drawPetForTest(this)
                }
                android.graphics.Canvas(bitmap).drawBitmap(image.asAndroidBitmap(), (i * size).toFloat(), (row * size).toFloat(), null)
            }
        }
        save("pet_sky", bitmap)
        org.junit.Assert.assertEquals(com.medipote.app.domain.TimeOfDay.DAWN, com.medipote.app.domain.TimeOfDay.of(6))
        org.junit.Assert.assertEquals(com.medipote.app.domain.TimeOfDay.DAY, com.medipote.app.domain.TimeOfDay.of(8))
        org.junit.Assert.assertEquals(com.medipote.app.domain.TimeOfDay.DAY, com.medipote.app.domain.TimeOfDay.of(15))
        org.junit.Assert.assertEquals(com.medipote.app.domain.TimeOfDay.DUSK, com.medipote.app.domain.TimeOfDay.of(16))
        org.junit.Assert.assertEquals(com.medipote.app.domain.TimeOfDay.NIGHT, com.medipote.app.domain.TimeOfDay.of(19))
        org.junit.Assert.assertEquals(com.medipote.app.domain.TimeOfDay.NIGHT, com.medipote.app.domain.TimeOfDay.of(2))
    }

    // ── Version 1.4 ──

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun doseHistory() {
        // Yesterday: the evening doses were forgotten in the app.
        val state = sampleState().let { s -> s.copy(doseLogs = s.doseLogs.filterNot { it.scheduled.toLocalDate() == today.minusDays(1) && it.scheduled.hour >= 12 }) }
        render("dose_history") { com.medipote.app.ui.home.DoseHistoryScreen(state, today.minusDays(1), now, {}, { _, _ -> }, {}, {}) }
        compose.onNodeWithText("Hier").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("Je l'ai pris")).onFirst().assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("Annuler")).onFirst().assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h2400dp-xhdpi")
    fun homeFullyPersonalized() {
        val look = com.medipote.app.data.Look(
            customColor = 0xFFE0559F, headingFont = com.medipote.app.data.FontChoice.ELEGANT, corners = com.medipote.app.data.Corners.SOFT,
            backdrop = com.medipote.app.data.Backdrop.TINTED, avatar = "🌸", motto = "Un jour après l'autre, en douceur",
            homeOrder = listOf(com.medipote.app.data.HomeSection.DOSES, com.medipote.app.data.HomeSection.WATER, com.medipote.app.data.HomeSection.COMPANION),
        )
        val state = sampleState().let { s ->
            s.copy(
                settings = s.settings.copy(look = look),
                doseLogs = s.doseLogs.filterNot { it.scheduled.toLocalDate() == today.minusDays(1) && it.scheduled.hour == 20 },
            )
        }
        compose.setContent {
            MediPoteTheme(themeMode = ThemeMode.LIGHT, look = look) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    HomeScreen(state, now, 0, 0, {}, {}, {}, {}, animate = false)
                }
            }
        }
        compose.waitForIdle()
        save("home_custom", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("Bonjour Alex 🌸").assertExists()
        // The old personal motto is replaced by the phrase of the day.
        compose.onNodeWithText("Un jour après l'autre, en douceur", substring = true).assertDoesNotExist()
        compose.onNodeWithText(com.medipote.app.domain.DailyPhrases.of(today, Season.AUTUMN), substring = true).assertExists()
        compose.onNodeWithText("Hier : ", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun homePureBlack() {
        val look = com.medipote.app.data.Look(pureBlack = true, customColor = 0xFF4FC3F7, headingFont = com.medipote.app.data.FontChoice.TYPEWRITER, avatar = "🦊")
        compose.setContent {
            MediPoteTheme(themeMode = ThemeMode.DARK, look = look) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    HomeScreen(sampleState(ThemeMode.DARK).let { it.copy(settings = it.settings.copy(look = look)) }, now, 0, 0, {}, {}, {}, {}, animate = false)
                }
            }
        }
        compose.waitForIdle()
        save("home_black", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    @Config(qualifiers = "w400dp-h3800dp-xhdpi")
    fun appearanceScreen() {
        val settings = Settings(look = com.medipote.app.data.Look(customColor = 0xFF7E57C2, motto = "Je prends soin de moi", avatar = "🦋"))
        render("appearance") { com.medipote.app.ui.settings.AppearanceScreen(settings, "Alex", true, {}, {}) }
        compose.onNodeWithText("Police des titres").assertExists()
        compose.onNodeWithText("🏠  Choisir ce qui s'affiche sur l'accueil").assertExists()
        compose.onNodeWithText("Teinte").assertExists()
    }

    @Test
    fun streakCelebration() {
        val reward = com.medipote.app.domain.CheckInReward(12, 50, 0, 0, 12)
        render("streak") { com.medipote.app.ui.home.StreakCelebration(reward, "Pilou", today, animate = false) {} }
        compose.onNodeWithText("12").assertExists()
        compose.onNodeWithText("jours d'affilée").assertExists()
        compose.onNodeWithText("Encore 18 jours avant le palier de 30 jours (+100 pts)").assertExists()
    }

    @Test
    fun streakNewStartDark() {
        val reward = com.medipote.app.domain.CheckInReward(1, 5, 0, 9, 15)
        render("streak_restart", ThemeMode.DARK) { com.medipote.app.ui.home.StreakCelebration(reward, "Pilou", today, animate = false) {} }
        compose.onNodeWithText("Nouveau départ 💪").assertExists()
    }

    @Test
    fun generatedColorsKeepTextReadable() {
        // Every accent, and a few custom colours (yellow is the hardest): white text on the main
        // colour in light mode, and the main colour on the dark background, stay readable.
        val seeds = com.medipote.app.data.Accent.entries.drop(6).map { it.seed } + listOf(0xFFFFEB3B, 0xFF00E5FF, 0xFFC6FF00, 0xFF9E9E9E)
        for (seed in seeds) {
            val light = com.medipote.app.ui.theme.appColorScheme(com.medipote.app.data.Accent.MINT, com.medipote.app.data.Look(customColor = seed), false)
            val dark = com.medipote.app.ui.theme.appColorScheme(com.medipote.app.data.Accent.MINT, com.medipote.app.data.Look(customColor = seed), true)
            val onLight = androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.WHITE, light.primary.toArgb())
            val onDark = androidx.core.graphics.ColorUtils.calculateContrast(dark.primary.toArgb(), dark.background.toArgb())
            org.junit.Assert.assertTrue("${seed.toString(16)} light $onLight", onLight >= 4.5)
            org.junit.Assert.assertTrue("${seed.toString(16)} dark $onDark", onDark >= 4.5)
        }
    }

    // ── Version 1.5 ──

    /** A month of use: nights, journal with answers, a few badges, rituals and challenges. */
    private fun motivationState(): AppState {
        val base = sleepState()
        val journal = (0 until 40).filter { it % 3 != 2 }.map { i ->
            val d = today.minusDays(i.toLong())
            DayEntry(
                d, physical = 2 + (i * 3 % 4), mental = 1 + (i * 7 % 5),
                reflection = listOf("Une balade au soleil", "Un bon repas avec Sam", "", "Un appel de ma sœur")[i % 4],
            )
        }
        var state = base.copy(
            journal = journal,
            game = base.game.copy(
                growth = 120,
                waterRewardDays = (1..4).map { today.minusDays(it.toLong()) }.toSet(),
                cuddleDays = (0..3).map { today.minusDays(it.toLong()) }.toSet(),
                ritualsDone = (1..3).map { com.medipote.app.domain.Rituals.key(today.minusDays(it.toLong()), com.medipote.app.domain.RitualKind.MORNING) }.toSet(),
                bestStreak = 12,
            ),
        )
        state = com.medipote.app.domain.Badges.award(state, today).first
        return state
    }

    @Test
    @Config(qualifiers = "w400dp-h3000dp-xhdpi")
    fun homeV15() {
        render("home_v15") { HomeScreen(motivationState(), today.atTime(8, 0), 0, 0, {}, {}, {}, {}, animate = false, quickAdd = com.medipote.app.ui.home.QuickAddActions()) }
        compose.onNodeWithText("Noter", useUnmergedTree = true).assertExists()
        // Mood noted: folded into one line; the phrase of the day instead of a question.
        compose.onNodeWithText("Humeur du jour :", substring = true).assertExists()
        compose.onNodeWithText(com.medipote.app.domain.DailyPhrases.of(today, Season.AUTUMN), substring = true).assertExists()
        compose.onNodeWithText("Rituel du matin").assertExists()
        // Challenges and garden live in the companion space; home keeps a chip.
        compose.onNodeWithText("🎯 ", substring = true).assertExists()
        compose.onNodeWithText("Mon jardin").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w400dp-h1100dp-xhdpi")
    fun quickAddPanel() {
        val state = richState().let { it.copy(profile = it.profile?.copy(menstruating = true)) }
        var water = 0
        var mood: Int? = null
        render("quick_add") {
            com.medipote.app.ui.home.QuickAddContent(state, today.atTime(12, 10), com.medipote.app.ui.home.QuickAddActions(onWater = { water++ }, onMood = { mood = it }), onDone = {})
        }
        compose.onNodeWithText("Que veux-tu noter ?").assertExists()
        compose.onNodeWithText("Une protection").assertExists()
        compose.onNodeWithText("Un verre d'eau").performClick()
        org.junit.Assert.assertEquals(1, water)
        compose.onNodeWithText("Une protection").performClick()
        compose.onNodeWithText("🩲").assertExists()
        save("quick_add_product", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    @Config(qualifiers = "w400dp-h3600dp-xhdpi")
    fun badgesScreen() {
        val state = motivationState()
        render("badges") { com.medipote.app.ui.motivation.BadgesScreen(state, today, onBack = {}) }
        compose.onNodeWithText("Marchand de sable").assertExists()
        compose.onNodeWithText("/ ${com.medipote.app.domain.Badges.all.size} badges", substring = true).assertExists()
    }

    private val noRitualActions = com.medipote.app.ui.motivation.RitualActions({}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})

    @Test
    @Config(qualifiers = "w400dp-h1400dp-xhdpi")
    fun ritualMorning() {
        val state = motivationState().let { s -> s.copy(sleep = s.sleep.filterNot { it.date == today }) }
        render("ritual_morning") { com.medipote.app.ui.motivation.RitualScreen(state, com.medipote.app.domain.RitualKind.MORNING, today.atTime(7, 40), noRitualActions, animate = false) }
        compose.onNodeWithText("Enregistrer ma nuit").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1400dp-xhdpi")
    fun ritualEvening() {
        val state = motivationState().let { s -> s.copy(treatments = emptyList()) }
        render("ritual_evening") { com.medipote.app.ui.motivation.RitualScreen(state, com.medipote.app.domain.RitualKind.EVENING, today.atTime(21, 30), noRitualActions, animate = false) }
        compose.onNodeWithText("Un mot sur ta journée ? (facultatif)").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h2400dp-xhdpi")
    fun myDay() {
        val state = motivationState()
        render("my_day") { com.medipote.app.ui.motivation.MyDayScreen(state, today.minusDays(1), now, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Ma journée").assertExists()
        compose.onNodeWithText("Hier").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun yearInPixels() {
        render("year") { com.medipote.app.ui.motivation.YearScreen(motivationState(), now, {}, {}) }
        compose.onNodeWithText("2026").assertExists()
    }

    @Test
    fun weeklyRecapCard() {
        val state = motivationState()
        render("recap") {
            com.medipote.app.ui.motivation.RecapScreen(state, com.medipote.app.domain.Challenges.weekStart(today).minusWeeks(1), now, {}, {}, animate = false)
        }
        compose.onNodeWithText("Ta semaine".uppercase()).assertExists()
    }

    @Test
    fun badgeEarnedDialog() {
        render("badge_dialog") { com.medipote.app.ui.motivation.BadgeEarnedDialog(com.medipote.app.domain.Badges.byId("sleep_7")!!, {}, {}) }
        compose.onNodeWithText("Objet exclusif débloqué : Lucioles").assertExists()
    }

    @Test
    fun exclusiveItems() {
        val size = 240
        val ids = listOf("hat_flowercrown" to "HAT", "glasses_gold" to "GLASSES", "neck_star" to "NECK", "toy_trophy" to "TOY", "fx_fireflies" to "EFFECT", "bg_dawn" to "BACKGROUND")
        val bitmap = Bitmap.createBitmap(size * ids.size, size, Bitmap.Config.ARGB_8888)
        ids.forEachIndexed { i, (id, slot) ->
            val equipped = mapOf("COLOR" to "color_mint", "BACKGROUND" to if (slot == "BACKGROUND") id else "bg_meadow") + (if (slot != "BACKGROUND") mapOf(slot to id) else emptyMap())
            val one = com.medipote.app.widget.renderPet(com.medipote.app.domain.PetMood.HAPPY, equipped, size, size)
            android.graphics.Canvas(bitmap).drawBitmap(one, (i * size).toFloat(), 0f, null)
        }
        save("exclusive_items", bitmap)
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun companionSpace() {
        val state = motivationState()
        render("hub_companion") {
            CardHub("Moi", listOf(HubCard("moi:shop", "🛍️", "Boutique"), HubCard("moi:badges", "🏅", "Badges et défis")), emptySet(), {}, {}, {}) {
                com.medipote.app.ui.motivation.BadgesScreen(state, today, onBack = {}, embedded = true)
            }
        }
        // A page of cards; opening one shows its content under a back arrow. Earned badges first, the rest folded.
        compose.onNodeWithText("Boutique").assertExists()
        compose.onNodeWithText("Badges et défis").performClick()
        compose.onNodeWithText("Ma collection").assertExists()
        compose.onNodeWithText("à débloquer", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun bilanSpace() {
        val state = motivationState()
        render("hub_bilan") {
            CardHub("Moi", listOf(HubCard("moi:stats", "📊", "Statistiques"), HubCard("moi:day", "📋", "Jour par jour")), emptySet(), {}, {}, {}) {
                com.medipote.app.ui.motivation.MyDayScreen(state, today.minusDays(1), now, {}, {}, {}, {}, {}, embedded = true)
            }
        }
        compose.onNodeWithText("Jour par jour").assertExists()
        compose.onNodeWithText("Jour par jour").performClick()
        compose.onNodeWithContentDescription("Retour").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h2200dp-xhdpi")
    fun cycleWithPeriodCare() {
        val ibuprofen = Treatment(
            "ibu2", "ibuprofene", "Ibuprofène", "400 mg", DoseUnit.TABLET, emptyList(),
            startDate = today.minusDays(5), asNeeded = true,
        )
        val state = sampleState().let { s ->
            s.copy(
                treatments = s.treatments + ibuprofen,
                productSession = com.medipote.app.data.ProductSession(com.medipote.app.data.PeriodProduct.TAMPON, now.minusHours(3).minusMinutes(5), 4 * 60),
                prnIntakes = listOf(com.medipote.app.data.PrnIntake("i", "ibu2", now.minusHours(2), reason = "Règles douloureuses")),
            )
        }
        render("cycle_period_care") {
            CycleScreen(profile.cycle, state.journal, today, {}, {}, {}, embedded = true) {
                com.medipote.app.ui.cycle.PeriodCareCard(state, now, {}, {}, {}, { _, _ -> })
                com.medipote.app.ui.cycle.PeriodMedsCard(state, now, {}, {})
            }
        }
        compose.onNodeWithText("Mise en place à 09:05", substring = true).assertExists()
        compose.onNodeWithText("J'ai changé").assertExists()
        compose.onNodeWithText("Phloroglucinol (Spasfon)").assertExists()
        compose.onNodeWithText("Autre médicament").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1000dp-xhdpi")
    fun periodMedsOwnMedication() {
        val alprazolam = Treatment("alp", "alprazolam", "Alprazolam", "0,25 mg", DoseUnit.TABLET, emptyList(), startDate = today.minusDays(5), asNeeded = true)
        val own = Treatment("own", null, "Antadys", "100 mg", DoseUnit.TABLET, emptyList(), startDate = today.minusDays(1), asNeeded = true, forPeriod = true)
        val state = sampleState().let { it.copy(treatments = it.treatments + alprazolam + own) }
        render("period_meds_add") { com.medipote.app.ui.cycle.PeriodMedsCard(state, now, {}, {}) }
        compose.onNodeWithText("Antadys 100 mg").assertExists()
        compose.onNodeWithText("Alprazolam 0,25 mg").assertDoesNotExist()
        compose.onNodeWithText("Autre médicament").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xhdpi")
    fun periodMedsAddForm() {
        // A text field inside a dialog window never lets Robolectric go idle: the form is tested on its own.
        val alprazolam = Treatment("alp", "alprazolam", "Alprazolam", "0,25 mg", DoseUnit.TABLET, emptyList(), startDate = today.minusDays(5), asNeeded = true)
        var picked: String? = null
        var added: Triple<String, String, String?>? = null
        render("period_meds_add_dialog") {
            com.medipote.app.ui.cycle.AddPeriodMedDialog(listOf(alprazolam), { picked = it }, { n, s, d -> added = Triple(n, s, d) }, {}, asDialog = false)
        }
        compose.onNodeWithText("Nom").performTextInput("Spasfon")
        compose.onNodeWithText("Phloroglucinol").performClick()
        compose.onNodeWithText("Ajouter").performClick()
        org.junit.Assert.assertEquals(Triple("Phloroglucinol", "80 mg", "phloroglucinol"), added)
        compose.onNodeWithText("Alprazolam 0,25 mg").performClick()
        org.junit.Assert.assertEquals("alp", picked)
    }

    @Test
    fun periodCareNotStarted() {
        render("period_care_start") { com.medipote.app.ui.cycle.PeriodCareCard(sampleState(), now, {}, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("🩲 Culotte de règles").assertExists()
    }

    // ───────────── Symptoms, the gentle day, tasks cut into steps ─────────────

    private fun wellState(heavy: Boolean = true): AppState {
        var s = sampleState()
        s = s.copy(settings = s.settings.copy(wellbeing = com.medipote.app.data.WellbeingSettings(
            enabled = true,
            conditions = listOf(com.medipote.app.data.MyCondition("Hashimoto / hypothyroïdie", "hashimoto")),
            symptoms = listOf("fatigue", "anxiety", "body"),
            customMissions = listOf("Prendre un bain"),
        )))
        val w = com.medipote.app.domain.Wellbeing
        for (back in 1..6) s = w.setIntensity(s, today.minusDays(back.toLong()), "fatigue", if (back % 2 == 0) 4 else 2)
        s = w.setIntensity(s, today, "fatigue", if (heavy) 4 else 2)
        s = w.setIntensity(s, today, "anxiety", 2)
        return s
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun symptomsScreen() {
        val state = wellState()
        val set = mutableListOf<Pair<String, Int?>>()
        var advice: String? = null
        render("symptoms") {
            com.medipote.app.ui.wellbeing.SymptomsScreen(state, now, { id, v -> set += id to v }, { advice = it }, {}, {})
        }
        compose.onNodeWithText("Qu'est-ce que tu ressens aujourd'hui ?").assertExists()
        compose.onNodeWithText("Fatigue").assertExists()
        compose.onNodeWithText("fort").assertExists()
        // A strong symptom offers what can help; the same number touched again removes it.
        compose.onNodeWithText("💛  Voir ce qui peut aider").performClick()
        org.junit.Assert.assertEquals("fatigue", advice)
        compose.onNodeWithContentDescription("Fatigue, intensité 4 sur 5").performClick()
        compose.onNodeWithContentDescription("Anxiété, intensité 5 sur 5").performClick()
        org.junit.Assert.assertEquals(listOf("fatigue" to null, "anxiety" to 5), set)
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun symptomAdvice() {
        var soft = 0
        render("symptom_advice") { com.medipote.app.ui.wellbeing.SymptomAdviceScreen("fatigue", { soft++ }, {}, {}) }
        compose.onNodeWithText("Quand en parler à un médecin").assertExists()
        compose.onAllNodesWithText("appelle le 15", substring = true).onFirst().assertExists()
        compose.onNodeWithText("💛  Des petites missions pour aujourd'hui").performClick()
        org.junit.Assert.assertEquals(1, soft)
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun symptomAdviceAboutTheBody() {
        var support = 0
        render("symptom_body") { com.medipote.app.ui.wellbeing.SymptomAdviceScreen("body", {}, { support++ }, {}) }
        compose.onNodeWithText("Ce que je te souhaite d'entendre d'abord :").assertExists()
        compose.onNodeWithText("ce n'est pas un manque de volonté", substring = true).assertExists()
        compose.onNodeWithText("🤗  Ça ne va pas : respirer, se poser").performClick()
        org.junit.Assert.assertEquals(1, support)
    }

    @Test
    @Config(qualifiers = "w400dp-h2300dp-xhdpi")
    fun softDay() {
        var state = wellState()
        state = com.medipote.app.domain.Wellbeing.setEnergy(state, today, 1)
        state = com.medipote.app.domain.Wellbeing.win(state, "tea", "Me préparer une boisson chaude", 5, now).state
        state = com.medipote.app.domain.Wellbeing.saveTask(state, "t1", "Faire ma lessive", listOf("Ramasser le linge", "Lancer la machine", "Étendre"), now)
        val done = mutableListOf<String>()
        var energy = 0
        var newTask = 0
        render("soft_day") {
            com.medipote.app.ui.wellbeing.SoftDayScreen(state, now, { energy = it }, { done += it.id }, { newTask++ }, {}, {}, {})
        }
        compose.onNodeWithText("Une journée à ton rythme").assertExists()
        compose.onNodeWithText("💛  1 petite victoire aujourd'hui").assertExists()
        compose.onNodeWithText("Mes missions").assertExists()
        compose.onNodeWithText("Prendre un bain").assertExists()
        compose.onNodeWithText("Faire ma lessive").assertExists()
        // Nothing reads as a failure: no "x sur y missions" counter, no list of what was not done.
        compose.onAllNodesWithText(" sur ", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("manqu", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Fait")[0].performClick()
        org.junit.Assert.assertEquals(1, done.size)
        compose.onNodeWithText("⚡  Presque comme d'habitude").performClick()
        org.junit.Assert.assertEquals(3, energy)
        compose.onNodeWithText("🧩  La découper en petites étapes").performClick()
        org.junit.Assert.assertEquals(1, newTask)
    }

    @Test
    @Config(qualifiers = "w400dp-h1900dp-xhdpi")
    fun taskEditor() {
        var saved: Pair<String, List<String>>? = null
        render("task_edit") { com.medipote.app.ui.wellbeing.TaskEditScreen({ t, steps -> saved = t to steps }, {}) }
        compose.onNodeWithText("Enregistrer · 0 étape").assertExists()
        compose.onNode(androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.hasText("Par exemple : faire ma lessive")).performTextInput("Faire ma lessive")
        compose.onNodeWithText("🧺  Ménage, rangement, lessive").performClick()
        compose.onNodeWithText("Ça me semble trop gros").performClick()
        compose.waitForIdle()
        // The blocker's step comes first, then the kind's steps; the first ones are already ticked.
        compose.onNodeWithText("Choisir un tout petit morceau et ne faire que lui aujourd'hui").assertExists()
        compose.onNodeWithText("Mettre un minuteur de 5 minutes").assertExists()
        compose.onNodeWithText("Enregistrer · 4 étapes").performScrollTo().performClick()
        org.junit.Assert.assertEquals("Faire ma lessive", saved?.first)
        org.junit.Assert.assertEquals("Choisir un tout petit morceau et ne faire que lui aujourd'hui", saved?.second?.first())
        org.junit.Assert.assertEquals(4, saved?.second?.size)
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun taskScreen() {
        val task = com.medipote.app.data.SplitTask(
            "t1", "Faire ma lessive",
            listOf(com.medipote.app.data.SplitStep("Ramasser le linge", true), com.medipote.app.data.SplitStep("Lancer la machine"), com.medipote.app.data.SplitStep("Étendre")),
            now,
        )
        var ticked = -1
        render("task") { com.medipote.app.ui.wellbeing.TaskScreen(task, { ticked = it }, {}, {}, animate = false) }
        compose.onNodeWithText("1 sur 3 étapes").assertExists()
        compose.onNodeWithText("Prochaine étape").assertExists()
        compose.onNodeWithText("⏱️  Cinq minutes seulement").assertExists()
        compose.onAllNodesWithText("Fait")[0].performClick()
        org.junit.Assert.assertEquals(1, ticked)
    }

    @Test
    @Config(qualifiers = "w400dp-h2500dp-xhdpi")
    fun conditionsScreen() {
        var settings = com.medipote.app.data.WellbeingSettings()
        render("conditions") {
            com.medipote.app.ui.wellbeing.ConditionsScreen(settings, { t -> settings = t(settings) }, {})
        }
        compose.onNodeWithText("Ce que je vis").assertExists()
        compose.onNodeWithText("🦋  Hashimoto / hypothyroïdie").performScrollTo().performClick()
        // Choosing one turns the tracking on and follows its usual symptoms.
        org.junit.Assert.assertTrue(settings.enabled)
        org.junit.Assert.assertEquals("hashimoto", settings.conditions.single().presetId)
        org.junit.Assert.assertTrue("fatigue" in settings.symptoms && "body" in settings.symptoms)
    }

    @Test
    @Config(qualifiers = "w400dp-h1100dp-xhdpi")
    fun conditionsOnboardingCanBeSkipped() {
        var back = 0
        render("conditions_onboarding") {
            com.medipote.app.ui.wellbeing.ConditionsScreen(com.medipote.app.data.WellbeingSettings(), {}, { back++ }, onboarding = true)
        }
        compose.onNodeWithText("Ce qui t'accompagne").assertExists()
        compose.onNodeWithText("Passer pour l'instant").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, back)
    }

    @Test
    @Config(qualifiers = "w400dp-h1000dp-xhdpi")
    fun wellBeingPageOfCards() {
        val state = wellState()
        var support = 0
        render("hub_mieux") {
            CardHub(
                "Mieux-être",
                listOf(
                    HubCard("mieux:softday", "💛", "Journée douce", com.medipote.app.ui.HubPreviews.softDay(state, now)),
                    HubCard("mieux:support", "🤗", "Ça ne va pas", "Respirer, se poser, appeler quelqu'un") { support++ },
                    HubCard("mieux:me", "🌿", "Ce qui m'accompagne", com.medipote.app.ui.HubPreviews.me(state)),
                ),
                emptySet(), {}, {}, {},
            ) { androidx.compose.material3.Text("contenu") }
        }
        compose.onNodeWithText("Hashimoto / hypothyroïdie").assertExists()
        compose.onNodeWithText("Ça ne va pas").performClick()
        org.junit.Assert.assertEquals(1, support)
        // A card that opens another screen doesn't open in place.
        compose.onNodeWithText("contenu").assertDoesNotExist()
        compose.onNodeWithText("Journée douce").performClick()
        compose.onNodeWithText("contenu").assertExists()
    }

    @Test
    fun hiddenCardsAreNotListed() {
        render("hub_hidden") {
            CardHub(
                "Soins",
                listOf(HubCard("soins:treatments", "💊", "Traitements"), HubCard("soins:agenda", "📅", "Agenda")),
                setOf("soins:agenda"), {}, {}, {},
            ) {}
        }
        compose.onNodeWithText("Traitements").assertExists()
        compose.onNodeWithText("Agenda").assertDoesNotExist()
        compose.onNodeWithText("✏️  Choisir mes cartes").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun cardsSettings() {
        val state = wellState()
        val toggled = mutableListOf<Pair<String, Boolean>>()
        render("cards_layout") { com.medipote.app.ui.settings.CardsScreen(state, { k, h -> toggled += k to h }, {}, {}) }
        compose.onNodeWithText("Symptômes").assertExists()
        compose.onNodeWithContentDescription("Afficher Agenda").performClick()
        org.junit.Assert.assertEquals(listOf("soins:agenda" to true), toggled)
    }

    @Test
    @Config(qualifiers = "w400dp-h2200dp-xhdpi")
    fun homeOffersTheGentleDayOnHardDaysOnly() {
        var opened = 0
        render("home_soft_day") { HomeScreen(wellState(), now, 0, 0, {}, {}, {}, {}, animate = false, onOpenSoftDay = { opened++ }) }
        compose.onNodeWithText("Journée lourde ?").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "w400dp-h2200dp-xhdpi")
    fun homeStaysQuietOnAnEasyDay() {
        render("home_soft_day_off") { HomeScreen(wellState(heavy = false), now, 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText("Journée lourde ?").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun quickAddOffersSymptomsOnlyWhenTurnedOn() {
        var symptoms = 0
        render("quick_add_symptoms") {
            com.medipote.app.ui.home.QuickAddContent(wellState(), now, com.medipote.app.ui.home.QuickAddActions(onSymptoms = { symptoms++ }), onDone = {})
        }
        compose.onNodeWithText("Journée douce").assertExists()
        compose.onNodeWithText("Un symptôme").performClick()
        org.junit.Assert.assertEquals(1, symptoms)
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun quickAddWithoutWellbeing() {
        render("quick_add_plain") { com.medipote.app.ui.home.QuickAddContent(sampleState(), now, com.medipote.app.ui.home.QuickAddActions(), onDone = {}) }
        compose.onNodeWithText("Un symptôme").assertDoesNotExist()
    }

    // ───────────── 1.19: journal + symptoms, simple home, quiet hours, appointment prep ─────────────

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun journalEntryShowsTheFollowedSymptomsWithTheirStrength() {
        val got = mutableListOf<Pair<String, Int?>>()
        render("day_entry_symptoms") {
            DayEntryScreen(
                sampleState().entryFor(today), showCycle = false, onSave = {}, onBack = {},
                trackedSymptoms = listOf("fatigue", "anxiety"), intensities = mapOf("fatigue" to 3),
                onSymptom = { id, v -> got += id to v },
            )
        }
        compose.onNodeWithText("Mes symptômes suivis").assertExists()
        compose.onNodeWithContentDescription("Fatigue, intensité 3 sur 5").performClick() // the chosen one again removes it
        compose.onNodeWithContentDescription("Anxiété, intensité 4 sur 5").performClick()
        org.junit.Assert.assertEquals(listOf("fatigue" to null, "anxiety" to 4), got)
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun journalEntryWithoutFollowedSymptomsHasNoSuchCard() {
        render("day_entry_plain") { DayEntryScreen(sampleState().entryFor(today), showCycle = false, onSave = {}, onBack = {}) }
        compose.onNodeWithText("Mes symptômes suivis").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun symptomsScreenSaysWhatTheJournalAlreadyMentions() {
        var s = wellState()
        s = s.copy(journal = s.journal + com.medipote.app.data.DayEntry(today.minusDays(0), mentalSymptoms = listOf("Stress")))
        s = com.medipote.app.domain.Wellbeing.setIntensity(s, today, "anxiety", null)
        render("symptoms_journal") { com.medipote.app.ui.wellbeing.SymptomsScreen(s, now, { _, _ -> }, {}, {}, {}) }
        compose.onNodeWithText("📖  Noté dans ton journal : choisis la force.").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1100dp-xhdpi")
    fun homeLayoutOffersSimpleAndComplete() {
        var settings = Settings()
        render("home_layout_presets") { com.medipote.app.ui.settings.HomeLayoutScreen(settings, { t -> settings = t(settings) }, {}) }
        compose.onNodeWithText("🌿  Accueil simple").performClick()
        org.junit.Assert.assertTrue(settings.look.isSimple)
        org.junit.Assert.assertFalse(settings.look.hiddenSections.isEmpty())
        compose.onNodeWithText("🧰  Accueil complet").performClick()
        org.junit.Assert.assertTrue(settings.look.isComplete)
    }

    @Test
    @Config(qualifiers = "w400dp-h2600dp-xhdpi")
    fun settingsHaveQuietHours() {
        // Off: only the switch. On: the two hours appear.
        render("settings_quiet") {
            SettingsScreen(
                Settings(quiet = com.medipote.app.data.QuietHours(enabled = true)), "Pilou", notificationsAllowed = true, exactAlarmsAllowed = true, dynamicColorAvailable = false,
                onBack = {}, onChange = {}, onRequestNotifications = {}, onRequestExactAlarms = {},
                onRenamePet = {}, onOpenProfile = {}, onReset = {},
            )
        }
        compose.onNodeWithText("Heures calmes").performScrollTo().assertExists()
        compose.onNodeWithText("Pas de rappel doux la nuit").performScrollTo().assertExists()
        compose.onNodeWithText("À partir de").performScrollTo().assertExists()
        compose.onNodeWithText("Jusqu'à").performScrollTo().assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h2000dp-xhdpi")
    fun appointmentPrepPage() {
        var s = wellState()
        s = s.copy(journal = (0..5).map { com.medipote.app.data.DayEntry(today.minusDays(it.toLong()), mental = 3, physical = 4) } + s.journal)
        val appointment = Appointment("a", "Endocrinologue", "Dr Martin", dateTime = today.plusDays(5).atTime(14, 30), questions = listOf("Ajuster mon traitement ?"))
        val prep = com.medipote.app.domain.AppointmentPrepper.build(s, appointment, now)
        val shared = mutableListOf<String>()
        var questions: List<String>? = null
        render("appointment_prep") {
            com.medipote.app.ui.agenda.AppointmentPrepScreen(prep, "samedi 17 novembre · 14:30", { questions = it }, {}, { shared += it }, {}, {})
        }
        compose.onNodeWithText("Endocrinologue · Dr Martin").assertExists()
        compose.onNodeWithText("•  Ajuster mon traitement ?").assertExists()
        compose.onNodeWithText("Mes symptômes").assertExists()
        compose.onNodeWithText("📤  Partager ce résumé").performScrollTo().performClick()
        org.junit.Assert.assertTrue(shared.single().contains("Mes questions :"))
        compose.onNodeWithContentDescription("Retirer « Ajuster mon traitement ? »").performClick()
        org.junit.Assert.assertEquals(emptyList<String>(), questions)
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun appointmentEditorOffersThePrepOnlyOnceItExists() {
        var prepared = 0
        val existing = sampleState().appointments.first()
        render("appointment_editor_prep") { AppointmentEditorScreen(existing, today, {}, {}, {}, onPrepare = { prepared++ }) }
        compose.onNodeWithText("🩺  Préparer ce rendez-vous").performClick()
        org.junit.Assert.assertEquals(1, prepared)
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xhdpi")
    fun newAppointmentHasNoPrepYet() {
        render("appointment_editor_new") { AppointmentEditorScreen(null, today, {}, {}, {}, onPrepare = {}) }
        compose.onNodeWithText("🩺  Préparer ce rendez-vous").assertDoesNotExist()
    }

    // ───────────── Ce que je ressens ─────────────

    @Test
    @Config(qualifiers = "w400dp-h3000dp-xhdpi")
    fun feelScreenAsksWhatIsFelt() {
        render("feel") { com.medipote.app.ui.support.FeelScreen(com.medipote.app.ui.support.FeelActions()) }
        compose.onNodeWithText("Touche ce qui te ressemble.").assertExists()
        compose.onNodeWithText("😰  Anxiété").assertExists()
        compose.onNodeWithText("😨  Angoisse").assertExists()
        compose.onNodeWithText("😔  Moral bas").assertExists()
        // The page starts light: three cards, signs only once one is opened.
        compose.onNodeWithText("💔  Poids dans la poitrine").assertDoesNotExist()
        compose.onNodeWithText("😔  Moral bas").performClick()
        // The person's own words are there.
        compose.onNodeWithText("💔  Poids dans la poitrine").assertExists()
        compose.onNodeWithText("🔋  Fatigue extrême").assertExists()
        compose.onNodeWithText("🛏️  Du mal à se lever du lit").assertExists()
        compose.onNodeWithText("😶  Envie de rien faire").assertExists()
        compose.onNodeWithText("choisis une ou plusieurs choses", substring = true).assertExists()
        compose.onNodeWithText("Ce qui peut aider maintenant").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w400dp-h3600dp-xhdpi")
    fun lowMoodSignsGiveGesturesToolsAndWhenToTalk() {
        val tools = mutableListOf<com.medipote.app.domain.SupportTool>()
        var called: String? = null
        var noted: Pair<List<String>, Int>? = null
        render("feel_low") {
            com.medipote.app.ui.support.FeelScreen(
                com.medipote.app.ui.support.FeelActions(onTool = { tools += it }, onCall = { called = it }, onNote = { s, v -> noted = s to v }),
                initial = listOf("heavy_chest", "cant_get_up", "exhausted", "no_desire"),
            )
        }
        compose.onNodeWithText("Ce qui peut aider maintenant").assertExists()
        compose.onNodeWithText("•  Ne vise pas « me lever » : vise une toute petite étape, comme t'asseoir au bord du lit.").assertExists()
        compose.onNodeWithText("Quand en parler").assertExists()
        // A weight on the chest reminds when to call; the line is one tap away.
        compose.onNodeWithText("appelle le 15 tout de suite", substring = true).assertExists()
        compose.onNodeWithText("📞 Appeler le 15").performScrollTo().performClick()
        org.junit.Assert.assertEquals("15", called)
        // The tools the signs point to.
        compose.onNodeWithText("💛  Journée douce").performScrollTo().performClick()
        compose.onNodeWithText("🌬️  Respirer").performScrollTo().performClick()
        org.junit.Assert.assertEquals(listOf(com.medipote.app.domain.SupportTool.SOFT_DAY, com.medipote.app.domain.SupportTool.BREATHE), tools)
        // Noting what was felt, with a strength.
        compose.onNodeWithText("📝  Les noter dans mes symptômes").assertExists()
        compose.onNodeWithText("4 · fort").performScrollTo().performClick()
        compose.onNodeWithText("Noter").performScrollTo().performClick()
        org.junit.Assert.assertEquals(listOf("lowmood", "fatigue") to 4, noted)
    }

    @Test
    @Config(qualifiers = "w400dp-h3200dp-xhdpi")
    fun darkThoughtsShowTheHelpLineFirst() {
        var called: String? = null
        render("feel_dark") {
            com.medipote.app.ui.support.FeelScreen(com.medipote.app.ui.support.FeelActions(onCall = { called = it }), initial = listOf("dark"))
        }
        compose.onNodeWithText("Tu n'as pas à rester seul·e avec ça").assertExists()
        compose.onNodeWithText("📞 Appeler le 3114").performClick()
        org.junit.Assert.assertEquals("3114", called)
        // A dark thought is not turned into a tip list or a symptom to note.
        compose.onNodeWithText("📝  Les noter dans mes symptômes").assertExists() // it can still be noted, as low mood
    }

    @Test
    @Config(qualifiers = "w400dp-h2600dp-xhdpi")
    fun anxietyAndPanicSignsOfferBreathingAndGrounding() {
        val tools = mutableListOf<com.medipote.app.domain.SupportTool>()
        render("feel_anxiety") {
            com.medipote.app.ui.support.FeelScreen(com.medipote.app.ui.support.FeelActions(onTool = { tools += it }), initial = listOf("heart_fast", "wave", "loop"))
        }
        compose.onNodeWithText("•  Dis-toi : « c'est une vague, elle va passer ».").assertExists()
        compose.onNodeWithText("🖐️  Me poser (5-4-3-2-1)").performScrollTo().performClick()
        compose.onNodeWithText("✍️  Vider ma tête").performScrollTo().performClick()
        org.junit.Assert.assertEquals(listOf(com.medipote.app.domain.SupportTool.GROUND, com.medipote.app.domain.SupportTool.THOUGHTS), tools)
        // The anxiety feeling mentions the thyroid among possible causes.
        compose.onNodeWithText("la thyroïde", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1800dp-xhdpi")
    fun feelCardsStayLightAndEncourageWithoutPressure() {
        val tried = mutableListOf<String>()
        render("feel_light") {
            com.medipote.app.ui.support.FeelScreen(
                com.medipote.app.ui.support.FeelActions(onTried = { tried += it.id }),
                initial = listOf("heavy_chest", "cant_get_up"), tried = setOf("cant_get_up"),
            )
        }
        // Three ideas at a time; the others are one touch away.
        compose.onNodeWithText("•  Pose une main sur ta poitrine, respire doucement et dis-toi une phrase gentille.").assertExists()
        compose.onNodeWithText("•  Une boisson chaude, un plaid, une bouillotte sur la poitrine ou le ventre.").assertExists()
        compose.onNodeWithText("•  Si les larmes viennent, laisse-les venir : pleurer soulage.").assertExists()
        compose.onNodeWithText("•  Dis à quelqu'un que ça pèse, même par un simple message.").assertDoesNotExist()
        compose.onAllNodesWithText("Plus d'idées")[0].performScrollTo().performClick()
        compose.onNodeWithText("•  Dis à quelqu'un que ça pèse, même par un simple message.").assertExists()
        compose.onNodeWithText("Moins d'idées").assertExists()
        // One button per card; what was tried shows as done, and nothing shows what was not.
        compose.onNodeWithText("✓ J'ai essayé").performScrollTo().performClick()
        org.junit.Assert.assertEquals(listOf("heavy_chest"), tried)
        compose.onNodeWithText("✓ Essayé, bravo 💛").assertExists()
        compose.onNodeWithText("Chaque essai rapporte quelques points. Ne rien essayer ne coûte rien.").assertExists()
        compose.onAllNodesWithText("pas fait", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun symptomAdviceCanBeTriedToo() {
        var tried = 0
        render("symptom_advice_tried") { com.medipote.app.ui.wellbeing.SymptomAdviceScreen("fatigue", {}, {}, {}, onTried = { tried++ }) }
        compose.onNodeWithText("✓ J'ai essayé un de ces conseils").performClick()
        org.junit.Assert.assertEquals(1, tried)
    }

    @Test
    fun theSupportScreenOpensTheFeelingGuideFirst() {
        var opened = 0
        render("support_with_feel") {
            com.medipote.app.ui.support.SupportScreen(supportState(), now, com.medipote.app.ui.support.SupportActions(onFeel = { opened++ }))
        }
        compose.onNodeWithText("Ce que je ressens").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, opened)
    }

    // ───────────── A treatment at different times on different days ─────────────

    @Test
    @Config(qualifiers = "w400dp-h2200dp-xhdpi")
    fun theEditorGivesEachTimeItsOwnDays() {
        var saved: Treatment? = null
        val levo = treatments.first().copy(doses = listOf(DoseTime(10 * 60, days = setOf(1, 2, 3, 4, 5)), DoseTime(12 * 60, days = setOf(6, 7))))
        render("editor_days") {
            TreatmentEditorScreen(
                existing = levo, drug = DrugGuide.byId(levo.drugId ?: "levothyroxine"), customName = "",
                allergies = emptyList(), today = today, onSave = { saved = it }, onDelete = {}, onBack = {},
            )
        }
        // The days of each time are said in words, "every day" being the default.
        compose.onNodeWithText("📅  En semaine").assertExists()
        compose.onNodeWithText("📅  Le week-end").assertExists()
        // The first time goes to "every day except Wednesday" through the day buttons.
        compose.onAllNodesWithText("Tous les jours")[0].performScrollTo().performClick()
        compose.onAllNodesWithText("mer")[0].performScrollTo().performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        org.junit.Assert.assertEquals(setOf(1, 2, 4, 5, 6, 7), saved!!.doses.first().days) // every day except Wednesday
        org.junit.Assert.assertEquals(setOf(6, 7), saved!!.doses.last().days)
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xhdpi")
    fun aTimeKeepsEveryDayUntilAskedAndNeverEndsWithNoDay() {
        var saved: Treatment? = null
        val plain = treatments.first()
        render("editor_days_plain") {
            TreatmentEditorScreen(
                existing = plain, drug = DrugGuide.byId(plain.drugId ?: "levothyroxine"), customName = "",
                allergies = emptyList(), today = today, onSave = { saved = it }, onDelete = {}, onBack = {},
            )
        }
        compose.onNodeWithText("📅  Tous les jours").assertExists()
        compose.onNodeWithText("Enregistrer").performClick()
        org.junit.Assert.assertNull(saved!!.doses.single().days)
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xhdpi")
    fun theTreatmentCardNamesTheDaysOfEachTime() {
        val levo = treatments.first().copy(doses = listOf(DoseTime(10 * 60, days = setOf(1, 2, 3, 4, 5)), DoseTime(12 * 60, days = setOf(6, 7))))
        render("treatments_days") { TreatmentsScreen(listOf(levo), today, {}, {}) }
        compose.onNodeWithText("10:00 en semaine · 12:00 le week-end").assertExists()
    }

    // ───────────── Hard nights, the companion's wish ─────────────

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun theSleepTabOffersTheNightHelpInOneCalmCard() {
        var opened = 0
        render("sleep_night_card") { com.medipote.app.ui.sleep.SleepScreen(sampleState(), now, {}, {}, onNightHelp = { opened++ }) }
        compose.onNodeWithText("Nuit difficile ?").performClick()
        org.junit.Assert.assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "w400dp-h2600dp-xhdpi")
    fun nightHelpStartsWithTwoChoicesAndThenFourIdeas() {
        val tools = mutableListOf<com.medipote.app.domain.SleepTool>()
        val tried = mutableListOf<com.medipote.app.domain.SleepTrouble>()
        var recipe: List<String>? = null
        render("sleep_help") {
            com.medipote.app.ui.sleep.SleepHelpScreen(
                listOf("Une tisane tiède"), emptySet(),
                com.medipote.app.ui.sleep.SleepHelpActions(onTool = { tools += it }, onTried = { tried += it }, onRecipe = { recipe = it }),
            )
        }
        compose.onNodeWithText("Respire. Tu n'as rien à réussir.").assertExists()
        // Two big choices first; nothing else until one is chosen.
        compose.onNodeWithText("Je n'arrive pas à m'endormir").assertExists()
        compose.onNodeWithText("Je me suis réveillé·e et je n'arrive pas à me rendormir").assertExists()
        compose.onNodeWithText("🕰️  La règle des 20 minutes").assertDoesNotExist()
        compose.onNodeWithText("Je n'arrive pas à m'endormir").performClick()
        compose.onNodeWithText("🌬️  Respirer lentement").assertExists()
        compose.onNodeWithText("🧘  Détendre le corps").assertExists()
        compose.onNodeWithText("✍️  Vider ma tête").assertExists()
        compose.onNodeWithText("🕯️  Doux et frais").assertExists()
        compose.onNodeWithText("🕰️  La règle des 20 minutes").assertExists()
        compose.onNodeWithText("Commencer").performClick()
        compose.onNodeWithText("Écrire").performClick()
        org.junit.Assert.assertEquals(listOf(com.medipote.app.domain.SleepTool.RELAX, com.medipote.app.domain.SleepTool.THOUGHTS), tools)
        // The person's own recipe is shown, and can be changed.
        compose.onNodeWithText("•  Une tisane tiède").assertExists()
        compose.onNodeWithContentDescription("Retirer « Une tisane tiède »").performClick()
        org.junit.Assert.assertEquals(emptyList<String>(), recipe)
        compose.onNodeWithText("✓ J'ai essayé quelque chose").performScrollTo().performClick()
        org.junit.Assert.assertEquals(listOf(com.medipote.app.domain.SleepTrouble.FALL), tried)
    }

    @Test
    @Config(qualifiers = "w400dp-h2400dp-xhdpi")
    fun goingBackToSleepHasItsOwnIdeasAndShowsWhatWasTried() {
        render("sleep_help_back") {
            com.medipote.app.ui.sleep.SleepHelpScreen(emptyList(), setOf("back"), com.medipote.app.ui.sleep.SleepHelpActions(), initial = com.medipote.app.domain.SleepTrouble.BACK)
        }
        compose.onNodeWithText("🤍  Un réveil, c'est normal").assertExists()
        compose.onNodeWithText("🙈  Ne pas regarder l'heure").assertExists()
        compose.onNodeWithText("✓ Essayé, bravo 💛").performScrollTo().assertExists()
        compose.onNodeWithText("Ce qui t'a aidé une nuit", substring = true).assertExists()
        compose.onNodeWithText("Chaque essai rapporte quelques points. Ne rien essayer ne coûte rien.").performScrollTo().assertExists()
    }

    @Test
    fun theGuidedRelaxationGoesOneLineAtATime() {
        render("sleep_relax") { com.medipote.app.ui.sleep.RelaxScreen({}) }
        compose.onNodeWithText("Installe-toi, ferme les yeux. Laisse ton souffle ralentir.").assertExists()
        compose.onNodeWithText("1 / 8").assertExists()
        compose.onNodeWithText("Suivant, quand tu es prêt·e").performClick()
        compose.onNodeWithText("Ton front : détends-le, comme si on le lissait doucement.").assertExists()
        compose.onNodeWithText("Ton front : détends-le, comme si on le lissait doucement.", substring = true).assertExists()
        compose.onNodeWithText("Revenir en arrière").performClick()
        compose.onNodeWithText("1 / 8").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1800dp-xhdpi")
    fun theDayTipsComeWithAnEveningReminderSwitchAndTheLimits() {
        var windDown: Boolean? = null
        render("sleep_tips") { com.medipote.app.ui.sleep.SleepTipsScreen(false, { windDown = it }, {}, {}, {}) }
        compose.onNodeWithText("⏰  Se lever à peu près à la même heure").assertExists()
        compose.onNodeWithText("Un petit mot le soir").performScrollTo().assertExists()
        compose.onNodeWithText("trois semaines", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Un petit mot le soir").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h2200dp-xhdpi")
    fun theShopShowsTheCompanionsWishWithAProgressBar() {
        val game = sampleState().game.copy(points = 70, wishId = "hat_santa", wishWeek = today)
        var bought: com.medipote.app.domain.ShopItem? = null
        render("shop_wish") { ShopScreen(game, { bought = it }, {}, animate = false) }
        compose.onNodeWithText("🎁  Cette semaine, Pilou rêve de…").assertExists()
        compose.onAllNodesWithText("Bonnet de Noël")[0].assertExists()
        compose.onNodeWithText("encore 70 pts").assertExists()
        // Touching it tries it on, ready to be offered.
        compose.onNodeWithText("🎁  Cette semaine, Pilou rêve de…").performClick()
        compose.onNodeWithText("Encore 70 pts").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun theWishDisappearsOnceGranted() {
        val game = sampleState().game.copy(points = 500, wishId = "hat_santa", wishWeek = today, owned = sampleState().game.owned + "hat_santa")
        render("shop_wish_done") { ShopScreen(game, {}, {}, animate = false) }
        compose.onNodeWithText("🎁  Cette semaine, Pilou rêve de…").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w400dp-h1000dp-xhdpi")
    fun theNewCosyItemsAreDrawn() {
        val game = sampleState().game.copy(
            equipped = mapOf("COLOR" to "color_mint", "BACKGROUND" to "bg_room", "HAT" to "hat_santa", "NECK" to "neck_plaid", "TOY" to "toy_tea"),
            owned = Shop.items.map { it.id }.toSet(),
        )
        render("shop_cosy", ThemeMode.DARK) { ShopScreen(game, {}, {}, animate = false) }
        compose.onNodeWithText("Boutique").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h2000dp-xhdpi")
    fun anAppointmentCanBeMarkedAsAttendedOnceItIsTime() {
        val appointment = Appointment("a", "Endocrinologue", "Dr Martin", dateTime = today.atTime(9, 0))
        val prep = com.medipote.app.domain.AppointmentPrepper.build(sampleState(), appointment, now)
        var went = 0
        render("appointment_prep_attended") {
            com.medipote.app.ui.agenda.AppointmentPrepScreen(prep, "aujourd'hui · 09:00", {}, {}, {}, {}, {}, canMarkAttended = true, onAttended = { went++ })
        }
        compose.onNodeWithText("✓ J'y suis allé·e").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, went)
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun beforeTheAppointmentThereIsNoAttendedButtonAndAfterItItShowsDone() {
        val future = Appointment("a", "Endocrinologue", dateTime = today.plusDays(3).atTime(9, 0))
        val prep = com.medipote.app.domain.AppointmentPrepper.build(sampleState(), future, now)
        render("appointment_prep_future") { com.medipote.app.ui.agenda.AppointmentPrepScreen(prep, "dans 3 jours", {}, {}, {}, {}, {}) }
        compose.onNodeWithText("✓ J'y suis allé·e").assertDoesNotExist()
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
