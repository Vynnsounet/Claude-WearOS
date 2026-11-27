package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Settings
import com.medipote.app.data.SymptomNote
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.DayFactors
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.wellbeing.DayEffectsCard
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1200dp-xhdpi")
class V36ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 12, 14)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    @Test
    fun theCardSaysWhatGoesWithTheHardDays() {
        val log = (0 until 20).flatMap { i ->
            val d = today.minusDays(i.toLong())
            listOfNotNull(SymptomNote(d, "fatigue", 1), SymptomNote(d, "headache", 4).takeIf { i < 10 })
        }
        val state = AppState(
            symptomLog = log, journal = (0 until 20).map { DayEntry(today.minusDays(it.toLong()), waterGlasses = if (it < 10) 2 else 8) },
            settings = Settings(wellbeing = WellbeingSettings(enabled = true, symptoms = listOf("headache", "fatigue"))),
        )
        val effects = DayFactors.shown(state, today)
        show { DayEffectsCard(effects, anyNoted = true) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File("build/screenshots").apply { mkdirs() }.let { dir -> java.io.File(dir, "day_effects.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        compose.onNodeWithText("🔍  Ce qui change tes jours").assertExists()
        compose.onNodeWithText("💧  Les jours de peu d'eau").assertExists()
        compose.onNodeWithText("plus intense les jours de peu d'eau", substring = true).assertExists()
        compose.onNodeWithText("pas des causes", substring = true).assertExists()
    }

    @Test
    fun theCardExplainsWhyThereIsNothingYet() {
        show { DayEffectsCard(emptyList(), anyNoted = false) }
        compose.onNodeWithText("Note ton moral, ta forme ou tes symptômes", substring = true).assertExists()
    }

    @Test
    fun theCardSaysWhenNothingStandsOut() {
        show { DayEffectsCard(emptyList(), anyNoted = true) }
        compose.onNodeWithText("Rien de net pour l'instant", substring = true).assertExists()
    }
}
