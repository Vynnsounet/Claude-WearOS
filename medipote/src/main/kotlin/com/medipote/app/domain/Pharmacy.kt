package com.medipote.app.domain

import com.medipote.app.data.AppState
import java.time.LocalDate

/** One line of the list to take to the pharmacy. */
data class PharmacyItem(val emoji: String, val text: String)

/** What to get at the pharmacy soon: treatments running low and prescriptions to renew. Nothing to type. */
object Pharmacy {
    fun list(state: AppState, today: LocalDate): List<PharmacyItem> = buildList {
        state.treatments.filter { it.isLowStock }.forEach { t ->
            val left = t.daysLeft?.let { "il reste environ $it jour${if (it > 1) "s" else ""}" } ?: t.stock?.let { "il reste ${t.unit.format(it)}" }
            add(PharmacyItem("💊", t.displayName + (left?.let { " : $it" } ?: "")))
        }
        Renewals.due(state, today).forEach { d ->
            add(PharmacyItem("📄", "Ordonnance à renouveler : ${d.title} (${Renewals.label(d, today)?.lowercase() ?: ""})".trimEnd(' ', '(', ')')))
        }
    }

    /** Plain text to share, one line each. */
    fun shareText(items: List<PharmacyItem>): String =
        (listOf("À la pharmacie :") + items.map { "• ${it.text}" }).joinToString("\n")
}
