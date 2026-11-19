package com.medipote.app.domain

import com.medipote.app.data.AppState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** What a day of the month shows with its small dots: a glance, never a score. */
data class DayDots(
    val date: LocalDate,
    val inMonth: Boolean,
    /** Doses of the day: none planned, all taken, some taken, or none taken (a past day). */
    val doses: DoseDots,
    /** Mood of the day, 1 to 5, if noted. */
    val mood: Int?,
    val symptoms: Boolean,
    val period: Boolean,
)

enum class DoseDots { NONE, ALL, SOME, NOT_NOTED }

object MonthCalendar {
    /** The weeks of [month], Monday first, padded with the days of the neighbouring months. */
    fun weeks(month: YearMonth): List<List<LocalDate>> {
        val first = month.atDay(1)
        val start = first.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= month.atEndOfMonth() || it.dayOfWeek != DayOfWeek.MONDAY }.toList()
        return days.chunked(7)
    }

    fun dots(state: AppState, date: LocalDate, month: YearMonth, today: LocalDate): DayDots {
        val logs = state.doseLogs.associateBy { it.key }
        val future = date.isAfter(today)
        val items = if (future) emptyList() else Schedule.itemsOn(state.treatments, logs, date, date.atTime(23, 59)).filter { !it.occurrence.treatment.asNeeded }
        val taken = items.count { it.state == DoseState.TAKEN }
        val doses = when {
            items.isEmpty() -> DoseDots.NONE
            taken == items.size -> DoseDots.ALL
            taken > 0 -> DoseDots.SOME
            // Today is not over: nothing is « not noted » yet.
            date == today -> DoseDots.NONE
            else -> DoseDots.NOT_NOTED
        }
        val entry = state.entryFor(date)
        return DayDots(
            date = date,
            inMonth = YearMonth.from(date) == month,
            doses = doses,
            mood = entry.mental.takeIf { it in 1..5 && !future },
            symptoms = !future && (Wellbeing.notesOn(state, date).isNotEmpty() || entry.physicalSymptoms.isNotEmpty() || entry.mentalSymptoms.isNotEmpty()),
            period = entry.flow != null,
        )
    }

    fun canGoNext(month: YearMonth, today: LocalDate) = month.isBefore(YearMonth.from(today))
}
