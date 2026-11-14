package com.radaroccaz.app

import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.sources.AramisAutoSource
import com.radaroccaz.app.sources.AutoScout24Source
import com.radaroccaz.app.sources.AutoheroSource
import com.radaroccaz.app.sources.HttpFetcher
import com.radaroccaz.app.sources.LargusSource
import com.radaroccaz.app.sources.ParuVenduSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Queries the real sites for common models and prints what each returns. Off by default
 * (no network in CI): run with `-Dradar.live=true`.
 */
class LiveSitesTest {
    @Test
    fun realSitesAnswerForCommonModels() = runBlocking {
        assumeTrue(System.getProperty("radar.live") == "true")
        TestCatalog.ensureLoaded()
        val fetcher = HttpFetcher()
        val sources = listOf(AutoScout24Source(fetcher), AramisAutoSource(fetcher), AutoheroSource(fetcher), LargusSource(fetcher), ParuVenduSource(fetcher))
        val models = (System.getProperty("radar.models") ?: "Peugeot:308,Renault:Clio,Renault:Megane,Mercedes-Benz:Classe A,Mercedes-Benz:GLA,BMW:Série 3,BMW:X1,Citroën:C3 Aircross,Volkswagen:Golf,Volkswagen:T-Roc,Dacia:Sandero,Toyota:Yaris,Tesla:Model 3,Audi:A3")
            .split(',').map { it.split(':') }
        val failures = mutableListOf<String>()
        for ((make, model) in models) {
            for (s in sources) {
                val c = SearchCriteria(make = make, model = model)
                val line = try {
                    val page = s.search(c)
                    val relevant = page.listings.count { com.radaroccaz.app.domain.Ranking.isRelevant(it, c) }
                    if (page.listings.isEmpty()) failures += "${s.name} $make $model: 0 annonce"
                    page.listings.filterNot { com.radaroccaz.app.domain.Ranking.isRelevant(it, c) }.take(4).forEach {
                        println("[live]      écartée : ${it.title} (modèle du site : ${it.model})")
                    }
                    page.listings.take(2).forEach { println("[live]      ex. ${it.title} · ${it.price} € · ${it.year} · ${it.mileageKm} km · ${it.fuel} · ${it.gearbox}") }
                    "OK  ${page.listings.size} annonces, $relevant du bon modèle  ${page.url}"
                } catch (e: Exception) {
                    failures += "${s.name} $make $model: ${e::class.simpleName} ${e.message}"
                    "ERR ${e::class.simpleName}: ${e.message}"
                }
                println("[live] ${s.name.padEnd(12)} $make $model -> $line")
                delay(1500)
            }
        }
        println("[live] problèmes : ${failures.size}")
        failures.forEach { println("[live]   $it") }
    }
}
