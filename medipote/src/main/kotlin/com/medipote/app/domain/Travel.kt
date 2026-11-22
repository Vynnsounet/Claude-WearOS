package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.TravelPlan
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sign

/**
 * A trip across time zones. The phone changes clock on arrival, the body does not: the doses stay at home time (shifted
 * by the time difference) and come back to the local clock a little each day. The same way back at the return.
 */
object Travel {
    const val MAX_OFFSET = 12

    /** Moves [value] toward 0 by [step] minutes a day, [days] days in. */
    private fun approach(value: Int, step: Int, days: Long): Int =
        if (abs(value) <= days * step) 0 else value - value.sign * (days * step).toInt()

    /** Minutes added to the doses of [date], in the phone's clock of that day. */
    fun shiftMinutes(plan: TravelPlan, date: LocalDate): Int {
        val offset = plan.offsetHours.coerceIn(-MAX_OFFSET, MAX_OFFSET) * 60
        val step = plan.stepHours.coerceIn(1, 6) * 60
        if (offset == 0 || date.isBefore(plan.arrival)) return 0
        val sinceArrival = ChronoUnit.DAYS.between(plan.arrival, date)
        val back = plan.returnDate
        if (back == null || date.isBefore(back)) return approach(offset, step, sinceArrival)
        // Back home: the body is where the trip left it, the clock is home's again.
        val startBack = approach(offset, step, ChronoUnit.DAYS.between(plan.arrival, back)) - offset
        return approach(startBack, step, ChronoUnit.DAYS.between(back, date))
    }

    /** The days of the plan on which something moves. */
    fun days(plan: TravelPlan): List<LocalDate> {
        val last = (plan.returnDate ?: plan.arrival).plusDays((abs(plan.offsetHours) / plan.stepHours.coerceAtLeast(1) + 3).toLong())
        return generateSequence(plan.arrival) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    /** Keeps the shifts of the treatments in step with the plan (and removes them when there is none). */
    fun sync(state: AppState): AppState {
        val plan = state.travel
        if (plan == null && state.treatments.all { it.shifts.isEmpty() }) return state
        val shifts = plan?.let { p -> days(p).associate { it.toString() to shiftMinutes(p, it) }.filterValues { it != 0 } }.orEmpty()
        if (state.treatments.all { it.shifts == shifts }) return state
        return state.copy(treatments = state.treatments.map { it.copy(shifts = shifts) })
    }

    fun active(plan: TravelPlan?, today: LocalDate): Boolean {
        if (plan == null || plan.offsetHours == 0) return false
        return days(plan).any { !it.isBefore(today) } && !today.isBefore(plan.arrival.minusDays(1))
    }

    /** A dose at [baseMinute] over the first days: (day, clock time on that day). */
    fun preview(plan: TravelPlan, baseMinute: Int, count: Int = 8): List<Pair<LocalDate, String>> =
        days(plan).take(count + if (plan.returnDate != null) 8 else 0).map { d ->
            val m = ((baseMinute + shiftMinutes(plan, d)) % (24 * 60) + 24 * 60) % (24 * 60)
            d to "%d h %02d".format(m / 60, m % 60)
        }

    /** Today's shift in words, for the home notice. */
    fun todayText(plan: TravelPlan, today: LocalDate): String? {
        val m = shiftMinutes(plan, today)
        if (m == 0) return null
        val h = abs(m) / 60
        val min = abs(m) % 60
        val d = if (min == 0) "$h h" else "$h h ${"%02d".format(min)}"
        return "Tes prises sont décalées de ${if (m > 0) "+" else "−"}$d aujourd'hui, et se rapprochent de l'heure locale chaque jour."
    }
}
