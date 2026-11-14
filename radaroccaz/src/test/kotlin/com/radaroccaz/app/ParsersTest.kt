package com.radaroccaz.app

import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerFilter
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.sources.AutoScout24Source
import com.radaroccaz.app.sources.GenericExtractor
import com.radaroccaz.app.sources.HttpFetcher
import com.radaroccaz.app.sources.LaCentraleSource
import com.radaroccaz.app.sources.LeboncoinSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

class ParsersTest {
    private val fetcher = HttpFetcher()

    @Test
    fun leboncoin_readsAdsFromNextData() {
        val ads = LeboncoinSource(fetcher).parse(fixture("leboncoin_search.html"))
        assertEquals(3, ads.size)
        val first = ads[0]
        assertEquals("leboncoin:2871234567", first.id)
        assertEquals("Peugeot 308 1.5 BlueHDi 130 GT Line", first.title)
        assertEquals(13490, first.price)
        assertEquals(86500, first.mileageKm)
        assertEquals(2019, first.year)
        assertEquals(Fuel.DIESEL, first.fuel)
        assertEquals(Gearbox.MANUELLE, first.gearbox)
        assertEquals(SellerType.PARTICULIER, first.sellerType)
        assertEquals("Bordeaux", first.city)
        assertEquals("33000", first.postalCode)
        assertEquals(130, first.powerHp)
        assertEquals("2026-11-12T18:21:07", first.publishedAt)
        assertEquals(2, first.imageUrls.size)
        assertTrue(first.imageUrls[0].endsWith("1-large.jpg"))
        assertEquals(setOf("camera", "gps", "carplay", "sieges_chauffants", "carnet", "premiere_main"), first.options)

        val second = ads[1]
        assertEquals(SellerType.PRO, second.sellerType)
        assertEquals(Gearbox.AUTOMATIQUE, second.gearbox)
        assertEquals(Fuel.ESSENCE, second.fuel)
        assertTrue("toit" in second.options && "garantie" in second.options)
    }

    @Test
    fun leboncoin_searchUrl_carriesFilters() {
        val url = LeboncoinSource(fetcher).searchUrl(
            SearchCriteria(make = "Peugeot", model = "308", budgetMax = 15000, mileageMax = 100000, yearMin = 2017,
                fuels = setOf(Fuel.DIESEL), gearbox = Gearbox.MANUELLE, seller = SellerFilter.PARTICULIER),
        )
        assertTrue(url, url.startsWith("https://www.leboncoin.fr/recherche?category=2&text=Peugeot%20308"))
        listOf("price=min-15000", "mileage=min-100000", "regdate=2017-max", "fuel=2", "gearbox=1", "owner_type=private", "sort=price")
            .forEach { assertTrue("$it in $url", it in url) }
    }

    @Test
    fun autoscout24_readsListings() {
        val list = AutoScout24Source(fetcher).parse(fixture("autoscout24_search.html"))
        assertEquals(2, list.size)
        val a = list[0]
        assertEquals("Peugeot 308 1.5 BlueHDi 130 Allure GPS Caméra", a.title)
        assertEquals(12990, a.price)
        assertEquals(98000, a.mileageKm)
        assertEquals(2019, a.year)
        assertEquals(Fuel.DIESEL, a.fuel)
        assertEquals(Gearbox.MANUELLE, a.gearbox)
        assertEquals(SellerType.PRO, a.sellerType)
        assertEquals(131, a.powerHp)
        assertTrue(a.url.startsWith("https://www.autoscout24.fr/offres/peugeot-308"))
        assertTrue(a.imageUrls.first().endsWith("/720x540.webp"))
        assertTrue("gps" in a.options && "camera" in a.options)

        val b = list[1]
        assertEquals(Fuel.HYBRIDE, b.fuel)
        assertEquals(Gearbox.AUTOMATIQUE, b.gearbox)
        assertEquals(SellerType.PARTICULIER, b.sellerType)
        assertEquals(2022, b.year)
    }

    @Test
    fun autoscout24_slugs() {
        assertEquals("mercedes-benz", AutoScout24Source.slug("Mercedes-Benz"))
        assertEquals("serie-3", AutoScout24Source.slug("Série 3"))
        val url = AutoScout24Source(fetcher).searchUrl(SearchCriteria(make = "Peugeot", model = "308", budgetMax = 15000, seller = SellerFilter.PRO))
        assertTrue(url, url.startsWith("https://www.autoscout24.fr/lst/peugeot/308?"))
        assertTrue("priceto=15000" in url && "custtype=D" in url)
    }

    @Test
    fun lacentrale_readsHydrationState() {
        val list = LaCentraleSource(fetcher).parse(fixture("lacentrale_search.html"))
        assertEquals(2, list.size)
        val a = list.first { it.id == "lacentrale:E108412345" }
        assertEquals("Peugeot 308 1.6 Bluehdi 120 S&S Active Business", a.title)
        assertEquals(10990, a.price)
        assertEquals(112000, a.mileageKm)
        assertEquals(2018, a.year)
        assertEquals(Fuel.DIESEL, a.fuel)
        assertEquals(Gearbox.MANUELLE, a.gearbox)
        assertEquals(SellerType.PARTICULIER, a.sellerType)
        assertEquals("Nantes", a.city)
        assertEquals("44", a.postalCode)
        assertEquals(120, a.powerHp)
        assertEquals("https://www.lacentrale.fr/auto-occasion-annonce-E108412345.html", a.url)
        val b = list.first { it.id == "lacentrale:B108499999" }
        assertEquals(SellerType.PRO, b.sellerType)
        assertEquals(Gearbox.MANUELLE, b.gearbox)
        assertEquals("https://photo.lacentrale.fr/def/1.jpg", b.imageUrls.single())
    }

    @Test
    fun lacentrale_searchUrl() {
        val url = LaCentraleSource(fetcher).searchUrl(SearchCriteria(make = "Citroën", model = "C3 Aircross", budgetMax = 12000, fuels = setOf(Fuel.ESSENCE)))
        assertTrue(url, "makesModelsCommercialNames=CITROEN%3AC3%20AIRCROSS" in url)
        assertTrue("priceMax=12000" in url && "energies=ess" in url)
    }

    @Test
    fun genericExtractor_fallsBackToJsonLd() {
        val list = GenericExtractor.extract(fixture("jsonld_listing.html"), "test", "https://example.org")
        val clio = list.single()
        assertEquals("Renault Clio V 1.0 TCe 90 Zen", clio.title)
        assertEquals(12900, clio.price)
        assertEquals(38000, clio.mileageKm)
        assertEquals(2021, clio.year)
        assertEquals("Renault", clio.make)
        assertEquals(setOf("clim", "radar"), clio.options)
    }

    @Test
    fun pagesWithoutData_giveNothing() {
        assertTrue(LeboncoinSource(fetcher).parse("<html><body>Rien</body></html>").isEmpty())
        assertTrue(AutoScout24Source(fetcher).parse(fixture("leboncoin_search.html")).isEmpty())
        assertTrue(GenericExtractor.extract("<html></html>", "x", "https://x").isEmpty())
    }

    @Test
    fun challengePagesAreDetected() {
        assertTrue(HttpFetcher.isChallenge(fixture("captcha.html")))
        assertFalse(HttpFetcher.isChallenge(fixture("leboncoin_search.html")))
    }

    @Test
    fun enumParsing() {
        assertEquals(Fuel.DIESEL, Fuel.parse("Gazole"))
        assertEquals(Fuel.HYBRIDE_RECHARGEABLE, Fuel.parse("Hybride rechargeable essence"))
        assertEquals(Fuel.HYBRIDE, Fuel.parse("Hybride essence électrique"))
        assertEquals(Fuel.ELECTRIQUE, Fuel.parse("Électrique"))
        assertNull(Fuel.parse("Inconnu"))
        assertEquals(Gearbox.AUTOMATIQUE, Gearbox.parse("Boîte automatique"))
        assertEquals(Gearbox.MANUELLE, Gearbox.parse("Mécanique"))
        assertEquals(SellerType.PRO, SellerType.parse("Professionnel"))
        assertEquals(SellerType.PARTICULIER, SellerType.parse("private"))
    }
}
