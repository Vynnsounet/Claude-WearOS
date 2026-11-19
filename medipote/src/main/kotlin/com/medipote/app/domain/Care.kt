package com.medipote.app.domain

import com.medipote.app.data.AppState
import java.time.LocalDate
import java.time.LocalDateTime

/** What a small reward gave. [points] is 0 when it had already been given. */
data class CareReward(val state: AppState, val points: Int)

/**
 * Small, discreet rewards for taking care of oneself beyond the doses: a measure noted, a symptom noted,
 * an appointment prepared or attended, a treatment bought back, and a gift after a week of gentle days.
 * Each is given once; none is ever taken away, and none rewards a symptom being worse or a medication
 * being taken more.
 */
object Care {
    const val PREP_POINTS = 5
    const val ATTENDED_POINTS = 10
    const val MEASURE_POINTS = 2
    const val SYMPTOM_POINTS = 2
    const val RESTOCK_POINTS = 5
    const val RENEWAL_POINTS = 5
    const val GIFT_POINTS = 25
    const val GIFT_DAYS = 7
    private const val KEEP = 600

    /** Gives [points] for [key] once; a key already given pays nothing. */
    fun reward(state: AppState, key: String, points: Int, reason: String, now: LocalDateTime): CareReward {
        if (key in state.game.careRewards) return CareReward(state, 0)
        val game = Game.addPoints(state.game, points, reason, now).copy(careRewards = (state.game.careRewards + key).takeLast(KEEP))
        return CareReward(state.copy(game = game), points)
    }

    private fun day(now: LocalDateTime) = now.toLocalDate()

    /** The first measure of the day. */
    fun measureNoted(state: AppState, now: LocalDateTime) = reward(state, "${day(now)}:measure", MEASURE_POINTS, "Mesure notée", now)

    /** The first symptom noted of the day, whatever its strength: noting is rewarded, never suffering. */
    fun symptomNoted(state: AppState, now: LocalDateTime) = reward(state, "${day(now)}:symptom", SYMPTOM_POINTS, "Symptôme noté", now)

    /** Questions written for an appointment: once per appointment. */
    fun appointmentPrepared(state: AppState, appointmentId: String, now: LocalDateTime) =
        reward(state, "prep:$appointmentId", PREP_POINTS, "Rendez-vous préparé", now)

    fun appointmentAttended(state: AppState, appointmentId: String, now: LocalDateTime) =
        reward(state, "attended:$appointmentId", ATTENDED_POINTS, "Rendez-vous honoré", now)

    /** A treatment that was running low is bought back: once per treatment and day. */
    fun restocked(state: AppState, treatmentId: String, now: LocalDateTime) =
        reward(state, "${day(now)}:restock:$treatmentId", RESTOCK_POINTS, "Traitement racheté", now)

    /** A prescription renewed: once per prescription and end date. */
    fun renewed(state: AppState, docId: String, validUntil: LocalDate?, now: LocalDateTime) =
        reward(state, "renew:$docId:${validUntil ?: ""}", RENEWAL_POINTS, "Ordonnance renouvelée", now)

    // ── The surprise gift ──

    /** Whether [date] had at least one thing done for oneself (never a dose: those have their own points). */
    fun gentleDay(state: AppState, date: LocalDate): Boolean {
        val entry = state.entryFor(date)
        return entry.hasJournal || entry.mental != null || entry.waterGlasses > 0 ||
            state.symptomLog.any { it.date == date } || state.wins.any { it.date == date } ||
            state.sleep.any { it.date == date } || state.measurements.any { it.at.toLocalDate() == date } ||
            state.game.ritualsDone.any { it.startsWith(date.toString()) }
    }

    /** Days in a row, up to [date] (or the day before when today is still empty), with something done for oneself. */
    fun gentleStreak(state: AppState, date: LocalDate): Int {
        var d = if (gentleDay(state, date)) date else date.minusDays(1)
        var n = 0
        while (n < 60 && gentleDay(state, d)) { n++; d = d.minusDays(1) }
        return n
    }

    /** A gift after seven gentle days in a row, then again after seven more. */
    fun gift(state: AppState, now: LocalDateTime): CareReward {
        val today = day(now)
        val last = state.game.giftOn
        if (last != null && last.plusDays(GIFT_DAYS.toLong()).isAfter(today)) return CareReward(state, 0)
        if (gentleStreak(state, today) < GIFT_DAYS) return CareReward(state, 0)
        val game = Game.addPoints(state.game, GIFT_POINTS, "Cadeau-surprise", now).copy(giftOn = today)
        return CareReward(state.copy(game = game), GIFT_POINTS)
    }
}
