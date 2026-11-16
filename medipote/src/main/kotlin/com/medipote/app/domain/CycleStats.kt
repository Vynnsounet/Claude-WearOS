package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.CycleInfo
import com.medipote.app.data.Flow
import com.medipote.app.data.PeriodProduct
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** One cycle: from the first day of a period to the day before the next one. */
data class CycleRecord(
    val start: LocalDate,
    /** Days until the next period; null for the cycle in progress. */
    val length: Int?,
    /** Days of bleeding noted in the journal from the first day, or null when not noted. */
    val periodDays: Int?,
)

/** How the days of one phase felt, from the journal. */
data class PhaseFigures(
    val phase: CyclePhase,
    /** Days of the window in this phase (estimated from the cycle's length). */
    val days: Int,
    val daysWithMood: Int,
    val averageMood: Double?,
    val averagePhysical: Double?,
    val daysWithSymptoms: Int,
)

data class CycleReport(
    /** The cycles looked at, oldest first (the one in progress last). */
    val records: List<CycleRecord>,
    /** Lengths of the finished cycles, oldest first. */
    val lengths: List<Int>,
    val averageLength: Double?,
    val shortest: Int?,
    val longest: Int?,
    val averagePeriodDays: Double?,
    val flowDays: List<Pair<Flow, Int>>,
    val symptoms: List<Pair<String, Int>>,
    val byPhase: List<PhaseFigures>,
    val products: List<Pair<PeriodProduct, Int>>,
    val periodMedIntakes: Int,
    val insights: List<Insight>,
) {
    /** Gap between the shortest and the longest cycle, in days. */
    val spread: Int? get() = if (shortest != null && longest != null) longest - shortest else null
}

/**
 * Statistics of the cycle: how long, how regular, how heavy, what comes with it, and how the phases
 * feel. Phases are estimates from the calendar method, so everything here is indicative.
 */
object CycleStats {
    /** The last cycles looked at. */
    const val CYCLES = 6

    /** Cycles of 21 to 35 days are the usual range; a longer gap in them is worth mentioning to a doctor. */
    const val USUAL_MIN = 21
    const val USUAL_MAX = 35

    /** A variation up to this many days between cycles is common. */
    const val COMMON_SPREAD = 7

    fun enabled(state: AppState) = state.profile?.menstruating == true

    /** The phase of [date], estimated from the logged period starts; null when no cycle covers it. */
    fun phaseOn(info: CycleInfo, date: LocalDate): CyclePhase? {
        val starts = info.periodStarts.sorted()
        val start = starts.lastOrNull { !it.isAfter(date) } ?: return null
        val next = starts.firstOrNull { it.isAfter(start) }
        val length = next?.let { ChronoUnit.DAYS.between(start, it).toInt() } ?: Cycle.averageLength(info)
        val day = ChronoUnit.DAYS.between(start, date).toInt() + 1
        // A cycle left unlogged for too long says nothing about the days it would have covered.
        if (day > length + 7 || length < 15) return null
        val ovulationDay = length - 14 + 1
        return when {
            day <= info.periodLength -> CyclePhase.MENSTRUAL
            day in (ovulationDay - 5)..(ovulationDay + 1) -> CyclePhase.OVULATION
            day < ovulationDay - 5 -> CyclePhase.FOLLICULAR
            else -> CyclePhase.LUTEAL
        }
    }

    fun report(state: AppState, today: LocalDate): CycleReport? {
        val info = state.profile?.cycle ?: return null
        val starts = info.periodStarts.sorted().filter { !it.isAfter(today) }
        if (starts.isEmpty()) return null
        val window = starts.takeLast(CYCLES + 1)
        val flows = state.journal.filter { it.flow != null }.associate { it.date to it.flow!! }
        val records = window.mapIndexed { i, start ->
            val next = window.getOrNull(i + 1)
            val bleeding = generateSequence(start) { it.plusDays(1) }.takeWhile { it in flows && (next == null || it.isBefore(next)) }.take(14).count()
            CycleRecord(start, next?.let { ChronoUnit.DAYS.between(start, it).toInt() }, bleeding.takeIf { it >= 2 })
        }
        val lengths = records.mapNotNull { it.length }
        val from = window.first()
        val inWindow = state.journal.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }

        // How heavy the bleeding was, day by day.
        val flowDays = Flow.entries.map { f -> f to inWindow.count { it.flow == f } }.filter { it.second > 0 }
        val symptoms = inWindow.flatMap { it.cycleSymptoms }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(5).map { it.key to it.value }

        // How the phases felt: the journal's scores and symptoms by phase of the day.
        val byPhase = CyclePhase.entries.map { phase ->
            val days = inWindow.filter { phaseOn(info, it.date) == phase }
            val mood = days.mapNotNull { it.mental }
            val physical = days.mapNotNull { it.physical }
            PhaseFigures(
                phase,
                generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.count { phaseOn(info, it) == phase },
                mood.size, mood.takeIf { it.isNotEmpty() }?.average(), physical.takeIf { it.isNotEmpty() }?.average(),
                days.count { it.physicalSymptoms.isNotEmpty() || it.mentalSymptoms.isNotEmpty() || it.cycleSymptoms.isNotEmpty() },
            )
        }

        val products = state.productLog.filter { !it.start.toLocalDate().isBefore(from) }
            .groupingBy { it.product }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
        val meds = state.prnIntakes.count { it.reason == PeriodCare.REASON && !it.takenAt.toLocalDate().isBefore(from) }

        val report = CycleReport(
            records, lengths, lengths.takeIf { it.isNotEmpty() }?.average(), lengths.minOrNull(), lengths.maxOrNull(),
            records.mapNotNull { it.periodDays }.takeIf { it.isNotEmpty() }?.average(),
            flowDays, symptoms, byPhase, products, meds, emptyList(),
        )
        return report.copy(insights = insights(state, report, info, today))
    }

    private fun f(x: Double) = "%.1f".format(Locale.FRANCE, x)

    private fun insights(state: AppState, r: CycleReport, info: CycleInfo, today: LocalDate): List<Insight> = buildList {
        if (r.lengths.size >= 3) {
            val spread = r.spread!!
            if (spread <= COMMON_SPREAD) add(Insight("Tes cycles sont assez réguliers : ${spread} jour${if (spread > 1) "s" else ""} d'écart entre le plus court et le plus long.", Tone.GOOD))
            else add(Insight("Tes cycles varient de $spread jours entre le plus court et le plus long. C'est utile à montrer à ton médecin si ça te préoccupe.", Tone.INFO))
        }
        val outside = r.lengths.count { it < USUAL_MIN || it > USUAL_MAX }
        if (r.lengths.size >= 3 && outside > 0) {
            add(Insight("$outside cycle${if (outside > 1) "s" else ""} sur ${r.lengths.size} ${if (outside > 1) "durent" else "dure"} moins de $USUAL_MIN jours ou plus de $USUAL_MAX jours : parles-en à ton médecin si cela se répète.", Tone.WARN))
        }
        // The phase that weighs most on the mood, if there is enough to say it.
        val moods = r.byPhase.filter { it.averageMood != null && it.daysWithMood >= 3 }
        if (moods.size >= 2) {
            val low = moods.minByOrNull { it.averageMood!! }!!
            val rest = moods.filter { it !== low }.map { it.averageMood!! }.average()
            if (rest - low.averageMood!! >= 0.7) {
                add(Insight("Ton moral est plus bas pendant la ${low.phase.label.lowercase()} (${f(low.averageMood)}/5 contre ${f(rest)}/5 le reste du temps).", Tone.INFO))
            }
        }
        // The symptoms the user follows, stronger in a phase.
        val cycleStarts = info.periodStarts.sorted().filter { !it.isAfter(today) }
        if (cycleStarts.isNotEmpty()) {
            val from = cycleStarts.takeLast(CYCLES + 1).first()
            Wellbeing.tracked(state).forEach { id ->
                val notes = state.symptomLog.filter { it.symptom == id && !it.date.isBefore(from) && !it.date.isAfter(today) }
                if (notes.size < 6) return@forEach
                val perPhase = notes.mapNotNull { n -> phaseOn(info, n.date)?.let { it to n.intensity } }.groupBy({ it.first }, { it.second })
                val worst = perPhase.filter { it.value.size >= 3 }.maxByOrNull { it.value.average() } ?: return@forEach
                val others = perPhase.filter { it.key != worst.key }.values.flatten()
                if (others.size < 3) return@forEach
                if (worst.value.average() - others.average() >= 1.0) {
                    add(Insight("Ton symptôme « ${Wellbeing.name(id).lowercase()} » est plus fort pendant la ${worst.key.label.lowercase()} (${f(worst.value.average())} contre ${f(others.average())} en moyenne sur 5).", Tone.INFO))
                }
            }
        }
    }

    /** The lengths as bars with their date, for a chart: oldest first. */
    fun lengthBars(r: CycleReport): List<Pair<LocalDate, Int>> = r.records.mapNotNull { rec -> rec.length?.let { rec.start to it } }

    fun roundedAverage(r: CycleReport): Int? = r.averageLength?.roundToInt()
}
