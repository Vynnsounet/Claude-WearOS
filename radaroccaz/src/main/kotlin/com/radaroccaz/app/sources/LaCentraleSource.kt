package com.radaroccaz.app.sources

import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerFilter
import com.radaroccaz.app.data.Text

/**
 * lacentrale.fr. Its listing page embeds the search hits as JSON (hydration state); their
 * exact shape changes often, so they are read with [GenericExtractor], which recognises
 * car-like objects (price + mileage/year + make/model) wherever they are, then JSON-LD.
 */
class LaCentraleSource(fetcher: HttpFetcher) : HtmlListingSource(fetcher) {
    override val id = ID
    override val name = "La Centrale"
    override val description = "Particuliers et professionnels, cote La Centrale"

    override fun searchUrl(criteria: SearchCriteria): String {
        val params = buildList {
            val make = Text.normalize(criteria.make).uppercase().replace(' ', '-')
            val model = Text.normalize(criteria.model).uppercase()
            if (make.isNotBlank()) add("makesModelsCommercialNames" to if (model.isNotBlank()) "$make:$model" else make)
            criteria.budgetMin?.let { add("priceMin" to "$it") }
            criteria.budgetMax?.let { add("priceMax" to "$it") }
            criteria.mileageMax?.let { add("mileageMax" to "$it") }
            criteria.yearMin?.let { add("yearMin" to "$it") }
            if (criteria.fuels.isNotEmpty()) add("energies" to criteria.fuels.map(::energyCode).distinct().joinToString(","))
            criteria.gearbox?.let { add("gearbox" to if (it == Gearbox.MANUELLE) "MANUAL" else "AUTO") }
            when (criteria.seller) {
                SellerFilter.PARTICULIER -> add("customerFamilyCodes" to "PARTICULIER")
                SellerFilter.PRO -> add("customerFamilyCodes" to "CONCESSIONNAIRE,PROFESSIONNEL")
                SellerFilter.ALL -> Unit
            }
            add("sortBy" to "priceAsc")
        }
        return "https://www.lacentrale.fr/listing?" + params.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }

    override fun parse(html: String): List<Listing> =
        GenericExtractor.extract(html, ID, baseUrl = "https://www.lacentrale.fr") { ref ->
            "https://www.lacentrale.fr/auto-occasion-annonce-$ref.html"
        }

    private fun energyCode(f: Fuel): String = when (f) {
        Fuel.ESSENCE -> "ess"
        Fuel.DIESEL -> "dies"
        Fuel.ELECTRIQUE -> "elec"
        Fuel.HYBRIDE -> "hyb"
        Fuel.HYBRIDE_RECHARGEABLE -> "plug_hyb"
        Fuel.GPL -> "gpl"
    }

    companion object {
        const val ID = "lacentrale"
    }
}
