package com.radaroccaz.app.sources

import com.radaroccaz.app.data.BodyType
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleOptions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * aramisauto.com, a professional reseller (recent used cars, 0 km). Pages are
 * `/voitures/<make>/<model>/offres/` (the site writes "mercedes", not "mercedes-benz");
 * robots.txt forbids query parameters such as sort or mileage, so filtering is done here.
 * The Nuxt state holds each vehicle (energy, gearbox, body, mileage, power, equipment);
 * the JSON-LD item list gives each vehicle's page.
 */
class AramisAutoSource(fetcher: HttpFetcher) : HtmlListingSource(fetcher) {
    override val id = ID
    override val name = "Aramis Auto"
    override val description = "Professionnel : véhicules récents révisés et garantis"

    override fun searchUrls(criteria: SearchCriteria): List<String> =
        SiteNames.candidates(criteria, makePrefer = MAKES) { mk, model ->
            if (model == null) "$BASE/voitures/$mk/offres/" else "$BASE/voitures/$mk/$model/offres/"
        }

    override fun parse(html: String): List<Listing> {
        val doc = JsonTools.document(html)
        val flat = doc.selectFirst("script#__NUXT_DATA__")?.data()?.let(JsonTools::parseOrNull) as? JsonArray ?: return emptyList()
        val urls = JsonTools.jsonLd(doc).flatMap { JsonTools.objects(it).toList() }
            .mapNotNull { it.str("url") }.filter { "vehicleId=" in it }
            .associateBy { it.substringAfter("vehicleId=").substringBefore('&') }
        return NuxtData.objects(flat) { "offerId" in it && "mileage" in it && "makerName" in it }
            .mapNotNull { toListing(it, urls) }
            .distinctBy { it.id }
    }

    private fun toListing(o: JsonObject, urls: Map<String, String>): Listing? {
        val vehicleId = o.str("id") ?: return null
        val price = o.int("sellingPriceWithTaxes", "eur") ?: return null
        val make = o.str("makerName")
        val model = o.str("modelName")
        val engine = o.str("engineName")
        val trim = o.str("trimLevel")
        val title = listOfNotNull(make, model, engine, trim).joinToString(" ")
        val highlights = o["highlights"].arr()?.mapNotNull { it.obj()?.str("label") }.orEmpty()
        val url = urls[vehicleId] ?: "$BASE/voitures/${SiteNames.slug(make.orEmpty())}/${SiteNames.slug(model.orEmpty())}/${SiteNames.slug(trim.orEmpty())}/${o.str("offerId")}?vehicleId=$vehicleId"
        val offerType = o.str("offerType", "label")
        val description = buildList {
            offerType?.let { add("Offre : $it") }
            if (highlights.isNotEmpty()) add("Équipements : " + highlights.joinToString(", "))
            o.str("freeDeliveryLocation", "label")?.let { add("Livraison gratuite à $it") }
        }.joinToString(". ")
        return Listing(
            id = "$ID:$vehicleId",
            source = ID,
            title = title,
            price = price,
            url = url,
            make = make,
            model = model,
            mileageKm = o.int("mileage", "km"),
            year = Text.year(o.str("firstRegistrationDate")),
            fuel = Fuel.parse(o.str("energyType", "label") ?: o.str("energyType", "id")),
            gearbox = Gearbox.parse(o.str("transmission", "label") ?: o.str("transmission", "id")),
            sellerType = SellerType.PRO,
            city = o.str("freeDeliveryLocation", "label"),
            imageUrls = listOfNotNull(o.str("thumbnailUrl")),
            description = description,
            publishedAt = null,
            powerHp = o.int("mainEnginePower", "ch"),
            body = BodyType.parse(o.str("body", "label")),
            options = VehicleOptions.detect(title, highlights.joinToString(", ")),
        ).withHistory()
    }

    companion object {
        const val ID = "aramisauto"
        private const val BASE = "https://www.aramisauto.com"
        private val MAKES = mapOf("mercedes-benz" to "mercedes", "ds-automobiles" to "ds", "lynk-co" to "lynk-co")
    }
}
