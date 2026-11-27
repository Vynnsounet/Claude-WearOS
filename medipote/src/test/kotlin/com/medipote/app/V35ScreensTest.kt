package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.medipote.app.data.Anchor
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseLog
import com.medipote.app.data.DoseTime
import com.medipote.app.data.Profile
import com.medipote.app.data.Routine
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.domain.DoseOccurrence
import com.medipote.app.domain.HabitSuggestion
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.settings.RoutineCard
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.treatments.TreatmentEditorScreen
import com.medipote.app.ui.treatments.TreatmentsScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2200dp-xhdpi")
class V35ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 30)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun levo(vararg doses: DoseTime) = Treatment("levo", name = "Lévothyrox", strength = "75 µg", doses = doses.toList(), startDate = day.minusDays(60), createdAt = day.minusDays(60).atStartOfDay())

    @Test
    fun aDoseCanFollowTheBreakfast() {
        var saved: Treatment? = null
        show { TreatmentEditorScreen(existing = levo(DoseTime(8 * 60)), drug = null, customName = "", allergies = emptyList(), today = day, routine = Routine(breakfast = 8 * 60 + 30), onSave = { saved = it }, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("🕐  À heure fixe").performClick()
        compose.onNodeWithText("Selon mes repas").performClick()
        // the default: 30 minutes before breakfast, so 8:00 with a breakfast at 8:30
        compose.onNodeWithText("🍽️  30 min avant le petit-déjeuner (08:00)").assertExists()
        compose.onNodeWithText("Ton petit-déjeuner est à 08:30", substring = true).assertExists()
        shot("editor_anchor")
        compose.onNodeWithText("Coucher").performClick()
        // the gap is kept when the new anchor allows it
        compose.onNodeWithText("🍽️  30 min avant le coucher (22:30)").assertExists()
        compose.onNodeWithText("Enregistrer").performClick()
        val dose = saved!!.doses.single()
        assertEquals("bed", dose.anchor); assertEquals(-30, dose.offsetMinutes); assertEquals(22 * 60 + 30, dose.minuteOfDay)
    }

    @Test
    fun aGapCanBeChosen() {
        var saved: Treatment? = null
        show { TreatmentEditorScreen(existing = levo(DoseTime(7 * 60, anchor = "breakfast", offsetMinutes = -30)), drug = null, customName = "", allergies = emptyList(), today = day, routine = Routine(), onSave = { saved = it }, onDelete = {}, onBack = {}) }
        // already anchored: the section is open
        compose.onNodeWithText("1 h avant").performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        assertEquals(-60, saved!!.doses.single().offsetMinutes); assertEquals(6 * 60 + 30, saved!!.doses.single().minuteOfDay)
    }

    @Test
    fun goingBackToAClockTimeForgetsTheAnchor() {
        var saved: Treatment? = null
        show { TreatmentEditorScreen(existing = levo(DoseTime(7 * 60, anchor = "breakfast", offsetMinutes = -30)), drug = null, customName = "", allergies = emptyList(), today = day, onSave = { saved = it }, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("À heure fixe").performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        assertTrue(saved!!.doses.single().anchor == null)
        assertEquals(7 * 60, saved!!.doses.single().minuteOfDay)
    }

    @Test
    fun theListSaysWhatTheDoseFollows() {
        show { TreatmentsScreen(listOf(levo(DoseTime(7 * 60, anchor = "breakfast", offsetMinutes = -30))), day, onAdd = {}, onEdit = {}) }
        compose.onNodeWithText("07:00 · 30 min avant le petit-déjeuner", substring = true).assertExists()
    }

    @Test
    fun theRoutineIsInTheSettings_andTakesTheWakeFromTheNights() {
        var routine = Routine()
        show { RoutineCard(routine, usualWake = 6 * 60 + 40) { routine = it } }
        shot("routine_card")
        listOf("Réveil", "Petit-déjeuner", "Déjeuner", "Dîner", "Coucher").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("07:30").assertExists()
        compose.onNodeWithText("Prendre mon réveil habituel d'après mes nuits (06:40)").performClick()
        assertEquals(6 * 60 + 40, routine.wake)
    }

    @Test
    fun theHomeOffersToFollowTheRealHabit() {
        val t = Treatment("t", name = "Vitamine D", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(60), createdAt = day.minusDays(60).atStartOfDay())
        val logs = (1..8).map { i ->
            val d = day.minusDays(i.toLong()); val sched = d.atTime(8, 0)
            DoseLog(DoseOccurrence.keyOf("t", d, 8 * 60), "t", sched, sched.plusMinutes(40), TakeStatus.LATE, 6)
        }
        val state = AppState(profile = Profile("Alex"), onboarded = true, treatments = listOf(t), doseLogs = logs)
        var applied: HabitSuggestion? = null; var dismissed: HabitSuggestion? = null
        show { HomeScreen(state, day.atTime(12, 0), 0, 0, {}, {}, {}, {}, animate = false, onApplyHabit = { applied = it }, onDismissHabit = { dismissed = it }) }
        shot("home_habit")
        compose.onNodeWithText("⏰  Tu prends Vitamine D vers 08:40").assertExists()
        compose.onNodeWithText("Pas maintenant").performClick()
        assertEquals("t", dismissed?.treatmentId)
        compose.onNodeWithText("Décaler à 08:40").performClick()
        assertEquals(8 * 60 + 40, applied?.usualMinute)
    }
}
