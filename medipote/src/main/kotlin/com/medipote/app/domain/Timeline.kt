package com.medipote.app.domain

import com.medipote.app.data.AppState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class EventKind { TREATMENT_START, TREATMENT_END, DOSE_CHANGE, LAB, APPOINTMENT, ILLNESS, BLANK_NIGHT, HARD_STRETCH, PERIOD }

/** One thing that happened: a treatment began or changed, a blood test, an illness, a hard stretch… */
data class TimelineEvent(
    val date: LocalDate,
    val kind: EventKind,
    val emoji: String,
    val title: String,
    val detail: String = "",
    /** The treatment or analysis it is about, when it is about one. */
    val ref: String = "",
)

/** One day of the strip: what was noted, or null when nothing was. */
data class DayPoint(val date: LocalDate, val sleepMinutes: Int?, val mood: Int?, val form: Int?, val symptom: Int?)

enum class Change { BETTER, WORSE, SAME }

data class BeforeAfterRow(val label: String, val before: String, val after: String, val change: Change)

/** What the 14 days around a change look like: averages before and after, side by side. */
data class BeforeAfter(val rows: List<BeforeAfterRow>, val beforeDays: Int, val afterDays: Int)

/**
 * « Ma période »: treatment changes, symptoms, sleep, mood and blood tests on one line of time, and for each treatment change
 * what the two weeks before and the two weeks after look like. All computed on the phone from the notes; the averages are what
 * was noted, never a conclusion, since other things change at the same time.
 */
object Timeline {
    const val WINDOW = 14
    const val MIN_DAYS = 5
    private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

    fun series(state: AppState, from: LocalDate, to: LocalDate): List<DayPoint> {
        val tracked = if (Wellbeing.enabled(state)) Wellbeing.tracked(state) else emptyList()
        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt()
        return (0..days).map { i ->
            val d = from.plusDays(i.toLong())
            val entry = state.entryFor(d)
            val symptoms = state.symptomLog.filter { it.date == d && it.symptom in tracked }
            DayPoint(
                d, state.sleep.firstOrNull { it.date == d }?.asleepMinutes, entry.mental, entry.physical,
                symptoms.maxOfOrNull { it.intensity },
            )
        }
    }

    fun events(state: AppState, from: LocalDate, to: LocalDate): List<TimelineEvent> {
        fun inRange(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        val out = mutableListOf<TimelineEvent>()
        state.treatments.filter { !it.asNeeded || true }.forEach { t ->
            if (inRange(t.startDate)) out += TimelineEvent(t.startDate, EventKind.TREATMENT_START, "💊", "Début : ${t.displayName}", ref = t.id)
            t.endDate?.takeIf { inRange(it) && !it.isAfter(to) }?.let { out += TimelineEvent(it, EventKind.TREATMENT_END, "🏁", "Fin : ${t.displayName}", ref = t.id) }
            t.changes.filter { inRange(it.date) }.forEach { out += TimelineEvent(it.date, EventKind.DOSE_CHANGE, "🔁", it.text, ref = t.id) }
        }
        state.labs.filter { inRange(it.date) }.forEach {
            out += TimelineEvent(it.date, EventKind.LAB, "🧪", "${Labs.name(it.marker)} : ${Labs.format(it.value, it.marker)} ${it.unit}".trim(), ref = it.marker)
        }
        state.appointments.filter { inRange(it.dateTime.toLocalDate()) && !it.dateTime.toLocalDate().isAfter(to) }.forEach {
            out += TimelineEvent(it.dateTime.toLocalDate(), EventKind.APPOINTMENT, "📅", it.title, it.doctor)
        }
        // An illness over several days is one event.
        val ill = state.journal.filter { inRange(it.date) && it.illness.isNotBlank() }.sortedBy { it.date }
        var i = 0
        while (i < ill.size) {
            var j = i
            while (j + 1 < ill.size && ill[j + 1].date == ill[j].date.plusDays(1) && ill[j + 1].illness == ill[i].illness) j++
            val n = j - i + 1
            out += TimelineEvent(ill[i].date, EventKind.ILLNESS, "🤒", ill[i].illness, if (n > 1) "$n jours" else "")
            i = j + 1
        }
        state.sleep.filter { inRange(it.date) && it.noSleep }.forEach { out += TimelineEvent(it.date, EventKind.BLANK_NIGHT, "🌙", "Nuit blanche") }
        // Three days or more in a row of a low mood or a strong symptom.
        val points = series(state, from, to)
        var run = mutableListOf<DayPoint>()
        fun close() {
            if (run.size >= 3) out += TimelineEvent(run.first().date, EventKind.HARD_STRETCH, "🌧️", "Période difficile", "${run.size} jours")
            run = mutableListOf()
        }
        points.forEach { p -> if ((p.mood ?: 5) <= 2 || (p.symptom ?: 0) >= 4) run += p else close() }
        close()
        state.profile?.cycle?.takeIf { state.profile.menstruating }?.periodStarts?.filter { inRange(it) }?.forEach {
            out += TimelineEvent(it, EventKind.PERIOD, "🌸", "Début des règles")
        }
        return out.sortedWith(compareByDescending<TimelineEvent> { it.date }.thenBy { it.kind.ordinal })
    }

    /** True for the events a before / after can be made for: a treatment that began, ended or changed. */
    fun hasComparison(e: TimelineEvent) = e.kind == EventKind.TREATMENT_START || e.kind == EventKind.TREATMENT_END || e.kind == EventKind.DOSE_CHANGE

    /**
     * The two weeks before [event] and the two weeks from it, side by side; null if the change is too recent (less than a week
     * of «after» yet) or if there is not enough noted to compare anything.
     */
    fun beforeAfter(state: AppState, event: TimelineEvent, today: LocalDate): BeforeAfter? {
        if (!hasComparison(event)) return null
        val afterEnd = minOf(event.date.plusDays(WINDOW - 1L), today)
        if (afterEnd.isBefore(event.date.plusDays(6))) return null
        val before = (1..WINDOW).map { event.date.minusDays(it.toLong()) }
        val after = generateSequence(event.date) { it.plusDays(1) }.takeWhile { !it.isAfter(afterEnd) }.toList()
        val rows = mutableListOf<BeforeAfterRow>()

        fun row(label: String, f: (LocalDate) -> Double?, text: (Double) -> String, threshold: Double, higherIsBetter: Boolean?) {
            val b = before.mapNotNull(f); val a = after.mapNotNull(f)
            if (b.size < MIN_DAYS || a.size < MIN_DAYS) return
            val mb = b.average(); val ma = a.average()
            val change = when {
                higherIsBetter == null || abs(ma - mb) < threshold -> Change.SAME
                (ma > mb) == higherIsBetter -> Change.BETTER
                else -> Change.WORSE
            }
            rows += BeforeAfterRow(label, text(mb), text(ma), change)
        }
        row("Sommeil par nuit", { d -> state.sleep.firstOrNull { it.date == d }?.asleepMinutes?.toDouble() }, { Sleep.format(it.toInt()) }, 20.0, true)
        row("Moral", { d -> state.entryFor(d).mental?.toDouble() }, { "%.1f/5".format(Locale.FRANCE, it) }, 0.3, true)
        row("Forme physique", { d -> state.entryFor(d).physical?.toDouble() }, { "%.1f/5".format(Locale.FRANCE, it) }, 0.3, true)
        if (Wellbeing.enabled(state)) Wellbeing.tracked(state).forEach { id ->
            row(Wellbeing.name(id), { d -> DayFactors.value(state, d, "symptom:$id") }, { "%.1f/5".format(Locale.FRANCE, it) }, 0.3, false)
        }
        row("Eau par jour", { d -> Water.ml(state, d).takeIf { it > 0 }?.toDouble() }, { Water.format(it.toInt()) }, 250.0, null)
        if (rows.isEmpty()) return null
        return BeforeAfter(rows, before.size, after.size)
    }

    fun dayText(d: LocalDate): String = d.format(DAY)

    /** « 2 changements de traitement, 1 prise de sang » for the head of the page. */
    fun summary(events: List<TimelineEvent>): String {
        val changes = events.count { it.kind == EventKind.TREATMENT_START || it.kind == EventKind.TREATMENT_END || it.kind == EventKind.DOSE_CHANGE }
        val labs = events.count { it.kind == EventKind.LAB }
        val hard = events.count { it.kind == EventKind.HARD_STRETCH }
        return listOfNotNull(
            "$changes changement${if (changes > 1) "s" else ""} de traitement".takeIf { changes > 0 },
            "$labs analyse${if (labs > 1) "s" else ""}".takeIf { labs > 0 },
            "$hard période${if (hard > 1) "s" else ""} difficile${if (hard > 1) "s" else ""}".takeIf { hard > 0 },
        ).joinToString(" · ").ifBlank { "Rien de marquant noté" }
    }
}
