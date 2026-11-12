package com.medipote.app.domain

import com.medipote.app.data.CycleInfo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CyclePhase(val label: String, val description: String) {
    MENSTRUAL("Règles", "Phase menstruelle : le cycle recommence."),
    FOLLICULAR("Phase folliculaire", "L'énergie remonte souvent pendant cette phase."),
    OVULATION("Ovulation (estimée)", "Fenêtre de fertilité estimée."),
    LUTEAL("Phase lutéale", "Des symptômes prémenstruels peuvent apparaître en fin de phase."),
}

enum class CycleDayType { PERIOD, PREDICTED_PERIOD, FERTILE, OVULATION, NONE }

data class CycleStatus(
    val cycleStart: LocalDate,
    val dayOfCycle: Int,
    val cycleLength: Int,
    val phase: CyclePhase,
    val nextPeriod: LocalDate,
    val daysUntilNextPeriod: Long,
    val ovulation: LocalDate,
    val fertileStart: LocalDate,
    val fertileEnd: LocalDate,
    /** Days the expected period is overdue (0 when not late). */
    val lateDays: Long,
)

/**
 * Simple calendar-method estimates: ovulation ~14 days before the next period,
 * fertile window from 5 days before to 1 day after it. Indicative only.
 */
object Cycle {
    private const val LUTEAL_DAYS = 14L

    /** Average of the recent cycle lengths the user logged, or their declared length. */
    fun averageLength(info: CycleInfo): Int {
        val diffs = info.periodStarts.sorted().zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
            .filter { it in 20..45 }
            .takeLast(6)
        return if (diffs.isEmpty()) info.cycleLength else diffs.average().let { Math.round(it).toInt() }
    }

    fun status(info: CycleInfo, today: LocalDate): CycleStatus? {
        val last = info.periodStarts.maxOrNull() ?: return null
        val length = averageLength(info)
        val sinceLast = ChronoUnit.DAYS.between(last, today)
        if (sinceLast < 0) return null
        // If the period is more than a week overdue, assume cycles went unlogged and roll forward.
        var start = last
        var lateDays = 0L
        if (sinceLast >= length) {
            val overdue = sinceLast - length
            if (overdue <= 7) {
                lateDays = overdue + 1
            } else {
                start = last.plusDays((sinceLast / length) * length)
            }
        }
        val day = ChronoUnit.DAYS.between(start, today).toInt() + 1
        val next = if (lateDays > 0) today else start.plusDays(length.toLong())
        val ovulation = start.plusDays(length - LUTEAL_DAYS)
        val fertileStart = ovulation.minusDays(5)
        val fertileEnd = ovulation.plusDays(1)
        val phase = when {
            lateDays > 0 -> CyclePhase.LUTEAL
            day <= info.periodLength -> CyclePhase.MENSTRUAL
            !today.isBefore(fertileStart) && !today.isAfter(fertileEnd) -> CyclePhase.OVULATION
            today.isBefore(fertileStart) -> CyclePhase.FOLLICULAR
            else -> CyclePhase.LUTEAL
        }
        return CycleStatus(
            cycleStart = start,
            dayOfCycle = day,
            cycleLength = length,
            phase = phase,
            nextPeriod = next,
            daysUntilNextPeriod = ChronoUnit.DAYS.between(today, next),
            ovulation = ovulation,
            fertileStart = fertileStart,
            fertileEnd = fertileEnd,
            lateDays = lateDays,
        )
    }

    /** What to show on a calendar day: logged periods, then predictions for the next cycles. */
    fun dayType(info: CycleInfo, date: LocalDate, today: LocalDate): CycleDayType {
        val starts = info.periodStarts.sorted()
        if (starts.isEmpty()) return CycleDayType.NONE
        for (s in starts) {
            val offset = ChronoUnit.DAYS.between(s, date)
            if (offset in 0 until info.periodLength) {
                // Logged period, but don't paint future days of a period that just started as "logged".
                return if (date.isAfter(today)) CycleDayType.PREDICTED_PERIOD else CycleDayType.PERIOD
            }
        }
        val length = averageLength(info).toLong()
        val last = starts.last()
        val sinceLast = ChronoUnit.DAYS.between(last, date)
        if (sinceLast < 0) return CycleDayType.NONE
        val k = sinceLast / length
        val cycleStart = last.plusDays(k * length)
        val offset = ChronoUnit.DAYS.between(cycleStart, date)
        if (k >= 1 && offset < info.periodLength && !date.isBefore(today)) return CycleDayType.PREDICTED_PERIOD
        val ovulationOffset = length - LUTEAL_DAYS
        return when (offset) {
            ovulationOffset -> CycleDayType.OVULATION
            in (ovulationOffset - 5)..(ovulationOffset + 1) -> CycleDayType.FERTILE
            else -> CycleDayType.NONE
        }
    }

    fun logPeriod(info: CycleInfo, start: LocalDate): CycleInfo {
        // Replace an entry within a week of the new one (a correction), otherwise add it.
        val others = info.periodStarts.filter { kotlin.math.abs(ChronoUnit.DAYS.between(it, start)) > 7 }
        return info.copy(periodStarts = (others + start).sorted().takeLast(24))
    }
}
