package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.EnergyNote
import com.medipote.app.data.SmallWin
import com.medipote.app.data.SplitStep
import com.medipote.app.data.SplitTask
import com.medipote.app.data.SymptomNote
import java.time.LocalDate
import java.time.LocalDateTime

/** What a mission or a step gave. */
data class WinResult(val state: AppState, val points: Int, val already: Boolean)

/** One symptom over the last days. */
data class SymptomSummary(
    val id: String,
    val days: Int,
    val average: Double,
    val strongDays: Int,
    /** Intensity of the last seven days, oldest first; null = not noted. */
    val week: List<Int?>,
)

/** One symptom over a period, for the statistics. */
data class SymptomPeriod(
    val id: String,
    val days: Int,
    /** Days of the period the symptom was noted. */
    val noted: Int,
    /** Average strength on the days it was noted, 1 to 5. */
    val average: Double?,
    val strongDays: Int,
    /** The same figures over the period before, to say whether it is better or worse. */
    val previousAverage: Double?,
    val previousNoted: Int,
    /** Strength per bar (a day, or a week beyond 30 days): 0 when not noted. */
    val bars: List<Float>,
    /** What each bar is: its first day and last day. */
    val barDays: List<Pair<LocalDate, LocalDate>>,
)

/** What the small missions gave over a period. */
data class WinsReport(
    val days: Int,
    val wins: Int,
    val daysWithWins: Int,
    val points: Int,
    /** The missions done most, with how many times. */
    val top: List<Pair<String, Int>>,
    val stepsDone: Int,
    val tasksFinished: Int,
)

/**
 * Hard days: symptoms noted with their strength, small missions to pick by the energy left, tasks
 * cut into steps. Points are only ever added: not doing a mission costs nothing and shows nowhere.
 */
object Wellbeing {
    const val MISSION_POINTS = 5
    const val FIRST_STEP_POINTS = 10
    const val STEP_POINTS = 3

    /** Wins beyond this many in a day are still noted and celebrated, but pay no more points. */
    const val POINTED_WINS_PER_DAY = 3

    /** A symptom this strong makes the day "heavy". */
    const val HEAVY = 4

    const val MAX_STEPS = 12
    private const val LOG_LIMIT = 4000

    fun enabled(state: AppState) = state.settings.wellbeing.enabled

    // ── Symptoms ──

    fun name(id: String): String = WellbeingLibrary.symptom(id)?.name ?: id

    fun emoji(id: String): String = WellbeingLibrary.symptom(id)?.emoji ?: "📝"

    /** The symptoms followed, in the user's order. */
    fun tracked(state: AppState): List<String> = state.settings.wellbeing.symptoms.distinct()

    fun notesOn(state: AppState, date: LocalDate): List<SymptomNote> = state.symptomLog.filter { it.date == date }

    fun intensityOn(state: AppState, date: LocalDate, symptom: String): Int? =
        state.symptomLog.firstOrNull { it.date == date && it.symptom == symptom }?.intensity

    /** Sets how strongly [symptom] was felt on [date]; null removes it. */
    fun setIntensity(state: AppState, date: LocalDate, symptom: String, intensity: Int?, note: String? = null): AppState {
        val old = state.symptomLog.firstOrNull { it.date == date && it.symptom == symptom }
        val rest = state.symptomLog.filterNot { it.date == date && it.symptom == symptom }
        val value = intensity?.coerceIn(1, 5)
        val log = if (value == null) rest else (rest + SymptomNote(date, symptom, value, note ?: old?.note.orEmpty(), old?.zones.orEmpty()))
        return state.copy(symptomLog = log.sortedByDescending { it.date }.take(LOG_LIMIT))
    }

    /** Where [symptom] was felt on [date]; only once an intensity is noted for it. */
    fun setZones(state: AppState, date: LocalDate, symptom: String, zones: List<String>): AppState {
        val old = state.symptomLog.firstOrNull { it.date == date && it.symptom == symptom } ?: return state
        val valid = zones.filter { BodyZones.byId(it) != null }.distinct()
        return state.copy(symptomLog = state.symptomLog.map { if (it === old) it.copy(zones = valid) else it })
    }

    fun setNote(state: AppState, date: LocalDate, symptom: String, note: String): AppState {
        val old = state.symptomLog.firstOrNull { it.date == date && it.symptom == symptom } ?: return state
        return setIntensity(state, date, symptom, old.intensity, note.take(500))
    }

    /**
     * Notes several symptoms for today at once (from "Ce que je ressens"). It turns the tracking on and
     * adds them to the followed list, so what was felt is kept and can be shown to a doctor.
     */
    fun noteFelt(state: AppState, date: LocalDate, symptoms: List<String>, intensity: Int): AppState {
        val ids = symptoms.distinct()
        if (ids.isEmpty()) return state
        val w = state.settings.wellbeing
        var s = state.copy(settings = state.settings.copy(wellbeing = w.copy(enabled = true, symptoms = (w.symptoms + ids).distinct())))
        ids.forEach { s = setIntensity(s, date, it, intensity) }
        return s
    }

    /** Symptoms felt strongly today, strongest first. */
    fun strongToday(state: AppState, date: LocalDate, from: Int = 3): List<String> =
        notesOn(state, date).filter { it.intensity >= from }.sortedByDescending { it.intensity }.map { it.symptom }

    /** Journal tags that mean the same as a symptom followed (the journal has no strength; the symptom does). */
    private val TAGS: Map<String, List<String>> = mapOf(
        "fatigue" to listOf("Fatigue"), "anxiety" to listOf("Anxiété", "Stress"), "headache" to listOf("Maux de tête"),
        "nausea" to listOf("Nausées"), "fog" to listOf("Difficultés de concentration", "Fatigue mentale"),
        "lowmood" to listOf("Tristesse"), "sleep" to listOf("Troubles du sommeil"), "dizzy" to listOf("Vertiges"),
        "joints" to listOf("Douleurs articulaires"), "pain" to listOf("Douleurs musculaires"),
        "digestion" to listOf("Maux de ventre"), "irritability" to listOf("Irritabilité"),
    )

    /** The followed symptoms the journal of [date] already mentions, with no strength noted yet. */
    fun mentionedInJournal(state: AppState, date: LocalDate): List<String> {
        val tags = state.entryFor(date).let { it.physicalSymptoms + it.mentalSymptoms + it.cycleSymptoms }.map { it.lowercase() }.toSet()
        return tracked(state).filter { id ->
            intensityOn(state, date, id) == null &&
                (TAGS[id].orEmpty().any { it.lowercase() in tags } || name(id).lowercase() in tags)
        }
    }

    // ── Energy and heavy days ──

    fun energyOn(state: AppState, date: LocalDate): Int? = state.energy?.takeIf { it.date == date }?.level

    fun setEnergy(state: AppState, date: LocalDate, level: Int): AppState = state.copy(energy = EnergyNote(date, level.coerceIn(1, 3)))

    /** A day worth offering gentleness on: a strong symptom or very little energy. */
    fun heavyDay(state: AppState, date: LocalDate): Boolean =
        enabled(state) && (notesOn(state, date).any { it.intensity >= HEAVY } || energyOn(state, date) == 1)

    // ── Small wins ──

    fun winsOn(state: AppState, date: LocalDate): List<SmallWin> = state.wins.filter { it.date == date }

    fun done(state: AppState, date: LocalDate, key: String): Boolean = state.wins.any { it.date == date && it.key == key }

    /** Notes a win. Once done for the day it stays done, so points can't be won twice for it. */
    fun win(state: AppState, key: String, label: String, base: Int, now: LocalDateTime): WinResult {
        val today = now.toLocalDate()
        if (done(state, today, key)) return WinResult(state, 0, already = true)
        val paid = winsOn(state, today).count { it.points > 0 }
        val points = if (paid < POINTED_WINS_PER_DAY) base else 0
        val game = if (points > 0) Game.addPoints(state.game, points, "Petite victoire", now) else state.game
        val wins = (listOf(SmallWin(today, key, label, points)) + state.wins).take(1500)
        return WinResult(state.copy(game = game, wins = wins), points, already = false)
    }

    /** The key under which trying something for a feeling or a symptom is noted (once a day). */
    fun triedKey(kind: String, id: String) = "$kind:$id"

    /** "I tried it": what the person did for a sign or a symptom counts as a small victory. */
    fun tried(state: AppState, kind: String, id: String, label: String, now: LocalDateTime): WinResult =
        win(state, triedKey(kind, id), label, MISSION_POINTS, now)

    /** The ids tried today for [kind] ("feel" for a sign of "Ce que je ressens", "advice" for a symptom). */
    fun triedToday(state: AppState, date: LocalDate, kind: String): Set<String> =
        winsOn(state, date).map { it.key }.filter { it.startsWith("$kind:") }.map { it.removePrefix("$kind:") }.toSet()

    fun missionWin(state: AppState, mission: Mission, now: LocalDateTime): WinResult = win(state, mission.id, mission.text, MISSION_POINTS, now)

    // ── Missions ──

    /** The user's own missions as missions (any energy). */
    fun custom(state: AppState): List<Mission> =
        state.settings.wellbeing.customMissions.map { Mission("my:$it", "⭐", it, 1, 0) }

    /**
     * A few missions that fit today: what the energy allows, the ones for the strongest symptoms first,
     * then those for the symptoms followed, then the gentle ones for everyone. [shuffle] gives other ideas.
     */
    fun suggest(state: AppState, date: LocalDate, level: Int, shuffle: Int = 0, count: Int = 4): List<Mission> {
        val strong = strongToday(state, date)
        val followed = tracked(state)
        val seed = date.toEpochDay().toInt() * 31 + shuffle * 7919
        fun priority(m: Mission): Int = when {
            m.symptoms.isEmpty() -> 2
            m.symptoms.any { it in strong } -> 0
            m.symptoms.any { it in followed } -> 1
            else -> 3
        }
        return WellbeingLibrary.MISSIONS
            .filter { it.energy <= level.coerceIn(1, 3) && !done(state, date, it.id) }
            .map { it to priority(it) }
            .filter { (_, p) -> p < 3 }
            .sortedWith(compareBy({ it.second }, { (it.first.id.hashCode() xor seed) and 0x7fffffff }))
            .map { it.first }
            .take(count)
    }

    // ── Tasks cut into steps ──

    fun activeTasks(state: AppState): List<SplitTask> = state.tasks.filter { !it.finished }.sortedByDescending { it.createdAt }

    fun saveTask(state: AppState, id: String, title: String, steps: List<String>, now: LocalDateTime): AppState {
        val clean = steps.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_STEPS)
        if (title.isBlank() || clean.isEmpty()) return state
        val old = state.tasks.firstOrNull { it.id == id }
        val task = SplitTask(id, title.trim().take(120), clean.map { text -> SplitStep(text, old?.steps?.firstOrNull { it.text == text }?.done == true) }, old?.createdAt ?: now)
        return state.copy(tasks = (state.tasks.filterNot { it.id == id } + task).sortedByDescending { it.createdAt }.take(60))
    }

    fun deleteTask(state: AppState, id: String): AppState = state.copy(tasks = state.tasks.filterNot { it.id == id })

    /** Ticks a step: the first step of a task, the hardest, pays most. */
    fun completeStep(state: AppState, taskId: String, index: Int, now: LocalDateTime): WinResult {
        val task = state.tasks.firstOrNull { it.id == taskId } ?: return WinResult(state, 0, already = true)
        val step = task.steps.getOrNull(index) ?: return WinResult(state, 0, already = true)
        if (step.done) return WinResult(state, 0, already = true)
        val ticked = task.copy(steps = task.steps.mapIndexed { i, s -> if (i == index) s.copy(done = true) else s })
        val updated = state.copy(tasks = state.tasks.map { if (it.id == taskId) ticked else it })
        val firstOne = task.steps.none { it.done }
        return win(updated, "step:$taskId:$index", step.text, if (firstOne) FIRST_STEP_POINTS else STEP_POINTS, now)
    }

    // ── What the notes show ──

    fun summary(state: AppState, id: String, today: LocalDate, days: Int = 30): SymptomSummary {
        val from = today.minusDays(days - 1L)
        val notes = state.symptomLog.filter { it.symptom == id && !it.date.isBefore(from) && !it.date.isAfter(today) }
        return SymptomSummary(
            id, notes.size,
            if (notes.isEmpty()) 0.0 else notes.sumOf { it.intensity }.toDouble() / notes.size,
            notes.count { it.intensity >= HEAVY },
            (6 downTo 0).map { back -> intensityOn(state, today.minusDays(back.toLong()), id) },
        )
    }

    /** A symptom over the last [days] days (bars by day, or by week beyond 30 days). */
    fun period(state: AppState, id: String, today: LocalDate, days: Int): SymptomPeriod {
        val from = today.minusDays(days - 1L)
        val notes = state.symptomLog.filter { it.symptom == id && !it.date.isBefore(from) && !it.date.isAfter(today) }
        val before = state.symptomLog.filter { it.symptom == id && it.date.isBefore(from) && !it.date.isBefore(from.minusDays(days.toLong())) }
        val byDate = notes.associateBy { it.date }
        val ranges: List<Pair<LocalDate, LocalDate>> =
            if (days <= 30) (days - 1 downTo 0).map { today.minusDays(it.toLong()).let { d -> d to d } }
            else (12 downTo 0).map { w -> today.minusDays(w * 7L + 6) to today.minusDays(w * 7L) }
        val bars = ranges.map { (a, b) ->
            val inRange = notes.filter { !it.date.isBefore(a) && !it.date.isAfter(b) }
            if (inRange.isEmpty()) 0f else inRange.map { it.intensity }.average().toFloat()
        }
        return SymptomPeriod(
            id, days, byDate.size,
            notes.takeIf { it.isNotEmpty() }?.map { it.intensity }?.average(),
            notes.count { it.intensity >= HEAVY },
            before.takeIf { it.size >= 3 }?.map { it.intensity }?.average(),
            before.size, bars, ranges,
        )
    }

    fun winsReport(state: AppState, today: LocalDate, days: Int): WinsReport {
        val from = today.minusDays(days - 1L)
        val wins = state.wins.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        val top = wins.filter { !it.key.startsWith("step:") }.groupingBy { it.label }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(3).map { it.key to it.value }
        val finished = state.tasks.count { t -> t.finished && wins.any { it.key.startsWith("step:${t.id}:") } }
        return WinsReport(
            days, wins.size, wins.map { it.date }.distinct().size, wins.sumOf { it.points }, top,
            wins.count { it.key.startsWith("step:") }, finished,
        )
    }

    /** Short sleep, under this many minutes, is what a symptom is compared against. */
    const val SHORT_SLEEP = 6 * 60

    /**
     * Links found in the user's own notes (nothing leaves the phone): a symptom stronger after a short
     * night. Observations to show a doctor, never a diagnosis. Only with enough days on both sides.
     */
    fun insights(state: AppState, today: LocalDate): List<String> {
        val from = today.minusDays(89)
        val nights = state.sleep.associateBy { it.date }
        return state.symptomLog.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
            .groupBy { it.symptom }
            .mapNotNull { (id, notes) ->
                val withNight = notes.mapNotNull { n -> nights[n.date]?.let { n.intensity to it.asleepMinutes } }
                val short = withNight.filter { it.second < SHORT_SLEEP }.map { it.first }
                val rested = withNight.filter { it.second >= SHORT_SLEEP }.map { it.first }
                if (short.size < 3 || rested.size < 3) return@mapNotNull null
                val a = short.average()
                val b = rested.average()
                if (a - b < 1.0) return@mapNotNull null
                fun f(x: Double) = "%.1f".format(java.util.Locale.FRANCE, x)
                "Après une nuit de moins de 6 h, « ${name(id).lowercase()} » est plus forte (${f(a)} contre ${f(b)} en moyenne sur 5)."
            }
    }
}
