package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Settings
import com.medipote.app.data.WaterSettings
import com.medipote.app.domain.Game
import com.medipote.app.domain.Reports
import com.medipote.app.domain.Rules
import com.medipote.app.domain.Water
import com.medipote.app.wear.WatchBridge
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchPlanner
import com.medipote.sync.WatchReminder
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.34: water in millilitres, drink by drink. */
class V34Test {
    private val day = LocalDate.of(2026, 11, 27)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private fun state(water: WaterSettings = WaterSettings(enabled = true)) = AppState(onboarded = true, settings = Settings(water = water))
    private fun drink(s: AppState, ml: Int, h: Int, m: Int = 0, hot: Boolean = false) = Game.addDrink(s, ml, at(h, m), hot = hot)

    @Test
    fun drinksAreNotedOneByOneAndTheTotalIsTheirSum() {
        var s = state()
        s = drink(s, 150, 9, 10).first
        s = drink(s, 250, 11, 40).first
        assertEquals(400, Water.ml(s, day))
        assertEquals(listOf(150, 250), s.entryFor(day).drinks.map { it.ml })
        assertEquals(listOf(at(9, 10), at(11, 40)), s.entryFor(day).drinks.map { it.at })
    }

    @Test
    fun aDrinkNotedLateKeepsItsPlaceInTheDay() {
        var s = drink(state(), 250, 15).first
        s = Game.addDrink(s, 150, at(16), at = at(9)).first
        assertEquals(listOf(150, 250), s.entryFor(day).drinks.map { it.ml })
    }

    @Test
    fun glassesNotedBefore134BecomeADrinkWithTheirOwnSize() {
        val old = state(WaterSettings(glassMl = 200)).copy(journal = listOf(DayEntry(day, waterGlasses = 3)))
        assertEquals(600, Water.ml(old, day))
        val s = drink(old, 150, 18).first
        assertEquals(750, Water.ml(s, day))
        assertEquals(0, s.entryFor(day).waterGlasses)
        assertEquals(setOf(600, 150), s.entryFor(day).drinks.map { it.ml }.toSet())
    }

    @Test
    fun theGoalFollowsTheGlassesUntilAGoalInMlIsSet() {
        assertEquals(2000, Water.goalMl(WaterSettings()))
        assertEquals(1800, Water.goalMl(WaterSettings(goalGlasses = 6, glassMl = 300)))
        assertEquals(2500, Water.goalMl(WaterSettings(goalGlasses = 6, glassMl = 300, goalMl = 2500)))
    }

    @Test
    fun amountsAreWrittenLikeAPerson() {
        assertEquals("1,2 L", Water.format(1200)); assertEquals("2 L", Water.format(2000)); assertEquals("1,25 L", Water.format(1250))
        assertEquals("850 ml", Water.format(850)); assertEquals("0 ml", Water.format(0))
        assertEquals("1,2 L / 2 L", Water.progress(1200, 2000))
    }

    @Test
    fun pointsComeFromEach250mlAndNeverTwice() {
        var s = state(WaterSettings(goalMl = 1000))
        var r = drink(s, 100, 9); s = r.first; assertEquals(0, r.second.glassPoints)
        r = drink(s, 150, 10); s = r.first; assertEquals(Rules.WATER_GLASS_POINTS, r.second.glassPoints)
        r = drink(s, 750, 11); s = r.first
        assertEquals(3 * Rules.WATER_GLASS_POINTS, r.second.glassPoints)
        assertEquals(Rules.WATER_BONUS, r.second.goalBonus)
        // taking back and noting again pays nothing more
        s = Game.undoDrink(s, at(12)).first
        r = drink(s, 750, 12, 5); s = r.first
        assertEquals(0, r.second.total)
        // up to twice the goal: 8 units of 250 ml, 4 already paid
        r = drink(s, 2000, 13); assertEquals(4 * Rules.WATER_GLASS_POINTS, r.second.glassPoints)
        // a new day starts over
        assertTrue(Game.addDrink(r.first, 500, at(9).plusDays(1)).second.glassPoints > 0)
    }

    @Test
    fun aDrinkCanBeTakenBack() {
        var s = state()
        s = drink(s, 150, 9).first; s = drink(s, 250, 10).first; s = drink(s, 330, 11).first
        s = Game.removeDrink(s, 1, at(12)).first
        assertEquals(listOf(150, 330), s.entryFor(day).drinks.map { it.ml })
        s = Game.undoDrink(s, at(12)).first
        assertEquals(listOf(150), s.entryFor(day).drinks.map { it.ml })
        assertEquals(s, Game.removeDrink(s, 5, at(12)).first)
        assertEquals(0, Water.ml(Game.undoDrink(Game.undoDrink(s, at(12)).first, at(12)).first, day))
    }

    @Test
    fun absurdAmountsAreIgnored() {
        val s = state()
        assertEquals(s, Game.addDrink(s, 5, at(9)).first)
        assertEquals(s, Game.addDrink(s, 3000, at(9)).first)
        assertTrue(Water.valid(10) && Water.valid(2000)); assertFalse(Water.valid(9) || Water.valid(2001))
    }

    @Test
    fun hotDrinksCountOnlyWhenTheSettingIsOn() {
        val off = drink(drink(state(), 250, 9).first, 200, 10, hot = true).first
        assertEquals(250, Water.ml(off, day))
        val on = off.copy(settings = off.settings.copy(water = off.settings.water.copy(countHotDrinks = true)))
        assertEquals(450, Water.ml(on, day))
    }

    @Test
    fun theOneTapGlassUsesTheSizeOfTheGlass() {
        var s = state(WaterSettings(glassMl = 300))
        s = Game.addWater(s, 1, at(9)).first
        assertEquals(300, Water.ml(s, day))
        s = Game.addWater(s, -1, at(9, 5)).first
        assertEquals(0, Water.ml(s, day))
    }

    @Test
    fun oldSavesLoad() {
        val e = AppRepository.JSON.decodeFromString(DayEntry.serializer(), """{"date":"2026-11-27","waterGlasses":2}""")
        assertTrue(e.drinks.isEmpty()); assertEquals(500, Water.ml(e, WaterSettings()))
        val s = drink(state(), 150, 9).first
        val back = AppRepository.JSON.decodeFromString(AppState.serializer(), AppRepository.JSON.encodeToString(AppState.serializer(), s))
        assertEquals(150, Water.ml(back, day))
        assertFalse("water alone is not a journal day", s.entryFor(day).isEmpty)
    }

    @Test
    fun theStatsAreInMillilitres() {
        val s = state().copy(journal = listOf(DayEntry(day, waterGlasses = 8), DayEntry(day.minusDays(1), waterGlasses = 4)))
        val report = Reports.adherence(s, day.atTime(20, 0), 7)
        assertEquals(1500f, report.waterAverage!!, 0.1f)
        assertEquals(1, report.waterGoalDays)
    }

    // ───────────── The watch ─────────────
    @Test
    fun theWatchKnowsTheTotalInLitres() {
        val s = drink(state(WaterSettings(enabled = true, glassMl = 250)), 400, 9).first
        val snapshot = WatchBridge.snapshot(s, at(10))
        assertEquals(400, snapshot.waterMl); assertEquals(2000, snapshot.goalMl); assertEquals(250, snapshot.glassMl)
        assertEquals("400 ml / 2 L", WatchPlanner.waterText(snapshot, day))
        val tapped = WatchPlanner.apply(snapshot, WatchAction("w", WatchAction.WATER, at(10).toString(), value = 1.0))
        assertEquals("650 ml / 2 L", WatchPlanner.waterText(tapped, day))
        // the next day starts at zero
        assertEquals("0 ml / 2 L", WatchPlanner.waterText(snapshot, day.plusDays(1)))
        val reminder = WatchReminder(WatchReminder.WATER, at(11).toString())
        assertEquals("400 ml / 2 L aujourd'hui", WatchPlanner.message(snapshot, reminder, at(11)).second)
        assertTrue(WatchPlanner.relevant(snapshot, reminder, at(11)))
        val done = drink(s, 1600, 12).first
        assertFalse(WatchPlanner.relevant(WatchBridge.snapshot(done, at(13)), reminder, at(13)))
    }

    @Test
    fun anOldPhoneSendingGlassesStillShowsGlasses() {
        val snapshot = com.medipote.sync.WatchSnapshot(generatedAt = at(9).toString(), waterEnabled = true, waterDate = day.toString(), glasses = 3, goal = 8)
        assertEquals("3 / 8 verres", WatchPlanner.waterText(snapshot, day))
    }
}
