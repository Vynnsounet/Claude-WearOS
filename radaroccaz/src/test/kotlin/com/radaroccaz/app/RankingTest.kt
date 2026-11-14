package com.radaroccaz.app

import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Priorities
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerFilter
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.VehicleOptions
import com.radaroccaz.app.domain.MarketModel
import com.radaroccaz.app.domain.Ranking
import com.radaroccaz.app.domain.SortOrder
import com.radaroccaz.app.domain.Verdict
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {
    private val c = SearchCriteria(make = "Peugeot", model = "308", budgetMax = 15_000, mileageMax = 120_000, yearMin = 2016)

    @Test
    fun filtersOutWhatContradictsTheCriteria() {
        assertTrue(Ranking.matches(car("ok", 13_000), c))
        assertFalse("over budget", Ranking.matches(car("a", 15_500), c))
        assertFalse("too many km", Ranking.matches(car("b", 9_000, km = 150_000), c))
        assertFalse("too old", Ranking.matches(car("c", 9_000, year = 2014), c))
        assertFalse("accessory price", Ranking.matches(car("d", 220, title = "4 jantes Peugeot 308"), c))
        assertTrue("unknown mileage is kept", Ranking.matches(car("e", 9_000, km = null), c))

        val diesel = c.copy(fuels = setOf(Fuel.DIESEL), gearbox = Gearbox.AUTOMATIQUE, seller = SellerFilter.PRO)
        assertFalse(Ranking.matches(car("f", 9_000, fuel = Fuel.ESSENCE, gearbox = Gearbox.AUTOMATIQUE, seller = SellerType.PRO), diesel))
        assertFalse(Ranking.matches(car("g", 9_000, gearbox = Gearbox.MANUELLE, seller = SellerType.PRO), diesel))
        assertFalse(Ranking.matches(car("h", 9_000, gearbox = Gearbox.AUTOMATIQUE, seller = SellerType.PARTICULIER), diesel))
        assertTrue(Ranking.matches(car("i", 9_000, gearbox = Gearbox.AUTOMATIQUE, seller = SellerType.PRO), diesel))

        val gironde = c.copy(department = "33")
        assertTrue(Ranking.matches(car("j", 9_000, postalCode = "33700"), gironde))
        assertFalse(Ranking.matches(car("k", 9_000, postalCode = "75015"), gironde))
    }

    @Test
    fun relevanceChecksTheModel() {
        assertFalse(Ranking.isRelevant(car("a", 9_000, title = "Peugeot 3008 1.6 BlueHDi"), c))
        assertTrue(Ranking.isRelevant(car("b", 9_000, title = "308 SW GT Line", make = null), c))
        assertFalse(Ranking.isRelevant(car("c", 9_000, title = "Renault Clio 308 ch", make = "Renault"), c))
        val mazda = SearchCriteria(make = "Mazda", model = "3")
        assertTrue(Ranking.isRelevant(car("d", 9_000, title = "Mazda 3 2.0 Skyactiv-G", make = "Mazda"), mazda))
        assertFalse(Ranking.isRelevant(car("e", 9_000, title = "Mazda CX-30 2.0", make = "Mazda"), mazda))
        val classA = SearchCriteria(make = "Mercedes-Benz", model = "Classe A")
        assertTrue(Ranking.isRelevant(car("f", 9_000, title = "Mercedes Classe-A 180 d Progressive", make = "Mercedes"), classA))
    }

    @Test
    fun sameCarOnTwoSitesIsMerged() {
        val lbc = car("1", 12_990, km = 98_000, year = 2019, source = "leboncoin")
        val as24 = car("2", 12_990, km = 98_200, year = 2019, source = "autoscout24", images = listOf("a", "b"))
        val other = car("3", 11_500, km = 120_000, year = 2018)
        val merged = Ranking.deduplicate(listOf(lbc, as24, other))
        assertEquals(2, merged.size)
        val kept = merged.first { it.price == 12_990 }
        assertEquals("autoscout24", kept.source)
        assertEquals(listOf("leboncoin"), kept.alsoOn)
    }

    @Test
    fun marketModelFindsTheBargain() {
        // Fair price ≈ 20 000 × 0.88^age × (1 − km/400 000); one car is 25 % under.
        val cars = (0 until 10).map { i ->
            val year = 2016 + i % 7
            val km = 30_000 + (i * 13_000) % 110_000
            val fair = 20_000 * Math.pow(0.88, (2022 - year).toDouble()) * (1 - km / 400_000.0)
            car("$i", (fair * if (i == 4) 0.75 else 1.0).toInt(), km = km, year = year)
        }
        val model = assertNotNull(MarketModel.fit(cars))
        val ranked = Ranking.score(cars, SearchCriteria(make = "Peugeot", model = "308"))
        val bargain = ranked.first { it.listing.id.endsWith(":4") }
        assertTrue("deal ${bargain.dealPercent}", bargain.dealPercent!! <= -20)
        ranked.filter { it !== bargain }.forEach { assertTrue("${it.dealPercent}", abs(it.dealPercent!!) <= 8) }
        assertEquals(bargain, Ranking.sort(ranked, SortOrder.DEAL).first())
        assertNull(MarketModel.fit(cars.take(3)))
    }

    @Test
    fun prioritiesChangeTheOrder() {
        val cheapHighKm = car("cheap", 9_000, km = 115_000, year = 2017)
        val dearLowKm = car("dear", 14_500, km = 25_000, year = 2021)
        val list = listOf(cheapHighKm, dearLowKm)
        val priceFirst = Ranking.sort(Ranking.score(list, c.copy(priorities = Priorities(price = 3, mileage = 0, age = 0))), SortOrder.BEST)
        assertEquals("leboncoin:cheap", priceFirst.first().listing.id)
        val kmFirst = Ranking.sort(Ranking.score(list, c.copy(priorities = Priorities(price = 0, mileage = 3, age = 3))), SortOrder.BEST)
        assertEquals("leboncoin:dear", kmFirst.first().listing.id)
    }

    @Test
    fun wantedOptionsRaiseTheScore() {
        val wanted = setOf("gps", "camera", "carplay")
        val crit = c.copy(wantedOptions = wanted, priorities = Priorities(price = 1, mileage = 1, age = 1, options = 3))
        val equipped = car("eq", 12_000, options = wanted + "cuir")
        val bare = car("bare", 12_000)
        val scored = Ranking.score(listOf(equipped, bare), crit).associateBy { it.listing.id }
        assertEquals(wanted, scored.getValue("leboncoin:eq").matchedOptions)
        assertTrue(scored.getValue("leboncoin:eq").score > scored.getValue("leboncoin:bare").score)
        assertEquals(1.0, scored.getValue("leboncoin:eq").breakdown.options!!, 1e-9)
    }

    @Test
    fun scoresStayInRange() {
        val list = (1..15).map { car("$it", 5_000 + it * 600, km = 20_000 + it * 6_000, year = 2016 + it % 6) }
        Ranking.rank(list, c).forEach { assertTrue(it.score in 0..100) }
        assertEquals(Verdict.EXCELLENT, Verdict.of(85))
        assertEquals(Verdict.WEAK, Verdict.of(20))
    }

    @Test
    fun optionsAreDetectedInFreeText() {
        val found = VehicleOptions.detect("Peugeot 308 GT-Line, toit pano, radars AV/AR, clim bi-zone, ACC, Apple CarPlay, 1ère main")
        assertEquals(setOf("toit", "radar", "clim", "regulateur", "carplay", "premiere_main"), found)
        assertTrue(VehicleOptions.detect("Vendue avec accessoires").isEmpty())
    }
}
