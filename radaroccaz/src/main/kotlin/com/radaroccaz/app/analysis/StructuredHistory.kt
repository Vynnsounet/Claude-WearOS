package com.radaroccaz.app.analysis

import com.radaroccaz.app.data.AccidentStatus
import com.radaroccaz.app.data.Fact
import com.radaroccaz.app.data.FactSource
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleHistory
import com.radaroccaz.app.sources.JsonTools
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * History the sites publish as data (number of previous owners, accident-free flag,
 * full service history...). Field names differ between sites and change over time, so
 * keys are matched loosely: "numberOfPreviousOwners", "previous_owners" and
 * "nbProprietaires" all count as owners. Lists of {key, value} attributes
 * (Leboncoin) are read the same way.
 */
object StructuredHistory {
    private val SRC = FactSource.SITE

    private fun norm(key: String) = key.lowercase().filter { it.isLetterOrDigit() }

    private val ownerKeys = setOf(
        "previousowners", "numberofpreviousowners", "previousownercount", "ownerscount", "ownercount",
        "numberofowners", "nbowners", "owners", "nombreproprietaires", "nbproprietaires", "proprietaires",
        "vehicleowners", "vehiclenumberofowners",
    )
    private val firstHandKeys = setOf("firsthand", "isfirsthand", "premieremain", "firstowner", "vehiclefirsthand")
    private val accidentKeys = setOf("hadaccident", "accidented", "isdamaged", "damaged", "accident", "hasaccident", "isaccidented")
    private val accidentFreeKeys = setOf("accidentfree", "noaccident", "isaccidentfree", "nonaccidente")
    private val damageKeys = setOf("vehicledamage", "damagestatus", "vehiclecondition", "etatvehicule")
    private val serviceKeys = setOf("fullservicehistory", "hasfullservicehistory", "servicehistory", "servicebook", "carnetentretien", "maintenancebook", "hasservicebook")
    private val smokerKeys = setOf("nonsmoking", "nonsmoker", "nonfumeur", "isnonsmoking", "nonsmokingvehicle")
    private val warrantyKeys = setOf("warranty", "warrantymonths", "warrantyduration", "garantie", "warrantyinmonths")
    private val ctKeys = setOf("lasttechnicalinspection", "technicalinspection", "vehicletechnicalinspectiona", "controletechnique", "ctdate", "generalinspection", "lastinspection")
    private val originKeys = setOf("vehicleorigin", "origin", "origine", "imported", "isimported", "import")

    fun fromJson(vararg roots: JsonElement?): VehicleHistory {
        val pairs = mutableListOf<Pair<String, String>>()
        for (root in roots.filterNotNull()) {
            for (o in JsonTools.objects(root)) {
                // {key: "mileage", value: "85000", value_label: "85 000 km"}
                val k = (o["key"] as? JsonPrimitive)?.contentOrNull
                val v = (o["value_label"] as? JsonPrimitive)?.contentOrNull ?: (o["value"] as? JsonPrimitive)?.contentOrNull
                if (k != null && v != null) pairs += k to v
                for ((key, value) in o) {
                    if (value is JsonPrimitive) value.contentOrNull?.let { pairs += key to it }
                }
            }
        }
        return fromPairs(pairs)
    }

    fun fromPairs(pairs: Collection<Pair<String, String>>): VehicleHistory {
        var h = VehicleHistory()
        for ((rawKey, rawValue) in pairs) {
            val key = norm(rawKey)
            val value = rawValue.trim()
            val evidence = "$rawKey : $value"
            when (key) {
                in firstHandKeys -> bool(value)?.let { if (it) h = h.copy(owners = Fact(1, SRC, evidence)) }
                in ownerKeys -> Text.digits(value)?.takeIf { it in 0..20 }?.let {
                    if (h.owners == null) h = h.copy(owners = Fact(it.coerceAtLeast(1), SRC, evidence))
                }
                in accidentKeys -> bool(value)?.let {
                    h = h.copy(accident = Fact(if (it) AccidentStatus.DAMAGED else AccidentStatus.NONE, SRC, evidence))
                }
                in accidentFreeKeys -> bool(value)?.let {
                    h = h.copy(accident = Fact(if (it) AccidentStatus.NONE else AccidentStatus.DAMAGED, SRC, evidence))
                }
                in damageKeys -> {
                    val f = HistoryText.fold(value)
                    when {
                        "undamaged" in f || "non endommag" in f || "sans dommage" in f || f == "intact" -> AccidentStatus.NONE
                        "damaged" in f || "endommag" in f || "accident" in f -> AccidentStatus.DAMAGED
                        else -> null
                    }?.let { h = h.copy(accident = Fact(it, SRC, evidence)) }
                }
                in serviceKeys -> bool(value)?.let { h = h.copy(serviceRecords = Fact(it, SRC, evidence)) }
                in smokerKeys -> bool(value)?.let { if (it) h = h.copy(nonSmoker = Fact(true, SRC, evidence)) }
                in warrantyKeys -> Text.digits(value)?.takeIf { it in 1..120 }?.let { h = h.copy(warrantyMonths = Fact(it, SRC, evidence)) }
                in ctKeys -> parseIsoish(value)?.let { h = h.copy(lastInspection = Fact(it, SRC, evidence)) }
                in originKeys -> {
                    val f = HistoryText.fold(value)
                    when {
                        bool(value) != null -> bool(value)
                        "france" in f || "francais" in f -> false
                        "import" in f || "etrang" in f -> true
                        else -> null
                    }?.let { h = h.copy(imported = Fact(it, SRC, evidence)) }
                }
            }
        }
        return h
    }

    private fun bool(v: String): Boolean? = when (HistoryText.fold(v)) {
        "true", "oui", "yes", "1", "y" -> true
        "false", "non", "no", "0", "n" -> false
        else -> null
    }

    /** "2024-03-15T00:00:00Z", "2024-03", "03/2024" → "2024-03". */
    private fun parseIsoish(v: String): String? =
        Regex("^(\\d{4})-(\\d{2})").find(v)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" } ?: HistoryText.parseDate(v)

    /** Attributes of an object, flattened, for sources that already parsed their JSON. */
    fun fromObject(o: JsonObject): VehicleHistory = fromJson(o)
}
