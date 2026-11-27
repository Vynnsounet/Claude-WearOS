package com.medipote.app.domain

import com.medipote.app.data.Anchor
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.Routine
import java.time.LocalDateTime

/**
 * Doses tied to the day's rhythm (« 30 minutes before breakfast », « at bedtime »): the clock time is worked out from the
 * person's usual wake-up, meals and bedtime, and follows them when they change.
 */
object Routines {
    /** The time of day an anchored dose falls at. */
    fun minuteFor(routine: Routine, anchor: Anchor, offset: Int): Int = (routine.minute(anchor) + offset).coerceIn(0, 24 * 60 - 1)

    /** The wake-up time the nights suggest: the usual one of the last month, once at least five nights are noted. */
    fun usualWake(state: AppState, today: java.time.LocalDate): Int? {
        val nights = state.sleep.filter { !it.noSleep && !it.date.isBefore(today.minusDays(30)) && !it.date.isAfter(today) }
        if (nights.size < 5) return null
        return Reports.circularMean(nights.map { Sleep.minuteOf(it.wakeTime) }, 0.3)?.let { (it / 5) * 5 }
    }

    /**
     * Puts every anchored dose at the time its anchor gives today. A dose that moves takes the doses already given with it, as
     * when the person edits its time, so nothing already taken becomes « missed ».
     */
    fun sync(state: AppState, now: LocalDateTime): AppState {
        var result = state
        for (t in state.treatments) {
            if (t.asNeeded || t.doses.none { it.isAnchored }) continue
            val taken = mutableSetOf<Int>()
            // Fixed doses keep their time; an anchored one never lands on a time already used by the same treatment.
            t.doses.filter { !it.isAnchored }.forEach { taken += it.minuteOfDay }
            val doses = t.doses.map { d ->
                if (!d.isAnchored) d else {
                    var minute = minuteFor(state.settings.routine, Anchor.of(d.anchor)!!, d.offsetMinutes)
                    while (minute in taken && minute < 24 * 60 - 1) minute++
                    taken += minute
                    if (minute == d.minuteOfDay && d.untilMinute == null) d else d.copy(minuteOfDay = minute, untilMinute = null, remindEnd = false)
                }
            }
            if (doses != t.doses) result = Schedule.applyEdit(result, t.copy(doses = doses), now)
        }
        return result
    }

    /** Doses of [treatments] that can follow an anchor: the ones the editor offers it for. */
    fun anchored(doses: List<DoseTime>): List<DoseTime> = doses.filter { it.isAnchored }
}
