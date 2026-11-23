package com.lingoquest.engine

import kotlin.random.Random

data class LevelUp(
    val level: Int,
    val cefr: Cefr,
    val cefrChanged: Boolean,
    val newWords: List<Entry>,
)

data class Outcome(
    val correct: Boolean,
    val accentWarning: Boolean,
    /** The right answer, ready to display. */
    val expected: String,
    val xpGained: Int,
    val milestoneBonus: Int,
    val levelUp: LevelUp?,
    val lives: Int,
    /** Three mistakes: the series is over and the next one starts from scratch. */
    val runOver: Boolean,
    /** Correct answers in a row after this answer. */
    val step: Int,
)

/**
 * One series of translations. Answer correctly to climb from single words to long sentences;
 * three mistakes end the series and the next one starts again from the first stage. XP and
 * levels are kept between series.
 */
class GameSession(
    private val fullLexicon: Lexicon,
    profile: Profile = Profile(),
    random: Random = Random.Default,
) {
    private val generator = Generator(random, fullLexicon)

    /** The words of the selected course (those that have a translation). */
    val lexicon: Lexicon get() = fullLexicon.forCourse(profile.course)

    var profile: Profile = profile
        private set
    var step = 0
        private set
    var lives = MAX_LIVES
        private set
    var runXp = 0
        private set
    var challenge: Challenge? = null
        private set

    val level get() = profile.level
    val cefr get() = Progression.cefrFor(level)
    val stage get() = Stage.forStep(step)

    /** Changes the course or the direction; a different course ends the series in progress. */
    fun setDirection(direction: Direction) {
        val courseChanged = direction.course != profile.course
        profile = profile.with(direction)
        if (courseChanged) {
            step = 0
            lives = MAX_LIVES
            runXp = 0
            challenge = null
        } else if (challenge != null) {
            challenge = nextChallenge()
        }
    }

    /** Starts a new series from zero. */
    fun startRun(): Challenge {
        step = 0
        lives = MAX_LIVES
        runXp = 0
        return nextChallenge().also { challenge = it }
    }

    /** [answer] is the typed text, or the tapped tiles joined with [Judge.fromTiles]. */
    fun submit(answer: String): Outcome {
        val current = checkNotNull(challenge) { "No challenge in progress" }
        val verdict = Judge.check(current, answer)
        val levelBefore = profile.level
        val missed = profile.missed.toMutableMap()
        val vocabulary = lexicon

        if (verdict.correct) {
            val xp = Progression.reward(current.stage, step)
            step++
            val bonus = Progression.milestoneBonus(step)
            runXp += xp + bonus
            current.entryIds.forEach { id ->
                val n = (missed[id] ?: 0) - 1
                if (n > 0) missed[id] = n else missed.remove(id)
            }
            profile = profile.update {
                it.copy(
                    xp = it.xp + xp + bonus,
                    bestStep = maxOf(it.bestStep, step),
                    correct = it.correct + 1,
                    missed = missed,
                )
            }
            val levelAfter = profile.level
            val up = if (levelAfter > levelBefore) levelUp(vocabulary, levelBefore, levelAfter) else null
            challenge = nextChallenge()
            return Outcome(true, verdict.accentWarning, current.answer, xp, bonus, up, lives, false, step)
        }

        lives--
        current.entryIds.forEach { id -> missed[id] = minOf((missed[id] ?: 0) + 1, 5) }
        val over = lives <= 0
        profile = profile.update { it.copy(wrong = it.wrong + 1, missed = missed, runs = it.runs + if (over) 1 else 0) }
        val reached = step
        if (over) {
            challenge = null
            step = 0
        } else {
            challenge = nextChallenge()
        }
        return Outcome(false, false, current.answer, 0, 0, null, lives.coerceAtLeast(0), over, reached)
    }

    private fun levelUp(vocabulary: Lexicon, from: Int, to: Int): LevelUp {
        val words = (from + 1..to).flatMap { Progression.newWordsAt(vocabulary, it) }
        return LevelUp(to, Progression.cefrFor(to), Progression.cefrFor(to) != Progression.cefrFor(from), words)
    }

    private fun nextChallenge(): Challenge = generator.generate(
        unlocked = Progression.unlocked(lexicon, level),
        stage = stage,
        cefr = cefr,
        step = step,
        direction = profile.direction,
        missed = profile.missed.keys,
    )

    companion object {
        const val MAX_LIVES = 3
    }
}
