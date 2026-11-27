package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.medipote.app.data.AppState
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.WaterSettings
import com.medipote.app.domain.Game
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.theme.MediPoteTheme
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
@Config(sdk = [35], qualifiers = "w400dp-h2200dp-xhdpi")
class V34ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 27)
    private val now = day.atTime(15, 0)

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun home(state: AppState, onAddDrink: (Int, Boolean) -> Unit = { _, _ -> }, onRemoveDrink: (Int) -> Unit = {}) {
        compose.setContent {
            MediPoteTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(state, now, 0, 0, {}, {}, {}, {}, animate = false, onAddDrink = onAddDrink, onRemoveDrink = onRemoveDrink)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun state(hot: Boolean = false): AppState {
        var s = AppState(profile = Profile("Alex"), onboarded = true, settings = Settings(water = WaterSettings(enabled = true, countHotDrinks = hot)))
        s = Game.addDrink(s, 150, now.withHour(9).withMinute(10)).first
        s = Game.addDrink(s, 250, now.withHour(11).withMinute(40)).first
        return s
    }

    @Test
    fun theCardShowsTheTotalAndTheQuickQuantities() {
        home(state())
        shot("water_card")
        compose.onNodeWithText("400 ml / 2 L").assertExists()
        listOf("+ 150 ml", "+ 250 ml", "+ 330 ml", "+ 500 ml", "Autre quantité").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun aTapNotesTheQuantity() {
        var noted: Pair<Int, Boolean>? = null
        home(state(), onAddDrink = { ml, hot -> noted = ml to hot })
        compose.onNodeWithText("+ 330 ml").performClick()
        assertEquals(330 to false, noted)
    }

    @Test
    fun anyQuantityCanBeTyped() {
        var noted: Pair<Int, Boolean>? = null
        home(state(), onAddDrink = { ml, hot -> noted = ml to hot })
        compose.onNodeWithText("Autre quantité").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("180")
        compose.onNodeWithText("Ajouter").performClick()
        assertEquals(180 to false, noted)
    }

    @Test
    fun anAbsurdQuantityCannotBeAdded() {
        var noted: Pair<Int, Boolean>? = null
        home(state(), onAddDrink = { ml, hot -> noted = ml to hot })
        compose.onNodeWithText("Autre quantité").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("5")
        compose.onNodeWithText("Ajouter").performClick()
        assertNull(noted)
    }

    @Test
    fun theDayCanBeReadAndCorrected() {
        var removed: Int? = null
        home(state(), onRemoveDrink = { removed = it })
        compose.onNodeWithText("Voir ce que j'ai bu (2)").performClick()
        compose.onNodeWithText("09:10 · 150 ml").assertExists()
        compose.onNodeWithText("11:40 · 250 ml").assertExists()
        shot("water_detail")
        compose.onAllNodesWithContentDescription("Retirer cette boisson")[0].performClick()
        assertEquals(0, removed)
        compose.onNodeWithContentDescription("Retirer la dernière boisson").performClick()
        assertEquals(1, removed)
    }

    @Test
    fun hotDrinksAreOfferedOnlyWhenChosen() {
        home(state(hot = false))
        compose.onNodeWithText("☕ Boisson chaude (sans sucre)").assertDoesNotExist()
    }

    @Test
    fun aHotDrinkIsMarkedAsSuch() {
        var noted: Pair<Int, Boolean>? = null
        home(state(hot = true), onAddDrink = { ml, hot -> noted = ml to hot })
        compose.onNodeWithText("☕ Boisson chaude (sans sucre)").performClick()
        compose.onNodeWithText("+ 250 ml").performClick()
        assertEquals(250 to true, noted)
    }
}
