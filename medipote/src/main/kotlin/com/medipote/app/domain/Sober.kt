package com.medipote.app.domain

/**
 * Sober mode: the care stays exactly the same, only the game is put away (points, streak, shop, badges,
 * celebrations). Points are still counted quietly, so turning it off again loses nothing.
 */
object Sober {
    private val POINTS = Regex("""\s*[:·]?\s*\+\d+\s*pts(\s+bonus)?""")

    /** A message without its points: "Journal enregistré : +5 pts" becomes "Journal enregistré"; nothing left gives null. */
    fun clean(message: String): String? {
        val text = POINTS.replace(message, "").replace(Regex("""\s{2,}"""), " ").trim().trim('·', ':', ' ')
        return text.takeIf { it.any(Char::isLetter) }
    }
}
