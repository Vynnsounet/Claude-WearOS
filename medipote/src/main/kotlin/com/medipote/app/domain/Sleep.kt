package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.SleepNight
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A stretch of the night, asleep or awake (falling asleep, waking up in the night). */
data class SleepSegment(val start: LocalDateTime, val end: LocalDateTime, val asleep: Boolean) {
    val minutes: Int get() = Duration.between(start, end).toMinutes().toInt()
}

data class SleepReport(
    val days: Int,
    val goalMinutes: Int,
    /** Nights of the period, oldest first. */
    val nights: List<SleepNight>,
    val averageAsleep: Int?,
    val averageInBed: Int?,
    val averageLatency: Int?,
    val averageEfficiency: Float?,
    val averageAwakenings: Float?,
    val averageAwakeMinutes: Int?,
    val averageLongestStretch: Int?,
    val typicalBedtime: Int?,
    val typicalWake: Int?,
    /** Mean gap, in minutes, between each bedtime and the usual one. */
    val bedtimeSpread: Int?,
    val goalNights: Int,
    val calmNights: Int,
    val averageQuality: Float?,
    /** Night awakenings per hour of the day. */
    val awakeningHours: IntArray,
    val typicalAwakening: Int?,
    val reasons: List<Pair<String, Int>>,
    val tags: List<Pair<String, Int>>,
    val previousAverageAsleep: Int?,
    val weekdayAverage: Int?,
    val weekendAverage: Int?,
    val insights: List<Insight>,
) {
    val count: Int get() = nights.size
    val awakeningCount: Int get() = nights.sumOf { it.awakenings.size }
}

object Sleep {
    private val FR = Locale.FRANCE

    val AWAKENING_REASONS = listOf(
        "Toilettes", "Bruit", "Chaleur", "Froid", "Douleur", "Cauchemar", "Anxiété", "Enfant", "Animal", "Soif", "Sans raison",
    )
    val TAGS = listOf(
        "Écrans avant de dormir", "Caféine", "Alcool", "Sport", "Repas tardif", "Stress", "Douleur", "Cauchemars",
        "Ronflements", "Sieste", "Médicament pour dormir", "Pas chez moi",
    )
    val QUALITY_LABELS = listOf("Très mauvaise", "Mauvaise", "Moyenne", "Bonne", "Excellente")
    val LATENCY_OPTIONS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)
    val DURATION_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180)

    /** "7 h 05", "45 min". */
    fun format(minutes: Int): String {
        val m = abs(minutes)
        return when {
            m < 60 -> "$m min"
            m % 60 == 0 -> "${m / 60} h"
            else -> "${m / 60} h ${"%02d".format(m % 60)}"
        }
    }

    fun minuteOf(t: LocalDateTime) = t.hour * 60 + t.minute

    /** Bedtime for the night ending on [date]: from noon on, it was the evening before. */
    fun bedtimeFor(date: LocalDate, minuteOfDay: Int): LocalDateTime =
        (if (minuteOfDay >= 12 * 60) date.minusDays(1) else date).atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))

    /** A time of the night ending at [wake]: the latest such clock time not after waking up. */
    fun inNight(wake: LocalDateTime, minuteOfDay: Int): LocalDateTime {
        val t = wake.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        return if (t.isAfter(wake)) t.minusDays(1) else t
    }

    fun segments(n: SleepNight): List<SleepSegment> {
        if (!n.wakeTime.isAfter(n.bedtime)) return emptyList()
        val out = mutableListOf<SleepSegment>()
        val sleepStart = minOf(n.bedtime.plusMinutes(n.latencyMinutes.toLong()), n.wakeTime)
        if (sleepStart.isAfter(n.bedtime)) out += SleepSegment(n.bedtime, sleepStart, asleep = false)
        var t = sleepStart
        for (a in n.awakenings.sortedBy { it.at }) {
            val s = maxOf(a.at, t).coerceAtMost(n.wakeTime)
            val e = minOf(s.plusMinutes(a.minutes.toLong()), n.wakeTime)
            if (s.isAfter(t)) out += SleepSegment(t, s, asleep = true)
            if (e.isAfter(s)) out += SleepSegment(s, e, asleep = false)
            t = maxOf(t, e)
        }
        if (n.wakeTime.isAfter(t)) out += SleepSegment(t, n.wakeTime, asleep = true)
        return out
    }

    /** Longest stretch of sleep without waking up. */
    fun longestStretch(n: SleepNight): Int = segments(n).filter { it.asleep }.maxOfOrNull { it.minutes } ?: 0

    /** What is wrong with the times entered, if anything. */
    fun problems(n: SleepNight): List<String> = buildList {
        if (!n.wakeTime.isAfter(n.bedtime)) {
            add("L'heure du réveil doit être après l'heure du coucher.")
            return@buildList
        }
        if (n.inBedMinutes > 16 * 60) add("Plus de 16 h entre le coucher et le réveil : vérifie les heures.")
        if (n.latencyMinutes + n.awakeMinutes >= n.inBedMinutes) add("L'endormissement et les réveils durent plus longtemps que la nuit.")
        if (n.awakenings.any { it.at.isBefore(n.bedtime) || !it.at.isBefore(n.wakeTime) }) {
            add("Un réveil est en dehors de la nuit (entre ${n.bedtime.toLocalTime()} et ${n.wakeTime.toLocalTime()}).")
        }
    }

    /** A new night pre-filled with the usual times of the last two weeks. */
    fun newNight(state: AppState, date: LocalDate): SleepNight {
        val recent = state.sleep.filter { it.date.isBefore(date) && !it.date.isBefore(date.minusDays(14)) }
        val bed = Reports.circularMean(recent.map { minuteOf(it.bedtime) }, 0.5)?.let { it / 5 * 5 } ?: (23 * 60)
        val wake = Reports.circularMean(recent.map { minuteOf(it.wakeTime) }, 0.5)?.let { it / 5 * 5 } ?: (7 * 60)
        val latency = recent.map { it.latencyMinutes }.sorted().let { if (it.isEmpty()) 15 else it[it.size / 2] }
        val wakeTime = date.atTime(LocalTime.of(wake / 60, wake % 60))
        val bedtime = bedtimeFor(date, bed).let { if (it.isBefore(wakeTime)) it else it.minusDays(1) }
        return SleepNight(date, bedtime, latency, wakeTime)
    }

    /** Saves a night (one per date); the first night logged on its own morning earns a small bonus. */
    fun save(state: AppState, night: SleepNight, now: LocalDateTime): Pair<AppState, Int> {
        val sleep = (state.sleep.filterNot { it.date == night.date } + night.copy(awakenings = night.awakenings.sortedBy { it.at }))
            .sortedByDescending { it.date }
        var game = state.game
        var bonus = 0
        val today = now.toLocalDate()
        if (night.date == today && today !in game.sleepRewardDays) {
            bonus = Rules.SLEEP_BONUS
            game = Game.addPoints(game, bonus, "Nuit notée", now).copy(sleepRewardDays = game.sleepRewardDays + today)
        }
        return state.copy(sleep = sleep, game = game) to bonus
    }

    fun delete(state: AppState, date: LocalDate): AppState = state.copy(sleep = state.sleep.filterNot { it.date == date })

    // ─────────────────────────── Statistics ───────────────────────────

    fun report(state: AppState, today: LocalDate, days: Int): SleepReport {
        val goal = state.settings.sleep.goalMinutes
        val start = today.minusDays(days - 1L)
        fun inRange(n: SleepNight, from: LocalDate, to: LocalDate) = !n.date.isBefore(from) && !n.date.isAfter(to)
        val nights = state.sleep.filter { inRange(it, start, today) }.sortedBy { it.date }
        val previous = state.sleep.filter { inRange(it, start.minusDays(days.toLong()), start.minusDays(1)) }

        fun avg(values: List<Int>) = values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val beds = nights.map { minuteOf(it.bedtime) }
        val typicalBed = Reports.circularMean(beds, 0.3)
        val awakenings = nights.flatMap { it.awakenings }
        val hours = IntArray(24).also { h -> awakenings.forEach { h[it.at.hour]++ } }
        fun ranked(values: List<String>) = values.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
        val weekend = nights.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        val weekday = nights - weekend.toSet()

        val report = SleepReport(
            days = days,
            goalMinutes = goal,
            nights = nights,
            averageAsleep = avg(nights.map { it.asleepMinutes }),
            averageInBed = avg(nights.map { it.inBedMinutes }),
            averageLatency = avg(nights.map { it.latencyMinutes }),
            averageEfficiency = nights.takeIf { it.isNotEmpty() }?.map { it.efficiency }?.average()?.toFloat(),
            averageAwakenings = nights.takeIf { it.isNotEmpty() }?.let { awakenings.size.toFloat() / it.size },
            averageAwakeMinutes = avg(nights.map { it.awakeMinutes }),
            averageLongestStretch = avg(nights.map { longestStretch(it) }),
            typicalBedtime = typicalBed,
            typicalWake = Reports.circularMean(nights.map { minuteOf(it.wakeTime) }, 0.3),
            bedtimeSpread = typicalBed?.let { t -> avg(beds.map { abs(Reports.circularDiff(it, t)) }) },
            goalNights = nights.count { it.asleepMinutes >= goal },
            calmNights = nights.count { it.awakenings.isEmpty() },
            averageQuality = nights.mapNotNull { it.quality }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            awakeningHours = hours,
            typicalAwakening = if (awakenings.size >= 3) Reports.circularMean(awakenings.map { minuteOf(it.at) }, 0.6) else null,
            reasons = ranked(awakenings.map { it.reason }),
            tags = ranked(nights.flatMap { it.tags }),
            previousAverageAsleep = if (previous.size >= 3) avg(previous.map { it.asleepMinutes }) else null,
            weekdayAverage = if (weekday.size >= 2) avg(weekday.map { it.asleepMinutes }) else null,
            weekendAverage = if (weekend.size >= 2) avg(weekend.map { it.asleepMinutes }) else null,
            insights = emptyList(),
        )
        return report.copy(insights = insights(report, state))
    }

    private fun pct(f: Float) = "${(f * 100).roundToInt()} %"
    private fun dec(f: Float) = "%.1f".format(FR, f)

    private fun insights(r: SleepReport, state: AppState): List<Insight> {
        val out = mutableListOf<Insight>()
        val avg = r.averageAsleep ?: return out
        if (r.count < 2) return out
        val goal = r.goalMinutes
        out += when {
            avg >= goal - 15 -> Insight("Tu dors en moyenne ${format(avg)} par nuit : objectif de ${format(goal)} atteint.", Tone.GOOD)
            avg >= goal - 45 -> Insight("Tu dors en moyenne ${format(avg)} par nuit, proche de ton objectif de ${format(goal)}.", Tone.INFO)
            else -> Insight("Tu dors en moyenne ${format(avg)} par nuit, soit ${format(goal - avg)} de moins que ton objectif (${format(goal)}).", Tone.WARN)
        }
        r.previousAverageAsleep?.let { prev ->
            val diff = avg - prev
            if (diff >= 20) out += Insight("Tu dors ${format(diff)} de plus que sur la période précédente.", Tone.GOOD)
            if (diff <= -20) out += Insight("Tu dors ${format(-diff)} de moins que sur la période précédente.", Tone.WARN)
        }
        val awake = r.averageAwakeMinutes ?: 0
        val perNight = r.averageAwakenings ?: 0f
        if (r.calmNights == r.count && r.count >= 3) {
            out += Insight("Aucun réveil noté sur la période : des nuits d'une traite !", Tone.GOOD)
        } else if (perNight >= 0.5f) {
            val time = r.typicalAwakening?.let { ", le plus souvent vers ${Reports.formatTime(it)}" }.orEmpty()
            out += Insight(
                "${dec(perNight)} réveil${if (perNight >= 2f) "s" else ""} par nuit en moyenne$time, pour ${format(awake)} d'éveil.",
                if (awake >= 30) Tone.WARN else Tone.INFO,
            )
        }
        r.reasons.firstOrNull()?.let { (reason, n) ->
            val share = n.toFloat() / r.awakeningCount
            if (n >= 2 && share >= 0.3f) out += Insight("Cause de réveil la plus fréquente : ${reason.lowercase()} (${pct(share)} des réveils).", Tone.INFO)
        }
        val spread = r.bedtimeSpread
        val bed = r.typicalBedtime
        if (spread != null && bed != null && r.count >= 4) {
            if (spread >= 60) {
                out += Insight(
                    "Coucher irrégulier : ${format(spread)} d'écart en moyenne avec ton heure habituelle (${Reports.formatTime(bed)}). Des horaires réguliers aident à mieux dormir.",
                    Tone.WARN,
                )
            } else if (spread <= 30 && r.count >= 5) {
                out += Insight("Horaires réguliers : coucher vers ${Reports.formatTime(bed)}, à ${format(spread)} près en moyenne.", Tone.GOOD)
            }
        }
        r.averageEfficiency?.let {
            if (it < 0.85f && r.count >= 3) out += Insight("Tu dors ${pct(it)} du temps passé au lit (l'idéal est au-dessus de 85 %).", Tone.WARN)
        }
        r.averageLatency?.let {
            if (it > 30 && r.count >= 3) out += Insight("Il te faut en moyenne ${format(it)} pour t'endormir.", Tone.INFO)
        }
        val we = r.weekendAverage
        val wd = r.weekdayAverage
        if (we != null && wd != null && we - wd >= 45) {
            out += Insight("Le week-end, tu dors ${format(we - wd)} de plus qu'en semaine : signe d'un manque de sommeil en semaine.", Tone.INFO)
        }
        // The tag that goes with the shortest nights.
        r.tags.mapNotNull { (tag, n) ->
            val with = r.nights.filter { tag in it.tags }
            val without = r.nights.filter { tag !in it.tags }
            if (n < 2 || without.size < 2) null
            else tag to (with.map { it.asleepMinutes }.average() - without.map { it.asleepMinutes }.average()).roundToInt()
        }.minByOrNull { it.second }?.let { (tag, diff) ->
            if (diff <= -30) out += Insight("Les nuits marquées « ${tag.lowercase()} », tu dors ${format(-diff)} de moins que les autres.", Tone.INFO)
        }
        // How the day felt after a good night or a short one (journal of the same morning).
        val good = mutableListOf<Double>()
        val short = mutableListOf<Double>()
        for (n in r.nights) {
            val e = state.entryFor(n.date)
            val scores = listOfNotNull(e.physical, e.mental)
            if (scores.isEmpty()) continue
            if (n.asleepMinutes >= goal - 60) good += scores.average() else short += scores.average()
        }
        if (good.size >= 3 && short.size >= 3 && good.average() - short.average() >= 0.5) {
            out += Insight(
                "Après une nuit d'au moins ${format(goal - 60)}, ta forme est notée ${dec(good.average().toFloat())}/5, " +
                    "contre ${dec(short.average().toFloat())}/5 après une nuit plus courte.",
                Tone.INFO,
            )
        }
        return out.sortedBy { when (it.tone) { Tone.WARN -> 0; Tone.GOOD -> 1; Tone.INFO -> 2 } }.take(7)
    }
}
