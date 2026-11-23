package com.lingoquest.engine

/** Spanish morphology shared by the realizer and the word notes. */
internal object EsMorph {
    private val unaccent = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u')
    private const val VOWELS = "aeiouáéíóú"

    fun plural(w: String): String {
        val last = w.last()
        return when {
            last in "aeiouáéó" -> w + "s"
            last in "íú" -> w + "es"
            last == 'z' -> w.dropLast(1) + "ces"
            last == 's' -> when {
                w.length > 2 && w[w.length - 2] in "áéíóú" -> w.dropLast(2) + unaccent.getValue(w[w.length - 2]) + "ses"
                countVowelGroups(w) >= 2 -> w
                else -> w + "es"
            }
            last == 'n' && w.length > 1 && w[w.length - 2] in unaccent -> w.dropLast(2) + unaccent.getValue(w[w.length - 2]) + "nes"
            else -> w + "es"
        }
    }

    private fun countVowelGroups(w: String): Int {
        var n = 0
        var inGroup = false
        for (c in w) {
            val v = c in VOWELS
            if (v && !inGroup) n++
            inGroup = v
        }
        return n
    }

    fun isFeminine(fx: Fx) = fx["g"] == "f"
    fun nounPlural(fx: Fx) = fx["pl"] ?: plural(fx.text)

    /** Agreement of an adjective (not the short form before a noun). */
    fun adjective(fx: Fx, feminine: Boolean, plural: Boolean): String {
        val base = fx.text
        val sg = if (feminine && base.endsWith("o")) base.dropLast(1) + "a" else base
        if (!plural) return sg
        return fx["pl"] ?: plural(sg)
    }

    fun verbPresent(fx: Fx): List<String> {
        fx["p"]?.let { return it.split(',') }
        val inf = fx.text
        val stem = inf.dropLast(2)
        val ends = when {
            inf.endsWith("ar") -> listOf("o", "as", "a", "amos", "áis", "an")
            inf.endsWith("er") -> listOf("o", "es", "e", "emos", "éis", "en")
            else -> listOf("o", "es", "e", "imos", "ís", "en")
        }
        return ends.map { stem + it }
    }

    fun participle(fx: Fx): String = fx["pp"] ?: (fx.text.dropLast(2) + if (fx.text.endsWith("ar")) "ado" else "ido")

    val persons = listOf("yo", "tú", "él/ella", "nosotros", "vosotros", "ellos/ellas")

    fun note(entry: Entry, fx: Fx): String? = when (entry) {
        is Noun -> "${fx.text} : nom ${if (isFeminine(fx)) "féminin" else "masculin"}, pluriel « ${nounPlural(fx)} »"
        is Adj -> {
            val forms = listOf(adjective(fx, false, false), adjective(fx, true, false), adjective(fx, false, true), adjective(fx, true, true)).distinct()
            "${fx.text} : ${forms.joinToString(", ")}" + if (fx.has("b")) " — se place avant le nom" else " — se place après le nom"
        }
        is Verb -> {
            val table = verbPresent(fx).mapIndexed { i, f -> "${persons[i]} $f" }.joinToString(", ")
            "${fx.text} : $table — participe : ${participle(fx)} (haber)"
        }
        else -> null
    }
}

internal class SpanishRealizer(private val lex: Lexicon) : Realizer {
    override val lang = Lang.ES

    private val haber = listOf("he", "has", "ha", "hemos", "habéis", "han")
    private val ser = listOf("soy", "eres", "es", "somos", "sois", "son")
    private val estar = listOf("estoy", "estás", "está", "estamos", "estáis", "están")
    private val serImperfect = listOf("era", "eras", "era", "éramos", "erais", "eran")
    private val estarImperfect = listOf("estaba", "estabas", "estaba", "estábamos", "estabais", "estaban")
    private val ir = listOf("voy", "vas", "va", "vamos", "vais", "van")

    private fun fx(e: Entry) = lex.fx(e, Lang.ES) ?: error("No Spanish translation for ${e.id}")

    private class Subj(val tokens: List<String>, val person: Int, val feminine: Boolean)

    override fun realize(node: Node, sink: Sink): List<T> {
        val raw = when (node) {
            is NpNode -> np(node, sink)
            is SentenceNode -> sentence(node, sink)
        }
        return finish(raw, sink).map { T(it) }
    }

    /** al / del, and "y" becomes "e" before an i- sound. */
    private fun finish(tokens: List<String>, sink: Sink): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            when {
                (t == "a" || t == "de") && next == "el" -> { out += if (t == "a") "al" else "del"; i += 2; sink.rule(R_CONTRACTION) }
                t == "y" && next != null && (next.startsWith("i") && !next.startsWith("ie") || next.startsWith("hi") && !next.startsWith("hie")) -> {
                    out += "e"; i++; sink.rule(R_CONNECTORS)
                }
                else -> { out += t; i++ }
            }
        }
        return out
    }

    // ───────── Noun phrases ─────────

    private fun np(n: NpNode, sink: Sink): List<String> {
        val nf = fx(n.noun)
        val fem = EsMorph.isFeminine(nf)
        val stressedA = nf.has("st")
        val plural = n.plural
        // "el agua" and "un agua": feminine nouns that start with a stressed a take the masculine singular article.
        val mascArt = !fem || (stressedA && !plural)
        fun pick(m: String, f: String, mp: String, fp: String) = if (plural) (if (fem) fp else mp) else if (mascArt) m else f

        val before = n.adjs.filter { fx(it).has("b") }
        val after = n.adjs.filter { !fx(it).has("b") }
        val out = ArrayList<String>()
        sink.rule(R_ARTICLE)
        when (n.det) {
            Det.THE -> { sink.alt("el", "la", "los", "las", "un", "una"); out += pick("el", "la", "los", "las") }
            Det.A -> { sink.alt("un", "una", "unos", "unas", "el", "la"); out += pick("un", "una", "unos", "unas") }
            Det.MY -> { sink.rule(R_POSSESSIVE); sink.alt("mi", "mis", "tu", "tus"); out += if (plural) "mis" else "mi" }
            Det.YOUR -> {
                sink.rule(R_POSSESSIVE)
                if (n.yourTu) { sink.alt("tu", "tus", "mi", "mis", "su"); out += if (plural) "tus" else "tu" }
                else { sink.alt("vuestro", "vuestra", "vuestros", "vuestras", "nuestro"); out += pick("vuestro", "vuestra", "vuestros", "vuestras") }
            }
            Det.OUR -> { sink.rule(R_POSSESSIVE); sink.alt("nuestro", "nuestra", "nuestros", "nuestras", "vuestro"); out += pick("nuestro", "nuestra", "nuestros", "nuestras") }
            Det.THEIR -> { sink.rule(R_POSSESSIVE); sink.alt("su", "sus", "mi", "tu"); out += if (plural) "sus" else "su" }
            Det.THIS -> { sink.alt("este", "esta", "estos", "estas", "ese"); out += pick("este", "esta", "estos", "estas") }
        }
        if (plural) sink.rule(R_PLURAL)
        if (n.adjs.isNotEmpty()) { sink.rule(R_ADJECTIVE) }
        val noun = if (plural) EsMorph.nounPlural(nf) else nf.text
        sink.alt(if (plural) nf.text else EsMorph.nounPlural(nf))
        for (a in before) {
            val af = fx(a)
            val short = af["ap"]
            val form = if (short != null && !plural && !fem) short else EsMorph.adjective(af, fem, plural)
            out += form
            sink.alt(EsMorph.adjective(af, fem, plural), EsMorph.adjective(af, !fem, plural))
        }
        out += noun.split(' ')
        for ((i, a) in after.withIndex()) {
            if (i == 1) out += "y"
            val af = fx(a)
            out += EsMorph.adjective(af, fem, plural)
            sink.alt(EsMorph.adjective(af, !fem, plural), EsMorph.adjective(af, fem, !plural))
        }
        n.modifier?.let { m ->
            sink.rule(R_PREP)
            out += if (m.prep == ModPrep.WITH) "con" else "en"
            out += np(m.np, sink)
        }
        n.relative?.let { r ->
            sink.rule(R_RELATIVE)
            val s = Subj(emptyList(), if (n.plural) 5 else 2, fem)
            val rest = r.obj?.let { objectTokens(r.verb, it, sink) }.orEmpty()
            out += "que"
            out += predicate(s, r.verb, Tense.PRESENT, false, rest, sink)
        }
        return out
    }

    /** Direct objects that are people take the "a personal". */
    private fun objectTokens(v: Verb, obj: NpNode, sink: Sink): List<String> {
        val tokens = np(obj, sink)
        if (obj.noun.hasTag("P") && v.id != "v:have") {
            sink.rule(R_PERSONAL_A)
            return listOf("a") + tokens
        }
        return tokens
    }

    // ───────── Clauses ─────────

    private fun subject(s: SubjNode, sink: Sink): Subj = when (s) {
        is SubjNp -> Subj(np(s.np, sink), if (s.np.plural) 5 else 2, EsMorph.isFeminine(fx(s.np.noun)))
        is SubjPron -> {
            sink.rule(R_PRONOUN)
            sink.alt("yo", "tú", "él", "ella", "nosotros", "vosotros", "ellos", "ellas")
            when (s.pron) {
                Pron.I -> Subj(listOf("yo"), 0, false)
                Pron.YOU_TU -> Subj(listOf("tú"), 1, false)
                Pron.YOU_VOUS -> Subj(listOf("vosotros"), 4, false)
                Pron.HE -> Subj(listOf("él"), 2, false)
                Pron.SHE -> Subj(listOf("ella"), 2, true)
                Pron.WE -> Subj(listOf("nosotros"), 3, false)
                Pron.THEY_M -> Subj(listOf("ellos"), 5, false)
                Pron.THEY_F -> Subj(listOf("ellas"), 5, true)
            }
        }
    }

    private fun predicate(s: Subj, v: Verb, tense: Tense, neg: Boolean, rest: List<String>, sink: Sink): List<String> {
        val vf = fx(v)
        val p = s.person
        val present = EsMorph.verbPresent(vf)
        val out = ArrayList<String>()
        if (neg) { sink.rule(R_NEGATION); out += "no" }
        sink.alt(present + vf.text + EsMorph.participle(vf))
        when {
            v.modal -> { sink.rule(R_MODAL); out += present[p] }
            tense == Tense.PRESENT -> { sink.rule(R_PRESENT); out += present[p] }
            tense == Tense.PAST -> {
                sink.rule(R_PERFECT); sink.alt(haber)
                out += haber[p]; out += EsMorph.participle(vf)
            }
            tense == Tense.FUTURE -> { sink.rule(R_FUTURE); sink.alt(ir); out += ir[p]; out += "a"; out += vf.text }
        }
        return out + rest
    }

    private fun copula(s: Subj, estarVerb: Boolean, tense: Tense, neg: Boolean, rest: List<String>, sink: Sink): List<String> {
        sink.rule(R_SER_ESTAR)
        val p = s.person
        val out = ArrayList<String>()
        if (neg) { sink.rule(R_NEGATION); out += "no" }
        sink.alt(ser + estar)
        when (tense) {
            Tense.PRESENT -> out += (if (estarVerb) estar else ser)[p]
            Tense.PAST -> out += (if (estarVerb) estarImperfect else serImperfect)[p]
            Tense.FUTURE -> { sink.rule(R_FUTURE); out += ir[p]; out += "a"; out += if (estarVerb) "estar" else "ser" }
        }
        return out + rest
    }

    private fun complements(p: VerbPred, sink: Sink): List<String> {
        val out = ArrayList<String>()
        p.obj?.let { out += objectTokens(p.verb, it, sink) }
        p.loc?.let { loc ->
            val prep = fx(p.verb)["loc"].orEmpty()
            if (prep.isNotEmpty()) out += prep
            out += np(loc, sink)
        }
        p.adv?.let { a -> out += fx(a).text.split(' ') }
        return out
    }

    private fun clause(c: ClauseNode, sink: Sink): List<String> {
        val s = subject(c.subj, sink)
        val body = when (val p = c.pred) {
            is CopAdj -> {
                val af = fx(p.adj)
                sink.rule(R_ADJECTIVE)
                copula(s, af.has("e"), c.tense, c.neg, listOf(EsMorph.adjective(af, s.feminine, s.person == 4 || s.person == 5 || s.person == 3)), sink)
            }
            is CopLoc -> copula(s, true, c.tense, c.neg, listOf("en") + np(p.place, sink), sink)
            is VerbPred -> predicate(s, p.verb, c.tense, c.neg, complements(p, sink), sink)
            is ModalPred -> predicate(s, p.modal, Tense.PRESENT, c.neg, listOf(fx(p.inner.verb).text) + complements(p.inner, sink), sink)
            is InfPred -> {
                sink.rule(R_INFINITIVE)
                val rest = ArrayList<String>()
                fx(p.outer)["inf"]?.takeIf { it.isNotEmpty() }?.let { rest += it }
                rest += fx(p.inner.verb).text
                rest += complements(p.inner, sink)
                predicate(s, p.outer, c.tense, c.neg, rest, sink)
            }
        }
        return s.tokens + body
    }

    private fun sentence(n: SentenceNode, sink: Sink): List<String> {
        val out = ArrayList<String>()
        val conditional = n.joiners.firstOrNull() == Conn.IF
        if (n.joiners.isNotEmpty()) sink.rule(R_CONNECTORS)
        if (conditional) out += "si"
        for ((i, c) in n.clauses.withIndex()) {
            if (i > 0 && !conditional) out += when (n.joiners[i - 1]) {
                Conn.AND -> "y"; Conn.BUT -> "pero"; Conn.BECAUSE -> "porque"; Conn.WHEN -> "cuando"; Conn.IF -> "si"
            }
            out += clause(c, sink)
        }
        return out
    }

    private companion object {
        fun r(key: String, title: String, text: String, example: String) = RuleInfo("es:$key", title, text, example)

        val R_ARTICLE = r("article", "Articles et genre",
            "En espagnol, chaque nom est masculin ou féminin : el/un (masculin), la/una (féminin), los/las (pluriel) et unos/unas pour « des ». " +
                "Les noms féminins qui commencent par un a accentué prennent el/un au singulier : el agua (mais las aguas).",
            "el perro · la casa · los gatos · unas flores · el agua fría")
        val R_PLURAL = r("plural", "Pluriel des noms",
            "Voyelle finale → +s (casa → casas) ; consonne finale → +es (mujer → mujeres) ; -z → -ces (pez → peces) ; " +
                "-ción → -ciones (l'accent disparaît) ; certains mots restent identiques (el paraguas → los paraguas).",
            "casa → casas · ciudad → ciudades · canción → canciones")
        val R_ADJECTIVE = r("adjective", "Accord et place de l'adjectif",
            "L'adjectif s'accorde en genre et en nombre : -o/-a/-os/-as (rojo, roja, rojos, rojas) ; les adjectifs en -e ou en consonne ne changent " +
                "qu'au pluriel (grande → grandes). Il se place le plus souvent après le nom ; bueno et malo se raccourcissent avant un nom masculin " +
                "singulier (buen, mal), primero devient primer.",
            "un perro negro · una casa grande · un buen amigo · la primera vez")
        val R_POSSESSIVE = r("possessive", "Possessifs",
            "mi/mis, tu/tus (tú), vuestro/a/os/as (vosotros), nuestro/a/os/as, su/sus (eux). Comme en français, ils s'accordent avec la chose possédée ; " +
                "mi, tu et su ne varient qu'en nombre.",
            "mi casa · tus libros · nuestras amigas · su coche")
        val R_PRONOUN = r("pronoun", "Pronoms sujets",
            "yo, tú, él, ella, nosotros, vosotros, ellos, ellas. En espagnol on peut souvent les omettre car la terminaison du verbe indique la " +
                "personne (como = je mange). « Vosotros » est le « vous » pluriel familier (Espagne).",
            "yo como · tú comes · ellos comen")
        val R_PRESENT = r("present", "Présent",
            "Trois conjugaisons : -ar (hablo, hablas, habla, hablamos, habláis, hablan), -er (como, comes…) et -ir (vivo, vives, vive, vivimos, vivís, viven). " +
                "Beaucoup de verbes courants sont irréguliers (tener : tengo, tienes, tiene…).",
            "yo hablo · ella come · vosotros vivís")
        val R_PERFECT = r("perfect", "Passé composé : haber + participe",
            "haber (he, has, ha, hemos, habéis, han) + participe passé. Le participe se forme en -ado (-ar) ou -ido (-er/-ir) et ne s'accorde pas. " +
                "Irréguliers : visto, abierto, escrito, descubierto.",
            "he comido · ha visto · hemos escrito")
        val R_FUTURE = r("future", "Futur proche",
            "ir a + infinitif : voy a comer, vas a comer, va a comer, vamos a comer, vais a comer, van a comer.",
            "voy a comer · no va a venir")
        val R_NEGATION = r("negation", "Négation",
            "On place simplement « no » devant le verbe conjugué (ou devant haber / ir).",
            "no como · no he visto · no voy a comer")
        val R_SER_ESTAR = r("ser-estar", "Ser et estar",
            "Deux verbes pour « être ». ser : identité, nature, caractéristique durable (soy, eres, es, somos, sois, son ; imparfait era…). " +
                "estar : état, émotion passagère et lieu (estoy, estás, está, estamos, estáis, están ; imparfait estaba…).",
            "es alto · está cansado · está en la casa")
        val R_PERSONAL_A = r("personal-a", "Le « a » personnel",
            "Quand le complément d'objet direct est une personne déterminée, on le fait précéder de « a » (sauf avec tener).",
            "veo a la mujer · ayudo a mi amigo · tengo un amigo")
        val R_CONTRACTION = r("contraction", "Contractions al et del",
            "a + el = al ; de + el = del. (Les autres articles ne se contractent pas : a la, a los…)",
            "voy al parque · hablo del libro")
        val R_MODAL = r("modal", "poder et deber + infinitif",
            "poder (puedo, puedes, puede…) et deber (debo, debes, debe…) sont suivis de l'infinitif sans préposition.",
            "puedo comer · no debes venir")
        val R_INFINITIVE = r("infinitive", "Verbe + infinitif",
            "querer, intentar, decidir, esperar, prometer, lograr, preferir, desear se construisent directement avec l'infinitif ; empezar a et aprender a " +
                "demandent « a ».",
            "quiero comer · empieza a nadar · aprendo a leer")
        val R_RELATIVE = r("relative", "Proposition relative",
            "« que » introduit la relative (sujet ou objet) : el hombre que come una manzana.",
            "el perro que duerme")
        val R_PREP = r("prep", "Compléments du nom", "con = avec, en = dans : le complément suit le nom.", "la mujer con un libro · el gato en el jardín")
        val R_CONNECTORS = r("connectors", "Mots de liaison",
            "y (et ; devient « e » devant un i : padre e hijo), pero (mais), porque (parce que), cuando (quand), si (si).",
            "como y bebo · si come, bebo")
    }
}
