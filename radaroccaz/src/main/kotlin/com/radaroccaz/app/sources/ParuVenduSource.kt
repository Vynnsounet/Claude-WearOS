package com.radaroccaz.app.sources

import com.radaroccaz.app.data.BodyType
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleOptions
import org.jsoup.nodes.Element

/**
 * paruvendu.fr. Make/model pages `/a/voiture-occasion/<make>/<model>/` are allowed by
 * robots.txt (its search form, `/auto-moto/listefo/`, is not, so filters are applied here).
 * Each `<article data-id>` card has the title, price, a "2019 - 85 000 km - Manuelle -
 * Diesel" line, the place with its postal code, the body and a "PRO" badge for dealers.
 */
class ParuVenduSource(fetcher: HttpFetcher) : HtmlListingSource(fetcher) {
    override val id = ID
    override val name = "ParuVendu"
    override val description = "Particuliers et professionnels"

    override fun searchUrls(criteria: SearchCriteria): List<String> =
        SiteNames.candidates(criteria, makePrefer = MAKES) { mk, model ->
            if (model == null) "$BASE/a/voiture-occasion/$mk/" else "$BASE/a/voiture-occasion/$mk/$model/"
        }

    override fun parse(html: String): List<Listing> =
        JsonTools.document(html).select("article[data-id]").mapNotNull(::toListing).distinctBy { it.id }

    private fun toListing(card: Element): Listing? {
        val nativeId = card.attr("data-id").ifBlank { return null }
        val link = card.select("a[href*=/a/voiture-occasion/]").firstOrNull { nativeId in it.attr("href") }
            ?: card.selectFirst("a[href*=/a/voiture-occasion/]") ?: return null
        val url = link.absUrl("href").ifBlank { link.attr("href").let { if (it.startsWith("http")) it else BASE + it } }
        val title = card.selectFirst("h3")?.text()?.trim().orEmpty().ifEmpty { link.attr("title") }
        if (title.isBlank()) return null
        val price = card.select(".encoded-lnk, div, span").map { it.ownText() }.firstOrNull { '€' in it && it.any(Char::isDigit) }?.let(Text::digits)
            ?: Regex("(\\d[\\d\\s  ]{2,9})\\s?€").find(card.text())?.groupValues?.get(1)?.let(Text::digits)
            ?: return null
        val summary = card.select("p").map { it.text() }.firstOrNull { " km" in it && " - " in it }.orEmpty()
        val parts = summary.split(" - ").map { it.trim() }
        val chips = card.select("span").map { it.text().trim() }.filter { it.isNotEmpty() && it.length < 40 }
        val place = card.select("p span.truncate, span").map { it.text().trim() }.firstOrNull { Regex(".+\\(\\d{5}\\)$").matches(it) }
        val pro = chips.any { it == "PRO" }
        return Listing(
            id = "$ID:$nativeId",
            source = ID,
            title = title,
            price = price,
            url = url,
            mileageKm = parts.firstOrNull { it.endsWith("km") }?.let(Text::digits)
                ?: chips.firstOrNull { it.endsWith(" km") }?.let(Text::digits),
            year = parts.firstNotNullOfOrNull { p -> p.takeIf { Regex("^(19|20)\\d\\d$").matches(it) }?.toInt() }
                ?: chips.firstNotNullOfOrNull { c -> Regex("^Année ((19|20)\\d\\d)$").find(c)?.groupValues?.get(1)?.toInt() },
            fuel = (parts + chips).firstNotNullOfOrNull { Fuel.parse(it) },
            gearbox = (parts + chips).firstNotNullOfOrNull { Gearbox.parse(it) },
            sellerType = if (pro) SellerType.PRO else SellerType.PARTICULIER,
            city = place?.substringBeforeLast(" (")?.trim(),
            postalCode = place?.let { Regex("\\((\\d{5})\\)").find(it)?.groupValues?.get(1) },
            imageUrls = listOfNotNull(card.selectFirst("img")?.attr("src")?.takeIf { it.startsWith("http") && "novisu" !in it }),
            body = chips.firstNotNullOfOrNull { BodyType.parse(it) },
            options = VehicleOptions.detect(title),
        ).withHistory()
    }

    companion object {
        const val ID = "paruvendu"
        private const val BASE = "https://www.paruvendu.fr"
        private val MAKES = mapOf("mercedes-benz" to "mercedes", "ds-automobiles" to "ds")
    }
}
