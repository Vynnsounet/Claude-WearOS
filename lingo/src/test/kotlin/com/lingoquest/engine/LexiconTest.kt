package com.lingoquest.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LexiconTest {
    private val lexicon = Lexicon.load()

    @Test
    fun loadsAllFiveLevels() {
        for (cefr in Cefr.entries) assertTrue("no $cefr words", lexicon.countAt(cefr) >= 25)
        assertTrue(lexicon.entries.size > 400)
    }

    @Test
    fun entriesAreSortedByLevelThenRank() {
        val keys = lexicon.entries.map { it.level.ordinal to it.rank }
        assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second })), keys)
    }

    @Test
    fun noFrenchOrEnglishWordIsTranslatedTwice() {
        for (lang in Lang.entries) {
            val seen = HashMap<String, String>()
            for (e in lexicon.entries) {
                val form = e.lemma(lang).lowercase()
                val other = seen.put(form, e.id)
                assertTrue("'$form' is the ${lang} word of both $other and ${e.id}", other == null)
            }
        }
    }

    @Test
    fun adjectivesAgree() {
        val big = lexicon.find("a:big") as Adj
        assertEquals("grand", big.fr(Gender.M, false))
        assertEquals("grande", big.fr(Gender.F, false))
        assertEquals("grands", big.fr(Gender.M, true))
        assertEquals("grandes", big.fr(Gender.F, true))
        val beautiful = lexicon.find("a:beautiful") as Adj
        assertEquals("beau", beautiful.fr(Gender.M, false, nextStartsWithVowel = false))
        assertEquals("bel", beautiful.fr(Gender.M, false, nextStartsWithVowel = true))
        assertEquals("belle", beautiful.fr(Gender.F, false, nextStartsWithVowel = true))
        assertEquals("beaux", beautiful.fr(Gender.M, true))
    }

    @Test
    fun verbsAreComplete() {
        val verbs = lexicon.entries.filterIsInstance<Verb>()
        assertTrue(verbs.size > 60)
        for (v in verbs) {
            assertEquals(v.id, 6, v.present.size)
            assertTrue(v.id, v.participle.isNotBlank())
        }
        val go = lexicon.find("v:go") as Verb
        assertEquals(Aux.ETRE, go.aux)
        assertEquals("à", go.locFr)
        val want = lexicon.find("v:want") as Verb
        assertTrue(want.takesInfinitive && want.transitive)
        assertTrue((lexicon.find("v:can") as Verb).modal)
    }

    @Test
    fun synonymsAreAccepted() {
        val big = lexicon.find("a:big")!!
        assertEquals(listOf("big", "large", "tall"), big.forms(Lang.EN))
        assertEquals("to eat", lexicon.find("v:eat")!!.display(Lang.EN))
    }
}
