package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DoseChange
import com.medipote.app.data.DoseTime
import com.medipote.app.data.LabResult
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.Treatment
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.timeline.TimelineScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2000dp-xhdpi")
class V37ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 12, 20)
    private fun d(i: Int) = today.minusDays(i.toLong())

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    private fun state(): AppState {
        val at = d(30)
        val nights = (1..14).map { night(at.minusDays(it.toLong()), 6.0) } + (0 until 14).map { night(at.plusDays(it.toLong()), 7.5) }
        val journal = (1..14).map { DayEntry(at.minusDays(it.toLong()), mental = 2) } + (0 until 14).map { DayEntry(at.plusDays(it.toLong()), mental = 4) }
        val t = Treatment("levo", name = "Lévothyrox", strength = "75 µg", doses = listOf(DoseTime(8 * 60)), startDate = d(100), createdAt = d(100).atStartOfDay(), changes = listOf(DoseChange(at, "Lévothyrox : 75 µg → 88 µg")))
        return AppState(onboarded = true, treatments = listOf(t), sleep = nights, journal = journal, labs = listOf(LabResult("1", "tsh", d(10), 2.2, "mUI/L")), settings = Settings())
    }

    private fun night(date: LocalDate, hours: Double): SleepNight {
        val bed = date.minusDays(1).atTime(23, 0)
        return SleepNight(date, bed, 15, bed.plusMinutes((hours * 60).toLong() + 15))
    }

    @Test
    fun theLineShowsWhatHappened_andOpensABeforeAfter() {
        show { TimelineScreen(state(), today) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File("build/screenshots").apply { mkdirs() }.let { dir -> java.io.File(dir, "timeline.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        // the fortnight of low mood before the change is a hard stretch of its own
        compose.onNodeWithText("1 changement de traitement · 1 analyse · 1 période difficile").assertExists()
        compose.onNodeWithText("Lévothyrox : 75 µg → 88 µg").assertExists()
        compose.onNodeWithText("TSH : 2,2 mUI/L").assertExists()
        compose.onNodeWithText("Voir avant / après").performClick()
        compose.onNodeWithText("14 jours avant → 14 jours depuis").assertExists()
        compose.onNodeWithText("6 h  →  7 h 30").assertExists()
        compose.onNodeWithText("2,0/5  →  4,0/5").assertExists()
        compose.onNodeWithText("d'autres choses ont pu changer", substring = true).assertExists()
    }

    @Test
    fun aLabOpensItsCurve() {
        var opened: String? = null
        show { TimelineScreen(state(), today, onOpenLab = { opened = it }) }
        compose.onNodeWithText("Voir la courbe").performClick()
        assertEquals("tsh", opened)
    }

    @Test
    fun theTreatmentOpens() {
        var opened: String? = null
        show { TimelineScreen(state(), today, onOpenTreatment = { opened = it }) }
        compose.onNodeWithText("Voir le traitement").performClick()
        assertEquals("levo", opened)
    }

    @Test
    fun anEmptyPeriodSaysSo() {
        show { TimelineScreen(AppState(onboarded = true), today) }
        compose.onNodeWithText("Rien de marquant noté").assertExists()
        compose.onNodeWithText("Rien de marquant sur cette période", substring = true).assertExists()
    }

    @Test
    fun thePeriodCanBeChanged() {
        show { TimelineScreen(state(), today) }
        compose.onNodeWithText("1 mois").performClick()
        compose.onNodeWithText("1 analyse").assertExists() // the change is 30 days ago: outside a 30-day window, the blood test is not
        compose.onNodeWithText("1 an").performClick()
        compose.onNodeWithText("2 changements de traitement", substring = true).assertExists() // the start (100 days ago) and the change
    }
}
