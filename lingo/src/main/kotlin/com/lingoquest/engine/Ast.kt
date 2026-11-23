package com.lingoquest.engine

import kotlin.random.Random

/**
 * Language-neutral sentence structure. The planner builds one tree from the unlocked words, then a
 * [Realizer] per language turns it into words, so both sides of a translation always say the same thing.
 */
internal sealed interface Node

internal enum class Pron(val known: Boolean) {
    I(false), YOU_TU(false), YOU_VOUS(false), HE(true), SHE(true), WE(false), THEY_M(true), THEY_F(true)
}

internal enum class ModPrep { WITH, IN }

internal class NpNode(
    val noun: Noun,
    val plural: Boolean,
    val det: Det,
    /** In English order: "before" adjectives (size, age, beauty) first. */
    val adjs: List<Adj>,
    /** For "your": the French "tu" form (true) or "vous" form (false). */
    val yourTu: Boolean,
    val modifier: ModNode? = null,
    val relative: RelNode? = null,
) : Node {
    val gender get() = noun.gender
}

internal class ModNode(val prep: ModPrep, val np: NpNode)

/** "that eats an apple": present tense, the subject is the head noun. */
internal class RelNode(val verb: Verb, val obj: NpNode?)

internal sealed interface SubjNode
internal class SubjPron(val pron: Pron) : SubjNode
internal class SubjNp(val np: NpNode) : SubjNode

internal sealed interface PredNode

/** be + adjective */
internal class CopAdj(val adj: Adj) : PredNode

/** be in/at a place */
internal class CopLoc(val place: NpNode) : PredNode

/** A plain verb with its complements. */
internal class VerbPred(val verb: Verb, val obj: NpNode?, val loc: NpNode?, val adv: Adverb?) : PredNode

/** can / must + infinitive */
internal class ModalPred(val modal: Verb, val inner: VerbPred) : PredNode

/** want / try / start… + infinitive */
internal class InfPred(val outer: Verb, val inner: VerbPred) : PredNode

internal class ClauseNode(val subj: SubjNode, val pred: PredNode, val tense: Tense, val neg: Boolean)

internal class SentenceNode(val clauses: List<ClauseNode>, val joiners: List<Conn>) : Node

/** What a language can express, so the planner never builds a sentence the realizer cannot say. */
internal data class Caps(
    val relative: Boolean = true,
    val negatedObject: Boolean = true,
    val connectors: Set<Conn> = Conn.entries.toSet(),
    val infinitive: (Verb) -> Boolean = { true },
    /** The past tense marks the subject's gender (Russian), so only subjects with a known gender are used. */
    val pastNeedsGender: Boolean = false,
    /** "can" / "must" agree with the subject's gender (Russian "должен / должна"). */
    val modalNeedsGender: Boolean = false,
)

/** Collects what a realizer used: grammar rules and plausible wrong tiles. */
internal class Sink(val course: Lang) {
    val rules = ArrayList<RuleInfo>()
    val alts = ArrayList<String>()

    fun rule(r: Rule) = rule(r.info(course))
    fun rule(info: RuleInfo) { if (rules.none { it.key == info.key }) rules += info }
    fun alt(vararg s: String) { alts += s }
    fun alt(s: List<String>) { alts += s }
}

internal interface Realizer {
    val lang: Lang
    val caps: Caps get() = Caps()

    /** Turns a tree into the words of this language (with spelling rules applied). */
    fun realize(node: Node, sink: Sink): List<T>

    /** Shows words as one line of text. */
    fun join(tokens: List<String>): String = tokens.joinToString(" ")

    /** Capitalisation and final punctuation. */
    fun sentence(tokens: List<String>, period: Boolean): String {
        val s = join(tokens).replaceFirstChar { it.uppercase() }
        return if (period) "$s." else s
    }
}

internal object Realizers {
    fun of(lang: Lang, lexicon: Lexicon): Realizer = when (lang) {
        Lang.EN -> EnglishRealizer
        Lang.FR -> FrenchRealizer
        Lang.ES -> SpanishRealizer(lexicon)
        Lang.DE -> GermanRealizer(lexicon)
        Lang.RU -> RussianRealizer(lexicon)
        Lang.JA -> JapaneseRealizer(lexicon)
    }
}

