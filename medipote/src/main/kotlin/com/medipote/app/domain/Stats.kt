package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.TakeStatus
import java.time.LocalDate
import java.time.LocalDateTime

/** [late] counts doses taken off time, late or early. */
data class DayStats(val date: LocalDate, val onTime: Int, val late: Int, val missed: Int, val pending: Int) {
    val planned: Int get() = onTime + late + missed + pending
    val taken: Int get() = onTime + late
}

data class TreatmentStats(val name: String, val taken: Int, val planned: Int) {
    val adherence: Float get() = if (planned == 0) 1f else taken.toFloat() / planned
}

data class Summary(
    val adherence7: Float?,
    val adherence30: Float?,
    /** Share of given doses that were on time over 30 days. */
    val punctuality30: Float?,
    val onTime30: Int,
    val late30: Int,
    val missed30: Int,
)

object Stats {

    /** Counts per day; doses still giveable (or not yet due) count as pending, not missed. */
    fun daily(state: AppState, now: LocalDateTime, days: Int): List<DayStats> {
        val logs = state.doseLogs.associateBy { it.key }
        val today = now.toLocalDate()
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            var onTime = 0
            var late = 0
            var missed = 0
            var pending = 0
            for (item in Schedule.itemsOn(state.treatments, logs, date, now)) {
                when (item.state) {
                    DoseState.TAKEN -> if (item.log?.status == TakeStatus.ON_TIME) onTime++ else late++
                    DoseState.MISSED -> missed++
                    else -> pending++
                }
            }
            DayStats(date, onTime, late, missed, pending)
        }
    }

    fun summary(state: AppState, now: LocalDateTime): Summary {
        val d30 = daily(state, now, 30)
        val d7 = d30.takeLast(7)
        fun adherence(days: List<DayStats>): Float? {
            val decided = days.sumOf { it.taken + it.missed }
            return if (decided == 0) null else days.sumOf { it.taken }.toFloat() / decided
        }
        val onTime = d30.sumOf { it.onTime }
        val late = d30.sumOf { it.late }
        return Summary(
            adherence7 = adherence(d7),
            adherence30 = adherence(d30),
            punctuality30 = if (onTime + late == 0) null else onTime.toFloat() / (onTime + late),
            onTime30 = onTime,
            late30 = late,
            missed30 = d30.sumOf { it.missed },
        )
    }

    fun perTreatment(state: AppState, now: LocalDateTime, days: Int = 30): List<TreatmentStats> {
        val logs = state.doseLogs.associateBy { it.key }
        val today = now.toLocalDate()
        return state.treatments.map { t ->
            var taken = 0
            var planned = 0
            for (offset in 0 until days) {
                val date = today.minusDays(offset.toLong())
                for (item in Schedule.itemsOn(listOf(t), logs, date, now)) {
                    when (item.state) {
                        DoseState.TAKEN -> { taken++; planned++ }
                        DoseState.MISSED -> planned++
                        else -> Unit
                    }
                }
            }
            TreatmentStats(t.displayName, taken, planned)
        }
    }
}
