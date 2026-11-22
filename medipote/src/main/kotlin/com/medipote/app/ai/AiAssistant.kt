package com.medipote.app.ai

import com.medipote.app.data.AppState
import com.medipote.app.domain.Understand
import com.medipote.app.domain.Urgency
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiExchange(val question: String?, val answer: AiAnswer)

data class AiSessionState(
    val topic: AiTopic? = null,
    val exchanges: List<AiExchange> = emptyList(),
    val busy: Boolean = false,
    val failure: AiFailureKind? = null,
    val refused: Boolean = false,
    /** The exact data sent with the first request, so the person can read it. */
    val sent: String = "",
    /** The most urgent level the app's own rules found for this topic. */
    val floor: Urgency? = null,
)

/**
 * One conversation with the assistant about a topic, kept in memory only: leaving the screen forgets it. The app's own
 * rules always come first; the answer is cleaned by [AiGuard] and its level can only rise above the rules'.
 */
class AiAssistant(
    private val client: AiClient,
    private val vault: KeyVault,
    private val state: () -> AppState,
    private val today: () -> LocalDate,
    private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow(AiSessionState())
    val ui: StateFlow<AiSessionState> = _ui.asStateFlow()
    private val history = mutableListOf<AiTurn>()

    fun hasKey(): Boolean = !vault.load().isNullOrBlank()

    /** The most urgent level the app's rules gave for [topic]. */
    fun floorFor(topic: AiTopic): Urgency? = AiContext.concerns(state(), topic, today()).map { it.urgency }.maxOrNull()

    /** Opens a conversation on [topic] and asks the first question; an already open one on the same topic is kept. */
    fun open(topic: AiTopic) {
        if (_ui.value.topic == topic && (_ui.value.exchanges.isNotEmpty() || _ui.value.busy)) return
        history.clear()
        val s = state()
        val shares = s.settings.ai.shares
        val context = AiContext.build(s, topic, shares, today())
        _ui.value = AiSessionState(topic = topic, sent = context, floor = floorFor(topic))
        send(AiPrompt.firstMessage(context), question = null)
    }

    /** A question of the person, in the same conversation (six at most: the cost stays small). */
    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _ui.value.busy || _ui.value.exchanges.size > MAX_FOLLOW_UPS) return
        send(AiPrompt.followUp(q), question = q)
    }

    fun retry() {
        val ui = _ui.value
        val topic = ui.topic ?: return
        if (ui.busy) return
        if (history.isEmpty()) { _ui.value = AiSessionState(); open(topic) } else {
            // The last request failed: the same turns are sent again.
            _ui.update { it.copy(failure = null, refused = false) }
            dispatch(question = null, resend = true)
        }
    }

    fun close() {
        history.clear()
        _ui.value = AiSessionState()
    }

    private fun send(text: String, question: String?) {
        history += AiTurn(fromPerson = true, text = text)
        _ui.update { it.copy(busy = true, failure = null, refused = false) }
        dispatch(question, resend = false)
    }

    private fun dispatch(question: String?, resend: Boolean) {
        val key = vault.load()
        if (key.isNullOrBlank()) {
            _ui.update { it.copy(busy = false, failure = AiFailureKind.NO_KEY) }
            return
        }
        _ui.update { it.copy(busy = true) }
        scope.launch {
            when (val r = client.complete(key, AiPrompt.SYSTEM, history.toList())) {
                is AiResult.Ok -> {
                    history += AiTurn(fromPerson = false, text = r.text)
                    val answer = AiGuard.apply(AiAnswer.parse(r.text), _ui.value.floor)
                    _ui.update { it.copy(busy = false, exchanges = it.exchanges + AiExchange(question, answer)) }
                }
                AiResult.Refused -> { dropLastPersonTurn(); _ui.update { it.copy(busy = false, refused = true) } }
                is AiResult.Failure -> _ui.update { it.copy(busy = false, failure = r.kind) }
            }
        }
    }

    private fun dropLastPersonTurn() {
        if (history.lastOrNull()?.fromPerson == true && history.size > 1) history.removeAt(history.lastIndex)
    }

    /** A tiny request to check that a key works, before it is kept. */
    suspend fun test(candidate: String): AiResult =
        client.complete(candidate, "Réponds uniquement par le mot OK.", listOf(AiTurn(true, "Test de connexion.")), maxTokens = 400)

    companion object {
        const val MAX_FOLLOW_UPS = 6
    }
}
