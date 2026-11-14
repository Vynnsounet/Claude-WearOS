package com.radaroccaz.app.sources

import com.radaroccaz.app.data.AccidentStatus
import com.radaroccaz.app.data.Drive
import com.radaroccaz.app.data.Fact
import com.radaroccaz.app.data.FactSource
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.VehicleHistory
import com.radaroccaz.app.data.VehicleOptions
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * autohero.com/fr, an online reseller (inspected cars, home delivery). The search page
 * `/fr/search/?brand0=<make>&models0=<model>` embeds its Apollo GraphQL cache as a JSON
 * string; each ad gives price, mileage, registration, energy and gearbox codes, drive,
 * power in kW and history: previous owners, accidents, service book.
 */
class AutoheroSource(fetcher: HttpFetcher) : HtmlListingSource(fetcher) {
    override val id = ID
    override val name = "Autohero"
    override val description = "Professionnel en ligne, voitures inspectées et livrées"

    /**
     * `/fr/auto/<make>-<model>/` pages exist for the common models (a true 404 otherwise);
     * the search page takes the model's label in lower case ("c3 aircross", "série 3");
     * the make page comes last.
     */
    override fun searchUrls(criteria: SearchCriteria): List<String> {
        val make = SiteNames.makeSlugs(criteria.make).first()
        val models = SiteNames.modelSlugs(criteria.model)
        // The site shows 24 cars per page without pagination in the URL: its own filters keep them relevant.
        val filters = buildList {
            criteria.budgetMin?.let { add("priceMin=$it") }
            criteria.budgetMax?.let { add("priceMax=$it") }
            criteria.mileageMax?.let { add("mileageMax=$it") }
            criteria.yearMin?.let { add("yearMin=$it") }
        }.joinToString("&")
        fun withFilters(url: String) = if (filters.isEmpty()) url else url + (if ('?' in url) "&" else "?") + filters
        return (models.map { "$BASE/fr/auto/$make-$it/" } +
            listOfNotNull(criteria.model.takeIf { it.isNotBlank() }?.let { "$BASE/fr/search/?brand0=$make&models0=${enc(it.trim().lowercase())}" }) +
            "$BASE/fr/auto/$make/").distinct().map(::withFilters)
    }

    override fun parse(html: String): List<Listing> {
        val literal = apolloLiteral(html) ?: return emptyList()
        val inner = (JsonTools.parseOrNull(literal) as? JsonPrimitive)?.content ?: return emptyList()
        val state = JsonTools.parseOrNull(inner) ?: return emptyList()
        return JsonTools.objects(state).filter { "offerPrice" in it && "mileage" in it && "manufacturer" in it }
            .mapNotNull(::toListing).distinctBy { it.id }.toList()
    }

    private fun toListing(o: JsonObject): Listing? {
        val id = o.str("id") ?: return null
        val minor = o.int("offerPrice", "amountMinorUnits") ?: return null
        val major = o.int("offerPrice", "conversionMajor") ?: 100
        val make = o.str("manufacturer")
        val model = o.str("model")
        val title = listOfNotNull(make, model, o.str("subType"), o.str("subTypeExtra")).joinToString(" ")
        val power = o.int("kw")?.let { (it * 1.35962).roundToInt() }
        val fuel = when (o.int("fuelType")) {
            1039 -> Fuel.ESSENCE
            1040 -> Fuel.DIESEL
            1046 -> Fuel.HYBRIDE_RECHARGEABLE
            else -> Fuel.parse(title) ?: guessFuel(title)
        }
        val gearbox = when (o.int("gearType")) {
            1138 -> Gearbox.MANUELLE
            1139 -> Gearbox.AUTOMATIQUE
            else -> null
        }
        val history = VehicleHistory(
            owners = o.int("carPreownerCount")?.takeIf { it >= 1 }?.let { Fact(it, FactSource.SITE, "Propriétaires précédents : $it") },
            accident = o.int("numberOfAccidents")?.let { n ->
                Fact(if (n == 0) AccidentStatus.NONE else AccidentStatus.DAMAGED, FactSource.SITE, "Accidents déclarés : $n")
            },
            serviceRecords = (o["hasFilledServiceBook"] as? JsonPrimitive)?.content?.takeIf { it == "true" }
                ?.let { Fact(true, FactSource.SITE, "Carnet d'entretien rempli") },
        )
        val damages = o.int("numberOfDamages")
        val description = listOfNotNull(
            o.str("subTypeExtra")?.let { "Finition $it" },
            damages?.takeIf { it > 0 }?.let { "$it défauts esthétiques documentés par Autohero (photos sur l'annonce)" },
            o.str("emissionSticker")?.let { "Vignette ${it.replace("critair-", "Crit'Air ")}" },
        ).joinToString(". ")
        return Listing(
            id = "$ID:$id",
            source = ID,
            title = title,
            price = minor / major,
            url = "$BASE/fr/${o.str("carUrlTitle") ?: "voiture"}/id/$id/",
            make = make,
            model = model,
            mileageKm = o.int("mileage", "distance"),
            year = o.int("firstRegistrationYear") ?: o.int("builtYear"),
            fuel = fuel,
            gearbox = gearbox,
            sellerType = SellerType.PRO,
            city = o.str("esBranch", "city"),
            postalCode = o.str("esBranch", "zipcode"),
            imageUrls = listOfNotNull((o.str("mainImageUrl") ?: o.str("ahMainImageUrl"))?.replace("{size}", "768x432-")),
            description = description,
            publishedAt = o.str("firstPublishedAt")?.let { "${it.take(4)}-${it.drop(4).take(2)}-${it.drop(6).take(2)}" },
            powerHp = power,
            drive = Drive.parse(o.str("driveTrain")),
            options = VehicleOptions.detect(title),
            history = history,
        ).withHistory()
    }

    /** The JSON string literal assigned to window.__APOLLO_STATE__ (scanned by hand: it is several hundred kB). */
    private fun apolloLiteral(html: String): String? {
        val marker = html.indexOf("__APOLLO_STATE__")
        if (marker < 0) return null
        val start = html.indexOf('"', html.indexOf('=', marker))
        if (start < 0) return null
        var i = start + 1
        while (i < html.length) {
            when (html[i]) {
                '\\' -> i += 2
                '"' -> return html.substring(start, i + 1)
                else -> i++
            }
        }
        return null
    }

    private fun guessFuel(title: String): Fuel? {
        val t = title.lowercase()
        return when {
            "phev" in t || "plug" in t -> Fuel.HYBRIDE_RECHARGEABLE
            "hybrid" in t -> Fuel.HYBRIDE
            "hdi" in t || "tdi" in t || "dci" in t || "crdi" in t || "d " in t -> Fuel.DIESEL
            else -> null
        }
    }

    companion object {
        const val ID = "autohero"
        private const val BASE = "https://www.autohero.com"
    }
}
