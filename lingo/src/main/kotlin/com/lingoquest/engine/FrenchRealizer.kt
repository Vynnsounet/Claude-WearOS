package com.lingoquest.engine

/** French side of every course. It also records the grammar rules shown for the English course. */
internal object FrenchRealizer : Realizer {
    override val lang = Lang.FR

    private val avoirPres = listOf("ai", "as", "a", "avons", "avez", "ont")
    private val etrePres = listOf("suis", "es", "est", "sommes", "êtes", "sont")
    private val etreImparfait = listOf("étais", "étais", "était", "étions", "étiez", "étaient")
    private val allerPres = listOf("vais", "vas", "va", "allons", "allez", "vont")

    private class Subj(val tokens: List<String>, val person: Int, val gender: Gender, val plural: Boolean)

    override fun realize(node: Node, sink: Sink): List<T> {
        val raw = when (node) {
            is NpNode -> np(node, sink)
            is SentenceNode -> sentence(node, sink)
        }
        val fixed = Spelling.french(raw)
        if (fixed != raw) sink.rule(Rule.ELISION)
        return fixed.map { T(it) }
    }

    override fun join(tokens: List<String>) = Spelling.join(tokens)

    private fun np(n: NpNode, sink: Sink): List<String> {
        val noun = n.noun
        val g = noun.gender
        val plural = n.plural
        fun pick(m: String, f: String, pl: String) = if (plural) pl else if (g == Gender.M) m else f

        val before = n.adjs.filter { it.before }
        val after = n.adjs.filter { !it.before }
        val fr = ArrayList<String>()
        when (n.det) {
            Det.THE -> { sink.rule(Rule.ARTICLE); sink.alt("le", "la", "les", "un", "une"); fr += pick("le", "la", "les") }
            Det.A -> {
                sink.rule(Rule.ARTICLE); sink.alt("un", "une", "des", "le", "la")
                if (plural) {
                    sink.rule(Rule.SOME_DES)
                    // "de bons amis", not "des bons amis".
                    if (before.isNotEmpty()) { sink.rule(Rule.DE_ADJ); fr += "de" } else fr += "des"
                } else fr += pick("un", "une", "des")
            }
            Det.MY -> { sink.rule(Rule.POSSESSIVE); sink.alt("mon", "ma", "mes", "ton", "ta"); fr += pick("mon", "ma", "mes") }
            Det.YOUR -> {
                sink.rule(Rule.POSSESSIVE)
                if (n.yourTu) { sink.alt("ton", "ta", "tes", "mon", "ma"); fr += pick("ton", "ta", "tes") }
                else { sink.alt("votre", "vos", "notre", "nos"); fr += pick("votre", "votre", "vos") }
            }
            Det.OUR -> { sink.rule(Rule.POSSESSIVE); sink.alt("notre", "nos", "votre", "vos"); fr += pick("notre", "notre", "nos") }
            Det.THEIR -> { sink.rule(Rule.POSSESSIVE); sink.alt("leur", "leurs", "notre"); fr += pick("leur", "leur", "leurs") }
            Det.THIS -> {
                sink.rule(Rule.DEMONSTRATIVE); sink.alt("ce", "cette", "ces")
                fr += if (plural) "ces" else if (g == Gender.M) "ce" else "cette"
            }
        }
        if (plural) sink.rule(Rule.PLURAL)
        if (n.adjs.isNotEmpty()) { sink.rule(Rule.ADJ_AGREEMENT); sink.rule(Rule.ADJ_PLACEMENT) }
        val nounFr = if (plural) noun.frPlural else noun.fr[0]
        sink.alt(if (plural) noun.fr[0] else noun.frPlural)
        val startsVowel = Spelling.frVowelStart(nounFr)
        for (a in before) {
            val form = a.fr(g, plural, startsVowel)
            if (form != a.fr(g, plural, false)) sink.rule(Rule.ADJ_BEL)
            fr += form.split(' ')
            sink.alt(a.allFrForms)
        }
        fr += nounFr.split(' ')
        for ((i, a) in after.withIndex()) {
            if (i == 1) { fr += "et"; sink.rule(Rule.ADJ_AND) }
            fr += a.fr(g, plural).split(' ')
            sink.alt(a.allFrForms)
        }
        n.relative?.let { fr += relative(it, n, sink) }
        n.modifier?.let { m ->
            sink.rule(Rule.PREP_NP)
            fr += if (m.prep == ModPrep.WITH) "avec" else "dans"
            fr += np(m.np, sink)
        }
        return fr
    }

    private fun relative(r: RelNode, head: NpNode, sink: Sink): List<String> {
        sink.rule(Rule.RELATIVE)
        val s = Subj(emptyList(), if (head.plural) 5 else 2, head.gender, head.plural)
        val rest = r.obj?.let { np(it, sink) }.orEmpty()
        return listOf("qui") + predicate(s, r.verb, Tense.PRESENT, false, rest, sink)
    }

    private fun subject(s: SubjNode, sink: Sink): Subj = when (s) {
        is SubjNp -> Subj(np(s.np, sink), if (s.np.plural) 5 else 2, s.np.gender, s.np.plural)
        is SubjPron -> {
            sink.rule(Rule.PRONOUNS)
            sink.alt("je", "tu", "il", "elle", "nous", "vous", "ils", "elles")
            when (s.pron) {
                Pron.I -> Subj(listOf("je"), 0, Gender.M, false)
                Pron.YOU_TU -> Subj(listOf("tu"), 1, Gender.M, false)
                Pron.YOU_VOUS -> Subj(listOf("vous"), 4, Gender.M, true)
                Pron.HE -> Subj(listOf("il"), 2, Gender.M, false)
                Pron.SHE -> Subj(listOf("elle"), 2, Gender.F, false)
                Pron.WE -> Subj(listOf("nous"), 3, Gender.M, true)
                Pron.THEY_M -> Subj(listOf("ils"), 5, Gender.M, true)
                Pron.THEY_F -> Subj(listOf("elles"), 5, Gender.F, true)
            }
        }
    }

    private fun agreeParticiple(v: Verb, s: Subj): String {
        if (v.aux != Aux.ETRE) return v.participle
        var p = v.participle
        if (s.gender == Gender.F) p += "e"
        if (s.plural) p += "s"
        return p
    }

    private fun predicate(s: Subj, v: Verb, tense: Tense, neg: Boolean, rest: List<String>, sink: Sink): List<String> {
        val fr = ArrayList<String>()
        val p = s.person
        sink.alt(v.present + v.fr[0] + v.participle)
        if (neg) sink.rule(Rule.NEGATION)
        when {
            v.modal -> {
                sink.rule(Rule.MODAL)
                if (neg) fr += listOf("ne", v.present[p], "pas") else fr += v.present[p]
            }
            tense == Tense.PRESENT -> {
                sink.rule(Rule.PRESENT)
                if (neg) fr += listOf("ne", v.present[p], "pas") else fr += v.present[p]
            }
            tense == Tense.PAST -> {
                sink.rule(if (v.aux == Aux.ETRE) Rule.PASSE_COMPOSE_ETRE else Rule.PASSE_COMPOSE)
                val aux = if (v.aux == Aux.ETRE) etrePres[p] else avoirPres[p]
                val ptcp = agreeParticiple(v, s)
                sink.alt(if (v.aux == Aux.ETRE) etrePres else avoirPres)
                if (neg) fr += listOf("ne", aux, "pas", ptcp) else fr += listOf(aux, ptcp)
            }
            tense == Tense.FUTURE -> {
                sink.rule(Rule.FUTURE)
                if (neg) fr += listOf("ne", allerPres[p], "pas", v.fr[0]) else fr += listOf(allerPres[p], v.fr[0])
            }
        }
        return fr + rest
    }

    private fun copula(s: Subj, tense: Tense, neg: Boolean, rest: List<String>, sink: Sink): List<String> {
        sink.rule(Rule.COPULA)
        if (neg) sink.rule(Rule.NEGATION)
        if (tense == Tense.FUTURE) sink.rule(Rule.FUTURE)
        sink.alt(etrePres + etreImparfait)
        val p = s.person
        val fr = ArrayList<String>()
        when (tense) {
            Tense.PRESENT, Tense.PAST -> {
                val form = if (tense == Tense.PRESENT) etrePres[p] else etreImparfait[p]
                if (neg) fr += listOf("ne", form, "pas") else fr += form
            }
            Tense.FUTURE -> if (neg) fr += listOf("ne", allerPres[p], "pas", "être") else fr += listOf(allerPres[p], "être")
        }
        return fr + rest
    }

    private fun complements(p: VerbPred, sink: Sink): List<String> {
        val out = ArrayList<String>()
        p.obj?.let { out += np(it, sink) }
        p.loc?.let { loc ->
            sink.rule(Rule.PLACE_PREP)
            out += p.verb.locFr!!
            out += np(loc, sink)
        }
        p.adv?.let { a -> sink.rule(Rule.ADVERB); out += a.fr[0].split(' ') }
        return out
    }

    private fun clause(c: ClauseNode, sink: Sink): List<String> {
        val s = subject(c.subj, sink)
        val body = when (val p = c.pred) {
            is CopAdj -> {
                sink.rule(Rule.ADJ_AGREEMENT)
                sink.alt(p.adj.allFrForms)
                copula(s, c.tense, c.neg, p.adj.fr(s.gender, s.plural).split(' '), sink)
            }
            is CopLoc -> {
                sink.rule(Rule.PLACE_PREP)
                copula(s, c.tense, c.neg, listOf("dans") + np(p.place, sink), sink)
            }
            is VerbPred -> predicate(s, p.verb, c.tense, c.neg, complements(p, sink), sink)
            is ModalPred -> {
                sink.alt(p.inner.verb.fr[0])
                predicate(s, p.modal, Tense.PRESENT, c.neg, listOf(p.inner.verb.fr[0]) + complements(p.inner, sink), sink)
            }
            is InfPred -> {
                sink.rule(Rule.INFINITIVE)
                sink.alt(p.inner.verb.fr[0])
                val rest = ArrayList<String>()
                if (!p.outer.infFr.isNullOrEmpty()) rest += p.outer.infFr
                rest += p.inner.verb.fr[0]
                rest += complements(p.inner, sink)
                predicate(s, p.outer, c.tense, c.neg, rest, sink)
            }
        }
        return s.tokens + body
    }

    private fun sentence(n: SentenceNode, sink: Sink): List<String> {
        val fr = ArrayList<String>()
        val conditional = n.joiners.firstOrNull() == Conn.IF
        if (n.joiners.isNotEmpty()) sink.rule(Rule.CONNECTORS)
        if (conditional) fr += "si"
        for ((i, c) in n.clauses.withIndex()) {
            if (i > 0 && !conditional) fr += n.joiners[i - 1].fr
            fr += clause(c, sink)
        }
        return fr
    }
}
