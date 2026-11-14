package com.radaroccaz.app.analysis

import com.radaroccaz.app.data.AccidentStatus
import com.radaroccaz.app.data.CtStatus
import com.radaroccaz.app.data.FactSource
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.MileageReading
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.VehicleHistory
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class Severity { CRITICAL, WARNING, INFO, POSITIVE }

enum class Topic(val label: String) {
    HISTORY("Historique"),
    MILEAGE("Kilométrage"),
    MECHANICS("Mécanique"),
    PRICE("Prix"),
    LISTING("Annonce"),
    SAFETY("Sécurité de l'achat"),
}

data class Finding(
    val severity: Severity,
    val topic: Topic,
    val title: String,
    val detail: String? = null,
    val source: FactSource? = null,
    /** The seller's words or the site's field this comes from. */
    val evidence: String? = null,
    /** Effect on the reliability score, for transparency. */
    val impact: Int = 0,
)

enum class Confidence(val label: String) {
    LOW("Peu d'informations"),
    MEDIUM("Informations partielles"),
    HIGH("Historique bien documenté"),
}

data class MileageCheck(
    /** Readings over time, the listing's current mileage last. */
    val readings: List<MileageReading>,
    val annualKm: Int?,
    val expectedAnnualKm: Int,
    /** A later reading is lower than an earlier one: the odometer may have been turned back. */
    val rollback: Boolean,
)

data class Analysis(
    /** 0–100: how much the car's past and the listing inspire confidence. */
    val reliability: Int,
    val confidence: Confidence,
    val history: VehicleHistory,
    val findings: List<Finding>,
    val mileage: MileageCheck,
    /** What to ask the seller before going further. */
    val questions: List<String>,
) {
    val critical get() = findings.filter { it.severity == Severity.CRITICAL }
    val warnings get() = findings.filter { it.severity == Severity.WARNING }
    val positives get() = findings.filter { it.severity == Severity.POSITIVE }

    /** Most important negative point, for compact displays. */
    val headline: Finding? get() = critical.firstOrNull() ?: warnings.firstOrNull()
}

/**
 * Turns everything known about a listing into findings and a reliability score.
 * The score starts at a neutral 70 (nothing known) and each fact moves it; every
 * finding records its effect so the user can see why.
 */
object VehicleAnalysis {
    const val BASELINE = 70

    /** Typical yearly mileage in France by energy. */
    fun expectedAnnualKm(fuel: Fuel?): Int = when (fuel) {
        Fuel.DIESEL -> 17_000
        Fuel.ESSENCE -> 11_000
        Fuel.GPL -> 14_000
        Fuel.ELECTRIQUE -> 12_000
        Fuel.HYBRIDE, Fuel.HYBRIDE_RECHARGEABLE -> 13_000
        null -> 13_000
    }

    fun analyze(
        listing: Listing,
        user: VehicleHistory? = null,
        dealPercent: Int? = null,
        today: LocalDate = LocalDate.now(),
    ): Analysis {
        val h = if (user == null) listing.history else listing.history.merge(user)
        val f = mutableListOf<Finding>()
        fun add(sev: Severity, topic: Topic, title: String, impact: Int, detail: String? = null, source: FactSource? = null, evidence: String? = null) {
            f += Finding(sev, topic, title, detail, source, evidence, impact)
        }
        val ageYears = listing.year?.let { y ->
            (ChronoUnit.MONTHS.between(YearMonth.of(y, 7), YearMonth.from(today)) / 12.0).coerceAtLeast(0.5)
        }

        // ---- Owners ----
        h.owners?.let { o ->
            when {
                o.value <= 1 -> add(Severity.POSITIVE, Topic.HISTORY, "Première main", 12,
                    "Un seul propriétaire : usage et entretien plus faciles à retracer.", o.source, o.evidence)
                o.value == 2 -> add(Severity.INFO, Topic.HISTORY, "2 propriétaires", 4, null, o.source, o.evidence)
                o.value == 3 -> add(Severity.WARNING, Topic.HISTORY, "3 propriétaires", -6,
                    "Chaque changement de main rend l'historique plus difficile à vérifier.", o.source, o.evidence)
                else -> add(Severity.WARNING, Topic.HISTORY, "${o.value} propriétaires", -14,
                    "Beaucoup de changements de main pour une voiture d'occasion.", o.source, o.evidence)
            }
            if (ageYears != null && ageYears < 3 && o.value >= 3) {
                add(Severity.WARNING, Topic.HISTORY, "Reventes rapprochées", -6,
                    "${o.value} propriétaires en moins de 3 ans : demandez pourquoi.", o.source)
            }
        }

        // ---- Accidents ----
        h.accident?.let { a ->
            val documented = a.source != FactSource.ANNONCE
            when (a.value) {
                AccidentStatus.NONE -> add(Severity.POSITIVE, Topic.HISTORY, "Aucun accident déclaré", if (documented) 10 else 5,
                    if (documented) null else "Déclaration du vendeur : vérifiez avec le rapport HistoVec.", a.source, a.evidence)
                AccidentStatus.MINOR -> add(Severity.WARNING, Topic.HISTORY, "Dégâts de carrosserie signalés", -8,
                    "Faites vérifier la qualité de la réparation et l'alignement des éléments.", a.source, a.evidence)
                AccidentStatus.DAMAGED -> add(Severity.CRITICAL, Topic.HISTORY, "Véhicule accidenté", -25,
                    "Demandez le rapport d'expertise et les factures de réparation.", a.source, a.evidence)
                AccidentStatus.SEVERE -> add(Severity.CRITICAL, Topic.HISTORY, "Sinistre grave (VE / VGA / VEI)", -40,
                    "Véhicule déclaré gravement endommagé : il ne peut circuler qu'après réparation certifiée par un expert. Exigez le rapport de levée de procédure.",
                    a.source, a.evidence)
            }
        }

        // ---- Maintenance ----
        h.serviceRecords?.let { s ->
            if (s.value) add(Severity.POSITIVE, Topic.HISTORY, "Entretien suivi", 10, "Carnet ou factures disponibles.", s.source, s.evidence)
            else add(Severity.WARNING, Topic.HISTORY, "Pas d'historique d'entretien", -10, null, s.source, s.evidence)
        }

        // ---- Technical inspection ----
        val needsCt = ageYears != null && ageYears > 4
        h.technicalInspection?.let { ct ->
            when (ct.value) {
                CtStatus.OK -> add(Severity.POSITIVE, Topic.HISTORY, "Contrôle technique OK", 5, null, ct.source, ct.evidence)
                CtStatus.MINOR_DEFECTS -> add(Severity.INFO, Topic.HISTORY, "CT avec défaillances mineures", 0,
                    "Pas de contre-visite, mais demandez le procès-verbal pour voir les points relevés.", ct.source, ct.evidence)
                CtStatus.COUNTER_VISIT -> add(Severity.WARNING, Topic.HISTORY, "Contre-visite au contrôle technique", -12,
                    "Des réparations sont obligatoires. Qui les paie ?", ct.source, ct.evidence)
                CtStatus.TO_DO -> add(Severity.WARNING, Topic.HISTORY, "Contrôle technique à faire", -8,
                    if (needsCt) "Pour une voiture de plus de 4 ans, le vendeur doit fournir un CT de moins de 6 mois." else null,
                    ct.source, ct.evidence)
            }
        }
        h.lastInspection?.let { last ->
            val months = runCatching { ChronoUnit.MONTHS.between(YearMonth.parse(last.value.take(7)), YearMonth.from(today)) }.getOrNull()
            if (months != null && needsCt && months > 6) {
                add(Severity.WARNING, Topic.HISTORY, "CT de plus de 6 mois", -4,
                    "Dernier contrôle il y a $months mois : un CT de moins de 6 mois est obligatoire pour la vente.", last.source, last.evidence)
            }
        }

        // ---- Origin, warranty, smoker ----
        h.imported?.takeIf { it.value }?.let {
            add(Severity.INFO, Topic.HISTORY, "Véhicule importé", -3,
                "L'historique à l'étranger n'apparaît pas dans HistoVec : demandez les documents du pays d'origine.", it.source, it.evidence)
        }
        h.warrantyMonths?.let { w ->
            if (w.value > 0) add(Severity.POSITIVE, Topic.SAFETY, "Garantie ${w.value} mois", 4, null, w.source, w.evidence)
            else if (listing.sellerType == SellerType.PRO) add(Severity.INFO, Topic.SAFETY, "Vendue sans garantie commerciale", -2,
                "La garantie légale de conformité s'applique quand même chez un professionnel.", w.source, w.evidence)
        }
        h.nonSmoker?.takeIf { it.value }?.let { add(Severity.POSITIVE, Topic.HISTORY, "Non-fumeur", 2, null, it.source, it.evidence) }
        if (h.reportUrl != null) {
            add(Severity.POSITIVE, Topic.HISTORY, "Rapport HistoVec fourni", 6,
                "Le rapport officiel donne propriétaires, sinistres et relevés de CT : ouvrez-le et reportez-les ici.")
        }

        // ---- Mechanics ----
        var issuePenalty = 0
        for (issue in h.issues) {
            val critical = issue.value in HistoryText.CRITICAL_ISSUES
            val impact = if (critical) -30 else (-10).coerceAtLeast(-30 - issuePenalty)
            issuePenalty += impact
            add(if (critical) Severity.CRITICAL else Severity.WARNING, Topic.MECHANICS, issue.value, impact,
                "Faites chiffrer la réparation et déduisez-la du prix.", issue.source, issue.evidence)
        }
        h.positives.take(4).forEachIndexed { i, p ->
            add(Severity.POSITIVE, Topic.MECHANICS, p.value, if (i < 3) 3 else 0, null, p.source, p.evidence)
        }

        // ---- Mileage ----
        val mileage = checkMileage(listing, h, today, ageYears)
        h.mileageGuaranteed?.let { g ->
            if (!g.value) add(Severity.CRITICAL, Topic.MILEAGE, "Kilométrage non garanti", -40,
                "Le kilométrage réel est inconnu : la cote et l'usure ne peuvent pas être estimées.", g.source, g.evidence)
        }
        if (mileage.rollback) {
            val r = mileage.readings
            val bad = r.zipWithNext().first { (a, b) -> b.km < a.km - 1_000 }
            add(Severity.CRITICAL, Topic.MILEAGE, "Kilométrage incohérent", -50,
                "${Format.km(bad.first.km)} relevés en ${monthLabel(bad.first.date)}, puis ${Format.km(bad.second.km)} en ${monthLabel(bad.second.date)} : le compteur a pu être modifié.",
                bad.second.source)
        }
        mileage.annualKm?.let { annual ->
            val ratio = annual.toDouble() / mileage.expectedAnnualKm
            val yearly = "≈ ${Format.km(annual)}/an (moyenne ~${Format.km(mileage.expectedAnnualKm)})"
            when {
                ratio < 0.35 && (ageYears ?: 0.0) >= 3 -> add(Severity.WARNING, Topic.MILEAGE, "Kilométrage très faible pour l'âge", -8,
                    "$yearly. Possible, mais à prouver par les relevés de CT et les factures.")
                ratio < 0.7 -> add(Severity.POSITIVE, Topic.MILEAGE, "Peu roulé", 4, yearly)
                ratio > 1.8 -> add(Severity.WARNING, Topic.MILEAGE, "Usage intensif", -5,
                    "$yearly : usure plus rapide (embrayage, suspensions, freins).")
                else -> add(Severity.INFO, Topic.MILEAGE, "Kilométrage cohérent avec l'âge", 0, yearly)
            }
        }
        if (mileage.readings.size >= 2 && !mileage.rollback) {
            add(Severity.POSITIVE, Topic.MILEAGE, "Relevés kilométriques cohérents", 5,
                "${mileage.readings.size} relevés qui progressent normalement.")
        }

        // ---- Price ----
        if (dealPercent != null) {
            when {
                dealPercent <= -30 -> add(
                    if (h.scamSignals.isNotEmpty()) Severity.CRITICAL else Severity.WARNING, Topic.PRICE,
                    "Prix anormalement bas", -10,
                    "${-dealPercent} % sous le marché : vice caché, sinistre non déclaré ou arnaque. Ne versez rien avant d'avoir vu la voiture et la carte grise.",
                )
                dealPercent <= -8 -> add(Severity.POSITIVE, Topic.PRICE, "Prix sous le marché", 0, "${-dealPercent} % sous les annonces comparables.")
                dealPercent >= 15 -> add(Severity.INFO, Topic.PRICE, "Prix au-dessus du marché", 0,
                    "+$dealPercent % : négociez, ou vérifiez ce qui le justifie (options, faible kilométrage, garantie).")
            }
        }

        // ---- Scam wording ----
        if (h.scamSignals.isNotEmpty()) {
            add(Severity.CRITICAL, Topic.SAFETY, "Signes d'arnaque", -60,
                h.scamSignals.joinToString(" · ") { it.value } +
                    ". Ne payez jamais à distance, par coupon ou mandat, et voyez la voiture avant tout versement.",
                FactSource.ANNONCE, h.scamSignals.first().evidence)
        }

        // ---- Seller ----
        when (listing.sellerType) {
            SellerType.PRO -> add(Severity.POSITIVE, Topic.SAFETY, "Vendeur professionnel", 3,
                "Garantie légale de conformité et recours plus simples en cas de problème.")
            SellerType.PARTICULIER -> add(Severity.INFO, Topic.SAFETY, "Vente entre particuliers", 0,
                "Seule la garantie des vices cachés s'applique : faites contrôler la voiture avant d'acheter.")
            null -> Unit
        }

        // ---- Listing quality ----
        when (listing.imageUrls.size) {
            0 -> add(Severity.WARNING, Topic.LISTING, "Aucune photo", -5)
            in 1..3 -> add(Severity.INFO, Topic.LISTING, "Peu de photos", -2, "Demandez des photos de l'intérieur, du compteur et des défauts.")
        }
        if (listing.detailsLoaded && listing.description.length < 100) {
            add(Severity.INFO, Topic.LISTING, "Description très courte", -3)
        }
        if (listing.year == null || listing.mileageKm == null) {
            add(Severity.WARNING, Topic.LISTING, "Année ou kilométrage non indiqué", -5)
        }

        val reliability = (BASELINE + f.sumOf { it.impact }).coerceIn(0, 100)
        val known = listOfNotNull(h.owners, h.accident, h.serviceRecords, h.technicalInspection, h.mileageGuaranteed).size +
            (if (h.readings.isNotEmpty()) 1 else 0)
        val documented = listOfNotNull(h.owners, h.accident, h.serviceRecords, h.technicalInspection)
            .any { it.source == FactSource.RAPPORT || it.source == FactSource.VOUS } || h.readings.any { it.source != FactSource.ANNONCE }
        val confidence = when {
            known >= 4 || (documented && known >= 2) -> Confidence.HIGH
            known >= 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val order = listOf(Severity.CRITICAL, Severity.WARNING, Severity.POSITIVE, Severity.INFO)
        return Analysis(
            reliability = reliability,
            confidence = confidence,
            history = h,
            findings = f.sortedBy { order.indexOf(it.severity) },
            mileage = mileage,
            questions = questions(listing, h, ageYears),
        )
    }

    private fun checkMileage(listing: Listing, h: VehicleHistory, today: LocalDate, ageYears: Double?): MileageCheck {
        val now = (listing.publishedAt?.take(7) ?: today.toString().take(7))
        val current = listing.mileageKm?.let { MileageReading(now, it, FactSource.SITE, "Annonce") }
        val readings = (h.readings + listOfNotNull(current))
            .sortedWith(compareBy<MileageReading> { it.date.take(7) }.thenBy { it.km })
        var max = 0
        var rollback = false
        for (r in readings) {
            if (r.km < max - 1_000) rollback = true
            max = maxOf(max, r.km)
        }
        val annual = if (listing.mileageKm != null && ageYears != null) (listing.mileageKm / ageYears).roundToInt() else null
        return MileageCheck(readings, annual, expectedAnnualKm(listing.fuel), rollback)
    }

    private fun questions(l: Listing, h: VehicleHistory, ageYears: Double?): List<String> = buildList {
        if (h.reportUrl == null) add("Pouvez-vous me partager le rapport HistoVec ? (gratuit, sur histovec.interieur.gouv.fr)")
        if (h.owners == null) add("Combien de propriétaires la voiture a-t-elle eus ?")
        if (h.accident == null) add("La voiture a-t-elle déjà été accidentée ou réparée en carrosserie ?")
        if (h.serviceRecords == null) add("Avez-vous le carnet d'entretien et les factures ?")
        if (h.technicalInspection == null && (ageYears ?: 0.0) > 4) add("Avez-vous un contrôle technique de moins de 6 mois ? Avec quels défauts ?")
        val km = l.mileageKm ?: 0
        if (km > 100_000 && h.positives.none { it.value.startsWith("Distribution") }) {
            add("La distribution a-t-elle été remplacée ? À quel kilométrage ?")
        }
        if (l.fuel == Fuel.DIESEL && km > 120_000) add("Le filtre à particules et les injecteurs ont-ils été entretenus ou remplacés ?")
        if (l.gearbox == Gearbox.AUTOMATIQUE && km > 80_000) add("La vidange de la boîte automatique a-t-elle été faite ?")
        if (l.fuel == Fuel.ELECTRIQUE || l.fuel == Fuel.HYBRIDE_RECHARGEABLE) add("Quel est l'état de santé de la batterie (certificat SoH) ?")
        if (h.imported?.value == true) add("Avez-vous le certificat de conformité (COC) et l'historique du pays d'origine ?")
        h.issues.forEach { add("« ${it.value} » : avez-vous un devis de réparation ?") }
        add("Puis-je faire un essai et la faire inspecter par un garage de mon choix ?")
    }

    private val monthNames = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

    fun monthLabel(date: String): String {
        val y = date.take(4)
        val m = date.drop(5).take(2).toIntOrNull()
        return if (m != null && m in 1..12) "${monthNames[m - 1]} $y" else y
    }
}
