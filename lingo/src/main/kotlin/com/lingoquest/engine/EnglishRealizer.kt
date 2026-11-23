package com.lingoquest.engine

internal object EnglishRealizer : Realizer {
    override val lang = Lang.EN

    private class Subj(val tokens: List<T>, val firstSg: Boolean, val thirdSg: Boolean)

    override fun realize(node: Node, sink: Sink): List<T> {
        val raw = when (node) {
            is NpNode -> np(node, sink)
            is SentenceNode -> sentence(node, sink)
        }
        val fixed = Spelling.english(raw.map { it.s })
        if (fixed != raw.map { it.s }) sink.rule(Rule.ELISION)
        return raw.mapIndexed { i, t -> T(fixed[i], t.hint) }
    }

    private fun np(n: NpNode, sink: Sink): List<T> {
        val en = ArrayList<T>()
        en += when (n.det) {
            Det.THE -> T("the")
            Det.A -> if (n.plural) T("some") else T("a")
            Det.MY -> T("my")
            Det.YOUR -> T("your", if (n.yourTu) "tu" else "vous")
            Det.OUR -> T("our")
            Det.THEIR -> T("their")
            Det.THIS -> if (n.plural) T("these").also { sink.alt("those") } else T("this").also { sink.alt("that") }
        }
        val names = n.adjs.map { it.en[0] }
        val bothAfter = n.adjs.size == 2 && n.adjs.none { it.before }
        if (bothAfter) {
            en += T(names[0]); en += T("and"); en += T(names[1])
        } else {
            names.forEach { en += T(it) }
        }
        en += T(if (n.plural) n.noun.enPlural else n.noun.en[0])
        sink.alt(if (n.plural) n.noun.en[0] else n.noun.enPlural)
        n.relative?.let { en += relative(it, n, sink) }
        n.modifier?.let { m ->
            en += T(if (m.prep == ModPrep.WITH) "with" else "in")
            en += np(m.np, sink)
        }
        return en
    }

    private fun relative(r: RelNode, head: NpNode, sink: Sink): List<T> {
        val s = Subj(emptyList(), false, !head.plural)
        val rest = r.obj?.let { np(it, sink) }.orEmpty()
        return listOf(T("that")) + predicate(s, r.verb, Tense.PRESENT, false, rest, sink)
    }

    private fun subject(s: SubjNode, sink: Sink): Subj = when (s) {
        is SubjNp -> Subj(np(s.np, sink), false, !s.np.plural)
        is SubjPron -> {
            sink.alt("I", "you", "he", "she", "we", "they")
            when (s.pron) {
                Pron.I -> Subj(listOf(T("I")), true, false)
                Pron.YOU_TU -> Subj(listOf(T("you", "tu")), false, false)
                Pron.YOU_VOUS -> Subj(listOf(T("you", "vous")), false, false)
                Pron.HE -> Subj(listOf(T("he")), false, true)
                Pron.SHE -> Subj(listOf(T("she")), false, true)
                Pron.WE -> Subj(listOf(T("we")), false, false)
                Pron.THEY_M -> Subj(listOf(T("they")), false, false)
                Pron.THEY_F -> Subj(listOf(T("they", "f.")), false, false)
            }
        }
    }

    private fun be(s: Subj, tense: Tense): String = when {
        tense == Tense.PAST -> if (s.firstSg || s.thirdSg) "was" else "were"
        s.firstSg -> "am"
        s.thirdSg -> "is"
        else -> "are"
    }

    /** Verb group + [rest], without the subject. */
    private fun predicate(s: Subj, v: Verb, tense: Tense, neg: Boolean, rest: List<T>, sink: Sink): List<T> {
        val en = ArrayList<T>()
        val base = v.en[0]
        sink.alt(listOf(base, v.en3, v.enPast).filter { it != "-" })
        when {
            v.modal -> {
                if (neg) {
                    if (base == "can") en += T("cannot") else { en += T(base); en += T("not") }
                } else en += T(base)
            }
            tense == Tense.PRESENT -> {
                if (neg) en += listOf(T(if (s.thirdSg) "does" else "do"), T("not"), T(base))
                else en += T(if (s.thirdSg) v.en3 else base)
            }
            tense == Tense.PAST -> {
                if (neg) en += listOf(T("did"), T("not"), T(base)) else en += T(v.enPast)
            }
            tense == Tense.FUTURE -> {
                en += T(be(s, Tense.PRESENT))
                if (neg) en += T("not")
                en += listOf(T("going"), T("to"), T(base))
            }
        }
        return en + rest
    }

    private fun copula(s: Subj, tense: Tense, neg: Boolean, rest: List<T>, sink: Sink): List<T> {
        sink.alt("is", "are", "am", "was", "were", "not")
        val en = ArrayList<T>()
        if (tense == Tense.FUTURE) {
            en += T(be(s, Tense.PRESENT))
            if (neg) en += T("not")
            en += listOf(T("going"), T("to"), T("be"))
        } else {
            en += T(be(s, tense))
            if (neg) en += T("not")
        }
        return en + rest
    }

    /** Complements of a plain verb: object or place, then adverb. */
    private fun complements(p: VerbPred, sink: Sink): List<T> {
        val out = ArrayList<T>()
        p.obj?.let { out += np(it, sink) }
        p.loc?.let { loc ->
            if (!p.verb.locEn.isNullOrEmpty()) out += T(p.verb.locEn)
            out += np(loc, sink)
        }
        p.adv?.let { a -> a.en[0].split(' ').forEach { out += T(it) } }
        return out
    }

    private fun clause(c: ClauseNode, sink: Sink): List<T> {
        val s = subject(c.subj, sink)
        val body = when (val p = c.pred) {
            is CopAdj -> copula(s, c.tense, c.neg, p.adj.en[0].split(' ').map { T(it) }, sink)
            is CopLoc -> copula(s, c.tense, c.neg, listOf(T("in")) + np(p.place, sink), sink)
            is VerbPred -> predicate(s, p.verb, c.tense, c.neg, complements(p, sink), sink)
            is ModalPred -> {
                sink.alt(listOf(p.inner.verb.en[0], p.inner.verb.en3))
                val rest = listOf(T(p.inner.verb.en[0])) + complements(p.inner, sink)
                predicate(s, p.modal, Tense.PRESENT, c.neg, rest, sink)
            }
            is InfPred -> {
                sink.alt(listOf(p.inner.verb.en[0], p.inner.verb.en3))
                val rest = ArrayList<T>()
                if (!p.outer.infEn.isNullOrEmpty()) rest += T(p.outer.infEn)
                rest += T(p.inner.verb.en[0])
                rest += complements(p.inner, sink)
                predicate(s, p.outer, c.tense, c.neg, rest, sink)
            }
        }
        return s.tokens + body
    }

    private fun sentence(n: SentenceNode, sink: Sink): List<T> {
        val en = ArrayList<T>()
        val conditional = n.joiners.firstOrNull() == Conn.IF
        if (conditional) en += T("if")
        for ((i, c) in n.clauses.withIndex()) {
            if (i > 0 && !conditional) n.joiners[i - 1].en.forEach { en += T(it) }
            en += clause(c, sink)
        }
        return en
    }
}
