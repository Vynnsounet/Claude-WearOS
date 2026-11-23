package com.medipote.app.domain

import com.medipote.app.data.ReminderPause
import com.medipote.app.notifications.AlarmKind
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Putting the reminders to sleep for a while, without ever touching what could matter for health by accident. */
object Pause {
    enum class Length(val label: String) { TOMORROW("Jusqu'à demain matin"), THREE_DAYS("3 jours"), WEEK("1 semaine") }

    /** What a pause silences: the soft reminders. The doses join only on purpose. */
    private val GENTLE = setOf(
        AlarmKind.WATER, AlarmKind.STOCK, AlarmKind.SLEEP, AlarmKind.WIND_DOWN, AlarmKind.RENEWAL,
        AlarmKind.RITUAL, AlarmKind.RECAP, AlarmKind.PRN_CHECK, AlarmKind.CHECKUP,
    )
    private val DOSES = setOf(AlarmKind.DOSE, AlarmKind.FOLLOW_UP, AlarmKind.SNOOZE, AlarmKind.WINDOW_END)

    fun until(length: Length, now: LocalDateTime): LocalDateTime = when (length) {
        Length.TOMORROW -> now.toLocalDate().plusDays(1).atTime(8, 0)
        Length.THREE_DAYS -> now.plusDays(3).withSecond(0).withNano(0)
        Length.WEEK -> now.plusWeeks(1).withSecond(0).withNano(0)
    }

    fun start(length: Length, doses: Boolean, now: LocalDateTime) = ReminderPause(until(length, now), doses)

    fun active(pause: ReminderPause, now: LocalDateTime): Boolean = pause.until?.isAfter(now) == true

    /** Whether a reminder of [kind] stays quiet during the pause. Appointments and period products never do. */
    fun silences(pause: ReminderPause, kind: AlarmKind): Boolean = kind in GENTLE || (pause.doses && kind in DOSES)

    private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' H 'h'", Locale.FRENCH)

    fun label(pause: ReminderPause): String = pause.until?.let { "En pause jusqu'à ${it.format(DAY)}" }.orEmpty()
}
