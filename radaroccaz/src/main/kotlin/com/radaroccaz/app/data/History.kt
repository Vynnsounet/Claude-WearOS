package com.radaroccaz.app.data

import kotlinx.serialization.Serializable

/** Where a piece of history comes from, from least to most trusted. */
@Serializable
enum class FactSource(val label: String, val trust: Int) {
    ANNONCE("Texte de l'annonce", 1),
    SITE("Données du site", 2),
    RAPPORT("Rapport d'historique", 3),
    VOUS("Saisi par vous", 4),
}

/** A value with its origin and, when read from text, the words it was read from. */
@Serializable
data class Fact<T>(val value: T, val source: FactSource, val evidence: String? = null)

@Serializable
enum class AccidentStatus(val label: String) {
    NONE("Aucun accident déclaré"),
    MINOR("Dégâts légers (carrosserie)"),
    DAMAGED("Accidenté / endommagé"),
    /** Procédure VE/VGA, VEI, épave: the car was declared economically or technically irreparable. */
    SEVERE("Sinistre grave (VE / VGA / VEI)"),
}

@Serializable
enum class CtStatus(val label: String) {
    OK("Contrôle technique OK"),
    MINOR_DEFECTS("CT avec défauts mineurs"),
    COUNTER_VISIT("Contre-visite"),
    TO_DO("CT à faire / absent"),
}

@Serializable
data class MileageReading(
    /** "yyyy-MM" or "yyyy-MM-dd". */
    val date: String,
    val km: Int,
    val source: FactSource,
    val label: String = "",
)

/** Everything known about the car's past. Every field is optional: sites and sellers give very different amounts. */
@Serializable
data class VehicleHistory(
    val owners: Fact<Int>? = null,
    val accident: Fact<AccidentStatus>? = null,
    val serviceRecords: Fact<Boolean>? = null,
    val technicalInspection: Fact<CtStatus>? = null,
    /** Date of the last inspection, "yyyy-MM" or "yyyy-MM-dd". */
    val lastInspection: Fact<String>? = null,
    val readings: List<MileageReading> = emptyList(),
    val imported: Fact<Boolean>? = null,
    /** False when the seller writes "kilométrage non garanti", a replaced odometer... */
    val mileageGuaranteed: Fact<Boolean>? = null,
    val warrantyMonths: Fact<Int>? = null,
    val nonSmoker: Fact<Boolean>? = null,
    /** Mechanical problems the seller mentions ("embrayage à prévoir"). */
    val issues: List<Fact<String>> = emptyList(),
    /** Recent work or good points ("distribution faite", "pneus neufs"). */
    val positives: List<Fact<String>> = emptyList(),
    /** Wording typical of scams (payment by money order, car abroad...). */
    val scamSignals: List<Fact<String>> = emptyList(),
    /** Link to a HistoVec report found in the listing. */
    val reportUrl: String? = null,
) {
    val isEmpty: Boolean get() = this == VehicleHistory()

    /** Combines two histories; for single values the most trusted source wins. */
    fun merge(other: VehicleHistory): VehicleHistory = VehicleHistory(
        owners = pick(owners, other.owners),
        accident = pick(accident, other.accident),
        serviceRecords = pick(serviceRecords, other.serviceRecords),
        technicalInspection = pick(technicalInspection, other.technicalInspection),
        lastInspection = pick(lastInspection, other.lastInspection),
        readings = (readings + other.readings).distinctBy { it.date.take(7) to it.km }.sortedBy { it.date },
        imported = pick(imported, other.imported),
        mileageGuaranteed = pick(mileageGuaranteed, other.mileageGuaranteed),
        warrantyMonths = pick(warrantyMonths, other.warrantyMonths),
        nonSmoker = pick(nonSmoker, other.nonSmoker),
        issues = (issues + other.issues).distinctBy { it.value },
        positives = (positives + other.positives).distinctBy { it.value },
        scamSignals = (scamSignals + other.scamSignals).distinctBy { it.value },
        reportUrl = other.reportUrl ?: reportUrl,
    )

    companion object {
        private fun <T> pick(a: Fact<T>?, b: Fact<T>?): Fact<T>? = when {
            a == null -> b
            b == null -> a
            b.source.trust >= a.source.trust -> b
            else -> a
        }
    }
}
