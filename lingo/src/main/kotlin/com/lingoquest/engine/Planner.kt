package com.lingoquest.engine

import kotlin.random.Random

/**
 * Chooses what to say: builds a random sentence tree from the unlocked words, respecting the
 * stage of the series, the player's CEFR level and what the learned language can express.
 */
internal class Planner(
    private val rnd: Random,
    unlocked: List<Entry>,
    private val missed: Set<String>,
    val spec: Spec,
    private val caps: Caps,
    /** True when the learned language marks the subject's gender in the past tense (Russian). */
    private val pastNeedsGender: Boolean = false,
) {
    private val nouns = unlocked.filterIsInstance<Noun>()
    private val adjs = unlocked.filterIsInstance<Adj>()
    private val verbs = unlocked.filterIsInstance<Verb>()
    private val advs = unlocked.filterIsInstance<Adverb>()
    private val recent = unlocked.takeLast(12).map { it.id }.toSet()

    /** Every lexicon entry used, for tracking mistakes per word. */
    val ids = ArrayList<String>()

    private fun <E : Entry> pick(list: List<E>): E? {
        if (list.isEmpty()) return null
        val weights = list.map { 1.0 + (if (it.id in recent) 2.0 else 0.0) + (if (it.id in missed) 2.0 else 0.0) }
        var r = rnd.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0) return list[i].also { ids += it.id }
        }
        return list.last().also { ids += it.id }
    }

    // ───────── Adjective / noun compatibility ─────────

    private val adjLimitedToPeople = setOf(
        "tired", "happy", "sad", "sick", "proud", "brave", "lucky", "polite", "clever", "honest", "patient", "kind",
        "curious", "generous", "wise", "lazy", "foolish", "careful", "ambitious", "stubborn", "modest", "ruthless",
        "wealthy", "rich", "weak", "young",
    )
    private val adjLimitedToThings = setOf(
        "empty", "full", "heavy", "expensive", "cheap", "fragile", "clean", "dirty", "ancient",
    )
    private val adjLimitedToMovers = setOf("fast", "slow")
    private val adjLimitedToFoodAndPlaces = setOf("hot", "cold")
    private val colors = setOf("red", "blue", "green", "yellow", "black", "white")
    private val adjOkForAbstract = setOf(
        "big", "small", "good", "bad", "new", "old", "important", "different", "interesting", "serious",
        "dangerous", "possible", "necessary", "easy", "difficult", "ordinary", "remarkable", "mysterious",
        "first", "last", "next", "other", "same", "long", "huge", "tiny", "fragile", "reliable", "ancient",
    )

    private fun adjFits(adj: Adj, tags: String): Boolean {
        val key = adj.en[0]
        val living = 'P' in tags || 'A' in tags
        val abstract = tags.all { it in "XU" }
        return when {
            key in adjLimitedToPeople -> living
            key in adjLimitedToMovers -> living || 'V' in tags
            key in adjLimitedToFoodAndPlaces -> tags.any { it in "FDLH" }
            key in adjLimitedToThings -> !living && !abstract
            key in colors -> 'P' !in tags && !abstract
            abstract -> key in adjOkForAbstract
            else -> true
        }
    }

    // ───────── Noun phrases ─────────

    private fun weightedDet(dets: List<Det>): Det {
        val weights = dets.map { if (it == Det.THE) 4.0 else if (it == Det.A) 3.0 else 1.0 }
        var r = rnd.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0) return dets[i]
        }
        return dets.last()
    }

    /** [tags] restricts the head noun (any listed tag matches); null accepts any noun. */
    fun nounPhrase(
        tags: String?, dets: List<Det>, maxAdj: Int, modifiers: Boolean, allowRelative: Boolean,
        forcePlural: Boolean? = null,
    ): NpNode? {
        val noun = pick(nouns.filter { tags == null || it.hasTag(tags) }) ?: return null
        val plural = if (noun.uncountable) false else forcePlural ?: (rnd.nextDouble() < 0.35)

        val adjective = ArrayList<Adj>()
        if (maxAdj > 0) {
            val fitting = adjs.filter { adjFits(it, noun.tags) }
            val first = pick(fitting)
            if (first != null && (maxAdj == 1 || rnd.nextDouble() < 0.5 || fitting.size < 2)) {
                adjective += first
            } else if (first != null) {
                val second = pick(fitting.filter { it !== first && !(it.before && first.before) })
                if (second == null) adjective += first else adjective += listOf(first, second)
            }
        }
        var det = weightedDet(dets.filter { !(noun.uncountable && it == Det.A) })
        // "another" replaces "a other": use "the" instead.
        if (det == Det.A && adjective.any { it.en[0] == "other" }) det = Det.THE

        var np = NpNode(noun, plural, det, adjective.sortedByDescending { it.before }, rnd.nextBoolean())
        if (allowRelative && caps.relative) np = withRelative(np) ?: np
        if (modifiers && rnd.nextDouble() < 0.6) np = withModifier(np) ?: np
        return np
    }

    private fun copy(head: NpNode, modifier: ModNode? = head.modifier, relative: RelNode? = head.relative) =
        NpNode(head.noun, head.plural, head.det, head.adjs, head.yourTu, modifier, relative)

    private fun withModifier(head: NpNode): NpNode? {
        val tags = head.noun.tags
        val options = ArrayList<Pair<ModPrep, String>>()
        if ('P' in tags) options += ModPrep.WITH to "AOBV"
        if ('P' in tags || 'A' in tags) options += ModPrep.IN to "L"
        if (options.isEmpty()) return null
        val (prep, compTags) = options[rnd.nextInt(options.size)]
        val comp = nounPhrase(compTags, listOf(Det.THE, Det.A), 0, false, false) ?: return null
        return copy(head, modifier = ModNode(prep, comp))
    }

    private fun withRelative(head: NpNode): NpNode? {
        if (!head.noun.hasTag("PA")) return null
        val v = pick(verbs.filter { it.isPlain && it.subjTags.any { c -> c in head.noun.tags } }) ?: return null
        val obj = if (v.transitive) nounPhrase(v.objTags, listOf(Det.THE, Det.A), 0, false, false) ?: return null else null
        return copy(head, relative = RelNode(v, obj))
    }

    // ───────── Clauses ─────────

    private fun subject(tags: String, needsGender: Boolean): SubjNode {
        if (nouns.any { it.hasTag(tags) } && rnd.nextDouble() < 0.5) {
            val adj = if (spec.stage == Stage.SENTENCE_COMPLEX) 1 else 0
            val np = nounPhrase(tags, spec.dets, adj.coerceAtMost(spec.maxAdjectives), false, false)
            if (np != null) return SubjNp(np)
        }
        val choices = if (needsGender) Pron.entries.filter { it.known } else Pron.entries
        return SubjPron(choices[rnd.nextInt(choices.size)])
    }

    private fun chooseTense(): Tense {
        val options = buildList {
            repeat(5) { add(Tense.PRESENT) }
            if (spec.past) repeat(3) { add(Tense.PAST) }
            if (spec.future) repeat(2) { add(Tense.FUTURE) }
        }
        return options[rnd.nextInt(options.size)]
    }

    private enum class Pattern { COPULA_ADJ, COPULA_LOC, TRANSITIVE, INTRANSITIVE, MODAL, INFINITIVE }

    private fun needsGender(tense: Tense, verb: Verb?): Boolean =
        verb == null || (tense == Tense.PAST && (verb.aux == Aux.ETRE || pastNeedsGender))

    private fun clause(tense: Tense): ClauseNode? {
        val neg = spec.negation && rnd.nextDouble() < 0.25
        val patterns = ArrayList<Pattern>()
        val plain = verbs.filter { it.isPlain }
        if (adjs.isNotEmpty()) { patterns += Pattern.COPULA_ADJ; patterns += Pattern.COPULA_ADJ }
        if (nouns.any { it.hasTag("L") }) patterns += Pattern.COPULA_LOC
        if (verbs.any { it.transitive && !it.modal }) repeat(5) { patterns += Pattern.TRANSITIVE }
        if (plain.any { !it.transitive }) repeat(2) { patterns += Pattern.INTRANSITIVE }
        if (spec.modal && tense == Tense.PRESENT && verbs.any { it.modal } && plain.isNotEmpty()) repeat(2) { patterns += Pattern.MODAL }
        if (spec.infinitiveComplement && tense != Tense.FUTURE && verbs.any { it.infEn != null && caps.infinitive(it) } && plain.isNotEmpty()) {
            repeat(2) { patterns += Pattern.INFINITIVE }
        }
        if (patterns.isEmpty()) return null

        repeat(10) {
            val mark = ids.size
            val result = when (patterns[rnd.nextInt(patterns.size)]) {
                Pattern.COPULA_ADJ -> copulaAdj(tense, neg)
                Pattern.COPULA_LOC -> copulaLoc(tense, neg)
                Pattern.TRANSITIVE -> transitive(tense, neg)
                Pattern.INTRANSITIVE -> intransitive(tense, neg)
                Pattern.MODAL -> modal(neg)
                Pattern.INFINITIVE -> infinitive(tense, neg)
            }
            if (result != null) return result
            ids.subList(mark, ids.size).clear()
        }
        return null
    }

    private fun objectFor(v: Verb): NpNode? = nounPhrase(
        v.objTags, spec.dets, spec.maxAdjectives,
        spec.modifiers && rnd.nextDouble() < 0.4, spec.relative && rnd.nextDouble() < 0.3,
    )

    private fun adverbFor(tense: Tense, bare: Boolean): Adverb? {
        if (!spec.adverbs || rnd.nextDouble() > 0.4) return null
        return pick(advs.filter { it.fits(tense) && (!it.manner || bare) })
    }

    private fun place(): NpNode? = nounPhrase("L", spec.dets, 0, false, false)

    private fun transitive(tense: Tense, neg: Boolean): ClauseNode? {
        val v = pick(verbs.filter { it.transitive && !it.modal }) ?: return null
        val s = subject(v.subjTags, needsGender(tense, v))
        val obj = objectFor(v) ?: return null
        val n = neg && caps.negatedObject
        return ClauseNode(s, VerbPred(v, obj, null, adverbFor(tense, false)), tense, n)
    }

    private fun intransitive(tense: Tense, neg: Boolean): ClauseNode? {
        val v = pick(verbs.filter { it.isPlain && !it.transitive }) ?: return null
        val s = subject(v.subjTags, needsGender(tense, v))
        val loc = if (v.hasLoc && rnd.nextDouble() < 0.7) place() else null
        return ClauseNode(s, VerbPred(v, null, loc, adverbFor(tense, loc == null)), tense, neg)
    }

    private fun innerPred(inner: Verb, tense: Tense): VerbPred? {
        val obj = if (inner.transitive) objectFor(inner) ?: return null else null
        val loc = if (obj == null && inner.hasLoc && rnd.nextDouble() < 0.7) place() else null
        return VerbPred(inner, obj, loc, adverbFor(tense, obj == null && loc == null && !inner.transitive))
    }

    private fun modal(neg: Boolean): ClauseNode? {
        val modalVerb = pick(verbs.filter { it.modal }) ?: return null
        val inner = pick(verbs.filter { it.isPlain }) ?: return null
        val tags = modalVerb.subjTags.filter { it in inner.subjTags }
        if (tags.isEmpty()) return null
        val s = subject(tags, false)
        val pred = innerPred(inner, Tense.PRESENT) ?: return null
        return ClauseNode(s, ModalPred(modalVerb, pred), Tense.PRESENT, neg && (caps.negatedObject || pred.obj == null))
    }

    private fun infinitive(tense: Tense, neg: Boolean): ClauseNode? {
        val outer = pick(verbs.filter { it.infEn != null && caps.infinitive(it) }) ?: return null
        val inner = pick(verbs.filter { it.isPlain }) ?: return null
        val tags = outer.subjTags.filter { it in inner.subjTags }
        if (tags.isEmpty()) return null
        val s = subject(tags, needsGender(tense, outer))
        val pred = innerPred(inner, tense) ?: return null
        return ClauseNode(s, InfPred(outer, pred), tense, neg && (caps.negatedObject || pred.obj == null))
    }

    private fun copulaAdj(tense: Tense, neg: Boolean): ClauseNode? {
        val s = subject("PA", true)
        val adj = pick(adjs.filter { adjFits(it, "P") }) ?: return null
        return ClauseNode(s, CopAdj(adj), tense, neg)
    }

    private fun copulaLoc(tense: Tense, neg: Boolean): ClauseNode? {
        val s = subject("PAOV", pastNeedsGender && tense == Tense.PAST)
        val loc = place() ?: return null
        return ClauseNode(s, CopLoc(loc), tense, neg)
    }

    // ───────── Sentences ─────────

    fun sentence(): SentenceNode? {
        val allowed = spec.connectors.filter { it in caps.connectors }
        val wanted = if (spec.clauses > 1 && allowed.isNotEmpty()) {
            if (rnd.nextDouble() < 0.3) spec.clauses - 1 else spec.clauses
        } else 1
        val count = wanted.coerceAtLeast(1)
        // Two clauses may use any connector; three stay with "and"/"but" so the sentence stays readable.
        val joiners = when (count) {
            1 -> emptyList()
            2 -> listOf(allowed[rnd.nextInt(allowed.size)])
            else -> {
                val simple = allowed.filter { it == Conn.AND || it == Conn.BUT }.ifEmpty { listOf(Conn.AND) }
                List(count - 1) { simple[rnd.nextInt(simple.size)] }
            }
        }
        // "if" clauses stay in the present; the other clauses share one tense.
        val tense = if (joiners.firstOrNull() == Conn.IF) Tense.PRESENT else chooseTense()
        val clauses = ArrayList<ClauseNode>()
        repeat(count) { clauses += clause(tense) ?: return null }
        return SentenceNode(clauses, joiners)
    }
}
