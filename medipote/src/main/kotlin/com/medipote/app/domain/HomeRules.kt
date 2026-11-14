package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** What the home screen shows, and when: each block only when it's useful. */
object HomeRules {
    /** The dose to put forward: the one to take now, or else the next one today. */
    fun nextDose(items: List<DoseItem>, now: LocalDateTime): DoseItem? =
        items.filter { it.canGive }.minByOrNull { it.occurrence.scheduled }
            ?: items.filter { it.state == DoseState.UPCOMING && it.occurrence.date == now.toLocalDate() }.minByOrNull { it.occurrence.scheduled }

    /** The weekly recap waits on Sunday evening and Monday. */
    fun recapTime(now: LocalDateTime): Boolean =
        (now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18) || now.dayOfWeek == DayOfWeek.MONDAY

    const val APPOINTMENT_DAYS = 3L

    fun nextAppointment(state: AppState, now: LocalDateTime): Appointment? = state.appointments
        .filter { !it.dateTime.isBefore(now.minusHours(1)) && it.dateTime.isBefore(now.toLocalDate().plusDays(APPOINTMENT_DAYS + 1).atStartOfDay()) }
        .minByOrNull { it.dateTime }

    /** Water only for people who follow it: reminders on, or a glass noted today. */
    fun showWater(state: AppState, date: LocalDate): Boolean =
        state.settings.water.enabled || state.entryFor(date).waterGlasses > 0

    /** As-needed medications taken in the last 24 hours: their timing matters for the next one. */
    fun recentPrn(state: AppState, now: LocalDateTime) = state.treatments.filter { t ->
        t.asNeeded && state.prnIntakes.any { it.treatmentId == t.id && it.takenAt.isAfter(now.minusHours(24)) && !it.takenAt.isAfter(now) }
    }

    fun sleepPrompt(state: AppState, now: LocalDateTime) = now.hour in 5..13 && state.sleep.none { it.date == now.toLocalDate() }

    fun journalPrompt(state: AppState, now: LocalDateTime) = now.hour >= 17 && !state.entryFor(now.toLocalDate()).hasJournal
}
