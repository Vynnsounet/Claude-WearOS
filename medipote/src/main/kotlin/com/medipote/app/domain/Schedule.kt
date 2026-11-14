package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.DoseLog
import com.medipote.app.data.DoseTime
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One planned dose of one treatment on one day. */
data class DoseOccurrence(val treatment: Treatment, val dose: DoseTime, val date: LocalDate) {
    val key: String get() = keyOf(treatment.id, date, dose.minuteOfDay)
    val scheduled: LocalDateTime get() = date.atTime(LocalTime.of(dose.minuteOfDay / 60, dose.minuteOfDay % 60))

    /** Last moment the dose can still be given: end of its day, or 4 hours after it for late-evening doses. */
    val deadline: LocalDateTime
        get() = maxOf(date.atTime(LocalTime.MAX), scheduled.plusMinutes(Rules.LAST_CALL_MINUTES))

    companion object {
        fun keyOf(treatmentId: String, date: LocalDate, minuteOfDay: Int) = "$treatmentId@$date@$minuteOfDay"
    }
}

enum class DoseState {
    /** More than an hour ahead: can't be given yet. */
    UPCOMING,

    /** From one hour before to 30 minutes after the planned time: giving it now earns full points. */
    DUE,

    /** Past the 30-minute window but still giveable today, with fewer points. */
    OVERDUE,
    TAKEN,

    /** Never given before the deadline. */
    MISSED,
}

data class DoseItem(val occurrence: DoseOccurrence, val state: DoseState, val log: DoseLog?) {
    val canGive: Boolean get() = state == DoseState.DUE || state == DoseState.OVERDUE

    /** A dose later today that the user can say they already took. */
    fun canMarkEarly(now: java.time.LocalDateTime): Boolean =
        state == DoseState.UPCOMING && occurrence.date == now.toLocalDate()
}

/** The game's timing and scoring rules, in one place so the UI can explain them. */
object Rules {
    const val EARLY_WINDOW_MINUTES = 60L
    const val ON_TIME_MINUTES = 30L
    const val LATE_MINUTES = 120L
    const val LAST_CALL_MINUTES = 240L

    const val POINTS_ON_TIME = 10
    const val POINTS_LATE = 6
    const val POINTS_VERY_LATE = 3

    /** Taken more than an hour early: counted, but not rewarded like a dose taken on time. */
    const val POINTS_EARLY = 6
    const val PERFECT_DAY_BONUS = 20
    const val JOURNAL_BONUS = 5
    const val WATER_GLASS_POINTS = 2
    const val CUDDLE_BONUS = 3

    /** Logging the night, once a day (never tied to how long or how well one slept). */
    const val SLEEP_BONUS = 3
    const val WATER_BONUS = 10

    const val STREAK_POINTS_PER_DAY = 5
    const val STREAK_POINTS_CAP = 50

    /** Extra one-time bonus when the daily streak reaches these lengths. */
    val STREAK_MILESTONES = mapOf(7 to 30, 30 to 100, 100 to 300, 365 to 1000)

    fun statusFor(scheduled: LocalDateTime, takenAt: LocalDateTime): TakeStatus {
        val late = Duration.between(scheduled, takenAt).toMinutes()
        return when {
            late < -EARLY_WINDOW_MINUTES -> TakeStatus.EARLY
            late <= ON_TIME_MINUTES -> TakeStatus.ON_TIME
            late <= LATE_MINUTES -> TakeStatus.LATE
            else -> TakeStatus.VERY_LATE
        }
    }

    fun pointsFor(status: TakeStatus): Int = when (status) {
        TakeStatus.ON_TIME -> POINTS_ON_TIME
        TakeStatus.LATE -> POINTS_LATE
        TakeStatus.VERY_LATE -> POINTS_VERY_LATE
        TakeStatus.EARLY -> POINTS_EARLY
    }

    /** Points for opening the app on the n-th consecutive day: +5 per day, capped at +50 from day 10. */
    fun streakReward(streak: Int): Int = minOf(streak * STREAK_POINTS_PER_DAY, STREAK_POINTS_CAP)
}

object Schedule {

    fun occurrencesOn(treatments: List<Treatment>, date: LocalDate): List<DoseOccurrence> =
        treatments.filter { it.isActiveOn(date) }
            .flatMap { t -> t.doses.map { DoseOccurrence(t, it, date) } }
            .filter { o ->
                // A dose whose on-time window had already closed when the treatment (or this dose time)
                // was added is not owed.
                listOfNotNull(o.treatment.createdAt, o.dose.since).all { !o.scheduled.plusMinutes(Rules.ON_TIME_MINUTES).isBefore(it) }
            }
            .sortedWith(compareBy({ it.dose.minuteOfDay }, { it.treatment.name }))

    fun stateOf(occurrence: DoseOccurrence, log: DoseLog?, now: LocalDateTime): DoseState = when {
        log != null -> DoseState.TAKEN
        now.isBefore(occurrence.scheduled.minusMinutes(Rules.EARLY_WINDOW_MINUTES)) -> DoseState.UPCOMING
        !now.isAfter(occurrence.scheduled.plusMinutes(Rules.ON_TIME_MINUTES)) -> DoseState.DUE
        !now.isAfter(occurrence.deadline) -> DoseState.OVERDUE
        else -> DoseState.MISSED
    }

    fun itemsOn(
        treatments: List<Treatment>,
        logs: Map<String, DoseLog>,
        date: LocalDate,
        now: LocalDateTime,
    ): List<DoseItem> = occurrencesOn(treatments, date).map {
        val log = logs[it.key]
        DoseItem(it, stateOf(it, log, now), log)
    }

    /** Doses from earlier days that can still be given now (late-evening doses after midnight). */
    fun carriedOver(
        treatments: List<Treatment>,
        logs: Map<String, DoseLog>,
        now: LocalDateTime,
    ): List<DoseItem> = itemsOn(treatments, logs, now.toLocalDate().minusDays(1), now).filter { it.canGive }

    /**
     * Pairs the dose times of a treatment before and after an edit: unchanged times with themselves,
     * then moved times in order (8:00 and 20:00 → 9:00 and 21:00) when there are as many, or else
     * by closeness. Returns old minute → new minute; new times left out were added.
     */
    fun pairDoseTimes(old: List<Int>, new: List<Int>): Map<Int, Int> {
        val oldSet = old.toSortedSet()
        val newSet = new.toSortedSet()
        val pairs = (oldSet intersect newSet).associateWith { it }.toMutableMap()
        val oldRest = (oldSet - newSet).toList()
        val newRest = (newSet - oldSet).toMutableList()
        if (oldRest.size == newRest.size) {
            oldRest.zip(newRest).forEach { (o, n) -> pairs[o] = n }
        } else {
            val candidates = oldRest.flatMap { o -> newRest.map { n -> Triple(o, n, kotlin.math.abs(o - n)) } }.sortedBy { it.third }
            val usedOld = mutableSetOf<Int>()
            for ((o, n, _) in candidates) {
                if (o in usedOld || n !in newRest) continue
                pairs[o] = n
                usedOld += o
                newRest -= n
            }
        }
        return pairs
    }

    /**
     * Saves an edited treatment. When its dose times change, doses already given (today or on earlier
     * days) follow their time to its new value, so they don't have to be given again; a time that was
     * added is only owed from now on.
     */
    fun applyEdit(state: AppState, edited: Treatment, now: LocalDateTime): AppState {
        val old = state.treatments.firstOrNull { it.id == edited.id }
            ?: return state.copy(treatments = state.treatments + edited)
        val pairs = pairDoseTimes(old.doses.map { it.minuteOfDay }, edited.doses.map { it.minuteOfDay })
        val oldSince = old.doses.associate { it.minuteOfDay to it.since }
        val newToOld = pairs.entries.associate { (o, n) -> n to o }
        val doses = edited.doses.map { d ->
            val from = newToOld[d.minuteOfDay]
            d.copy(since = if (from != null) oldSince[from] else now)
        }
        val treatment = edited.copy(doses = doses)

        val moved = pairs.filter { (o, n) -> o != n }
        val logs = if (moved.isEmpty()) state.doseLogs else {
            val taken = state.doseLogs.filter { it.treatmentId == edited.id }.mapTo(HashSet()) { it.key }
            state.doseLogs.map { log ->
                if (log.treatmentId != edited.id) return@map log
                val minute = log.scheduled.hour * 60 + log.scheduled.minute
                val target = moved[minute] ?: return@map log
                val date = log.scheduled.toLocalDate()
                val key = DoseOccurrence.keyOf(edited.id, date, target)
                if (key in taken) log // that day, the new time was already given
                else log.copy(key = key, scheduled = date.atTime(LocalTime.of(target / 60, target % 60)))
            }
        }
        return state.copy(treatments = state.treatments.map { if (it.id == edited.id) treatment else it }, doseLogs = logs)
    }

    /** Next planned doses after [from], for the reminder alarms. */
    fun upcoming(treatments: List<Treatment>, from: LocalDateTime, hours: Long): List<DoseOccurrence> {
        val until = from.plusHours(hours)
        var date = from.toLocalDate()
        val result = mutableListOf<DoseOccurrence>()
        while (!date.isAfter(until.toLocalDate())) {
            result += occurrencesOn(treatments, date).filter { it.scheduled.isAfter(from) && !it.scheduled.isAfter(until) }
            date = date.plusDays(1)
        }
        return result
    }
}
