package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Festival
import com.medipote.app.data.Season
import com.medipote.app.data.SeasonSettings
import com.medipote.app.data.Settings
import com.medipote.app.domain.DailyPhrases
import com.medipote.app.domain.PetTalk
import com.medipote.app.domain.Seasons
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun state(seasons: SeasonSettings = SeasonSettings(petBirthday = d(2024, 11, 12))) = AppState(settings = Settings(seasons = seasons))

    @Test
    fun seasonsFollowTheMonthsAndTheHemisphere() {
        assertEquals(Season.WINTER, Seasons.of(d(2026, 12, 1)))
        assertEquals(Season.WINTER, Seasons.of(d(2026, 2, 28)))
        assertEquals(Season.SPRING, Seasons.of(d(2026, 3, 1)))
        assertEquals(Season.SUMMER, Seasons.of(d(2026, 6, 1)))
        assertEquals(Season.AUTUMN, Seasons.of(d(2026, 9, 1)))
        assertEquals(Season.AUTUMN, Seasons.of(d(2026, 11, 30)))
        // The southern hemisphere has them the other way round.
        assertEquals(Season.SUMMER, Seasons.of(d(2026, 12, 25), southern = true))
        assertEquals(Season.SPRING, Seasons.of(d(2026, 10, 10), southern = true))
    }

    @Test
    fun easterIsRightForManyYears() {
        assertEquals(d(2024, 3, 31), Seasons.easter(2024))
        assertEquals(d(2025, 4, 20), Seasons.easter(2025))
        assertEquals(d(2026, 4, 5), Seasons.easter(2026))
        assertEquals(d(2027, 3, 28), Seasons.easter(2027))
        assertEquals(d(2038, 4, 25), Seasons.easter(2038))
    }

    @Test
    fun festivalsFallOnTheirDaysAndOnlyIfKept() {
        val s = state()
        assertEquals(Festival.NEW_YEAR, Seasons.festival(s, d(2026, 12, 31)))
        assertEquals(Festival.NEW_YEAR, Seasons.festival(s, d(2027, 1, 1)))
        assertEquals(Festival.VALENTINE, Seasons.festival(s, d(2027, 2, 14)))
        assertEquals(Festival.EASTER, Seasons.festival(s, d(2026, 4, 5)))
        assertEquals(Festival.EASTER, Seasons.festival(s, d(2026, 4, 6)))
        assertNull(Seasons.festival(s, d(2026, 4, 7)))
        assertEquals(Festival.HALLOWEEN, Seasons.festival(s, d(2026, 10, 31)))
        assertEquals(Festival.CHRISTMAS, Seasons.festival(s, d(2026, 12, 24)))
        assertEquals(Festival.CHRISTMAS, Seasons.festival(s, d(2026, 12, 25)))
        assertNull(Seasons.festival(s, d(2026, 12, 26)))
        // A festival the user unchecked is not marked.
        val noXmas = state(SeasonSettings(festivals = Festival.entries.toSet() - Festival.CHRISTMAS, petBirthday = d(2024, 11, 12)))
        assertNull(Seasons.festival(noXmas, d(2026, 12, 25)))
        // Mother's and father's days are not part of it.
        assertTrue(Festival.entries.none { "mère" in it.label.lowercase() || "père" in it.label.lowercase() })
    }

    @Test
    fun birthdays() {
        val s = state(SeasonSettings(birthday = d(1990, 5, 20), petBirthday = d(2025, 11, 12)))
        assertEquals(Festival.USER_BIRTHDAY, Seasons.festival(s, d(2026, 5, 20)))
        // The companion's birthday is the first day of use, from the second year on.
        assertNull(Seasons.festival(s, d(2025, 11, 12)))
        assertEquals(Festival.PET_BIRTHDAY, Seasons.festival(s, d(2026, 11, 12)))
        // Someone's own day wins over a festival; 29 February is kept on the 28th in other years.
        val leap = state(SeasonSettings(birthday = d(2000, 2, 29), petBirthday = d(2024, 11, 12)))
        assertEquals(Festival.USER_BIRTHDAY, Seasons.festival(leap, d(2027, 2, 28)))
        assertEquals(Festival.USER_BIRTHDAY, Seasons.festival(leap, d(2028, 2, 29)))
        assertNull(Seasons.festival(leap, d(2028, 2, 28)))
        val onXmas = state(SeasonSettings(birthday = d(1990, 12, 25), petBirthday = d(2024, 11, 12)))
        assertEquals(Festival.USER_BIRTHDAY, Seasons.festival(onXmas, d(2026, 12, 25)))
    }

    @Test
    fun theCompanionsBirthdayIsTheFirstDayThingsWereRecorded() {
        val noDate = AppState(journal = listOf(DayEntry(d(2025, 3, 3), mental = 3), DayEntry(d(2025, 6, 1), mental = 3)))
        assertEquals(d(2025, 3, 3), Seasons.petBirthday(noDate, d(2026, 11, 16)))
        assertEquals(d(2026, 11, 16), Seasons.petBirthday(AppState(), d(2026, 11, 16)))
        // Once noted, it never moves.
        assertEquals(d(2024, 1, 2), Seasons.petBirthday(noDate.copy(settings = Settings(seasons = SeasonSettings(petBirthday = d(2024, 1, 2)))), d(2026, 11, 16)))
    }

    @Test
    fun noFestivalOnAVeryHardDayAndNothingWhenSwitchedOff() {
        val xmas = d(2026, 12, 25).atTime(10, 0)
        val normal = state()
        assertEquals(Festival.CHRISTMAS, Seasons.ambiance(normal, xmas)?.festival)
        assertEquals(Season.WINTER, Seasons.ambiance(normal, xmas)?.season)
        // Mood "very bad": the season stays, the festival goes.
        val hard = normal.copy(journal = listOf(DayEntry(d(2026, 12, 25), mental = 1)))
        assertNull(Seasons.ambiance(hard, xmas)?.festival)
        assertEquals(Season.WINTER, Seasons.ambiance(hard, xmas)?.season)
        assertFalse("Noël" in PetTalk.greeting(hard.copy(onboarded = true), xmas))
        assertTrue("Noël" in PetTalk.greeting(normal.copy(onboarded = true), xmas))
        // The master switch turns everything off.
        assertNull(Seasons.ambiance(state(SeasonSettings(enabled = false)), xmas))
    }

    @Test
    fun phrasesFollowTheSeasonNowAndThenAndStayDeterministic() {
        val days = (0 until 30).map { d(2026, 11, 1).plusDays(it.toLong()) }
        val plain = days.map { DailyPhrases.of(it) }
        val autumn = days.map { DailyPhrases.of(it, Season.AUTUMN) }
        // A third of the days are seasonal, the others unchanged.
        val changed = plain.indices.count { plain[it] != autumn[it] }
        assertTrue("changed=$changed", changed in 8..12)
        assertEquals(autumn, days.map { DailyPhrases.of(it, Season.AUTUMN) })
        assertNotEquals(DailyPhrases.of(d(2026, 11, 3), Season.SPRING), DailyPhrases.of(d(2026, 11, 3), Season.WINTER))
        assertTrue((plain + autumn + days.map { DailyPhrases.of(it, Season.WINTER) }).none { "·" in it })
        // The greetings are neutral and short.
        Festival.entries.forEach { assertTrue(Seasons.greeting(it, "Sam").length < 90) }
    }
}
