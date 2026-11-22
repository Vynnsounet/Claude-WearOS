package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.DoseChange
import com.medipote.app.data.LabResult
import com.medipote.app.data.Sex
import java.time.LocalDate
import java.time.LocalDateTime

/** An analysis usually asked for, with its usual reference range. The range on the person's own report always prevails. */
data class LabMarker(
    val id: String,
    val emoji: String,
    val name: String,
    val unit: String,
    val low: Double?,
    val high: Double?,
    val hint: String,
    val decimals: Int = 1,
)

object Labs {
    enum class Level { LOW, NORMAL, HIGH, UNKNOWN }

    val MARKERS = listOf(
        LabMarker("tsh", "🦋", "TSH", "mUI/L", 0.4, 4.0, "L'hormone qui commande la thyroïde.", 2),
        LabMarker("ft4", "🦋", "T4 libre", "pmol/L", 12.0, 22.0, "L'hormone produite par la thyroïde.", 1),
        LabMarker("ferritin", "🩸", "Ferritine", "µg/L", 15.0, 150.0, "Les réserves de fer.", 0),
        LabMarker("vitd", "☀️", "Vitamine D", "ng/mL", 30.0, 100.0, "Le taux de 25-OH vitamine D.", 0),
        LabMarker("b12", "🧬", "Vitamine B12", "pg/mL", 200.0, 900.0, "", 0),
        LabMarker("hb", "🩸", "Hémoglobine", "g/dL", 12.0, 16.0, "Dépend du sexe ; ton compte rendu donne tes repères.", 1),
        LabMarker("hba1c", "🍬", "HbA1c", "%", 4.0, 5.6, "La glycémie moyenne des trois derniers mois ; la cible diffère avec un diabète.", 1),
        LabMarker("glucose", "🍬", "Glycémie à jeun", "g/L", 0.7, 1.1, "", 2),
        LabMarker("crp", "🔥", "CRP", "mg/L", 0.0, 5.0, "Un marqueur d'inflammation.", 1),
        LabMarker("ldl", "🫀", "LDL-cholestérol", "g/L", 0.0, 1.6, "La cible dépend de ton risque cardiovasculaire.", 2),
    )

    fun marker(id: String): LabMarker? = MARKERS.firstOrNull { it.id == id }
    fun name(marker: String) = marker(marker)?.name ?: marker
    fun emoji(marker: String) = marker(marker)?.emoji ?: "🧪"
    fun unit(marker: String) = marker(marker)?.unit.orEmpty()

    fun format(value: Double, marker: String): String =
        "%.${marker(marker)?.decimals ?: 1}f".format(java.util.Locale.FRANCE, value).let { if ('.' in it || ',' in it) it.trimEnd('0').trimEnd(',', '.') else it }.ifEmpty { "0" }

    /** The range used for a result: the one typed from the report, else the usual one (the hemoglobin one follows the sex). */
    fun range(r: LabResult, sex: Sex?): Pair<Double?, Double?> {
        if (r.low != null || r.high != null) return r.low to r.high
        val m = marker(r.marker) ?: return null to null
        if (m.id == "hb") return if (sex == Sex.MALE) 13.0 to 17.0 else 12.0 to 16.0
        return m.low to m.high
    }

    fun level(r: LabResult, sex: Sex?): Level {
        val (low, high) = range(r, sex)
        if (low == null && high == null) return Level.UNKNOWN
        return when {
            low != null && r.value < low -> Level.LOW
            high != null && r.value > high -> Level.HIGH
            else -> Level.NORMAL
        }
    }

    fun levelText(level: Level) = when (level) {
        Level.LOW -> "Sous les repères"
        Level.HIGH -> "Au-dessus des repères"
        Level.NORMAL -> "Dans les repères"
        Level.UNKNOWN -> "Repères non renseignés"
    }

    /** Oldest first. */
    fun series(state: AppState, marker: String): List<LabResult> = state.labs.filter { it.marker == marker }.sortedBy { it.date }

    /** The markers with at least one result, the usual ones first, then the person's own. */
    fun tracked(state: AppState): List<String> {
        val present = state.labs.map { it.marker }.distinct()
        return MARKERS.map { it.id }.filter { it in present } + present.filter { marker(it) == null }.sorted()
    }

    fun latest(state: AppState, marker: String): LabResult? = series(state, marker).lastOrNull()

    /** « En hausse depuis le 12/05 (1,8 → 2,4) », or null with fewer than two results. */
    fun trend(series: List<LabResult>): String? {
        if (series.size < 2) return null
        val a = series[series.size - 2]
        val b = series.last()
        val dir = when {
            b.value > a.value * 1.02 -> "en hausse"
            b.value < a.value * 0.98 -> "en baisse"
            else -> "stable"
        }
        val day = a.date.let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) }
        return "$dir depuis le $day (${format(a.value, a.marker)} → ${format(b.value, b.marker)})"
    }

    /** The changes of treatments between two dates, to read a curve against them. */
    fun doseChanges(state: AppState, from: LocalDate, to: LocalDate): List<DoseChange> =
        state.treatments.flatMap { it.changes }.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }

    fun save(state: AppState, result: LabResult): AppState =
        state.copy(labs = (state.labs.filterNot { it.id == result.id } + result).sortedWith(compareByDescending<LabResult> { it.date }.thenBy { it.marker }))

    fun delete(state: AppState, id: String): AppState = state.copy(labs = state.labs.filterNot { it.id == id })

    fun newId(now: LocalDateTime, n: Int) = "lab-${now.toString().replace(Regex("[^0-9]"), "")}-$n"
}
