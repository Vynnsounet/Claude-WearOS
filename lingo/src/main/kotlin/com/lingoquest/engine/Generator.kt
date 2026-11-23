package com.lingoquest.engine

import kotlin.random.Random

/**
 * Builds translation challenges of growing length from the words the player has unlocked.
 * A [Planner] decides what to say as a language-neutral tree; a [Realizer] per language
 * writes it in French and in the learned language, so both sides always say the same thing
 * (articles, genders, cases, conjugations, word order…).
 */
class Generator(
    private val rnd: Random = Random.Default,
    private val lexicon: Lexicon = Lexicon(emptyList()),
) {

    fun generate(
        unlocked: List<Entry>,
        stage: Stage,
        cefr: Cefr,
        step: Int,
        direction: Direction,
        missed: Set<String> = emptySet(),
    ): Challenge {
        require(unlocked.isNotEmpty()) { "No unlocked words" }
        val course = Realizers.of(direction.course, lexicon)
        var current = stage
        while (current != Stage.WORD) {
            repeat(12) {
                val spec = Spec.of(current, cefr, step)
                val planner = Planner(rnd, unlocked, missed, spec, course.caps, course.caps.pastNeedsGender)
                val node: Node? = when (current) {
                    Stage.NOUN_PHRASE, Stage.NOUN_PHRASE_ADJ, Stage.NOUN_PHRASE_COMPLEX ->
                        planner.nounPhrase(null, spec.dets, spec.maxAdjectives, spec.modifiers, false)
                    else -> planner.sentence()
                }
                if (node != null) {
                    build(node, planner.ids, unlocked, spec, step, direction, course)?.let { return it }
                }
            }
            // Not enough words unlocked for this stage yet: fall back to a simpler one.
            current = Stage.entries[current.ordinal - 1]
        }
        return wordChallenge(unlocked, missed, step, direction)
    }

    // ───────── Words ─────────

    private fun wordChallenge(unlocked: List<Entry>, missed: Set<String>, step: Int, direction: Direction): Challenge {
        val recent = unlocked.takeLast(12).map { it.id }.toSet()
        val weights = unlocked.map { 1.0 + (if (it.id in recent) 2.0 else 0.0) + (if (it.id in missed) 2.0 else 0.0) }
        var r = rnd.nextDouble() * weights.sum()
        var entry = unlocked.last()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0) { entry = unlocked[i]; break }
        }
        return Challenge(
            stage = Stage.WORD, direction = direction,
            prompt = lexicon.display(entry, direction.from),
            answer = lexicon.display(entry, direction.to),
            accepted = lexicon.accepted(entry, direction.to),
            bank = emptyList(), entryIds = listOf(entry.id), step = step,
            notes = listOfNotNull(Notes.forEntry(entry, direction.to, lexicon)),
        )
    }

    // ───────── Phrases and sentences ─────────

    private fun build(
        node: Node, ids: List<String>, unlocked: List<Entry>, spec: Spec, step: Int,
        direction: Direction, foreign: Realizer,
    ): Challenge? {
        val frSink = Sink(direction.course)
        val foSink = Sink(direction.course)
        val frTokens = FrenchRealizer.realize(node, frSink)
        val foTokens = foreign.realize(node, foSink)
        if (frTokens.size > 26 || foTokens.size > 26) return null

        val promptRealizer = if (direction.from == Lang.FR) FrenchRealizer else foreign
        val answerRealizer = if (direction.to == Lang.FR) FrenchRealizer else foreign
        val promptTokens = if (direction.from == Lang.FR) frTokens else foTokens
        val answerTokens = if (direction.to == Lang.FR) frTokens else foTokens
        val answerSink = if (direction.to == Lang.FR) frSink else foSink

        val sentence = spec.stage.isSentence
        val prompt = promptRealizer.sentence(promptTokens.map { if (it.hint != null) "${it.s} (${it.hint})" else it.s }, sentence)
        val answerWords = answerTokens.map { it.s }
        val rules = if (direction.course == Lang.EN) (frSink.rules + foSink.rules).distinctBy { it.key } else answerSink.rules
        val bank = tiles(answerWords, answerSink.alts, unlocked, direction.to, distractorCount(spec))
        return Challenge(
            stage = spec.stage, direction = direction,
            prompt = prompt, answer = answerRealizer.sentence(answerWords, sentence),
            accepted = listOf(Spelling.join(answerWords)),
            bank = bank,
            entryIds = ids.distinct(), step = step,
            rules = rules.toList(),
            notes = ids.distinct().mapNotNull { id -> unlocked.firstOrNull { it.id == id } }
                .sortedBy { it.pos.ordinal }.mapNotNull { Notes.forEntry(it, direction.to, lexicon) }.take(6),
        )
    }

    private fun distractorCount(spec: Spec) = when {
        spec.stage == Stage.SENTENCE_COMPLEX && spec.clauses > 1 -> 4
        spec.stage.isSentence -> 3
        else -> 2
    }

    private fun tiles(answer: List<String>, confusers: List<String>, unlocked: List<Entry>, lang: Lang, count: Int): List<String> {
        val used = answer.map { it.lowercase() }.toHashSet()
        val extra = ArrayList<String>()
        val wantConfusers = (count + 1) / 2
        for (c in confusers.shuffled(rnd)) {
            val low = c.lowercase()
            if (extra.size >= wantConfusers) break
            if (low !in used && extra.none { it.lowercase() == low } && ' ' !in c) extra += c
        }
        val fillers = unlocked.flatMap { lexicon.forms(it, lang) }.filter { ' ' !in it }.shuffled(rnd)
        for (f in fillers) {
            if (extra.size >= count) break
            val low = f.lowercase()
            if (low !in used && extra.none { it.lowercase() == low }) extra += f
        }
        return (answer + extra).shuffled(rnd)
    }
}
