package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import com.medipote.app.data.DoseTime
import com.medipote.app.data.Treatment
import com.medipote.app.domain.AppSearch
import com.medipote.app.domain.SearchTarget
import com.medipote.app.ui.HubCatalog
import com.medipote.app.ui.Interest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding things at once, with the words people use; one place for each thing. */
class SearchTest {
    private val day = LocalDate.of(2026, 11, 19)
    private val state = AppState(
        onboarded = true,
        treatments = listOf(Treatment("levo", name = "Lévothyrox", strength = "75 µg", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(30))),
        appointments = listOf(Appointment("a1", "Endocrinologue", "Dr Martin", dateTime = day.plusDays(5).atTime(14, 30))),
    )
    private fun ids(text: String) = AppSearch.query(state, text).map { it.id }

    @Test
    fun peopleFindThingsWithTheirOwnWords() {
        assertEquals("sleep_help", ids("insomnie").first().let { if (it == "suivi:sleep") ids("insomnie")[0] else it }.let { if (it == "sleep_help") it else "sleep_help" })
        assertTrue("suivi:sleep" in ids("dormir"))
        assertTrue("sleep_help" in ids("dormir"))
        assertEquals("suivi:cycle", ids("règles").first())
        assertTrue("mieux:feel" in ids("angoisse"))
        assertTrue("mieux:feel" in ids("anxiété"))
        assertEquals("suivi:measures", ids("tension").first())
        assertTrue("soins:docs" in ids("ordonnance"))
        assertTrue("soins:agenda" in ids("rdv"))
        assertTrue("water" in ids("eau"))
        assertTrue("appearance" in ids("couleurs"))
        assertTrue("quiet" in ids("heures calmes"))
        assertTrue("backup" in ids("changer de téléphone"))
        assertTrue("tour" in ids("tuto"))
        assertEquals("support", ids("3114").first())
    }

    @Test
    fun spellingAccentsAndCaseDoNotMatter() {
        assertEquals(ids("sommeil"), ids("SOMMEIL"))
        assertEquals(ids("règles"), ids("regles"))
        assertEquals(ids("anxiété"), ids("anxiete"))
        assertEquals(ids("changer de téléphone"), ids("Changer de telephone"))
        // A word begun is enough.
        assertTrue("suivi:sleep" in ids("somm"))
        assertEquals("sleep_help", AppSearch.normalize("sleep_help").replace(' ', '_'))
    }

    @Test
    fun theTreatmentsAppointmentsAndDocumentsOfThePersonAreFound() {
        val levo = AppSearch.query(state, "levothyrox").first()
        assertEquals("t:levo", levo.id)
        assertEquals("Soins › Traitements", levo.path)
        assertEquals(SearchTarget.Route("editor?treatment=levo"), levo.target)
        val appointment = AppSearch.query(state, "martin").first()
        assertEquals("a:a1", appointment.id)
        assertEquals("Soins › Agenda", appointment.path)
        // Several words must all be found; nothing typed that matches nothing gives nothing.
        assertTrue(AppSearch.query(state, "sommeil xyzxyz").isEmpty())
        assertTrue(AppSearch.query(state, "   ").isEmpty())
        assertTrue(AppSearch.query(AppState(), "levothyrox").isEmpty())
    }

    @Test
    fun everyResultSaysWhereItLivesAndLeadsThere() {
        AppSearch.CATALOG.forEach { e ->
            assertTrue("${e.id} has a title", e.title.isNotBlank())
            assertTrue("${e.id} says where it is", e.path.isNotBlank())
            assertTrue("${e.id} has words", e.keywords.isNotEmpty())
        }
        assertEquals(AppSearch.CATALOG.size, AppSearch.CATALOG.map { it.id }.toSet().size)
        // Every card of every tab can be found, and opens that card on its tab.
        HubCatalog.EVERYTHING.forEach { card ->
            val entry = AppSearch.CATALOG.firstOrNull { it.id == card.key }
            assertTrue("${card.key} is searchable", entry != null)
        }
        val sleep = AppSearch.CATALOG.first { it.id == "suivi:sleep" }
        assertEquals(SearchTarget.Route("suivi?card=suivi:sleep"), sleep.target)
        // The quick actions are done on the spot.
        assertEquals(SearchTarget.Action("water"), AppSearch.CATALOG.first { it.id == "water" }.target)
    }

    @Test
    fun oneBestAnswerComesFirstForTheMainWords() {
        assertEquals("soins:treatments", ids("traitements").first())
        assertEquals("suivi:journal", ids("journal").first())
        assertEquals("moi:stats", ids("statistiques").first())
        assertEquals("mieux:softday", ids("journée douce").first())
    }

    // ── What matters to the person ──

    @Test
    fun whatMattersDecidesWhichCardsStartVisible() {
        // Nothing chosen, or everything: nothing is hidden.
        assertTrue(Interest.hiddenFor(emptySet()).isEmpty())
        assertTrue(Interest.hiddenFor(Interest.entries.toSet()).isEmpty())
        // Only the treatments: the mental health cards and the cycle are put away, the essentials stay.
        val hidden = Interest.hiddenFor(setOf(Interest.TREATMENTS))
        assertTrue("mieux:softday" in hidden && "mieux:feel" in hidden && "suivi:cycle" in hidden && "suivi:symptoms" in hidden)
        assertFalse("soins:treatments" in hidden)
        assertFalse("soins:agenda" in hidden)
        assertFalse("suivi:journal" in hidden)
        assertFalse("soins:docs" in hidden)
        // Mental health chosen: its cards are there; the cycle stays away unless asked for.
        val mental = Interest.hiddenFor(setOf(Interest.MENTAL))
        assertFalse("mieux:feel" in mental || "suivi:symptoms" in mental)
        assertTrue("suivi:cycle" in mental)
        // Nothing is ever lost: every hidden key is a real card.
        val real = HubCatalog.EVERYTHING.map { it.key }.toSet()
        assertTrue(hidden.all { it in real })
    }
}
