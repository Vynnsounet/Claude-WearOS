package com.medipote.app.ai

import com.medipote.app.domain.Urgency

/**
 * The last word, on the phone: whatever the model writes, a line about a medication or a diagnosis is held back, and
 * the level of the answer is never lower than what the app's own rules found. The model can raise the level, never lower it.
 */
object AiGuard {
    private val MEDICATION = listOf(
        "paracétamol", "paracetamol", "doliprane", "dafalgan", "efferalgan", "ibuprofène", "ibuprofene", "advil", "nurofen", "aspirine",
        "kardegic", "spasfon", "antibiotique", "anti-inflammatoire", "antidouleur", "antalgique", "somnifère", "mélatonine", "melatonine",
        "magnésium", "magnesium", "vitamine", "complément", "compléments", "probiotique", "tisane de", "huile essentielle", "cbd", "codéine",
        "tramadol", "morphine", "corticoïde", "cortisone", "lévothyrox", "levothyrox", "metformine", "insuline",
    )
    private val DOSE = Regex("""\b\d+([.,]\d+)?\s?(mg|µg|mcg|ml|ui|gouttes?)\b""", RegexOption.IGNORE_CASE)
    private val ORDERS = listOf(
        "arrête ton traitement", "arrête de prendre", "arrêtez de prendre", "diminue ta dose", "augmente ta dose", "double ta dose", "double la dose",
        "prends un comprimé", "prends du", "prenez du", "prends de la", "change ton traitement", "saute ta prise",
    )
    private val DIAGNOSIS = listOf("tu as une ", "tu as un ", "tu souffres de", "tu es atteint", "tu fais une ", "c'est sûrement", "c'est certainement", "c'est une maladie", "diagnostic de")

    fun banned(line: String): Boolean {
        val t = line.lowercase()
        return MEDICATION.any { it in t } || DOSE.containsMatchIn(t) || ORDERS.any { it in t } || DIAGNOSIS.any { it in t }
    }

    /** The answer cleaned up, with its level raised to at least [floor] (the app's rules). */
    fun apply(answer: AiAnswer, floor: Urgency?): AiAnswer {
        if (answer.raw) {
            // An unreadable shape is shown only if it passes the same tests.
            return if (banned(answer.summary)) answer.copy(summary = "", filtered = true, level = floor) else answer.copy(level = max(answer.level, floor))
        }
        var filtered = false
        fun keep(line: String) = !banned(line).also { if (it) filtered = true }
        val points = answer.explanations.filter { keep(it.title) && keep(it.detail) }
        val relief = answer.relief.filter(::keep)
        val summary = if (keep(answer.summary)) answer.summary else ""
        val reasonOk = keep(answer.reason)
        val questions = answer.questions.filter(::keep)
        return answer.copy(
            summary = summary, explanations = points, relief = relief, questions = questions,
            reason = if (reasonOk) answer.reason else "", level = max(answer.level, floor), filtered = filtered,
        )
    }

    /** The more urgent of two levels; null = none. */
    fun max(a: Urgency?, b: Urgency?): Urgency? = when {
        a == null -> b
        b == null -> a
        else -> if (a.ordinal >= b.ordinal) a else b
    }
}
