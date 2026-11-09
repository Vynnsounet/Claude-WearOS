package com.claude.wear.core

/** Events emitted while Claude answers one message. */
sealed interface ChatEvent {
    /** A new piece of reply text, in order. */
    data class TextDelta(val text: String) : ChatEvent

    /**
     * The reply finished and was added to the conversation.
     *
     * @param truncated true when the reply hit the token limit before it ended.
     */
    data class Completed(val text: String, val truncated: Boolean) : ChatEvent

    /**
     * Claude declined the request. Any partial text already streamed should be
     * discarded; the question is not kept in the conversation.
     */
    data class Refused(val explanation: String?) : ChatEvent
}
