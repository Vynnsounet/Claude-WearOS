package com.lingoquest.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val lexicon = Lexicon.load()

    private fun gen(seed: Int, level: Int, step: Int, d: Direction = Direction.FR_TO_EN) =
        Generator(Random(seed)).generate(Progression.unlocked(lexicon, level), Stage.forStep(step), Progression.cefrFor(level), step, d)

    @Test
    fun everyRuleIsExplained() {
        for (r in Rule.entries) {
            assertTrue(r.name, r.title.isNotBlank() && r.text.length > 30 && r.example.isNotBlank())
        }
    }

    @Test
    fun phrasesAndSentencesAlwaysCarryRules() {
        for (step in listOf(5, 12, 17, 25, 40, 60)) repeat(60) { seed ->
            val c = gen(seed, 125, step)
            assertTrue("${c.prompt} has no rule", c.rules.isNotEmpty())
        }
    }

    @Test
    fun rulesMatchWhatIsInTheSentence() {
        val seen = HashSet<Rule>()
        repeat(4000) { seed ->
            val step = 20 + seed % 60
            val c = gen(seed, 125, step, Direction.EN_TO_FR)
            seen += c.rules
            val en = c.prompt.lowercase()
            if (" not " in en || "cannot" in en) assertTrue(c.prompt, Rule.NEGATION in c.rules)
            if ("going to" in en) assertTrue(c.prompt, Rule.FUTURE in c.rules)
            if (c.answer.contains(" est allé") || c.answer.contains(" sont allé")) assertTrue(c.answer, Rule.PASSE_COMPOSE_ETRE in c.rules)
            if (" because " in en || " but " in en || " and " in en) assertTrue(c.prompt, Rule.CONNECTORS in c.rules || Rule.ADJ_AND in c.rules)
            if (c.answer.replace("aujourd'hui", "").contains("'")) assertTrue(c.answer, Rule.ELISION in c.rules)
        }
        val missing = Rule.entries.filter { it !in seen }
        assertTrue("rules never produced: $missing", missing.isEmpty())
    }

    @Test
    fun wordChallengesExplainTheWord() {
        val verb = Challenge(Stage.WORD, Direction.EN_TO_FR, "to eat", "manger", listOf("manger"), emptyList(), listOf("v:eat"), 0)
        assertTrue(verb.rules.isEmpty())
        val unlocked = Progression.unlocked(lexicon, 25)
        val notes = (0 until 300).map { Generator(Random(it)).generate(unlocked, Stage.WORD, Cefr.A1, 0, Direction.EN_TO_FR) }
        assertTrue(notes.any { n -> n.notes.any { it.contains("nom féminin") } })
        assertTrue(notes.any { n -> n.notes.any { it.contains("je mange, tu manges, il/elle mange, nous mangeons") } })
        assertEquals(true, notes.filter { it.notes.isNotEmpty() }.all { it.notes.size == 1 })
    }

    @Test
    fun conjugationNoteShowsAuxiliary() {
        val go = lexicon.find("v:go")!!
        val note = Notes.forEntry(go, Lang.FR)!!
        assertTrue(note, note.contains("je vais") && note.contains("allé (être)"))
        assertEquals("go : he/she goes, passé « went »", Notes.forEntry(go, Lang.EN))
    }
}
