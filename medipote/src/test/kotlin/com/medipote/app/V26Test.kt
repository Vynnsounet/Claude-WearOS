package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.HomeSection
import com.medipote.app.data.Look
import com.medipote.app.data.Settings
import com.medipote.app.data.UsageStats
import com.medipote.app.domain.AppSearch
import com.medipote.app.domain.CardUse
import com.medipote.app.domain.HomeMoment
import com.medipote.app.domain.Moment
import com.medipote.app.domain.Sober
import com.medipote.app.ui.HubCatalog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.26: home by the hour, pinned shortcuts, tidying by use, sober mode. */
class V26Test {
    private val day = LocalDate.of(2026, 11, 19)

    // ── Home « du moment » ──
    @Test
    fun theDayIsCutInThree() {
        assertEquals(Moment.MORNING, Moment.of(7))
        assertEquals(Moment.DAY, Moment.of(14))
        assertEquals(Moment.EVENING, Moment.of(21))
        assertEquals(Moment.EVENING, Moment.of(2))
    }

    @Test
    fun eachMomentPutsForwardWhatSuitsIt() {
        val morning = day.atTime(8, 0)
        val afternoon = day.atTime(15, 0)
        val evening = day.atTime(21, 0)
        // The doses, the companion and the pinned cards are always in front.
        listOf(morning, afternoon, evening).forEach { t ->
            listOf(HomeSection.DOSES, HomeSection.COMPANION, HomeSection.NEXT_DOSE, HomeSection.SHORTCUTS).forEach { assertTrue("$it at $t", HomeMoment.inFront(it, t)) }
        }
        assertTrue(HomeMoment.inFront(HomeSection.RITUAL, morning))
        assertTrue(HomeMoment.inFront(HomeSection.PROMPTS, morning))
        assertFalse(HomeMoment.inFront(HomeSection.WATER, morning))
        assertTrue(HomeMoment.inFront(HomeSection.WATER, afternoon))
        assertFalse(HomeMoment.inFront(HomeSection.RITUAL, afternoon))
        assertTrue(HomeMoment.inFront(HomeSection.RITUAL, evening))
        assertFalse(HomeMoment.inFront(HomeSection.MEASURES, evening))
    }

    @Test
    fun theRestWaitsAndIsNeverLost() {
        val all = Look().visibleSections
        val (front, later) = HomeMoment.split(all, day.atTime(15, 0))
        assertEquals(all.toSet(), (front + later).toSet())
        assertTrue(later.isNotEmpty())
        // The order of the person is kept inside each part.
        assertEquals(all.filter { it in front }, front)
    }

    @Test
    fun theShortcutsBlockComesAfterTheDosesAndOldSavesGetIt() {
        val order = Look().fullOrder
        assertEquals(HomeSection.DOSES, order[order.indexOf(HomeSection.SHORTCUTS) - 1])
        // A save made before 1.26 does not know the block: it appears, in its default place.
        val old = Look(homeOrder = HomeSection.entries.filter { it != HomeSection.SHORTCUTS })
        assertTrue(HomeSection.SHORTCUTS in old.fullOrder)
        assertTrue(old.isComplete)
        // « Accueil simple » keeps what the person pinned.
        assertTrue(HomeSection.SHORTCUTS !in Look().simple().hiddenSections)
    }

    // ── Pins ──
    @Test
    fun fourPinsAtMost_noDuplicates() {
        var pins = emptyList<String>()
        listOf("a", "b", "c", "d", "e", "a").forEach { pins = CardUse.pin(pins, it) }
        assertEquals(listOf("a", "b", "c", "d"), pins)
        assertEquals(listOf("b", "c", "d"), CardUse.unpin(pins, "a"))
    }

    @Test
    fun everyHubCardCanBePinnedAndOpenedFromHome() {
        // A pin is a card key: the search knows where each of them leads.
        HubCatalog.EVERYTHING.forEach { card -> assertTrue(card.key, AppSearch.CATALOG.any { it.id == card.key }) }
    }

    // ── Use ──
    @Test
    fun usageIsCountedPerCardAndPerDay() {
        var u = UsageStats()
        u = CardUse.record(u, "soins:agenda", day)
        u = CardUse.record(u, "soins:agenda", day)
        u = CardUse.record(u, "suivi:sleep", day.plusDays(1))
        assertEquals(2, u.opens["soins:agenda"])
        assertEquals(1, u.opens["suivi:sleep"])
        assertEquals(2, u.days)
    }

    @Test
    fun nothingChangesBeforeTwoWeeks() {
        val young = UsageStats(opens = mapOf("b" to 9), days = CardUse.MIN_DAYS - 1)
        assertEquals(listOf("a", "b"), CardUse.ordered(young, listOf("a", "b")))
        assertNull(CardUse.suggestion(young, listOf("a", "b")))
    }

    @Test
    fun afterTwoWeeksTheMostOpenedComeFirst_andTheOffNeverMovesIt() {
        val u = UsageStats(opens = mapOf("b" to 9, "c" to 3), days = CardUse.MIN_DAYS)
        assertEquals(listOf("b", "c", "a"), CardUse.ordered(u, listOf("a", "b", "c")))
        assertEquals(listOf("a", "b", "c"), CardUse.ordered(u, listOf("a", "b", "c"), enabled = false))
    }

    @Test
    fun aNeverOpenedCardIsOfferedOnceItsOwnerDecides() {
        val u = UsageStats(opens = mapOf("soins:treatments" to 30), days = CardUse.MIN_DAYS)
        // The essentials are never offered.
        assertNull(CardUse.suggestion(u, listOf("soins:treatments", "soins:agenda", "suivi:journal")))
        assertEquals("soins:pill", CardUse.suggestion(u, listOf("soins:treatments", "soins:pill", "suivi:sleep")))
        // « Garder » : not offered again; the next unused one comes instead.
        val kept = CardUse.keep(u, "soins:pill")
        assertEquals("suivi:sleep", CardUse.suggestion(kept, listOf("soins:treatments", "soins:pill", "suivi:sleep")))
        // An opened card is not offered.
        val opened = u.copy(opens = u.opens + ("soins:pill" to 1))
        assertEquals("suivi:sleep", CardUse.suggestion(opened, listOf("soins:pill", "suivi:sleep")))
    }

    @Test
    fun anOldSaveLoadsWithTheNewSettings() {
        val json = """{"onboarded":true,"settings":{"themeMode":"DARK","hiddenCards":["suivi:sleep"]}}"""
        val state = AppRepository.JSON.decodeFromString(AppState.serializer(), json)
        assertEquals(emptyList<String>(), state.settings.pins)
        assertEquals(0, state.settings.usage.days)
        assertTrue(state.settings.cardsByUse)
        assertFalse(state.settings.sober)
        assertFalse(state.settings.look.moment)
        // And the new ones come back as they were saved.
        val saved = state.copy(settings = state.settings.copy(pins = listOf("soins:agenda"), sober = true, usage = CardUse.record(UsageStats(), "x", day)))
        val back = AppRepository.JSON.decodeFromString(AppState.serializer(), AppRepository.JSON.encodeToString(AppState.serializer(), saved))
        assertEquals(saved.settings, back.settings)
    }

    // ── Sober mode ──
    @Test
    fun soberMessagesLoseTheirPointsAndNothingElse() {
        assertEquals("Journal enregistré", Sober.clean("Journal enregistré : +5 pts"))
        assertEquals("Prise notée · Pilou va mieux 💚", Sober.clean("Prise notée : +10 pts · Pilou va mieux 💚"))
        assertEquals("Objectif d'hydratation atteint 💧", Sober.clean("Objectif d'hydratation atteint : +5 pts bonus 💧"))
        assertEquals("Une petite victoire 💛", Sober.clean("Une petite victoire : +3 pts 💛"))
        assertEquals("🎯 Défi réussi : boire de l'eau", Sober.clean("🎯 Défi réussi : boire de l'eau · +20 pts"))
        // Nothing left to say: nothing shown.
        assertNull(Sober.clean("+5 pts 💛"))
        // Without points, the text is untouched.
        assertEquals("Nuit enregistrée", Sober.clean("Nuit enregistrée"))
        assertEquals("Raccourci retiré de l'accueil", Sober.clean("Raccourci retiré de l'accueil"))
    }

    @Test
    fun soberModePutsTheShopAndBadgesAway_andOnlyThat() {
        val normal = HubCatalog.hidden(Settings(hiddenCards = setOf("suivi:sleep")))
        assertEquals(setOf("suivi:sleep"), normal)
        val sober = HubCatalog.hidden(Settings(hiddenCards = setOf("suivi:sleep"), sober = true))
        assertEquals(setOf("suivi:sleep", "moi:shop", "moi:badges"), sober)
        // Still found with the search: nothing is lost.
        assertTrue(AppSearch.query(AppState(), "boutique").any { it.id == "moi:shop" })
        assertTrue(AppSearch.query(AppState(), "sobre").any { it.id == "sober" })
    }

    @Test
    fun theSettingsSearchLeadsToTheRightGroup() {
        val quiet = AppSearch.CATALOG.first { it.id == "quiet" }.target as com.medipote.app.domain.SearchTarget.Route
        assertEquals("settings?section=reminders", quiet.route)
        val sober = AppSearch.CATALOG.first { it.id == "sober" }.target as com.medipote.app.domain.SearchTarget.Route
        assertEquals("settings?section=game", sober.route)
    }
}
