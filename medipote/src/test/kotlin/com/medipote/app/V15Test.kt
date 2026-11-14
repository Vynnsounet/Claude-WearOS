package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DoseTime
import com.medipote.app.data.GameState
import com.medipote.app.data.RitualSettings
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.Treatment
import com.medipote.app.domain.Badges
import com.medipote.app.domain.Bloom
import com.medipote.app.domain.Challenges
import com.medipote.app.domain.DailyQuestions
import com.medipote.app.domain.Game
import com.medipote.app.domain.Garden
import com.medipote.app.domain.Recaps
import com.medipote.app.domain.RitualKind
import com.medipote.app.domain.Rituals
import com.medipote.app.domain.Schedule
import com.medipote.app.domain.Shop
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Version 1.5: badges, challenges, joker, rituals, recap, garden, question of the day. */
class V15Test {

    private val day = LocalDate.of(2026, 11, 12) // Thursday
    private fun at(h: Int, m: Int = 0, date: LocalDate = day) = date.atTime(h, m)
    private val monday = LocalDate.of(2026, 11, 9)

    private fun night(date: LocalDate) = SleepNight(date, date.minusDays(1).atTime(23, 0), 15, date.atTime(7, 0))

    @Test
    fun badgesAreEarnedOnceAndUnlockTheirItem() {
        val nights = (0 until 7).map { night(day.minusDays(it.toLong())) }
        val state = AppState(sleep = nights, journal = listOf(DayEntry(day, mental = 4)))
        val (after, won) = Badges.award(state, day)
        val ids = won.map { it.id }.toSet()
        assertTrue(ids.toString(), ids.containsAll(setOf("sleep_1", "sleep_7", "journal_1")))
        assertFalse("sleep_30" in ids)
        assertEquals(day, after.game.badges["sleep_7"])
        // Its exclusive item is now owned; it can't be bought.
        assertTrue("fx_fireflies" in after.game.owned)
        assertNull(Shop.buy(GameState(points = 9999), Shop.item("fx_fireflies")!!))
        // Not twice.
        assertTrue(Badges.award(after, day).second.isEmpty())
        // A gap in the nights breaks the run of 7.
        assertEquals(3, Badges.sleepRun(AppState(sleep = (0 until 3).map { night(day.minusDays(it.toLong())) } + night(day.minusDays(5)))))
        assertEquals(6, Shop.items.count { it.badge != null })
        Shop.items.mapNotNull { it.badge }.forEach { assertTrue(it, Badges.byId(it) != null) }
    }

    @Test
    fun weeklyChallengesAreGentleAndRewardedOnce() {
        val t = Treatment("a", name = "A", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(30), createdAt = day.minusDays(30).atStartOfDay())
        val week = Challenges.of(AppState(treatments = listOf(t)), day)
        assertEquals(3, week.size)
        assertEquals(3, week.map { it.id }.toSet().size)
        assertEquals(week, Challenges.of(AppState(treatments = listOf(t)), monday.plusDays(6))) // same all week
        // Without regular treatments, no dose challenge.
        (0 until 20).forEach { w -> assertTrue(Challenges.of(AppState(), day.plusWeeks(w.toLong())).none { it.id == "complete4" }) }

        // Fill everything that could be asked this week.
        val days = (0 until 4).map { monday.plusDays(it.toLong()) }
        var state = AppState(
            treatments = listOf(t),
            sleep = (0 until 5).map { night(monday.plusDays(it.toLong())) },
            journal = days.map { DayEntry(it, mental = 4, reflection = "Une balade") },
            game = GameState(waterRewardDays = days.toSet(), cuddleDays = days.toSet(), ritualsDone = days.map { Rituals.key(it, RitualKind.MORNING) }.toSet()),
        )
        days.forEach { d ->
            val item = Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, d, at(8, 5, d)).single()
            state = Game.give(state, item, at(8, 5, d))!!.state
        }
        val mood = Challenges.status(state, day).firstOrNull { it.challenge.id == "mood5" }
        val (rewarded, won) = Challenges.reward(state, at(20))
        assertEquals(Challenges.status(state, day).count { it.complete }, won.size)
        if (mood != null) assertFalse(mood.complete) // 4 moods out of 5
        assertEquals(state.game.points + won.sumOf { it.points }, rewarded.game.points)
        assertTrue(Challenges.reward(rewarded, at(21)).second.isEmpty())
    }

    @Test
    fun theJokerForgivesOneMissedDayAWeek() {
        // Last opened Tuesday, not Wednesday, back on Thursday: the streak goes on.
        val game = GameState(streak = 10, bestStreak = 10, lastCheckIn = day.minusDays(2))
        val (after, reward) = Game.checkIn(game, at(8))!!
        assertEquals(11, reward.streak)
        assertTrue(reward.jokerUsed)
        assertEquals(day, after.jokerUsedOn)
        assertFalse(Game.jokerAvailable(after, day.plusDays(2))) // same week
        // A second missed day the same week: new start.
        val again = Game.checkIn(after.copy(lastCheckIn = day.plusDays(1)), at(8, 0, day.plusDays(3)))!!.second
        assertEquals(1, again.streak)
        assertEquals(11, again.previousStreak)
        // Available again the next week.
        assertTrue(Game.jokerAvailable(after, day.plusDays(4)))
        // Two missed days: the joker doesn't cover it.
        assertEquals(1, Game.checkIn(GameState(streak = 10, lastCheckIn = day.minusDays(3)), at(8))!!.second.streak)
    }

    @Test
    fun ritualsGiveAPointBonusOncePerDay() {
        val state = AppState()
        assertEquals(RitualKind.MORNING, Rituals.current(at(7)))
        assertEquals(RitualKind.EVENING, Rituals.current(at(21)))
        assertNull(Rituals.current(at(15)))
        // After midnight, the evening ritual still belongs to the day before.
        assertEquals(day.minusDays(1), Rituals.day(at(0, 30), RitualKind.EVENING))
        val (once, bonus) = Rituals.complete(state, RitualKind.EVENING, at(22))
        assertEquals(Rituals.BONUS, bonus)
        assertTrue(Rituals.done(once.game, at(23), RitualKind.EVENING))
        assertTrue(Rituals.done(once.game, at(0, 30, day.plusDays(1)), RitualKind.EVENING))
        assertEquals(0, Rituals.complete(once, RitualKind.EVENING, at(23)).second)
        assertEquals(Rituals.BONUS, Rituals.complete(once, RitualKind.MORNING, at(8, 0, day.plusDays(1))).second)
    }

    @Test
    fun ritualAndRecapReminders() {
        val settings = Settings(rituals = RitualSettings(morningReminder = true, morningMinute = 8 * 60, eveningReminder = true, eveningMinute = 21 * 60 + 30))
        val state = AppState(onboarded = true, settings = settings)
        val plan = ReminderPlanner.plan(state, at(7))
        assertEquals(
            listOf(at(8), at(21, 30), at(8, 0, day.plusDays(1)), at(21, 30, day.plusDays(1))),
            plan.filter { it.kind == AlarmKind.RITUAL }.map { it.time },
        )
        assertEquals(listOf(at(19, 0, LocalDate.of(2026, 11, 15))), plan.filter { it.kind == AlarmKind.RECAP }.map { it.time })
        val done = state.copy(game = GameState(ritualsDone = setOf(Rituals.key(day, RitualKind.MORNING))))
        assertFalse(ReminderPlanner.plan(done, at(7)).any { it.kind == AlarmKind.RITUAL && it.time == at(8) })
        assertTrue(ReminderPlanner.plan(AppState(onboarded = true), at(7)).none { it.kind == AlarmKind.RITUAL })
    }

    @Test
    fun weeklyRecap() {
        val t = Treatment("a", name = "A", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(30), createdAt = day.minusDays(30).atStartOfDay())
        var state = AppState(
            treatments = listOf(t),
            sleep = (0 until 4).map { night(monday.plusDays(it.toLong())) },
            journal = listOf(DayEntry(monday, mental = 3), DayEntry(monday.plusDays(2), mental = 5, reflection = "Super")),
        )
        (0 until 5).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val item = Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, d, at(8, 5, d)).single()
            state = Game.give(state, item, at(8, 5, d))!!.state
        }
        val sundayEvening = at(20, 0, monday.plusDays(6))
        assertEquals(monday, Recaps.weekToShow(sundayEvening))
        assertEquals(monday.minusWeeks(1), Recaps.weekToShow(at(10, 0, monday.plusDays(6))))
        val r = Recaps.build(state, monday, sundayEvening)
        assertEquals(7, r.dosesPlanned)
        assertEquals(5, r.dosesTaken)
        assertEquals(4, r.nights)
        assertEquals(monday.plusDays(2), r.bestDay)
        assertEquals(4f, r.moodAverage!!, 0.001f)
        assertEquals(monday, Recaps.pending(state, sundayEvening))
        assertNull(Recaps.pending(state.copy(game = state.game.copy(recapSeen = monday)), sundayEvening))
        assertNull(Recaps.pending(AppState(), sundayEvening)) // nothing used that week
    }

    @Test
    fun gardenAndQuestionOfTheDay() {
        val state = AppState(
            sleep = listOf(night(day), night(day.minusDays(1))),
            journal = listOf(DayEntry(day, mental = 4), DayEntry(day.minusDays(3), reflection = "Merci")),
        )
        val garden = Garden.of(state, day)
        assertEquals(28, garden.size)
        assertEquals(Bloom.BOTH, garden.last().second)
        assertEquals(Bloom.SLEEP, garden[26].second)
        assertEquals(Bloom.JOURNAL, garden[24].second)
        assertEquals(3, Garden.flowers(state, day))
        assertEquals(DailyQuestions.of(day), DailyQuestions.of(day))
        assertTrue(DailyQuestions.of(day) != DailyQuestions.of(day.plusDays(1)))
        assertTrue(DayEntry(day, reflection = "Une balade").hasJournal)
    }
}
