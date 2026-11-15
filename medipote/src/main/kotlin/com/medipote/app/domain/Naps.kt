package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.Nap
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Naps over a period. */
data class NapReport(
    val naps: List<Nap>,
    val totalMinutes: Int,
    val averageMinutes: Int?,
    val daysWithNap: Int,
)

/** What pressing "I wake up" gave. */
sealed interface NapStop {
    data class Saved(val state: AppState, val nap: Nap) : NapStop

    /** Pressed a few seconds after "I fall asleep": nothing worth noting. */
    data class TooShort(val state: AppState) : NapStop

    /** The timer was forgotten: the user fixes the duration before it is noted. */
    data class TooLong(val state: AppState, val start: LocalDateTime) : NapStop
}

/** Naps: a nap is not a night, so nights' figures (average, efficiency, awakenings, streaks) never include it. */
object Naps {
    const val MAX_MINUTES = 240
    const val MIN_MINUTES = 1
    val SHORTCUTS = listOf(15, 20, 30, 45, 60, 90, 120)

    /** A timer left running longer than this is forgotten. */
    const val STALE_HOURS = 12L

    fun save(state: AppState, nap: Nap): AppState =
        state.copy(naps = (state.naps.filterNot { it.id == nap.id } + nap).sortedByDescending { it.start }.take(2000))

    fun delete(state: AppState, id: String): AppState = state.copy(naps = state.naps.filterNot { it.id == id })

    fun on(state: AppState, date: LocalDate): List<Nap> = state.naps.filter { it.date == date }.sortedBy { it.start }

    fun minutesOn(state: AppState, date: LocalDate): Int = on(state, date).sumOf { it.minutes }

    // ── The timer ──

    /** The running timer's start, or null (none, or forgotten for too long). */
    fun running(state: AppState, now: LocalDateTime): LocalDateTime? =
        state.napSince?.takeIf { !it.isAfter(now) && Duration.between(it, now).toHours() < STALE_HOURS }

    fun elapsedMinutes(since: LocalDateTime, now: LocalDateTime): Int = Duration.between(since, now).toMinutes().toInt().coerceAtLeast(0)

    /** "I'm falling asleep": a timer starts, unless one is already running. */
    fun start(state: AppState, now: LocalDateTime): AppState =
        if (running(state, now) != null) state else state.copy(napSince = now.withSecond(0).withNano(0))

    fun cancel(state: AppState): AppState = state.copy(napSince = null)

    /** "I wake up": the nap is noted with the time that went by. */
    fun stop(state: AppState, now: LocalDateTime, id: String): NapStop {
        val since = state.napSince ?: return NapStop.TooShort(state)
        val cleared = state.copy(napSince = null)
        val minutes = elapsedMinutes(since, now)
        return when {
            Duration.between(since, now).toHours() >= STALE_HOURS || minutes > MAX_MINUTES -> NapStop.TooLong(cleared, since)
            minutes < MIN_MINUTES -> NapStop.TooShort(cleared)
            else -> Nap(id, since, minutes).let { NapStop.Saved(save(cleared, it), it) }
        }
    }

    // ── Statistics ──

    fun report(state: AppState, today: LocalDate, days: Int): NapReport {
        val start = today.minusDays(days - 1L)
        val naps = state.naps.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }.sortedBy { it.start }
        val total = naps.sumOf { it.minutes }
        return NapReport(naps, total, naps.takeIf { it.isNotEmpty() }?.let { (total.toFloat() / it.size).roundToInt() }, naps.map { it.date }.toSet().size)
    }

    /** Night plus naps of that day (naps belong to the day they start), average over the nights of [nights]. */
    fun averageDailyTotal(state: AppState, nights: List<com.medipote.app.data.SleepNight>): Int? =
        nights.takeIf { it.isNotEmpty() }?.map { it.asleepMinutes + minutesOn(state, it.date) }?.average()?.roundToInt()

    /** Whether a night reaches the goal, counting that day's naps when the setting says so. */
    fun reachesGoal(state: AppState, night: com.medipote.app.data.SleepNight): Boolean {
        val extra = if (state.settings.sleep.napsInGoal) minutesOn(state, night.date) else 0
        return night.asleepMinutes + extra >= state.settings.sleep.goalMinutes
    }

    /** A nap noted with the time it ended at "now": it started [minutes] earlier. */
    fun endingAt(id: String, now: LocalDateTime, minutes: Int) = Nap(id, now.minusMinutes(minutes.toLong()).withSecond(0).withNano(0), minutes)
}
