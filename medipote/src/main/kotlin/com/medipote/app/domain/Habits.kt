package com.medipote.app.domain

import com.medipote.app.data.AppState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** « You usually take it around 8:40 »: the reminder could follow. */
data class HabitSuggestion(
    val treatmentId: String,
    val treatmentName: String,
    /** The dose time as it is now, and the time it is really taken at. */
    val minute: Int,
    val usualMinute: Int,
    val logs: Int,
) {
    val key: String get() = "habit:$treatmentId:$minute"
    private fun clock(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    val usualText: String get() = clock(usualMinute)
    val currentText: String get() = clock(minute)
}

/**
 * Learns, from the doses given, when each fixed dose is really taken, and offers to move its reminder when the two are far
 * apart and steady. Local, no model: a median over the last three weeks. Never touches windows, anchored doses or as-needed ones.
 */
object Habits {
    const val WINDOW_DAYS = 21L
    const val MIN_LOGS = 7
    const val MIN_SHIFT = 20
    const val MAX_SPREAD = 25
    const val SNOOZE_DAYS = 30L

    fun suggestion(state: AppState, now: LocalDateTime): HabitSuggestion? {
        val from = now.minusDays(WINDOW_DAYS)
        for (t in state.treatments) {
            if (t.asNeeded || t.pill != null || !t.isActiveOn(now.toLocalDate())) continue
            for (d in t.doses) {
                if (d.isWindow || d.isAnchored) continue
                val offsets = state.doseLogs.filter { l ->
                    l.treatmentId == t.id && l.scheduled.hour * 60 + l.scheduled.minute == d.minuteOfDay &&
                        !l.takenAt.isBefore(from) && t.shiftOn(l.scheduled.toLocalDate()) == 0
                }.map { ChronoUnit.MINUTES.between(it.scheduled, it.takenAt).toInt() }
                    // A dose noted much later than planned says nothing of when it is taken.
                    .filter { abs(it) <= 180 }.sorted()
                if (offsets.size < MIN_LOGS) continue
                val median = offsets[offsets.size / 2]
                val spread = offsets[(offsets.size * 3) / 4] - offsets[offsets.size / 4]
                if (abs(median) < MIN_SHIFT || spread > MAX_SPREAD) continue
                val usual = (((d.minuteOfDay + median) / 5.0).roundToInt() * 5).coerceIn(0, 24 * 60 - 1)
                if (t.doses.any { it.minuteOfDay == usual }) continue
                val s = HabitSuggestion(t.id, t.displayName, d.minuteOfDay, usual, offsets.size)
                if (!dismissed(state, s, now.toLocalDate())) return s
            }
        }
        return null
    }

    private fun dismissed(state: AppState, s: HabitSuggestion, today: LocalDate): Boolean =
        state.settings.dismissedChecks[s.key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { !it.plusDays(SNOOZE_DAYS).isBefore(today) } ?: false

    /** Moves the reminder to the usual time; the doses already given go with it. */
    fun apply(state: AppState, s: HabitSuggestion, now: LocalDateTime): AppState {
        val t = state.treatments.firstOrNull { it.id == s.treatmentId } ?: return state
        val doses = t.doses.map { if (it.minuteOfDay == s.minute) it.copy(minuteOfDay = s.usualMinute) else it }
        return Schedule.applyEdit(state, t.copy(doses = doses), now)
    }

    /** « Pas maintenant »: not offered again for a month. */
    fun dismiss(state: AppState, s: HabitSuggestion, today: LocalDate): AppState =
        state.copy(settings = state.settings.copy(dismissedChecks = state.settings.dismissedChecks + (s.key to today.toString())))
}
