package com.radaroccaz.app.sources

import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerFilter
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleOptions
import com.radaroccaz.app.analysis.StructuredHistory
import kotlinx.serialization.json.JsonObject

/**
 * autoscout24.fr. Its list page (Next.js) embeds `props.pageProps.listings[]`, each with
 * `vehicle`, `price`, `location`, `seller` and `tracking` (raw price, mileage, registration).
 */
class AutoScout24Source(fetcher: HttpFetcher) : HtmlListingSource(fetcher) {
    override val id = ID
    override val name = "AutoScout24"
    override val description = "Surtout des professionnels, France et Europe"

    override fun searchUrl(criteria: SearchCriteria): String {
        val path = buildString {
            append("https://www.autoscout24.fr/lst")
            if (criteria.make.isNotBlank()) append("/").append(slug(criteria.make))
            if (criteria.model.isNotBlank()) append("/").append(slug(criteria.model))
        }
        val params = buildList {
            add("atype" to "C")
            add("cy" to "F")
            add("damaged_listing" to "exclude")
            add("ustate" to "N,U")
            add("sort" to "price")
            add("desc" to "0")
            criteria.budgetMin?.let { add("pricefrom" to "$it") }
            criteria.budgetMax?.let { add("priceto" to "$it") }
            criteria.mileageMax?.let { add("kmto" to "$it") }
            criteria.yearMin?.let { add("fregfrom" to "$it") }
            if (criteria.fuels.isNotEmpty()) add("fuel" to criteria.fuels.flatMap(::fuelCodes).distinct().joinToString(","))
            criteria.gearbox?.let { add("gear" to if (it == Gearbox.MANUELLE) "M" else "A,S") }
            when (criteria.seller) {
                SellerFilter.PARTICULIER -> add("custtype" to "P")
                SellerFilter.PRO -> add("custtype" to "D")
                SellerFilter.ALL -> Unit
            }
        }
        return path + "?" + params.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }

    override fun parse(html: String): List<Listing> {
        val root = JsonTools.nextData(JsonTools.document(html)) ?: return emptyList()
        val listings = JsonTools.findArray(root, "listings") { "vehicle" in it || "tracking" in it } ?: return emptyList()
        return listings.mapNotNull { it.obj()?.let(::toListing) }
    }

    private fun toListing(o: JsonObject): Listing? {
        val nativeId = o.str("id") ?: return null
        val price = o.int("tracking", "price") ?: o.int("price", "priceFormatted") ?: return null
        val make = o.str("vehicle", "make")
        val model = o.str("vehicle", "model")
        val version = o.str("vehicle", "modelVersionInput") ?: o.str("vehicle", "variant")
        val title = listOfNotNull(make, model, version).joinToString(" ").ifBlank { return null }
        val details = o["vehicleDetails"].arr()?.mapNotNull { it.obj() }.orEmpty()
        fun detail(icon: String) = details.firstOrNull { it.str("iconName") == icon }?.str("data")
        val url = o.str("url")?.let { if (it.startsWith("http")) it else "https://www.autoscout24.fr$it" }
            ?: "https://www.autoscout24.fr/offres/$nativeId"
        val images = o["images"].strings().map { it.replace(Regex("/\\d+x\\d+\\.(webp|jpg)$"), "/720x540.$1") }
        val power = detail("speedometer")?.let { Regex("(\\d+)\\s*(CH|ch|PS|hp)").find(it)?.groupValues?.get(1)?.toInt() }
        return Listing(
            id = "$ID:$nativeId",
            source = ID,
            title = title,
            price = price,
            url = url,
            make = make,
            model = model,
            mileageKm = o.int("tracking", "mileage") ?: Text.digits(o.str("vehicle", "mileageInKm") ?: detail("mileage_road")),
            year = Text.year(o.str("tracking", "firstRegistration") ?: detail("calendar")),
            fuel = Fuel.parse(o.str("vehicle", "fuel") ?: detail("gas_pump") ?: o.str("tracking", "fuelType")),
            gearbox = Gearbox.parse(o.str("vehicle", "transmission") ?: detail("transmission")),
            sellerType = SellerType.parse(o.str("seller", "type")),
            city = o.str("location", "city"),
            postalCode = o.str("location", "zip"),
            imageUrls = images,
            description = o.str("vehicle", "subtitle").orEmpty(),
            powerHp = power,
            options = VehicleOptions.detect(title, o.str("vehicle", "subtitle")),
        ).withHistory(StructuredHistory.fromJson(o))
    }

    private fun fuelCodes(f: Fuel): List<String> = when (f) {
        Fuel.ESSENCE -> listOf("B")
        Fuel.DIESEL -> listOf("D")
        Fuel.ELECTRIQUE -> listOf("E")
        Fuel.HYBRIDE, Fuel.HYBRIDE_RECHARGEABLE -> listOf("2", "3")
        Fuel.GPL -> listOf("L")
    }

    companion object {
        const val ID = "autoscout24"

        /** "Mercedes-Benz" → "mercedes-benz", "Série 3" → "serie-3". */
        fun slug(s: String): String = Text.normalize(s).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
