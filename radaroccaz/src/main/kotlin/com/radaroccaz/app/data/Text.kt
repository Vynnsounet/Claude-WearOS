package com.radaroccaz.app.data

import java.text.Normalizer
import java.util.Locale

object Text {
    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    /** Lower case, no accents, single spaces: "Série  3 Évolution" → "serie 3 evolution". */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "")
            .lowercase(Locale.ROOT).replace('’', '\'').replace(spaces, " ").trim()

    /** Letters and digits only: "Classe-A 180 d" → "classea180d". */
    fun compact(s: String): String = normalize(s).filter { it.isLetterOrDigit() }

    /** "85 000 km" → 85000, "€ 14 990,-" → 14990, null if there is no digit. */
    fun digits(s: String?): Int? {
        if (s == null) return null
        val d = s.substringBefore(',').filter { it.isDigit() }
        return if (d.isEmpty() || d.length > 9) null else d.toInt()
    }

    /** First plausible 4-digit year (1950–2099) in the text: "03/2019" → 2019. */
    fun year(s: String?): Int? =
        s?.let { Regex("(19[5-9]\\d|20\\d\\d)").find(it)?.value?.toInt() }
}

object Format {
    private fun group(n: Int): String {
        val raw = kotlin.math.abs(n).toString()
        val sb = StringBuilder()
        raw.forEachIndexed { i, c ->
            if (i > 0 && (raw.length - i) % 3 == 0) sb.append(' ')
            sb.append(c)
        }
        return (if (n < 0) "-" else "") + sb
    }

    fun price(n: Int): String = "${group(n)} €"
    fun km(n: Int): String = "${group(n)} km"
    fun number(n: Int): String = group(n)
}
