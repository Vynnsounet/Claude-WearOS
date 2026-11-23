package com.lingoquest.engine

/** What the player has to translate, from a single word up to a multi-clause sentence. */
enum class Stage(val baseXp: Int) {
    WORD(10),
    NOUN_PHRASE(15),
    NOUN_PHRASE_ADJ(20),
    NOUN_PHRASE_COMPLEX(30),
    SENTENCE_SIMPLE(40),
    SENTENCE_COMPLEX(60),
    ;

    val isSentence get() = this == SENTENCE_SIMPLE || this == SENTENCE_COMPLEX

    companion object {
        /** Which stage a run is at after [step] correct answers in a row. */
        fun forStep(step: Int): Stage = when {
            step < 5 -> WORD
            step < 10 -> NOUN_PHRASE
            step < 15 -> NOUN_PHRASE_ADJ
            step < 20 -> NOUN_PHRASE_COMPLEX
            step < 30 -> SENTENCE_SIMPLE
            else -> SENTENCE_COMPLEX
        }
    }
}

enum class Det { THE, A, MY, YOUR, OUR, THEIR, THIS }

enum class Conn(val en: List<String>, val fr: List<String>) {
    AND(listOf("and"), listOf("et")),
    BUT(listOf("but"), listOf("mais")),
    BECAUSE(listOf("because"), listOf("parce", "que")),
    WHEN(listOf("when"), listOf("quand")),
    IF(listOf("if"), listOf("si")),
}

/** Everything the generator may use for one challenge, derived from the run's progress and CEFR level. */
data class Spec(
    val stage: Stage,
    val dets: List<Det>,
    val maxAdjectives: Int,
    val modifiers: Boolean,
    val relative: Boolean,
    val negation: Boolean,
    val past: Boolean,
    val future: Boolean,
    val modal: Boolean,
    val infinitiveComplement: Boolean,
    val adverbs: Boolean,
    val clauses: Int,
    val connectors: List<Conn>,
) {
    companion object {
        fun of(stage: Stage, cefr: Cefr, step: Int): Spec {
            val lvl = cefr.ordinal
            val dets = if (lvl >= 1) Det.entries else listOf(Det.THE, Det.A)
            val connectors = buildList {
                add(Conn.AND)
                if (lvl >= 1) add(Conn.BUT)
                if (lvl >= 2) { add(Conn.BECAUSE); add(Conn.WHEN) }
                if (lvl >= 3) add(Conn.IF)
            }
            val sentence = stage.isSentence
            val complex = stage == Stage.SENTENCE_COMPLEX
            val maxClauses = if (lvl >= 2) 3 else 2
            val clauses = when {
                !complex -> 1
                step < 40 -> 1
                step < 55 -> 2
                else -> maxClauses
            }.coerceAtMost(maxClauses)
            return Spec(
                stage = stage,
                dets = dets,
                maxAdjectives = when (stage) {
                    Stage.WORD, Stage.NOUN_PHRASE -> 0
                    Stage.NOUN_PHRASE_ADJ, Stage.SENTENCE_SIMPLE -> 1
                    else -> 2
                },
                modifiers = stage == Stage.NOUN_PHRASE_COMPLEX || complex,
                relative = complex && lvl >= 4,
                negation = sentence && lvl >= 1,
                past = sentence && lvl >= 1,
                future = sentence && lvl >= 2,
                modal = sentence,
                infinitiveComplement = sentence && lvl >= 1,
                adverbs = sentence && (lvl >= 1 || complex),
                clauses = clauses,
                connectors = connectors,
            )
        }
    }
}

/** One adjustable word in the challenge, shown to the player with an optional hint such as "you (tu)". */
internal data class T(val s: String, val hint: String? = null)

/**
 * A ready-to-play question. The player answers either by typing ([typed], single words) or by
 * tapping the tiles of [bank] in order (phrases and sentences).
 */
data class Challenge(
    val stage: Stage,
    val direction: Direction,
    /** The text to translate, ready to display. */
    val prompt: String,
    /** The canonical answer, ready to display. */
    val answer: String,
    /** Every form accepted when typing; for tile answers, just the canonical sentence. */
    val accepted: List<String>,
    /** Shuffled tiles (answer words plus distractors); empty when the answer is typed. */
    val bank: List<String>,
    /** Lexicon entries used, so mistakes can be tracked per word. */
    val entryIds: List<String>,
    val step: Int,
) {
    val typed get() = bank.isEmpty()
}

data class Verdict(
    val correct: Boolean,
    /** Correct, but with accents missing: still counts, with a reminder. */
    val accentWarning: Boolean = false,
)

object Judge {
    private val ARTICLES = listOf("the ", "a ", "an ", "to ", "le ", "la ", "les ", "un ", "une ", "des ", "l'")

    fun check(challenge: Challenge, input: String): Verdict {
        val given = Spelling.normalize(input)
        if (given.isEmpty()) return Verdict(false)
        val accepted = challenge.accepted.map(Spelling::normalize)
        if (given in accepted) return Verdict(true)
        // A single word may be typed with its article ("la maison", "to eat").
        val stripped = ARTICLES.firstOrNull { given.startsWith(it) }?.let { given.removePrefix(it).trim() }
        if (challenge.typed && !stripped.isNullOrEmpty() && stripped in accepted) return Verdict(true)
        val folded = challenge.accepted.map(Spelling::fold)
        val foldedGiven = Spelling.fold(input)
        if (foldedGiven in folded) return Verdict(true, accentWarning = true)
        if (challenge.typed && !stripped.isNullOrEmpty() && Spelling.fold(stripped) in folded) {
            return Verdict(true, accentWarning = true)
        }
        return Verdict(false)
    }

    /** Joins the tiles the player tapped into the sentence they built. */
    fun fromTiles(tiles: List<String>): String = Spelling.join(tiles)
}
