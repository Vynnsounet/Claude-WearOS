package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.Vaccination
import com.medipote.app.domain.AppSearch
import com.medipote.app.domain.VaccineStatus
import com.medipote.app.domain.Vaccines
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import com.medipote.app.ui.HubCatalog
import com.medipote.app.ui.HubPreviews
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.38: the vaccine calendar. */
class V38Test {
    private val nov = LocalDate.of(2026, 11, 20)

    private fun person(birth: Int?, risk: Boolean = false, age: Int? = null, doses: List<Pair<String, LocalDate>> = emptyList(), reminders: Boolean = true) = AppState(
        onboarded = true, profile = Profile("Alex", age = age, birthYear = birth, vaccineRisk = risk),
        vaccinations = doses.mapIndexed { i, (v, d) -> Vaccination("v$i", v, d) }, settings = Settings(vaccineReminders = reminders),
    )

    private fun card(s: AppState, id: String, today: LocalDate = nov) = Vaccines.cards(s, today).firstOrNull { it.id == id }

    @Test
    fun theAgeComesFromTheBirthYearOrFromTheProfileAge() {
        assertEquals(31, Vaccines.age(person(1995), nov))
        assertEquals(40, Vaccines.age(person(null, age = 40), nov))
        assertNull(Vaccines.age(person(null), nov))
        assertTrue("no age: nothing to say", Vaccines.cards(person(null), nov).isEmpty())
        assertTrue("a child is not in this calendar", Vaccines.cards(person(2020), nov).isEmpty())
    }

    // ───────────── Tetanus ─────────────
    @Test
    fun tetanusBoostersAt25_45_65AndThenEveryTenYears() {
        assertEquals(VaccineStatus.CHECK, card(person(1996), "dtp")!!.status) // 30, never noted
        val young = card(person(2004), "dtp")!! // 22
        assertEquals(VaccineStatus.SOON, young.status); assertTrue(young.headline, young.headline.contains("25 ans (en 2029)"))
        assertEquals(VaccineStatus.OK, card(person(1995, doses = listOf("dtp" to LocalDate.of(2020, 5, 1))), "dtp")!!.status)
        val late = card(person(1970, doses = listOf("dtp" to LocalDate.of(2000, 1, 1))), "dtp")!!
        assertEquals(VaccineStatus.DUE, late.status); assertTrue(late.headline, late.headline.contains("45 ans (en 2015)"))
        assertEquals(VaccineStatus.SOON, card(person(1962, doses = listOf("dtp" to LocalDate.of(2010, 1, 1))), "dtp")!!.status) // 65 in 2027
        assertEquals(VaccineStatus.DUE, card(person(1961, doses = listOf("dtp" to LocalDate.of(2010, 1, 1))), "dtp")!!.status) // 65 this year
        assertEquals(VaccineStatus.OK, card(person(1960, doses = listOf("dtp" to LocalDate.of(2025, 3, 1))), "dtp")!!.status) // next at 75
    }

    // ───────────── Seasonal ─────────────
    @Test
    fun fluAndCovidAreForThe65AndOverAndThoseAtRisk() {
        assertNull(card(person(1985), "flu")); assertNull(card(person(1985), "covid"))
        assertNotNull(card(person(1985, risk = true), "flu")); assertNotNull(card(person(1950), "covid"))
    }

    @Test
    fun theSeasonDecidesWhatIsDue() {
        val old = person(1950)
        assertEquals(VaccineStatus.DUE, card(old, "flu", nov)!!.status)
        assertEquals(VaccineStatus.DUE, card(old, "flu", LocalDate.of(2026, 1, 15))!!.status)
        assertEquals(VaccineStatus.OK, card(old, "flu", LocalDate.of(2026, 7, 1))!!.status)
        assertEquals(VaccineStatus.SOON, card(old, "flu", LocalDate.of(2026, 9, 10))!!.status)
        val done = person(1950, doses = listOf("flu" to LocalDate.of(2026, 10, 20)))
        assertEquals(VaccineStatus.OK, card(done, "flu", nov)!!.status)
        assertEquals("last autumn's is not this autumn's", VaccineStatus.DUE, card(person(1950, doses = listOf("flu" to LocalDate.of(2025, 11, 2))), "flu", nov)!!.status)
        assertEquals("seen from January, the dose of the autumn still counts", VaccineStatus.OK, card(person(1950, doses = listOf("flu" to LocalDate.of(2025, 11, 2))), "flu", LocalDate.of(2026, 1, 20))!!.status)
    }

    // ───────────── The others ─────────────
    @Test
    fun pneumococcusAndShingles() {
        val s = person(1955)
        assertEquals(VaccineStatus.DUE, card(s, "pneumo")!!.status)
        assertEquals(VaccineStatus.OK, card(person(1955, doses = listOf("pneumo" to nov.minusYears(1))), "pneumo")!!.status)
        assertEquals(VaccineStatus.DUE, card(s, "zona")!!.status)
        val first = card(person(1955, doses = listOf("zona" to nov.minusDays(10))), "zona")!!
        assertEquals(VaccineStatus.SOON, first.status); assertTrue(first.headline, first.headline.contains("2e dose"))
        assertEquals(VaccineStatus.DUE, card(person(1955, doses = listOf("zona" to nov.minusMonths(3))), "zona")!!.status)
        assertEquals(VaccineStatus.OK, card(person(1955, doses = listOf("zona" to nov.minusMonths(5), "zona" to nov.minusMonths(1))), "zona")!!.status)
    }

    @Test
    fun measlesAndHpvDependOnTheBirthYearAndAge() {
        assertEquals(VaccineStatus.CHECK, card(person(1990), "ror")!!.status)
        assertEquals(VaccineStatus.OK, card(person(1990, doses = listOf("ror" to LocalDate.of(1992, 1, 1), "ror" to LocalDate.of(1996, 1, 1))), "ror")!!.status)
        assertNull("born before 1980: not in the recommendation", card(person(1975), "ror"))
        assertEquals(VaccineStatus.DUE, card(person(2013), "hpv")!!.status)
        assertNull(card(person(1990), "hpv"))
        assertEquals(VaccineStatus.OK, card(person(2013, doses = listOf("hpv" to nov.minusMonths(8), "hpv" to nov.minusMonths(1))), "hpv")!!.status)
    }

    @Test
    fun whatIsDueComesFirst() {
        val s = person(1950, doses = listOf("dtp" to LocalDate.of(2025, 1, 1)))
        val order = Vaccines.cards(s, nov).map { it.status }
        assertEquals(order.sortedBy { listOf(VaccineStatus.DUE, VaccineStatus.CHECK, VaccineStatus.SOON, VaccineStatus.OK).indexOf(it) }, order)
        assertTrue(Vaccines.pending(s, nov).all { it.status == VaccineStatus.DUE })
        assertTrue("reminders off: nothing on the home page", Vaccines.pending(person(1950, reminders = false), nov).isEmpty())
    }

    // ───────────── Keeping the record ─────────────
    @Test
    fun aVaccineIsNotedAndRemoved() {
        var s = person(1990)
        s = Vaccines.add(s, "flu", nov, "  pharmacie  ", "a")
        s = Vaccines.add(s, "Hépatite A", nov.minusDays(40), "voyage", "b")
        assertEquals(listOf("flu", "Hépatite A"), s.vaccinations.map { it.vaccine })
        assertEquals("pharmacie", s.vaccinations.first().note)
        assertEquals(s, Vaccines.add(s, "   ", nov, "", "c"))
        assertEquals(listOf("Hépatite A"), Vaccines.remove(s, "a").vaccinations.map { it.vaccine })
        assertEquals("Grippe", Vaccines.name("flu")); assertEquals("Hépatite A", Vaccines.name("Hépatite A"))
    }

    @Test
    fun theBirthYearAndRiskAreKept() {
        val s = Vaccines.setProfile(person(null, age = 30), 1996, true)
        assertEquals(1996, s.profile!!.birthYear); assertTrue(s.profile!!.vaccineRisk)
        assertNull(Vaccines.setProfile(s, 1700, false).profile!!.birthYear)
        assertEquals(AppState(), Vaccines.setProfile(AppState(), 1990, false))
    }

    // ───────────── Reminders ─────────────
    @Test
    fun aSoftReminderOnThe15thOfOctoberForTheSeasonalOnes() {
        val s = person(1950)
        val planned = ReminderPlanner.plan(s, LocalDate.of(2026, 9, 20).atTime(9, 0)).filter { it.kind == AlarmKind.VACCINE }
        assertEquals(listOf("covid", "flu"), planned.map { it.ref }.sorted())
        assertTrue(planned.all { it.time == LocalDate.of(2026, 10, 15).atTime(10, 0) })
        assertTrue("already done this season: no reminder", ReminderPlanner.plan(person(1950, doses = listOf("flu" to LocalDate.of(2026, 9, 25), "covid" to LocalDate.of(2026, 9, 25))), LocalDate.of(2026, 10, 1).atTime(9, 0)).none { it.kind == AlarmKind.VACCINE && it.time.year == 2026 })
        assertTrue(ReminderPlanner.plan(person(1950, reminders = false), LocalDate.of(2026, 9, 20).atTime(9, 0)).none { it.kind == AlarmKind.VACCINE })
        assertTrue("nothing for someone who is not concerned", ReminderPlanner.plan(person(1990), LocalDate.of(2026, 9, 20).atTime(9, 0)).none { it.kind == AlarmKind.VACCINE })
    }

    // ───────────── Where it lives ─────────────
    @Test
    fun oneCardOfTheSoinsPage_findable_andOldSavesLoad() {
        assertTrue(HubCatalog.EVERYTHING.any { it.key == "soins:vaccines" })
        assertTrue(AppSearch.CATALOG.any { it.id == "soins:vaccines" })
        assertEquals("Ton calendrier selon ton âge", HubPreviews.vaccines(person(null), nov))
        assertEquals("À faire : grippe, covid-19, pneumocoque, zona", HubPreviews.vaccines(person(1950, doses = listOf("dtp" to LocalDate.of(2025, 1, 1))), nov).let { it })
        val s = AppRepository.JSON.decodeFromString(AppState.serializer(), """{"onboarded":true,"profile":{"name":"A"}}""")
        assertTrue(s.vaccinations.isEmpty()); assertNull(s.profile!!.birthYear); assertFalse(s.profile!!.vaccineRisk); assertTrue(s.settings.vaccineReminders)
    }
}
