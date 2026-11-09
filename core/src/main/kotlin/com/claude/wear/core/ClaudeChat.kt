package com.claude.wear.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.helpers.BetaMessageAccumulator
import com.anthropic.models.beta.messages.BetaContentBlock
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive
import com.anthropic.models.beta.messages.MessageCreateParams
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A multi-turn conversation with Claude, streamed over the Messages API.
 *
 * The full assistant message (thinking blocks included) is appended to the
 * history after each turn, so later requests replay it unchanged (see
 * [replayableContent] for the one exception).
 * Only one message can be in flight at a time.
 */
class ClaudeChat(
    private val client: AnthropicClient,
    private val config: ChatConfig = ChatConfig(),
) {
    private val history = mutableListOf<BetaMessageParam>()
    private val lock = Mutex()

    /** Number of messages (user and assistant) in the conversation. */
    val size: Int get() = synchronized(history) { history.size }

    /** Forget the conversation and start over. */
    fun reset() {
        synchronized(history) { history.clear() }
    }

    /**
     * Send [text] as the next user turn and stream Claude's reply.
     *
     * The flow ends with [ChatEvent.Completed] or [ChatEvent.Refused]. API and
     * network failures are thrown as the SDK's typed exceptions
     * (`com.anthropic.errors.*`); in that case the user turn is not kept.
     * Cancelling the collector closes the HTTP stream.
     */
    fun send(text: String): Flow<ChatEvent> = flow {
        lock.withLock {
            val userTurn = BetaMessageParam.ofUser(text)
            val params = buildParams(snapshot() + userTurn)

            val accumulator = BetaMessageAccumulator.create()
            client.beta().messages().createStreaming(params).use { response ->
                val events = response.stream().iterator()
                while (events.hasNext()) {
                    currentCoroutineContext().ensureActive()
                    val event = accumulator.accumulate(events.next())
                    // emit() suspends, so it can't run inside Optional.ifPresent.
                    val delta = event.contentBlockDelta().flatMap { it.delta().text() }
                    if (delta.isPresent) emit(ChatEvent.TextDelta(delta.get().text()))
                }
            }

            val message = accumulator.message()
            val stopReason = message.stopReason().orElse(null)
            if (stopReason == BetaStopReason.REFUSAL) {
                emit(ChatEvent.Refused(message.stopDetails().flatMap { it.explanation() }.orElse(null)))
                return@withLock
            }

            currentCoroutineContext().ensureActive()
            val assistantTurn = replayableContent(message.content())
            if (assistantTurn.isNotEmpty()) {
                synchronized(history) {
                    history += userTurn
                    history += BetaMessageParam.ofAssistantOfContentBlockParams(assistantTurn.map { it.toParam() })
                }
            }
            val reply = message.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString("")
            emit(ChatEvent.Completed(reply, truncated = stopReason == BetaStopReason.MAX_TOKENS))
        }
    }.flowOn(Dispatchers.IO)

    internal fun buildParams(messages: List<BetaMessageParam>): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(config.model)
            .maxTokens(config.maxTokens)
            .system(config.systemPrompt)
            .thinking(BetaThinkingConfigAdaptive.builder().build())
            .outputConfig(BetaOutputConfig.builder().effort(config.effort).build())
            .messages(messages)
        if (config.serverSideFallback) {
            builder.addBeta(SERVER_SIDE_FALLBACK_BETA).fallbacksDefault()
        }
        return builder.build()
    }

    /**
     * The assistant content to send back on later turns.
     *
     * Without a model switch this is the message unchanged, thinking blocks
     * included. After a mid-reply server-side fallback, only text (and the
     * `fallback` marker) is kept before the last switch point: the declined
     * model's thinking blocks must not be replayed.
     */
    internal fun replayableContent(content: List<BetaContentBlock>): List<BetaContentBlock> {
        val boundary = content.indexOfLast { it.isFallback() }
        if (boundary < 0) return content
        return content.filterIndexed { index, block -> index >= boundary || block.isText() }
    }

    private fun snapshot(): List<BetaMessageParam> = synchronized(history) { history.toList() }

    companion object {
        const val SERVER_SIDE_FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** Build a client for [apiKey]. Pass [baseUrl] only to point at a test server. */
        fun createClient(apiKey: String, baseUrl: String? = null): AnthropicClient =
            AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .apply { if (baseUrl != null) baseUrl(baseUrl) }
                // A watch can hop between Bluetooth, Wi-Fi and LTE; give slow links room.
                .timeout(Duration.ofMinutes(2))
                .maxRetries(2)
                .build()
    }
}
