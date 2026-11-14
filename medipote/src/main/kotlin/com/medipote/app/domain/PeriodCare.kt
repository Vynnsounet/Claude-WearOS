package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.PeriodProduct
import com.medipote.app.data.ProductSession
import com.medipote.app.data.ProductUse
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

enum class WearLevel { OK, CHANGE, OVERDUE }

/** Timer for period products: when to change a pad, tampon, cup, disc or period underwear. */
object PeriodCare {
    /** Common medications for painful periods, from the drug guide. */
    val COMMON_MEDS = listOf("paracetamol", "ibuprofene", "phloroglucinol")
    const val REASON = "Règles douloureuses"
    const val CONTEXT = "Règles"

    fun reminderFor(state: AppState, product: PeriodProduct): Int =
        (state.settings.productReminders[product] ?: product.defaultMinutes).coerceIn(30, product.maxMinutes)

    fun start(state: AppState, product: PeriodProduct, now: LocalDateTime): AppState {
        val closed = close(state, now)
        return closed.copy(productSession = ProductSession(product, now, reminderFor(state, product)))
    }

    /** Changed for a new one of the same kind. */
    fun change(state: AppState, now: LocalDateTime): AppState {
        val current = state.productSession ?: return state
        return start(state, current.product, now)
    }

    /** Removed, nothing worn now. */
    fun stop(state: AppState, now: LocalDateTime): AppState = close(state, now).copy(productSession = null)

    fun setReminder(state: AppState, product: PeriodProduct, minutes: Int): AppState {
        val m = minutes.coerceIn(30, product.maxMinutes)
        val session = state.productSession?.let { if (it.product == product) it.copy(remindMinutes = m) else it }
        return state.copy(settings = state.settings.copy(productReminders = state.settings.productReminders + (product to m)), productSession = session)
    }

    private fun close(state: AppState, now: LocalDateTime): AppState {
        val s = state.productSession ?: return state
        val use = ProductUse(s.product, s.since, maxOf(now, s.since))
        val cutoff = now.minusDays(120)
        return state.copy(productLog = (listOf(use) + state.productLog).filter { it.end.isAfter(cutoff) })
    }

    fun elapsed(s: ProductSession, now: LocalDateTime): Duration = Duration.between(s.since, now).let { if (it.isNegative) Duration.ZERO else it }

    fun level(s: ProductSession, now: LocalDateTime): WearLevel {
        val minutes = elapsed(s, now).toMinutes()
        return when {
            minutes >= s.product.maxMinutes -> WearLevel.OVERDUE
            minutes >= s.remindMinutes -> WearLevel.CHANGE
            else -> WearLevel.OK
        }
    }

    fun changeAt(s: ProductSession): LocalDateTime = s.since.plusMinutes(s.remindMinutes.toLong())
    fun maxAt(s: ProductSession): LocalDateTime = s.since.plusMinutes(s.product.maxMinutes.toLong())

    /** Products put on that day (finished ones and the current one). */
    fun usesOn(state: AppState, date: LocalDate): Int =
        state.productLog.count { it.start.toLocalDate() == date } + (if (state.productSession?.since?.toLocalDate() == date) 1 else 0)

    /** Intakes of as-needed medications on a given day, linked to periods or not. */
    fun intakesOn(state: AppState, date: LocalDate) = state.prnIntakes.filter { it.takenAt.toLocalDate() == date }.sortedBy { it.takenAt }
}
