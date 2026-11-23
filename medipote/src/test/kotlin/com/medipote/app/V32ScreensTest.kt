package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.Profile
import com.medipote.app.data.Treatment
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.treatments.TreatmentEditorScreen
import com.medipote.app.ui.treatments.TreatmentsScreen
import java.time.LocalDate
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
@Config(sdk = [35], qualifiers = "w400dp-h1800dp-xhdpi")
class V32ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 23)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun vitamin(vararg doses: DoseTime) =
        Treatment("v", name = "Vitamine D", strength = "1000 UI", doses = doses.toList(), startDate = day.minusDays(30), createdAt = day.minusDays(30).atStartOfDay())

    private val window = DoseTime(10 * 60, untilMinute = 12 * 60, remindEnd = true)

    // ───────────── Editor ─────────────
    @Test
    fun aDoseIsAtAFixedTimeUntilAskedOtherwise_andBecomesAWindow() {
        var saved: Treatment? = null
        show { TreatmentEditorScreen(existing = vitamin(DoseTime(8 * 60)), drug = null, customName = "", allergies = emptyList(), today = day, onSave = { saved = it }, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("🕐  À heure fixe").assertExists()
        compose.onNodeWithText("🕐  À heure fixe").performClick()
        compose.onNodeWithText("Dans une plage").performClick()
        compose.onNodeWithText("🕐  Entre 08:00 et 10:00").assertExists()
        compose.onNodeWithText("Me rappeler avant la fin").assertExists()
        compose.onNodeWithText("Tu peux la prendre n'importe quand entre 08:00 et 10:00", substring = true).assertExists()
        shot("editor_window")
        compose.onNodeWithText("Enregistrer").performClick()
        val dose = saved!!.doses.single()
        assertEquals(8 * 60, dose.minuteOfDay); assertEquals(10 * 60, dose.untilMinute); assertTrue("on by default, and a visible choice", dose.remindEnd)
    }

    @Test
    fun theEndReminderCanBeTurnedOff_andTheWindowGivenUp() {
        var saved: Treatment? = null
        show { TreatmentEditorScreen(existing = vitamin(window), drug = null, customName = "", allergies = emptyList(), today = day, onSave = { saved = it }, onDelete = {}, onBack = {}) }
        // a window opens its settings straight away
        compose.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        assertFalse(saved!!.doses.single().remindEnd); assertEquals(12 * 60, saved!!.doses.single().untilMinute)
        compose.onNodeWithText("À heure fixe").performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        val dose = saved!!.doses.single()
        assertNull(dose.untilMinute); assertFalse(dose.remindEnd); assertEquals(10 * 60, dose.minuteOfDay)
    }

    @Test
    fun anAsNeededTreatmentHasNoWindow() {
        var saved: Treatment? = null
        show { TreatmentEditorScreen(existing = vitamin(window).copy(asNeeded = true), drug = null, customName = "", allergies = emptyList(), today = day, onSave = { saved = it }, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("🕐  Entre 10:00 et 12:00").assertDoesNotExist()
        compose.onNodeWithText("Enregistrer").performClick()
        assertTrue(saved!!.doses.isEmpty())
    }

    // ───────────── Lists ─────────────
    @Test
    fun theListShowsTheWindow() {
        show { TreatmentsScreen(listOf(vitamin(window)), day, onAdd = {}, onEdit = {}) }
        shot("treatments_window")
        compose.onNodeWithText("10:00–12:00", substring = true).assertExists()
    }

    @Test
    fun theHomeSaysYouCanTakeItAndUntilWhen() {
        val state = AppState(profile = Profile("Alex"), onboarded = true, treatments = listOf(vitamin(window)))
        show { HomeScreen(state, day.atTime(10, 30), 0, 0, {}, {}, {}, {}, animate = false) }
        shot("home_window")
        compose.onNodeWithText("Prochaine prise · Tu peux la prendre jusqu'à 12:00").assertExists()
        compose.onNodeWithText("1 comprimé · entre 10:00 et 12:00").assertExists()
    }

    @Test
    fun aMedicationWhoseTimeMattersGetsAWordOfCaution() {
        val levo = DrugGuide.byId("levothyroxine")!!
        show { TreatmentEditorScreen(existing = vitamin(DoseTime(8 * 60)).copy(drugId = levo.id), drug = levo, customName = "", allergies = emptyList(), today = day, onSave = {}, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("l'heure compte", substring = true).assertDoesNotExist()
        compose.onNodeWithText("🕐  À heure fixe").performClick()
        compose.onNodeWithText("Dans une plage").performClick()
        compose.onNodeWithText("l'heure compte", substring = true).assertExists()
    }

    @Test
    fun anOrdinaryMedicationHasNoCaution() {
        show { TreatmentEditorScreen(existing = vitamin(window), drug = null, customName = "", allergies = emptyList(), today = day, onSave = {}, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("l'heure compte", substring = true).assertDoesNotExist()
    }
}
