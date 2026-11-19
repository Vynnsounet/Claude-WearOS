package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.MyCondition
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.Wellbeing
import com.medipote.app.domain.WellbeingLibrary
import com.medipote.app.ui.HubCatalog
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Symptoms, hard days, small missions (points only ever added) and tasks cut into steps. */
class WellbeingTest {
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m)
    private fun on(vararg symptoms: String) = AppState(settings = Settings(wellbeing = WellbeingSettings(enabled = true, symptoms = symptoms.toList())))

    @Test
    fun symptomsAreNotedWithTheirStrengthAndCanBeRemoved() {
        var s = on("fatigue")
        s = Wellbeing.setIntensity(s, day, "fatigue", 3)
        assertEquals(3, Wellbeing.intensityOn(s, day, "fatigue"))
        s = Wellbeing.setIntensity(s, day, "fatigue", 5)
        assertEquals(1, s.symptomLog.size) // one note per symptom and day
        assertEquals(5, Wellbeing.intensityOn(s, day, "fatigue"))
        s = Wellbeing.setIntensity(s, day, "fatigue", null)
        assertNull(Wellbeing.intensityOn(s, day, "fatigue"))
        // Out-of-range values are brought back to 1..5.
        assertEquals(5, Wellbeing.intensityOn(Wellbeing.setIntensity(on("fatigue"), day, "fatigue", 9), day, "fatigue"))
    }

    @Test
    fun aHeavyDayIsAStrongSymptomOrVeryLittleEnergyAndOnlyWhenTurnedOn() {
        var s = on("anxiety")
        assertFalse(Wellbeing.heavyDay(s, day))
        s = Wellbeing.setIntensity(s, day, "anxiety", 3)
        assertFalse(Wellbeing.heavyDay(s, day))
        s = Wellbeing.setIntensity(s, day, "anxiety", 4)
        assertTrue(Wellbeing.heavyDay(s, day))
        assertFalse(Wellbeing.heavyDay(s, day.plusDays(1)))
        assertTrue(Wellbeing.heavyDay(Wellbeing.setEnergy(on(), day, 1), day))
        assertFalse(Wellbeing.heavyDay(Wellbeing.setEnergy(on(), day, 2), day))
        // Not turned on: nothing is offered.
        val off = Wellbeing.setIntensity(AppState(), day, "anxiety", 5)
        assertFalse(Wellbeing.heavyDay(off, day))
    }

    @Test
    fun aMissionPaysOnceAndPointsAreNeverTakenAway() {
        val mission = WellbeingLibrary.mission("tea")!!
        val start = on()
        val first = Wellbeing.missionWin(start, mission, at(10))
        assertEquals(Wellbeing.MISSION_POINTS, first.points)
        assertEquals(Wellbeing.MISSION_POINTS, first.state.game.points)
        // Done again the same day: nothing more, nothing less.
        val again = Wellbeing.missionWin(first.state, mission, at(11))
        assertTrue(again.already)
        assertEquals(0, again.points)
        assertEquals(first.state.game.points, again.state.game.points)
        // The next day it can be done again.
        assertEquals(Wellbeing.MISSION_POINTS, Wellbeing.missionWin(again.state, mission, at(9, d = day.plusDays(1))).points)
    }

    @Test
    fun theDayPaysForAFewWinsThenKeepsCelebratingWithoutMorePoints() {
        var s = on()
        val pays = WellbeingLibrary.MISSIONS.take(5).map { m ->
            val r = Wellbeing.missionWin(s, m, at(10))
            s = r.state
            r.points
        }
        assertEquals(listOf(5, 5, 5, 0, 0), pays)
        // All five are noted; only three paid.
        assertEquals(5, Wellbeing.winsOn(s, day).size)
        assertEquals(15, s.game.points)
        assertEquals(15, s.game.totalEarned)
    }

    @Test
    fun notDoingAnythingChangesNothing() {
        val s = Wellbeing.setIntensity(on("fatigue"), day, "fatigue", 5)
        // Suggested missions are only suggestions: no penalty, no record, points untouched.
        Wellbeing.suggest(s, day, 1)
        assertEquals(0, s.game.points)
        assertEquals(0, Wellbeing.winsOn(s, day).size)
        assertEquals(s.game.streak, s.game.streak)
    }

    @Test
    fun suggestionsFollowTheEnergyAndTheStrongSymptoms() {
        var s = on("anxiety", "pain")
        s = Wellbeing.setIntensity(s, day, "anxiety", 5)
        val veryLow = Wellbeing.suggest(s, day, 1)
        assertEquals(4, veryLow.size)
        assertTrue(veryLow.all { it.energy == 1 })
        // What suits the strong symptom comes first.
        assertTrue(veryLow.first().symptoms.contains("anxiety"))
        // More energy opens more ideas, but never an unrelated symptom's mission.
        assertTrue(Wellbeing.suggest(s, day, 3, count = 40).any { it.energy == 3 })
        assertTrue(Wellbeing.suggest(s, day, 3, count = 40).none { it.symptoms.isNotEmpty() && it.symptoms.none { sym -> sym in listOf("anxiety", "pain") } })
        // A mission done is not suggested again, and "other ideas" changes the list.
        val done = Wellbeing.missionWin(s, veryLow.first(), at(9)).state
        assertFalse(Wellbeing.suggest(done, day, 1).any { it.id == veryLow.first().id })
        assertTrue((0..5).map { Wellbeing.suggest(s, day, 3, shuffle = it).map { m -> m.id } }.toSet().size > 1)
    }

    @Test
    fun ownMissionsComeFromTheUsersList() {
        val s = AppState(settings = Settings(wellbeing = WellbeingSettings(enabled = true, customMissions = listOf("Prendre un bain"))))
        val mine = Wellbeing.custom(s).single()
        assertEquals("Prendre un bain", mine.text)
        val r = Wellbeing.missionWin(s, mine, at(10))
        assertEquals(Wellbeing.MISSION_POINTS, r.points)
        assertTrue(Wellbeing.done(r.state, day, mine.id))
    }

    @Test
    fun aTaskCutIntoStepsPaysMoreForTheFirstStep() {
        var s = Wellbeing.saveTask(on(), "t1", "Faire ma lessive", listOf("Ramasser le linge", "Lancer la machine", "Étendre", "  ", "Ramasser le linge"), at(8))
        val task = s.tasks.single()
        assertEquals(3, task.steps.size) // blanks and duplicates dropped
        val first = Wellbeing.completeStep(s, "t1", 0, at(9))
        assertEquals(Wellbeing.FIRST_STEP_POINTS, first.points)
        s = first.state
        val second = Wellbeing.completeStep(s, "t1", 1, at(9, 5))
        assertEquals(Wellbeing.STEP_POINTS, second.points)
        s = second.state
        // Ticking twice pays once.
        assertTrue(Wellbeing.completeStep(s, "t1", 1, at(9, 6)).already)
        assertEquals(1, Wellbeing.activeTasks(s).size)
        s = Wellbeing.completeStep(s, "t1", 2, at(9, 10)).state
        assertTrue(s.tasks.single().finished)
        assertEquals(0, Wellbeing.activeTasks(s).size)
        s = Wellbeing.deleteTask(s, "t1")
        assertTrue(s.tasks.isEmpty())
        // Points already won stay.
        assertEquals(Wellbeing.FIRST_STEP_POINTS + 2 * Wellbeing.STEP_POINTS, s.game.points)
    }

    @Test
    fun aTaskNeedsATitleAndAtLeastOneStep() {
        assertTrue(Wellbeing.saveTask(on(), "t", " ", listOf("a"), at(8)).tasks.isEmpty())
        assertTrue(Wellbeing.saveTask(on(), "t", "Titre", listOf(" "), at(8)).tasks.isEmpty())
        assertEquals(Wellbeing.MAX_STEPS, Wellbeing.saveTask(on(), "t", "Titre", (1..30).map { "étape $it" }, at(8)).tasks.single().steps.size)
    }

    @Test
    fun aSymptomStrongerAfterShortNightsIsNoticedOnlyWithEnoughDays() {
        fun night(d: LocalDate, hours: Int) = SleepNight(d, d.minusDays(1).atTime(23, 0), 0, d.minusDays(1).atTime(23, 0).plusHours(hours.toLong()))
        var s = on("fatigue")
        val days = (1..8).map { day.minusDays(it.toLong()) }
        days.forEachIndexed { i, d ->
            val short = i % 2 == 0
            s = s.copy(sleep = s.sleep + night(d, if (short) 5 else 8))
            s = Wellbeing.setIntensity(s, d, "fatigue", if (short) 4 else 2)
        }
        val found = Wellbeing.insights(s, day)
        assertEquals(1, found.size)
        assertTrue(found.single().contains("moins de 6 h"))
        assertTrue(found.single().contains("fatigue"))
        // Too few days on one side: nothing is claimed.
        val few = s.copy(symptomLog = s.symptomLog.take(4), sleep = s.sleep.take(4))
        assertTrue(Wellbeing.insights(few, day).isEmpty())
        // No difference: nothing either.
        val flat = days.fold(s) { acc, d -> Wellbeing.setIntensity(acc, d, "fatigue", 3) }
        assertTrue(Wellbeing.insights(flat, day).isEmpty())
    }

    @Test
    fun theWeekShowsTheLastSevenDays() {
        var s = on("pain")
        s = Wellbeing.setIntensity(s, day, "pain", 2)
        s = Wellbeing.setIntensity(s, day.minusDays(2), "pain", 4)
        val sum = Wellbeing.summary(s, "pain", day)
        assertEquals(listOf(null, null, null, null, 4, null, 2), sum.week)
        assertEquals(2, sum.days)
        assertEquals(1, sum.strongDays)
        assertEquals(3.0, sum.average, 0.001)
    }

    @Test
    fun aSymptomOverAPeriodComparesWithThePeriodBefore() {
        var s = on("fatigue")
        // This week (7 days): 4 days at 4 or 5. The week before: 3 days at 2.
        listOf(0 to 4, 1 to 5, 3 to 4, 5 to 4).forEach { (back, v) -> s = Wellbeing.setIntensity(s, day.minusDays(back.toLong()), "fatigue", v) }
        listOf(8, 9, 11).forEach { back -> s = Wellbeing.setIntensity(s, day.minusDays(back.toLong()), "fatigue", 2) }
        val p = Wellbeing.period(s, "fatigue", day, 7)
        assertEquals(4, p.noted)
        assertEquals(4.25, p.average!!, 0.001)
        assertEquals(4, p.strongDays)
        assertEquals(2.0, p.previousAverage!!, 0.001)
        assertEquals(7, p.bars.size)
        assertEquals(4f, p.bars.last(), 0.001f)
        assertEquals(0f, p.bars[2], 0.001f) // not noted: nothing, never counted as zero strength in the average
        // Beyond 30 days the bars are weeks.
        val q = Wellbeing.period(s, "fatigue", day, 90)
        assertEquals(13, q.bars.size)
        assertEquals(7, q.noted)
        // Too few days before: no comparison is claimed.
        assertNull(Wellbeing.period(on("fatigue"), "fatigue", day, 7).previousAverage)
    }

    @Test
    fun theSmallWinsOfAPeriodAreCountedWithoutGaps() {
        var s = on()
        s = Wellbeing.win(s, "tea", "Boisson chaude", 5, at(9)).state
        s = Wellbeing.win(s, "tea", "Boisson chaude", 5, at(9, d = day.minusDays(1))).state
        s = Wellbeing.win(s, "breath", "Respirer", 5, at(9, d = day.minusDays(2))).state
        s = Wellbeing.saveTask(s, "t", "Ma tâche", listOf("a", "b"), at(8))
        s = Wellbeing.completeStep(s, "t", 0, at(10)).state
        s = Wellbeing.completeStep(s, "t", 1, at(10, 5)).state
        val r = Wellbeing.winsReport(s, day, 7)
        assertEquals(5, r.wins)
        assertEquals(3, r.daysWithWins)
        assertEquals(2, r.stepsDone)
        assertEquals(1, r.tasksFinished)
        assertEquals("Boisson chaude" to 2, r.top.first())
        // A short period only sees its own days.
        assertEquals(3, Wellbeing.winsReport(s, day, 1).wins)
        assertEquals(0, Wellbeing.winsReport(s, day.minusDays(30), 7).wins)
    }

    @Test
    fun theLibraryIsConsistentAndSafe() {
        val symptomIds = WellbeingLibrary.SYMPTOMS.map { it.id }
        assertEquals(symptomIds.size, symptomIds.toSet().size)
        // Every symptom says when to see a doctor, and has gentle tips.
        WellbeingLibrary.SYMPTOMS.forEach {
            assertTrue("${it.id} has tips", it.tips.isNotEmpty())
            assertTrue("${it.id} says when to consult", it.consult.isNotEmpty())
        }
        // Every condition points at known symptoms and warns about what to watch.
        WellbeingLibrary.CONDITIONS.forEach { c ->
            assertTrue("${c.id} symptoms", c.symptoms.isNotEmpty() && c.symptoms.all { it in symptomIds })
            assertTrue("${c.id} watch", c.watch.isNotEmpty())
        }
        assertEquals(WellbeingLibrary.CONDITIONS.size, WellbeingLibrary.CONDITIONS.map { it.id }.toSet().size)
        WellbeingLibrary.MISSIONS.forEach { m -> assertTrue("${m.id} symptoms", m.symptoms.all { it in symptomIds }); assertTrue(m.energy in 1..3) }
        assertEquals(WellbeingLibrary.MISSIONS.size, WellbeingLibrary.MISSIONS.map { it.id }.toSet().size)
        // Whatever the energy, something gentle can be offered for each symptom.
        symptomIds.forEach { id ->
            val s = Wellbeing.setIntensity(on(id), day, id, 5)
            assertTrue("$id has a mission for very low energy", Wellbeing.suggest(s, day, 1).isNotEmpty())
        }
        // Nothing in the advice touches a dose.
        val text = (WellbeingLibrary.SYMPTOMS.flatMap { it.tips + it.consult } + WellbeingLibrary.CONDITIONS.flatMap { it.tips + it.watch }).joinToString(" ").lowercase()
        assertFalse(text.contains("augmente ta dose"))
        assertFalse(text.contains("arrête ton traitement"))
    }

    @Test
    fun theBodyImageAdviceNeverGivesADietOrAGoal() {
        val body = WellbeingLibrary.symptom("body")!!
        assertTrue(body.kind)
        val text = (body.tips + body.consult).joinToString(" ").lowercase()
        assertFalse(text.contains("kg"))
        assertFalse(text.contains("calories"))
        assertTrue(text.contains("régimes"))
    }

    @Test
    fun anOldSaveLoadsWithEverythingOff() {
        val state = AppRepository.JSON.decodeFromString<AppState>("""{"onboarded":true,"treatments":[]}""")
        assertFalse(state.settings.wellbeing.enabled)
        assertTrue(state.symptomLog.isEmpty() && state.wins.isEmpty() && state.tasks.isEmpty())
        assertNull(state.energy)
        assertTrue(state.settings.hiddenCards.isEmpty())
        // And what is saved comes back.
        val saved = Wellbeing.setIntensity(on("fatigue"), day, "fatigue", 3).copy(
            settings = Settings(wellbeing = WellbeingSettings(true, listOf(MyCondition("Hashimoto", "hashimoto")), listOf("fatigue"), listOf("Bain"))),
        )
        assertEquals(saved, AppRepository.JSON.decodeFromString<AppState>(AppRepository.JSON.encodeToString(saved)))
    }

    @Test
    fun theHubsShowSymptomsAndTheGentleDayOnlyWhenTurnedOn() {
        fun keys(state: AppState) = HubCatalog.cards(state).flatMap { it.second }.map { it.key }
        assertFalse("suivi:symptoms" in keys(AppState()))
        assertFalse("mieux:softday" in keys(AppState()))
        assertTrue("suivi:symptoms" in keys(on()))
        assertTrue("mieux:softday" in keys(on()))
        // The page for hard moments is always there, and so is the way to turn the rest on.
        assertTrue("mieux:feel" in keys(AppState()))
        // One path per thing: the help for bad moments is the heart, not a second card.
        assertFalse("mieux:support" in keys(AppState()))
        assertFalse("mieux:breathe" in keys(AppState()))
        assertFalse("moi:day" in keys(AppState()))
        assertTrue("mieux:me" in keys(AppState()))
    }
}
