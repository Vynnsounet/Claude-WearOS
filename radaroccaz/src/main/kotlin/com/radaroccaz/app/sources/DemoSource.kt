package com.radaroccaz.app.sources

import com.radaroccaz.app.data.CarCatalog
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerFilter
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleOptions
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Generated listings, the same for the same search, to try the app without network access.
 * Clearly labelled "Démo" in the interface; never mixed with real sources.
 */
class DemoSource(
    private val today: LocalDate = LocalDate.now(),
    private val latencyMs: Long = 700,
) : ListingSource {
    override val id = ID
    override val name = "Démo"
    override val description = "Annonces fictives pour essayer l'application"

    override fun searchUrl(criteria: SearchCriteria) = "https://www.example.com/demo"

    override suspend fun search(criteria: SearchCriteria): List<Listing> {
        if (latencyMs > 0) delay(latencyMs)
        return generate(criteria, 30)
    }

    fun generate(criteria: SearchCriteria, count: Int = 24): List<Listing> {
        val rnd = Random(Text.compact(criteria.title).hashCode())
        val make = CarCatalog.canonicalMake(criteria.make.ifBlank { "Peugeot" })
        val model = criteria.model.ifBlank { CarCatalog.suggestModels(make, "").firstOrNull() ?: "" }
        val thisYear = today.year
        val minYear = criteria.yearMin ?: (thisYear - 12)
        // New price chosen so that a car of median age sits around the budget.
        val medianAge = (thisYear - minYear) / 2.0
        val newPrice = criteria.budgetMax?.let { (it / (0.86.pow(medianAge) * 0.82)).roundToInt() }
            ?: (18_000 + (Text.compact(make + model).hashCode().mod(25)) * 1_400)
        val kmCap = criteria.mileageMax ?: 220_000
        return (0 until count).map { i ->
            val year = (minYear + rnd.nextInt(max(1, thisYear - minYear + 1))).coerceAtMost(thisYear)
            val age = thisYear - year
            val km = ((age * 13_500 + rnd.nextInt(-9_000, 16_000)).coerceIn(4_000, kmCap) / 100) * 100
            val fuel = criteria.fuels.randomOrNull(rnd)
                ?: listOf(Fuel.ESSENCE, Fuel.DIESEL, Fuel.DIESEL, Fuel.HYBRIDE, Fuel.ESSENCE).random(rnd)
            val gearbox = criteria.gearbox ?: if (rnd.nextInt(3) == 0) Gearbox.AUTOMATIQUE else Gearbox.MANUELLE
            val seller = when (criteria.seller) {
                SellerFilter.PARTICULIER -> SellerType.PARTICULIER
                SellerFilter.PRO -> SellerType.PRO
                SellerFilter.ALL -> if (rnd.nextBoolean()) SellerType.PARTICULIER else SellerType.PRO
            }
            val fair = newPrice * 0.86.pow(age) * (1 - km / 400_000.0) * (if (seller == SellerType.PRO) 1.07 else 1.0)
            val price = ((fair * rnd.nextDouble(0.8, 1.2)).roundToInt() / 50 * 50).coerceAtLeast(1_500)
            val trim = listOf("Active", "Allure", "GT Line", "Business", "Style", "Intens", "Zen", "Titanium", "Life").random(rnd)
            val engine = when (fuel) {
                Fuel.DIESEL -> listOf("1.5 dCi 115", "1.6 HDi 110", "2.0 TDI 150", "1.5 BlueHDi 130").random(rnd)
                Fuel.ELECTRIQUE -> listOf("Électrique 136 ch", "EV 50 kWh").random(rnd)
                Fuel.HYBRIDE, Fuel.HYBRIDE_RECHARGEABLE -> listOf("Hybrid 136", "E-Tech 145", "Hybrid 180 e-EAT8").random(rnd)
                else -> listOf("1.2 PureTech 110", "1.0 TCe 90", "1.5 TSI 150", "1.0 EcoBoost 125").random(rnd)
            }
            val opts = VehicleOptions.all.shuffled(rnd).take(rnd.nextInt(1, 8))
            val (city, zip) = CITIES.random(rnd)
            val description = buildString {
                append("$make $model $engine $trim de $year, ${Format.km(km)}. ")
                append("Équipements : ").append(opts.joinToString(", ") { it.keywords.first() }).append(". ")
                append(historyText(rnd, year, km, seller))
            }
            Listing(
                id = "$ID:${Text.compact(make + model)}-$i",
                source = ID,
                title = "$make $model $engine $trim",
                price = price,
                url = "https://www.example.com/demo/$i",
                make = make,
                model = model,
                mileageKm = km,
                year = year,
                fuel = fuel,
                gearbox = gearbox,
                sellerType = seller,
                city = city,
                postalCode = zip,
                description = description,
                publishedAt = today.minusDays(rnd.nextLong(0, 21)).toString(),
                powerHp = Text.digits(engine.substringAfterLast(' '))?.takeIf { it in 60..400 },
                options = VehicleOptions.detect(description),
            ).withHistory()
        }
    }

    override suspend fun details(listing: Listing): Listing {
        if (latencyMs > 0) delay(latencyMs / 2)
        return listing.copy(detailsLoaded = true)
    }

    /** A plausible mix of what sellers write about a car's past, good and bad. */
    private fun historyText(rnd: Random, year: Int, km: Int, seller: SellerType): String {
        val age = (today.year - year).coerceAtLeast(0)
        val parts = mutableListOf<String>()
        when (rnd.nextInt(10)) {
            in 0..3 -> parts += "Première main"
            in 4..6 -> parts += "${2 + rnd.nextInt(2)} propriétaires"
            else -> Unit
        }
        when (rnd.nextInt(12)) {
            in 0..5 -> parts += "jamais accidentée"
            6 -> parts += "petit choc arrière réparé"
            7 -> parts += "accidentée à l'avant, réparée en garage"
            else -> Unit
        }
        if (rnd.nextInt(3) > 0) parts += "carnet d'entretien à jour avec factures"
        if (age > 4) {
            val ctYear = today.year - 1
            val ctKm = (km - rnd.nextInt(6_000, 16_000)).coerceAtLeast(1_000)
            parts += when (rnd.nextInt(8)) {
                0 -> "CT avec contre-visite (pneus)"
                1 -> "CT à faire"
                // A reading higher than today's mileage: what a rolled-back odometer looks like.
                2 -> "CT de mars $ctYear à ${Format.km(km + 23_000)}"
                else -> "CT OK, dernier CT de mars $ctYear à ${Format.km(ctKm)}"
            }
        }
        if (km > 110_000 && rnd.nextBoolean()) parts += "distribution faite à ${Format.km(km - rnd.nextInt(5_000, 30_000))}"
        when (rnd.nextInt(14)) {
            0 -> parts += "embrayage à prévoir"
            1 -> parts += "voyant moteur allumé"
            2 -> parts += "kilométrage non garanti"
            3 -> parts += "pneus neufs"
            4 -> parts += "rapport HistoVec disponible sur demande"
            else -> Unit
        }
        parts += if (seller == SellerType.PRO) "garantie 12 mois" else "non fumeur, aucun frais à prévoir"
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }

    companion object {
        const val ID = "demo"
        private val CITIES = listOf(
            "Bordeaux" to "33000", "Lyon" to "69003", "Nantes" to "44000", "Lille" to "59000",
            "Toulouse" to "31000", "Rennes" to "35000", "Marseille" to "13008", "Paris" to "75015",
            "Strasbourg" to "67000", "Montpellier" to "34000", "Pau" to "64000", "Tours" to "37000",
        )
    }
}
