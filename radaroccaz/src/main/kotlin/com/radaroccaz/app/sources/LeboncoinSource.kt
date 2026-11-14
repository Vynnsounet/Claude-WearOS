package com.radaroccaz.app.sources

import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerFilter
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleOptions
import java.net.URLEncoder
import kotlinx.serialization.json.JsonObject

/**
 * leboncoin.fr, "Voitures" category. The search page is a Next.js app whose embedded
 * __NEXT_DATA__ JSON holds the ads (`ads[]` with `list_id`, `subject`, `price`, `attributes`...).
 */
class LeboncoinSource(fetcher: HttpFetcher) : HtmlListingSource(fetcher) {
    override val id = ID
    override val name = "Leboncoin"
    override val description = "Particuliers et professionnels"

    override fun searchUrl(criteria: SearchCriteria): String {
        val params = buildList {
            add("category" to "2")
            add("text" to criteria.title)
            range(criteria.budgetMin, criteria.budgetMax)?.let { add("price" to it) }
            range(null, criteria.mileageMax)?.let { add("mileage" to it) }
            range(criteria.yearMin, null)?.let { add("regdate" to it) }
            if (criteria.fuels.isNotEmpty()) add("fuel" to criteria.fuels.flatMap(::fuelCodes).distinct().joinToString(","))
            criteria.gearbox?.let { add("gearbox" to if (it == Gearbox.MANUELLE) "1" else "2") }
            when (criteria.seller) {
                SellerFilter.PARTICULIER -> add("owner_type" to "private")
                SellerFilter.PRO -> add("owner_type" to "pro")
                SellerFilter.ALL -> Unit
            }
            add("sort" to "price")
            add("order" to "asc")
        }
        return "https://www.leboncoin.fr/recherche?" + params.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }

    override fun parse(html: String): List<Listing> {
        val doc = JsonTools.document(html)
        val root = JsonTools.nextData(doc) ?: return emptyList()
        val ads = JsonTools.findArray(root, "ads") { "list_id" in it || "subject" in it } ?: return emptyList()
        return ads.mapNotNull { it.obj()?.let(::toListing) }
    }

    private fun toListing(ad: JsonObject): Listing? {
        val nativeId = ad.str("list_id") ?: return null
        val price = ad.int("price") ?: ad.int("price_cents")?.div(100) ?: return null
        val attrs = ad["attributes"].arr()?.mapNotNull { a ->
            val o = a.obj() ?: return@mapNotNull null
            val key = o.str("key") ?: return@mapNotNull null
            key to (o.str("value_label") ?: o.str("value") ?: "")
        }?.toMap().orEmpty()
        val rawValues = ad["attributes"].arr()?.mapNotNull { a ->
            a.obj()?.let { o -> o.str("key")?.let { it to (o.str("value") ?: "") } }
        }?.toMap().orEmpty()
        val title = ad.str("subject") ?: return null
        val body = ad.str("body").orEmpty()
        val images = ad["images"].obj()?.let { img ->
            img["urls_large"].strings().ifEmpty { img["urls"].strings() }.ifEmpty { listOfNotNull(img.str("small_url"), img.str("thumb_url")) }
        }.orEmpty()
        return Listing(
            id = "$ID:$nativeId",
            source = ID,
            title = title,
            price = price,
            url = ad.str("url") ?: "https://www.leboncoin.fr/ad/voitures/$nativeId",
            make = attrs["brand"],
            model = attrs["model"],
            mileageKm = Text.digits(rawValues["mileage"] ?: attrs["mileage"]),
            year = Text.year(rawValues["regdate"]) ?: Text.year(attrs["issuance_date"]),
            fuel = Fuel.parse(attrs["fuel"]),
            gearbox = Gearbox.parse(attrs["gearbox"]),
            sellerType = SellerType.parse(ad.str("owner", "type")),
            city = ad.str("location", "city"),
            postalCode = ad.str("location", "zipcode"),
            imageUrls = images,
            description = body,
            publishedAt = ad.str("first_publication_date")?.replace(' ', 'T'),
            powerHp = Text.digits(rawValues["horse_power_din"]),
            options = VehicleOptions.detect(title, body),
        )
    }

    private fun fuelCodes(f: Fuel): List<String> = when (f) {
        Fuel.ESSENCE -> listOf("1")
        Fuel.DIESEL -> listOf("2")
        Fuel.GPL -> listOf("3")
        Fuel.ELECTRIQUE -> listOf("4")
        Fuel.HYBRIDE -> listOf("6")
        Fuel.HYBRIDE_RECHARGEABLE -> listOf("6", "7")
    }

    companion object {
        const val ID = "leboncoin"
    }
}

internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** "min-15000", "2016-max", or null when both ends are open. */
internal fun range(min: Int?, max: Int?): String? =
    if (min == null && max == null) null else "${min ?: "min"}-${max ?: "max"}"
