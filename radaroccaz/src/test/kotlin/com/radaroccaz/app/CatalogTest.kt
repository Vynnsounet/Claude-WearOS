package com.radaroccaz.app

import com.radaroccaz.app.data.BodyType
import com.radaroccaz.app.data.CarCatalog
import com.radaroccaz.app.data.Drive
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.Priorities
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.VehicleSpecs
import com.radaroccaz.app.domain.Ranking
import com.radaroccaz.app.sources.SiteNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogTest {
    @Before fun load() = TestCatalog.ensureLoaded()

    private fun annotate(title: String, make: String, model: String, power: Int? = null, fuel: Fuel? = null): Listing =
        VehicleSpecs.annotate(car("x", 10_000, title = title, make = make, fuel = fuel).copy(model = model, powerHp = power), CarCatalog.spec(make, model))

    @Test
    fun catalogCoversTheMarket() {
        assertTrue(CarCatalog.makes.size >= 40)
        assertEquals("Peugeot", CarCatalog.makes.first())
        assertTrue(CarCatalog.suggestModels("Renault", "").size >= 40)
        // Models with a technical sheet come first.
        assertEquals("Twingo", CarCatalog.suggestModels("Renault", "").first())
        assertEquals("Mercedes-Benz", CarCatalog.canonicalMake("mercedes"))
        assertEquals("DS Automobiles", CarCatalog.canonicalMake("DS"))
        val s308 = CarCatalog.spec("peugeot", "308")!!
        assertEquals(listOf(BodyType.COMPACTE, BodyType.BREAK), s308.bodies)
        assertTrue(s308.engines.any { it.name == "1.5 BlueHDi 130" && it.fuel == Fuel.DIESEL && it.powerHp == 130 })
        assertEquals(listOf(Drive.TRACTION, Drive.INTEGRALE), CarCatalog.spec("Peugeot", "3008")!!.drives)
        assertTrue(CarCatalog.spec("Volkswagen", "Tiguan")!!.engines.any { it.allWheel })
        assertNull(CarCatalog.spec("Peugeot", "Inconnue"))
    }

    @Test
    fun enginesBodiesAndDrivesFromTitles() {
        val sw = annotate("Peugeot 308 SW 1.5 BlueHDi 130 EAT8 GT", "Peugeot", "308")
        assertEquals("1.5 BlueHDi 130", sw.engine)
        assertEquals(BodyType.BREAK, sw.body)
        assertEquals(Drive.TRACTION, sw.drive)

        val hatch = annotate("Peugeot 308 1.2 PureTech 130 Allure", "Peugeot", "308")
        assertEquals("1.2 PureTech 130", hatch.engine)
        assertEquals(BodyType.COMPACTE, hatch.body)

        val tiguan = annotate("Volkswagen Tiguan 2.0 TDI 150 4Motion DSG7 Carat", "Volkswagen", "Tiguan")
        assertEquals("2.0 TDI 150 4Motion", tiguan.engine)
        assertEquals(Drive.INTEGRALE, tiguan.drive)

        val bmw = annotate("BMW Série 3 320d xDrive 190 ch M Sport", "BMW", "Série 3")
        assertEquals("320d xDrive 190", bmw.engine)
        assertEquals(Drive.INTEGRALE, bmw.drive)

        val merc = annotate("Mercedes-Benz Classe A 180 d Progressive Line", "Mercedes-Benz", "Classe A", power = 116, fuel = Fuel.DIESEL)
        assertEquals("A 180 d 116", merc.engine)

        // Nothing to tell 110 and 130 apart: no engine rather than a wrong one.
        assertNull(annotate("Peugeot 308 PureTech Allure", "Peugeot", "308").engine)
        // Model sold only in two-wheel drive.
        assertEquals(Drive.TRACTION, annotate("Renault Clio V 1.0 TCe 90 Zen", "Renault", "Clio").drive)
    }

    @Test
    fun technicalFiltersInRanking() {
        val c = SearchCriteria(make = "Peugeot", model = "308", engines = setOf("1.5 BlueHDi 130"), bodies = setOf(BodyType.BREAK),
            priorities = Priorities())
        fun l(title: String, power: Int? = null, fuel: Fuel? = Fuel.DIESEL) =
            Ranking.annotate(car(title.hashCode().toString(), 12_000, title = title, fuel = fuel).copy(model = "308", powerHp = power), c)
        assertTrue(Ranking.matches(l("Peugeot 308 SW 1.5 BlueHDi 130 Allure"), c))
        assertFalse("hatchback", Ranking.matches(l("Peugeot 308 1.5 BlueHDi 130 Allure"), c))
        assertFalse("other engine", Ranking.matches(l("Peugeot 308 SW 1.2 PureTech 130 Allure", 130, Fuel.ESSENCE), c.copy(engines = setOf("1.5 BlueHDi 130"))))
        // Unknown engine, matching power: kept.
        assertTrue(Ranking.matches(l("Peugeot 308 SW Allure Business", 131), c))
        val awd = SearchCriteria(make = "Peugeot", model = "3008", drives = setOf(Drive.INTEGRALE))
        assertFalse(Ranking.matches(Ranking.annotate(car("a", 20_000, title = "Peugeot 3008 1.5 BlueHDi 130 GT").copy(model = "3008"), awd), awd))
        assertTrue(Ranking.matches(Ranking.annotate(car("b", 25_000, title = "Peugeot 3008 Hybrid4 300 GT").copy(model = "3008"), awd), awd))
        val power = SearchCriteria(make = "Peugeot", model = "308", powerMin = 120)
        assertFalse(Ranking.matches(car("p", 9_000).copy(powerHp = 110), power))
    }

    @Test
    fun urlCandidatesCoverEachSitesSpelling() {
        assertEquals(listOf("mercedes-benz", "mercedes"), SiteNames.makeSlugs("Mercedes-Benz"))
        assertEquals("mercedes", SiteNames.makeSlugs("Mercedes-Benz", mapOf("mercedes-benz" to "mercedes")).first())
        assertEquals("ds", SiteNames.makeSlugs("DS", mapOf("ds-automobiles" to "ds")).first())
        assertEquals(listOf("serie-3", "serie3"), SiteNames.modelSlugs("Série 3"))
        assertEquals("id-3", SiteNames.modelSlugs("ID.3").first())
        assertEquals("c3-aircross", SiteNames.modelSlugs("C3 Aircross").first())
        val urls = SiteNames.candidates(SearchCriteria(make = "Peugeot", model = "308")) { mk, m -> "/$mk/${m ?: ""}" }
        assertEquals("/peugeot/308", urls.first())
        assertEquals("/peugeot/", urls.last())
    }
}

class ModelAliasTest {
    @Test
    fun designationsCountAsTheModel() {
        val classeA = SearchCriteria(make = "Mercedes-Benz", model = "Classe A")
        assertTrue(Ranking.isRelevant(car("1", 15_000, title = "Mercedes-Benz A 180 AMG Line", make = "Mercedes-Benz").copy(model = "A 180"), classeA))
        assertTrue(Ranking.isRelevant(car("2", 15_000, title = "Mercedes A200d Progressive", make = null), classeA))
        assertFalse(Ranking.isRelevant(car("3", 15_000, title = "Mercedes CLA 200 Shooting Brake", make = "Mercedes").copy(model = "CLA 200"), classeA))
        val serie3 = SearchCriteria(make = "BMW", model = "Série 3")
        assertTrue(Ranking.isRelevant(car("4", 15_000, title = "BMW 320 d Pack", make = "BMW").copy(model = "320"), serie3))
        assertTrue(Ranking.isRelevant(car("5", 15_000, title = "BMW 318d Touring Luxury", make = "BMW"), serie3))
        assertFalse(Ranking.isRelevant(car("6", 15_000, title = "BMW X1 sDrive 18d 150 ch", make = "BMW").copy(model = "X1"), SearchCriteria(make = "BMW", model = "Série 1")))
        assertFalse(Ranking.isRelevant(car("7", 15_000, title = "BMW X3 xDrive30d 310 ch", make = "BMW"), serie3))
    }

    @Test
    fun leasesAndPlaceholderPricesAreNotSales() {
        val y = java.time.LocalDate.now().year
        assertTrue(Ranking.isFakePrice(car("a", 390, year = y - 1, title = "Mercedes A 180 AMG Line – 390-mois")))
        assertTrue(Ranking.isFakePrice(car("b", 1, year = 2017, title = "BMW M4 GT - prix sur demande")))
        assertTrue(Ranking.isFakePrice(car("c", 1_900, year = y - 1, title = "Peugeot 208 en LOA sans apport")))
        assertFalse(Ranking.isFakePrice(car("d", 1_900, year = 2006, title = "Peugeot 206 1.4 HDi")))
    }
}

class GearboxTextTest {
    @Test
    fun gearboxFromTitles() {
        listOf("308 1.5 BlueHDi 130 EAT8 Allure", "Classe A 180 7G-DCT", "Golf 2.0 TDI DSG7", "Clio E-Tech EDC", "Série 3 320d BVA8", "Yaris Hybrid e-CVT")
            .forEach { assertEquals(it, com.radaroccaz.app.data.Gearbox.AUTOMATIQUE, VehicleSpecs.detectGearbox(com.radaroccaz.app.data.Text.normalize(it).replace('-', ' '))) }
        assertEquals(com.radaroccaz.app.data.Gearbox.MANUELLE, VehicleSpecs.detectGearbox("308 1.5 bluehdi 130 bvm6 active"))
        assertNull(VehicleSpecs.detectGearbox("peugeot 308 allure"))
    }
}
