package com.radaroccaz.app.sources

import com.radaroccaz.app.analysis.HistoryText
import com.radaroccaz.app.analysis.StructuredHistory
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.VehicleHistory
import com.radaroccaz.app.data.VehicleOptions
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup

/** Fills in a listing from its full page: whole description, all attributes, history. */
object DetailExtractor {

    private val descriptionKeys = setOf("description", "body", "comment", "sellercomment", "sellerdescription", "text")
    private val stateAssignment = Regex("""(?s)window\.__[A-Z0-9_]+__\s*=\s*(\{.*?\})\s*;?\s*$""")

    fun blobs(html: String): List<JsonElement> {
        val doc = JsonTools.document(html)
        return buildList {
            JsonTools.nextData(doc)?.let(::add)
            doc.select("script[type=application/json]").filter { it.id() != "__NEXT_DATA__" }
                .forEach { s -> JsonTools.parseOrNull(s.data())?.let(::add) }
            doc.select("script:not([type]), script[type=text/javascript]").forEach { s ->
                stateAssignment.find(s.data())?.groupValues?.get(1)?.let(JsonTools::parseOrNull)?.let(::add)
            }
            addAll(JsonTools.jsonLd(doc))
        }
    }

    /** The object holding the longest description: the listing itself, not the "similar ads" around it. */
    fun mainObject(blobs: List<JsonElement>): Pair<JsonObject, String>? {
        var best: Pair<JsonObject, String>? = null
        for (blob in blobs) for (o in JsonTools.objects(blob)) {
            for ((k, v) in o) {
                if (k.lowercase().filter(Char::isLetterOrDigit) !in descriptionKeys) continue
                val text = (v as? JsonPrimitive)?.contentOrNull ?: continue
                if (text.length > (best?.second?.length ?: 40)) best = o to text
            }
        }
        return best
    }

    fun enrich(listing: Listing, html: String): Listing {
        val all = blobs(html)
        val main = mainObject(all)
        val description = main?.second?.let { if ('<' in it) Jsoup.parse(it).wholeText() else it }?.trim()
            ?.takeIf { it.length > listing.description.length }
            ?: listing.description.ifBlank {
                JsonTools.document(html).selectFirst("meta[name=description], meta[property=og:description]")?.attr("content").orEmpty()
            }
        val structured = main?.first?.let { StructuredHistory.fromJson(it) } ?: VehicleHistory()
        val text = HistoryText.extract(listing.title, description)
        val link = HistoryText.extract(html.take(400_000)).reportUrl
        val history = listing.history.merge(text).merge(structured).let { if (link != null && it.reportUrl == null) it.copy(reportUrl = link) else it }
        return listing.copy(
            description = description,
            history = history,
            options = listing.options + VehicleOptions.detect(listing.title, description),
            detailsLoaded = true,
        )
    }
}

/** Text history merged with what the site publishes as data. */
internal fun Listing.withHistory(structured: VehicleHistory = VehicleHistory()): Listing =
    copy(history = history.merge(HistoryText.extract(title, description)).merge(structured))
