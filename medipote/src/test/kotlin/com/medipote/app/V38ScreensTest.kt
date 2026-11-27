package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.medipote.app.data.AppState
import com.medipote.app.data.Profile
import com.medipote.app.data.Vaccination
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.vaccines.VaccinesScreen
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
@Config(sdk = [35], qualifiers = "w400dp-h2600dp-xhdpi")
class V38ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 20)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    private fun person(birth: Int? = 1950, risk: Boolean = false, doses: List<Vaccination> = emptyList()) =
        AppState(onboarded = true, profile = Profile("Alex", birthYear = birth, vaccineRisk = risk), vaccinations = doses)

    private fun screen(state: AppState, onAdd: (String, LocalDate, String) -> Unit = { _, _, _ -> }, onRemove: (String) -> Unit = {}, onProfile: (Int?, Boolean) -> Unit = { _, _ -> }, onReminders: (Boolean) -> Unit = {}) =
        show { VaccinesScreen(state, today, onAdd, onRemove, onProfile, onReminders) }

    @Test
    fun theCalendarShowsWhatIsDueFirst() {
        screen(person())
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File("build/screenshots").apply { mkdirs() }.let { dir -> java.io.File(dir, "vaccines.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        compose.onNodeWithText("Ton calendrier à 76 ans").assertExists()
        compose.onNodeWithText("🤧  Grippe").assertExists()
        compose.onAllNodesWithText("C'est la saison : la campagne a lieu d'octobre à janvier.")[0].assertExists() // flu and Covid-19
        compose.onAllNodesWithText("À faire")[0].assertExists()
        compose.onNodeWithText("Calendrier simplifié", substring = true).assertExists()
    }

    @Test
    fun aVaccineIsNoted() {
        var noted: Triple<String, LocalDate, String>? = null
        screen(person(), onAdd = { v, d, n -> noted = Triple(v, d, n) })
        compose.onAllNodesWithText("Noter ce vaccin")[0].performClick()
        compose.onNode(hasSetTextAction() and hasText("Un mot (facultatif) : lieu, lot…")).performTextInput("pharmacie")
        compose.onAllNodesWithText("Enregistrer")[1].performClick()
        assertTrue(noted!!.first in com.medipote.app.domain.Vaccines.KNOWN)
        assertEquals("pharmacie", noted!!.third)
    }

    @Test
    fun theBirthYearIsKept() {
        var saved: Pair<Int?, Boolean>? = null
        screen(person(birth = 1990), onProfile = { y, r -> saved = y to r })
        compose.onNode(hasSetTextAction() and hasText("Année de naissance")).performTextClearance()
        compose.onNode(hasSetTextAction() and hasText("Année de naissance")).performTextInput("1985")
        compose.onAllNodesWithText("Enregistrer")[0].performClick()
        assertEquals(1985 to false, saved)
    }

    @Test
    fun theRiskAndTheRemindersAreSwitches() {
        var profile: Pair<Int?, Boolean>? = null; var reminders: Boolean? = null
        screen(person(birth = 1985), onProfile = { y, r -> profile = y to r }, onReminders = { reminders = it })
        compose.onAllNodes(isToggleable())[0].performClick()
        assertEquals(1985 to true, profile)
        compose.onAllNodes(isToggleable())[1].performClick()
        assertEquals(false, reminders)
    }

    @Test
    fun aRecordedVaccineCanBeRemoved() {
        var removed: String? = null
        screen(person(doses = listOf(Vaccination("v1", "flu", LocalDate.of(2026, 10, 20), "pharmacie"))), onRemove = { removed = it })
        compose.onNodeWithText("Mes vaccins notés").assertExists()
        compose.onNodeWithText("20 octobre 2026 · pharmacie").assertExists()
        compose.onNodeWithContentDescription("Retirer Grippe du 20 octobre 2026").performClick()
        assertEquals("v1", removed)
    }

    @Test
    fun withoutABirthYearTheScreenAsksForIt() {
        screen(person(birth = null))
        compose.onNodeWithText("Donne ton année de naissance", substring = true).assertExists()
    }

    @Test
    fun theHomeSaysWhenSomethingIsDue() {
        var opened = 0
        val state = person()
        show { HomeScreen(state, today.atTime(10, 0), 0, 0, {}, {}, {}, {}, animate = false, onOpenVaccines = { opened++ }) }
        compose.onNodeWithText("Vaccins : grippe", substring = true).performClick()
        assertEquals(1, opened)
    }
}
