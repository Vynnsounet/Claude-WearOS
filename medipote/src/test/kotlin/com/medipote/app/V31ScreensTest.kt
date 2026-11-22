package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.SymptomNote
import com.medipote.app.data.Treatment
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.treatments.DrugSearchScreen
import com.medipote.app.ui.treatments.TreatmentEditorScreen
import com.medipote.app.ui.treatments.TreatmentsScreen
import com.medipote.app.ui.wellbeing.BodyMap
import com.medipote.app.ui.wellbeing.SymptomsScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1800dp-xhdpi")
class V31ScreensTest {
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

    private fun t(id: String, drug: String, name: String, vararg minutes: Int) =
        Treatment(id, name = name, drugId = drug, doses = minutes.map { DoseTime(it) }, startDate = day.minusDays(30), createdAt = day.minusDays(30).atStartOfDay())

    private val painState = AppState(
        profile = Profile("Alex"), onboarded = true,
        settings = Settings(wellbeing = WellbeingSettings(enabled = true, symptoms = listOf("pain"))),
        symptomLog = listOf(
            SymptomNote(day, "pain", 3, zones = listOf("lower_back")),
            SymptomNote(day.minusDays(2), "pain", 2, zones = listOf("lower_back", "leg_r")),
        ),
    )

    // ───────────── Body map ─────────────
    @Test
    fun theBodyMapOpensUnderTheSymptom_andSaysWhatWasChosen() {
        var saved: Pair<String, List<String>>? = null
        show { SymptomsScreen(painState, now, onSet = { _, _ -> }, onAdvice = {}, onManage = {}, onSoftDay = {}, onZones = { s, z -> saved = s to z }) }
        compose.onNodeWithText("📍  Bas du dos").assertExists()
        compose.onNodeWithText("Ce mois-ci : bas du dos (2 j), jambe droite (1 j)").assertExists()
        compose.onNodeWithText("📍  Bas du dos").performClick()
        compose.onNodeWithContentDescription("Schéma du corps, vue de face. Touche l'endroit qui te fait mal.").assertExists()
        shot("body_map")
        compose.onNodeWithText("De dos").performClick()
        compose.onNodeWithContentDescription("Schéma du corps, vue de dos. Touche l'endroit qui te fait mal.").assertExists()
        shot("body_map_back")
        compose.onNodeWithText("Fessiers").performClick()
        assertEquals("pain" to listOf("lower_back", "glutes"), saved)
    }

    @Test
    fun aSymptomNotNotedYetOffersNoBody() {
        show { SymptomsScreen(painState.copy(symptomLog = emptyList()), now, onSet = { _, _ -> }, onAdvice = {}, onManage = {}, onSoftDay = {}) }
        compose.onNodeWithText("Où ça se passe ?").assertDoesNotExist()
    }

    @Test
    fun theZonesOfTheOtherSideAreMentioned() {
        show { BodyMap(listOf("chest", "lower_back"), onChange = {}) }
        compose.onNodeWithText("Aussi, de l'autre côté : bas du dos").assertExists()
        compose.onNodeWithText("Poitrine").assertExists()
        compose.onNodeWithText("De dos").performClick()
        compose.onNodeWithText("Aussi, de l'autre côté : poitrine").assertExists()
    }

    // ───────────── Precautions ─────────────
    @Test
    fun theEditorGivesTheHabitsAndTheSpacing() {
        val levo = DrugGuide.byId("levothyroxine")!!
        val calcium = t("c", "calcium_vitd", "Cacit D3", 8 * 60 + 30)
        show {
            TreatmentEditorScreen(existing = null, drug = levo, customName = "", allergies = emptyList(), today = day, others = listOf(calcium), onSave = {}, onDelete = {}, onBack = {})
        }
        shot("editor_precautions")
        compose.onNodeWithText("À savoir pour bien la prendre").assertExists()
        compose.onNodeWithText("Le matin à jeun", substring = true).assertExists()
        compose.onNodeWithText("Cacit D3 est pris à 30 min d'écart", substring = true).assertExists()
        compose.onNodeWithText("la notice et ton pharmacien ont toujours le dernier mot", substring = true).assertExists()
    }

    @Test
    fun aDrugWithoutPrecautionsShowsNoCard() {
        show { TreatmentEditorScreen(existing = null, drug = null, customName = "Truc", allergies = emptyList(), today = day, onSave = {}, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("À savoir pour bien la prendre").assertDoesNotExist()
    }

    @Test
    fun theListWarnsOfTwoTreatmentsTooClose_andOpensOne() {
        val levo = t("l", "levothyroxine", "Lévothyrox", 8 * 60)
        val calcium = t("c", "calcium_vitd", "Cacit D3", 8 * 60 + 15)
        var opened: String? = null
        show { TreatmentsScreen(listOf(levo, calcium), day, onAdd = {}, onEdit = { opened = it.id }) }
        shot("treatments_spacing")
        compose.onNodeWithText("laisse au moins 4 h", substring = true).assertExists()
        compose.onNodeWithText("laisse au moins 4 h", substring = true).performClick()
        assertEquals("l", opened)
    }

    @Test
    fun theListIsQuietWhenSpacedOut() {
        val levo = t("l", "levothyroxine", "Lévothyrox", 8 * 60)
        val calcium = t("c", "calcium_vitd", "Cacit D3", 14 * 60)
        show { TreatmentsScreen(listOf(levo, calcium), day, onAdd = {}, onEdit = {}) }
        compose.onNodeWithText("laisse au moins", substring = true).assertDoesNotExist()
    }

    // ───────────── Scan ─────────────
    @Test
    fun theSearchOffersTheScanner_andSaysWhenABoxIsUnknown() {
        var scans = 0
        show { DrugSearchScreen(onBack = {}, onPick = {}, onCustom = {}, autoFocus = false, onScan = { scans++ }, scanMessage = "Cette boîte n'est pas dans la liste du téléphone : cherche-la par son nom.") }
        shot("search_scan")
        compose.onNodeWithText("Cette boîte n'est pas dans la liste", substring = true).assertExists()
        compose.onNodeWithText("📷  Scanner la boîte").performClick()
        assertEquals(1, scans)
    }

    @Test
    fun withoutAScannerTheSearchIsAsBefore() {
        show { DrugSearchScreen(onBack = {}, onPick = {}, onCustom = {}, autoFocus = false) }
        compose.onNodeWithText("📷  Scanner la boîte").assertDoesNotExist()
    }

    @Test
    fun aScannedDosageFillsTheEditor() {
        val levo = DrugGuide.byId("levothyroxine")!!
        show { TreatmentEditorScreen(existing = null, drug = levo, customName = "", presetStrength = "88 µg", allergies = emptyList(), today = day, onSave = {}, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("88 µg", substring = true).assertExists()
    }
}
