package com.lingoquest.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class JapaneseTest {
    private val mini = miniLexicon(
        Lang.JA,
        """
        n:apple|りんご|r=ringo
        n:man|おとこのひと|r=otokonohito
        n:dog|いぬ|r=inu
        n:house|いえ|r=ie
        n:park|こうえん|r=kouen
        a:big|おおきい|r=ookii|c=i
        a:red|あかい|r=akai|c=i
        a:good|いい|r=ii|c=i|st=よ
        v:eat|たべる|r=taberu|c=1
        v:see|みる|r=miru|c=1
        v:go|いく|r=iku|c=5|loc=ni
        v:come|くる|r=kuru|c=kuru|loc=ni
        v:live|すむ|r=sumu|c=5|ti=1|loc=ni
        v:can|できる|r=dekiru|c=1
        v:must|なければならない|r=nakereba naranai|c=1
        v:want|ほしい|r=hoshii|c=si|op=ga|inf=tai
        v:decide|きめる|r=kimeru|c=1|inf=koto
        v:start|はじめる|r=hajimeru|c=1|inf=stem
        v:try|ためす|r=tamesu|c=5|inf=te
        d:today|きょう|r=kyou|t=1
        """,
    )

    @Test
    fun frenchSentencesBecomeJapanese() = checkPairs(
        mini, Lang.JA,
        listOf(
            "^il mange " to "^かれ は .*たべます(。| )",
            "^elle mange " to "^かのじょ は .*たべます(。| )",
            "^je mange " to "^わたし は .*たべます(。| )",
            "^nous mangeons " to "^わたしたち は .*たべます(。| )",
            "^ils mangent " to "^かれら は .*たべます(。| )",
            "^elles mangent " to "^かのじょたち は .*たべます(。| )",
            "^il a mangé " to "^かれ は .*たべました(。| )",
            "^il ne mange pas " to "^かれ は .*たべません(。| )",
            "^il n'a pas mangé " to "^かれ は .*たべませんでした(。| )",
            "^il va manger " to "^かれ は .*たべる つもり です(。| )",
            "^il ne va pas manger " to "^かれ は .*たべない つもり です(。| )",
            "^il est grand" to "^かれ は おおきい です(。| )",
            "^il n'est pas grand" to "^かれ は おおきくない です(。| )",
            "^il était grand" to "^かれ は おおきかった です(。| )",
            "^il n'était pas grand" to "^かれ は おおきくなかった です(。| )",
            "^il va être grand" to "^かれ は おおきい でしょう(。| )",
            "^il veut manger " to "^かれ は .*たべたい です(。| )",
            "^il ne veut pas manger " to "^かれ は .*たべたくない です(。| )",
            "^il a voulu manger " to "^かれ は .*たべたかった です(。| )",
            "^il veut \\w+ pomme" to "^かれ は .*りんご が ほしい です(。| )",
            "^il peut manger " to "^かれ は .*たべること が できます(。| )",
            "^il ne peut pas manger " to "^かれ は .*たべること が できません(。| )",
            "^il doit manger " to "^かれ は .*たべなければ なりません(。| )",
            "^il commence à manger " to "^かれ は .*たべはじめます(。| )",
            "^il décide de manger " to "^かれ は .*たべることにします(。| )",
            "^il essaie de manger " to "^かれ は .*たべてみます(。| )",
            "^il va au parc" to "^かれ は .*こうえん に いきます(。| )",
            "^il est allé " to "^かれ は .*いきました(。| )",
            "^il est venu" to "^かれ は .*きました(。| )",
            "^il habite " to "^かれ は .*すんでいます(。| )",
            "^il n'a pas habité " to "^かれ は .*すんでいませんでした(。| )",
            "^il est dans " to "^かれ は .*に います(。| )",
        ),
        skipIf = Regex("parce qu"),
    )

    @Test
    fun conjugation() {
        fun fx(text: String, c: String) = Fx(listOf(text), mapOf("c" to c))
        assertEquals("たべ", JaMorph.masuStem(fx("たべる", "1")))
        assertEquals("のみ", JaMorph.masuStem(fx("のむ", "5")))
        assertEquals("かき", JaMorph.masuStem(fx("かく", "5")))
        assertEquals("いき", JaMorph.masuStem(fx("いく", "5")))
        assertEquals("かい", JaMorph.masuStem(fx("かう", "5")))
        assertEquals("りょうりし", JaMorph.masuStem(fx("りょうりする", "suru")))
        assertEquals("き", JaMorph.masuStem(fx("くる", "kuru")))
        assertEquals("もってき", JaMorph.masuStem(fx("もってくる", "kuru")))
        assertEquals("のま", JaMorph.naiStem(fx("のむ", "5")))
        assertEquals("かわ", JaMorph.naiStem(fx("かう", "5")))
        assertEquals("こ", JaMorph.naiStem(fx("くる", "kuru")))
        assertEquals("のんで", JaMorph.teForm(fx("のむ", "5")))
        assertEquals("かいて", JaMorph.teForm(fx("かく", "5")))
        assertEquals("いって", JaMorph.teForm(fx("いく", "5")))
        assertEquals("およいで", JaMorph.teForm(fx("およぐ", "5")))
        assertEquals("はなして", JaMorph.teForm(fx("はなす", "5")))
        assertEquals("かって", JaMorph.teForm(fx("かう", "5")))
        assertEquals("たべて", JaMorph.teForm(fx("たべる", "1")))
        assertEquals("きて", JaMorph.teForm(fx("くる", "kuru")))
        assertEquals("たべませんでした", JaMorph.polite("たべ", true, true))
        assertEquals(listOf("よくない", "です"), JaMorph.predicate("いい", "i", "よ", Tense.PRESENT, true))
        assertEquals(listOf("しずか", "では", "ありません", "でした"), JaMorph.predicate("しずか", "na", "", Tense.PAST, true))
    }

    @Test
    fun romajiIsAcceptedWhenTyping() {
        val lex = Lexicon.load()
        val dog = lex.find("n:dog")!!
        assertEquals(listOf("いぬ", "inu"), lex.accepted(dog, Lang.JA))
        val c = Challenge(Stage.WORD, Direction.FR_TO_JA, "chien", "いぬ (inu)", lex.accepted(dog, Lang.JA), emptyList(), listOf("n:dog"), 0)
        assertEquals(true, Judge.check(c, "inu").correct)
        assertEquals(true, Judge.check(c, "いぬ").correct)
        assertEquals(false, Judge.check(c, "ine").correct)
        // The dakuten is not an accent: が is not か.
        val ga = Challenge(Stage.WORD, Direction.FR_TO_JA, "x", "か", listOf("か"), emptyList(), listOf("n:dog"), 0)
        assertEquals(false, Judge.check(ga, "が").correct)
    }
}
