package com.medipote.app

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the real activity: first-run flow, adding a medication from the guide, giving it to the pet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingThenAddAndGiveAMedication() {
        compose.onNodeWithText("Commencer").performScrollTo().performClick()

        // Profile
        compose.onNode(hasSetTextAction() and hasText("Prénom *")).performTextInput("Sam")
        compose.onNodeWithText("Continuer").performClick()

        // Pet
        compose.onNodeWithText("Voici ton compagnon", substring = true).assertExists()
        compose.onNodeWithText("Continuer").performClick()

        // Treatments: search the guide and add one
        compose.onNodeWithText("Tes traitements en cours").assertExists()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Ajouter")), useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("doliprane")
        compose.onNodeWithText("Paracétamol").performClick()
        compose.onNodeWithText("Nouveau traitement").assertExists()
        compose.onNodeWithText("Enregistrer").performClick()
        compose.onNodeWithText("Paracétamol 500 mg").assertExists()
        compose.onNodeWithText("C'est parti !").performScrollTo().performClick()

        // Daily check-in reward on the first day
        compose.onNodeWithText("Bienvenue !").assertExists()
        compose.onNodeWithText("Super !").performClick()
        compose.onNodeWithText("Bonjour Sam 👋").assertExists()

        val repo = ApplicationProvider.getApplicationContext<MediPoteApplication>().repository
        val state = repo.state.value
        assertTrue(state.onboarded)
        assertEquals("Sam", state.profile?.name)
        assertEquals(1, state.treatments.size)
        assertEquals(1, state.game.streak)

        // A dose created at 08:00 by default: give it if it's giveable right now, else check it's listed.
        val giveButtons = compose.onAllNodesWithText("Donner").fetchSemanticsNodes()
        if (giveButtons.isNotEmpty()) {
            val before = repo.state.value.game.points
            compose.onAllNodesWithText("Donner").onFirst().performClick()
            compose.waitForIdle()
            assertEquals(1, repo.state.value.doseLogs.size)
            assertTrue(repo.state.value.game.points > before)
        } else {
            // Before 07:00 the dose is listed as upcoming; after 08:30 it was added too late to be owed today.
            val listed = compose.onAllNodesWithText("08:00").fetchSemanticsNodes().isNotEmpty()
            val none = compose.onAllNodesWithText("Aucune prise prévue aujourd'hui", substring = true).fetchSemanticsNodes().isNotEmpty()
            assertTrue(listed || none)
        }

        // Tabs are reachable
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Statistiques").assertExists()
        compose.onNodeWithText("Boutique").performClick()
        compose.onNodeWithText("Touche un objet", substring = true).assertExists()
        compose.onNodeWithText("Accueil").performClick()
        compose.onNodeWithContentDescription("Paramètres").performClick()
        compose.onNodeWithText("Sombre").performClick()
        compose.waitForIdle()
        assertEquals(com.medipote.app.data.ThemeMode.DARK, repo.state.value.settings.themeMode)
    }
}
