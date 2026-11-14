package com.radaroccaz.app.sources

import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleOptions
import org.jsoup.nodes.Element

/**
 * occasion.largus.fr, L'Argus' listings (private sellers and dealers). Pages are
 * `/auto/<make>/<model>/`, newest first; each result is an `<article>` card with title,
 * price, year, mileage, energy, gearbox, place and date.
 */
class LargusSource(fetcher: HttpFetcher) : HtmlListingSource(fetcher) {
    override val id = ID
    override val name = "L'Argus"
    override val description = "Particuliers et professionnels, annonces L'Argus"

    override fun searchUrls(criteria: SearchCriteria): List<String> =
        SiteNames.candidates(criteria) { mk, model -> if (model == null) "$BASE/auto/$mk/" else "$BASE/auto/$mk/$model/" }

    override fun parse(html: String): List<Listing> {
        val doc = JsonTools.document(html)
        // The page renders the same cards twice (grid and list views): keep the first of each.
        val cards = (doc.select("#results-card article").ifEmpty { doc.select("article") })
            .filter { it.selectFirst("a[href*=/auto/annonce-]") != null }
        return cards.mapNotNull(::toListing).distinctBy { it.id }
    }

    private fun toListing(card: Element): Listing? {
        val href = card.selectFirst("a[href*=/auto/annonce-]")?.attr("href") ?: return null
        val url = if (href.startsWith("http")) href else BASE + href
        val nativeId = Regex("annonce-([0-9a-f-]{36})").find(href)?.groupValues?.get(1) ?: href.substringAfterLast('/')
        val title = card.selectFirst("h3")?.text()?.trim().orEmpty().ifEmpty { card.selectFirst("img[alt]")?.attr("alt").orEmpty() }
        if (title.isBlank()) return null
        val price = card.select("p").map { it.text() }.firstOrNull { '€' in it }?.let(Text::digits) ?: return null
        val facts = card.select("div.flex-wrap span, div.flex span").map { it.text().trim() }.filter { it.isNotEmpty() }
        val year = facts.firstNotNullOfOrNull { f -> f.takeIf { Regex("^(19|20)\\d\\d$").matches(it) }?.toInt() }
        val km = facts.firstOrNull { it.endsWith("km") }?.let(Text::digits)
        val fuel = facts.firstNotNullOfOrNull { Fuel.parse(it.trimEnd('.')) }
        val gearbox = facts.firstNotNullOfOrNull { Gearbox.parse(it.trimEnd('.')) }
        val place = card.select("p span.truncate").firstOrNull()?.text()?.trim()
        val date = card.select("p").map { it.text().trim() }.firstOrNull { Regex("^\\d{2}/\\d{2}/\\d{4}$").matches(it) }
        return Listing(
            id = "$ID:$nativeId",
            source = ID,
            title = title,
            price = price,
            url = url,
            mileageKm = km,
            year = year,
            fuel = fuel,
            gearbox = gearbox,
            city = place?.substringBefore(" (")?.trim(),
            imageUrls = listOfNotNull(card.selectFirst("img")?.attr("src")?.replace("/small-", "/")?.takeIf { it.startsWith("http") }),
            publishedAt = date?.let { "${it.substring(6)}-${it.substring(3, 5)}-${it.substring(0, 2)}" },
            options = VehicleOptions.detect(title),
        ).withHistory()
    }

    companion object {
        const val ID = "largus"
        private const val BASE = "https://occasion.largus.fr"
    }
}
