package com.claude.wear

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anthropic.client.AnthropicClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.OverloadedException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.ServiceUnavailableException
import com.anthropic.errors.UnauthorizedException
import com.claude.wear.core.ChatConfig
import com.claude.wear.core.ChatEvent
import com.claude.wear.core.ClaudeChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    /** True while Claude's reply is still streaming in. */
    val pending: Boolean = false,
    /** A short status line shown under the text, e.g. "reply cut short". */
    val note: String? = null,
    val isError: Boolean = false,
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isResponding: Boolean = false,
    /** null until the stored key has been read. */
    val hasApiKey: Boolean? = null,
    val maskedApiKey: String? = null,
    val model: String = ChatConfig.DEFAULT_MODEL,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStore = ApiKeyStore(application)
    private val config = ChatConfig()

    private val _state = MutableStateFlow(ChatUiState(model = config.model))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var client: AnthropicClient? = null
    private var chat: ClaudeChat? = null
    private var responseJob: Job? = null
    private var nextId = 0L

    init {
        viewModelScope.launch {
            keyStore.apiKey.collect { key -> onApiKeyChanged(key) }
        }
    }

    private fun onApiKeyChanged(key: String) {
        responseJob?.cancel()
        client?.close()
        client = null
        chat = null
        if (key.isNotEmpty()) {
            client = ClaudeChat.createClient(key).also { chat = ClaudeChat(it, config) }
        }
        _state.update {
            it.copy(
                messages = emptyList(),
                isResponding = false,
                hasApiKey = key.isNotEmpty(),
                maskedApiKey = key.takeIf { k -> k.isNotEmpty() }?.let(ApiKeyStore::mask),
            )
        }
    }

    fun send(rawText: String) {
        val text = rawText.trim()
        val chat = chat ?: return
        if (text.isEmpty() || _state.value.isResponding) return

        val userId = nextId++
        val replyId = nextId++
        _state.update {
            it.copy(
                isResponding = true,
                messages = it.messages +
                    UiMessage(userId, fromUser = true, text = text) +
                    UiMessage(replyId, fromUser = false, text = "", pending = true),
            )
        }

        responseJob = viewModelScope.launch {
            try {
                chat.send(text).collect { event ->
                    when (event) {
                        is ChatEvent.TextDelta -> updateReply(replyId) { it.copy(text = it.text + event.text) }
                        is ChatEvent.Completed -> updateReply(replyId) {
                            it.copy(
                                text = event.text,
                                pending = false,
                                note = if (event.truncated) string(R.string.truncated) else null,
                            )
                        }
                        is ChatEvent.Refused -> updateReply(replyId) {
                            it.copy(text = string(R.string.refused), pending = false, note = event.explanation)
                        }
                    }
                }
            } catch (e: CancellationException) {
                updateReply(replyId) { it.copy(pending = false, note = string(R.string.stop)) }
                throw e
            } catch (e: Exception) {
                updateReply(replyId) { it.copy(text = errorMessage(e), pending = false, isError = true) }
            } finally {
                _state.update { it.copy(isResponding = false) }
            }
        }
    }

    fun stop() {
        responseJob?.cancel()
    }

    fun newChat() {
        responseJob?.cancel()
        chat?.reset()
        _state.update { it.copy(messages = emptyList(), isResponding = false) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch { keyStore.save(key) }
    }

    fun clearApiKey() {
        viewModelScope.launch { keyStore.clear() }
    }

    override fun onCleared() {
        client?.close()
    }

    private fun updateReply(id: Long, change: (UiMessage) -> UiMessage) {
        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == id) change(it) else it }) }
    }

    private fun errorMessage(e: Exception): String = when (e) {
        is UnauthorizedException, is PermissionDeniedException -> string(R.string.error_auth)
        is RateLimitException -> string(R.string.error_rate_limit)
        is OverloadedException, is ServiceUnavailableException, is InternalServerException ->
            string(R.string.error_overloaded)
        is AnthropicIoException -> string(R.string.error_network)
        is AnthropicServiceException -> string(R.string.error_generic, e.message ?: e.statusCode().toString())
        else -> string(R.string.error_generic, e.message ?: e.javaClass.simpleName)
    }

    private fun string(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)
}
