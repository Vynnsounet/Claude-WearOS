package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Measurement
import com.medipote.app.data.MeasureType
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.SymptomNote
import com.medipote.app.domain.Understand
import com.medipote.app.ui.HubPreviews
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.understand.CheckupScreen
import com.medipote.app.ui.wellbeing.SymptomAdviceScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
class UnderstandScreensTest {
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

    private fun night(daysAgo: Int, minutes: Int): SleepNight {
        val date = day.minusDays(daysAgo.toLong())
        val bed = date.minusDays(1).atTime(23, 0)
        return SleepNight(date, bed, 15, bed.plusMinutes(minutes + 15L))
    }

    private val calm = AppState(profile = Profile("Alex"), onboarded = true)
    private val worried = calm.copy(
        symptomLog = listOf(SymptomNote(day, "pain", 4), SymptomNote(day.minusDays(1), "pain", 5), SymptomNote(day, "fatigue", 3)),
        measurements = listOf(Measurement("o", MeasureType.OXYGEN, day.atTime(8, 0), 90.0)),
        sleep = listOf(night(0, 5 * 60), night(1, 5 * 60), night(2, 5 * 60)),
        journal = (0..9).map { DayEntry(day.minusDays(it.toLong()), mental = 2) },
    )

    @Test
    fun calmNotesSayRienDInhabituel() {
        show { CheckupScreen(calm, day, {}, {}, null, {}, {}, {}) }
        compose.onNodeWithText("Rien d'inhabituel", substring = true).assertExists()
        compose.onNodeWithText("Ce n'est pas une IA", substring = true).assertExists()
        compose.onAllNodesWithText("Appeler le 15", substring = true).assertCountEquals(0)
    }

    @Test
    fun anUrgentConcernOffersTheCall_andCannotBeSetAside() {
        var called: String? = null
        show { CheckupScreen(worried, day, onCall = { called = it }, onBook = {}, onPrepare = null, onDismiss = {}, onOpenSymptom = {}, onBack = {}) }
        shot("checkup")
        compose.onNodeWithText("Une saturation basse").assertExists()
        compose.onNodeWithText("📞  Appeler le 15").performScrollTo().performClick()
        assertEquals("15", called)
        compose.onNodeWithText("112").performScrollTo().performClick()
        assertEquals("112", called)
    }

    @Test
    fun aSoonConcernOffersAnAppointment_andCanBeSetAside() {
        var booked = 0
        var dismissed: String? = null
        show { CheckupScreen(worried, day, {}, onBook = { booked++ }, onPrepare = null, onDismiss = { dismissed = it }, onOpenSymptom = {}, onBack = {}) }
        compose.onNodeWithText("Douleur fort plusieurs jours de suite").assertExists()
        compose.onAllNodesWithText("📅  Prendre rendez-vous")[0].performScrollTo().performClick()
        assertEquals(1, booked)
        compose.onAllNodesWithText("Je l'ai vu")[0].performScrollTo().performClick()
        assertTrue(dismissed!!.isNotEmpty())
    }

    @Test
    fun aMentionOffersToPrepareTheNextAppointmentOnlyIfThereIsOne() {
        val mention = calm.copy(measurements = listOf(Measurement("a", MeasureType.WEIGHT, day.minusDays(25).atTime(8, 0), 70.0), Measurement("b", MeasureType.WEIGHT, day.minusDays(1).atTime(8, 0), 65.0)))
        show { CheckupScreen(mention, day, {}, {}, null, {}, {}, {}) }
        compose.onNodeWithText("Un poids qui change vite").assertExists()
        compose.onAllNodesWithText("📝  Préparer mon rendez-vous").assertCountEquals(0)
    }

    @Test
    fun aMentionWithAnAppointmentOffersToPrepareIt() {
        var prepared = 0
        val mention = calm.copy(measurements = listOf(Measurement("a", MeasureType.WEIGHT, day.minusDays(25).atTime(8, 0), 70.0), Measurement("b", MeasureType.WEIGHT, day.minusDays(1).atTime(8, 0), 65.0)))
        show { CheckupScreen(mention, day, {}, {}, onPrepare = { prepared++ }, onDismiss = {}, onOpenSymptom = {}, onBack = {}) }
        compose.onNodeWithText("📝  Préparer mon rendez-vous").performScrollTo().performClick()
        assertEquals(1, prepared)
    }

    @Test
    fun theTrackedSymptomsOpenTheirExplanation() {
        var opened: String? = null
        show { CheckupScreen(worried, day, {}, {}, null, {}, onOpenSymptom = { opened = it }, onBack = {}) }
        compose.onNodeWithText("🤕  Douleur").performScrollTo().performClick()
        assertEquals("pain", opened)
    }

    @Test
    fun theSymptomPageExplainsAndWarns() {
        val s = worried.copy(treatments = emptyList())
        show {
            SymptomAdviceScreen(
                "fatigue", {}, {}, {},
                factors = Understand.factors(s, "fatigue", day),
                concerns = Understand.symptomConcerns(s, "fatigue", day),
            )
        }
        shot("symptom_understand")
        compose.onNodeWithText("Chez toi, ça peut venir de…").assertExists()
        compose.onNodeWithText("Peu de sommeil").assertExists()
        compose.onNodeWithText("Quand en parler à un médecin").performScrollTo().assertExists()
    }

    @Test
    fun theSymptomPageSaysSoWhenNothingExplainsIt() {
        show { SymptomAdviceScreen("fatigue", {}, {}, {}, factors = emptyList()) }
        compose.onNodeWithText("Rien dans tes notes", substring = true).assertExists()
    }

    @Test
    fun homeTellsAnUrgentMatter_andOpensTheCheckup() {
        var opened = 0
        show { HomeScreen(worried, now, 0, 0, {}, {}, {}, {}, animate = false, onOpenCheckup = { opened++ }) }
        compose.onNodeWithText("À ne pas laisser passer").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun homeStaysQuietWhenTheNotesAreCalm_andWhenTheAlertIsOff() {
        show { HomeScreen(calm, now, 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onAllNodesWithText("À ne pas laisser passer").assertCountEquals(0)
        compose.onAllNodesWithText("Ça vaut peut-être un avis médical").assertCountEquals(0)
    }

    @Test
    fun homeStaysQuietWhenThePersonTurnedTheAlertOff() {
        show { HomeScreen(worried.copy(settings = Settings(checkups = false)), now, 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onAllNodesWithText("À ne pas laisser passer").assertCountEquals(0)
    }

    private val soonOnly = calm.copy(symptomLog = listOf(SymptomNote(day, "pain", 4), SymptomNote(day.minusDays(1), "pain", 5)))

    @Test
    fun aSoonConcernShowsOnHome() {
        show { HomeScreen(soonOnly, now, 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onNodeWithText("Ça vaut peut-être un avis médical").assertExists()
    }

    @Test
    fun aSetAsideSoonConcernLeavesTheHome() {
        val dismissed = soonOnly.copy(settings = Settings(dismissedChecks = Understand.dismiss(emptyMap(), "sym:pain:strong", day)))
        show { HomeScreen(dismissed, now, 0, 0, {}, {}, {}, {}, animate = false) }
        compose.onAllNodesWithText("Ça vaut peut-être un avis médical").assertCountEquals(0)
    }

    @Test
    fun theHubPreviewCounts() {
        assertEquals("Rien d'inhabituel dans tes notes", HubPreviews.checkup(calm, day))
        assertTrue(HubPreviews.checkup(worried, day).endsWith("à regarder"))
    }
}
