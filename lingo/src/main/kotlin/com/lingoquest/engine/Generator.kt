package com.lingoquest.engine

import kotlin.random.Random

/**
 * Builds translation challenges of growing length from the words the player has unlocked.
 * Sentences are produced in English and French at the same time from one small grammar, so the
 * two sides always agree (articles, genders, plurals, verb conjugations, elision…).
 */
class Generator(private val rnd: Random = Random.Default) {

    // ───────── Internal building blocks ─────────

    private class Np(val en: List<T>, val fr: List<String>, val gender: Gender, val plural: Boolean, val noun: Noun)

    private class Subject(
        val en: List<T>, val fr: List<String>, val person: Int,
        val gender: Gender, val plural: Boolean,
        val knownGender: Boolean, val firstSg: Boolean, val thirdSg: Boolean,
    )

    private class Piece(val en: List<T>, val fr: List<String>) {
        companion object { val EMPTY = Piece(emptyList(), emptyList()) }
    }

    private class Clause(val en: List<T>, val fr: List<String>)

    private enum class Pattern { COPULA_ADJ, COPULA_LOC, TRANSITIVE, INTRANSITIVE, MODAL, INFINITIVE }

    /** Per-challenge state. */
    private inner class Ctx(
        val unlocked: List<Entry>, val missed: Set<String>, val spec: Spec, val step: Int,
    ) {
        val nouns = unlocked.filterIsInstance<Noun>()
        val adjs = unlocked.filterIsInstance<Adj>()
        val verbs = unlocked.filterIsInstance<Verb>()
        val advs = unlocked.filterIsInstance<Adverb>()
        val recent = unlocked.takeLast(12).map { it.id }.toSet()
        val ids = ArrayList<String>()
        val altEn = ArrayList<String>()
        val altFr = ArrayList<String>()
        val rules = ArrayList<Rule>()

        fun rule(r: Rule) { if (r !in rules) rules += r }

        fun <E : Entry> pick(list: List<E>): E? {
            if (list.isEmpty()) return null
            val weights = list.map { 1.0 + (if (it.id in recent) 2.0 else 0.0) + (if (it.id in missed) 2.0 else 0.0) }
            var r = rnd.nextDouble() * weights.sum()
            for ((i, w) in weights.withIndex()) {
                r -= w
                if (r <= 0) return list[i].also { ids += it.id }
            }
            return list.last().also { ids += it.id }
        }
    }

    // ───────── Public entry point ─────────

    fun generate(
        unlocked: List<Entry>,
        stage: Stage,
        cefr: Cefr,
        step: Int,
        direction: Direction,
        missed: Set<String> = emptySet(),
    ): Challenge {
        require(unlocked.isNotEmpty()) { "No unlocked words" }
        var current = stage
        while (current != Stage.WORD) {
            repeat(12) {
                val ctx = Ctx(unlocked, missed, Spec.of(current, cefr, step), step)
                tryStage(ctx, current, direction)?.let { return it }
            }
            // Not enough words unlocked for this stage yet: fall back to a simpler one.
            current = Stage.entries[current.ordinal - 1]
        }
        return wordChallenge(Ctx(unlocked, missed, Spec.of(Stage.WORD, cefr, step), step), direction)
    }

    private fun tryStage(ctx: Ctx, stage: Stage, direction: Direction): Challenge? = when (stage) {
        Stage.WORD -> wordChallenge(ctx, direction)
        Stage.NOUN_PHRASE, Stage.NOUN_PHRASE_ADJ, Stage.NOUN_PHRASE_COMPLEX -> {
            val np = nounPhrase(ctx, null, ctx.spec.dets, ctx.spec.maxAdjectives, ctx.spec.modifiers, false)
            np?.let { build(ctx, Piece(it.en, it.fr), direction, 2) }
        }
        Stage.SENTENCE_SIMPLE, Stage.SENTENCE_COMPLEX ->
            sentence(ctx)?.let { build(ctx, it, direction, if (ctx.spec.clauses > 1) 4 else 3) }
    }

    // ───────── Words ─────────

    private fun wordChallenge(ctx: Ctx, direction: Direction): Challenge {
        val entry = ctx.pick(ctx.unlocked)!!
        return Challenge(
            stage = Stage.WORD, direction = direction,
            prompt = entry.display(direction.from),
            answer = entry.display(direction.to),
            accepted = entry.forms(direction.to),
            bank = emptyList(), entryIds = listOf(entry.id), step = ctx.step,
            notes = listOfNotNull(Notes.forEntry(entry, direction.to)),
        )
    }

    // ───────── Final assembly ─────────

    private fun build(ctx: Ctx, piece: Piece, direction: Direction, distractors: Int): Challenge? {
        val frTokens = Spelling.french(piece.fr)
        val enTokens = Spelling.english(piece.en.map { it.s })
        if (frTokens.size != piece.fr.size || frTokens != piece.fr || enTokens != piece.en.map { it.s }) ctx.rule(Rule.ELISION)
        // Re-attach the English hints (the a/an pass keeps the token count).
        val enShown = piece.en.mapIndexed { i, t -> T(enTokens[i], t.hint) }
        if (frTokens.size > 26 || enTokens.size > 26) return null
        val sentence = ctx.spec.stage.isSentence
        val promptText = if (direction.from == Lang.FR) {
            Spelling.sentence(frTokens, sentence)
        } else {
            Spelling.sentence(enShown.map { if (it.hint != null) "${it.s} (${it.hint})" else it.s }, sentence)
        }
        val answerTokens = if (direction.to == Lang.FR) frTokens else enTokens
        val answerText = Spelling.sentence(answerTokens, sentence)
        val altPool = if (direction.to == Lang.FR) ctx.altFr else ctx.altEn
        return Challenge(
            stage = ctx.spec.stage, direction = direction,
            prompt = promptText, answer = answerText,
            accepted = listOf(Spelling.join(answerTokens)),
            bank = tiles(ctx, answerTokens, altPool, direction.to, distractors),
            entryIds = ctx.ids.distinct(), step = ctx.step,
            rules = ctx.rules.toList(), notes = notesFor(ctx, direction.to),
        )
    }

    private fun notesFor(ctx: Ctx, lang: Lang): List<String> =
        ctx.ids.distinct().mapNotNull { id -> ctx.unlocked.firstOrNull { it.id == id } }
            .sortedBy { it.pos.ordinal }.mapNotNull { Notes.forEntry(it, lang) }.take(6)

    private fun tiles(ctx: Ctx, answer: List<String>, confusers: List<String>, lang: Lang, count: Int): List<String> {
        val used = answer.map { it.lowercase() }.toHashSet()
        val extra = ArrayList<String>()
        val wantConfusers = (count + 1) / 2
        for (c in confusers.shuffled(rnd)) {
            val low = c.lowercase()
            if (extra.size >= wantConfusers) break
            if (low !in used && extra.none { it.lowercase() == low } && ' ' !in c) extra += c
        }
        val fillers = ctx.unlocked.flatMap { it.forms(lang) }.filter { ' ' !in it }.shuffled(rnd)
        for (f in fillers) {
            if (extra.size >= count) break
            val low = f.lowercase()
            if (low !in used && extra.none { it.lowercase() == low }) extra += f
        }
        return (answer + extra).shuffled(rnd)
    }

    // ───────── Noun phrases ─────────

    private val adjLimitedToPeople = setOf(
        "tired", "happy", "sad", "sick", "proud", "brave", "lucky", "polite", "clever", "honest", "patient", "kind",
        "curious", "generous", "wise", "lazy", "foolish", "careful", "ambitious", "stubborn", "modest", "ruthless",
        "wealthy", "rich", "weak", "young",
    )
    private val adjLimitedToThings = setOf(
        "empty", "full", "heavy", "expensive", "cheap", "fragile", "clean", "dirty", "ancient",
    )
    private val adjLimitedToMovers = setOf("fast", "slow")
    private val adjLimitedToFoodAndPlaces = setOf("hot", "cold")
    private val colors = setOf("red", "blue", "green", "yellow", "black", "white")
    private val adjOkForAbstract = setOf(
        "big", "small", "good", "bad", "new", "old", "important", "different", "interesting", "serious",
        "dangerous", "possible", "necessary", "easy", "difficult", "ordinary", "remarkable", "mysterious",
        "first", "last", "next", "other", "same", "long", "huge", "tiny", "fragile", "reliable", "ancient",
    )

    private fun adjFits(adj: Adj, tags: String): Boolean {
        val key = adj.en[0]
        val living = 'P' in tags || 'A' in tags
        val abstract = tags.all { it in "XU" }
        return when {
            key in adjLimitedToPeople -> living
            key in adjLimitedToMovers -> living || 'V' in tags
            key in adjLimitedToFoodAndPlaces -> tags.any { it in "FDLH" }
            key in adjLimitedToThings -> !living && !abstract
            key in colors -> 'P' !in tags && !abstract
            abstract -> key in adjOkForAbstract
            else -> true
        }
    }

    private val tuVous = listOf("tu", "vous")

    private fun determiner(ctx: Ctx, det: Det, noun: Noun, plural: Boolean): Piece {
        val g = noun.gender
        when (det) {
            Det.THE, Det.A -> ctx.rule(Rule.ARTICLE)
            Det.THIS -> ctx.rule(Rule.DEMONSTRATIVE)
            else -> ctx.rule(Rule.POSSESSIVE)
        }
        if (det == Det.A && plural) ctx.rule(Rule.SOME_DES)
        fun fr(m: String, f: String, pl: String) = if (plural) pl else if (g == Gender.M) m else f
        return when (det) {
            Det.THE -> {
                ctx.altFr += listOf("le", "la", "les", "un", "une")
                Piece(listOf(T("the")), listOf(fr("le", "la", "les")))
            }
            Det.A -> {
                ctx.altFr += listOf("un", "une", "des", "le", "la")
                if (plural) Piece(listOf(T("some")), listOf("des")) else Piece(listOf(T("a")), listOf(fr("un", "une", "des")))
            }
            Det.MY -> Piece(listOf(T("my")), listOf(fr("mon", "ma", "mes"))).also { ctx.altFr += listOf("mon", "ma", "mes", "ton", "ta") }
            Det.YOUR -> if (rnd.nextBoolean()) {
                Piece(listOf(T("your", "tu")), listOf(fr("ton", "ta", "tes"))).also { ctx.altFr += listOf("ton", "ta", "tes", "mon", "ma") }
            } else {
                Piece(listOf(T("your", "vous")), listOf(fr("votre", "votre", "vos"))).also { ctx.altFr += listOf("votre", "vos", "notre", "nos") }
            }
            Det.OUR -> Piece(listOf(T("our")), listOf(fr("notre", "notre", "nos"))).also { ctx.altFr += listOf("notre", "nos", "votre", "vos") }
            Det.THEIR -> Piece(listOf(T("their")), listOf(fr("leur", "leur", "leurs"))).also { ctx.altFr += listOf("leur", "leurs", "notre") }
            Det.THIS -> if (plural) Piece(listOf(T("these")), listOf("ces")).also { ctx.altEn += "those" }
            else Piece(listOf(T("this")), listOf(if (g == Gender.M) "ce" else "cette")).also { ctx.altFr += listOf("ce", "cette", "ces"); ctx.altEn += "that" }
        }
    }

    /**
     * A noun phrase such as "the big dog", "my red houses" or "the woman with a book".
     * [tags] restricts the head noun (any listed tag matches); null accepts any noun.
     */
    private fun nounPhrase(
        ctx: Ctx, tags: String?, dets: List<Det>, maxAdj: Int, modifiers: Boolean, allowRelative: Boolean,
        forcePlural: Boolean? = null,
    ): Np? {
        val candidates = ctx.nouns.filter { tags == null || it.hasTag(tags) }
        val noun = ctx.pick(candidates) ?: return null
        val plural = if (noun.uncountable) false else forcePlural ?: (rnd.nextDouble() < 0.35)
        val adjective = ArrayList<Adj>()
        if (maxAdj > 0) {
            val fitting = ctx.adjs.filter { adjFits(it, noun.tags) }
            val first = ctx.pick(fitting)
            if (first != null && (maxAdj == 1 || rnd.nextDouble() < 0.5 || fitting.size < 2)) {
                adjective += first
            } else if (first != null) {
                val second = ctx.pick(fitting.filter { it !== first && !(it.before && first.before) })
                if (second == null) adjective += first else adjective += listOf(first, second)
            }
        }
        var det = weightedDet(dets.filter { !(noun.uncountable && it == Det.A) })
        // "another" replaces "a other": use "the" instead.
        if (det == Det.A && adjective.any { it.en[0] == "other" }) det = Det.THE
        val detPiece = determiner(ctx, det, noun, plural)

        if (plural) ctx.rule(Rule.PLURAL)
        if (adjective.isNotEmpty()) { ctx.rule(Rule.ADJ_AGREEMENT); ctx.rule(Rule.ADJ_PLACEMENT) }
        // English order: a "before" adjective (size, age, beauty) goes first.
        val ordered = adjective.sortedByDescending { it.before }
        val frBefore = ordered.filter { it.before }
        val frAfter = ordered.filter { !it.before }

        val nounEn = if (plural) noun.enPlural else noun.en[0]
        val nounFr = if (plural) noun.frPlural else noun.fr[0]
        ctx.altEn += if (plural) noun.en[0] else noun.enPlural
        ctx.altFr += if (plural) noun.fr[0] else noun.frPlural

        val en = ArrayList<T>(detPiece.en)
        val fr = ArrayList<String>(detPiece.fr)
        // "de bons amis", not "des bons amis".
        if (det == Det.A && plural && frBefore.isNotEmpty()) { fr[0] = "de"; ctx.rule(Rule.DE_ADJ) }
        val enAdj = ordered.map { it.en[0] }
        if (enAdj.size == 2 && frAfter.size == 2) {
            en += T(enAdj[0]); en += T("and"); en += T(enAdj[1])
            ctx.rule(Rule.ADJ_AND)
        } else {
            enAdj.forEach { en += T(it) }
        }
        en += T(nounEn)

        val nounStartsVowel = Spelling.frVowelStart(nounFr)
        for (a in frBefore) {
            val form = a.fr(noun.gender, plural, nounStartsVowel)
            if (form != a.fr(noun.gender, plural, false)) ctx.rule(Rule.ADJ_BEL)
            form.split(' ').forEach { fr += it }
            ctx.altFr += a.allFrForms
        }
        fr += nounFr.split(' ')
        for ((i, a) in frAfter.withIndex()) {
            if (i == 1) fr += "et"
            a.fr(noun.gender, plural).split(' ').forEach { fr += it }
            ctx.altFr += a.allFrForms
        }

        var np = Np(en, fr, noun.gender, plural, noun)
        if (allowRelative) np = withRelative(ctx, np) ?: np
        if (modifiers && rnd.nextDouble() < 0.6) np = withModifier(ctx, np) ?: np
        return np
    }

    private fun weightedDet(dets: List<Det>): Det {
        // "the" and "a" are the most common; the rest share the remainder.
        val weights = dets.map { if (it == Det.THE) 4.0 else if (it == Det.A) 3.0 else 1.0 }
        var r = rnd.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0) return dets[i]
        }
        return dets.last()
    }

    private fun withModifier(ctx: Ctx, head: Np): Np? {
        val tags = head.noun.tags
        val options = ArrayList<Triple<String, String, String>>() // en prep, fr prep, complement tags
        if ('P' in tags) options += Triple("with", "avec", "AOBV")
        if ('P' in tags || 'A' in tags) options += Triple("in", "dans", "L")
        if (options.isEmpty()) return null
        val (enPrep, frPrep, compTags) = options[rnd.nextInt(options.size)]
        val simple = listOf(Det.THE, Det.A)
        val comp = nounPhrase(ctx, compTags, simple, 0, false, false) ?: return null
        ctx.rule(Rule.PREP_NP)
        return Np(
            head.en + T(enPrep) + comp.en,
            head.fr + frPrep + comp.fr,
            head.gender, head.plural, head.noun,
        )
    }

    /** "the man that eats an apple" / "l'homme qui mange une pomme" (present tense only). */
    private fun withRelative(ctx: Ctx, head: Np): Np? {
        if (!head.noun.hasTag("PA")) return null
        val verbs = ctx.verbs.filter { it.isPlain && it.subjTags.any { c -> c in head.noun.tags } }
        val v = ctx.pick(verbs) ?: return null
        val subj = Subject(
            emptyList(), emptyList(), if (head.plural) 5 else 2, head.gender, head.plural,
            knownGender = true, firstSg = false, thirdSg = !head.plural,
        )
        val rest = if (v.transitive) {
            val obj = nounPhrase(ctx, v.objTags, listOf(Det.THE, Det.A), 0, false, false) ?: return null
            Piece(obj.en, obj.fr)
        } else Piece.EMPTY
        val pred = predicate(ctx, subj, v, Tense.PRESENT, false, rest)
        ctx.rule(Rule.RELATIVE)
        return Np(head.en + T("that") + pred.en, head.fr + "qui" + pred.fr, head.gender, head.plural, head.noun)
    }

    // ───────── Sentences ─────────

    private val pronouns = listOf(
        Subject(listOf(T("I")), listOf("je"), 0, Gender.M, false, false, true, false),
        Subject(listOf(T("you", "tu")), listOf("tu"), 1, Gender.M, false, false, false, false),
        Subject(listOf(T("you", "vous")), listOf("vous"), 4, Gender.M, true, false, false, false),
        Subject(listOf(T("he")), listOf("il"), 2, Gender.M, false, true, false, true),
        Subject(listOf(T("she")), listOf("elle"), 2, Gender.F, false, true, false, true),
        Subject(listOf(T("we")), listOf("nous"), 3, Gender.M, true, false, false, false),
        Subject(listOf(T("they")), listOf("ils"), 5, Gender.M, true, true, false, false),
        Subject(listOf(T("they", "f.")), listOf("elles"), 5, Gender.F, true, true, false, false),
    )

    private val avoirPres = listOf("ai", "as", "a", "avons", "avez", "ont")
    private val etrePres = listOf("suis", "es", "est", "sommes", "êtes", "sont")
    private val etreImparfait = listOf("étais", "étais", "était", "étions", "étiez", "étaient")
    private val allerPres = listOf("vais", "vas", "va", "allons", "allez", "vont")

    private fun enBe(s: Subject, tense: Tense): String = when {
        tense == Tense.PAST -> if (s.firstSg || s.thirdSg) "was" else "were"
        s.firstSg -> "am"
        s.thirdSg -> "is"
        else -> "are"
    }

    private fun subjectFor(ctx: Ctx, tags: String, needsGender: Boolean): Subject? {
        val pronounChoices = if (needsGender) pronouns.filter { it.knownGender } else pronouns
        val npAvailable = ctx.nouns.any { it.hasTag(tags) }
        if (npAvailable && rnd.nextDouble() < 0.5) {
            val adj = if (ctx.spec.stage == Stage.SENTENCE_COMPLEX) 1 else 0
            val np = nounPhrase(ctx, tags, ctx.spec.dets, adj.coerceAtMost(ctx.spec.maxAdjectives), false, false)
            if (np != null) {
                return Subject(
                    np.en, np.fr, if (np.plural) 5 else 2, np.gender, np.plural,
                    knownGender = true, firstSg = false, thirdSg = !np.plural,
                )
            }
        }
        val p = pronounChoices[rnd.nextInt(pronounChoices.size)]
        ctx.rule(Rule.PRONOUNS)
        ctx.altEn += pronouns.flatMap { it.en.map(T::s) }
        ctx.altFr += pronouns.flatMap { it.fr }
        return p
    }

    private fun agreeParticiple(v: Verb, s: Subject): String {
        if (v.aux != Aux.ETRE) return v.participle
        var p = v.participle
        if (s.gender == Gender.F) p += "e"
        if (s.plural) p += "s"
        return p
    }

    /** Everything after the subject: verb group + [rest]. */
    private fun predicate(ctx: Ctx, s: Subject, v: Verb, tense: Tense, neg: Boolean, rest: Piece): Piece {
        val en = ArrayList<T>()
        val fr = ArrayList<String>()
        val base = v.en[0]
        val p = s.person
        ctx.altEn += listOf(base, v.en3, v.enPast).filter { it != "-" }
        ctx.altFr += v.present + v.fr[0] + v.participle
        if (neg) ctx.rule(Rule.NEGATION)
        when {
            v.modal -> {
                ctx.rule(Rule.MODAL)
                if (neg) {
                    if (base == "can") en += T("cannot") else { en += T(base); en += T("not") }
                    fr += listOf("ne", v.present[p], "pas")
                } else {
                    en += T(base)
                    fr += v.present[p]
                }
            }
            tense == Tense.PRESENT -> {
                ctx.rule(Rule.PRESENT)
                if (neg) {
                    en += listOf(T(if (s.thirdSg) "does" else "do"), T("not"), T(base))
                    fr += listOf("ne", v.present[p], "pas")
                } else {
                    en += T(if (s.thirdSg) v.en3 else base)
                    fr += v.present[p]
                }
            }
            tense == Tense.PAST -> {
                ctx.rule(if (v.aux == Aux.ETRE) Rule.PASSE_COMPOSE_ETRE else Rule.PASSE_COMPOSE)
                val aux = if (v.aux == Aux.ETRE) etrePres[p] else avoirPres[p]
                val ptcp = agreeParticiple(v, s)
                ctx.altFr += (if (v.aux == Aux.ETRE) etrePres else avoirPres)
                if (neg) {
                    en += listOf(T("did"), T("not"), T(base))
                    fr += listOf("ne", aux, "pas", ptcp)
                } else {
                    en += T(v.enPast)
                    fr += listOf(aux, ptcp)
                }
            }
            tense == Tense.FUTURE -> {
                ctx.rule(Rule.FUTURE)
                en += T(enBe(s, Tense.PRESENT))
                if (neg) en += T("not")
                en += listOf(T("going"), T("to"), T(base))
                if (neg) fr += listOf("ne", allerPres[p], "pas", v.fr[0]) else fr += listOf(allerPres[p], v.fr[0])
            }
        }
        return Piece(en + rest.en, fr + rest.fr)
    }

    private fun copula(ctx: Ctx, s: Subject, tense: Tense, neg: Boolean, rest: Piece): Piece {
        val en = ArrayList<T>()
        val fr = ArrayList<String>()
        val p = s.person
        ctx.rule(Rule.COPULA)
        if (neg) ctx.rule(Rule.NEGATION)
        if (tense == Tense.FUTURE) ctx.rule(Rule.FUTURE)
        ctx.altEn += listOf("is", "are", "am", "was", "were", "not")
        ctx.altFr += etrePres + etreImparfait
        when (tense) {
            Tense.PRESENT, Tense.PAST -> {
                en += T(enBe(s, tense))
                if (neg) en += T("not")
                val form = if (tense == Tense.PRESENT) etrePres[p] else etreImparfait[p]
                if (neg) fr += listOf("ne", form, "pas") else fr += form
            }
            Tense.FUTURE -> {
                en += T(enBe(s, Tense.PRESENT))
                if (neg) en += T("not")
                en += listOf(T("going"), T("to"), T("be"))
                if (neg) fr += listOf("ne", allerPres[p], "pas", "être") else fr += listOf(allerPres[p], "être")
            }
        }
        return Piece(en + rest.en, fr + rest.fr)
    }

    private fun locativePhrase(ctx: Ctx, enPrep: String?, frPrep: String, tags: String = "L"): Piece? {
        val np = nounPhrase(ctx, tags, ctx.spec.dets, 0, false, false) ?: return null
        ctx.rule(Rule.PLACE_PREP)
        val en = ArrayList<T>()
        if (!enPrep.isNullOrEmpty()) en += T(enPrep)
        en += np.en
        return Piece(en, listOf(frPrep) + np.fr)
    }

    private fun chooseTense(ctx: Ctx): Tense {
        val options = buildList {
            repeat(5) { add(Tense.PRESENT) }
            if (ctx.spec.past) repeat(3) { add(Tense.PAST) }
            if (ctx.spec.future) repeat(2) { add(Tense.FUTURE) }
        }
        return options[rnd.nextInt(options.size)]
    }

    private fun clause(ctx: Ctx, tense: Tense): Clause? {
        val spec = ctx.spec
        val neg = spec.negation && rnd.nextDouble() < 0.25
        val patterns = ArrayList<Pattern>()
        val plain = ctx.verbs.filter { it.isPlain }
        if (ctx.adjs.isNotEmpty()) { patterns += Pattern.COPULA_ADJ; patterns += Pattern.COPULA_ADJ }
        if (ctx.nouns.any { it.hasTag("L") }) patterns += Pattern.COPULA_LOC
        if (ctx.verbs.any { it.transitive && !it.modal }) repeat(5) { patterns += Pattern.TRANSITIVE }
        if (plain.any { !it.transitive }) repeat(2) { patterns += Pattern.INTRANSITIVE }
        if (spec.modal && tense == Tense.PRESENT && ctx.verbs.any { it.modal } && plain.isNotEmpty()) repeat(2) { patterns += Pattern.MODAL }
        if (spec.infinitiveComplement && tense != Tense.FUTURE && ctx.verbs.any { it.infEn != null } && plain.isNotEmpty()) repeat(2) { patterns += Pattern.INFINITIVE }
        if (patterns.isEmpty()) return null

        repeat(10) {
            val mark = intArrayOf(ctx.ids.size, ctx.altEn.size, ctx.altFr.size, ctx.rules.size)
            val result = when (patterns[rnd.nextInt(patterns.size)]) {
                Pattern.COPULA_ADJ -> copulaAdj(ctx, tense, neg)
                Pattern.COPULA_LOC -> copulaLoc(ctx, tense, neg)
                Pattern.TRANSITIVE -> transitive(ctx, tense, neg)
                Pattern.INTRANSITIVE -> intransitive(ctx, tense, neg)
                Pattern.MODAL -> modal(ctx, neg)
                Pattern.INFINITIVE -> infinitive(ctx, tense, neg)
            }
            if (result != null) return result
            ctx.ids.subList(mark[0], ctx.ids.size).clear()
            ctx.altEn.subList(mark[1], ctx.altEn.size).clear()
            ctx.altFr.subList(mark[2], ctx.altFr.size).clear()
            ctx.rules.subList(mark[3], ctx.rules.size).clear()
        }
        return null
    }

    private fun complete(s: Subject, pred: Piece) = Clause(s.en + pred.en, s.fr + pred.fr)

    private fun objectFor(ctx: Ctx, v: Verb): Np? {
        val spec = ctx.spec
        return nounPhrase(
            ctx, v.objTags, spec.dets, spec.maxAdjectives,
            spec.modifiers && rnd.nextDouble() < 0.4, spec.relative && rnd.nextDouble() < 0.3,
        )
    }

    private fun adverbFor(ctx: Ctx, tense: Tense, intransitiveBare: Boolean): Piece {
        if (!ctx.spec.adverbs || rnd.nextDouble() > 0.4) return Piece.EMPTY
        val ok = ctx.advs.filter { it.fits(tense) && (!it.manner || intransitiveBare) }
        val adv = ctx.pick(ok) ?: return Piece.EMPTY
        ctx.rule(Rule.ADVERB)
        return Piece(adv.en[0].split(' ').map { T(it) }, adv.fr[0].split(' '))
    }

    private fun transitive(ctx: Ctx, tense: Tense, neg: Boolean): Clause? {
        val verbs = ctx.verbs.filter { it.transitive && !it.modal }
        val v = ctx.pick(verbs) ?: return null
        val s = subjectFor(ctx, v.subjTags, v.aux == Aux.ETRE && tense == Tense.PAST) ?: return null
        val obj = objectFor(ctx, v) ?: return null
        val adverb = adverbFor(ctx, tense, false)
        val rest = Piece(obj.en + adverb.en, obj.fr + adverb.fr)
        return complete(s, predicate(ctx, s, v, tense, neg, rest))
    }

    private fun intransitive(ctx: Ctx, tense: Tense, neg: Boolean): Clause? {
        val v = ctx.pick(ctx.verbs.filter { it.isPlain && !it.transitive }) ?: return null
        val s = subjectFor(ctx, v.subjTags, v.aux == Aux.ETRE && tense == Tense.PAST) ?: return null
        val loc = if (v.hasLoc && rnd.nextDouble() < 0.7) locativePhrase(ctx, v.locEn, v.locFr!!) else null
        val adverb = adverbFor(ctx, tense, loc == null)
        val rest = Piece((loc?.en ?: emptyList()) + adverb.en, (loc?.fr ?: emptyList()) + adverb.fr)
        return complete(s, predicate(ctx, s, v, tense, neg, rest))
    }

    private fun innerVerbPiece(ctx: Ctx, inner: Verb): Piece? {
        return if (inner.transitive) {
            val obj = objectFor(ctx, inner) ?: return null
            Piece(obj.en, obj.fr)
        } else if (inner.hasLoc && rnd.nextDouble() < 0.7) {
            locativePhrase(ctx, inner.locEn, inner.locFr!!) ?: Piece.EMPTY
        } else Piece.EMPTY
    }

    private fun modal(ctx: Ctx, neg: Boolean): Clause? {
        val modalVerb = ctx.pick(ctx.verbs.filter { it.modal }) ?: return null
        val inner = ctx.pick(ctx.verbs.filter { it.isPlain }) ?: return null
        val tags = modalVerb.subjTags.filter { it in inner.subjTags }
        if (tags.isEmpty()) return null
        val s = subjectFor(ctx, tags, false) ?: return null
        val tail = innerVerbPiece(ctx, inner) ?: return null
        val adverb = adverbFor(ctx, Tense.PRESENT, !inner.transitive && tail.en.isEmpty())
        val rest = Piece(listOf(T(inner.en[0])) + tail.en + adverb.en, listOf(inner.fr[0]) + tail.fr + adverb.fr)
        ctx.altEn += listOf(inner.en[0], inner.en3)
        ctx.altFr += inner.fr[0]
        return complete(s, predicate(ctx, s, modalVerb, Tense.PRESENT, neg, rest))
    }

    private fun infinitive(ctx: Ctx, tense: Tense, neg: Boolean): Clause? {
        val outer = ctx.pick(ctx.verbs.filter { it.infEn != null }) ?: return null
        val inner = ctx.pick(ctx.verbs.filter { it.isPlain }) ?: return null
        val tags = outer.subjTags.filter { it in inner.subjTags }
        if (tags.isEmpty()) return null
        val s = subjectFor(ctx, tags, false) ?: return null
        val tail = innerVerbPiece(ctx, inner) ?: return null
        ctx.rule(Rule.INFINITIVE)
        val enRest = ArrayList<T>()
        if (!outer.infEn.isNullOrEmpty()) enRest += T(outer.infEn)
        enRest += T(inner.en[0])
        val frRest = ArrayList<String>()
        if (!outer.infFr.isNullOrEmpty()) frRest += outer.infFr
        frRest += inner.fr[0]
        ctx.altEn += listOf(inner.en[0], inner.en3)
        ctx.altFr += inner.fr[0]
        val rest = Piece(enRest + tail.en, frRest + tail.fr)
        return complete(s, predicate(ctx, s, outer, tense, neg, rest))
    }

    private fun copulaAdj(ctx: Ctx, tense: Tense, neg: Boolean): Clause? {
        val s = subjectFor(ctx, "PA", true) ?: return null
        val adj = ctx.pick(ctx.adjs.filter { adjFits(it, "P") }) ?: return null
        val form = adj.fr(s.gender, s.plural)
        ctx.rule(Rule.ADJ_AGREEMENT)
        ctx.altFr += adj.allFrForms
        val rest = Piece(adj.en[0].split(' ').map { T(it) }, form.split(' '))
        return complete(s, copula(ctx, s, tense, neg, rest))
    }

    private fun copulaLoc(ctx: Ctx, tense: Tense, neg: Boolean): Clause? {
        val s = subjectFor(ctx, "PAOV", false) ?: return null
        val loc = locativePhrase(ctx, "in", "dans") ?: return null
        return complete(s, copula(ctx, s, tense, neg, loc))
    }

    private fun sentence(ctx: Ctx): Piece? {
        val spec = ctx.spec
        val count = if (spec.clauses > 1 && rnd.nextDouble() < 0.3) spec.clauses - 1 else spec.clauses
        // Two clauses may use any connector; three stay with "and"/"but" so the sentence stays readable.
        val joiners = when (count) {
            1 -> emptyList()
            2 -> listOf(spec.connectors[rnd.nextInt(spec.connectors.size)])
            else -> {
                val simple = spec.connectors.filter { it == Conn.AND || it == Conn.BUT }
                List(count - 1) { simple[rnd.nextInt(simple.size)] }
            }
        }
        // "if" clauses stay in the present; the other clauses share one tense.
        val tense = if (joiners.firstOrNull() == Conn.IF) Tense.PRESENT else chooseTense(ctx)
        if (joiners.isNotEmpty()) ctx.rule(Rule.CONNECTORS)
        val clauses = ArrayList<Clause>()
        repeat(count) { clauses += clause(ctx, tense) ?: return null }

        val en = ArrayList<T>()
        val fr = ArrayList<String>()
        val conditional = joiners.firstOrNull() == Conn.IF
        if (conditional) { en += T("if"); fr += "si" }
        for ((i, c) in clauses.withIndex()) {
            if (i > 0 && !conditional) {
                joiners[i - 1].en.forEach { en += T(it) }
                fr += joiners[i - 1].fr
            }
            en += c.en
            fr += c.fr
        }
        return Piece(en, fr)
    }
}
