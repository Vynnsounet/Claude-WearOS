package com.medipote.app.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One turn of the conversation sent to the model. */
data class AiTurn(val fromPerson: Boolean, val text: String)

enum class AiFailureKind(val message: String) {
    NO_KEY("Pas de clé : ajoute-la dans les réglages de l'IA."),
    AUTH("La clé est refusée : vérifie qu'elle est complète et toujours valide."),
    BILLING("Le compte de la clé n'a plus de crédit : ajoute-en dans la console d'Anthropic."),
    RATE_LIMIT("Trop de demandes à la suite : attends une minute et réessaie."),
    OVERLOADED("Le service est très sollicité : réessaie dans un moment."),
    NETWORK("Pas de connexion : l'IA a besoin d'Internet. Ce que l'appli sait déjà reste affiché."),
    TIMEOUT("L'IA met trop de temps à répondre : réessaie."),
    BAD_REQUEST("L'IA a refusé la demande. Réessaie, ou dis-le-moi dans « Une idée, un souci ? »."),
    OTHER("Un problème est survenu avec l'IA : réessaie plus tard."),
}

sealed interface AiResult {
    data class Ok(val text: String) : AiResult
    /** The model declined to answer (a safety classifier): shown as such, never retried by itself. */
    data object Refused : AiResult
    data class Failure(val kind: AiFailureKind, val detail: String = "") : AiResult
}

/** The way to reach the model; faked in tests. */
interface AiClient {
    suspend fun complete(apiKey: String, system: String, turns: List<AiTurn>, maxTokens: Long = AiPrompt.MAX_TOKENS): AiResult
}

/**
 * Claude, through the official Anthropic SDK, with the person's own key. One request, no tool, no storage on the
 * model's side that the app asks for; the key is only ever held in memory for the call.
 */
class AnthropicAiClient(
    /** Only for tests: a local server standing in for the API. */
    private val baseUrl: String? = null,
    private val timeout: Duration = Duration.ofSeconds(120),
    private val maxRetries: Int = 2,
) : AiClient {
    override suspend fun complete(apiKey: String, system: String, turns: List<AiTurn>, maxTokens: Long): AiResult = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey.trim())
            .apply { baseUrl?.let(::baseUrl) }
            .timeout(timeout)
            .maxRetries(maxRetries)
            .build()
        try {
            val params = MessageCreateParams.builder()
                .model(AiPrompt.MODEL)
                .maxTokens(maxTokens)
                .system(system)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .apply { turns.forEach { if (it.fromPerson) addUserMessage(it.text) else addAssistantMessage(it.text) } }
                .build()
            val message = client.messages().create(params)
            if (message.stopReason().map { it == StopReason.REFUSAL }.orElse(false)) return@withContext AiResult.Refused
            val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("").trim()
            if (text.isEmpty()) AiResult.Failure(AiFailureKind.OTHER, "réponse vide") else AiResult.Ok(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AnthropicServiceException) {
            AiResult.Failure(kindOf(e.statusCode(), e.message.orEmpty()), "HTTP ${e.statusCode()}")
        } catch (e: AnthropicIoException) {
            AiResult.Failure(if (e.cause is java.net.SocketTimeoutException) AiFailureKind.TIMEOUT else AiFailureKind.NETWORK)
        } catch (e: Exception) {
            AiResult.Failure(if (e.cause is java.io.IOException) AiFailureKind.NETWORK else AiFailureKind.OTHER, e.javaClass.simpleName)
        } finally {
            runCatching { client.close() }
        }
    }

    companion object {
        /** The failure a status code means. */
        fun kindOf(status: Int, message: String): AiFailureKind = when {
            status == 401 || status == 403 -> AiFailureKind.AUTH
            status == 402 || (status == 400 && message.contains("credit balance", ignoreCase = true)) -> AiFailureKind.BILLING
            status == 429 -> AiFailureKind.RATE_LIMIT
            status == 529 || status in 500..599 -> AiFailureKind.OVERLOADED
            status == 408 || status == 504 -> AiFailureKind.TIMEOUT
            status in 400..499 -> AiFailureKind.BAD_REQUEST
            else -> AiFailureKind.OTHER
        }
    }
}
