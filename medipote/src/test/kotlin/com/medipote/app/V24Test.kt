package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import com.medipote.app.data.DayEntry
import com.medipote.app.data.GameState
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepSettings
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.Care
import com.medipote.app.domain.Challenges
import com.medipote.app.domain.Shop
import com.medipote.app.domain.SleepHelp
import com.medipote.app.domain.SleepTool
import com.medipote.app.domain.SleepTrouble
import com.medipote.app.domain.Wellbeing
import com.medipote.app.domain.Wishes
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.24: small rewards for taking care, the companion's wish, the help of hard nights. */
class V24Test {
    private val day = LocalDate.of(2026, 11, 18) // a Wednesday
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m)
    private val start = AppState(onboarded = true)

    // ── Points for taking care ──

    @Test
    fun eachCareRewardIsGivenOnceAndNeverTakenAway() {
        var s = start
        val measure = Care.measureNoted(s, at(9)); s = measure.state
        assertEquals(Care.MEASURE_POINTS, measure.points)
        // A second measure the same day pays nothing; the next day it pays again.
        assertEquals(0, Care.measureNoted(s, at(18)).points)
        assertEquals(Care.MEASURE_POINTS, Care.measureNoted(s, at(9, d = day.plusDays(1))).points)
        val symptom = Care.symptomNoted(s, at(10)); s = symptom.state
        assertEquals(Care.SYMPTOM_POINTS, symptom.points)
        assertEquals(0, Care.symptomNoted(s, at(11)).points)
        val prep = Care.appointmentPrepared(s, "a1", at(12)); s = prep.state
        assertEquals(Care.PREP_POINTS, prep.points)
        assertEquals(0, Care.appointmentPrepared(s, "a1", at(13)).points)
        assertEquals(Care.PREP_POINTS, Care.appointmentPrepared(s, "a2", at(13)).points)
        val went = Care.appointmentAttended(s, "a1", at(15)); s = went.state
        assertEquals(Care.ATTENDED_POINTS, went.points)
        assertEquals(0, Care.appointmentAttended(s, "a1", at(16)).points)
        val restock = Care.restocked(s, "levo", at(17)); s = restock.state
        assertEquals(Care.RESTOCK_POINTS, restock.points)
        assertEquals(0, Care.restocked(s, "levo", at(18)).points)
        // Everything added, nothing subtracted.
        assertEquals(Care.MEASURE_POINTS + Care.SYMPTOM_POINTS + Care.PREP_POINTS + Care.ATTENDED_POINTS + Care.RESTOCK_POINTS, s.game.points)
        assertEquals(s.game.points, s.game.totalEarned)
    }

    @Test
    fun theSurpriseGiftComesAfterSevenGentleDaysThenWaitsAnotherWeek() {
        // Seven days in a row with something done for oneself (a journal entry, a mood, water…).
        var s = start.copy(journal = (0 until 7).map { DayEntry(day.minusDays(it.toLong()), mental = 3) })
        assertEquals(7, Care.gentleStreak(s, day))
        val gift = Care.gift(s, at(20)); s = gift.state
        assertEquals(Care.GIFT_POINTS, gift.points)
        assertEquals(day, s.game.giftOn)
        // Not again the next days, even though the streak goes on.
        s = s.copy(journal = s.journal + DayEntry(day.plusDays(1), mental = 3))
        assertEquals(0, Care.gift(s, at(20, d = day.plusDays(1))).points)
        // A week later, with seven more gentle days, another one.
        s = s.copy(journal = s.journal + (2..7).map { DayEntry(day.plusDays(it.toLong()), mental = 3) })
        assertEquals(Care.GIFT_POINTS, Care.gift(s, at(20, d = day.plusDays(7))).points)
        // A broken streak gives nothing: six days is not enough, and today still empty counts from yesterday.
        val six = start.copy(journal = (1..6).map { DayEntry(day.minusDays(it.toLong()), mental = 3) })
        assertEquals(6, Care.gentleStreak(six, day))
        assertEquals(0, Care.gift(six, at(20)).points)
    }

    @Test
    fun newWeeklyChallengesNeverRunDry() {
        val withWellbeing = start.copy(settings = Settings(wellbeing = WellbeingSettings(enabled = true)))
        // Whatever the week and whatever is turned on, three different challenges come.
        (0 until 60).forEach { w ->
            assertEquals(3, Challenges.of(start, day.plusWeeks(w.toLong())).map { it.id }.toSet().size)
            assertEquals(3, Challenges.of(withWellbeing, day.plusWeeks(w.toLong())).map { it.id }.toSet().size)
            assertTrue(Challenges.of(start, day.plusWeeks(w.toLong())).none { it.id == "wins3" })
        }
        // The new ones count what they say.
        val monday = Challenges.weekStart(day)
        var s = withWellbeing.copy(
            journal = (0 until 5).map { DayEntry(monday.plusDays(it.toLong()), mental = 3, notes = "ok") },
        )
        s = (1..3).fold(s) { acc, i -> Wellbeing.win(acc, "m$i", "x", 5, at(10, d = monday.plusDays(i.toLong()))).state }
        val all = (0 until 80).flatMap { w -> Challenges.status(s, monday.plusWeeks(w.toLong())).map { it.challenge.id } }.toSet()
        assertTrue("journal5" in all && "sleep6" in all && "wins3" in all)
    }

    // ── The companion's wish ──

    @Test
    fun theWishIsPickedOnceAWeekLeansOnTheSeasonAndNeverRepeatsWhatIsOwned() {
        // Winter: the Christmas bonnet, the plaid, a mug of tea are the candidates.
        val december = LocalDate.of(2026, 12, 14)
        val wish = Wishes.pick(start, december)!!
        assertTrue("winter item, got ${wish.id}", Wishes.fits(wish, com.medipote.app.data.Season.WINTER))
        // Chosen once: the same all week, whatever is bought, until next Monday.
        val chosen = Wishes.refresh(start, december)
        assertEquals(wish.id, chosen.game.wishId)
        val bought = chosen.copy(game = chosen.game.copy(owned = chosen.game.owned + wish.id))
        assertEquals(wish.id, Wishes.refresh(bought, december.plusDays(3)).game.wishId)
        // The next week, a new wish, and never one already owned.
        val next = Wishes.refresh(bought, december.plusDays(7))
        assertNotNull(next.game.wishId)
        assertFalse(next.game.wishId == wish.id)
        assertFalse(next.game.wishId in bought.game.owned)
        // Everything owned: no wish.
        val all = start.copy(game = start.game.copy(owned = Shop.items.map { it.id }.toSet()))
        assertNull(Wishes.pick(all, december))
        // The progress bar follows the points.
        val item = Shop.item("hat_santa")!!
        assertEquals(0.5f, Wishes.progress(GameState(points = item.price / 2), item), 0.02f)
        assertEquals(1f, Wishes.progress(GameState(points = item.price * 3), item), 0.001f)
    }

    @Test
    fun theNewCosyItemsAreInTheShopAtAFairPrice() {
        listOf("hat_santa", "toy_tea", "neck_plaid", "toy_lantern").forEach { id ->
            val item = Shop.item(id)!!
            assertTrue("$id costs something but not a fortune", item.price in 80..200)
            assertNull(item.badge)
        }
        assertEquals(Shop.items.size, Shop.items.map { it.id }.toSet().size)
    }

    @Test
    fun lateDosesNeverTalkAboutAPenalty() {
        val source = java.io.File("src/main/kotlin/com/medipote/app").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(source.contains("malus"))
    }

    // ── Hard nights ──

    @Test
    fun theNightHelpHasFourIdeasForEachTroubleAndTheRuleOfTwentyMinutes() {
        SleepTrouble.entries.forEach { t -> assertEquals("${t.id} ideas", 4, SleepHelp.ideas(t).size) }
        assertEquals(setOf(SleepTool.BREATHE, SleepTool.RELAX), SleepHelp.BACK.mapNotNull { it.tool }.toSet() - SleepTool.THOUGHTS)
        assertTrue(SleepHelp.FALL.any { it.tool == SleepTool.THOUGHTS })
        assertTrue(SleepHelp.RULE_TEXT.contains("20 minutes"))
        assertEquals(8, SleepHelp.RELAX_STEPS.size)
        assertTrue(SleepHelp.DAY_TIPS.size >= 5)
        // A doctor is named for long insomnia and snoring; no medication, supplement or dose is advised.
        assertTrue(SleepHelp.CONSULT.joinToString(" ").contains("trois semaines"))
        val text = (SleepHelp.FALL + SleepHelp.BACK + SleepHelp.DAY_TIPS).joinToString(" ") { it.title + " " + it.text }.lowercase() + SleepHelp.RULE_TEXT.lowercase()
        listOf("mélatonine", "somnifère", "dose", "comprimé", "médicament").forEach { assertFalse(it, text.contains(it)) }
    }

    @Test
    fun theEveningWordRingsBeforeTheNightAndNeverInQuietHours() {
        val s = start.copy(settings = Settings(sleep = SleepSettings(windDown = true, windDownMinute = 21 * 60 + 30)))
        val plan = ReminderPlanner.plan(s, at(18)).filter { it.kind == AlarmKind.WIND_DOWN }.map { it.time }
        assertEquals(listOf(at(21, 30), at(21, 30, day.plusDays(1))), plan)
        // Off by default.
        assertTrue(ReminderPlanner.plan(start, at(18)).none { it.kind == AlarmKind.WIND_DOWN })
        // Quiet hours from 21: the word is skipped, not moved to the morning.
        val quiet = s.copy(settings = s.settings.copy(quiet = com.medipote.app.data.QuietHours(enabled = true, startHour = 21, endHour = 8)))
        assertTrue(ReminderPlanner.plan(quiet, at(18)).none { it.kind == AlarmKind.WIND_DOWN })
    }

    @Test
    fun tryingTheNightHelpIsASmallVictoryOncePerTrouble() {
        val a = Wellbeing.tried(start, SleepHelp.TRIED_KIND, "fall", "J'ai essayé : m'aider à dormir", at(2))
        assertEquals(Wellbeing.MISSION_POINTS, a.points)
        assertEquals(setOf("fall"), Wellbeing.triedToday(a.state, day, SleepHelp.TRIED_KIND))
        assertTrue(Wellbeing.tried(a.state, SleepHelp.TRIED_KIND, "fall", "x", at(4)).already)
        assertEquals(Wellbeing.MISSION_POINTS, Wellbeing.tried(a.state, SleepHelp.TRIED_KIND, "back", "x", at(4)).points)
    }

    @Test
    fun anOldSaveLoadsWithTheNewFieldsEmpty() {
        val s = com.medipote.app.data.AppRepository.JSON.decodeFromString<AppState>("""{"onboarded":true,"game":{"points":10}}""")
        assertTrue(s.sleepRecipe.isEmpty())
        assertTrue(s.game.careRewards.isEmpty())
        assertNull(s.game.giftOn)
        assertNull(s.game.wishId)
        assertFalse(s.settings.sleep.windDown)
        assertFalse(Appointment("a", "x", dateTime = at(10)).attended)
    }
}
