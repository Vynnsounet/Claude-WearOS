package com.radaroccaz.app.sources

import com.radaroccaz.app.analysis.StructuredHistory
import com.radaroccaz.app.data.BodyType
import com.radaroccaz.app.data.Drive
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerFilter
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleOptions
import kotlinx.serialization.json.JsonObject

/**
 * autoscout24.fr. Search pages are `/lst/<make>/<model>` (robots.txt only forbids `/lst?`
 * without a path); an unknown model name answers 404, so the model group ("serie-3-tous")
 * and then the make alone are tried. The Next.js data holds `props.pageProps.listings[]`
 * with `vehicle`, `price.priceRaw`, `location`, `seller.type` (Dealer / PrivateSeller),
 * `tracking` (raw price, mileage, first registration) and `vehicleDetails`.
 */
class AutoScout24Source(fetcher: HttpFetcher) : HtmlListingSource(fetcher) {
    override val id = ID
    override val name = "AutoScout24"
    override val description = "Professionnels et particuliers, France"

    override fun searchUrls(criteria: SearchCriteria): List<String> {
        val query = query(criteria)
        val makes = SiteNames.makeSlugs(criteria.make)
        val models = SiteNames.modelSlugs(criteria.model).flatMap { listOf(it, "$it-tous") }.distinct()
        val urls = makes.flatMap { mk -> models.map { "$BASE/lst/$mk/$it?$query" } } + makes.map { "$BASE/lst/$it?$query" }
        return urls.distinct().ifEmpty { listOf("$BASE/lst/${SiteNames.slug(criteria.model)}?$query") }
    }

    private fun query(criteria: SearchCriteria): String = buildList {
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
        if (criteria.bodies.isNotEmpty()) add("body" to criteria.bodies.flatMap(::bodyCodes).distinct().joinToString(","))
        if (criteria.drives.isNotEmpty()) add("drivetrain" to criteria.drives.joinToString(",") { driveCode(it) })
        if (criteria.powerMin != null || criteria.powerMax != null) {
            add("powertype" to "hp")
            criteria.powerMin?.let { add("powerfrom" to "$it") }
            criteria.powerMax?.let { add("powerto" to "$it") }
        }
        when (criteria.seller) {
            SellerFilter.PARTICULIER -> add("custtype" to "P")
            SellerFilter.PRO -> add("custtype" to "D")
            SellerFilter.ALL -> Unit
        }
    }.joinToString("&") { (k, v) -> "$k=${enc(v)}" }

    override fun parse(html: String): List<Listing> {
        val root = JsonTools.nextData(JsonTools.document(html)) ?: return emptyList()
        val listings = JsonTools.findArray(root, "listings") { "vehicle" in it || "tracking" in it } ?: return emptyList()
        return listings.mapNotNull { it.obj()?.let(::toListing) }
    }

    private fun toListing(o: JsonObject): Listing? {
        val nativeId = o.str("id") ?: return null
        val price = o.int("tracking", "price") ?: o.int("price", "priceRaw") ?: o.int("price", "priceFormatted") ?: return null
        val make = o.str("vehicle", "make")
        val model = o.str("vehicle", "model")
        val version = o.str("vehicle", "modelVersionInput") ?: o.str("vehicle", "variant")
        // "Peugeot" + "308" + "1.5 BlueHDi 130 Allure", without repeating the model when the version starts with it.
        val repeatsModel = model != null && version != null && Text.normalize(version).startsWith(Text.normalize(model))
        val title = listOfNotNull(make, model.takeUnless { repeatsModel }, version)
            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { return null }
        val details = o["vehicleDetails"].arr()?.mapNotNull { it.obj() }.orEmpty()
        fun detail(vararg icons: String) = details.firstOrNull { it.str("iconName") in icons }?.str("data")
        val url = o.str("url")?.let { if (it.startsWith("http")) it else "$BASE$it" } ?: "$BASE/offres/$nativeId"
        val images = o["images"].strings().map { it.replace(Regex("/\\d+x\\d+\\.(webp|jpg)$"), "/720x540.$1") }
        val power = detail("speedometer")?.let { Regex("(\\d+)\\s*(CH|Ch|ch|PS|hp)").find(it)?.groupValues?.get(1)?.toInt() }
        return Listing(
            id = "$ID:$nativeId",
            source = ID,
            title = title,
            price = price,
            url = url,
            make = make,
            model = model,
            mileageKm = o.int("tracking", "mileage") ?: Text.digits(o.str("vehicle", "mileageInKm") ?: detail("mileage_odometer", "mileage_road")),
            year = Text.year(o.str("tracking", "firstRegistration") ?: detail("calendar")),
            fuel = Fuel.parse(o.str("vehicle", "fuel") ?: detail("gas_pump") ?: o.str("tracking", "fuelType")),
            gearbox = Gearbox.parse(o.str("vehicle", "transmission") ?: detail("gearbox", "transmission")),
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

    /** Body codes of AutoScout24's taxonomy. */
    private fun bodyCodes(b: BodyType): List<String> = when (b) {
        BodyType.CITADINE -> listOf("1")
        BodyType.CABRIOLET -> listOf("2")
        BodyType.COUPE -> listOf("3")
        BodyType.SUV, BodyType.PICKUP -> listOf("4")
        BodyType.BREAK -> listOf("5")
        BodyType.BERLINE -> listOf("6")
        BodyType.COMPACTE -> listOf("1", "6")
        BodyType.MONOSPACE -> listOf("12")
        BodyType.UTILITAIRE -> listOf("13")
    }

    private fun driveCode(d: Drive) = when (d) {
        Drive.TRACTION -> "F"
        Drive.PROPULSION -> "R"
        Drive.INTEGRALE -> "4"
    }

    companion object {
        const val ID = "autoscout24"
        private const val BASE = "https://www.autoscout24.fr"

        /** "Mercedes-Benz" → "mercedes-benz", "Série 3" → "serie-3". */
        fun slug(s: String): String = SiteNames.slug(s)
    }
}
