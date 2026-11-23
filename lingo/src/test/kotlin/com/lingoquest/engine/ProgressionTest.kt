package com.lingoquest.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private val lexicon = Lexicon.load()

    @Test
    fun cefrLevelRisesEvery25Levels() {
        assertEquals(Cefr.A1, Progression.cefrFor(1))
        assertEquals(Cefr.A1, Progression.cefrFor(25))
        assertEquals(Cefr.A2, Progression.cefrFor(26))
        assertEquals(Cefr.A2, Progression.cefrFor(50))
        assertEquals(Cefr.B1, Progression.cefrFor(51))
        assertEquals(Cefr.B2, Progression.cefrFor(76))
        assertEquals(Cefr.C1, Progression.cefrFor(101))
        assertEquals(Cefr.C1, Progression.cefrFor(125))
        assertEquals(1, Progression.levelInBand(26))
        assertEquals(25, Progression.levelInBand(125))
    }

    @Test
    fun xpAndLevelsAreConsistent() {
        assertEquals(1, Progression.levelFor(0))
        assertEquals(0L, Progression.xpForLevel(1))
        assertEquals(80L, Progression.xpForLevel(2))
        assertEquals(1, Progression.levelFor(79))
        assertEquals(2, Progression.levelFor(80))
        for (level in 1 until Progression.MAX_LEVEL) {
            val need = Progression.xpForLevel(level + 1) - Progression.xpForLevel(level)
            assertEquals(Progression.xpToNext(level).toLong(), need)
            assertEquals(level + 1, Progression.levelFor(Progression.xpForLevel(level + 1)))
            assertEquals(level, Progression.levelFor(Progression.xpForLevel(level + 1) - 1))
        }
        assertEquals(Progression.MAX_LEVEL, Progression.levelFor(Long.MAX_VALUE / 4))
        assertEquals(0.5f, Progression.levelProgress(40), 0.001f)
    }

    @Test
    fun everyLevelUnlocksNewWords() {
        var previous = 0
        for (level in 1..Progression.MAX_LEVEL) {
            val count = Progression.unlockedCount(lexicon, level)
            if (level == 1) assertTrue(count >= 12) else assertTrue("level $level adds no word", count > previous)
            previous = count
            assertTrue(Progression.newWordsAt(lexicon, level).isNotEmpty())
        }
        assertEquals(lexicon.entries.size, previous)
    }

    @Test
    fun theLastLevelOfABandCompletesIt() {
        for (cefr in Cefr.entries) {
            val level = (cefr.ordinal + 1) * Progression.LEVELS_PER_CEFR
            val unlocked = Progression.unlocked(lexicon, level)
            assertEquals(lexicon.entries.count { it.level <= cefr }, unlocked.size)
        }
    }

    @Test
    fun wordsUnlockInFrequencyOrderAndLevelByLevel() {
        val a1 = Progression.unlocked(lexicon, 1)
        assertTrue(a1.all { it.level == Cefr.A1 })
        // Level 26 keeps every A1 word and starts on A2 words.
        val l26 = Progression.unlocked(lexicon, 26)
        assertTrue(l26.containsAll(Progression.unlocked(lexicon, 25)))
        assertTrue(l26.any { it.level == Cefr.A2 })
        assertTrue(l26.none { it.level > Cefr.A2 })
    }

    @Test
    fun firstLevelHasEverythingNeededForSentences() {
        val a1 = Progression.unlocked(lexicon, 1)
        assertTrue(a1.any { it is Noun })
        assertTrue(a1.any { it is Verb && it.transitive })
        assertTrue(a1.any { it is Adj })
    }

    @Test
    fun rewardsGrowWithStageAndDepth() {
        assertEquals(10, Progression.reward(Stage.WORD, 0))
        assertTrue(Progression.reward(Stage.WORD, 4) > Progression.reward(Stage.WORD, 0))
        assertTrue(Progression.reward(Stage.SENTENCE_COMPLEX, 30) > Progression.reward(Stage.SENTENCE_SIMPLE, 30))
        assertEquals(0, Progression.milestoneBonus(9))
        assertEquals(50, Progression.milestoneBonus(10))
        assertEquals(100, Progression.milestoneBonus(20))
    }

    @Test
    fun stagesFollowTheSeries() {
        assertEquals(Stage.WORD, Stage.forStep(0))
        assertEquals(Stage.WORD, Stage.forStep(4))
        assertEquals(Stage.NOUN_PHRASE, Stage.forStep(5))
        assertEquals(Stage.NOUN_PHRASE_ADJ, Stage.forStep(10))
        assertEquals(Stage.NOUN_PHRASE_COMPLEX, Stage.forStep(15))
        assertEquals(Stage.SENTENCE_SIMPLE, Stage.forStep(20))
        assertEquals(Stage.SENTENCE_COMPLEX, Stage.forStep(30))
        assertEquals(Stage.SENTENCE_COMPLEX, Stage.forStep(200))
    }
}
