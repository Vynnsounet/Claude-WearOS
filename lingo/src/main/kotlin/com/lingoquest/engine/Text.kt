package com.lingoquest.engine

import java.text.Normalizer

enum class Tense { PRESENT, PAST, FUTURE }

/** Spelling rules that depend on the next word: elision, contractions, a/an. */
internal object Spelling {

    private val H_ASPIRATED = setOf(
        "haricot", "héros", "hibou", "hockey", "homard", "hamburger", "honte", "huit", "hall", "hasard",
        "hanche", "harpe", "hérisson", "hurlement",
    )
    private const val FR_VOWELS = "aeiouàâäéèêëîïôöùûüœ"

    fun frVowelStart(word: String): Boolean {
        val w = word.lowercase()
        if (w.isEmpty()) return false
        val c = w[0]
        if (c in FR_VOWELS) return true
        if (c == 'h') return w !in H_ASPIRATED && w.length > 1 && w[1] in FR_VOWELS
        return false
    }

    private val ELIDING = mapOf(
        "le" to "l'", "la" to "l'", "je" to "j'", "ne" to "n'", "de" to "d'", "que" to "qu'",
        "se" to "s'", "me" to "m'", "te" to "t'",
    )
    private val POSSESSIVE_FEM = mapOf("ma" to "mon", "ta" to "ton")

    /** French: à+le→au, de+les→des, l'ami, j'aime, mon amie, cet homme, s'il… */
    fun french(tokens: List<String>): List<String> {
        val contracted = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            when {
                t == "à" && next == "le" -> { contracted += "au"; i += 2 }
                t == "à" && next == "les" -> { contracted += "aux"; i += 2 }
                t == "de" && next == "le" -> { contracted += "du"; i += 2 }
                t == "de" && next == "les" -> { contracted += "des"; i += 2 }
                else -> { contracted += t; i++ }
            }
        }
        val out = ArrayList<String>()
        for ((index, t) in contracted.withIndex()) {
            val next = contracted.getOrNull(index + 1)
            val vowel = next != null && frVowelStart(next)
            out += when {
                vowel && t in ELIDING -> ELIDING.getValue(t)
                vowel && t in POSSESSIVE_FEM -> POSSESSIVE_FEM.getValue(t)
                vowel && t == "ce" -> "cet"
                t == "si" && (next == "il" || next == "ils") -> "s'"
                else -> t
            }
        }
        return out
    }

    private val AN_EXCEPTIONS_A = setOf("university", "uniform", "european", "one", "unit", "useful", "user")
    private val AN_EXCEPTIONS_AN = setOf("hour", "honest", "heir", "honor")

    /** English: "a" becomes "an" before a vowel sound. */
    fun english(tokens: List<String>): List<String> = tokens.mapIndexed { i, t ->
        val next = tokens.getOrNull(i + 1)?.lowercase()
        if (t == "a" && next != null && next.isNotEmpty() &&
            ((next[0] in "aeiou" && next !in AN_EXCEPTIONS_A && !next.startsWith("uni") && !next.startsWith("use")) ||
                next in AN_EXCEPTIONS_AN)
        ) "an" else t
    }

    /** Shows tokens as a normal line of text: no space after an apostrophe (French elision). */
    fun join(tokens: List<String>): String {
        val sb = StringBuilder()
        for ((i, t) in tokens.withIndex()) {
            if (i > 0 && !tokens[i - 1].endsWith("'")) sb.append(' ')
            sb.append(t)
        }
        return sb.toString()
    }

    fun sentence(tokens: List<String>, period: Boolean): String {
        val s = join(tokens)
        val capital = s.replaceFirstChar { it.uppercase() }
        return if (period) "$capital." else capital
    }

    /** Case, spacing and apostrophe-insensitive form used to compare answers. */
    fun normalize(s: String): String =
        s.lowercase().replace('’', '\'').replace(Regex("[.,;:!?¿¡]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    /**
     * [normalize] plus accents removed, to tell "missing accent" from "wrong word".
     * Japanese is left alone (removing the dakuten would turn が into か); Russian only loses ё/й marks.
     */
    fun fold(s: String, lang: Lang = Lang.FR): String {
        val n = normalize(s).replace("œ", "oe").replace("æ", "ae")
        return when (lang) {
            Lang.JA -> n
            Lang.RU -> n.replace('ё', 'е')
            Lang.DE -> n.replace("ß", "ss").let { Normalizer.normalize(it, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "") }
            else -> Normalizer.normalize(n, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        }
    }
}
