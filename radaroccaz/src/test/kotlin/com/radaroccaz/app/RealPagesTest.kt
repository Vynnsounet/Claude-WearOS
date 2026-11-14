package com.radaroccaz.app

import com.radaroccaz.app.data.AccidentStatus
import com.radaroccaz.app.data.BodyType
import com.radaroccaz.app.data.Drive
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.sources.AramisAutoSource
import com.radaroccaz.app.sources.AutoScout24Source
import com.radaroccaz.app.sources.AutoheroSource
import com.radaroccaz.app.sources.HttpFetcher
import com.radaroccaz.app.sources.LargusSource
import com.radaroccaz.app.sources.ParuVenduSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsers against pages saved from the real sites (trimmed, without sellers' personal data). */
class RealPagesTest {
    private val fetcher = HttpFetcher()

    @Test
    fun autoscout24() {
        val list = AutoScout24Source(fetcher).parse(fixture("as24_real_search.html"))
        assertEquals(4, list.size)
        val a = list.first()
        assertTrue(a.title, a.title.startsWith("Peugeot 308"))
        assertEquals(1400, a.price)
        assertEquals(351_200, a.mileageKm)
        assertEquals(2009, a.year)
        assertEquals(Fuel.DIESEL, a.fuel)
        assertEquals(Gearbox.MANUELLE, a.gearbox)
        assertEquals(SellerType.PARTICULIER, a.sellerType)
        assertEquals(90, a.powerHp)
        assertTrue(a.url.startsWith("https://www.autoscout24.fr/offres/"))
        assertTrue(list.all { it.price > 0 && it.mileageKm != null && it.year != null })
    }

    @Test
    fun aramisAuto() {
        val list = AramisAutoSource(fetcher).parse(fixture("aramis_real_search.html"))
        assertEquals(3, list.size)
        val a = list.first()
        assertEquals("Peugeot 308 1.5 BlueHDi 130 EAT8 Allure + GPS", a.title)
        assertEquals(25_799, a.price)
        assertEquals(Fuel.DIESEL, a.fuel)
        assertEquals(Gearbox.AUTOMATIQUE, a.gearbox)
        assertEquals(BodyType.COMPACTE, a.body)
        assertEquals(130, a.powerHp)
        assertEquals(2026, a.year)
        assertEquals(SellerType.PRO, a.sellerType)
        assertTrue(a.url, a.url.startsWith("https://www.aramisauto.com/voitures/peugeot/308/") && "vehicleId=" in a.url)
        assertTrue("camera" in a.options && "carplay" in a.options)
    }

    @Test
    fun autohero() {
        val list = AutoheroSource(fetcher).parse(fixture("autohero_real_search.html"))
        assertEquals(3, list.size)
        val a = list.first()
        assertEquals("Peugeot 308 1.2 PureTech GT Line", a.title)
        assertEquals(8990, a.price)
        assertEquals(76_019, a.mileageKm)
        assertEquals(2015, a.year)
        assertEquals(Fuel.ESSENCE, a.fuel)
        assertEquals(Gearbox.MANUELLE, a.gearbox)
        assertEquals(Drive.TRACTION, a.drive)
        assertEquals(131, a.powerHp)
        assertEquals("60160", a.postalCode)
        assertTrue(a.imageUrls.single().contains("768x432-"))
        assertEquals(AccidentStatus.NONE, a.history.accident?.value)
        assertTrue(a.url, a.url.matches(Regex("https://www\\.autohero\\.com/fr/peugeot-308/id/[0-9a-f-]+/")))
        assertEquals(1, list[1].history.owners?.value)
    }

    @Test
    fun largus() {
        val list = LargusSource(fetcher).parse(fixture("largus_real_search.html"))
        assertEquals(3, list.size)
        val a = list.first()
        assertEquals("PEUGEOT 308 SW 1.5 BlueHDi 130 - EAT8 GT", a.title)
        assertEquals(15_990, a.price)
        assertEquals(2022, a.year)
        assertEquals(125, a.mileageKm)
        assertEquals(Fuel.DIESEL, a.fuel)
        assertEquals(Gearbox.AUTOMATIQUE, a.gearbox)
        assertNotNull(a.city)
        assertEquals("2026-11-14", a.publishedAt)
        assertTrue(a.url.startsWith("https://occasion.largus.fr/auto/annonce-"))
    }

    @Test
    fun paruvendu() {
        val list = ParuVenduSource(fetcher).parse(fixture("paruvendu_real_search.html"))
        assertTrue(list.size >= 2)
        list.forEach {
            assertTrue(it.price > 0)
            assertNotNull(it.year)
            assertNotNull(it.mileageKm)
            assertTrue(it.url, it.url.startsWith("https://www.paruvendu.fr/a/voiture-occasion/peugeot/308/"))
        }
        assertTrue(list.any { it.postalCode != null && it.city != null })
        assertTrue(list.any { it.fuel != null && it.gearbox != null })
    }
}
