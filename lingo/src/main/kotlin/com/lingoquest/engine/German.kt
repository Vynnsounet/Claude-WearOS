package com.lingoquest.engine

internal enum class DeCase { NOM, ACC, DAT }

/** German morphology shared by the realizer and the word notes. */
internal object DeMorph {
    private val definite = mapOf(
        'm' to listOf("der", "den", "dem"), 'f' to listOf("die", "die", "der"),
        'n' to listOf("das", "das", "dem"), 'p' to listOf("die", "die", "den"),
    )
    private val dieser = mapOf(
        'm' to listOf("dieser", "diesen", "diesem"), 'f' to listOf("diese", "diese", "dieser"),
        'n' to listOf("dieses", "dieses", "diesem"), 'p' to listOf("diese", "diese", "diesen"),
    )
    /** Endings added to ein / kein / mein… */
    private val einEnd = mapOf(
        'm' to listOf("", "en", "em"), 'f' to listOf("e", "e", "er"),
        'n' to listOf("", "", "em"), 'p' to listOf("e", "e", "en"),
    )
    private val weak = mapOf(
        'm' to listOf("e", "en", "en"), 'f' to listOf("e", "e", "en"),
        'n' to listOf("e", "e", "en"), 'p' to listOf("en", "en", "en"),
    )
    private val mixed = mapOf(
        'm' to listOf("er", "en", "en"), 'f' to listOf("e", "e", "en"),
        'n' to listOf("es", "es", "en"), 'p' to listOf("en", "en", "en"),
    )
    private val strongPlural = listOf("e", "e", "en")

    enum class Declension { WEAK, MIXED, STRONG }

    private fun key(g: Char, plural: Boolean) = if (plural) 'p' else g

    fun gender(fx: Fx): Char = fx["g"]?.firstOrNull() ?: 'm'
    fun nounPlural(fx: Fx) = fx["pl"] ?: fx.text

    fun noun(fx: Fx, plural: Boolean, case: DeCase): String {
        if (plural) {
            val pl = nounPlural(fx)
            return if (case == DeCase.DAT && !pl.endsWith("n") && !pl.endsWith("s")) pl + "n" else pl
        }
        return if (case != DeCase.NOM && fx.has("w")) fx["w"]!! else fx.text
    }

    fun article(g: Char, plural: Boolean, case: DeCase) = definite.getValue(key(g, plural))[case.ordinal]
    fun demonstrative(g: Char, plural: Boolean, case: DeCase) = dieser.getValue(key(g, plural))[case.ordinal]

    /** ein, kein, mein, dein, euer, unser, ihr + their ending. */
    fun einWord(stem: String, g: Char, plural: Boolean, case: DeCase): String {
        val ending = einEnd.getValue(key(g, plural))[case.ordinal]
        return if (stem == "euer" && ending.isNotEmpty()) "eur$ending" else stem + ending
    }

    fun adjective(fx: Fx, type: Declension, g: Char, plural: Boolean, case: DeCase): String {
        val stem = fx["st"] ?: fx.text
        val ending = when (type) {
            Declension.WEAK -> weak.getValue(key(g, plural))[case.ordinal]
            Declension.MIXED -> mixed.getValue(key(g, plural))[case.ordinal]
            Declension.STRONG -> strongPlural[case.ordinal]
        }
        // müde + en → müden (no double e)
        return if (stem.endsWith("e") && ending.startsWith("e")) stem + ending.drop(1) else stem + ending
    }

    fun present(fx: Fx): List<String> {
        fx["p"]?.let { return it.split(',') }
        val core = fx.text.removePrefix(fx["sep"].orEmpty())
        val stem = core.dropLast(2)
        val needE = stem.endsWith("d") || stem.endsWith("t") ||
            (stem.length >= 2 && stem.last() in "mn" && stem[stem.length - 2] !in "aeiouäöüéhlr" && stem[stem.length - 2] != stem.last())
        val du = if (stem.last() in "sßxz") "t" else if (needE) "est" else "st"
        val t = if (needE) "et" else "t"
        return listOf(stem + "e", stem + du, stem + t, stem + "en", stem + t, stem + "en")
    }

    fun preterite(fx: Fx): List<String>? = fx["pt"]?.split(',')
    fun participle(fx: Fx) = fx["pp"] ?: error("No participle for ${fx.text}")
    fun usesSein(fx: Fx) = fx["aux"] == "sein"

    /** The infinitive with "zu": ankommen → anzukommen. */
    fun zuInfinitive(fx: Fx): List<String> {
        val sep = fx["sep"]
        return if (sep != null) listOf(sep + "zu" + fx.text.removePrefix(sep)) else listOf("zu", fx.text)
    }

    val persons = listOf("ich", "du", "er/sie/es", "wir", "ihr", "sie")

    fun note(entry: Entry, fx: Fx): String? = when (entry) {
        is Noun -> {
            val g = gender(fx)
            val art = definite.getValue(g)[0]
            "$art ${fx.text} : nom ${when (g) { 'm' -> "masculin"; 'f' -> "féminin"; else -> "neutre" }}, pluriel « die ${nounPlural(fx)} »"
        }
        is Adj -> "${fx.text} : ${fx.text} (après sein), ${adjective(fx, Declension.WEAK, 'm', false, DeCase.NOM)} / " +
            "${adjective(fx, Declension.MIXED, 'm', false, DeCase.NOM)} (devant un nom)"
        is Verb -> {
            val table = present(fx).mapIndexed { i, f -> "${persons[i]} $f" }.joinToString(", ") +
                (fx["sep"]?.let { " (préfixe séparable : $it, rejeté en fin de phrase)" } ?: "")
            val pret = preterite(fx)?.let { " — prétérit : ${it[0]}" }.orEmpty()
            "${fx.text} : $table — participe : ${participle(fx)} (${if (usesSein(fx)) "sein" else "haben"})$pret"
        }
        else -> null
    }
}

internal class GermanRealizer(private val lex: Lexicon) : Realizer {
    override val lang = Lang.DE
    override val caps = Caps(infinitive = { v -> lex.fx(v, Lang.DE)?.has("inf") == true })

    private val haben = listOf("habe", "hast", "hat", "haben", "habt", "haben")
    private val sein = listOf("bin", "bist", "ist", "sind", "seid", "sind")
    private val war = listOf("war", "warst", "war", "waren", "wart", "waren")
    private val werden = listOf("werde", "wirst", "wird", "werden", "werdet", "werden")

    private fun fx(e: Entry) = lex.fx(e, Lang.DE) ?: error("No German translation for ${e.id}")

    private class Subj(val tokens: List<String>, val person: Int)

    /** The pieces of a clause, so it can be written as a main clause, an inverted one or a subordinate one. */
    private class Parts(
        val subject: List<String>,
        val fin: String,
        val prefix: String?,
        val mid: List<String>,
        val end: List<String>,
        val post: List<String>,
    ) {
        fun main() = subject + fin + mid + end + listOfNotNull(prefix) + post
        fun inverted() = listOf(fin) + subject + mid + end + listOfNotNull(prefix) + post
        fun subordinate() = subject + mid + end + ((prefix ?: "") + fin) + post
    }

    override fun realize(node: Node, sink: Sink): List<T> {
        sink.rule(R_CAPITALS)
        val raw = when (node) {
            is NpNode -> np(node, DeCase.NOM, sink)
            is SentenceNode -> sentence(node, sink)
        }
        // "sie\u0001f." carries the hint "f." (elles) for the prompt.
        return finish(raw, sink).map { t -> if ('\u0001' in t) T(t.substringBefore('\u0001'), t.substringAfter('\u0001')) else T(t) }
    }

    private fun finish(tokens: List<String>, sink: Sink): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            if (t == "in" && (next == "dem" || next == "das")) {
                out += if (next == "dem") "im" else "ins"; i += 2; sink.rule(R_CONTRACTION)
            } else { out += t; i++ }
        }
        return out
    }

    // ───────── Noun phrases ─────────

    private fun np(n: NpNode, case: DeCase, sink: Sink, kein: Boolean = false): List<String> {
        val nf = fx(n.noun)
        val g = DeMorph.gender(nf)
        val plural = n.plural
        val out = ArrayList<String>()
        sink.rule(R_ARTICLE)
        val type: DeMorph.Declension
        if (kein) {
            sink.rule(R_NEGATION)
            out += DeMorph.einWord("kein", g, plural, case); type = DeMorph.Declension.MIXED
        } else when (n.det) {
            Det.THE -> { out += DeMorph.article(g, plural, case); type = DeMorph.Declension.WEAK; sink.alt("der", "die", "das", "den", "dem") }
            Det.A -> if (plural) {
                type = DeMorph.Declension.STRONG
            } else {
                out += DeMorph.einWord("ein", g, false, case); type = DeMorph.Declension.MIXED; sink.alt("ein", "eine", "einen", "einem", "einer")
            }
            Det.MY -> { sink.rule(R_POSSESSIVE); out += DeMorph.einWord("mein", g, plural, case); type = DeMorph.Declension.MIXED; sink.alt("mein", "meine", "meinen", "meinem", "meiner") }
            Det.YOUR -> {
                sink.rule(R_POSSESSIVE)
                out += DeMorph.einWord(if (n.yourTu) "dein" else "euer", g, plural, case); type = DeMorph.Declension.MIXED
                sink.alt("dein", "deine", "euer", "eure")
            }
            Det.OUR -> { sink.rule(R_POSSESSIVE); out += DeMorph.einWord("unser", g, plural, case); type = DeMorph.Declension.MIXED; sink.alt("unser", "unsere", "unseren") }
            Det.THEIR -> { sink.rule(R_POSSESSIVE); out += DeMorph.einWord("ihr", g, plural, case); type = DeMorph.Declension.MIXED; sink.alt("ihr", "ihre", "ihren") }
            Det.THIS -> { out += DeMorph.demonstrative(g, plural, case); type = DeMorph.Declension.WEAK; sink.alt("dieser", "diese", "dieses", "diesen") }
        }
        if (case != DeCase.NOM) sink.rule(R_CASES)
        if (plural) sink.rule(R_PLURAL)
        for (a in n.adjs) {
            sink.rule(R_ADJECTIVE)
            val af = fx(a)
            out += DeMorph.adjective(af, type, g, plural, case)
            sink.alt(af.text + "e", af.text + "en", af.text + "er", af.text + "es")
        }
        out += DeMorph.noun(nf, plural, case)
        sink.alt(DeMorph.noun(nf, !plural, case))
        n.modifier?.let { m ->
            sink.rule(R_PREPOSITIONS)
            out += if (m.prep == ModPrep.WITH) "mit" else "in"
            out += np(m.np, DeCase.DAT, sink)
        }
        n.relative?.let { r ->
            sink.rule(R_RELATIVE)
            val rel = DeMorph.article(g, plural, DeCase.NOM)
            val s = Subj(listOf(rel), if (plural) 5 else 2)
            val obj = r.obj?.let { objectTokens(r.verb, it, false, sink) }.orEmpty()
            val parts = verbParts(s, r.verb, Tense.PRESENT, mid = obj, sink = sink)
            out += parts.subordinate()
        }
        return out
    }

    private fun objectTokens(v: Verb, obj: NpNode, kein: Boolean, sink: Sink): List<String> {
        val dative = fx(v)["objc"] == "dat"
        if (dative) sink.rule(R_DATIVE_VERB)
        return np(obj, if (dative) DeCase.DAT else DeCase.ACC, sink, kein)
    }

    // ───────── Clauses ─────────

    private fun subject(s: SubjNode, sink: Sink): Subj = when (s) {
        is SubjNp -> Subj(np(s.np, DeCase.NOM, sink), if (s.np.plural) 5 else 2)
        is SubjPron -> {
            sink.rule(R_PRONOUN)
            sink.alt("ich", "du", "er", "sie", "wir", "ihr")
            when (s.pron) {
                Pron.I -> Subj(listOf("ich"), 0)
                Pron.YOU_TU -> Subj(listOf("du"), 1)
                Pron.YOU_VOUS -> Subj(listOf("ihr"), 4)
                Pron.HE -> Subj(listOf("er"), 2)
                Pron.SHE -> Subj(listOf("sie"), 2)
                Pron.WE -> Subj(listOf("wir"), 3)
                Pron.THEY_M -> Subj(listOf("sie"), 5)
                Pron.THEY_F -> Subj(listOf("sie\u0001f."), 5)
            }
        }
    }

    /** The finite verb and the verbs that go to the end of the clause. */
    private fun verbParts(
        s: Subj, v: Verb, tense: Tense, mid: List<String>,
        bareInfinitive: List<String>? = null, post: List<String> = emptyList(), sink: Sink,
    ): Parts {
        val vf = fx(v)
        val p = s.person
        val present = DeMorph.present(vf)
        sink.alt(present)
        val prefix = vf["sep"]
        val bare = v.modal || (vf["inf"] == "" && bareInfinitive != null)
        var fin: String
        var finPrefix: String? = null
        val end = ArrayList<String>()
        when {
            v.modal -> { sink.rule(R_MODAL); fin = present[p]; end += bareInfinitive.orEmpty() }
            bare -> {
                sink.rule(R_MODAL)
                val pt = DeMorph.preterite(vf)
                fin = if (tense == Tense.PAST && pt != null) pt[p] else present[p]
                end += bareInfinitive.orEmpty()
            }
            tense == Tense.PRESENT -> { sink.rule(R_PRESENT); fin = present[p]; finPrefix = prefix }
            tense == Tense.PAST -> {
                val pt = DeMorph.preterite(vf)
                if (pt != null) { sink.rule(R_PRETERITE); fin = pt[p]; finPrefix = prefix }
                else {
                    sink.rule(R_PERFECT)
                    val aux = if (DeMorph.usesSein(vf)) sein else haben
                    sink.alt(aux)
                    fin = aux[p]; end += DeMorph.participle(vf)
                }
            }
            else -> { sink.rule(R_FUTURE); sink.alt(werden); fin = werden[p]; end += vf.text }
        }
        if (finPrefix != null) sink.rule(R_SEPARABLE)
        return Parts(s.tokens, fin, finPrefix, mid, end, post)
    }

    /** "nicht" unless the object already carries "kein". */
    private fun negation(neg: Boolean, kein: Boolean, sink: Sink): List<String> =
        if (neg && !kein) { sink.rule(R_NEGATION); listOf("nicht") } else emptyList()

    private fun adverbTokens(a: Adverb?): Pair<List<String>, List<String>> {
        if (a == null) return emptyList<String>() to emptyList()
        val f = fx(a)
        val words = (f["s"] ?: f.text).split(' ')
        return if (f.has("t")) words to emptyList() else emptyList<String>() to words
    }

    /** Place complement: preposition + case from the verb's `loc` option. */
    private fun place(v: Verb, loc: NpNode, sink: Sink): List<String> {
        val spec = fx(v)["loc"].orEmpty().split(':')
        val prep = spec.getOrElse(0) { "" }
        val case = if (spec.getOrElse(1) { "dat" } == "acc") DeCase.ACC else DeCase.DAT
        sink.rule(R_PREPOSITIONS)
        return (if (prep.isNotEmpty()) listOf(prep) else emptyList()) + np(loc, case, sink)
    }

    private fun verbPred(s: Subj, p: VerbPred, tense: Tense, neg: Boolean, sink: Sink): Parts {
        val kein = neg && p.obj != null && p.obj.det == Det.A
        val (timeAdv, otherAdv) = adverbTokens(p.adv)
        val mid = ArrayList<String>()
        mid += timeAdv
        p.obj?.let { mid += objectTokens(p.verb, it, kein, sink) }
        mid += negation(neg, kein, sink)
        p.loc?.let { mid += place(p.verb, it, sink) }
        mid += otherAdv
        return verbParts(s, p.verb, tense, mid, sink = sink)
    }

    private fun infinitiveToken(v: Verb): String = fx(v).text

    private fun clauseParts(c: ClauseNode, sink: Sink): Parts {
        val s = subject(c.subj, sink)
        val parts = when (val p = c.pred) {
            is CopAdj -> {
                val af = fx(p.adj)
                sink.rule(R_ADJECTIVE_PRED)
                copula(s, c.tense, listOf(af.text) .let { negation(c.neg, false, sink) + it }, sink)
            }
            is CopLoc -> {
                sink.rule(R_PREPOSITIONS)
                copula(s, c.tense, negation(c.neg, false, sink) + "in" + np(p.place, DeCase.DAT, sink), sink)
            }
            is VerbPred -> verbPred(s, p, c.tense, c.neg, sink)
            is ModalPred -> {
                val inner = p.inner
                val kein = c.neg && inner.obj != null && inner.obj.det == Det.A
                val (timeAdv, otherAdv) = adverbTokens(inner.adv)
                val mid = ArrayList<String>(timeAdv)
                inner.obj?.let { mid += objectTokens(inner.verb, it, kein, sink) }
                mid += negation(c.neg, kein, sink)
                inner.loc?.let { mid += place(inner.verb, it, sink) }
                mid += otherAdv
                verbParts(s, p.modal, Tense.PRESENT, mid, listOf(infinitiveToken(inner.verb)), sink = sink)
            }
            is InfPred -> {
                val inner = p.inner
                val of = fx(p.outer)
                sink.alt(infinitiveToken(inner.verb))
                if (of["inf"] == "") {
                    // wollen: the infinitive goes to the end, like a modal verb.
                    val kein = c.neg && inner.obj != null && inner.obj.det == Det.A
                    val (timeAdv, otherAdv) = adverbTokens(inner.adv)
                    val mid = ArrayList<String>(timeAdv)
                    inner.obj?.let { mid += objectTokens(inner.verb, it, kein, sink) }
                    mid += negation(c.neg, kein, sink)
                    inner.loc?.let { mid += place(inner.verb, it, sink) }
                    mid += otherAdv
                    verbParts(s, p.outer, c.tense, mid, listOf(infinitiveToken(inner.verb)), sink = sink)
                } else {
                    // versuchen, anfangen… : "zu" + infinitive in a clause placed after the verb group.
                    sink.rule(R_ZU)
                    val kein = false
                    val (timeAdv, otherAdv) = adverbTokens(inner.adv)
                    val post = ArrayList<String>(timeAdv)
                    inner.obj?.let { post += objectTokens(inner.verb, it, kein, sink) }
                    inner.loc?.let { post += place(inner.verb, it, sink) }
                    post += otherAdv
                    post += DeMorph.zuInfinitive(fx(inner.verb))
                    verbParts(s, p.outer, c.tense, negation(c.neg, false, sink), post = post, sink = sink)
                }
            }
        }
        return parts
    }

    private fun copula(s: Subj, tense: Tense, complement: List<String>, sink: Sink): Parts {
        sink.rule(R_SEIN)
        sink.alt(sein + war)
        val p = s.person
        return when (tense) {
            Tense.PRESENT -> Parts(s.tokens, sein[p], null, complement, emptyList(), emptyList())
            Tense.PAST -> Parts(s.tokens, war[p], null, complement, emptyList(), emptyList())
            Tense.FUTURE -> { sink.rule(R_FUTURE); Parts(s.tokens, werden[p], null, complement, listOf("sein"), emptyList()) }
        }
    }

    private fun sentence(n: SentenceNode, sink: Sink): List<String> {
        val parts = n.clauses.map { clauseParts(it, sink) }
        // Hint on "sie" meaning "elles": only the first clause of the sentence can carry it.
        if (n.joiners.isNotEmpty()) sink.rule(R_CONNECTORS)
        if (parts.size == 1) return parts[0].main()
        val out = ArrayList<String>()
        if (n.joiners[0] == Conn.IF) {
            sink.rule(R_VERB_FINAL)
            out += "wenn"; out += parts[0].subordinate(); out += parts[1].inverted()
            sink.rule(R_V2)
            return out
        }
        out += parts[0].main()
        for ((i, joiner) in n.joiners.withIndex()) {
            val next = parts[i + 1]
            when (joiner) {
                Conn.AND -> { out += "und"; out += next.main() }
                Conn.BUT -> { out += "aber"; out += next.main() }
                Conn.BECAUSE -> { sink.rule(R_VERB_FINAL); out += "weil"; out += next.subordinate() }
                Conn.WHEN -> {
                    sink.rule(R_VERB_FINAL)
                    out += if (n.clauses[i + 1].tense == Tense.PAST) "als" else "wenn"
                    out += next.subordinate()
                }
                Conn.IF -> { out += "wenn"; out += next.subordinate() }
            }
        }
        return out
    }

    private companion object {
        fun r(key: String, title: String, text: String, example: String) = RuleInfo("de:$key", title, text, example)

        val R_CAPITALS = r("capitals", "Majuscule aux noms",
            "En allemand, tous les noms prennent une majuscule, même au milieu de la phrase.", "der Hund · die Stadt · das Haus")
        val R_ARTICLE = r("article", "Genre : der, die, das",
            "Trois genres : masculin (der), féminin (die), neutre (das) ; pluriel : die. Indéfini : ein (m., n.), eine (f.). " +
                "Le genre ne se devine pas toujours : il faut l'apprendre avec le nom.",
            "der Hund · die Frau · das Haus · die Kinder · ein Buch · eine Katze")
        val R_CASES = r("cases", "Les cas : nominatif, accusatif, datif",
            "Le nominatif est le sujet. L'accusatif est le complément d'objet direct : seul le masculin change (der → den, ein → einen). " +
                "Le datif suit mit, in (lieu), et certains verbes : der → dem, die → der, das → dem, pluriel : den + -n au nom.",
            "Der Mann sieht den Hund · mit dem Mann · in der Stadt · in den Park (direction = accusatif)")
        val R_PLURAL = r("plural", "Pluriel des noms",
            "Le pluriel est irrégulier et s'apprend avec le nom : -e, -er, -en, -n, - (avec ou sans tréma ä ö ü), -s.",
            "der Hund → die Hunde · das Buch → die Bücher · die Frau → die Frauen")
        val R_ADJECTIVE = r("adjective", "Terminaisons des adjectifs",
            "Devant un nom, l'adjectif prend une terminaison selon l'article : après der/dieser : -e (nominatif et féminin/neutre à l'accusatif), -en ailleurs ; " +
                "après ein/mein : -er (m.), -e (f.), -es (n.) au nominatif ; sans article au pluriel : -e (nom./acc.), -en (datif).",
            "der große Hund · ein großer Hund · eine große Katze · große Hunde · mit dem großen Hund")
        val R_ADJECTIVE_PRED = r("adjective-pred", "Adjectif attribut",
            "Après sein (être), l'adjectif ne prend aucune terminaison.", "Der Hund ist groß · Die Katzen sind müde")
        val R_POSSESSIVE = r("possessive", "Possessifs",
            "mein, dein (tu), euer (vous), unser, ihr (leur) se déclinent comme ein : mein Hund, meine Katze, meinen Hund (accusatif).",
            "mein Haus · meine Freunde · euer Auto · unsere Kinder")
        val R_PRONOUN = r("pronoun", "Pronoms sujets",
            "ich, du, er, sie (elle), wir, ihr (vous, pluriel / familier) et sie (ils/elles : même mot que « elle », seule la conjugaison les distingue).",
            "sie isst (elle mange) · sie essen (ils/elles mangent)")
        val R_PRESENT = r("present", "Présent",
            "Radical + -e, -st, -t, -en, -t, -en. Si le radical se termine par -d ou -t, on ajoute un e (du arbeitest). " +
                "Certains verbes changent de voyelle à du/er : essen → du isst, fahren → du fährst, sehen → du siehst.",
            "ich wohne · du arbeitest · er isst · wir gehen")
        val R_PERFECT = r("perfect", "Le Perfekt",
            "haben (ou sein pour aller, venir, rester…) au présent en 2e position + participe passé à la fin de la phrase. " +
                "Le participe est souvent en ge-…-t (gekauft) ou ge-…-en (gegessen).",
            "Ich habe einen Apfel gegessen · Er ist nach Hause gegangen")
        val R_PRETERITE = r("preterite", "Prétérit",
            "Pour haben, mögen et wollen on utilise plutôt le prétérit (hatte, mochte, wollte) que le Perfekt.",
            "Er hatte einen Hund · Sie wollte essen")
        val R_FUTURE = r("future", "Futur avec werden",
            "werden (werde, wirst, wird, werden, werdet, werden) en 2e position + infinitif à la fin de la phrase.",
            "Ich werde einen Apfel essen · Er wird groß sein")
        val R_NEGATION = r("negation", "nicht et kein",
            "kein (déclinaison de ein) nie un nom précédé de ein ou sans article ; nicht dans les autres cas, " +
                "en général juste avant l'adjectif, le complément de lieu ou le verbe à la fin de la phrase.",
            "Ich habe keinen Hund · Ich esse den Apfel nicht · Er ist nicht groß")
        val R_MODAL = r("modal", "Verbes modaux",
            "können, müssen, wollen se conjuguent en 2e position et l'infinitif du verbe principal part à la fin, sans « zu ».",
            "Ich kann den Apfel essen · Er will nicht schlafen")
        val R_ZU = r("zu-infinitive", "zu + infinitif",
            "Après versuchen, anfangen, lernen, beschließen, hoffen…, on utilise « zu » + infinitif (virgule en allemand) ; " +
                "avec un verbe séparable, zu s'insère : anzukommen.",
            "Er versucht, einen Apfel zu essen · Sie beginnt, zu singen")
        val R_V2 = r("v2", "Le verbe en deuxième position",
            "Dans une proposition principale, le verbe conjugué est toujours en 2e position. " +
                "Après une proposition subordonnée placée en tête, le verbe de la principale passe avant le sujet.",
            "Wenn er isst, trinke ich Wasser")
        val R_VERB_FINAL = r("verb-final", "Subordonnées : verbe à la fin",
            "Après weil, wenn, als, dass et dans une relative, le verbe conjugué part à la fin de la proposition.",
            "Er isst, weil er Hunger hat · der Mann, der einen Apfel isst")
        val R_SEPARABLE = r("separable", "Verbes à particule séparable",
            "Au présent, la particule (an-, weg-…) se détache et va à la fin de la phrase ; dans une subordonnée elle reste collée au verbe.",
            "Er kommt in der Stadt an · weil er ankommt")
        val R_SEIN = r("sein", "Le verbe sein",
            "sein : ich bin, du bist, er ist, wir sind, ihr seid, sie sind ; passé : war, warst, war, waren, wart, waren.",
            "Er ist müde · Sie waren zu Hause")
        val R_DATIVE_VERB = r("dative-verb", "Verbes + datif",
            "helfen et folgen sont suivis du datif, pas de l'accusatif.", "Ich helfe dem Mann · Sie folgt der Frau")
        val R_PREPOSITIONS = r("prepositions", "Prépositions et cas",
            "mit + datif ; in + datif pour un lieu où l'on est (in der Stadt), in + accusatif pour une direction (in die Stadt).",
            "mit dem Hund · in dem Park (im Park) · Er geht in die Stadt")
        val R_CONTRACTION = r("contraction", "Contractions", "in + dem = im ; in + das = ins.", "im Haus · ins Haus")
        val R_RELATIVE = r("relative", "Proposition relative",
            "Pronom relatif = article défini (der, die, das, die) et verbe à la fin.", "der Mann, der einen Apfel isst")
        val R_CONNECTORS = r("connectors", "Mots de liaison",
            "und (et) et aber (mais) ne changent pas l'ordre ; weil (parce que), wenn (si, quand), als (quand, au passé) envoient le verbe à la fin.",
            "Er isst und sie trinkt · Er schläft, weil er müde ist")
    }
}
