package com.lingoquest.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val lexicon = Lexicon.load()

    private fun withXp(xp: Long) = Profile().update { it.copy(xp = xp) }

    private fun session(profile: Profile = Profile(), seed: Int = 1) = GameSession(lexicon, profile, Random(seed))

    private fun GameSession.answerRight() = submit(challenge!!.accepted.first())
    private fun GameSession.answerWrong() = submit("zzz")

    @Test
    fun aSeriesClimbsThroughEveryStage() {
        val s = session()
        s.startRun()
        val stages = ArrayList<Stage>()
        repeat(32) {
            stages += s.challenge!!.stage
            assertTrue(s.answerRight().correct)
        }
        assertEquals(Stage.WORD, stages[0])
        assertEquals(Stage.WORD, stages[4])
        assertEquals(Stage.NOUN_PHRASE, stages[5])
        assertEquals(Stage.NOUN_PHRASE_ADJ, stages[10])
        assertEquals(Stage.NOUN_PHRASE_COMPLEX, stages[15])
        assertEquals(Stage.SENTENCE_SIMPLE, stages[20])
        assertEquals(Stage.SENTENCE_COMPLEX, stages[30])
        assertEquals(32, s.step)
    }

    @Test
    fun correctAnswersEarnMoreAndMoreXp() {
        val s = session()
        s.startRun()
        val first = s.answerRight().xpGained
        repeat(3) { s.answerRight() }
        val fifth = s.answerRight().xpGained
        assertEquals(10, first)
        assertTrue(fifth > first)
        assertEquals(s.profile.xp, s.runXp.toLong())
    }

    @Test
    fun tenthCorrectAnswerGivesABonus() {
        val s = session()
        s.startRun()
        repeat(9) { assertEquals(0, s.answerRight().milestoneBonus) }
        assertEquals(50, s.answerRight().milestoneBonus)
    }

    @Test
    fun threeMistakesEndTheSeriesAndRestartFromZero() {
        val s = session()
        s.startRun()
        repeat(12) { s.answerRight() }
        val xp = s.profile.xp
        val first = s.answerWrong()
        assertFalse(first.correct)
        assertEquals(2, first.lives)
        assertFalse(first.runOver)
        assertEquals(12, s.step)            // a mistake does not push the series back…
        assertNotNull(s.challenge)
        s.answerWrong()
        val last = s.answerWrong()
        assertTrue(last.runOver)
        assertEquals(0, last.lives)
        assertEquals(12, last.step)         // …the result reports how far it went
        assertNull(s.challenge)
        assertEquals(0, s.step)
        assertEquals(xp, s.profile.xp)      // XP and level are kept
        assertEquals(12, s.profile.bestStep)
        assertEquals(1, s.profile.runs)

        val restart = s.startRun()
        assertEquals(Stage.WORD, restart.stage)
        assertEquals(3, s.lives)
        assertEquals(0, s.runXp)
    }

    @Test
    fun mistakeKeepsTheSameStageButGivesTheRightAnswer() {
        val s = session()
        s.startRun()
        repeat(7) { s.answerRight() }
        val expected = s.challenge!!.answer
        val out = s.answerWrong()
        assertEquals(expected, out.expected)
        assertEquals(Stage.NOUN_PHRASE, s.challenge!!.stage)
        assertEquals(0, out.xpGained)
    }

    @Test
    fun levelingUpUnlocksWordsAndReportsThem() {
        val s = session(withXp(70))
        s.startRun()
        var up: LevelUp? = null
        while (up == null) up = s.answerRight().levelUp
        assertEquals(2, up.level)
        assertEquals(Cefr.A1, up.cefr)
        assertFalse(up.cefrChanged)
        assertTrue(up.newWords.isNotEmpty())
        assertEquals(Progression.newWordsAt(lexicon, 2), up.newWords)
    }

    @Test
    fun reachingLevel26MovesToA2() {
        val s = session(withXp(Progression.xpForLevel(26) - 5))
        assertEquals(Cefr.A1, s.cefr)
        s.startRun()
        var up: LevelUp? = null
        while (up == null) up = s.answerRight().levelUp
        assertEquals(26, up.level)
        assertEquals(Cefr.A2, up.cefr)
        assertTrue(up.cefrChanged)
        assertTrue(up.newWords.all { it.level == Cefr.A2 })
    }

    @Test
    fun missedWordsAreTrackedAndForgivenWhenLearned() {
        val s = session()
        s.startRun()
        val ids = s.challenge!!.entryIds
        s.answerWrong()
        assertTrue(ids.all { (s.profile.missed[it] ?: 0) >= 1 })
    }

    @Test
    fun singleWordsAcceptArticlesAndMissingAccents() {
        val c = Challenge(
            Stage.WORD, Direction.EN_TO_FR, "school", "école", listOf("école"), emptyList(), listOf("n:school"), 0,
        )
        assertTrue(Judge.check(c, "école").correct)
        assertTrue(Judge.check(c, "  École ").correct)
        assertTrue(Judge.check(c, "l'école").correct)
        assertTrue(Judge.check(c, "une école").correct)
        val noAccent = Judge.check(c, "ecole")
        assertTrue(noAccent.correct && noAccent.accentWarning)
        assertFalse(Judge.check(c, "maison").correct)
        assertFalse(Judge.check(c, "").correct)
        val verb = Challenge(Stage.WORD, Direction.FR_TO_EN, "manger", "to eat", listOf("eat"), emptyList(), listOf("v:eat"), 0)
        assertTrue(Judge.check(verb, "to eat").correct)
        assertTrue(Judge.check(verb, "Eat").correct)
        assertFalse(Judge.check(verb, "drink").correct)
    }

    @Test
    fun tilesMustFormTheExactSentence() {
        val c = Challenge(
            Stage.NOUN_PHRASE, Direction.EN_TO_FR, "The friend", "L'ami", listOf("l'ami"),
            listOf("ami", "le", "l'"), listOf("n:friend"), 0,
        )
        assertTrue(Judge.check(c, Judge.fromTiles(listOf("l'", "ami"))).correct)
        assertFalse(Judge.check(c, Judge.fromTiles(listOf("le", "ami"))).correct)
        assertFalse(Judge.check(c, Judge.fromTiles(listOf("ami", "l'"))).correct)
    }

    @Test
    fun changingDirectionSwitchesTheLanguages() {
        val s = session()
        s.startRun()
        s.setDirection(Direction.EN_TO_FR)
        assertEquals(Direction.EN_TO_FR, s.challenge!!.direction)
        assertEquals(Direction.EN_TO_FR, s.profile.direction)
    }

    @Test
    fun aLongSeriesNeverBreaks() {
        val s = session(withXp(Progression.xpForLevel(100)), seed = 9)
        s.startRun()
        repeat(150) { assertTrue(s.answerRight().correct) }
        assertEquals(150, s.step)
        assertTrue(s.profile.xp > Progression.xpForLevel(100))
    }
}
