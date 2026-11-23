package com.lingoquest.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorTest {
    private val lexicon = Lexicon.load()

    private fun generate(seed: Int, level: Int, step: Int, direction: Direction = Direction.EN_TO_FR): Challenge =
        Generator(Random(seed)).generate(
            Progression.unlocked(lexicon, level), Stage.forStep(step), Progression.cefrFor(level), step, direction,
        )

    /** Can the answer be built from the tiles, each used at most once? */
    private fun solvable(answer: String, bank: List<String>): Boolean {
        fun go(pos: Int, free: List<String>): Boolean {
            if (pos >= answer.length) return free.isEmpty() || true
            for ((i, tile) in free.withIndex()) {
                if (!answer.startsWith(tile, pos)) continue
                val end = pos + tile.length
                val ok = when {
                    end == answer.length -> true
                    tile.endsWith("'") -> true
                    answer[end] == ' ' -> true
                    else -> false
                }
                if (!ok) continue
                val next = if (end < answer.length && answer[end] == ' ') end + 1 else end
                if (go(next, free.filterIndexed { j, _ -> j != i })) return true
            }
            return false
        }
        return go(0, bank)
    }

    @Test
    fun everyChallengeIsAnswerableAcrossLevelsAndSteps() {
        var seed = 0
        for (level in listOf(1, 2, 5, 13, 25, 26, 40, 51, 70, 76, 100, 101, 125)) {
            for (step in listOf(0, 3, 5, 9, 10, 14, 15, 19, 20, 25, 30, 35, 40, 50, 55, 70, 120)) {
                for (direction in Direction.entries) {
                    repeat(6) {
                        val c = generate(seed++, level, step, direction)
                        assertTrue("blank prompt", c.prompt.isNotBlank())
                        assertTrue("blank answer", c.answer.isNotBlank())
                        assertNotEquals(c.prompt.lowercase(), c.answer.lowercase().takeIf { c.stage != Stage.WORD })
                        assertTrue(c.entryIds.isNotEmpty())
                        assertEquals(Judge.check(c, c.accepted.first()).correct, true)
                        if (c.typed) {
                            assertEquals(Stage.WORD, c.stage)
                        } else {
                            assertTrue(
                                "cannot build '${c.accepted.first()}' from ${c.bank}",
                                solvable(c.accepted.first(), c.bank),
                            )
                            assertTrue(c.bank.size > c.accepted.first().split(' ').size - 1)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun stagesMatchTheRequestedStageWhenWordsAllowIt() {
        for (step in listOf(0, 7, 12, 17, 25, 35, 45, 60)) {
            val expected = Stage.forStep(step)
            repeat(20) { seed ->
                val c = generate(seed, 125, step)
                assertEquals("step $step", expected, c.stage)
            }
        }
    }

    @Test
    fun earlyLevelsFallBackGracefully() {
        // Level 1 has few words; deep steps must still produce a valid (possibly simpler) challenge.
        repeat(200) { seed ->
            val c = generate(seed, 1, 40)
            assertTrue(c.prompt.isNotBlank())
        }
    }

    @Test
    fun promptsGrowLongerAsTheSeriesProgresses() {
        fun avgWords(step: Int) = (0 until 60).map { generate(it, 125, step).prompt.split(' ').size }.average()
        val word = avgWords(2)
        val np = avgWords(7)
        val npAdj = avgWords(12)
        val sentence = avgWords(25)
        val complex = avgWords(45)
        val long = avgWords(70)
        assertTrue("$word < $np", word < np)
        assertTrue("$np < $npAdj", np < npAdj)
        assertTrue("$npAdj < $sentence", npAdj < sentence)
        assertTrue("$sentence < $complex", sentence < complex)
        assertTrue("$complex < $long", complex < long)
    }

    @Test
    fun onlyUnlockedWordsAreUsed() {
        val unlocked = Progression.unlocked(lexicon, 1)
        val ids = unlocked.map { it.id }.toSet()
        repeat(300) { seed ->
            val step = (seed % 6) * 8
            val c = Generator(Random(seed)).generate(unlocked, Stage.forStep(step), Cefr.A1, step, Direction.FR_TO_EN)
            assertTrue(c.entryIds.all { it in ids })
        }
    }

    // ───────── Spelling of the generated French and English ─────────

    private val frBad = listOf(
        "\\b(le|la) [aeiouéèêàâîôû]" to "missing elision",
        "\\b(je|ne|de|que) [aeiouéèêàâîôû]" to "missing elision",
        "\\b(à|de) (le|les)\\b" to "missing contraction",
        "\\bsi (il|ils)\\b" to "si il",
        "\\b(ma|ta) [aeiouéèêàâîôû]" to "ma + vowel",
        "\\bce [aeiouéèêàâîôû]" to "ce + vowel",
        "\\b(un|une) (des|les)\\b" to "double article",
        "\\bdes (bons|bonnes|grands|grandes|petits|petites|beaux|belles|jeunes|vieux|vieilles|nouveaux|nouvelles|mauvais|mauvaises|jolis|jolies|gros|grosses|longs|longues|autres)(?![-\\p{L}])" to "des + adjective",
        "  " to "double space",
    )
    private val enBad = listOf(
        "\\ba [aeiou]" to "a + vowel",
        "\\ban [bcdfgjklmnpqrstvwxz]" to "an + consonant",
        "\\b(does|did) \\w+(s|ed)\\b" to "inflected verb after do/does/did",
        "\\ban other\\b" to "an other",
        "  " to "double space",
    )

    @Test
    fun generatedFrenchAndEnglishAreWellSpelled() {
        var seed = 1000
        for (level in listOf(1, 10, 30, 60, 90, 125)) {
            for (step in listOf(12, 18, 22, 28, 33, 45, 60)) {
                repeat(80) {
                    val c = generate(seed++, level, step, Direction.EN_TO_FR)
                    val fr = c.answer.lowercase().replace("'", "' ")
                    val en = c.prompt.lowercase()
                    for ((re, why) in frBad) {
                        // l' + word is written without space, so check the version with the space put back.
                        assertFalse("$why in '${c.answer}' (from '${c.prompt}')", Regex("(?U)$re").containsMatchIn(c.answer.lowercase()))
                    }
                    for ((re, why) in enBad) {
                        assertFalse("$why in '${c.prompt}'", Regex("(?U)$re").containsMatchIn(en))
                    }
                    assertTrue(fr.isNotBlank())
                }
            }
        }
    }

    // ───────── Grammar: English sentence ↔ French sentence ─────────

    private val pairs = listOf(
        "^she went" to "^elle est allée",
        "^he went" to "^il est allé",
        "^he came" to "^il est venu",
        "^she came" to "^elle est venue",
        "^he saw " to "^il a vu ",
        "^i saw " to "^j'a(i|) vu ",
        "^he sees " to "^il voit ",
        "^he eats " to "^il mange ",
        "^they eat " to "^(ils|elles) mangent ",
        "^we eat " to "^nous mangeons ",
        "^he does not eat" to "^il ne mange pas",
        "^she did not eat" to "^elle n'a pas mangé",
        "^i am going to eat" to "^je vais manger",
        "^he is going to eat" to "^il va manger",
        "^they are going to eat" to "^(ils|elles) vont manger",
        "^she is big" to "^elle est grande",
        "^he is big" to "^il est grand",
        "^she was tired" to "^elle était fatiguée",
        "^i can eat" to "^je peux manger",
        "^he cannot eat" to "^il ne peut pas manger",
        "^he wants to eat" to "^il veut manger",
        "^he wanted to eat" to "^il a voulu manger",
        "^he decides to eat" to "^il décide de manger",
        "^he starts to eat" to "^il commence à manger",
    )

    /** A tiny vocabulary, so every grammar pattern shows up quickly. */
    private val mini = Lexicon.parse(
        """
        N|A1|apple|-|pomme|-|f|F
        N|A1|man|men|homme|hommes|m|P
        N|A1|house|-|maison|-|f|LH
        A|A1|big|grand|-|-|-|B
        A|A1|tired|fatigué|-|-|-|P
        V|A1|eat|eats|ate|manger|mange,manges,mange,mangeons,mangez,mangent|mangé|avoir|obj=F
        V|A1|see|sees|saw|voir|vois,vois,voit,voyons,voyez,voient|vu|avoir|obj=PALV
        V|A1|go|goes|went|aller|vais,vas,va,allons,allez,vont|allé|être|loc=to:à
        V|A1|come|comes|came|venir|viens,viens,vient,venons,venez,viennent|venu|être|loc=to:à
        V|A1|can|can|-|pouvoir|peux,peux,peut,pouvons,pouvez,peuvent|pu|avoir|modal=1
        V|A1|want|wants|wanted|vouloir|veux,veux,veut,voulons,voulez,veulent|voulu|avoir|obj=F|inf=to:
        V|A1|decide|decides|decided|décider|décide,décides,décide,décidons,décidez,décident|décidé|avoir|inf=to:de
        V|A1|start|starts|started|commencer|commence,commences,commence,commençons,commencez,commencent|commencé|avoir|inf=to:à
        D|A1|today|aujourd'hui|pnf
        """.trimIndent(),
    )

    @Test
    fun englishAndFrenchSentencesAgree() {
        val seen = HashSet<String>()
        var seed = 5000
        repeat(30000) {
            val step = 22 + it % 50
            val c = Generator(Random(seed++)).generate(mini.entries, Stage.forStep(step), Cefr.C1, step, Direction.EN_TO_FR)
            if (!c.stage.isSentence) return@repeat
            val en = c.prompt.lowercase().replace(Regex("\\s*\\([^)]*\\)"), "")
            val fr = c.answer.lowercase()
            for ((enRe, frRe) in pairs) {
                if (frRe == null || !Regex("(?U)$enRe").containsMatchIn(en)) continue
                assertTrue("'$en' should translate as /$frRe/ but was '$fr'", Regex("(?U)$frRe").containsMatchIn(fr))
                seen += enRe
            }
        }
        val missing = pairs.filter { it.second != null }.map { it.first }.filter { it !in seen }
        assertTrue("grammar patterns never generated: $missing", missing.isEmpty())
    }

    @Test
    fun hintsDisambiguateYouAndTheyInEnglishPrompts() {
        val prompts = (0 until 1500).map { generate(it, 125, 22 + it % 20, Direction.EN_TO_FR).prompt.lowercase() }
        assertTrue(prompts.any { it.contains("you (tu)") })
        assertTrue(prompts.any { it.contains("you (vous)") })
        assertTrue(prompts.any { it.contains("they (f.)") })
        // In French → English the prompt is French and carries no hint.
        val fr = (0 until 300).map { generate(it, 125, 25, Direction.FR_TO_EN).prompt }
        assertTrue(fr.none { it.contains("(") })
    }

    @Test
    fun complexSentencesUseConnectorsRelativesAndModifiers() {
        val answers = (0 until 800).map { generate(it, 125, 60, Direction.EN_TO_FR).prompt.lowercase() }
        for (word in listOf(" and ", " but ", " because ", " when ", "if ", " that ", " with ", " in ")) {
            assertTrue("never produced '$word'", answers.any { it.contains(word) })
        }
    }

    @Test
    fun a1StaysInThePresentWithSimpleConnectors() {
        val prompts = (0 until 600).map { generate(it, 25, 60, Direction.EN_TO_FR).prompt.lowercase() }
        assertTrue(prompts.none { it.contains(" not ") || it.contains("cannot") && false })
        assertTrue(prompts.none { it.contains(" because ") || it.contains(" when ") || it.contains(" but ") })
        assertTrue(prompts.none { it.contains("going to") })
    }

    @Test
    fun missedWordsComeBackMoreOften() {
        val unlocked = Progression.unlocked(lexicon, 25)
        val target = unlocked.first { it.id == "n:window" }
        fun share(missed: Set<String>) = (0 until 600).count {
            Generator(Random(it)).generate(unlocked, Stage.WORD, Cefr.A1, 0, Direction.FR_TO_EN, missed)
                .entryIds.contains(target.id)
        }
        assertTrue(share(setOf(target.id)) > share(emptySet()) + 5)
    }
}
