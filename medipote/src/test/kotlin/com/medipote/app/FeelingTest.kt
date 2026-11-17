package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.domain.FeelingGuide
import com.medipote.app.domain.Wellbeing
import com.medipote.app.domain.WellbeingLibrary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Ce que je ressens": anxiety, panic and low mood as they are lived in the body and in the head. */
class FeelingTest {
    private val day = LocalDate.of(2026, 11, 17)

    @Test
    fun theThreeFeelingsAreThereWithTheirSigns() {
        assertEquals(listOf("anxiety", "panic", "low"), FeelingGuide.FEELINGS.map { it.id })
        FeelingGuide.FEELINGS.forEach { f -> assertTrue("${f.id} has signs", FeelingGuide.signsOf(f.id).size >= 5) }
        // Exactly what was asked for is in the guide, in the person's own words.
        val labels = FeelingGuide.signsOf("low").map { it.label.lowercase() }
        assertTrue(labels.any { it.contains("poids dans la poitrine") })
        assertTrue(labels.any { it.contains("fatigue extrême") })
        assertTrue(labels.any { it.contains("mal à se lever") })
        assertTrue(labels.any { it.contains("envie de rien faire") })
    }

    @Test
    fun everySignHasThingsToTryAndKnowsItsSymptom() {
        val ids = FeelingGuide.SIGNS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        FeelingGuide.SIGNS.forEach { s ->
            assertTrue("${s.id} has three or four things to try", s.tryNow.size in 4..5)
            assertTrue("${s.id} why", s.why.length > 20)
            assertTrue("${s.id} feeling", FeelingGuide.feeling(s.feeling) != null)
            // The symptom it can be noted as exists in the tracking.
            s.symptom?.let { assertTrue("${s.id} symptom $it", WellbeingLibrary.symptom(it) != null) }
        }
        FeelingGuide.FEELINGS.forEach { assertTrue("${it.id} says when to talk to someone", it.consult.isNotEmpty()) }
    }

    @Test
    fun chestSignsAlwaysCarryTheRemindToCallAndNothingDiagnoses() {
        // Every sign about the chest, the heart or the breath is marked: the screen then shows the 15.
        listOf("chest_tight", "heart_fast", "short_breath", "wave", "choke", "heavy_chest").forEach {
            assertTrue("$it is a chest sign", FeelingGuide.sign(it)!!.chest)
        }
        assertTrue(FeelingGuide.CHEST_ALERT.contains("15"))
        // Dark thoughts lead to the 3114, and the low-mood advice mentions it too.
        assertTrue(FeelingGuide.sign("dark")!!.urgent)
        assertTrue(FeelingGuide.sign("dark")!!.tryNow.first().contains("3114"))
        assertTrue(FeelingGuide.feeling("low")!!.consult.any { it.contains("3114") })
        // A cause that can be physical is named for the signs it can come from (the thyroid, for one).
        assertTrue(FeelingGuide.feeling("low")!!.consult.any { it.contains("thyroïde") })
        assertTrue(FeelingGuide.feeling("anxiety")!!.consult.any { it.contains("thyroïde") })
        // Never a diagnosis, never a dose.
        val text = (FeelingGuide.SIGNS.flatMap { it.tryNow + it.why } + FeelingGuide.FEELINGS.flatMap { it.consult + it.intro }).joinToString(" ").lowercase()
        assertFalse(text.contains("tu as une dépression"))
        assertFalse(text.contains("tu fais une"))
        assertFalse(text.contains("dose"))
        assertFalse(text.contains("médicament"))
    }

    @Test
    fun theChosenSignsGiveTheirFeelingsAndSymptomsWithoutDoubles() {
        val chosen = listOf("heavy_chest", "cant_get_up", "no_desire", "wave")
        assertEquals(listOf("panic", "low"), FeelingGuide.feelingsOf(chosen).map { it.id })
        assertEquals(listOf("lowmood", "anxiety"), FeelingGuide.symptomsOf(chosen))
        assertTrue(FeelingGuide.feelingsOf(emptyList()).isEmpty())
        assertTrue(FeelingGuide.symptomsOf(listOf("appetite")).isEmpty())
    }

    @Test
    fun notingWhatWasFeltTurnsTheTrackingOnAndKeepsItForTheDoctor() {
        val start = AppState()
        assertFalse(Wellbeing.enabled(start))
        val noted = Wellbeing.noteFelt(start, day, listOf("lowmood", "fatigue", "lowmood"), 4)
        assertTrue(Wellbeing.enabled(noted))
        assertEquals(listOf("lowmood", "fatigue"), Wellbeing.tracked(noted))
        assertEquals(4, Wellbeing.intensityOn(noted, day, "lowmood"))
        assertEquals(4, Wellbeing.intensityOn(noted, day, "fatigue"))
        // A strong feeling makes it a heavy day, so the gentle day is offered.
        assertTrue(Wellbeing.heavyDay(noted, day))
        // Nothing to note: nothing changes. Already followed: no double.
        assertEquals(start, Wellbeing.noteFelt(start, day, emptyList(), 3))
        assertEquals(listOf("lowmood", "fatigue"), Wellbeing.tracked(Wellbeing.noteFelt(noted, day, listOf("fatigue"), 2)))
    }

    @Test
    fun tryingSomethingForASignPaysOncePerDayAndNeverTakesAnythingAway() {
        val now = day.atTime(10, 0)
        val start = AppState()
        val first = Wellbeing.tried(start, "feel", "heavy_chest", "J'ai essayé : poids dans la poitrine", now)
        assertEquals(Wellbeing.MISSION_POINTS, first.points)
        assertEquals(setOf("heavy_chest"), Wellbeing.triedToday(first.state, day, "feel"))
        // Again, the same day: nothing more, nothing less.
        val again = Wellbeing.tried(first.state, "feel", "heavy_chest", "x", now.plusHours(1))
        assertTrue(again.already)
        assertEquals(first.state.game.points, again.state.game.points)
        // A tip tried for a symptom is another thing, and the daily points are shared with the missions.
        var s = first.state
        listOf("a" to "feel", "b" to "feel", "c" to "advice").forEach { (id, kind) -> s = Wellbeing.tried(s, kind, id, "t", now).state }
        assertEquals(3 * Wellbeing.MISSION_POINTS, s.game.points) // the fourth win of the day pays nothing
        assertEquals(4, Wellbeing.winsOn(s, day).size)
        // Not trying costs nothing: no record of it, anywhere.
        assertTrue(Wellbeing.triedToday(start, day, "feel").isEmpty())
        // The next day it can be tried again.
        assertEquals(Wellbeing.MISSION_POINTS, Wellbeing.tried(s, "feel", "heavy_chest", "t", now.plusDays(1)).points)
    }

    @Test
    fun theKnottedThroatLivesWithTheFeelingsAndKeepsAllItsTips() {
        val lump = FeelingGuide.sign("lump")!!
        assertEquals("low", lump.feeling)
        val text = lump.tryNow.joinToString(" ")
        // What the old card said: jaw, shoulders, sips of water, a hand on the throat.
        assertTrue(text.contains("mâchoire") && text.contains("épaules"))
        assertTrue(text.contains("petites gorgées d'eau"))
        assertTrue(text.contains("main sur ta gorge"))
    }

    @Test
    fun everySymptomHasAtLeastFourTipsToEaseIt() {
        WellbeingLibrary.SYMPTOMS.forEach { assertTrue("${it.id} tips", it.tips.size >= 4) }
    }
}
