package com.medipote.app.domain

import com.medipote.app.data.AppState
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/** What goes with better or worse days: one factor, one thing it is compared to (mood, a symptom…), in plain figures. */
data class DayEffect(
    /** "mood", "physical", "energy" or "symptom:<id>". */
    val outcome: String,
    val outcomeLabel: String,
    val key: String,
    /** When the factor applies: « après une nuit courte ». */
    val whenText: String,
    val emoji: String,
    val withDays: Int,
    val withoutDays: Int,
    /** Mean with the factor minus mean without. */
    val diff: Double,
    val worseWhenHigher: Boolean,
    val sensitive: Boolean,
    val scaleMax: Int,
    val strength: Double,
) {
    /** The factor goes with worse days (a stronger symptom, a lower mood). */
    val worse: Boolean get() = if (worseWhenHigher) diff > 0 else diff < 0

    val sentence: String get() {
        val more = if (worseWhenHigher) "plus intense" to "moins intense" else "plus haut" to "plus bas"
        val word = if (diff > 0) more.first else more.second
        return "« $outcomeLabel » : $word $whenText (${"%+.1f".format(Locale.FRANCE, diff)}/$scaleMax · $withDays jours avec, $withoutDays sans)."
    }
}

/**
 * « Ce qui change tes jours »: the comparison of « Ma soirée » extended to mood, form, energy and each followed symptom. Each
 * factor is compared on the days it applied to against the days it did not, over three months; nothing is said below 5 days on
 * each side or for a small difference, and it is never said as a cause. All of it is computed here, from the notes.
 */
object DayFactors {
    const val DAYS = 90
    const val MIN_WITH = 5
    const val MIN_WITHOUT = 5
    const val MIN_DIFF = 0.7
    const val MIN_DIFF_ENERGY = 0.5

    private fun outcomes(state: AppState): List<String> = buildList {
        add("mood"); add("physical")
        if (state.energyLog.isNotEmpty() || state.energy != null) add("energy")
        if (Wellbeing.enabled(state)) Wellbeing.tracked(state).forEach { add("symptom:$it") }
    }

    private fun label(outcome: String) = when {
        outcome == "mood" -> "Moral"
        outcome == "physical" -> "Forme physique"
        outcome == "energy" -> "Énergie"
        else -> Wellbeing.name(outcome.removePrefix("symptom:"))
    }

    private fun scaleMax(outcome: String) = if (outcome == "energy") 3 else 5

    /** The day's value of [outcome], null when the day says nothing about it. */
    private fun value(state: AppState, date: LocalDate, outcome: String): Double? {
        val entry = state.entryFor(date)
        return when {
            outcome == "mood" -> entry.mental?.toDouble()
            outcome == "physical" -> entry.physical?.toDouble()
            outcome == "energy" -> Wellbeing.energyOn(state, date)?.toDouble()
            else -> {
                // A symptom not noted on a day the person did note something counts as absent.
                val id = outcome.removePrefix("symptom:")
                val active = state.symptomLog.any { it.date == date } || entry.hasJournal
                if (!active) null else (Wellbeing.intensityOn(state, date, id) ?: 0).toDouble()
            }
        }
    }

    /** The factors that can be told about [date], with whether each applied. Only what is known appears. */
    fun factorsOn(state: AppState, date: LocalDate, evening: Set<String> = emptySet()): Map<String, Boolean> = buildMap {
        val night = state.sleep.firstOrNull { it.date == date }
        if (night != null) {
            val goal = state.settings.sleep.goalMinutes
            put("night_short", night.noSleep || night.asleepMinutes < goal - 60)
            if (night.quality != null || night.rested != null) put("night_bad", (night.quality ?: 5) <= 2 || (night.rested ?: 5) <= 2)
            put("night_woken", night.awakenings.size >= 3)
            // Every evening factor the person ever noted is known on a day with a night: it applied or it did not.
            val present = SleepFactors.keysOf(state, night).filter { !it.startsWith("auto_") }.toSet()
            (evening + present).forEach { put("ev:$it", it in present) }
        }
        val water = state.settings.water
        Water.ml(state, date).takeIf { it > 0 }?.let { put("water_low", it < Water.goalMl(water) / 2) }
        state.profile?.cycle?.takeIf { state.profile.menstruating }?.let { c ->
            Cycle.status(c, date)?.let { st ->
                put("period", st.phase == CyclePhase.MENSTRUAL)
                put("luteal", st.phase == CyclePhase.LUTEAL)
            }
        }
    }

    private fun whenText(key: String): String = when {
        key == "night_short" -> "après une nuit courte"
        key == "night_bad" -> "après une nuit peu reposante"
        key == "night_woken" -> "après une nuit avec plusieurs réveils"
        key == "water_low" -> "les jours de peu d'eau"
        key == "period" -> "pendant les règles"
        key == "luteal" -> "avant les règles"
        key.startsWith("ev:") -> "après un soir avec « ${SleepFactors.label(key.removePrefix("ev:"))} »"
        else -> key
    }

    private fun emoji(key: String) = when {
        key == "night_short" || key == "night_bad" || key == "night_woken" -> "🌙"
        key == "water_low" -> "💧"
        key == "period" || key == "luteal" -> "🌸"
        key.startsWith("ev:") -> SleepFactors.emoji(key.removePrefix("ev:"))
        else -> "•"
    }

    fun effects(state: AppState, today: LocalDate, days: Int = DAYS): List<DayEffect> {
        val dates = (0 until days).map { today.minusDays(it.toLong()) }
        val evening = dates.mapNotNull { date -> state.sleep.firstOrNull { it.date == date } }
            .flatMap { SleepFactors.keysOf(state, it).filter { k -> !k.startsWith("auto_") } }.toSet()
        val factors = dates.associateWith { factorsOn(state, it, evening) }
        val keys = factors.values.flatMap { it.keys }.toSet()
        val result = mutableListOf<DayEffect>()
        for (outcome in outcomes(state)) {
            val values = dates.associateWith { value(state, it, outcome) }
            val threshold = if (outcome == "energy") MIN_DIFF_ENERGY else MIN_DIFF
            val found = keys.mapNotNull { key ->
                val known = dates.filter { values[it] != null && factors.getValue(it).containsKey(key) }
                val with = known.filter { factors.getValue(it).getValue(key) }
                val without = known - with.toSet()
                if (with.size < MIN_WITH || without.size < MIN_WITHOUT) return@mapNotNull null
                val diff = with.map { values.getValue(it)!! }.average() - without.map { values.getValue(it)!! }.average()
                if (abs(diff) < threshold) return@mapNotNull null
                Triple(key, diff, with to without)
            }
            // « café » and « café après 16 h » on the same days say one thing: keep the precise one.
            val kept = found.filter { (key, _, sets) -> found.none { (other, _, s2) -> other != key && other.startsWith("$key=") && s2.first.size == sets.first.size } }
            kept.forEach { (key, diff, sets) ->
                result += DayEffect(
                    outcome, label(outcome), key, whenText(key), emoji(key), sets.first.size, sets.second.size, diff,
                    worseWhenHigher = outcome.startsWith("symptom:"),
                    sensitive = key.startsWith("ev:") && SleepFactors.isSensitive(key.removePrefix("ev:")),
                    scaleMax = scaleMax(outcome), strength = abs(diff) / threshold,
                )
            }
        }
        return result.sortedByDescending { it.strength }
    }

    /** The effects to show: private factors only when the person turned them on. */
    fun shown(state: AppState, today: LocalDate, limit: Int = 6): List<DayEffect> =
        effects(state, today).filter { !it.sensitive || state.settings.sleep.sensitiveFactors }.take(limit)
}
