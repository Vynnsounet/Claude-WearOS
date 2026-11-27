package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Drink
import com.medipote.app.data.WaterSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** Water in millilitres: each drink noted with its quantity, the total worked out. */
object Water {
    /** Points are given for each 250 ml, however the water was noted. */
    const val UNIT_ML = 250
    const val MAX_DRINK_ML = 2000
    const val MIN_DRINK_ML = 10

    val PRESETS = listOf(150, 250, 330, 500)

    fun goalMl(w: WaterSettings): Int = if (w.goalMl > 0) w.goalMl else w.goalGlasses * w.glassMl

    /** What counts in the total of [entry]: the glasses of before, and every drink (the hot ones only if the person chose so). */
    fun ml(entry: DayEntry, w: WaterSettings): Int =
        entry.waterGlasses * w.glassMl + entry.drinks.filter { !it.hot || w.countHotDrinks }.sumOf { it.ml }

    fun ml(state: AppState, date: LocalDate): Int = ml(state.entryFor(date), state.settings.water)

    /** The total in glasses of the person, for the places that still count so (the watch). */
    fun glasses(ml: Int, w: WaterSettings): Int = ml / w.glassMl.coerceAtLeast(1)

    /** « 1,2 L », « 850 ml ». */
    fun format(ml: Int): String = when {
        ml >= 1000 -> "%.2f".format(Locale.FRANCE, ml / 1000.0).trimEnd('0').trimEnd(',') + " L"
        else -> "$ml ml"
    }

    /** « 1,2 L / 2 L »: the day's progress. */
    fun progress(ml: Int, goal: Int): String = "${format(ml)} / ${format(goal)}"

    /** The most that is rewarded in a day: twice the goal, never more than 5 L. */
    fun rewardedUnits(goalMl: Int): Int = (goalMl * 2).coerceAtMost(5000).coerceAtLeast(goalMl) / UNIT_ML

    /** Glasses noted before 1.34 become one line of drinks, so they can be seen and edited like the others. */
    fun normalized(entry: DayEntry, w: WaterSettings): DayEntry =
        if (entry.waterGlasses == 0) entry
        else entry.copy(
            waterGlasses = 0,
            drinks = listOf(Drink(entry.date.atTime(LocalTime.NOON), entry.waterGlasses * w.glassMl)) + entry.drinks,
        )

    fun valid(ml: Int): Boolean = ml in MIN_DRINK_ML..MAX_DRINK_ML
}
