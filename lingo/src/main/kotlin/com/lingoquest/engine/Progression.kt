package com.lingoquest.engine

import kotlin.math.ceil
import kotlin.math.min
import kotlinx.serialization.Serializable

/** XP, player levels, CEFR levels and which words each level unlocks. */
object Progression {
    const val LEVELS_PER_CEFR = 25
    const val MAX_LEVEL = LEVELS_PER_CEFR * 5

    /** XP needed to go from [level] to the next one. */
    fun xpToNext(level: Int): Int = 80 + 15 * (level - 1)

    /** Total XP at which [level] is reached. */
    fun xpForLevel(level: Int): Long {
        val n = (level - 1).toLong()
        return 80 * n + 15 * n * (n - 1) / 2
    }

    fun levelFor(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL && xpForLevel(level + 1) <= xp) level++
        return level
    }

    /** Progress inside the current level, 0f..1f (1f once the last level is reached). */
    fun levelProgress(xp: Long): Float {
        val level = levelFor(xp)
        if (level >= MAX_LEVEL) return 1f
        return ((xp - xpForLevel(level)).toFloat() / xpToNext(level)).coerceIn(0f, 1f)
    }

    /** Every 25 levels the language level goes up: 1-25 A1, 26-50 A2, … 101-125 C1. */
    fun cefrFor(level: Int): Cefr = Cefr.entries[min((level - 1) / LEVELS_PER_CEFR, Cefr.entries.lastIndex)]

    /** Position inside the CEFR band, 1..25. */
    fun levelInBand(level: Int): Int = (level - 1) % LEVELS_PER_CEFR + 1

    /** Words available from the very first level of a CEFR band. */
    private fun startingWords(cefr: Cefr) = if (cefr == Cefr.A1) 12 else 5

    /**
     * Words unlocked at [level]: all words of the lower CEFR levels, plus a growing share of the
     * current one (in frequency order). Each of the 25 levels of a band adds new words, and the
     * last one completes the band.
     */
    fun unlockedCount(lexicon: Lexicon, level: Int): Int {
        val cefr = cefrFor(level)
        val lower = lexicon.entries.count { it.level.ordinal < cefr.ordinal }
        val inBand = lexicon.countAt(cefr)
        val start = min(inBand, startingWords(cefr))
        val share = start + ceil((inBand - start) * (levelInBand(level) - 1) / (LEVELS_PER_CEFR - 1.0)).toInt()
        return lower + share
    }

    fun unlocked(lexicon: Lexicon, level: Int): List<Entry> =
        lexicon.entries.take(unlockedCount(lexicon, level))

    /** Words that [level] unlocks on top of the previous level. */
    fun newWordsAt(lexicon: Lexicon, level: Int): List<Entry> {
        if (level <= 1) return unlocked(lexicon, 1)
        return lexicon.entries.subList(unlockedCount(lexicon, level - 1), unlockedCount(lexicon, level))
    }

    /** XP for a correct answer: harder stages pay more, and so does going further in the run. */
    fun reward(stage: Stage, step: Int): Int =
        Math.round(stage.baseXp * (1.0 + min(step, 100) * 0.04)).toInt()

    /** Extra XP for every 10th correct answer in a row. */
    fun milestoneBonus(stepAfter: Int): Int = if (stepAfter > 0 && stepAfter % 10 == 0) stepAfter * 5 else 0
}

@Serializable
data class Profile(
    val xp: Long = 0,
    val bestStep: Int = 0,
    val runs: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val direction: Direction = Direction.FR_TO_EN,
    /** Word id → how many times it was missed recently; those words come back more often. */
    val missed: Map<String, Int> = emptyMap(),
) {
    val level: Int get() = Progression.levelFor(xp)
}
