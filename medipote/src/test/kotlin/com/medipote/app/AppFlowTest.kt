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

        // What accompanies the person: optional, skipped here
        compose.onNodeWithText("Ce qui t'accompagne").assertExists()
        compose.onNodeWithText("Passer pour l'instant").performScrollTo().performClick()

        // Treatments: search the guide and add one
        compose.onNodeWithText("Tes traitements en cours").assertExists()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Ajouter")), useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("doliprane")
        compose.onNodeWithText("Paracétamol").performClick()
        compose.onNodeWithText("Nouveau traitement").assertExists()
        compose.onNodeWithText("Enregistrer").performClick()
        compose.onNodeWithText("Paracétamol 500 mg").assertExists()
        compose.onNodeWithText("C'est parti !").performScrollTo().performClick()

        // Daily streak celebration on the first day
        compose.onAllNodes(androidx.compose.ui.test.hasText("C'est parti !")).onFirst().assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("jour")).onFirst().assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("C'est parti !") and androidx.compose.ui.test.hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Bonjour Sam 👋").assertExists()

        val repo = ApplicationProvider.getApplicationContext<MediPoteApplication>().repository
        val state = repo.state.value
        assertTrue(state.onboarded)
        assertEquals("Sam", state.profile?.name)
        assertEquals(1, state.treatments.size)
        assertEquals(1, state.game.streak)

        // A dose created at 08:00 by default: give it if it's giveable right now, else check it's listed.
        val giveButtons = compose.onAllNodesWithText("Donner", substring = true).fetchSemanticsNodes()
        if (giveButtons.isNotEmpty()) {
            val before = repo.state.value.game.points
            compose.onAllNodesWithText("Donner", substring = true).onFirst().performClick()
            compose.waitForIdle()
            assertEquals(1, repo.state.value.doseLogs.size)
            assertTrue(repo.state.value.game.points > before)
        } else {
            // Before 07:00 the dose is shown as the next one; after 08:30 it was added too late to be owed today.
            val listed = compose.onAllNodesWithText("08:00").fetchSemanticsNodes().isNotEmpty()
            val none = compose.onAllNodesWithText("Aujourd'hui").fetchSemanticsNodes().isEmpty()
            assertTrue(listed || none)
        }

        // Tabs are reachable
        // Each tab is a page of cards; a card opens its content under a back arrow.
        compose.onNodeWithText("Moi").performClick()
        compose.onNodeWithText("Statistiques").performClick()
        compose.onNodeWithText("14 derniers jours").assertExists()
        compose.onNodeWithText("Soins").performClick()
        compose.onNodeWithText("Agenda").performClick()
        compose.onNodeWithText("À venir").assertExists()
        compose.onNodeWithText("Suivi").performClick()
        compose.onNodeWithText("Sommeil").performClick()
        compose.onNodeWithText("Comment as-tu dormi cette nuit ?").assertExists()
        compose.onNodeWithText("Moi").performClick()
        compose.onNodeWithContentDescription("Retour").performClick() // back from the statistics to the page of cards
        compose.onNodeWithText("Boutique").performClick()
        compose.onNodeWithText("Touche un objet", substring = true).assertExists()
        compose.onNodeWithText("Mieux-être").performClick()
        compose.onNodeWithText("Ça ne va pas").assertExists()
        compose.onNodeWithText("Accueil").performClick()
        compose.onNodeWithContentDescription("Paramètres").performClick()
        compose.onNodeWithText("Sombre").performClick()
        compose.waitForIdle()
        assertEquals(com.medipote.app.data.ThemeMode.DARK, repo.state.value.settings.themeMode)
    }
}
