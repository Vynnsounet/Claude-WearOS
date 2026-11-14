package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.DocCategory
import com.medipote.app.data.HealthDocument
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Prescriptions to renew before they run out. */
object Renewals {
    /** How long an expired prescription keeps being shown. */
    private const val SHOW_AFTER_DAYS = 14L

    fun reminderDate(doc: HealthDocument): LocalDate? {
        if (doc.category != DocCategory.PRESCRIPTION) return null
        val until = doc.validUntil ?: return null
        val days = doc.remindDays ?: return null
        return until.minusDays(days.toLong())
    }

    /** Prescriptions that run out soon (or just did), soonest first. */
    fun due(state: AppState, today: LocalDate): List<HealthDocument> = state.documents
        .filter { it.category == DocCategory.PRESCRIPTION }
        .filter { d ->
            val from = reminderDate(d) ?: return@filter false
            !today.isBefore(from) && !today.isAfter(d.validUntil!!.plusDays(SHOW_AFTER_DAYS))
        }
        .sortedBy { it.validUntil }

    fun daysLeft(doc: HealthDocument, today: LocalDate): Long? = doc.validUntil?.let { ChronoUnit.DAYS.between(today, it) }

    fun label(doc: HealthDocument, today: LocalDate): String? {
        val left = daysLeft(doc, today) ?: return null
        return when {
            left < 0 -> "Expirée depuis ${-left} jour${if (left < -1) "s" else ""}"
            left == 0L -> "Valable jusqu'à aujourd'hui"
            left == 1L -> "Valable jusqu'à demain"
            else -> "Valable encore $left jours"
        }
    }
}
