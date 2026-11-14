package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.PrnIntake
import com.medipote.app.data.TakeStatus
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Moments of the day used to group doses and intakes. */
enum class TimeSlot(val label: String, val phrase: String, val of: String) {
    MORNING("Matin", "le matin", "du matin"),
    NOON("Midi", "à midi", "de midi"),
    AFTERNOON("Après-midi", "l'après-midi", "de l'après-midi"),
    EVENING("Soir", "le soir", "du soir"),
    NIGHT("Nuit", "la nuit", "de la nuit");

    companion object {
        fun of(hour: Int): TimeSlot = when (hour) {
            in 5..10 -> MORNING
            in 11..13 -> NOON
            in 14..17 -> AFTERNOON
            in 18..21 -> EVENING
            else -> NIGHT
        }
    }
}

/** Doses taken out of doses that are decided (taken or missed). */
data class Rate(val taken: Int, val decided: Int) {
    val value: Float? get() = if (decided == 0) null else taken.toFloat() / decided
    operator fun plus(o: Rate) = Rate(taken + o.taken, decided + o.decided)

    companion object {
        val ZERO = Rate(0, 0)
    }
}

enum class Tone { GOOD, WARN, INFO }

/** A sentence the stats screen shows, worked out from the numbers. */
data class Insight(val text: String, val tone: Tone)

data class TreatmentReport(val name: String, val rate: Rate, val averageDelayMinutes: Int?)

data class AdherenceReport(
    val days: Int,
    val daily: List<DayStats>,
    val rate: Rate,
    val previousRate: Rate,
    val onTime: Int,
    val offTime: Int,
    val missed: Int,
    /** Mean of (taken − planned) in minutes: positive when late on average. */
    val averageDelayMinutes: Int?,
    val bySlot: Map<TimeSlot, Rate>,
    val byWeekday: Map<DayOfWeek, Rate>,
    val perTreatment: List<TreatmentReport>,
    /** Days where every planned dose was taken. */
    val completeDays: Int,
    val daysWithDoses: Int,
    /** The last 12 weeks, oldest first: week start and rate. */
    val weekly: List<Pair<LocalDate, Rate>>,
    /** The last 84 days, oldest first, for the calendar heatmap. */
    val heatmap: List<DayStats>,
    val waterAverage: Float?,
    val waterGoalDays: Int,
    val waterDays: Int,
    val physicalAverage: Float?,
    val mentalAverage: Float?,
    val insights: List<Insight>,
) {
    val punctuality: Float? get() = if (onTime + offTime == 0) null else onTime.toFloat() / (onTime + offTime)
}

data class PrnReport(
    val treatmentId: String,
    val name: String,
    /** Period length in days (from the first intake when "all time"). */
    val days: Int,
    val intakes: List<PrnIntake>,
    val previousCount: Int?,
    val perWeek: Float,
    val daysWithIntake: Int,
    val averageIntervalHours: Float?,
    val hourCounts: IntArray,
    /** Typical time of day (circular mean), or null when intakes are spread over the day. */
    val typicalMinuteOfDay: Int?,
    val slotCounts: Map<TimeSlot, Int>,
    val weekdayCounts: Map<DayOfWeek, Int>,
    val reasons: List<Pair<String, Int>>,
    val contexts: List<Pair<String, Int>>,
    val averageIntensity: Float?,
    val averageRelief: Float?,
    val reliefRated: Int,
    val effectiveShare: Float?,
    /** The last 12 weeks, oldest first: week start and number of intakes. */
    val weekly: List<Pair<LocalDate, Int>>,
    val daysOverMax: Int,
    /** Share of intakes during periods, and share of days that were period days. */
    val cycleShare: Pair<Float, Float>?,
    val insights: List<Insight>,
) {
    val count: Int get() = intakes.size
}

object Reports {
    private val FR = Locale.FRANCE

    private fun pct(f: Float) = "${(f * 100).roundToInt()} %"

    fun weekdayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, FR)

    // ─────────────────────────── Regular treatments ───────────────────────────

    fun adherence(state: AppState, now: LocalDateTime, days: Int): AdherenceReport {
        val today = now.toLocalDate()
        val logs = state.doseLogs.associateBy { it.key }
        val history = Stats.daily(state, now, maxOf(days * 2, 84))
        val daily = history.takeLast(days)
        val previous = history.dropLast(days).takeLast(days)

        val bySlot = TimeSlot.entries.associateWith { Rate.ZERO }.toMutableMap()
        val byWeekday = DayOfWeek.entries.associateWith { Rate.ZERO }.toMutableMap()
        val perTreatment = linkedMapOf<String, Pair<Rate, MutableList<Long>>>()
        val delays = mutableListOf<Long>()
        val completeDates = mutableSetOf<LocalDate>()
        val missedDates = mutableSetOf<LocalDate>()

        for (offset in 0 until days) {
            val date = today.minusDays(offset.toLong())
            val items = Schedule.itemsOn(state.treatments, logs, date, now)
            var dayTaken = 0
            var dayDecided = 0
            for (item in items) {
                val taken = item.state == DoseState.TAKEN
                if (!taken && item.state != DoseState.MISSED) continue
                val r = Rate(if (taken) 1 else 0, 1)
                val slot = TimeSlot.of(item.occurrence.dose.minuteOfDay / 60)
                bySlot[slot] = bySlot.getValue(slot) + r
                byWeekday[date.dayOfWeek] = byWeekday.getValue(date.dayOfWeek) + r
                val t = item.occurrence.treatment
                val entry = perTreatment.getOrPut(t.id) { Rate.ZERO to mutableListOf() }
                perTreatment[t.id] = (entry.first + r) to entry.second
                if (taken && item.log != null && item.log.status != TakeStatus.EARLY) {
                    val delay = Duration.between(item.occurrence.scheduled, item.log.takenAt).toMinutes()
                    delays += delay
                    entry.second += delay
                }
                dayTaken += r.taken
                dayDecided++
            }
            if (dayDecided > 0 && dayTaken == dayDecided && items.none { it.state != DoseState.TAKEN && it.state != DoseState.MISSED }) {
                completeDates += date
            }
            if (dayDecided > dayTaken) missedDates += date
        }

        fun rateOf(list: List<DayStats>) = Rate(list.sumOf { it.taken }, list.sumOf { it.taken + it.missed })
        val rate = rateOf(daily)
        val previousRate = rateOf(previous)

        val weekly = (11 downTo 0).map { w ->
            val week = history.takeLast(84).drop((11 - w) * 7).take(7)
            (week.firstOrNull()?.date ?: today) to rateOf(week)
        }

        // Water and wellbeing over the period
        val entries = state.journal.filter { !it.date.isBefore(today.minusDays(days - 1L)) && !it.date.isAfter(today) }
        val water = entries.filter { it.waterGlasses > 0 }
        val goal = state.settings.water.goalGlasses
        val physical = entries.mapNotNull { it.physical }
        val mental = entries.mapNotNull { it.mental }

        val treatments = perTreatment.map { (id, p) ->
            val name = state.treatments.firstOrNull { it.id == id }?.displayName ?: "?"
            TreatmentReport(name, p.first, p.second.takeIf { it.isNotEmpty() }?.average()?.roundToInt())
        }
        val report = AdherenceReport(
            days = days,
            daily = daily,
            rate = rate,
            previousRate = previousRate,
            onTime = daily.sumOf { it.onTime },
            offTime = daily.sumOf { it.late },
            missed = daily.sumOf { it.missed },
            averageDelayMinutes = delays.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            bySlot = bySlot,
            byWeekday = byWeekday,
            perTreatment = treatments,
            completeDays = completeDates.size,
            daysWithDoses = daily.count { it.planned > 0 },
            weekly = weekly,
            heatmap = history.takeLast(84),
            waterAverage = if (water.isEmpty()) null else water.map { it.waterGlasses }.average().toFloat(),
            waterGoalDays = water.count { it.waterGlasses >= goal },
            waterDays = water.size,
            physicalAverage = physical.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            mentalAverage = mental.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            insights = emptyList(),
        )
        return report.copy(insights = adherenceInsights(report, entries, completeDates, missedDates))
    }

    private fun adherenceInsights(
        r: AdherenceReport,
        entries: List<com.medipote.app.data.DayEntry>,
        complete: Set<LocalDate>,
        missed: Set<LocalDate>,
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        val now = r.rate.value
        val before = r.previousRate.value
        if (now != null && before != null && r.rate.decided >= 5 && r.previousRate.decided >= 5) {
            val diff = now - before
            if (diff >= 0.05f) out += Insight("Observance en hausse : ${pct(now)} contre ${pct(before)} sur la période précédente. Bravo !", Tone.GOOD)
            if (diff <= -0.05f) out += Insight("Observance en baisse : ${pct(now)} contre ${pct(before)} sur la période précédente.", Tone.WARN)
        }
        if (now != null) {
            r.bySlot.filter { it.value.decided >= 4 }.minByOrNull { it.value.value ?: 1f }?.let { (slot, rate) ->
                val v = rate.value ?: return@let
                if (v < now - 0.1f) out += Insight("Les prises ${slot.of} sont les plus souvent oubliées (seulement ${pct(v)} faites).", Tone.WARN)
            }
            r.byWeekday.filter { it.value.decided >= 3 }.minByOrNull { it.value.value ?: 1f }?.let { (day, rate) ->
                val v = rate.value ?: return@let
                if (v < now - 0.1f) out += Insight("Le ${weekdayName(day)} est ton jour le plus difficile (${pct(v)} des prises).", Tone.WARN)
            }
        }
        if (r.perTreatment.size >= 2) {
            r.perTreatment.filter { it.rate.decided >= 4 }.minByOrNull { it.rate.value ?: 1f }?.let { t ->
                val v = t.rate.value ?: return@let
                if (v < 0.8f) out += Insight("${t.name} est le traitement le plus souvent oublié (${pct(v)} des prises).", Tone.WARN)
            }
        }
        r.averageDelayMinutes?.let { d ->
            when {
                abs(d) <= 10 -> out += Insight("Ponctualité au top : ${abs(d)} min d'écart en moyenne avec l'heure prévue.", Tone.GOOD)
                d > 30 -> out += Insight("Tes prises ont en moyenne $d min de retard. Décaler l'horaire prévu pourrait t'aider.", Tone.WARN)
            }
        }
        if (r.daysWithDoses >= 3) out += Insight("${r.completeDays} journée${if (r.completeDays > 1) "s" else ""} complète${if (r.completeDays > 1) "s" else ""} sur ${r.daysWithDoses} (toutes les prises faites).", Tone.INFO)
        if (r.waterDays >= 3) out += Insight("Objectif d'hydratation atteint ${r.waterGoalDays} jour${if (r.waterGoalDays > 1) "s" else ""} sur ${r.waterDays} suivis.", Tone.INFO)
        val onComplete = entries.filter { it.date in complete }.mapNotNull { it.physical }
        val onMissed = entries.filter { it.date in missed }.mapNotNull { it.physical }
        if (onComplete.size >= 3 && onMissed.size >= 3) {
            val a = onComplete.average()
            val b = onMissed.average()
            if (a - b >= 0.5) {
                out += Insight(
                    "Les jours où tout est pris, ton état physique est noté ${"%.1f".format(FR, a)}/5 en moyenne, contre ${"%.1f".format(FR, b)}/5 les autres jours.",
                    Tone.INFO,
                )
            }
        }
        return out.sortedBy { when (it.tone) { Tone.WARN -> 0; Tone.GOOD -> 1; Tone.INFO -> 2 } }.take(6)
    }

    // ─────────────────────────── As-needed medications ───────────────────────────

    /** Statistics of one as-needed medication over [days] (null = since the first intake). */
    fun prn(state: AppState, treatmentId: String, now: LocalDateTime, days: Int?): PrnReport {
        val t = state.treatments.firstOrNull { it.id == treatmentId }
        val all = Prn.intakesOf(state, treatmentId).filter { !it.takenAt.isAfter(now) }
        val today = now.toLocalDate()
        val start = when {
            days != null -> today.minusDays(days - 1L)
            else -> all.minOfOrNull { it.takenAt.toLocalDate() } ?: today
        }
        val periodDays = ChronoUnit.DAYS.between(start, today).toInt() + 1
        val intakes = all.filter { !it.takenAt.toLocalDate().isBefore(start) }
        // Only compare with the previous period if intakes were already being logged back then.
        val previousCount = days?.takeIf { all.any { it.takenAt.toLocalDate().isBefore(start) } }?.let { d -> all.count { val x = it.takenAt.toLocalDate(); x.isBefore(start) && !x.isBefore(start.minusDays(d.toLong())) } }

        val sorted = intakes.sortedBy { it.takenAt }
        val intervals = sorted.zipWithNext { a, b -> Duration.between(a.takenAt, b.takenAt).toMinutes() / 60f }
        val hourCounts = IntArray(24).also { h -> intakes.forEach { h[it.takenAt.hour]++ } }

        // Circular mean of the time of day: 23:30 and 00:30 average to midnight, not noon.
        val typical = if (intakes.size < 3) null else circularMean(intakes.map { it.takenAt.hour * 60 + it.takenAt.minute })

        fun ranked(values: List<String>) = values.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

        val intensities = intakes.mapNotNull { it.intensity }
        val reliefs = intakes.mapNotNull { it.relief }
        val weekly = (11 downTo 0).map { w ->
            val from = today.minusDays(w * 7L + 6)
            val to = today.minusDays(w * 7L)
            from to all.count { val d = it.takenAt.toLocalDate(); !d.isBefore(from) && !d.isAfter(to) }
        }
        val perDay = intakes.groupingBy { it.takenAt.toLocalDate() }.eachCount()
        val daysOverMax = t?.maxPerDay?.let { max -> perDay.count { it.value > max } } ?: 0

        val cycleInfo = state.profile?.takeIf { it.menstruating }?.cycle
        val cycleShare = cycleInfo?.takeIf { it.periodStarts.isNotEmpty() && intakes.size >= 3 }?.let { info ->
            val periodDayCount = (0 until periodDays).count { Cycle.dayType(info, start.plusDays(it.toLong()), today) == CycleDayType.PERIOD }
            if (periodDayCount == 0) null else {
                val during = intakes.count { Cycle.dayType(info, it.takenAt.toLocalDate(), today) == CycleDayType.PERIOD }
                during.toFloat() / intakes.size to periodDayCount.toFloat() / periodDays
            }
        }

        val last30Days = all.filter { !it.takenAt.toLocalDate().isBefore(today.minusDays(29)) }.map { it.takenAt.toLocalDate() }.toSet().size

        val report = PrnReport(
            treatmentId = treatmentId,
            name = t?.displayName ?: "?",
            days = periodDays,
            intakes = intakes,
            previousCount = previousCount,
            perWeek = intakes.size * 7f / periodDays,
            daysWithIntake = perDay.size,
            averageIntervalHours = intervals.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            hourCounts = hourCounts,
            typicalMinuteOfDay = typical,
            slotCounts = TimeSlot.entries.associateWith { s -> intakes.count { TimeSlot.of(it.takenAt.hour) == s } },
            weekdayCounts = DayOfWeek.entries.associateWith { d -> intakes.count { it.takenAt.dayOfWeek == d } },
            reasons = ranked(intakes.map { it.reason }),
            contexts = ranked(intakes.flatMap { it.contexts }),
            averageIntensity = intensities.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            averageRelief = reliefs.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            reliefRated = reliefs.size,
            effectiveShare = reliefs.takeIf { it.isNotEmpty() }?.let { r -> r.count { it >= 4 }.toFloat() / r.size },
            weekly = weekly,
            daysOverMax = daysOverMax,
            cycleShare = cycleShare,
            insights = emptyList(),
        )
        return report.copy(insights = prnInsights(report, last30Days))
    }

    /**
     * Mean time of day of [minutes] (minutes after midnight) on a 24 h circle, or null when they
     * are too spread out for a mean to mean anything.
     */
    fun circularMean(minutes: List<Int>, minLength: Double = 0.35): Int? {
        if (minutes.isEmpty()) return null
        var x = 0.0
        var y = 0.0
        minutes.forEach {
            val a = it / 1440.0 * 2 * Math.PI
            x += cos(a); y += sin(a)
        }
        if (sqrt(x * x + y * y) / minutes.size < minLength) return null
        val angle = atan2(y, x).let { if (it < 0) it + 2 * Math.PI else it }
        return (angle / (2 * Math.PI) * 1440).roundToInt() % 1440
    }

    /** Signed difference a − b in minutes on a 24 h circle, between −720 and 720. */
    fun circularDiff(a: Int, b: Int): Int = ((a - b) % 1440 + 1440 + 720) % 1440 - 720

    fun formatTime(minuteOfDay: Int) = "%d h %02d".format(minuteOfDay / 60, minuteOfDay % 60)

    private fun prnInsights(r: PrnReport, daysUsedLast30: Int): List<Insight> {
        val out = mutableListOf<Insight>()
        if (r.count == 0) return out
        out += Insight(
            "${r.count} prise${if (r.count > 1) "s" else ""} en ${r.days} jour${if (r.days > 1) "s" else ""}, " +
                "soit environ ${"%.1f".format(FR, r.perWeek)} par semaine.",
            Tone.INFO,
        )
        if (daysUsedLast30 >= 10) {
            out += Insight(
                "Pris $daysUsedLast30 jours sur les 30 derniers : un usage aussi régulier mérite d'en parler à ton médecin ou à ton pharmacien.",
                Tone.WARN,
            )
        }
        if (r.daysOverMax > 0) {
            out += Insight("Maximum quotidien dépassé ${r.daysOverMax} jour${if (r.daysOverMax > 1) "s" else ""} sur la période.", Tone.WARN)
        }
        r.previousCount?.let { prev ->
            if (r.count + prev >= 4) {
                if (r.count >= prev * 1.5 && r.count - prev >= 2) out += Insight("En hausse : ${r.count} prises contre $prev sur la période précédente.", Tone.WARN)
                if (prev >= r.count * 1.5 && prev - r.count >= 2) out += Insight("En baisse : ${r.count} prises contre $prev sur la période précédente.", Tone.GOOD)
            }
        }
        if (r.count >= 3) {
            r.slotCounts.maxByOrNull { it.value }?.let { (slot, n) ->
                val share = n.toFloat() / r.count
                if (share >= 0.4f) {
                    val time = r.typicalMinuteOfDay?.let { " (vers ${formatTime(it)})" }.orEmpty()
                    out += Insight("Pris surtout ${slot.phrase}$time : ${pct(share)} des prises.", Tone.INFO)
                }
            }
        }
        if (r.count >= 5) {
            r.weekdayCounts.maxByOrNull { it.value }?.let { (day, n) ->
                val share = n.toFloat() / r.count
                if (share >= 0.3f) out += Insight("Le ${weekdayName(day)} revient souvent : ${pct(share)} des prises.", Tone.INFO)
            }
        }
        r.reasons.firstOrNull()?.let { (reason, n) ->
            val share = n.toFloat() / r.count
            if (n >= 2 && share >= 0.4f) out += Insight("Motif le plus fréquent : ${reason.lowercase()} (${pct(share)} des prises).", Tone.INFO)
        }
        r.contexts.firstOrNull()?.let { (context, n) ->
            val share = n.toFloat() / r.count
            if (n >= 2 && share >= 0.4f) out += Insight("Contexte fréquent : ${context.lowercase()} (${pct(share)} des prises).", Tone.INFO)
        }
        r.cycleShare?.let { (during, days) ->
            if (during >= days * 1.5f && during >= 0.25f) {
                out += Insight("${pct(during)} des prises ont lieu pendant tes règles, qui ne représentent que ${pct(days)} des jours.", Tone.INFO)
            }
        }
        if (r.reliefRated >= 3) {
            val eff = r.effectiveShare ?: 0f
            out += if (eff >= 0.6f) Insight("Efficace dans ${pct(eff)} des cas notés (au moins « Bien »).", Tone.GOOD)
            else Insight("Efficace dans seulement ${pct(eff)} des cas notés : parles-en à ton médecin ou à ton pharmacien.", Tone.WARN)
        }
        return out
    }
}
