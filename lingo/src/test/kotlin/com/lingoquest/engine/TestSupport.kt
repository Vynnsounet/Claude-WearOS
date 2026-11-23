package com.lingoquest.engine

import kotlin.random.Random
import org.junit.Assert.assertTrue

/** Can the answer be built from the tiles, each used at most once? */
fun solvable(answer: String, bank: List<String>): Boolean {
    fun go(pos: Int, free: List<String>): Boolean {
        if (pos >= answer.length) return true
        for ((i, tile) in free.withIndex()) {
            if (!answer.startsWith(tile, pos)) continue
            val end = pos + tile.length
            val ok = end == answer.length || tile.endsWith("'") || answer[end] == ' '
            if (!ok) continue
            val next = if (end < answer.length && answer[end] == ' ') end + 1 else end
            if (go(next, free.filterIndexed { j, _ -> j != i })) return true
        }
        return false
    }
    return go(0, bank)
}

/** A tiny vocabulary, so every grammar pattern shows up quickly. */
const val MINI_BASE = """
N|A1|apple|-|pomme|-|f|F
N|A1|man|men|homme|hommes|m|P
N|A1|woman|women|femme|femmes|f|P
N|A1|dog|-|chien|-|m|A
N|A1|house|-|maison|-|f|LH
N|A1|park|-|parc|-|m|L
N|A1|water|-|eau|eaux|f|DU
A|A1|big|grand|-|-|-|B
A|A1|tired|fatigué|-|-|-|P
A|A1|red|rouge|-|-|-|P
A|A1|good|bon|bonne|bons|bonnes|B
V|A1|eat|eats|ate|manger|mange,manges,mange,mangeons,mangez,mangent|mangé|avoir|obj=F
V|A1|see|sees|saw|voir|vois,vois,voit,voyons,voyez,voient|vu|avoir|obj=PALV
V|A1|go|goes|went|aller|vais,vas,va,allons,allez,vont|allé|être|loc=to:à
V|A1|come|comes|came|venir|viens,viens,vient,venons,venez,viennent|venu|être|loc=to:à
V|A1|must|must|-|devoir|dois,dois,doit,devons,devez,doivent|dû|avoir|modal=1
V|A1|try|tries|tried|essayer|essaie,essaies,essaie,essayons,essayez,essaient|essayé|avoir|inf=to:de
V|A1|live|lives|lived|habiter|habite,habites,habite,habitons,habitez,habitent|habité|avoir|loc=in:dans
V|A1|can|can|-|pouvoir|peux,peux,peut,pouvons,pouvez,peuvent|pu|avoir|modal=1
V|A1|want|wants|wanted|vouloir|veux,veux,veut,voulons,voulez,veulent|voulu|avoir|obj=F|inf=to:
V|A1|decide|decides|decided|décider|décide,décides,décide,décidons,décidez,décident|décidé|avoir|inf=to:de
V|A1|start|starts|started|commencer|commence,commences,commence,commençons,commencez,commencent|commencé|avoir|inf=to:à
D|A1|today|aujourd'hui|pnf
"""

/** The mini vocabulary translated into [lang] with the given translation lines. */
fun miniLexicon(lang: Lang, translations: String): Lexicon {
    val base = Lexicon.parse(MINI_BASE.trimIndent())
    return Lexicon(base.entries, mapOf(lang to Lexicon.parseTranslations(translations.trimIndent(), base)))
}

/**
 * Generates many sentences French → [lang] (prompt in French, answer in [lang]) and checks that every
 * French pattern is rendered with the expected pattern in [lang].
 */
fun checkPairs(
    lex: Lexicon, lang: Lang, pairs: List<Pair<String, String>>, samples: Int = 30000, toFrench: Boolean = false,
    /** French sentences matching this are not checked (e.g. clause order differs). */
    skipIf: Regex? = null,
) {
    val direction = Direction.of(lang, toFrench)
    val seen = HashSet<String>()
    val entries = lex.forCourse(lang).entries
    repeat(samples) { seed ->
        val step = 22 + seed % 50
        val c = Generator(Random(seed), lex).generate(entries, Stage.forStep(step), Cefr.C1, step, direction)
        if (!c.stage.isSentence) return@repeat
        val src = (if (toFrench) c.answer else c.prompt).lowercase().replace(Regex("\\s*\\([^)]*\\)"), "")
        val dst = (if (toFrench) c.prompt else c.answer).lowercase()
        if (skipIf != null && skipIf.containsMatchIn(src)) return@repeat
        for ((frRe, foRe) in pairs) {
            if (!Regex("(?U)$frRe").containsMatchIn(src)) continue
            assertTrue("'$src' should be /$foRe/ but was '$dst'", Regex("(?U)$foRe").containsMatchIn(dst))
            seen += frRe
        }
    }
    val missing = pairs.map { it.first }.filter { it !in seen }
    assertTrue("patterns never generated in $lang: $missing", missing.isEmpty())
}
