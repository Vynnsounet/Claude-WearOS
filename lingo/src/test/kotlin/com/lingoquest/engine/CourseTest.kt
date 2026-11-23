package com.lingoquest.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that apply to every foreign course. */
class CourseTest {
    private val lexicon = Lexicon.load()
    private val courses = listOf(Lang.ES)

    @Test
    fun translationsLoadAndAreUnique() {
        for (lang in courses) {
            val course = lexicon.forCourse(lang)
            assertTrue("$lang has only ${course.entries.size} words", course.entries.size > 400)
            val seen = HashMap<String, String>()
            for (e in course.entries) {
                val lemma = lexicon.forms(e, lang)[0].lowercase()
                val other = seen.put(lemma, e.id)
                assertTrue("$lang: '$lemma' is the translation of both $other and ${e.id}", other == null)
            }
        }
    }

    @Test
    fun everyCefrBandHasWordsInEveryCourse() {
        for (lang in courses) for (cefr in Cefr.entries) {
            assertTrue("$lang $cefr", lexicon.forCourse(lang).countAt(cefr) >= 20)
        }
    }

    @Test
    fun everyLevelUnlocksWordsInEveryCourse() {
        for (lang in courses) {
            val course = lexicon.forCourse(lang)
            var previous = 0
            for (level in 1..Progression.MAX_LEVEL) {
                val count = Progression.unlockedCount(course, level)
                if (level > 1) assertTrue("$lang level $level adds no word", count > previous)
                previous = count
            }
            assertEquals(course.entries.size, previous)
        }
    }

    @Test
    fun challengesAreAnswerableInBothDirections() {
        var seed = 0
        for (lang in courses) {
            val course = lexicon.forCourse(lang)
            for (level in listOf(1, 10, 25, 26, 50, 51, 76, 101, 125)) {
                val unlocked = Progression.unlocked(course, level)
                for (step in listOf(0, 4, 5, 10, 15, 20, 25, 30, 40, 55, 80)) {
                    for (toFrench in listOf(false, true)) {
                        repeat(5) {
                            val direction = Direction.of(lang, toFrench)
                            val c = Generator(Random(seed++), lexicon)
                                .generate(unlocked, Stage.forStep(step), Progression.cefrFor(level), step, direction)
                            val ctx = "$lang level=$level step=$step toFr=$toFrench"
                            assertTrue("$ctx blank prompt", c.prompt.isNotBlank())
                            assertTrue("$ctx blank answer", c.answer.isNotBlank())
                            assertTrue("$ctx no entries", c.entryIds.isNotEmpty())
                            assertTrue("$ctx canonical answer rejected: ${c.accepted}", Judge.check(c, c.accepted.first()).correct)
                            if (c.typed) {
                                assertEquals(Stage.WORD, c.stage)
                            } else {
                                assertTrue("$ctx cannot build '${c.accepted.first()}' from ${c.bank}", solvable(c.accepted.first(), c.bank))
                                assertNotEquals(c.prompt.lowercase(), c.answer.lowercase())
                                assertTrue("$ctx no rule for '${c.prompt}'", c.rules.isNotEmpty())
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun onlyUnlockedWordsAreUsed() {
        for (lang in courses) {
            val course = lexicon.forCourse(lang)
            val unlocked = Progression.unlocked(course, 3)
            val ids = unlocked.map { it.id }.toSet()
            repeat(300) { seed ->
                val step = (seed % 8) * 8
                val c = Generator(Random(seed), lexicon)
                    .generate(unlocked, Stage.forStep(step), Cefr.A1, step, Direction.of(lang, false))
                assertTrue(c.entryIds.all { it in ids })
            }
        }
    }

    @Test
    fun promptsGrowLongerAsTheSeriesProgresses() {
        for (lang in courses) {
            val unlocked = lexicon.forCourse(lang).entries
            fun avg(step: Int) = (0 until 60).map {
                Generator(Random(it), lexicon).generate(unlocked, Stage.forStep(step), Cefr.C1, step, Direction.of(lang, true))
                    .prompt.split(' ').size
            }.average()
            val sizes = listOf(2, 7, 12, 25, 45, 70).map { avg(it) }
            for (i in 1 until sizes.size) assertTrue("$lang $sizes", sizes[i - 1] < sizes[i])
        }
    }

    @Test
    fun typedWordsAreCheckedLeniently() {
        for (lang in courses) {
            val unlocked = lexicon.forCourse(lang).entries
            repeat(200) { seed ->
                val c = Generator(Random(seed), lexicon).generate(unlocked, Stage.WORD, Cefr.A1, 0, Direction.of(lang, false))
                for (form in c.accepted) assertTrue("$lang $form", Judge.check(c, form).correct)
                assertTrue(Judge.check(c, " " + c.accepted[0].uppercase() + " ").correct)
                assertFalse(Judge.check(c, "zzzz").correct)
            }
        }
    }

    @Test
    fun wordNotesDescribeTheWords() {
        for (lang in courses) {
            val unlocked = lexicon.forCourse(lang).entries
            val notes = (0 until 400).flatMap {
                Generator(Random(it), lexicon).generate(unlocked, Stage.WORD, Cefr.A1, 0, Direction.of(lang, false)).notes
            }
            assertTrue("$lang has no notes", notes.size > 100)
            assertTrue(notes.all { it.contains(":") })
        }
    }
}
