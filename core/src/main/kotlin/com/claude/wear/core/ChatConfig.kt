package com.claude.wear.core

import com.anthropic.models.beta.messages.BetaOutputConfig

/**
 * Request settings for the watch chat.
 *
 * Defaults are tuned for a wrist: short answers, low latency.
 */
data class ChatConfig(
    val model: String = DEFAULT_MODEL,
    /**
     * Upper bound on one reply, thinking included. Replies on a watch are
     * deliberately short, so this is well below the model maximum.
     */
    val maxTokens: Long = 8_192L,
    /**
     * Lower effort means faster, more direct answers, which suits quick
     * questions asked from a watch. Raise it for harder questions.
     */
    val effort: BetaOutputConfig.Effort = BetaOutputConfig.Effort.LOW,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    /**
     * Server-side fallback: if a safety classifier declines the request, the
     * API retries it on the model Anthropic recommends for that category.
     */
    val serverSideFallback: Boolean = true,
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"

        const val DEFAULT_SYSTEM_PROMPT =
            "You are Claude, talking with someone through an app on their smartwatch. " +
                "The screen is small and round, and they often ask by voice while on the move. " +
                "Answer in one to three short sentences unless they ask for more detail. " +
                "Write plain text: no markdown, headings, tables or code blocks, since the " +
                "watch shows text without formatting. If a question needs a long answer, give " +
                "the essential part first and offer to go deeper."
    }
}
