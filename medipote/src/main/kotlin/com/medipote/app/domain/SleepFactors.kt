package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.SleepNight
import java.time.LocalDate
import kotlin.math.abs

enum class FactorGroup(val emoji: String, val label: String, val sensitive: Boolean = false) {
    DRINKS("☕", "Boissons"),
    MEAL("🍽️", "Repas"),
    EVENING("🌆", "Soirée"),
    PLACE("🛏️", "Autour du lit"),
    PRIVATE("🔒", "Plus personnel", sensitive = true),
}

/**
 * One thing of the evening that can touch the night. [choices] are the precisions of a factor ("après 16 h");
 * [exclusive] names a group of which only one can be chosen at a time ("how much was eaten").
 */
data class SleepFactor(
    val id: String,
    val emoji: String,
    val label: String,
    val group: FactorGroup,
    val choices: List<Pair<String, String>> = emptyList(),
    val exclusive: String? = null,
    /** Free text asked for ("which one?"), kept after an "=". */
    val askDetail: Boolean = false,
)

/** The effect of one factor on the nights, in plain figures. Never a cause: only what went with what. */
data class FactorEffect(
    val key: String,
    val emoji: String,
    val label: String,
    val withNights: Int,
    val withoutNights: Int,
    /** Differences "with minus without". */
    val asleepDiff: Int?,
    val latencyDiff: Int?,
    val qualityDiff: Double?,
    val restedDiff: Double?,
    /** How strong it is, for ranking (1 = just enough to be shown). */
    val strength: Double,
    val sensitive: Boolean,
    val auto: Boolean,
) {
    /** Good for sleep (positive) or not. */
    val good: Boolean get() = score > 0
    val score: Double get() = listOfNotNull(
        asleepDiff?.let { it / 45.0 }, latencyDiff?.let { -it / 15.0 }, qualityDiff?.let { it / 0.7 }, restedDiff?.let { it / 0.7 },
    ).maxByOrNull { abs(it) } ?: 0.0

    /** The sentence, with the night counts so the person sees how much it rests on. */
    val sentence: String get() {
        val parts = buildList {
            asleepDiff?.takeIf { abs(it) >= SleepFactors.MIN_ASLEEP }?.let { add("tu dors ${Sleep.format(abs(it))} ${if (it > 0) "de plus" else "de moins"}") }
            latencyDiff?.takeIf { abs(it) >= SleepFactors.MIN_LATENCY }?.let { add(if (it > 0) "tu mets ${Sleep.format(it)} de plus à t'endormir" else "tu t'endors ${Sleep.format(-it)} plus vite") }
            qualityDiff?.takeIf { abs(it) >= SleepFactors.MIN_RATING }?.let { add("ta nuit est ${if (it > 0) "mieux" else "moins bien"} notée (${"%+.1f".format(java.util.Locale.FRANCE, it)}/5)") }
            restedDiff?.takeIf { abs(it) >= SleepFactors.MIN_RATING }?.let { add("tu te sens ${if (it > 0) "plus" else "moins"} reposé·e (${"%+.1f".format(java.util.Locale.FRANCE, it)}/5)") }
        }
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + " ($withNights nuits avec, $withoutNights sans)."
    }
}

/**
 * « Ma soirée »: what the evening held (coffee, a late meal, a party…) and what it goes with in the nights. Everything
 * stays on the phone. The comparison is only ever « nights with » against « nights without »: it shows what goes together,
 * not what causes what, and says nothing until there are enough nights on each side.
 */
object SleepFactors {
    const val MIN_WITH = 5
    const val MIN_WITHOUT = 5
    const val MIN_ASLEEP = 30
    const val MIN_LATENCY = 10
    const val MIN_RATING = 0.5

    val ALL: List<SleepFactor> = listOf(
        SleepFactor("coffee", "☕", "Café", FactorGroup.DRINKS, listOf("morning" to "le matin", "afternoon" to "l'après-midi", "late" to "après 16 h")),
        SleepFactor("tea", "🍵", "Thé", FactorGroup.DRINKS),
        SleepFactor("hot", "♨️", "Boisson chaude", FactorGroup.DRINKS),
        SleepFactor("alcohol", "🍷", "Alcool", FactorGroup.DRINKS, listOf("little" to "un verre", "lots" to "plusieurs")),
        SleepFactor("water", "💧", "Beaucoup d'eau", FactorGroup.DRINKS),
        SleepFactor("meal_early", "🌤️", "Mangé tôt", FactorGroup.MEAL, exclusive = "mealtime"),
        SleepFactor("meal_late", "🌙", "Mangé tard", FactorGroup.MEAL, exclusive = "mealtime"),
        SleepFactor("amount_lots", "🍝", "Beaucoup mangé", FactorGroup.MEAL, exclusive = "amount"),
        SleepFactor("amount_normal", "🍽️", "Mangé normalement", FactorGroup.MEAL, exclusive = "amount"),
        SleepFactor("amount_little", "🥣", "Peu mangé", FactorGroup.MEAL, exclusive = "amount"),
        SleepFactor("amount_none", "🚫", "Pas mangé", FactorGroup.MEAL, exclusive = "amount"),
        SleepFactor("meal_healthy", "🥗", "Repas sain", FactorGroup.MEAL, exclusive = "kind"),
        SleepFactor("meal_fast", "🍔", "Fast-food, gras", FactorGroup.MEAL, exclusive = "kind"),
        SleepFactor("party", "🎉", "Soirée, sortie", FactorGroup.EVENING),
        SleepFactor("sport", "🏃", "Sport", FactorGroup.EVENING),
        SleepFactor("screens", "📱", "Écrans avant de dormir", FactorGroup.EVENING),
        SleepFactor("nap", "😴", "Sieste", FactorGroup.EVENING),
        SleepFactor("stress", "😬", "Stress", FactorGroup.EVENING),
        SleepFactor("sleeping_pill", "💊", "Médicament pour dormir", FactorGroup.EVENING),
        SleepFactor("hot_room", "🥵", "Chaleur", FactorGroup.PLACE),
        SleepFactor("noise", "🔊", "Bruit", FactorGroup.PLACE),
        SleepFactor("away", "🧳", "Pas chez moi", FactorGroup.PLACE),
        SleepFactor("snoring", "🛌", "Ronflements", FactorGroup.PLACE),
        SleepFactor("pain", "🤕", "Douleur", FactorGroup.PLACE),
        SleepFactor("nightmares", "😨", "Cauchemars", FactorGroup.PLACE),
        SleepFactor("sex", "💞", "Rapport sexuel", FactorGroup.PRIVATE),
        SleepFactor("cannabis", "🌿", "Cannabis", FactorGroup.PRIVATE),
        SleepFactor("nicotine", "🚬", "Nicotine", FactorGroup.PRIVATE),
        SleepFactor("drug", "⚠️", "Autre substance", FactorGroup.PRIVATE, askDetail = true),
    )

    private val BY_ID = ALL.associateBy { it.id }

    /** The old tags of 1.x nights, now mapped to factors. */
    private val LEGACY = mapOf(
        "Écrans avant de dormir" to "screens", "Caféine" to "coffee", "Alcool" to "alcohol", "Sport" to "sport", "Repas tardif" to "meal_late",
        "Stress" to "stress", "Douleur" to "pain", "Cauchemars" to "nightmares", "Ronflements" to "snoring", "Sieste" to "nap",
        "Médicament pour dormir" to "sleeping_pill", "Pas chez moi" to "away",
    )

    fun byId(id: String): SleepFactor? = BY_ID[id]

    /** "coffee=late" → "coffee" and "late". */
    fun baseOf(tag: String): String = tag.substringBefore('=')
    fun detailOf(tag: String): String? = tag.substringAfter('=', "").takeIf { it.isNotBlank() }

    /** A stored tag, upgraded if it is an old one. */
    fun normalize(tag: String): String = LEGACY[tag] ?: tag

    fun isSensitive(tag: String): Boolean = byId(baseOf(tag))?.group?.sensitive == true

    /** What to say about a tag: « Café (après 16 h) », « Autre substance : tilleul », or the person's own words. */
    fun label(tag: String): String {
        val factor = byId(baseOf(tag)) ?: return tag
        val detail = detailOf(tag) ?: return factor.label
        val shown = factor.choices.firstOrNull { it.first == detail }?.second ?: detail
        return if (factor.askDetail) "${factor.label} : $shown" else "${factor.label} ($shown)"
    }

    fun emoji(tag: String): String = byId(baseOf(tag))?.emoji ?: "✏️"

    /** Choosing [tag] among [selected]: an exclusive choice replaces its siblings, a precision replaces the factor's previous one. */
    fun toggle(selected: List<String>, tag: String): List<String> {
        val factor = byId(baseOf(tag))
        val withoutSelf = selected.filter { baseOf(it) != baseOf(tag) }
        if (selected.any { baseOf(it) == baseOf(tag) }) return withoutSelf
        val withoutSiblings = if (factor?.exclusive != null) withoutSelf.filter { byId(baseOf(it))?.exclusive != factor.exclusive } else withoutSelf
        return withoutSiblings + tag
    }

    /** Gives a factor a precision ("coffee" → "coffee=late"); choosing the same one again goes back to the plain factor. */
    fun choose(selected: List<String>, id: String, choice: String): List<String> {
        val current = selected.firstOrNull { baseOf(it) == id }
        val next = if (current == "$id=$choice") id else "$id=$choice"
        return selected.filter { baseOf(it) != id } + next
    }

    /** Writes the free text asked for a factor ("which substance?"); empty text goes back to the plain factor. */
    fun describe(selected: List<String>, id: String, text: String): List<String> =
        selected.filter { baseOf(it) != id } + (text.trim().take(40).takeIf { it.isNotBlank() }?.let { "$id=${it.replace('|', ' ')}" } ?: id)

    /** Factors the person can pick today: the hidden ones and, until turned on, the sensitive ones are left out. */
    fun visible(settings: com.medipote.app.data.SleepSettings): List<SleepFactor> =
        ALL.filter { it.id !in settings.hiddenFactors && (!it.group.sensitive || settings.sensitiveFactors) }

    /** Evening factors noted on [date]'s evening, and those still on the night itself (old notes). */
    fun noted(state: AppState, night: SleepNight): List<String> {
        val own = (state.evenings[night.date.minusDays(1).toString()].orEmpty() + night.tags.map(::normalize)).distinct()
        // Sexual activity already noted in the cycle log counts too, without being typed twice.
        val fromCycleLog = "sex".takeIf { state.entryFor(night.date.minusDays(1)).intercourse != null && own.none { baseOf(it) == "sex" } }
        return own + listOfNotNull(fromCycleLog)
    }

    /** What the app already knows of the evening before [night]: no typing needed. */
    fun automatic(state: AppState, night: SleepNight): List<String> {
        val evening = night.date.minusDays(1)
        val entry = state.entryFor(evening)
        return buildList {
            val low = listOfNotNull(entry.physical, entry.mental).minOrNull()
            if (low != null && low <= 2) add("auto_lowmood")
            if (listOfNotNull(entry.physical, entry.mental).minOrNull().let { it != null && it >= 4 }) add("auto_goodmood")
            state.symptomLog.filter { it.date == evening && it.intensity >= 3 }.forEach { add("auto_symptom_${it.symptom}") }
            if (state.settings.water.enabled && entry.waterGlasses > 0 && entry.waterGlasses < state.settings.water.goalGlasses / 2) add("auto_lowwater")
            if (state.doseLogs.any { it.takenAt.toLocalDate() == evening && it.takenAt.hour * 60 + it.takenAt.minute >= 22 * 60 }) add("auto_latedose")
            state.profile?.cycle?.takeIf { state.profile.menstruating }?.let { c ->
                Cycle.status(c, evening)?.let { st ->
                    when (st.phase) {
                        CyclePhase.MENSTRUAL -> add("auto_period")
                        CyclePhase.LUTEAL -> add("auto_luteal")
                        else -> Unit
                    }
                }
            }
        }
    }

    fun autoLabel(key: String): String = when {
        key == "auto_lowmood" -> "Journée au moral ou au corps difficile"
        key == "auto_goodmood" -> "Journée en forme"
        key == "auto_lowwater" -> "Peu d'eau dans la journée"
        key == "auto_latedose" -> "Prise de médicament après 22 h"
        key == "auto_period" -> "Pendant les règles"
        key == "auto_luteal" -> "Avant les règles (phase lutéale)"
        key.startsWith("auto_symptom_") -> "${Wellbeing.name(key.removePrefix("auto_symptom_"))} dans la journée"
        else -> key
    }

    fun autoEmoji(key: String): String = when {
        key == "auto_lowmood" -> "🌧️"; key == "auto_goodmood" -> "🌤️"; key == "auto_lowwater" -> "💧"; key == "auto_latedose" -> "💊"
        key == "auto_period" || key == "auto_luteal" -> "🌸"
        key.startsWith("auto_symptom_") -> Wellbeing.emoji(key.removePrefix("auto_symptom_"))
        else -> "•"
    }

    /** Every factor (chosen, then automatic) that went with [night], by tag: the unit of the comparison. */
    private fun keysOf(state: AppState, night: SleepNight): Set<String> = buildSet {
        noted(state, night).forEach { tag ->
            add(tag)
            // A factor with a precision also counts as itself: « café » and « café après 16 h ».
            if (detailOf(tag) != null) add(baseOf(tag))
        }
        addAll(automatic(state, night))
    }

    private fun mean(values: List<Double>) = values.takeIf { it.isNotEmpty() }?.average()

    /**
     * What goes with better or worse nights, strongest first: only factors with at least [MIN_WITH] nights with and [MIN_WITHOUT]
     * without, and a difference worth saying (30 min of sleep, 10 min to fall asleep, half a point on a rating).
     * Sensitive ones are returned too, marked, so the screen can hide them.
     */
    fun effects(state: AppState, nights: List<SleepNight>): List<FactorEffect> {
        val keysByNight = nights.associateWith { keysOf(state, it) }
        val keys = keysByNight.values.flatten().toSet()
        val withByKey = keys.associateWith { k -> nights.filter { k in keysByNight[it].orEmpty() } }
        return keys.mapNotNull { key ->
            val with = withByKey.getValue(key)
            // « Café » and « café après 16 h » on the very same nights say one thing: keep the precise one.
            if (keys.any { other -> other != key && other.startsWith("$key=") && withByKey.getValue(other).size == with.size }) return@mapNotNull null
            val without = nights - with.toSet()
            if (with.size < MIN_WITH || without.size < MIN_WITHOUT) return@mapNotNull null
            fun diff(f: (SleepNight) -> Double?): Double? {
                val a = mean(with.mapNotNull(f)); val b = mean(without.mapNotNull(f))
                return if (a == null || b == null || with.mapNotNull(f).size < MIN_WITH || without.mapNotNull(f).size < MIN_WITHOUT) null else a - b
            }
            val asleep = diff { it.asleepMinutes.toDouble() }?.toInt()
            val latency = diff { n -> n.latencyMinutes.toDouble().takeIf { !n.noSleep } }?.toInt()
            val quality = diff { it.quality?.toDouble() }
            val rested = diff { it.rested?.toDouble() }
            val strength = listOfNotNull(
                asleep?.let { abs(it) / MIN_ASLEEP.toDouble() }, latency?.let { abs(it) / MIN_LATENCY.toDouble() },
                quality?.let { abs(it) / MIN_RATING }, rested?.let { abs(it) / MIN_RATING },
            ).maxOrNull() ?: 0.0
            if (strength < 1.0) return@mapNotNull null
            val auto = key.startsWith("auto_")
            FactorEffect(
                key, if (auto) autoEmoji(key) else emoji(key), if (auto) autoLabel(key) else label(key), with.size, without.size,
                asleep, latency, quality, rested, strength, sensitive = !auto && isSensitive(key), auto = auto,
            )
        }.sortedByDescending { it.strength }
    }

    /** The effects the person has chosen to see (sensitive ones only when turned on). */
    fun shown(state: AppState, nights: List<SleepNight>, limit: Int = 5): List<FactorEffect> =
        effects(state, nights).filter { !it.sensitive || state.settings.sleep.sensitiveFactors }.take(limit)

    /** How many nights a factor needs before it can be compared; for the screen to explain waiting. */
    fun nightsNeeded(state: AppState, nights: List<SleepNight>): Int = (MIN_WITH + MIN_WITHOUT - nights.size).coerceAtLeast(0)

    fun setEvening(state: AppState, evening: LocalDate, tags: List<String>): AppState {
        val clean = tags.map(::normalize).filter { it.isNotBlank() }.distinct()
        val map = state.evenings.toMutableMap()
        if (clean.isEmpty()) map.remove(evening.toString()) else map[evening.toString()] = clean
        // Keep a year of evenings: older ones have long fed the figures of their nights.
        val limit = evening.minusDays(400).toString()
        return state.copy(evenings = map.filterKeys { it >= limit })
    }
}
