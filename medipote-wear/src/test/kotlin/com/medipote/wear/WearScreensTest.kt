package com.medipote.wear

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.wear.compose.material3.AppScaffold
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchDose
import com.medipote.sync.WatchPrn
import com.medipote.sync.WatchReminder
import com.medipote.sync.WatchSnapshot
import com.medipote.wear.ui.HomeScreen
import com.medipote.wear.ui.MediPoteWearTheme
import com.medipote.wear.ui.PulseContent
import com.medipote.wear.ui.WearActions
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the watch screens on a round watch and saves PNGs to medipote-wear/build/screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w225dp-h225dp-small-notlong-round-watch-xhdpi-keyshidden-nonav")
class WearScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    private val snapshot = sampleSnapshot(day)

    @Test
    fun homeWithADoseDue() {
        var taken: List<WatchDose>? = null
        render("wear_home") { HomeScreen(WearState(snapshot), at(8, 5), null, WearActions(onTake = { taken = it }), onOpenHeartRate = {}) }
        compose.onNodeWithText("Pilou").assertExists()
        compose.onNodeWithText("1 / 4 prises faites").assertExists()
        // One tap, one confirmation: both medications of 8:00 are given together.
        compose.onNodeWithText("08:00 · Metformine 1000 mg, Vitamine D3").performClick()
        compose.onNodeWithText("Tout est pris ?").assertExists()
        save("wear_confirm", compose.onNode(isDialog()).captureToImage().asAndroidBitmap())
        compose.onNodeWithText("Tout est pris ?").assertExists()
        assertEquals(null, taken)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w225dp-h900dp-round-watch-xhdpi-keyshidden-nonav")
    fun homeFull() {
        var water = 0
        val state = WearState(snapshot, pending = listOf(WatchAction("x", WatchAction.WATER, at(8, 1).toString(), value = 1.0)))
        render("wear_home_full") { HomeScreen(state, at(8, 5), null, WearActions(onWater = { water++ }), onOpenHeartRate = {}) }
        // The glass noted on the watch shows at once, while it waits for the phone.
        compose.onNodeWithText("💧 4 / 8 verres").assertExists()
        compose.onNodeWithText("1 action en attente du téléphone").assertExists()
        compose.onNodeWithText("Ibuprofène 400 mg").assertExists()
        compose.onNodeWithText("❤️ Mesurer mon pouls").assertExists()
        compose.onNodeWithText("72 bpm · il y a 2 h").assertExists()
        compose.onNodeWithText("💧 4 / 8 verres").performClick()
        assertEquals(1, water)
    }

    @Test
    fun notLinkedYet() {
        render("wear_empty") { HomeScreen(WearState(), at(8, 5), null, WearActions(), onOpenHeartRate = {}) }
        compose.onNodeWithText("Ouvre MédiPote sur ton téléphone pour relier la montre.").assertExists()
    }

    @Test
    fun pulse() {
        var saved: Int? = null
        render("wear_pulse") { PulseContent(available = true, allowed = true, live = 71, steady = 72, timedOut = false, onAllow = {}, onRetry = {}, onSave = { saved = it }) }
        compose.onNodeWithText("72").assertExists()
        compose.onNodeWithText("Enregistrer").performClick()
        assertEquals(72, saved)
    }

    @Test
    fun pulseMeasuring() {
        render("wear_pulse_measuring") { PulseContent(available = true, allowed = true, live = null, steady = null, timedOut = false, onAllow = {}, onRetry = {}, onSave = {}) }
        compose.onNodeWithText("Reste immobile, le bras posé, quelques secondes.").assertExists()
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        compose.setContent { MediPoteWearTheme { AppScaffold { content() } } }
        compose.waitForIdle()
        save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

fun sampleSnapshot(day: LocalDate): WatchSnapshot {
    fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    return WatchSnapshot(
        generatedAt = at(8).toString(), petName = "Pilou", userName = "Alex", streak = 6, points = 726,
        petLine = "C'est l'heure ! Pilou attend Metformine.",
        doses = listOf(
            WatchDose("a", "Lévothyroxine 75 µg", "1 comprimé", at(7, 30).toString(), taken = true),
            WatchDose("b", "Metformine 1000 mg", "1 comprimé", at(8).toString(), withFood = true),
            WatchDose("b2", "Vitamine D3", "1 ampoule", at(8).toString()),
            WatchDose("c", "Amlodipine 5 mg", "1 gélule", at(12).toString()),
            WatchDose("d", "Metformine 1000 mg", "1 comprimé", at(8).plusDays(1).toString()),
        ),
        reminders = listOf(
            WatchReminder(WatchReminder.DOSE, at(8).toString(), at(8).toString()),
            WatchReminder(WatchReminder.FOLLOW_UP, at(8, 30).toString(), at(8).toString()),
            WatchReminder(WatchReminder.DOSE, at(12).toString(), at(12).toString()),
            WatchReminder(WatchReminder.WATER, at(11).toString()),
        ),
        waterEnabled = true, waterDate = day.toString(), glasses = 3, goal = 8,
        prn = listOf(WatchPrn("ibu", "Ibuprofène 400 mg", "1 comprimé", at(5).toString())),
        lastHeartRate = 72, lastHeartRateAt = at(6).toString(),
    )
}
