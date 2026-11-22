package com.medipote.app.ai

import com.medipote.app.domain.Urgency
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class AiPoint(val title: String, val detail: String)

/** What the model answered, after the guard: shown as cards. [level] null means « nothing worrying in these notes ». */
data class AiAnswer(
    val summary: String,
    val explanations: List<AiPoint> = emptyList(),
    val relief: List<String> = emptyList(),
    val level: Urgency? = null,
    val reason: String = "",
    val questions: List<String> = emptyList(),
    /** The text came back in an unexpected shape and is shown as it is. */
    val raw: Boolean = false,
    /** Some lines were held back by the guard (medication, diagnosis). */
    val filtered: Boolean = false,
) {
    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /** The answer from the model's text: tolerant of a code fence or a word around it; plain text if it is not JSON. */
        fun parse(text: String): AiAnswer {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return AiAnswer(text.trim(), raw = true)
            val obj = runCatching { JSON.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull()
                ?: return AiAnswer(text.trim(), raw = true)
            fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            fun strings(k: String) = (obj[k] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            val points = (obj["explanations"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val detail = str(o, "detail")
                AiPoint(str(o, "title"), detail).takeIf { detail.isNotEmpty() || str(o, "title").isNotEmpty() }
            }
            val consult = obj["consult"] as? JsonObject
            val level = when (consult?.let { str(it, "level") }?.lowercase()) {
                "urgent" -> Urgency.URGENT
                "soon" -> Urgency.SOON
                "mention" -> Urgency.MENTION
                else -> null
            }
            return AiAnswer(
                summary = str(obj, "summary"), explanations = points.take(4), relief = strings("relief").take(5),
                level = level, reason = consult?.let { str(it, "reason") }.orEmpty(), questions = strings("questions").take(3),
            )
        }
    }
}
