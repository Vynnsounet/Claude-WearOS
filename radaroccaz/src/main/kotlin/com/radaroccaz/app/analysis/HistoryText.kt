package com.radaroccaz.app.analysis

import com.radaroccaz.app.data.AccidentStatus
import com.radaroccaz.app.data.CtStatus
import com.radaroccaz.app.data.Fact
import com.radaroccaz.app.data.FactSource
import com.radaroccaz.app.data.MileageReading
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleHistory
import java.text.Normalizer

/**
 * Reads the car's history from the seller's own words: owners, accidents, maintenance,
 * inspection, mileage readings, problems and scam wording. Matching ignores case and
 * accents, and a statement preceded by a negation ("jamais accidentée", "aucun frais
 * à prévoir") is read as such.
 */
object HistoryText {

    private val SRC = FactSource.ANNONCE

    /** Lower case without accents, keeping every character at its position (so evidence can be cut from the original). */
    internal fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val base = when (c) {
                'œ', 'Œ' -> 'o'
                'æ', 'Æ' -> 'a'
                '’', '‘' -> '\''
                ' ', ' ' -> ' '
                else -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first()
            }
            sb.append(base.lowercaseChar())
        }
        return sb.toString()
    }

    private val negation = Regex("(?<![a-z])(non|jamais|aucune?s?|pas|sans|ni|zero)(?![a-z])")

    /** A negation word shortly before [start], in the same sentence. */
    private fun negated(folded: String, start: Int): Boolean {
        var from = (start - 32).coerceAtLeast(0)
        val stop = folded.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ';', ','), start - 1)
        if (stop >= from) from = stop + 1
        // "jamais accidentée mais un choc": a contrast word ends the negation's reach.
        val window = folded.substring(from, start)
        val contrast = Regex("(?<![a-z])(mais|sauf|par contre|cependant|hormis)(?![a-z])").findAll(window).lastOrNull()
        return negation.containsMatchIn(if (contrast != null) window.substring(contrast.range.last + 1) else window)
    }

    private fun evidence(original: String, range: IntRange): String {
        val from = (range.first - 12).coerceAtLeast(0).let { s -> original.lastIndexOf(' ', s).takeIf { it >= 0 && range.first - it < 30 } ?: s }
        val to = (range.last + 13).coerceAtMost(original.length).let { e -> original.indexOf(' ', e).takeIf { it in e..(range.last + 30) } ?: e }
        return original.substring(from, to).trim().replace(Regex("\\s+"), " ")
    }

    private fun rx(p: String) = Regex(p)

    private val wordNumbers = mapOf("un" to 1, "une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5, "six" to 6)

    // ---- Owners ----
    private val firstHand = rx("premiere main|1 ?(?:ere|re|er) ?main|(?:un|1) seul (?:et unique )?proprietaire|une seule main|premier proprietaire|1 proprietaire(?!s)")
    private val nOwners = rx("(?<![\\d.])(\\d|deux|trois|quatre|cinq|six) ?(?:proprietaires|mains)(?![a-z])")
    private val nthHand = rx("(?<![\\d.])(\\d) ?(?:e|eme|nd|nde|ieme) main|(seconde|deuxieme|troisieme|quatrieme) main")

    // ---- Accidents ----
    private val severe = rx("procedure (?:ve|vge|vei|rsv)(?![a-z])|(?<![a-z])(?:vge|vga|vei)(?![a-z])|declaree? ve(?![a-z])|vehicule endommage|(?<![a-z])epave|(?:economiquement|techniquement) irreparable")
    private val accident = rx("(?<![a-z])accident(?:e|ee|es|ees|ee?s)?(?![a-z])|sinistre(?:e)?(?![a-z])")
    private val minor = rx("(?<!pare )(?<!pare-)(?<!pare)(?<![a-z])chocs?(?![a-z])|carrosserie a (?:refaire|reprendre|revoir)|(?<![a-z])bosses?(?![a-z])|accroch(?:age|e)")
    private val toRepair = rx("accidentee? a reparer|a reparer suite")

    // ---- Maintenance ----
    private val service = rx("carnet d'?entretien|carnet (?:complet|tamponne|a jour)|entretiens? (?:suivis?|constructeur|a jour|reguliers?|chez (?:le )?concessionnaire|en concession|fait chez)|historique d'?entretien|(?:toutes les |avec )?factures d'?entretien|toutes les factures|factures a l'appui|revisions? (?:a jour|suivies?|constructeur)")

    // ---- Technical inspection ----
    private val ctWord = "(?:(?<![a-z])ct(?![a-z])|controle technique)"
    private val ctOk = rx("$ctWord\\s*(?:ok|vierge|valide|favorable|a jour|sans (?:aucun )?defauts?|r\\.?a\\.?s|passe|recent|neuf|fait|de moins de \\d+ mois)")
    private val ctMinor = rx("defaillances? mineures?|defauts? mineurs?")
    private val counterVisit = rx("contre[- ]?visite")
    private val ctToDo = rx("$ctWord a (?:faire|prevoir|refaire)|sans $ctWord|pas de $ctWord")
    private val month = "(janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre)"
    private val dateRx = "(\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}/\\d{4}|\\d{1,2}-\\d{4}|$month\\s+(?:\\d{1,2}\\s+)?\\d{4}|(?<![\\d])20\\d\\d(?![\\d]))"
    private val ctDate = rx("$ctWord[^.\\n]{0,30}?$dateRx")
    private val ctReading = rx("$ctWord[^.\\n]{0,30}?$dateRx[^.\\n]{0,20}?(?<![\\d])(\\d{1,3}(?:[ .]?\\d{3})+|\\d{4,6}) ?(?:km|kms|kilometres)(?![a-z])")

    // ---- Mileage ----
    private val notGuaranteed = rx("(?:kilometrage|km|kms|kilometres) (?:non garantis?|non certifies?|inconnus?)|compteur (?:change|remplace|bloque|hs|defectueux|en panne)")
    private val guaranteed = rx("(?<!non )(?:kilometrage|km|kms) (?:certifies?|garantis?|reels?|d'origine)")

    // ---- Origin, warranty, smoker ----
    private val imported = rx("(?<![a-z])import(?:e|ee|ation)?(?![a-z])|origine (?:etrangere|allemande|belge|espagnole|italienne)|vehicule etranger|carte grise etrangere|plaques? etrangeres?")
    private val french = rx("origine france|vehicule francais|premiere (?:mise en circulation|immatriculation) en france")
    private val warrantyMonths = rx("garantie (?:de )?(\\d{1,2}) ?mois|garantie (?:de )?(\\d) ?ans?")
    private val noWarranty = rx("sans garantie")
    private val nonSmoker = rx("non[- ]?fumeur")

    private data class Rule(val regex: Regex, val label: String, val checkNegation: Boolean = true)

    /** Problems that cost money; "critical" ones make the car unusable as is. */
    private val issueRules = listOf(
        Rule(rx("moteur (?:hs|a refaire|a revoir|casse|bruyant|claque|fume)"), "Moteur à revoir"),
        Rule(rx("boite (?:de vitesses? |auto(?:matique)? )?(?:hs|a revoir|a changer|craque|qui craque|defectueuse|patine)"), "Boîte de vitesses à revoir"),
        Rule(rx("embrayage (?:hs|a (?:changer|prevoir|refaire|revoir)|patine|fatigue|use)"), "Embrayage à changer"),
        Rule(rx("voyants? (?:moteur |d'?alerte |abs |airbag |orange )?(?:allumes?|s'allume|reste allume|au tableau)"), "Voyant allumé"),
        Rule(rx("fuites? (?:d'?huile|d'?eau|de liquide)|(?<![a-z])fuites?(?![a-z])"), "Fuite"),
        Rule(rx("turbo (?:hs|a changer|siffle|fatigue|a revoir)"), "Turbo à revoir"),
        Rule(rx("joint de culasse"), "Joint de culasse"),
        Rule(rx("(?:fap|filtre a particules) (?:bouche|hs|colmate|a nettoyer|a changer|a revoir)"), "Filtre à particules à revoir"),
        Rule(rx("(?:courroie de )?distribution a (?:faire|changer|prevoir|refaire)"), "Distribution à faire"),
        Rule(rx("freins? a (?:changer|faire|prevoir)|plaquettes a changer|disques a changer"), "Freins à faire"),
        Rule(rx("pneus? a (?:changer|prevoir)"), "Pneus à changer"),
        Rule(rx("ne demarre (?:pas|plus)|en panne|non roulante?|ne roule (?:pas|plus)"), "Ne roule pas", checkNegation = false),
        Rule(rx("pour pieces|vendue? pour piece"), "Vendue pour pièces", checkNegation = false),
        Rule(rx("(?<!pas )(?<!aucun )a reparer|travaux a prevoir|petits travaux|frais a prevoir|reparations? a prevoir"), "Travaux à prévoir"),
        Rule(rx("vendue? en l'?etat"), "Vendue en l'état", checkNegation = false),
        Rule(rx("bruit (?:au|a l'|moteur|suspect|de (?:roulement|boite|train))|claquement"), "Bruit signalé"),
        Rule(rx("rouille|corrosion|perfor(?:ation|e)"), "Rouille"),
    )
    val CRITICAL_ISSUES = setOf("Ne roule pas", "Vendue pour pièces", "Moteur à revoir", "Joint de culasse")

    private val positiveRules = listOf(
        Rule(rx("distribution (?:faite|changee|refaite|neuve|remplacee|ok)|kit distribution (?:neuf|change)"), "Distribution faite", false),
        Rule(rx("embrayage (?:neuf|change|refait|remplace)"), "Embrayage refait", false),
        Rule(rx("pneus? (?:neufs?|recents?)"), "Pneus récents", false),
        Rule(rx("freins? (?:neufs?|refaits?)|plaquettes (?:et disques )?neu(?:ves|fs)|disques (?:et plaquettes )?neufs"), "Freins refaits", false),
        Rule(rx("revision (?:faite|recente|a jour|complete)|vidange (?:faite|recente)|entretien recent|revisee?(?![a-z])"), "Révision récente", false),
        Rule(rx("(?:garee?|dort|stationnee?) (?:en|dans un|au|dans le|dans son) (?:box|garage)"), "Gardée au garage", false),
        Rule(rx("batterie neuve"), "Batterie neuve", false),
        Rule(rx("aucun(?:s)? (?:frais|travaux)(?: a prevoir)?|rien a prevoir|aucune? reparation a prevoir"), "Aucun frais à prévoir", false),
    )

    private val scamRules = listOf(
        Rule(rx("mandat cash|western union|moneygram|transcash|(?<![a-z])pcs(?![a-z])|paysafecard|neosurf"), "Paiement par coupon ou mandat", false),
        Rule(rx("(?:situee?|se trouve|actuellement|est) (?:a l'etranger|en (?:espagne|belgique|allemagne|italie|angleterre|irlande|ecosse|suisse))"), "Voiture située à l'étranger", false),
        Rule(rx("livraison (?:par transporteur|a domicile|assuree|gratuite)|envoyee? par transporteur|transporteur agree"), "Livraison par transporteur", false),
        Rule(rx("(?:je suis|suis) (?:militaire|en mission|a l'etranger|expatrie|muté)"), "Vendeur absent / à l'étranger", false),
        Rule(rx("contact(?:ez)?(?:-moi)? (?:uniquement|seulement|exclusivement) par (?:mail|e-mail|email|whatsapp)"), "Contact hors plateforme uniquement", false),
        Rule(rx("paiement (?:avant|a la commande|d'avance)|acompte (?:avant|pour reserver)|virement avant"), "Paiement demandé à l'avance", false),
    )

    private val distributionAt = rx("distribution (?:faite|changee|refaite|remplacee)[^.\\n]{0,12}?(?:a|aux) (\\d{1,3}(?:[ .]?\\d{3})+|\\d{4,6}) ?(?:km|kms)")
    private val histovecUrl = Regex("https?://histovec\\.interieur\\.gouv\\.fr/[^\\s\"'<>)]+", RegexOption.IGNORE_CASE)

    fun extract(vararg texts: String?): VehicleHistory {
        val original = texts.filterNotNull().filter { it.isNotBlank() }.joinToString(". \n")
        if (original.isBlank()) return VehicleHistory()
        val t = fold(original)

        // Owners
        var owners: Fact<Int>? = null
        firstHand.find(t)?.let { m -> owners = Fact(1, SRC, evidence(original, m.range)) }
        if (owners == null) {
            (nOwners.find(t) ?: nthHand.find(t))?.let { m ->
                val raw = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
                val n = raw.toIntOrNull() ?: wordNumbers[raw] ?: when (raw) {
                    "seconde", "deuxieme" -> 2; "troisieme" -> 3; "quatrieme" -> 4; else -> null
                }
                if (n != null && n in 1..15) owners = Fact(n, SRC, evidence(original, m.range))
            }
        }

        // Accident: the worst positive statement wins; otherwise a denial means "none".
        var worst: Pair<AccidentStatus, IntRange>? = null
        var denial: IntRange? = null
        fun consider(status: AccidentStatus, regex: Regex) {
            regex.findAll(t).forEach { m ->
                if (negated(t, m.range.first)) denial = denial ?: m.range
                else if (worst == null || status.ordinal > worst!!.first.ordinal) worst = status to m.range
            }
        }
        consider(AccidentStatus.SEVERE, severe)
        consider(AccidentStatus.DAMAGED, accident)
        consider(AccidentStatus.DAMAGED, toRepair)
        consider(AccidentStatus.MINOR, minor)
        val accidentFact = worst?.let { (s, r) -> Fact(s, SRC, evidence(original, r)) }
            ?: denial?.let { Fact(AccidentStatus.NONE, SRC, evidence(original, it)) }

        // Maintenance
        val serviceFact = service.find(t)?.let { m -> Fact(!negated(t, m.range.first), SRC, evidence(original, m.range)) }

        // Inspection
        val ct = when {
            counterVisit.find(t)?.takeIf { !negated(t, it.range.first) } != null ->
                counterVisit.find(t)!!.let { Fact(CtStatus.COUNTER_VISIT, SRC, evidence(original, it.range)) }
            ctToDo.find(t) != null -> ctToDo.find(t)!!.let { Fact(CtStatus.TO_DO, SRC, evidence(original, it.range)) }
            ctMinor.find(t) != null && ctOk.find(t) == null -> ctMinor.find(t)!!.let { Fact(CtStatus.MINOR_DEFECTS, SRC, evidence(original, it.range)) }
            ctOk.find(t) != null -> ctOk.find(t)!!.let { Fact(CtStatus.OK, SRC, evidence(original, it.range)) }
            counterVisit.find(t) != null -> counterVisit.find(t)!!.let { Fact(CtStatus.OK, SRC, evidence(original, it.range)) } // "sans contre-visite"
            else -> null
        }
        val lastCt = ctDate.find(t)?.let { m -> parseDate(m.groupValues[1])?.let { Fact(it, SRC, evidence(original, m.range)) } }

        // Readings: "CT de mars 2024 à 78 000 km"
        val readings = ctReading.findAll(t).mapNotNull { m ->
            val date = parseDate(m.groupValues[1]) ?: return@mapNotNull null
            // Groups: 1 = date, 2 = month name inside the date, 3 = mileage.
            val km = Text.digits(m.groupValues[3]) ?: return@mapNotNull null
            if (km !in 100..999_999) null else MileageReading(date, km, SRC, "Contrôle technique (annonce)")
        }.toList()

        // Mileage guarantee
        val guarantee = notGuaranteed.find(t)?.let { Fact(false, SRC, evidence(original, it.range)) }
            ?: guaranteed.find(t)?.let { Fact(true, SRC, evidence(original, it.range)) }

        // Origin
        val origin = imported.find(t)?.let { m -> Fact(!negated(t, m.range.first), SRC, evidence(original, m.range)) }
            ?: french.find(t)?.let { Fact(false, SRC, evidence(original, it.range)) }

        // Warranty
        val warranty = warrantyMonths.find(t)?.let { m ->
            val months = m.groupValues[1].toIntOrNull() ?: m.groupValues[2].toIntOrNull()?.times(12)
            months?.let { Fact(it, SRC, evidence(original, m.range)) }
        } ?: noWarranty.find(t)?.let { Fact(0, SRC, evidence(original, it.range)) }

        val smoker = nonSmoker.find(t)?.let { Fact(true, SRC, evidence(original, it.range)) }

        fun facts(rules: List<Rule>) = rules.mapNotNull { rule ->
            rule.regex.findAll(t).firstOrNull { !rule.checkNegation || !negated(t, it.range.first) }
                ?.let { Fact(rule.label, SRC, evidence(original, it.range)) }
        }
        val positives = facts(positiveRules).toMutableList()
        distributionAt.find(t)?.let { m ->
            Text.digits(m.groupValues[1])?.let { km ->
                positives.removeAll { it.value == "Distribution faite" }
                positives += Fact("Distribution faite à ${com.radaroccaz.app.data.Format.km(km)}", SRC, evidence(original, m.range))
            }
        }
        val reportUrl = histovecUrl.find(original)?.value
        if (reportUrl == null && "histovec" in t) {
            positives += Fact("Rapport HistoVec proposé", SRC, evidence(original, t.indexOf("histovec").let { it until it + 8 }))
        }

        return VehicleHistory(
            owners = owners,
            accident = accidentFact,
            serviceRecords = serviceFact,
            technicalInspection = ct,
            lastInspection = lastCt,
            readings = readings,
            imported = origin,
            mileageGuaranteed = guarantee,
            warrantyMonths = warranty,
            nonSmoker = smoker,
            issues = facts(issueRules),
            positives = positives,
            scamSignals = facts(scamRules),
            reportUrl = reportUrl,
        )
    }

    private val months = listOf("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre", "novembre", "decembre")

    /** "03/2024", "12/03/2024", "mars 2024", "2024" → "2024-03" (a year alone becomes mid-year). */
    fun parseDate(raw: String): String? {
        val s = fold(raw.trim())
        Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{2,4})$").find(s)?.let { m ->
            val y = m.groupValues[3].let { if (it.length == 2) "20$it" else it }.toInt()
            val mo = m.groupValues[2].toInt()
            return if (mo in 1..12 && y in 1990..2100) "%04d-%02d".format(y, mo) else null
        }
        Regex("^(\\d{1,2})[/-](\\d{4})$").find(s)?.let { m ->
            val mo = m.groupValues[1].toInt()
            return if (mo in 1..12) "${m.groupValues[2]}-%02d".format(mo) else null
        }
        Regex("^([a-z]+)\\s+(?:\\d{1,2}\\s+)?(\\d{4})$").find(s)?.let { m ->
            val mo = months.indexOf(m.groupValues[1]) + 1
            return if (mo > 0) "${m.groupValues[2]}-%02d".format(mo) else null
        }
        Regex("^(20\\d\\d)$").find(s)?.let { return "${it.groupValues[1]}-06" }
        return null
    }
}
