package com.lingoquest.engine

internal enum class RuCase { NOM, ACC, PREP, INSTR }

/** Russian morphology shared by the realizer and the word notes. Genders: m, f, n and p (plural). */
internal object RuMorph {
    fun gender(fx: Fx): Char = fx["g"]?.firstOrNull() ?: 'm'
    fun isAnimate(fx: Fx) = fx.has("an")

    private fun oblPl(pl: String, hard: String, soft: String): String {
        val stem = pl.dropLast(1)
        return when (pl.last()) {
            'ы', 'а' -> stem + hard
            'и' -> if ((stem.lastOrNull() ?: ' ') in "гкхжчшщ") stem + hard else stem + soft
            else -> stem + soft
        }
    }

    private fun prepSg(text: String, g: Char, base: String): String = when {
        text.endsWith("ия") || text.endsWith("ие") -> text.dropLast(1) + "и"
        g == 'f' && text.endsWith("ь") -> text.dropLast(1) + "и"
        text.endsWith("а") || text.endsWith("я") || text.endsWith("о") || text.endsWith("е") -> text.dropLast(1) + "е"
        text.endsWith("ь") || text.endsWith("й") -> text.dropLast(1) + "е"
        else -> base + "е"
    }

    private fun instrSg(text: String, g: Char, base: String): String = when {
        text.endsWith("а") -> text.dropLast(1) + "ой"
        text.endsWith("я") -> text.dropLast(1) + "ей"
        g == 'f' && text.endsWith("ь") -> text.dropLast(1) + "ью"
        text.endsWith("ь") || text.endsWith("й") -> text.dropLast(1) + "ем"
        text.endsWith("о") -> text.dropLast(1) + "ом"
        text.endsWith("е") -> text + "м"
        base.last() in "жчшщц" -> base + "ем"
        else -> base + "ом"
    }

    private fun genitiveMasc(text: String, base: String): String = when {
        text.endsWith("ь") || text.endsWith("й") -> text.dropLast(1) + "я"
        else -> base + "а"
    }

    fun noun(fx: Fx, plural: Boolean, case: RuCase): String {
        if (fx.has("ind")) return fx.text
        val animate = isAnimate(fx)
        if (plural) {
            val pl = fx["pl"] ?: fx.text
            return when (case) {
                RuCase.NOM -> pl
                RuCase.ACC -> if (animate) fx["gp"] ?: pl else pl
                RuCase.PREP -> oblPl(pl, "ах", "ях")
                RuCase.INSTR -> fx["ip"] ?: oblPl(pl, "ами", "ями")
            }
        }
        val text = fx.text
        val g = gender(fx)
        val base = fx["st"] ?: text
        return when (case) {
            RuCase.NOM -> text
            RuCase.ACC -> when {
                text.endsWith("а") -> text.dropLast(1) + "у"
                text.endsWith("я") -> text.dropLast(1) + "ю"
                g == 'm' && animate -> genitiveMasc(text, base)
                else -> text
            }
            RuCase.PREP -> fx["pr"] ?: prepSg(text, g, base)
            RuCase.INSTR -> fx["in"] ?: instrSg(text, g, base)
        }
    }

    private class Endings(
        val f: String, val n: String, val p: String, val genMN: String, val genP: String, val accF: String,
        val prepMN: String, val prepF: String, val prepP: String, val instrMN: String, val instrF: String, val instrP: String,
    )

    private val HARD = Endings("ая", "ое", "ые", "ого", "ых", "ую", "ом", "ой", "ых", "ым", "ой", "ыми")
    private val SIBILANT = Endings("ая", "ее", "ие", "его", "их", "ую", "ем", "ей", "их", "им", "ей", "ими")
    private val SOFT = Endings("яя", "ее", "ие", "его", "их", "юю", "ем", "ей", "их", "им", "ей", "ими")

    fun adjective(text: String, g: Char, case: RuCase, animate: Boolean): String {
        val kind = text.takeLast(2)
        val stem = text.dropLast(2)
        val last = stem.lastOrNull() ?: ' '
        val sibilant = last in "жчшщ"
        val velar = last in "гкх"
        val endings = when {
            kind == "ий" && sibilant -> SIBILANT
            kind == "ий" && !velar -> SOFT
            else -> HARD
        }
        fun y(s: String) = if (endings === HARD && (sibilant || velar)) s.replace('ы', 'и') else s
        return when (case) {
            RuCase.NOM -> when (g) { 'm' -> text; 'f' -> stem + endings.f; 'n' -> stem + endings.n; else -> stem + y(endings.p) }
            RuCase.ACC -> when (g) {
                'm' -> if (animate) stem + endings.genMN else text
                'f' -> stem + endings.accF
                'n' -> stem + endings.n
                else -> if (animate) stem + y(endings.genP) else stem + y(endings.p)
            }
            RuCase.PREP -> stem + when (g) { 'f' -> endings.prepF; 'p' -> y(endings.prepP); else -> endings.prepMN }
            RuCase.INSTR -> stem + when (g) { 'f' -> endings.instrF; 'p' -> y(endings.instrP); else -> y(endings.instrMN) }
        }
    }

    /** мой, твой, наш, ваш, этот: [kind] is "мо", "тво", "наш", "ваш" or "эт". */
    fun possessive(kind: String, g: Char, case: RuCase, animate: Boolean): String {
        val my = kind == "мо" || kind == "тво"
        val dem = kind == "эт"
        fun suffix(nom: String, gen: String, genP: String, acc: String, prepMN: String, prepF: String, prepP: String, instrMN: String, instrF: String, instrP: String, f: String, n: String, p: String) =
            when (case) {
                RuCase.NOM -> when (g) { 'm' -> nom; 'f' -> f; 'n' -> n; else -> p }
                RuCase.ACC -> when (g) { 'm' -> if (animate) gen else nom; 'f' -> acc; 'n' -> n; else -> if (animate) genP else p }
                RuCase.PREP -> when (g) { 'f' -> prepF; 'p' -> prepP; else -> prepMN }
                RuCase.INSTR -> when (g) { 'f' -> instrF; 'p' -> instrP; else -> instrMN }
            }
        return kind + when {
            my -> suffix("й", "его", "их", "ю", "ём", "ей", "их", "им", "ей", "ими", "я", "ё", "и")
            dem -> suffix("от", "ого", "их", "у", "ом", "ой", "их", "им", "ой", "ими", "а", "о", "и")
            else -> suffix("", "его", "их", "у", "ем", "ей", "их", "им", "ей", "ими", "а", "е", "и")
        }
    }

    fun present(fx: Fx): List<String> = fx["p"]?.split(',') ?: error("No present forms for ${fx.text}")

    /** Past tense: g = m, f, n or p. */
    fun past(fx: Fx, g: Char): String {
        val index = when (g) { 'm' -> 0; 'f' -> 1; 'n' -> 2; else -> 3 }
        fx["past"]?.let { return it.split(',')[index] }
        val inf = fx.text
        val reflexive = inf.endsWith("ся")
        val stem = (if (reflexive) inf.dropLast(2) else inf).dropLast(2)
        val suffix = listOf("л", "ла", "ло", "ли")[index]
        return stem + suffix + if (reflexive) (if (index == 0) "ся" else "сь") else ""
    }

    val persons = listOf("я", "ты", "он/она", "мы", "вы", "они")

    fun note(entry: Entry, fx: Fx): String? = when (entry) {
        is Noun -> {
            val g = gender(fx)
            "${fx.text} : nom ${when (g) { 'm' -> "masculin"; 'f' -> "féminin"; else -> "neutre" }}" +
                (if (isAnimate(fx)) ", animé" else "") + (if (fx.has("pl")) ", pluriel « ${fx["pl"]} »" else "") +
                (if (fx.has("ind")) " (invariable)" else "") +
                " — à l'accusatif : ${noun(fx, false, RuCase.ACC)}, au prépositionnel : ${noun(fx, false, RuCase.PREP)}"
        }
        is Adj -> "${fx.text} : ${adjective(fx.text, 'f', RuCase.NOM, false)}, ${adjective(fx.text, 'n', RuCase.NOM, false)}, ${adjective(fx.text, 'p', RuCase.NOM, false)}"
        is Verb -> if (fx.has("mf")) "${fx.text} : ${fx["mf"]!!.split(',').joinToString(", ")}" else {
            val table = present(fx).mapIndexed { i, f -> "${persons[i]} $f" }.joinToString(", ")
            "${fx.text} : $table — passé : ${listOf('m', 'f', 'n', 'p').joinToString(", ") { past(fx, it) }}"
        }
        else -> null
    }
}

internal class RussianRealizer(private val lex: Lexicon) : Realizer {
    override val lang = Lang.RU
    override val caps = Caps(
        negatedObject = false, pastNeedsGender = true, modalNeedsGender = true,
        infinitive = { v -> lex.fx(v, Lang.RU)?.has("inf") == true },
    )

    private val byt = listOf("буду", "будешь", "будет", "будем", "будете", "будут")
    private fun fx(e: Entry) = lex.fx(e, Lang.RU) ?: error("No Russian translation for ${e.id}")

    private class Subj(val tokens: List<String>, val person: Int, val g: Char)

    private fun beForms(g: Char) = when (g) { 'm' -> "был"; 'f' -> "была"; 'n' -> "было"; else -> "были" }

    override fun realize(node: Node, sink: Sink): List<T> {
        val raw = when (node) {
            is NpNode -> np(node, RuCase.NOM, sink)
            is SentenceNode -> sentence(node, sink)
        }
        // "собака\u0001défini": the hint tells the French definite/indefinite article.
        return raw.map { t -> if ('\u0001' in t) T(t.substringBefore('\u0001'), t.substringAfter('\u0001')) else T(t) }
    }

    // ───────── Noun phrases ─────────

    private fun np(n: NpNode, case: RuCase, sink: Sink): List<String> {
        val nf = fx(n.noun)
        val g = RuMorph.gender(nf)
        val agree = if (n.plural) 'p' else g
        val animate = RuMorph.isAnimate(nf)
        val out = ArrayList<String>()
        var hint: String? = null
        when (n.det) {
            Det.THE -> hint = "défini"
            Det.A -> hint = "indéfini"
            Det.MY -> { sink.rule(R_POSSESSIVE); out += RuMorph.possessive("мо", agree, case, animate) }
            Det.YOUR -> { sink.rule(R_POSSESSIVE); out += RuMorph.possessive(if (n.yourTu) "тво" else "ваш", agree, case, animate) }
            Det.OUR -> { sink.rule(R_POSSESSIVE); out += RuMorph.possessive("наш", agree, case, animate) }
            Det.THEIR -> { sink.rule(R_POSSESSIVE); out += "их" }
            Det.THIS -> { sink.rule(R_POSSESSIVE); out += RuMorph.possessive("эт", agree, case, animate) }
        }
        if (hint != null) sink.rule(R_NO_ARTICLES)
        sink.rule(R_GENDER)
        if (case != RuCase.NOM) sink.rule(R_CASES)
        if (case == RuCase.ACC && animate) sink.rule(R_ANIMATE)
        if (n.plural) sink.rule(R_PLURAL)
        for (a in n.adjs) {
            sink.rule(R_ADJECTIVE)
            val form = fx(a).text
            out += RuMorph.adjective(form, agree, case, animate && case == RuCase.ACC)
            sink.alt(RuMorph.adjective(form, 'f', case, false), RuMorph.adjective(form, 'n', case, false), RuMorph.adjective(form, 'p', case, false))
        }
        val noun = RuMorph.noun(nf, n.plural, case)
        out += if (hint != null) noun + "\u0001" + hint else noun
        sink.alt(RuMorph.noun(nf, n.plural, RuCase.NOM), RuMorph.noun(nf, n.plural, RuCase.ACC), RuMorph.noun(nf, n.plural, RuCase.PREP))
        n.modifier?.let { m ->
            sink.rule(R_PREPOSITIONS)
            if (m.prep == ModPrep.WITH) { out += "с"; out += np(m.np, RuCase.INSTR, sink) }
            else { out += "в"; out += np(m.np, RuCase.PREP, sink) }
        }
        n.relative?.let { r ->
            sink.rule(R_RELATIVE)
            val rel = when (agree) { 'm' -> "который"; 'f' -> "которая"; 'n' -> "которое"; else -> "которые" }
            val s = Subj(listOf(rel), if (n.plural) 5 else 2, agree)
            val obj = r.obj?.let { np(it, RuCase.ACC, sink) }.orEmpty()
            out += finiteVerb(s, r.verb, Tense.PRESENT, false, sink) + obj
        }
        return out
    }

    // ───────── Clauses ─────────

    private fun subject(s: SubjNode, sink: Sink): Subj = when (s) {
        is SubjNp -> {
            val nf = fx(s.np.noun)
            Subj(np(s.np, RuCase.NOM, sink), if (s.np.plural) 5 else 2, if (s.np.plural) 'p' else RuMorph.gender(nf))
        }
        is SubjPron -> {
            sink.rule(R_PRONOUN)
            sink.alt("я", "ты", "он", "она", "мы", "вы", "они")
            when (s.pron) {
                Pron.I -> Subj(listOf("я"), 0, 'm')
                Pron.YOU_TU -> Subj(listOf("ты"), 1, 'm')
                Pron.YOU_VOUS -> Subj(listOf("вы"), 4, 'p')
                Pron.HE -> Subj(listOf("он"), 2, 'm')
                Pron.SHE -> Subj(listOf("она"), 2, 'f')
                Pron.WE -> Subj(listOf("мы"), 3, 'p')
                Pron.THEY_M -> Subj(listOf("они"), 5, 'p')
                Pron.THEY_F -> Subj(listOf("они\u0001f."), 5, 'p')
            }
        }
    }

    /** The conjugated verb, with "не" and the auxiliary "быть" in the future. Infinitive is added after for the future. */
    private fun finiteVerb(s: Subj, v: Verb, tense: Tense, neg: Boolean, sink: Sink): List<String> {
        val vf = fx(v)
        val out = ArrayList<String>()
        if (neg) { sink.rule(R_NEGATION); out += "не" }
        sink.alt(listOf("не"))
        if (vf.has("mf")) {
            sink.rule(R_MODAL)
            val forms = vf["mf"]!!.split(',')
            sink.alt(forms)
            out += forms[when (s.g) { 'm' -> 0; 'f' -> 1; 'n' -> 2; else -> 3 }]
            return out
        }
        when {
            v.modal || tense == Tense.PRESENT -> {
                sink.rule(if (v.modal) R_MODAL else R_PRESENT)
                out += RuMorph.present(vf)[s.person]
                sink.alt(RuMorph.present(vf))
            }
            tense == Tense.PAST -> {
                sink.rule(R_PAST); sink.rule(R_ASPECT)
                out += RuMorph.past(vf, s.g)
                sink.alt(listOf('m', 'f', 'n', 'p').map { RuMorph.past(vf, it) })
            }
            else -> {
                sink.rule(R_FUTURE); sink.rule(R_ASPECT)
                out += byt[s.person]; out += vf.text
                sink.alt(byt)
            }
        }
        return out
    }

    private fun complements(p: VerbPred, sink: Sink): List<String> {
        val out = ArrayList<String>()
        p.obj?.let { out += np(it, RuCase.ACC, sink) }
        p.loc?.let { loc ->
            val spec = fx(p.verb)["loc"].orEmpty().split(':')
            sink.rule(R_PREPOSITIONS)
            out += spec[0]
            out += np(loc, if (spec.getOrElse(1) { "prep" } == "acc") RuCase.ACC else RuCase.PREP, sink)
        }
        p.adv?.let { a -> out += fx(a).text.split(' ') }
        return out
    }

    private fun copula(s: Subj, tense: Tense, neg: Boolean, rest: List<String>, sink: Sink): List<String> {
        sink.rule(R_COPULA)
        val out = ArrayList<String>()
        if (neg) { sink.rule(R_NEGATION); out += "не" }
        when (tense) {
            Tense.PRESENT -> {}
            Tense.PAST -> { sink.rule(R_PAST); out += beForms(s.g); sink.alt("был", "была", "было", "были") }
            Tense.FUTURE -> { sink.rule(R_FUTURE); out += byt[s.person]; sink.alt(byt) }
        }
        return out + rest
    }

    private fun clause(c: ClauseNode, sink: Sink): List<String> {
        val s = subject(c.subj, sink)
        val body = when (val p = c.pred) {
            is CopAdj -> {
                sink.rule(R_ADJECTIVE)
                val form = RuMorph.adjective(fx(p.adj).text, s.g, RuCase.NOM, false)
                copula(s, c.tense, c.neg, listOf(form), sink)
            }
            is CopLoc -> { sink.rule(R_PREPOSITIONS); copula(s, c.tense, c.neg, listOf("в") + np(p.place, RuCase.PREP, sink), sink) }
            is VerbPred -> finiteVerb(s, p.verb, c.tense, c.neg, sink) + complements(p, sink)
            is ModalPred -> {
                sink.alt(fx(p.inner.verb).text)
                finiteVerb(s, p.modal, Tense.PRESENT, c.neg, sink) + fx(p.inner.verb).text + complements(p.inner, sink)
            }
            is InfPred -> {
                sink.rule(R_INFINITIVE)
                sink.alt(fx(p.inner.verb).text)
                finiteVerb(s, p.outer, c.tense, c.neg, sink) + fx(p.inner.verb).text + complements(p.inner, sink)
            }
        }
        return s.tokens + body
    }

    private fun sentence(n: SentenceNode, sink: Sink): List<String> {
        val out = ArrayList<String>()
        if (n.joiners.isNotEmpty()) sink.rule(R_CONNECTORS)
        val conditional = n.joiners.firstOrNull() == Conn.IF
        if (conditional) out += "если"
        for ((i, c) in n.clauses.withIndex()) {
            if (i > 0 && !conditional) out += when (n.joiners[i - 1]) {
                Conn.AND -> listOf("и"); Conn.BUT -> listOf("но"); Conn.BECAUSE -> listOf("потому", "что")
                Conn.WHEN -> listOf("когда"); Conn.IF -> listOf("если")
            }
            out += clause(c, sink)
        }
        return out
    }

    private companion object {
        fun r(key: String, title: String, text: String, example: String) = RuleInfo("ru:$key", title, text, example)

        val R_NO_ARTICLES = r("no-articles", "Pas d'articles",
            "Le russe n'a ni article défini ni article indéfini : « собака » peut se traduire par le chien, un chien ou la chienne. Le contexte décide.",
            "собака = le chien / un chien")
        val R_GENDER = r("gender", "Les trois genres",
            "Masculin (consonne finale : стол, -ь, -й), féminin (-а, -я : книга, -ь : дверь) et neutre (-о, -е : окно, море). " +
                "Le genre commande l'accord des adjectifs et du verbe au passé.",
            "новый стол · новая книга · новое окно")
        val R_CASES = r("cases", "Les cas",
            "Le nom change de terminaison selon sa fonction. Nominatif : sujet. Accusatif : complément d'objet direct, et lieu où l'on va (в + accusatif). " +
                "Prépositionnel : lieu où l'on est (в + prépositionnel). Instrumental : « avec » (с + instrumental).",
            "Я вижу собаку · в доме · с собакой")
        val R_ANIMATE = r("animate", "Accusatif des êtres animés",
            "Pour les personnes et les animaux, l'accusatif masculin singulier et tout le pluriel prennent la forme du génitif : " +
                "я вижу брата (frère), я вижу братьев ; pour les noms inanimés il est identique au nominatif : я вижу стол.",
            "Я вижу брата · Я вижу собак · Я вижу стол")
        val R_PLURAL = r("plural", "Pluriel",
            "Le pluriel nominatif est en général -ы/-и (стол → столы, книга → книги) ou -а/-я pour certains masculins (дом → дома) ; irrégulier : ребёнок → дети, друг → друзья.",
            "стол → столы · дом → дома · ребёнок → дети")
        val R_ADJECTIVE = r("adjective", "Accord de l'adjectif",
            "L'adjectif s'accorde en genre, nombre et cas avec le nom : -ый/-ой (m.), -ая (f.), -ое (n.), -ые (pluriel) au nominatif.",
            "большой дом · большая собака · большое окно · большие дома")
        val R_POSSESSIVE = r("possessive", "Possessifs et démonstratifs",
            "мой, твой, наш, ваш, этот s'accordent avec le nom (мой, моя, моё, мои) ; « leur » : их, invariable.",
            "моя книга · наш дом · эти собаки · их машина")
        val R_PRONOUN = r("pronoun", "Pronoms sujets",
            "я, ты (familier), он, она, мы, вы (pluriel ou politesse), они (pour ils comme pour elles).",
            "я ем · ты ешь · они едят")
        val R_PRESENT = r("present", "Présent",
            "Le verbe se conjugue à chaque personne (я читаю, ты читаешь, он читает, мы читаем, вы читаете, они читают). Pas de verbe « être » au présent : « Он большой ».",
            "я читаю · она живёт · мы идём")
        val R_PAST = r("past", "Passé",
            "Le passé se forme avec l'infinitif sans -ть + -л (masculin), -ла (féminin), -ло (neutre), -ли (pluriel) : il s'accorde avec le sujet, pas avec la personne.",
            "он читал · она читала · они читали")
        val R_FUTURE = r("future", "Futur",
            "будущее composé : быть conjugué (буду, будешь, будет, будем, будете, будут) + infinitif. « Je vais manger » se dit « я буду есть ».",
            "я буду читать · он будет большим")
        val R_ASPECT = r("aspect", "Aspect",
            "Chaque verbe a souvent deux formes : imperfectif (action en cours ou répétée : читать) et perfectif (action terminée : прочитать). " +
                "Ce cours utilise la forme imperfective, qui sert pour le présent, le passé et le futur composé.",
            "я читал (j'ai lu / je lisais) · я прочитал (j'ai fini de lire)")
        val R_NEGATION = r("negation", "Négation",
            "On met « не » devant le mot nié (le verbe, l'adjectif…). Après une négation le complément d'objet passe d'ordinaire au génitif ; " +
                "pour cette raison ce cours n'utilise la négation que sans complément d'objet.",
            "Он не читает · Она не большая")
        val R_MODAL = r("modal", "мочь et должен",
            "мочь (могу, можешь, может, можем, можете, могут) + infinitif. « Devoir » : « должен » est un adjectif qui s'accorde : он должен, она должна, они должны.",
            "я могу читать · она должна читать")
        val R_INFINITIVE = r("infinitive", "Verbe + infinitif",
            "хотеть, пытаться, начинать, решать, надеяться, обещать, продолжать… sont suivis directement de l'infinitif (pas de préposition).",
            "я хочу читать · он начинает работать")
        val R_COPULA = r("copula", "Pas de « être » au présent",
            "Au présent le verbe être disparaît : « Он дома », « Она большая ». Au passé : был, была, было, были ; au futur : буду, будешь, будет…",
            "Она была в доме · Он будет большим")
        val R_PREPOSITIONS = r("prepositions", "в et с",
            "в + prépositionnel = dans, à l'intérieur (в доме) ; в + accusatif = vers un lieu (иду в дом) ; с + instrumental = avec (с собакой).",
            "Я живу в городе · Я иду в город · Женщина с собакой")
        val R_RELATIVE = r("relative", "который",
            "« Qui » relatif se traduit par « который », décliné comme un adjectif (который, которая, которое, которые).",
            "мужчина, который читает книгу")
        val R_CONNECTORS = r("connectors", "Mots de liaison",
            "и (et), но (mais), потому что (parce que), когда (quand), если (si) ; l'ordre des mots ne change pas.",
            "Он читает, а она спит · Если он читает, я читаю")
    }
}
