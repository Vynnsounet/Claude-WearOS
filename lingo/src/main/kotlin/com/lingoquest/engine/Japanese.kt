package com.lingoquest.engine

/** Japanese morphology (polite style, written in kana) shared by the realizer and the word notes. */
internal object JaMorph {
    private val iRow = mapOf('う' to 'い', 'く' to 'き', 'ぐ' to 'ぎ', 'す' to 'し', 'つ' to 'ち', 'ぬ' to 'に', 'ぶ' to 'び', 'む' to 'み', 'る' to 'り')
    private val aRow = mapOf('う' to 'わ', 'く' to 'か', 'ぐ' to 'が', 'す' to 'さ', 'つ' to 'た', 'ぬ' to 'な', 'ぶ' to 'ば', 'む' to 'ま', 'る' to 'ら')

    fun cls(fx: Fx) = fx["c"] ?: "1"
    fun isStative(fx: Fx) = cls(fx) == "si" || cls(fx) == "sna"

    private fun isSuru(d: String) = d.endsWith("する")
    private fun isKuru(d: String) = d.endsWith("くる")

    /** The stem that takes ます / たい / はじめる: たべ, のみ, し, き. */
    fun masuStem(fx: Fx): String {
        val d = fx.text
        return when {
            isSuru(d) -> d.dropLast(2) + "し"
            isKuru(d) -> d.dropLast(2) + "き"
            cls(fx) == "1" -> d.dropLast(1)
            else -> d.dropLast(1) + (iRow[d.last()] ?: error("Bad godan verb $d"))
        }
    }

    /** The stem of the negative: たべ, のま, し, こ. */
    fun naiStem(fx: Fx): String {
        val d = fx.text
        return when {
            isSuru(d) -> d.dropLast(2) + "し"
            isKuru(d) -> d.dropLast(2) + "こ"
            cls(fx) == "1" -> d.dropLast(1)
            else -> d.dropLast(1) + (aRow[d.last()] ?: error("Bad godan verb $d"))
        }
    }

    fun teForm(fx: Fx): String {
        val d = fx.text
        return when {
            isSuru(d) -> d.dropLast(2) + "して"
            isKuru(d) -> d.dropLast(2) + "きて"
            cls(fx) == "1" -> d.dropLast(1) + "て"
            d == "いく" -> "いって"
            else -> when (d.last()) {
                'う', 'つ', 'る' -> d.dropLast(1) + "って"
                'む', 'ぶ', 'ぬ' -> d.dropLast(1) + "んで"
                'く' -> d.dropLast(1) + "いて"
                'ぐ' -> d.dropLast(1) + "いで"
                'す' -> d.dropLast(1) + "して"
                else -> error("Bad godan verb $d")
            }
        }
    }

    /** ます, ません, ました or ませんでした after [stem]. */
    fun polite(stem: String, past: Boolean, neg: Boolean) =
        stem + if (!neg) (if (past) "ました" else "ます") else (if (past) "ませんでした" else "ません")

    fun adjStem(fx: Fx) = fx["st"] ?: fx.text.dropLast(1)

    /** What goes before a noun. */
    fun attributive(fx: Fx): String = when (fx["c"]) {
        "na" -> fx.text + "な"
        else -> fx.text
    }

    /** The predicate "…です" (and its tense / negative) of an adjective-like word. */
    fun predicate(base: String, cls: String, stem: String, tense: Tense, neg: Boolean): List<String> {
        if (tense == Tense.FUTURE) return if (cls == "i" || cls == "si") listOf(if (neg) stem + "くない" else base, "でしょう") else
            if (neg) listOf(base, "では", "ない", "でしょう") else listOf(base, "でしょう")
        val past = tense == Tense.PAST
        return when (cls) {
            "i", "si" -> when {
                !neg && !past -> listOf(base, "です")
                neg && !past -> listOf(stem + "くない", "です")
                !neg -> listOf(stem + "かった", "です")
                else -> listOf(stem + "くなかった", "です")
            }
            else -> when {
                !neg && !past -> listOf(base, "です")
                neg && !past -> listOf(base, "では", "ありません")
                !neg -> listOf(base, "でした")
                else -> listOf(base, "では", "ありません", "でした")
            }
        }
    }

    fun note(entry: Entry, fx: Fx): String? {
        val reading = fx["r"]?.let { " ($it)" }.orEmpty()
        return when (entry) {
            is Noun -> "${fx.text}$reading : pas de genre ni de pluriel ; l'article se déduit du contexte"
            is Adj -> {
                val kind = when (fx["c"]) { "i" -> "adjectif en い"; "na" -> "adjectif en な (+ な devant un nom)"; "no" -> "se relie au nom avec の"; else -> "invariable" }
                "${fx.text}$reading : $kind"
            }
            is Verb -> when {
                isStative(fx) -> "${fx.text}$reading : se comporte comme un adjectif (${if (cls(fx) == "si") "ほしい → ほしくない" else "すき → すきです"}) ; l'objet prend が"
                entry.modal -> "${fx.text}$reading : verbe modal (pouvoir : ことが できます · devoir : なければ なりません)"
                else -> {
                    val group = when {
                        isSuru(fx.text) -> "verbe en する"
                        isKuru(fx.text) -> "verbe irrégulier (くる)"
                        cls(fx) == "1" -> "groupe 1 (る-verbe)"
                        else -> "groupe 2 (う-verbe)"
                    }
                    val stem = masuStem(fx)
                    "${fx.text}$reading : $group — ${polite(stem, false, false)} · ${polite(stem, false, true)} · ${polite(stem, true, false)} · ${polite(stem, true, true)}"
                }
            }
            else -> null
        }
    }
}

internal class JapaneseRealizer(private val lex: Lexicon) : Realizer {
    override val lang = Lang.JA
    override val caps = Caps(
        modifiers = false,
        innerVerb = { v -> lex.fx(v, Lang.JA)?.let { !JaMorph.isStative(it) } ?: false },
        connectors = setOf(Conn.AND, Conn.BUT, Conn.BECAUSE),
        infinitive = { v -> lex.fx(v, Lang.JA)?.has("inf") == true },
    )

    private fun fx(e: Entry) = lex.fx(e, Lang.JA) ?: error("No Japanese translation for ${e.id}")

    private class Subj(val tokens: List<String>, val animate: Boolean)

    override fun sentence(tokens: List<String>, period: Boolean): String = join(tokens) + if (period) "。" else ""

    override fun realize(node: Node, sink: Sink): List<T> {
        val raw = when (node) {
            is NpNode -> np(node, sink)
            is SentenceNode -> sentence(node, sink)
        }
        // "いぬ\u0001le": the hint tells which French article to use.
        return raw.map { t -> if ('\u0001' in t) T(t.substringBefore('\u0001'), t.substringAfter('\u0001')) else T(t) }
    }

    // ───────── Noun phrases ─────────

    private fun np(n: NpNode, sink: Sink): List<String> {
        val out = ArrayList<String>()
        sink.rule(R_NO_ARTICLES)
        n.relative?.let { r ->
            sink.rule(R_RELATIVE)
            out += relativeClause(r, sink)
        }
        var hint: String? = null
        when (n.det) {
            Det.THE -> hint = if (n.plural) "défini pl." else "défini"
            Det.A -> hint = if (n.plural) "indéfini pl." else "indéfini"
            Det.MY -> { out += "わたし"; out += "の" }
            Det.YOUR -> { out += if (n.yourTu) "あなた" else "あなたたち"; out += "の" }
            Det.OUR -> { out += "わたしたち"; out += "の" }
            Det.THEIR -> { out += "かれら"; out += "の" }
            Det.THIS -> out += "この"
        }
        if (n.det in listOf(Det.MY, Det.YOUR, Det.OUR, Det.THEIR, Det.THIS)) {
            sink.rule(R_POSSESSIVE)
            if (n.plural) hint = "pl."
        }
        for (a in n.adjs) {
            sink.rule(R_ADJECTIVES)
            val af = fx(a)
            out += JaMorph.attributive(af)
            sink.alt(af.text)
        }
        val noun = fx(n.noun).text
        out += if (hint != null) noun + "\u0001" + hint else noun
        return out
    }

    /** "りんごを たべる" : a plain-form clause in front of the noun it describes. */
    private fun relativeClause(r: RelNode, sink: Sink): List<String> {
        val vf = fx(r.verb)
        val out = ArrayList<String>()
        r.obj?.let { out += np(it, sink); out += particle(r.verb) }
        out += plainPresent(r.verb, vf)
        return out
    }

    private fun plainPresent(v: Verb, vf: Fx): String = when {
        JaMorph.cls(vf) == "sna" -> vf.text + "な"
        JaMorph.cls(vf) == "si" -> vf.text
        vf.has("ti") -> JaMorph.teForm(vf) + "いる"
        else -> vf.text
    }

    private fun particle(v: Verb): String = when (fx(v)["op"]) { "ga" -> "が"; "ni" -> "に"; else -> "を" }

    // ───────── Verb groups ─────────

    private fun subject(s: SubjNode, sink: Sink): Subj = when (s) {
        is SubjNp -> Subj(np(s.np, sink), s.np.noun.hasTag("PA"))
        is SubjPron -> {
            sink.rule(R_PRONOUN)
            sink.alt("わたし", "あなた", "かれ", "かのじょ", "わたしたち", "かれら")
            when (s.pron) {
                Pron.I -> Subj(listOf("わたし"), true)
                Pron.YOU_TU -> Subj(listOf("あなた\u0001tu"), true)
                Pron.YOU_VOUS -> Subj(listOf("あなたたち"), true)
                Pron.HE -> Subj(listOf("かれ"), true)
                Pron.SHE -> Subj(listOf("かのじょ"), true)
                Pron.WE -> Subj(listOf("わたしたち"), true)
                Pron.THEY_M -> Subj(listOf("かれら"), true)
                Pron.THEY_F -> Subj(listOf("かのじょたち"), true)
            }
        }
    }

    /** The main verb with its ending for [tense] and [neg]. */
    private fun verbGroup(v: Verb, tense: Tense, neg: Boolean, sink: Sink): List<String> {
        val vf = fx(v)
        val cls = JaMorph.cls(vf)
        sink.rule(R_POLITE)
        if (neg) sink.rule(R_NEGATION)
        if (tense == Tense.PAST) sink.rule(R_PAST)
        if (cls == "si" || cls == "sna") {
            sink.rule(R_STATIVE)
            return JaMorph.predicate(vf.text, if (cls == "si") "si" else "na", JaMorph.adjStem(vf), if (tense == Tense.FUTURE) Tense.PRESENT else tense, neg)
        }
        sink.rule(R_VERB_GROUPS)
        val past = tense == Tense.PAST
        return when {
            tense == Tense.FUTURE -> {
                sink.rule(R_FUTURE)
                listOf(if (neg) JaMorph.naiStem(vf) + "ない" else vf.text, "つもり", "です")
            }
            vf.has("ti") -> { sink.rule(R_TE_IRU); listOf(JaMorph.polite(JaMorph.teForm(vf) + "い", past, neg)) }
            else -> listOf(JaMorph.polite(JaMorph.masuStem(vf), past, neg))
        }
    }

    /** Verb phrase with its object / place, in the order topic, place, object, adverb, verb. */
    private fun complements(p: VerbPred, sink: Sink): Pair<List<String>, List<String>> {
        val time = ArrayList<String>()
        val rest = ArrayList<String>()
        p.adv?.let { a ->
            val af = fx(a)
            sink.rule(R_ADVERBS)
            if (af.has("t")) time += af.text else rest += af.text
        }
        p.loc?.let { loc ->
            sink.rule(R_PARTICLES)
            rest += np(loc, sink)
            rest += when (fx(p.verb)["loc"]) { "de" -> "で"; else -> "に" }
        }
        p.obj?.let {
            sink.rule(R_PARTICLES)
            rest += np(it, sink); rest += particle(p.verb)
        }
        return time to rest
    }

    private fun clauseTokens(c: ClauseNode, sink: Sink, subjectParticle: String = "は"): List<String> {
        val s = subject(c.subj, sink)
        sink.rule(R_ORDER)
        val head = s.tokens + subjectParticle
        sink.rule(R_PARTICLES)
        return head + when (val p = c.pred) {
            is CopAdj -> {
                val af = fx(p.adj)
                sink.rule(R_ADJECTIVES); sink.rule(R_COPULA)
                if (c.neg) sink.rule(R_NEGATION)
                val cls = af["c"] ?: "i"
                val base = if (cls == "no") af.text.removeSuffix("の") else af.text
                JaMorph.predicate(base, if (cls == "i") "i" else "na", JaMorph.adjStem(af), c.tense, c.neg)
            }
            is CopLoc -> {
                sink.rule(R_EXISTENCE)
                val place = np(p.place, sink)
                place + "に" + existence(s.animate, c.tense, c.neg)
            }
            is VerbPred -> {
                val (time, rest) = complements(p, sink)
                time + rest + verbGroup(p.verb, c.tense, c.neg, sink)
            }
            is ModalPred -> {
                val (time, rest) = complements(p.inner, sink)
                val vf = fx(p.inner.verb)
                sink.rule(R_MODAL)
                time + rest + modalTail(p.modal, vf, c.neg)
            }
            is InfPred -> {
                val (time, rest) = complements(p.inner, sink)
                time + rest + infinitiveTail(p.outer, p.inner.verb, c.tense, c.neg, sink)
            }
        }
    }

    private fun existence(animate: Boolean, tense: Tense, neg: Boolean): List<String> {
        val iru = animate
        return when (tense) {
            Tense.FUTURE -> listOf(if (neg) (if (iru) "いない" else "ない") else (if (iru) "いる" else "ある"), "でしょう")
            else -> {
                val stem = if (iru) "い" else "あり"
                listOf(JaMorph.polite(stem, tense == Tense.PAST, neg))
            }
        }
    }

    private fun modalTail(modal: Verb, inner: Fx, neg: Boolean): List<String> =
        if (modal.id == "v:can") listOf(inner.text + "こと", "が", if (neg) "できません" else "できます")
        else if (neg) listOf(JaMorph.teForm(inner) + "は", "いけません")
        else listOf(JaMorph.naiStem(inner) + "なければ", "なりません")

    private fun infinitiveTail(outer: Verb, inner: Verb, tense: Tense, neg: Boolean, sink: Sink): List<String> {
        val o = fx(outer)
        val i = fx(inner)
        sink.rule(R_COMPOUND)
        val past = tense == Tense.PAST
        return when (o["inf"]) {
            "tai" -> {
                sink.rule(R_STATIVE)
                JaMorph.predicate(JaMorph.masuStem(i) + "たい", "i", JaMorph.masuStem(i) + "た", tense, neg)
                    .let { if (it.size >= 2 && it[0].endsWith("たい")) it else it }
            }
            "koto" -> listOf(JaMorph.polite(i.text + "ことに" + "し", past, neg))
            "te" -> listOf(JaMorph.polite(JaMorph.teForm(i) + "み", past, neg))
            else -> listOf(JaMorph.polite(JaMorph.masuStem(i) + (if (outer.id == "v:continue") "つづけ" else "はじめ"), past, neg))
        }
    }

    private fun sentence(n: SentenceNode, sink: Sink): List<String> {
        if (n.joiners.isNotEmpty()) sink.rule(R_CONNECTORS)
        val clauses = n.clauses.map { clauseTokens(it, sink) }
        if (clauses.size == 1) return clauses[0]
        val out = ArrayList<String>()
        if (n.joiners[0] == Conn.BECAUSE) {
            // "A because B" is said "B から A".
            out += clauses[1]; out += "から"; out += clauses[0]
            return out
        }
        out += clauses[0]
        for ((i, joiner) in n.joiners.withIndex()) {
            out += when (joiner) { Conn.BUT -> "が"; else -> "そして" }
            out += clauses[i + 1]
        }
        return out
    }

    private companion object {
        fun r(key: String, title: String, text: String, example: String) = RuleInfo("ja:$key", title, text, example)

        val R_ORDER = r("order", "Ordre des mots : sujet – objet – verbe",
            "Le verbe est toujours à la fin de la phrase. L'ordre est : sujet、 (lieu / temps)、 objet、 verbe. Les phrases finissent par 。",
            "わたしは りんごを たべます (je mange une pomme)")
        val R_PARTICLES = r("particles", "Les particules は を に で が",
            "Les mots sont suivis de petites particules qui donnent leur fonction : は (thème, souvent le sujet), を (objet direct), " +
                "に (lieu où l'on va ou où l'on est, destination), で (lieu de l'action), が (sujet / objet de « aimer », « vouloir », « comprendre »), の (appartenance).",
            "ねこは さかなを たべます · がっこうに いきます · こうえんで あそびます")
        val R_NO_ARTICLES = r("no-articles", "Ni articles, ni pluriel, ni genre",
            "Le japonais n'a pas d'articles, pas de genre et, en général, pas de pluriel : « いぬ » peut être le chien, un chien, des chiens… Le contexte décide.",
            "いぬ = le chien / un chien / des chiens")
        val R_PRONOUN = r("pronoun", "Pronoms",
            "わたし (je), あなた (tu / vous), かれ (il), かのじょ (elle), わたしたち (nous), あなたたち (vous, pluriel), かれら (ils), かのじょたち (elles). " +
                "On les omet souvent quand le contexte est clair ; ils sont ici toujours exprimés.",
            "かれは たべます · かのじょたちは のみます")
        val R_POSSESSIVE = r("possessive", "の, この",
            "N1 の N2 = le N2 de N1 : わたしの いぬ (mon chien). この = ce / cette / ces.",
            "わたしの ほん · この いぬ")
        val R_ADJECTIVES = r("adjectives", "Adjectifs en い et en な",
            "Les adjectifs en い s'emploient tels quels devant un nom (おおきい いぬ) ; ceux en な prennent な (きれいな はな) ; certains se relient avec の (ほかの ひと). " +
                "Après le nom, on met です : いぬは おおきいです.",
            "あかい くるま · きれいな はな · ほかの ひと")
        val R_POLITE = r("polite", "Le style poli : ます",
            "On forme le verbe poli avec le radical + ます (présent et futur) : たべる → たべます, のむ → のみます. Le japonais n'a pas de futur : le présent sert aussi.",
            "たべます · のみます · いきます")
        val R_VERB_GROUPS = r("verb-groups", "Groupes de verbes",
            "Groupe 1 (る-verbes) : on retire る (たべる → たべ). Groupe 2 (う-verbes) : la dernière syllabe passe à la colonne い (のむ → のみ, かく → かき). " +
                "Irréguliers : する (し) et くる (き).",
            "たべる → たべます · のむ → のみます · する → します")
        val R_NEGATION = r("negation", "Négation",
            "Verbes : ます → ません (たべません). Adjectifs en い : い → くない (おおきくない), puis です. Adjectifs en な : では ありません.",
            "たべません · おおきくないです · きれいでは ありません")
        val R_PAST = r("past", "Passé",
            "Verbes : ます → ました (たべました), ません → ませんでした. Adjectifs en い : い → かった (おおきかったです). Adjectifs en な : でした.",
            "たべました · おおきかったです · しずかでした")
        val R_FUTURE = r("future", "Le futur proche",
            "Pour « aller faire », on emploie la forme du dictionnaire + つもりです (j'ai l'intention de). Négation : forme en ない + つもりです.",
            "たべる つもりです · たべない つもりです")
        val R_COPULA = r("copula", "です",
            "です correspond à « être » après un adjectif ou un nom. Au futur, でしょう (sera / sans doute).",
            "かれは おおきいです · かれは おおきいでしょう")
        val R_EXISTENCE = r("existence", "います et あります",
            "« Se trouver » : います pour les êtres vivants, あります pour les objets. Le lieu est suivi de に.",
            "いぬは いえに います · ほんは つくえに あります")
        val R_STATIVE = r("stative", "Aimer, vouloir : comme des adjectifs",
            "すき (aimer) et ほしい (vouloir) fonctionnent comme des adjectifs et leur objet prend が. Avec un verbe, « vouloir » = radical + たい : たべたい (je veux manger).",
            "りんごが すきです · りんごが ほしいです · たべたいです")
        val R_MODAL = r("modal", "Pouvoir et devoir",
            "Pouvoir : forme du dictionnaire + ことが できます. Devoir : radical négatif + なければ なりません (たべなければ なりません) ; ne pas devoir : forme en て + は いけません.",
            "たべる ことが できます · たべなければ なりません")
        val R_COMPOUND = r("compound", "Verbe + verbe",
            "Commencer : radical + はじめる ; continuer : radical + つづける ; décider : forme du dictionnaire + ことに する ; essayer : forme en て + みる.",
            "たべはじめます · たべる ことに します · たべて みます")
        val R_TE_IRU = r("te-iru", "L'état : て + います",
            "Pour un état ou une action en cours, on emploie la forme en て + います : すむ → すんで います (j'habite), もつ → もって います (je possède).",
            "すんで います · もって います")
        val R_ADVERBS = r("adverbs", "Adverbes",
            "Les adverbes de temps se placent juste après le thème (わたしは きょう…) ; ceux de manière juste avant le verbe.", "わたしは きょう はたらきます")
        val R_RELATIVE = r("relative", "Proposition relative",
            "Une relative se place devant le nom qu'elle décrit, avec le verbe à la forme simple (pas de « qui »).",
            "りんごを たべる いぬ (le chien qui mange une pomme)")
        val R_CONNECTORS = r("connectors", "Mots de liaison",
            "そして (et) relie deux phrases, が (mais) se place après le verbe poli de la première, から (parce que) suit la raison, qui vient donc en premier : « B から A ».",
            "たべますが のみません · のみますから たべません")
    }
}
