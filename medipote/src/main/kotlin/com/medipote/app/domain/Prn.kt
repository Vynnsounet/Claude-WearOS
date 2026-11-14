package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.PrnIntake
import com.medipote.app.data.Treatment
import java.time.Duration
import java.time.LocalDateTime

/** "As needed" (si besoin) medications: logging intakes, safety warnings, stock. */
object Prn {
    /** An hour after an intake, the app can ask whether it helped. */
    const val RELIEF_CHECK_MINUTES = 60L

    val REASONS = listOf(
        "Douleur", "Mal de tête", "Migraine", "Fièvre", "Règles douloureuses", "Mal de dos",
        "Allergie", "Anxiété", "Insomnie", "Nausées", "Brûlures d'estomac", "Toux",
    )
    val CONTEXTS = listOf(
        "À la maison", "Au travail", "En déplacement", "Après un repas", "À jeun", "Stress", "Fatigue",
        "Manque de sommeil", "Sport", "Écrans", "Règles", "Alcool", "Changement de météo",
    )
    val RELIEF_LABELS = listOf("Aucun effet", "Un peu", "Moyen", "Bien", "Très bien")

    fun intakesOf(state: AppState, treatmentId: String): List<PrnIntake> =
        state.prnIntakes.filter { it.treatmentId == treatmentId }.sortedByDescending { it.takenAt }

    fun last(state: AppState, treatmentId: String, before: LocalDateTime? = null): PrnIntake? =
        intakesOf(state, treatmentId).firstOrNull { before == null || !it.takenAt.isAfter(before) }

    /**
     * Gentle safety checks against the limits the user entered (from the prescription or the leaflet).
     * They never block: the intake may already have happened.
     */
    fun warnings(state: AppState, t: Treatment, at: LocalDateTime, excludeId: String? = null): List<String> {
        val others = intakesOf(state, t.id).filter { it.id != excludeId }
        val warnings = mutableListOf<String>()
        t.maxPerDay?.let { max ->
            val in24h = others.count { it.takenAt.isAfter(at.minusHours(24)) && !it.takenAt.isAfter(at) }
            if (in24h + 1 > max) {
                warnings += "Ce serait la ${in24h + 1}e prise en 24 h, alors que le maximum indiqué est de $max."
            }
        }
        t.minIntervalHours?.let { min ->
            others.firstOrNull { !it.takenAt.isAfter(at) }?.let { last ->
                val since = Duration.between(last.takenAt, at)
                if (since.toMinutes() < min * 60L) {
                    warnings += "Seulement ${formatDuration(since)} depuis la prise précédente, alors que l'intervalle minimum indiqué est de $min h."
                }
            }
        }
        return warnings
    }

    /** Adds or updates an intake; the stock follows the change of quantity. */
    fun save(state: AppState, intake: PrnIntake): AppState {
        val previous = state.prnIntakes.firstOrNull { it.id == intake.id }
        val delta = intake.quantity - (previous?.quantity ?: 0.0)
        return state.copy(
            prnIntakes = (state.prnIntakes.filterNot { it.id == intake.id } + intake).sortedByDescending { it.takenAt },
            treatments = adjustStock(state.treatments, intake.treatmentId, -delta),
        )
    }

    /** Removes an intake and gives its quantity back to the stock. */
    fun delete(state: AppState, id: String): AppState {
        val intake = state.prnIntakes.firstOrNull { it.id == id } ?: return state
        return state.copy(
            prnIntakes = state.prnIntakes.filterNot { it.id == id },
            treatments = adjustStock(state.treatments, intake.treatmentId, intake.quantity),
        )
    }

    private fun adjustStock(treatments: List<Treatment>, id: String, delta: Double) = treatments.map { t ->
        if (t.id == id && t.stock != null) t.copy(stock = (t.stock + delta).coerceAtLeast(0.0)) else t
    }

    fun formatDuration(d: Duration): String {
        val minutes = d.toMinutes().coerceAtLeast(0)
        return when {
            minutes < 60 -> "$minutes min"
            minutes < 48 * 60 -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0L) "$h h" else "$h h ${"%02d".format(m)}"
            }
            else -> "${minutes / (24 * 60)} jours"
        }
    }
}
