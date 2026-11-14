package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.MeasureType
import com.medipote.app.data.Measurement
import com.medipote.app.data.Profile
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Level { LOW, NORMAL, HIGH }

/** Where a value stands against common reference values. Informative only, never a diagnosis. */
data class Assessment(val level: Level, val text: String)

data class MeasureReport(
    val type: MeasureType,
    val days: Int,
    /** Oldest first. */
    val values: List<Measurement>,
    val average: Double?,
    val average2: Double?,
    val averagePulse: Int?,
    val min: Measurement?,
    val max: Measurement?,
    /** Share of measurements within the reference values. */
    val inRange: Float?,
    /** Last minus first value of the period (weight). */
    val change: Double?,
    val insights: List<Insight>,
) {
    val count: Int get() = values.size
}

object Measures {
    private val FR = Locale.FRANCE
    val GLUCOSE_CONTEXTS = listOf("À jeun", "Avant un repas", "2 h après un repas", "Au coucher")

    fun dec(v: Double, d: Int = 1) = "%.${d}f".format(FR, v)

    fun assess(m: Measurement, profile: Profile? = null): Assessment? = when (m.type) {
        MeasureType.BLOOD_PRESSURE -> {
            val sys = m.value
            val dia = m.value2 ?: 0.0
            when {
                sys >= 180 || dia >= 110 -> Assessment(Level.HIGH, "Très élevée : refais une mesure au calme et appelle ton médecin si elle reste à ce niveau.")
                sys >= 135 || dia >= 85 -> Assessment(Level.HIGH, "Au-dessus des repères pour une mesure à domicile (135/85).")
                sys < 90 || (m.value2 != null && dia < 60) -> Assessment(Level.LOW, "Basse (sous 90/60).")
                else -> Assessment(Level.NORMAL, "Dans les repères pour une mesure à domicile (sous 135/85).")
            }
        }
        MeasureType.HEART_RATE -> when {
            m.value > 100 -> Assessment(Level.HIGH, "Rapide pour un pouls au repos (plus de 100).")
            m.value < 50 -> Assessment(Level.LOW, "Lent (moins de 50), ce qui peut être normal chez une personne sportive.")
            else -> Assessment(Level.NORMAL, "Dans les repères au repos (50 à 100).")
        }
        MeasureType.GLUCOSE -> {
            val v = m.value
            val (limit, label) = when (m.context) {
                "À jeun", "Avant un repas" -> 1.10 to "à jeun ou avant un repas (0,70 à 1,10 g/L)"
                "2 h après un repas" -> 1.40 to "2 h après un repas (sous 1,40 g/L)"
                else -> 1.80 to "hors repas (sous 1,80 g/L)"
            }
            when {
                v < 0.70 -> Assessment(Level.LOW, "Hypoglycémie (sous 0,70 g/L) : resucre-toi et suis les consignes de ton médecin.")
                v > limit -> Assessment(Level.HIGH, "Au-dessus des repères $label.")
                else -> Assessment(Level.NORMAL, "Dans les repères $label.")
            }
        }
        MeasureType.TEMPERATURE -> when {
            m.value >= 38.0 -> Assessment(Level.HIGH, "Fièvre (38 °C ou plus).")
            m.value < 35.5 -> Assessment(Level.LOW, "Basse (sous 35,5 °C) : refais la mesure.")
            else -> Assessment(Level.NORMAL, "Normale.")
        }
        MeasureType.OXYGEN -> when {
            m.value < 92 -> Assessment(Level.LOW, "Basse (sous 92 %) : refais la mesure et contacte un médecin si elle reste basse.")
            m.value < 95 -> Assessment(Level.LOW, "Un peu basse (sous 95 %).")
            else -> Assessment(Level.NORMAL, "Normale (95 % ou plus).")
        }
        // Weight is shown as it is, without a BMI or a verdict: it shouldn't make anyone feel judged.
        MeasureType.WEIGHT -> null
    }

    fun latest(state: AppState, type: MeasureType): Measurement? =
        state.measurements.filter { it.type == type }.maxByOrNull { it.at }

    /** Adds or updates a measurement; a new latest weight also updates the profile. */
    fun save(state: AppState, m: Measurement): AppState {
        val list = (state.measurements.filterNot { it.id == m.id } + m).sortedByDescending { it.at }
        var profile = state.profile
        if (m.type == MeasureType.WEIGHT && list.first { it.type == MeasureType.WEIGHT }.id == m.id) {
            profile = profile?.copy(weightKg = m.value)
        }
        return state.copy(measurements = list, profile = profile)
    }

    fun delete(state: AppState, id: String): AppState = state.copy(measurements = state.measurements.filterNot { it.id == id })

    fun report(state: AppState, type: MeasureType, today: LocalDate, days: Int): MeasureReport {
        val start = today.minusDays(days - 1L)
        val values = state.measurements.filter { it.type == type && !it.at.toLocalDate().isBefore(start) && !it.at.toLocalDate().isAfter(today) }
            .sortedBy { it.at }
        val levels = values.mapNotNull { assess(it, state.profile)?.level }
        val report = MeasureReport(
            type = type,
            days = days,
            values = values,
            average = values.takeIf { it.isNotEmpty() }?.map { it.value }?.average(),
            average2 = values.mapNotNull { it.value2 }.takeIf { it.isNotEmpty() }?.average(),
            averagePulse = values.mapNotNull { it.pulse }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            min = values.minByOrNull { it.value },
            max = values.maxByOrNull { it.value },
            inRange = levels.takeIf { it.isNotEmpty() }?.let { l -> l.count { it == Level.NORMAL }.toFloat() / l.size },
            change = if (values.size >= 2) values.last().value - values.first().value else null,
            insights = emptyList(),
        )
        return report.copy(insights = insights(report, state))
    }

    private fun pct(f: Float) = "${(f * 100).roundToInt()} %"

    private fun insights(r: MeasureReport, state: AppState): List<Insight> {
        val out = mutableListOf<Insight>()
        if (r.count == 0) return out
        val outOfRange = r.values.count { assess(it, state.profile)?.level?.let { l -> l != Level.NORMAL } == true }
        when (r.type) {
            MeasureType.BLOOD_PRESSURE -> {
                val sys = r.average!!.roundToInt()
                val dia = r.average2?.roundToInt()
                out += Insight("Moyenne : $sys/${dia ?: "?"} mmHg sur ${r.count} mesure${if (r.count > 1) "s" else ""}.", Tone.INFO)
                val high = r.values.count { assess(it)?.level == Level.HIGH }
                if (r.count >= 3 && high * 3 >= r.count) {
                    out += Insight("${pct(high.toFloat() / r.count)} des mesures sont au-dessus des repères (135/85) : montre-les à ton médecin.", Tone.WARN)
                } else if (r.count >= 3 && high == 0) {
                    out += Insight("Toutes les mesures sont dans les repères.", Tone.GOOD)
                }
                val morning = r.values.filter { it.at.hour < 12 }
                val evening = r.values.filter { it.at.hour >= 17 }
                if (morning.size >= 2 && evening.size >= 2) {
                    val diff = morning.map { it.value }.average() - evening.map { it.value }.average()
                    if (abs(diff) >= 8) {
                        out += Insight(
                            "La systolique est plus ${if (diff > 0) "haute le matin" else "haute le soir"} (${dec(abs(diff), 0)} mmHg d'écart en moyenne).",
                            Tone.INFO,
                        )
                    }
                }
                if (r.count < 6) out += Insight("Repère courant : 3 mesures le matin et 3 le soir, 3 jours de suite, en position assise et au calme depuis 5 min.", Tone.INFO)
            }
            MeasureType.WEIGHT -> {
                r.change?.let { c ->
                    val days = java.time.Duration.between(r.values.first().at, r.values.last().at).toDays().coerceAtLeast(1)
                    if (abs(c) >= 0.2) {
                        out += Insight("${if (c > 0) "+" else "−"}${dec(abs(c))} kg en $days jour${if (days > 1) "s" else ""}.", Tone.INFO)
                    } else {
                        out += Insight("Poids stable sur la période.", Tone.INFO)
                    }
                }
            }
            MeasureType.GLUCOSE -> {
                val hypo = r.values.count { it.value < 0.70 }
                if (hypo > 0) out += Insight("$hypo hypoglycémie${if (hypo > 1) "s" else ""} (sous 0,70 g/L) sur la période.", Tone.WARN)
                val fasting = r.values.filter { it.context == "À jeun" }
                if (fasting.isNotEmpty()) out += Insight("Moyenne à jeun : ${dec(fasting.map { it.value }.average(), 2)} g/L (${fasting.size} mesure${if (fasting.size > 1) "s" else ""}).", Tone.INFO)
                if (r.count >= 3 && outOfRange - hypo == 0 && hypo == 0) out += Insight("Toutes les mesures sont dans les repères.", Tone.GOOD)
            }
            MeasureType.TEMPERATURE -> {
                val fever = r.values.filter { it.value >= 38.0 }
                if (fever.isNotEmpty()) {
                    val days = fever.map { it.at.toLocalDate() }.distinct().size
                    out += Insight("Fièvre notée $days jour${if (days > 1) "s" else ""} (max ${dec(fever.maxOf { it.value })} °C).", Tone.WARN)
                }
            }
            MeasureType.HEART_RATE -> out += Insight("Pouls moyen : ${r.average!!.roundToInt()} bpm.", Tone.INFO)
            MeasureType.OXYGEN -> {
                val low = r.values.count { it.value < 95 }
                if (low > 0) out += Insight("$low mesure${if (low > 1) "s" else ""} sous 95 %.", Tone.WARN)
            }
        }
        return out.sortedBy { when (it.tone) { Tone.WARN -> 0; Tone.GOOD -> 1; Tone.INFO -> 2 } }
    }
}
