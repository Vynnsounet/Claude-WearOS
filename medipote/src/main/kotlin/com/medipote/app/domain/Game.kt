package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.DoseLog
import com.medipote.app.data.GameState
import com.medipote.app.data.PointEvent
import com.medipote.app.data.TakeStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

enum class PetMood { HAPPY, CONTENT, HUNGRY, UNWELL, SICK, SLEEPING }

data class PetCondition(
    val health: Int,
    val mood: PetMood,
    val dueCount: Int,
    val overdueCount: Int,
    val message: String,
)

data class CheckInReward(val streak: Int, val points: Int, val milestoneBonus: Int) {
    val total: Int get() = points + milestoneBonus
}

data class GiveResult(val state: AppState, val log: DoseLog, val perfectDayBonus: Int)

private const val HISTORY_LIMIT = 300

object Game {

    fun addPoints(game: GameState, amount: Int, reason: String, at: LocalDateTime): GameState = game.copy(
        points = (game.points + amount).coerceAtLeast(0),
        totalEarned = game.totalEarned + amount.coerceAtLeast(0),
        history = (listOf(PointEvent(at, amount, reason)) + game.history).take(HISTORY_LIMIT),
    )

    /**
     * Daily login streak. The first opening of each day gives +5 points per consecutive
     * day (capped at +50), plus a bonus on milestone days. Returns null if already counted today.
     */
    fun checkIn(game: GameState, now: LocalDateTime): Pair<GameState, CheckInReward>? {
        val today = now.toLocalDate()
        if (game.lastCheckIn == today) return null
        val streak = if (game.lastCheckIn == today.minusDays(1)) game.streak + 1 else 1
        val reward = CheckInReward(streak, Rules.streakReward(streak), Rules.STREAK_MILESTONES[streak] ?: 0)
        var updated = game.copy(streak = streak, bestStreak = maxOf(game.bestStreak, streak), lastCheckIn = today)
        updated = addPoints(updated, reward.points, "Connexion quotidienne (série de $streak j)", now)
        if (reward.milestoneBonus > 0) {
            updated = addPoints(updated, reward.milestoneBonus, "Palier de série : $streak jours", now)
        }
        return updated to reward
    }

    /** Records that the dose was given to the pet at [now] and awards points. */
    fun give(state: AppState, item: DoseItem, now: LocalDateTime): GiveResult? {
        if (!item.canGive) return null
        val occ = item.occurrence
        val status = Rules.statusFor(occ.scheduled, now)
        val points = Rules.pointsFor(status)
        val log = DoseLog(occ.key, occ.treatment.id, occ.scheduled, now, status, points)
        val label = when (status) {
            TakeStatus.ON_TIME -> "à l'heure"
            TakeStatus.LATE -> "en retard"
            TakeStatus.VERY_LATE -> "très en retard"
        }
        var game = addPoints(state.game, points, "${occ.treatment.displayName} $label", now)
        val logs = state.doseLogs + log

        // Perfect-day bonus: every dose of that day given on time.
        var bonus = 0
        val day = occ.date
        if (day !in game.perfectDays) {
            val byKey = logs.associateBy { it.key }
            val dayDoses = Schedule.occurrencesOn(state.treatments, day)
            val allOnTime = dayDoses.isNotEmpty() && dayDoses.all { byKey[it.key]?.status == TakeStatus.ON_TIME }
            if (allOnTime) {
                bonus = Rules.PERFECT_DAY_BONUS
                game = addPoints(game, bonus, "Journée parfaite", now).copy(perfectDays = game.perfectDays + day)
            }
        }
        return GiveResult(state.copy(doseLogs = logs, game = game), log, bonus)
    }

    /** The pet's health reflects the last two days of doses and anything overdue right now. */
    fun petCondition(state: AppState, now: LocalDateTime): PetCondition {
        val logs = state.doseLogs.associateBy { it.key }
        val today = now.toLocalDate()
        val name = state.game.petName
        var health = 100
        var due = 0
        var overdue = 0
        var overdueLong = 0
        var dueName: String? = null
        for (offset in 2 downTo 0) {
            val date = today.minusDays(offset.toLong())
            for (item in Schedule.itemsOn(state.treatments, logs, date, now)) {
                when (item.state) {
                    DoseState.MISSED -> health -= 12
                    DoseState.OVERDUE -> {
                        overdue++
                        val late = Duration.between(item.occurrence.scheduled, now).toMinutes()
                        if (late > Rules.LATE_MINUTES) {
                            overdueLong++
                            health -= 30
                        } else {
                            health -= 15
                        }
                        dueName = dueName ?: item.occurrence.treatment.name
                    }
                    DoseState.DUE -> {
                        due++
                        dueName = dueName ?: item.occurrence.treatment.name
                    }
                    DoseState.TAKEN -> when (item.log?.status) {
                        TakeStatus.LATE -> health -= 1
                        TakeStatus.VERY_LATE -> health -= 3
                        else -> Unit
                    }
                    DoseState.UPCOMING -> Unit
                }
            }
        }
        health = health.coerceIn(5, 100)
        val hour = now.hour
        val mood = when {
            overdueLong > 0 || health < 40 -> PetMood.SICK
            overdue > 0 || health < 70 -> PetMood.UNWELL
            due > 0 -> PetMood.HUNGRY
            hour >= 23 || hour < 6 -> PetMood.SLEEPING
            health >= 90 -> PetMood.HAPPY
            else -> PetMood.CONTENT
        }
        val message = when (mood) {
            PetMood.SICK -> "$name se sent très mal… $dueName se fait attendre depuis longtemps !".takeIf { dueName != null }
                ?: "$name est encore fragile après les prises oubliées. Prends-en soin !"
            PetMood.UNWELL -> if (dueName != null) "$name commence à se sentir mal : $dueName est en retard."
            else "$name se remet doucement. Les prochaines prises à l'heure vont l'aider."
            PetMood.HUNGRY -> "C'est l'heure ! $name attend $dueName."
            PetMood.SLEEPING -> "$name dort paisiblement. Chut…"
            PetMood.HAPPY -> "$name est en pleine forme !"
            PetMood.CONTENT -> "$name va bien."
        }
        return PetCondition(health, mood, due, overdue, message)
    }

    fun hasCheckedInToday(game: GameState, today: LocalDate) = game.lastCheckIn == today
}
