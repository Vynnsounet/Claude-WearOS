package com.radaroccaz.app.sources

import com.radaroccaz.app.data.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal object JsonTools {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun document(html: String): Document = Jsoup.parse(html)

    /** The JSON a Next.js page embeds for hydration, if any. */
    fun nextData(doc: Document): JsonElement? =
        doc.selectFirst("script#__NEXT_DATA__")?.data()?.let(::parseOrNull)

    /** Every JSON-LD block of the page. */
    fun jsonLd(doc: Document): List<JsonElement> =
        doc.select("script[type=application/ld+json]").mapNotNull { parseOrNull(it.data()) }

    fun parseOrNull(text: String): JsonElement? =
        try { json.parseToJsonElement(text.trim()) } catch (_: Exception) { null }

    /** Depth-first search for the first array stored under [key] whose items satisfy [accept]. */
    fun findArray(root: JsonElement, key: String, accept: (JsonObject) -> Boolean): JsonArray? {
        val stack = ArrayDeque<JsonElement>().apply { add(root) }
        while (stack.isNotEmpty()) {
            when (val e = stack.removeLast()) {
                is JsonObject -> {
                    val candidate = e[key]
                    if (candidate is JsonArray && candidate.isNotEmpty() &&
                        candidate.all { it is JsonObject } && accept(candidate.first() as JsonObject)
                    ) return candidate
                    e.values.reversed().forEach(stack::addLast)
                }
                is JsonArray -> e.reversed().forEach(stack::addLast)
                else -> Unit
            }
        }
        return null
    }

    /** Every object in the tree, depth-first. */
    fun objects(root: JsonElement): Sequence<JsonObject> = sequence {
        val stack = ArrayDeque<JsonElement>().apply { add(root) }
        while (stack.isNotEmpty()) {
            when (val e = stack.removeLast()) {
                is JsonObject -> { yield(e); e.values.reversed().forEach(stack::addLast) }
                is JsonArray -> e.reversed().forEach(stack::addLast)
                else -> Unit
            }
        }
    }
}

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray

/** Text of a primitive (numbers included); null for objects, arrays and JSON null. */
internal fun JsonElement?.text(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.str(vararg path: String): String? {
    var e: JsonElement? = this
    for (p in path) e = (e as? JsonObject)?.get(p)
    return e.text()
}

/** Integer from a number or a formatted string ("14 990 €"), following a path of keys. */
internal fun JsonObject.int(vararg path: String): Int? {
    var e: JsonElement? = this
    for (p in path) e = (e as? JsonObject)?.get(p)
    return when (e) {
        is JsonArray -> e.firstOrNull().text()?.let(::toIntLoose)
        else -> e.text()?.let(::toIntLoose)
    }
}

private fun toIntLoose(s: String): Int? =
    s.toDoubleOrNull()?.toInt() ?: Text.digits(s)

/** Strings in an array, or a single string. */
internal fun JsonElement?.strings(): List<String> = when (this) {
    is JsonArray -> mapNotNull { it.text() ?: it.obj()?.let { o -> o.str("url") ?: o.str("contentUrl") ?: o.str("src") } }
    is JsonPrimitive -> listOfNotNull(text())
    is JsonObject -> listOfNotNull(str("url") ?: str("contentUrl"))
    else -> emptyList()
}
