package com.lingoquest.engine

/** CEFR language levels, from beginner (A1) to advanced (C1). */
enum class Cefr { A1, A2, B1, B2, C1 }

enum class Gender { M, F }

enum class Lang(val code: String, val nameFr: String) {
    EN("en", "anglais"),
    FR("fr", "français"),
    ES("es", "espagnol"),
    DE("de", "allemand"),
    JA("ja", "japonais"),
    RU("ru", "russe"),
    ;

    /** The language being learned: every course is French ↔ another language. */
    val isCourse get() = this != FR

    companion object {
        val courses = listOf(EN, ES, DE, JA, RU)
    }
}

/** Which language the player reads (`from`) and which one they must write (`to`). French is always one side. */
enum class Direction(val from: Lang, val to: Lang) {
    FR_TO_EN(Lang.FR, Lang.EN),
    EN_TO_FR(Lang.EN, Lang.FR),
    FR_TO_ES(Lang.FR, Lang.ES),
    ES_TO_FR(Lang.ES, Lang.FR),
    FR_TO_DE(Lang.FR, Lang.DE),
    DE_TO_FR(Lang.DE, Lang.FR),
    FR_TO_JA(Lang.FR, Lang.JA),
    JA_TO_FR(Lang.JA, Lang.FR),
    FR_TO_RU(Lang.FR, Lang.RU),
    RU_TO_FR(Lang.RU, Lang.FR),
    ;

    /** The foreign language of this direction. */
    val course: Lang get() = if (from == Lang.FR) to else from
    val toFrench: Boolean get() = to == Lang.FR

    companion object {
        fun of(course: Lang, toFrench: Boolean): Direction =
            entries.first { it.course == course && it.toFrench == toFrench }
    }
}

/**
 * Translation of a base entry into a foreign language (Spanish, German, Japanese, Russian):
 * accepted forms (first = canonical) and language-specific options such as gender or conjugation.
 */
class Fx(val forms: List<String>, private val opts: Map<String, String>) {
    operator fun get(key: String): String? = opts[key]
    fun has(key: String) = key in opts
    val text get() = forms[0]
}

enum class Pos { NOUN, VERB, ADJ, ADV, WORD }

enum class Aux { AVOIR, ETRE }

/**
 * One vocabulary item. [rank] is the position in the frequency-ordered word list
 * (lower = more frequent / unlocked earlier).
 */
sealed class Entry(val level: Cefr, val rank: Int, val pos: Pos) {
    abstract val id: String

    /** Accepted translations in [lang]; the first one is the canonical form. */
    abstract fun forms(lang: Lang): List<String>

    fun lemma(lang: Lang): String = forms(lang).first()

    /** How the word is shown when it is the whole challenge. */
    open fun display(lang: Lang): String = lemma(lang)
}

class Noun(
    level: Cefr, rank: Int,
    val en: List<String>, val enPlural: String,
    val fr: List<String>, val frPlural: String,
    val gender: Gender, val tags: String,
) : Entry(level, rank, Pos.NOUN) {
    override val id get() = "n:${en[0]}"
    val uncountable get() = 'U' in tags
    override fun forms(lang: Lang) = if (lang == Lang.EN) en else fr
    fun hasTag(letters: String) = letters.any { it in tags }
}

class Adj(
    level: Cefr, rank: Int,
    val en: List<String>,
    private val m: String, private val mVowel: String?,
    private val f: String, private val mpl: String, private val fpl: String,
    /** True when the adjective goes before the noun in French (BAGS adjectives). */
    val before: Boolean,
) : Entry(level, rank, Pos.ADJ) {
    override val id get() = "a:${en[0]}"
    override fun forms(lang: Lang) = if (lang == Lang.EN) en else listOf(m)

    fun fr(gender: Gender, plural: Boolean, nextStartsWithVowel: Boolean = false): String = when {
        plural -> if (gender == Gender.M) mpl else fpl
        gender == Gender.F -> f
        nextStartsWithVowel && mVowel != null -> mVowel
        else -> m
    }

    /** Every French form, for building plausible wrong answers. */
    val allFrForms: List<String> get() = listOfNotNull(m, f, mpl, fpl, mVowel).distinct()
}

class Verb(
    level: Cefr, rank: Int,
    val en: List<String>, val en3: String, val enPast: String,
    val fr: List<String>, val present: List<String>, val participle: String,
    val aux: Aux,
    val objTags: String, val subjTags: String,
    val locEn: String?, val locFr: String?,
    val infEn: String?, val infFr: String?,
    val modal: Boolean,
) : Entry(level, rank, Pos.VERB) {
    override val id get() = "v:${en[0]}"
    val transitive get() = objTags.isNotEmpty()
    val hasLoc get() = locFr != null
    /** Takes an infinitive complement ("want to eat", "can eat"). */
    val takesInfinitive get() = modal || infEn != null
    /** A plain verb that can be the infinitive complement of another. */
    val isPlain get() = !takesInfinitive
    override fun forms(lang: Lang) = if (lang == Lang.EN) en else fr
    override fun display(lang: Lang) = if (lang == Lang.EN) "to ${en[0]}" else fr[0]
}

class Adverb(
    level: Cefr, rank: Int,
    val en: List<String>, val fr: List<String>,
    /** Tenses it fits: p = present, n = past, f = future; m = manner (intransitive only). */
    private val flags: String,
) : Entry(level, rank, Pos.ADV) {
    override val id get() = "d:${en[0]}"
    override fun forms(lang: Lang) = if (lang == Lang.EN) en else fr
    val manner get() = 'm' in flags
    fun fits(tense: Tense) = when (tense) {
        Tense.PRESENT -> 'p' in flags
        Tense.PAST -> 'n' in flags
        Tense.FUTURE -> 'f' in flags
    }
}

/** Anything else (greetings, numbers, connectors…): only used as single-word challenges. */
class Word(level: Cefr, rank: Int, val en: List<String>, val fr: List<String>) : Entry(level, rank, Pos.WORD) {
    override val id get() = "w:${en[0]}"
    override fun forms(lang: Lang) = if (lang == Lang.EN) en else fr
}

class Lexicon(entries: List<Entry>, private val translations: Map<Lang, Map<String, Fx>> = emptyMap()) {
    /** Entries sorted by CEFR level then frequency rank. */
    val entries: List<Entry> = entries.sortedWith(compareBy({ it.level.ordinal }, { it.rank }))
    private val byId = this.entries.associateBy { it.id }

    fun find(id: String): Entry? = byId[id]

    fun countAt(level: Cefr) = entries.count { it.level == level }

    /** Foreign translation of [entry]; null for English/French or when the word is not translated yet. */
    fun fx(entry: Entry, lang: Lang): Fx? = translations[lang]?.get(entry.id)

    /** The words available in the course for [lang]: those that have a translation. */
    fun forCourse(lang: Lang): Lexicon =
        if (lang == Lang.EN || lang == Lang.FR) this
        else Lexicon(entries.filter { fx(it, lang) != null }, translations)

    /** Accepted translations of [entry] in [lang]; the first one is canonical. */
    fun forms(entry: Entry, lang: Lang): List<String> =
        if (lang == Lang.EN || lang == Lang.FR) entry.forms(lang) else fx(entry, lang)?.forms.orEmpty()

    /** How the word is shown when it is the whole challenge. */
    fun display(entry: Entry, lang: Lang): String = when (lang) {
        Lang.EN, Lang.FR -> entry.display(lang)
        Lang.JA -> fx(entry, lang)?.let { f -> f["r"]?.let { "${f.text} ($it)" } ?: f.text } ?: ""
        else -> fx(entry, lang)?.text ?: ""
    }

    /** Everything accepted when the word is typed: for Japanese also the rōmaji. */
    fun accepted(entry: Entry, lang: Lang): List<String> {
        val forms = forms(entry, lang)
        if (lang != Lang.JA) return forms
        val romaji = fx(entry, lang)?.get("r")?.split('/').orEmpty()
        return forms + romaji
    }

    fun mapOfTranslations() = translations

    companion object {
        fun load(): Lexicon {
            val stream = Lexicon::class.java.getResourceAsStream("/lexicon.txt")
                ?: error("lexicon.txt is missing from the resources")
            val base = parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val translations = HashMap<Lang, Map<String, Fx>>()
            for (lang in listOf(Lang.ES, Lang.DE, Lang.JA, Lang.RU)) {
                val res = Lexicon::class.java.getResourceAsStream("/lexicon_${lang.code}.txt") ?: continue
                translations[lang] = parseTranslations(res.bufferedReader(Charsets.UTF_8).use { it.readText() }, base)
            }
            return Lexicon(base.entries, translations)
        }

        /** `id|form/form|key=value|…`, one translation per line. */
        fun parseTranslations(text: String, base: Lexicon): Map<String, Fx> {
            val out = LinkedHashMap<String, Fx>()
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
                val p = line.split('|')
                val id = p[0]
                require(base.find(id) != null) { "Unknown word '$id' in translation: $line" }
                require(p.size >= 2 && p[1].isNotBlank()) { "Bad translation line: $line" }
                val opts = p.drop(2).filter { it.isNotEmpty() }
                    .associate { it.substringBefore('=') to it.substringAfter('=', "") }
                require(out.put(id, Fx(p[1].split('/').map { it.trim() }.filter { it.isNotEmpty() }, opts)) == null) {
                    "Duplicate translation of $id"
                }
            }
            return out
        }

        fun parse(text: String): Lexicon {
            val entries = ArrayList<Entry>()
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEachIndexed { index, line -> entries += parseLine(line, index) }
            val ids = HashSet<String>()
            for (e in entries) require(ids.add(e.id)) { "Duplicate lexicon entry ${e.id}" }
            return Lexicon(entries)
        }

        private fun alts(s: String) = s.split('/').map { it.trim() }.filter { it.isNotEmpty() }

        private fun parseLine(line: String, rank: Int): Entry {
            val p = line.split('|')
            fun bad(): Nothing = error("Bad lexicon line: $line")
            if (p.size < 4) bad()
            val level = Cefr.valueOf(p[1])
            return when (p[0]) {
                "N" -> {
                    if (p.size < 8) bad()
                    val en = alts(p[2])
                    val fr = alts(p[4])
                    Noun(
                        level, rank, en, if (p[3] == "-") en[0] + "s" else p[3],
                        fr, if (p[5] == "-") fr[0] + "s" else p[5],
                        if (p[6] == "m") Gender.M else Gender.F, p[7],
                    )
                }
                "A" -> {
                    if (p.size < 8) bad()
                    val (m, mVowel) = p[3].split('~').let { it[0] to it.getOrNull(1) }
                    val f = if (p[4] == "-") (if (m.endsWith("e")) m else m + "e") else p[4]
                    val mpl = if (p[5] == "-") (if (m.endsWith("s") || m.endsWith("x")) m else m + "s") else p[5]
                    val fpl = if (p[6] == "-") (if (f.endsWith("s")) f else f + "s") else p[6]
                    Adj(level, rank, alts(p[2]), m, mVowel, f, mpl, fpl, p[7] == "B")
                }
                "V" -> {
                    if (p.size < 9) bad()
                    val present = p[6].split(',')
                    if (present.size != 6) bad()
                    val opts = p.drop(9).associate { it.substringBefore('=') to it.substringAfter('=', "") }
                    fun pair(key: String): Pair<String, String>? =
                        opts[key]?.split(':')?.let { it[0] to it.getOrElse(1) { "" } }
                    val loc = pair("loc")
                    val inf = pair("inf")
                    Verb(
                        level, rank, alts(p[2]), p[3], p[4], alts(p[5]), present, p[7],
                        if (p[8] == "être") Aux.ETRE else Aux.AVOIR,
                        opts["obj"] ?: "", opts["subj"] ?: "PA",
                        loc?.first, loc?.second, inf?.first, inf?.second,
                        opts["modal"] == "1",
                    )
                }
                "D" -> Adverb(level, rank, alts(p[2]), alts(p[3]), p.getOrElse(4) { "pnf" })
                "W" -> Word(level, rank, alts(p[2]), alts(p[3]))
                else -> bad()
            }
        }
    }
}
