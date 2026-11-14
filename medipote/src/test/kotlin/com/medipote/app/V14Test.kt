package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.GameState
import com.medipote.app.data.HomeSection
import com.medipote.app.data.Look
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Game
import com.medipote.app.domain.Rules
import com.medipote.app.domain.Schedule
import com.medipote.app.ui.home.forgottenDoses
import com.medipote.app.ui.home.nextMilestone
import com.medipote.app.ui.home.streakMessage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Version 1.4: forgotten doses, streak celebration, personalization. */
class V14Test {

    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0, date: LocalDate = day) = date.atTime(h, m)
    private val yesterday = day.minusDays(1)

    private val levo = Treatment(
        "levo", name = "Lévothyroxine", doses = listOf(DoseTime(8 * 60), DoseTime(20 * 60)), startDate = day.minusDays(30),
        createdAt = day.minusDays(30).atStartOfDay(), stock = 10.0,
    )

    private fun items(state: AppState, date: LocalDate) =
        Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, date, at(9))

    @Test
    fun aForgottenDoseCanBeNotedAfterwards() {
        var state = AppState(treatments = listOf(levo), onboarded = true)
        assertEquals(2, forgottenDoses(state, yesterday, at(9)).size)
        val morning = items(state, yesterday).first { it.occurrence.dose.minuteOfDay == 8 * 60 }
        assertEquals(DoseState.MISSED, morning.state)
        // Taken at 8:10 yesterday, noted today at 9:00: on time, full points.
        val r = Game.catchUp(state, morning, at(8, 10, yesterday), at(9))!!
        assertEquals(TakeStatus.ON_TIME, r.log.status)
        assertEquals(Rules.POINTS_ON_TIME, r.log.points)
        assertEquals(at(8, 10, yesterday), r.log.takenAt)
        assertTrue(r.state.game.history.first().reason.endsWith("(noté après coup)"))
        assertEquals(9.0, r.state.treatments.single().stock!!, 0.001)
        state = r.state
        assertEquals(DoseState.TAKEN, items(state, yesterday).first { it.occurrence.dose.minuteOfDay == 8 * 60 }.state)
        assertEquals(1, forgottenDoses(state, yesterday, at(9)).size)
        // Taken late: noted as late, like any late dose.
        val evening = items(state, yesterday).first { it.occurrence.dose.minuteOfDay == 20 * 60 }
        assertEquals(TakeStatus.LATE, Game.catchUp(state, evening, at(21, 30, yesterday), at(9))!!.log.status)
        // Not in the future, not twice, not too far back.
        assertNull(Game.catchUp(state, evening, at(10), at(9)))
        assertNull(Game.catchUp(state, items(state, yesterday).first { it.state == DoseState.TAKEN }, at(8, 10, yesterday), at(9)))
        val old = day.minusDays(Game.CATCH_UP_DAYS + 1)
        assertNull(Game.catchUp(state, items(state, old).first(), at(8, 0, old), at(9)))
    }

    @Test
    fun theCompanionFeelsBetterOnceTheForgottenDoseIsNoted() {
        var state = AppState(treatments = listOf(levo.copy(doses = listOf(DoseTime(8 * 60)))), onboarded = true)
        val before = Game.petCondition(state, at(9)).health
        state = Game.catchUp(state, items(state, yesterday).single(), at(8, 0, yesterday), at(9))!!.state
        assertTrue(Game.petCondition(state, at(9)).health > before)
    }

    @Test
    fun aDoseNotedByMistakeCanBeCancelled() {
        var state = AppState(treatments = listOf(levo), onboarded = true, game = GameState(points = 5))
        val item = items(state, day).first { it.canGive }
        state = Game.give(state, item, at(8, 5))!!.state
        assertEquals(15, state.game.points)
        assertEquals(9.0, state.treatments.single().stock!!, 0.001)
        state = Game.undo(state, item.occurrence.key, at(8, 6))
        assertTrue(state.doseLogs.isEmpty())
        assertEquals(5, state.game.points)
        assertEquals(10.0, state.treatments.single().stock!!, 0.001)
        assertTrue(state.game.history.first().reason.startsWith("Prise annulée"))
        assertEquals(DoseState.OVERDUE, items(state, day).first().state) // to give again (it is 9:00)
    }

    @Test
    fun streakCelebrationWords() {
        val first = Game.checkIn(GameState(), at(8))!!.second
        assertEquals("C'est parti !", streakMessage(first, "Pilou").first)
        // After a break: cheer the new start and remember the record, without blame.
        val broken = Game.checkIn(GameState(streak = 9, bestStreak = 15, lastCheckIn = day.minusDays(3)), at(8))!!.second
        assertEquals(1, broken.streak)
        assertEquals(9, broken.previousStreak)
        assertEquals(15, broken.best)
        val (title, text) = streakMessage(broken, "Pilou")
        assertEquals("Nouveau départ 💪", title)
        assertTrue(text, text.contains("9 jours") && text.contains("record : 15"))
        val week = Game.checkIn(GameState(streak = 6, lastCheckIn = yesterday), at(8))!!.second
        assertEquals(7, week.streak)
        assertTrue(streakMessage(week, "Pilou").first.startsWith("Palier"))
        assertEquals(Triple(7, 30, 4f / 7), nextMilestone(4))
        assertEquals(30, nextMilestone(7)!!.first)
        assertNull(nextMilestone(400))
    }

    @Test
    fun homeSectionsFollowTheUsersOrder() {
        assertEquals(HomeSection.entries, Look().visibleSections)
        val look = Look(
            homeOrder = listOf(HomeSection.WATER, HomeSection.DOSES, HomeSection.COMPANION),
            hiddenSections = setOf(HomeSection.APPOINTMENT),
        )
        // Sections missing from the saved order (added by an update) come at the end.
        assertEquals(
            listOf(HomeSection.WATER, HomeSection.DOSES, HomeSection.COMPANION, HomeSection.AS_NEEDED, HomeSection.MEASURES, HomeSection.PROMPTS),
            look.visibleSections,
        )
    }
}
