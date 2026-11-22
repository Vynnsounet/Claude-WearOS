package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.medipote.app.ai.AiAnswer
import com.medipote.app.ai.AiExchange
import com.medipote.app.ai.AiFailureKind
import com.medipote.app.ai.AiSessionState
import com.medipote.app.data.AiSettings
import com.medipote.app.data.AiShare
import com.medipote.app.data.AppState
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.domain.Concern
import com.medipote.app.domain.Urgency
import com.medipote.app.ui.ai.AiAssistantScreen
import com.medipote.app.ui.ai.AiSetupScreen
import com.medipote.app.ui.settings.SettingsScreen
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.understand.CheckupScreen
import com.medipote.app.ui.wellbeing.SymptomAdviceScreen
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
@Config(sdk = [35], qualifiers = "w400dp-h2200dp-xhdpi")
class AiScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 19)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ───────────── Setup ─────────────
    private fun setup(ai: AiSettings = AiSettings(), hasKey: Boolean = false, onActivate: (String?, (AiFailureKind?) -> Unit) -> Unit = { _, done -> done(null) }, onShares: (Set<AiShare>) -> Unit = {}, onDisable: () -> Unit = {}) = show {
        AiSetupScreen(ai, hasKey, preview = "Date : 2026-11-19\nSujet demandé : un bilan", onShares = onShares, onActivate = onActivate, onDisable = onDisable, onOpenConsole = {}, onBack = {})
    }

    @Test
    fun theSetupSaysWhatIsSentAndToWhom() {
        setup()
        shot("ai_setup")
        compose.onNodeWithText("Ce qui part sur Internet").assertExists()
        compose.onNodeWithText("Seulement quand tu touches « Demander à l'IA », jamais en arrière-plan.").assertExists()
        compose.onNodeWithText("Jamais : ton nom", substring = true).assertExists()
        // The seven categories, one switch each.
        AiShare.entries.forEach { compose.onNodeWithText(it.label).assertExists() }
    }

    @Test
    fun theAiCannotBeTurnedOnWithoutAKeyAndConsent() {
        var activated: String? = null
        setup(onActivate = { k, done -> activated = k; done(null) })
        val button = compose.onNodeWithText("Vérifier ma clé et activer l'IA")
        button.performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("sk-ant-abc")
        button.assertIsNotEnabled() // the consent is still missing
        compose.onNodeWithText("J'ai compris", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Vérifier ma clé et activer l'IA").performScrollTo().assertIsEnabled().performClick()
        assertEquals("sk-ant-abc", activated)
    }

    @Test
    fun aRefusedKeyIsExplained() {
        setup(onActivate = { _, done -> done(AiFailureKind.AUTH) })
        compose.onNode(hasSetTextAction()).performTextInput("sk-ant-bad")
        compose.onNodeWithText("J'ai compris", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Vérifier ma clé et activer l'IA").performScrollTo().performClick()
        compose.onNodeWithText(AiFailureKind.AUTH.message).assertExists()
    }

    @Test
    fun aCategoryCanBeSwitchedOff() {
        var shares: Set<AiShare>? = null
        setup(onShares = { shares = it })
        compose.onNodeWithText(AiShare.TREATMENTS.label).performScrollTo()
        // The switch of « Mes traitements » is the last one of the list.
        compose.onAllNodesWithText(AiShare.TREATMENTS.detail)[0].assertExists()
        val toggles = compose.onAllNodes(androidx.compose.ui.test.isToggleable())
        toggles[AiShare.entries.indexOf(AiShare.TREATMENTS)].performScrollTo().performClick()
        assertEquals(AiShare.entries.toSet() - AiShare.TREATMENTS, shares)
    }

    @Test
    fun theExactTextCanBeRead() {
        setup()
        compose.onNodeWithText("👁️  Voir exactement ce qui serait envoyé").performScrollTo().performClick()
        compose.onNodeWithText("Sujet demandé : un bilan", substring = true).assertExists()
    }

    @Test
    fun anActiveAiCanBeDisabledAndItsKeyDeleted() {
        var disabled = 0
        setup(ai = AiSettings(enabled = true, consentAt = "2026-11-19T10:00"), hasKey = true, onDisable = { disabled++ })
        compose.onNodeWithText("✅  L'IA est activée", substring = true).assertExists()
        compose.onAllNodesWithText("J'ai compris", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Désactiver l'IA et supprimer ma clé").performScrollTo().performClick()
        compose.onNodeWithText("Désactiver").performClick()
        assertEquals(1, disabled)
    }

    // ───────────── Assistant ─────────────
    private val answer = AiAnswer(
        summary = "Tu as peu dormi ces derniers jours.",
        explanations = listOf(com.medipote.app.ai.AiPoint("Sommeil", "Tu as dormi 5 h en moyenne.")),
        relief = listOf("Couche-toi plus tôt.", "Bois un grand verre d'eau."),
        level = Urgency.MENTION, reason = "À évoquer au prochain rendez-vous.", questions = listOf("Et pour ce soir ?"),
    )

    private fun assistant(
        session: AiSessionState, rules: List<Concern> = emptyList(), onAsk: (String) -> Unit = {}, onRetry: () -> Unit = {},
        onCall: (String) -> Unit = {}, onSettings: () -> Unit = {},
    ) = show {
        AiAssistantScreen("😴  Fatigue", session, rules, onAsk, onRetry, onCall, {}, null, {}, onSettings, {})
    }

    @Test
    fun theAnswerIsShownInCards_andFollowUpsAreOffered() {
        var asked: String? = null
        assistant(AiSessionState(exchanges = listOf(AiExchange(null, answer)), sent = "Date : 2026-11-19"), onAsk = { asked = it })
        shot("ai_assistant")
        compose.onNodeWithText("Tu as peu dormi ces derniers jours.").assertExists()
        compose.onNodeWithText("Ce qui peut l'expliquer chez toi").assertExists()
        compose.onNodeWithText("Ce qui peut aider").assertExists()
        compose.onNodeWithText("Couche-toi plus tôt.").assertExists()
        compose.onNodeWithText("Avis de l'IA").assertExists()
        // What the AI said is not kept: nothing to « set aside ».
        compose.onAllNodesWithText("Je l'ai vu").assertCountEquals(0)
        compose.onNodeWithText("Et pour ce soir ?").performScrollTo().performClick()
        assertEquals("Et pour ce soir ?", asked)
        compose.onNodeWithText("Réponse écrite par une IA", substring = true).assertExists()
    }

    @Test
    fun aQuestionCanBeTyped() {
        var asked: String? = null
        assistant(AiSessionState(exchanges = listOf(AiExchange(null, answer))), onAsk = { asked = it })
        compose.onNode(hasSetTextAction()).performTextInput("Est-ce grave ?")
        compose.onNodeWithContentDescription("Envoyer ma question").performClick()
        assertEquals("Est-ce grave ?", asked)
    }

    @Test
    fun theAppsRulesComeFirst_andAnUrgentOneOffersTheCall() {
        var called: String? = null
        val rule = Concern("sym:breath:max", Urgency.URGENT, "Essoufflement au plus fort", listOf("Appelle le 15 si c'est brutal."))
        assistant(AiSessionState(busy = true), rules = listOf(rule), onCall = { called = it })
        compose.onNodeWithText("Ce que l'appli a repéré").assertExists()
        compose.onNodeWithText("Essoufflement au plus fort").assertExists()
        compose.onNodeWithText("📞  Appeler le 15").performScrollTo().performClick()
        assertEquals("15", called)
        // Even while the AI is thinking.
        compose.onNodeWithText("L'IA lit tes notes", substring = true).assertExists()
    }

    @Test
    fun aFailureIsExplained_andCanBeRetried() {
        var retried = 0
        assistant(AiSessionState(failure = AiFailureKind.NETWORK), onRetry = { retried++ })
        compose.onNodeWithText(AiFailureKind.NETWORK.message).assertExists()
        compose.onNodeWithText("Réessayer").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun aRejectedKeyLeadsToTheSettings() {
        var opened = 0
        assistant(AiSessionState(failure = AiFailureKind.AUTH), onSettings = { opened++ })
        compose.onNodeWithText("Réglages de l'IA").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun aRefusalIsShownAsSuch() {
        assistant(AiSessionState(refused = true))
        compose.onNodeWithText("L'IA n'a pas voulu répondre", substring = true).assertExists()
    }

    @Test
    fun whatWasSentCanBeRead() {
        assistant(AiSessionState(exchanges = listOf(AiExchange(null, answer)), sent = "Traitements :\n- Lévothyrox 75 µg"))
        compose.onNodeWithText("👁️  Voir ce qui a été envoyé").performScrollTo().performClick()
        compose.onNodeWithText("Lévothyrox 75 µg", substring = true).assertExists()
    }

    @Test
    fun aReassuringAnswerSaysSoWithoutHidingTheDoctor() {
        assistant(AiSessionState(exchanges = listOf(AiExchange(null, answer.copy(level = null)))))
        compose.onNodeWithText("rien d'inquiétant", substring = true).assertExists()
        compose.onNodeWithText("parles-en à ton médecin", substring = true).assertExists()
    }

    @Test
    fun heldBackLinesAreAnnounced() {
        assistant(AiSessionState(exchanges = listOf(AiExchange(null, answer.copy(filtered = true)))))
        compose.onNodeWithText("retenues par l'appli", substring = true).assertExists()
    }

    // ───────────── Entry points ─────────────
    @Test
    fun theSymptomPageOffersTheAssistant() {
        var asked = 0
        show { SymptomAdviceScreen("fatigue", {}, {}, {}, factors = emptyList(), onAskAi = { asked++ }) }
        compose.onNodeWithText("🤖  Demander à l'IA").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun theSymptomPageHasNoAiButtonWhereNoneIsWired() {
        show { SymptomAdviceScreen("fatigue", {}, {}, {}, factors = emptyList()) }
        compose.onAllNodesWithText("🤖  Demander à l'IA").assertCountEquals(0)
    }

    @Test
    fun theCheckupOffersTheAssistant() {
        var asked = 0
        show { CheckupScreen(AppState(profile = Profile("Alex"), onboarded = true), day, {}, {}, null, {}, {}, {}, onAskAi = { asked++ }) }
        compose.onNodeWithText("🤖  Demander à l'IA").performScrollTo().performClick()
        assertEquals(1, asked)
        // The page's own rules stay what they were: not an AI.
        compose.onNodeWithText("Ce n'est pas une IA", substring = true).assertExists()
    }

    @Test
    fun theSettingsPointToTheAi() {
        var opened = 0
        show {
            SettingsScreen(
                Settings(), "Pilou", notificationsAllowed = true, exactAlarmsAllowed = true, dynamicColorAvailable = false,
                onBack = {}, onChange = {}, onRequestNotifications = {}, onRequestExactAlarms = {}, onRenamePet = {}, onOpenProfile = {}, onReset = {},
                onOpenAi = { opened++ }, openSection = "mind",
            )
        }
        compose.onNodeWithText("🤖  Intelligence artificielle (facultatif)").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theSettingsSayWhenTheAiIsOn() {
        show {
            SettingsScreen(
                Settings(ai = AiSettings(enabled = true)), "Pilou", notificationsAllowed = true, exactAlarmsAllowed = true, dynamicColorAvailable = false,
                onBack = {}, onChange = {}, onRequestNotifications = {}, onRequestExactAlarms = {}, onRenamePet = {}, onOpenProfile = {}, onReset = {},
                openSection = "mind",
            )
        }
        compose.onNodeWithText("🤖  L'IA est activée : réglages").assertExists()
    }
}
