package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.Festival
import com.medipote.app.data.Season
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month

/** What the companion's world looks like today: the season, and a festival if it is one. */
data class Ambiance(val season: Season, val festival: Festival? = null)

/** Seasons and festivals: decor, phrases and a very light tint. Quiet, optional, and never on a very hard day. */
object Seasons {
    /** Meteorological seasons: December to February is winter (swapped in the southern hemisphere). */
    fun of(date: LocalDate, southern: Boolean = false): Season {
        val north = when (date.month) {
            Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.WINTER
            Month.MARCH, Month.APRIL, Month.MAY -> Season.SPRING
            Month.JUNE, Month.JULY, Month.AUGUST -> Season.SUMMER
            else -> Season.AUTUMN
        }
        return if (!southern) north else when (north) {
            Season.WINTER -> Season.SUMMER
            Season.SUMMER -> Season.WINTER
            Season.SPRING -> Season.AUTUMN
            Season.AUTUMN -> Season.SPRING
        }
    }

    /** Easter Sunday (Gregorian calendar, Meeus/Jones/Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** A birthday falls on the same day and month; 29 February is kept on the 28th in other years. */
    private fun sameDay(date: LocalDate, birthday: LocalDate): Boolean {
        if (birthday.monthValue == 2 && birthday.dayOfMonth == 29 && !date.isLeapYear) return date.monthValue == 2 && date.dayOfMonth == 28
        return date.monthValue == birthday.monthValue && date.dayOfMonth == birthday.dayOfMonth
    }

    /**
     * When the companion was "born": the day it was first used. Kept once known; otherwise the oldest
     * thing the app remembers (older installs have no recorded date), or today.
     */
    fun petBirthday(state: AppState, today: LocalDate): LocalDate = state.settings.seasons.petBirthday
        ?: listOfNotNull(
            state.doseLogs.minOfOrNull { it.takenAt.toLocalDate() },
            state.journal.minOfOrNull { it.date },
            state.sleep.minOfOrNull { it.date },
            state.treatments.mapNotNull { it.createdAt?.toLocalDate() }.minOrNull(),
        ).minOrNull() ?: today

    /** The festival of [date], if it is one the user keeps. The user's own day comes first. */
    fun festival(state: AppState, date: LocalDate): Festival? {
        val s = state.settings.seasons
        fun on(f: Festival, test: Boolean) = f.takeIf { it in s.festivals && test }
        val easter = easter(date.year)
        return on(Festival.USER_BIRTHDAY, s.birthday?.let { sameDay(date, it) } == true)
            ?: on(Festival.PET_BIRTHDAY, petBirthday(state, date).let { it.year < date.year && sameDay(date, it) })
            ?: on(Festival.NEW_YEAR, (date.monthValue == 12 && date.dayOfMonth == 31) || (date.monthValue == 1 && date.dayOfMonth == 1))
            ?: on(Festival.VALENTINE, date.monthValue == 2 && date.dayOfMonth == 14)
            ?: on(Festival.EASTER, date == easter || date == easter.plusDays(1))
            ?: on(Festival.HALLOWEEN, date.monthValue == 10 && date.dayOfMonth == 31)
            ?: on(Festival.CHRISTMAS, date.monthValue == 12 && (date.dayOfMonth == 24 || date.dayOfMonth == 25))
    }

    /**
     * Today's ambiance, or null when the user turned the seasons off. On a very hard day (mood "very bad")
     * the festival is left out: a festive companion would be out of place.
     */
    fun ambiance(state: AppState, now: LocalDateTime): Ambiance? {
        val s = state.settings.seasons
        if (!s.enabled) return null
        val date = now.toLocalDate()
        val festival = festival(state, date)?.takeUnless { Support.lowMood(state.entryFor(date)) }
        return Ambiance(of(date, s.southern), festival)
    }

    /** A few words for the day itself. */
    fun greeting(festival: Festival, name: String): String {
        val who = name.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
        return when (festival) {
            Festival.NEW_YEAR -> "Bonne année$who ! Que cette année te soit douce 🎆"
            Festival.VALENTINE -> "Joyeuse Saint-Valentin$who ! Un peu d'amour pour toi aussi 💝"
            Festival.EASTER -> "Joyeuses Pâques$who ! 🥚"
            Festival.HALLOWEEN -> "Joyeux Halloween$who ! 🎃 Pas de peur, je reste là."
            Festival.CHRISTMAS -> "Joyeux Noël$who ! Prends soin de toi 🎄"
            Festival.PET_BIRTHDAY -> "C'est mon anniversaire aujourd'hui ! Merci d'être là depuis le début 🎂"
            Festival.USER_BIRTHDAY -> "Joyeux anniversaire$who ! 🎈 Aujourd'hui, c'est toi qu'on fête."
        }
    }
}
