package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.medipote.app.data.AppState
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.SleepSettings
import com.medipote.app.domain.SleepFactors
import com.medipote.app.ui.sleep.EveningCard
import com.medipote.app.ui.sleep.FactorEffectsCard
import com.medipote.app.ui.sleep.FactorPicker
import com.medipote.app.ui.sleep.SleepEditorScreen
import com.medipote.app.ui.sleep.SleepScreen
import com.medipote.app.ui.theme.MediPoteTheme
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
@Config(sdk = [35], qualifiers = "w400dp-h2400dp-xhdpi")
class V33ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 25)
    private val now = today.atTime(21, 0)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val chosenState = mutableStateOf(emptyList<String>())
    private val settingsState = mutableStateOf(SleepSettings())
    private val chosen get() = chosenState.value
    private val settings get() = settingsState.value

    /** A picker that keeps what is chosen and the settings, like the real page. */
    @androidx.compose.runtime.Composable
    private fun Harness() {
        FactorPicker(chosenState.value, settingsState.value, { chosenState.value = it }, { t -> settingsState.value = t(settingsState.value) })
    }

    // ───────────── The picker ─────────────
    @Test
    fun theFactorsAreGroupedAndPrivateOnesAreHidden() {
        show { Harness() }
        shot("factors")
        listOf("☕  Boissons", "🍽️  Repas", "🌆  Soirée", "🛏️  Autour du lit").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("🔒  Plus personnel").assertDoesNotExist()
        compose.onNodeWithText("🌿 Cannabis").assertDoesNotExist()
        compose.onNodeWithText("☕ Café").assertExists(); compose.onNodeWithText("🍔 Fast-food, gras").assertExists()
    }

    @Test
    fun aFactorAsksForItsPrecision() {
        show { Harness() }
        compose.onNodeWithText("☕ Café").performClick()
        assertEquals(listOf("coffee"), chosen)
        compose.onNodeWithText("Café : après 16 h").performClick()
        assertEquals(listOf("coffee=late"), chosen)
        compose.onNodeWithText("☕ Café").performClick()
        assertTrue(chosen.isEmpty())
    }

    @Test
    fun howMuchWasEatenIsOneChoice() {
        show { Harness() }
        compose.onNodeWithText("🍝 Beaucoup mangé").performClick()
        compose.onNodeWithText("🥣 Peu mangé").performClick()
        compose.onNodeWithText("🌙 Mangé tard").performClick()
        assertEquals(setOf("amount_little", "meal_late"), chosen.toSet())
    }

    @Test
    fun privateThemesAreTurnedOnFromTheList_andAsksWhichSubstance() {
        show { Harness() }
        compose.onNodeWithText("⚙️  Personnaliser ma liste").performClick()
        compose.onNodeWithText("Substances et rapport sexuel").assertExists()
        compose.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        assertTrue(settings.sensitiveFactors)
        compose.onNodeWithText("🔒  Plus personnel").assertExists()
        compose.onAllNodesWithText("⚠️ Autre substance")[0].performClick()
        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasText("Laquelle ? (facultatif)")).performTextInput("tilleul")
        assertEquals(listOf("drug=tilleul"), chosen)
    }

    @Test
    fun aFactorCanBePutAway_andOwnWordsAdded() {
        show { Harness() }
        compose.onNodeWithText("⚙️  Personnaliser ma liste").performClick()
        // the tea is in the picker and in the manage list: the second one puts it away
        compose.onAllNodesWithText("🍵 Thé")[1].performClick()
        assertEquals(listOf("tea"), settings.hiddenFactors)
        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasText("Un facteur à moi")).performTextInput("Lecture")
        compose.onNodeWithText("Ajouter").performClick()
        assertEquals(listOf("Lecture"), settings.customFactors)
        compose.onNodeWithText("✏️  À moi").assertExists()
        compose.onNodeWithText("Lecture").assertExists()
    }

    // ───────────── The pages ─────────────
    @Test
    fun theEveningIsNotedFromTheSleepPage() {
        var noted: Pair<LocalDate, List<String>>? = null
        val state = AppState(onboarded = true)
        show { SleepScreen(state, now, {}, {}, onEvening = { d, l -> noted = d to l }) }
        compose.onNodeWithText("🌆  Ma soirée").assertExists()
        compose.onNodeWithText("Noter", substring = false).performClick()
        compose.onNodeWithText("☕ Café").performClick()
        assertEquals(today to listOf("coffee"), noted)
        shot("evening_card")
    }

    @Test
    fun theEveningCardSaysWhatWasNoted() {
        show { EveningCard(listOf("coffee=late", "party"), SleepSettings(), {}, {}) }
        compose.onNodeWithText("Café (après 16 h), Soirée, sortie").assertExists()
    }

    @Test
    fun theNightFormShowsTheEveningAndKeepsItOnSave() {
        val night = SleepNight(today, today.minusDays(1).atTime(23, 0), 15, today.atTime(7, 0))
        val state = AppState(onboarded = true, evenings = mapOf(today.minusDays(1).toString() to listOf("coffee", "party")))
        var saved: SleepNight? = null
        show { SleepEditorScreen(state, null, today, today, onSave = { saved = it }, onDelete = {}, onBack = {}) }
        // the evening noted the day before is already there: the section opens by itself
        compose.onNodeWithText("Ma soirée").assertExists()
        compose.onNodeWithText("2 facteurs", substring = true).assertDoesNotExist() // open: no summary
        compose.onNodeWithText("Enregistrer").performClick()
        assertEquals(listOf("coffee", "party"), saved!!.tags)
        shot("sleep_editor_evening")
    }

    // ───────────── The analysis card ─────────────
    @Test
    fun theCardSaysWhyThereIsNothingYet() {
        show { FactorEffectsCard(emptyList(), 3, anyNoted = false) }
        compose.onNodeWithText("Note ta soirée", substring = true).assertExists()
    }

    @Test
    fun theCardCountsTheNightsLeft() {
        show { FactorEffectsCard(emptyList(), 6, anyNoted = true) }
        compose.onNodeWithText("Encore 4 nuits", substring = true).assertExists()
    }

    @Test
    fun theCardSaysWhenNothingStandsOut() {
        show { FactorEffectsCard(emptyList(), 30, anyNoted = true) }
        compose.onNodeWithText("Rien de net pour l'instant", substring = true).assertExists()
    }

    @Test
    fun theCardShowsWhatGoesWithTheNights_asNotACause() {
        val nights = (0 until 12).map { i ->
            val date = today.minusDays(i.toLong()); val bed = date.minusDays(1).atTime(23, 0)
            SleepNight(date, bed, 15, bed.plusMinutes(((if (i < 6) 6.5 else 8.0) * 60).toLong() + 15))
        }
        val state = AppState(sleep = nights, evenings = (0 until 6).associate { today.minusDays(it.toLong() + 1).toString() to listOf("coffee=late") }, settings = Settings())
        val effects = SleepFactors.shown(state, nights)
        show { FactorEffectsCard(effects, nights.size, anyNoted = true) }
        shot("factor_effects")
        compose.onNodeWithText("☕  Café (après 16 h)").assertExists()
        compose.onNodeWithText("Tu dors 1 h 30 de moins", substring = true).assertExists() // said once, not for "café" and "café après 16 h"
        compose.onNodeWithText("pas des causes", substring = true).assertExists()
    }
}
