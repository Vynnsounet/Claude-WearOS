package com.radaroccaz.app.sources

import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.Text
import com.radaroccaz.app.data.VehicleOptions
import com.radaroccaz.app.analysis.StructuredHistory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Finds car listings in any JSON a page embeds (Next.js data, hydration state, JSON-LD),
 * without knowing its exact layout: an object is a listing when it has a price plus a
 * mileage or a year, and a make, model or title. Used for sites whose markup changes often.
 */
object GenericExtractor {

    private val stateAssignment = Regex("""(?s)window\.__[A-Z0-9_]+__\s*=\s*(\{.*?\})\s*;?\s*(?:</script>|$)""")

    fun extract(html: String, sourceId: String, baseUrl: String, urlForRef: (String) -> String? = { null }): List<Listing> {
        val doc = JsonTools.document(html)
        val blobs = buildList {
            JsonTools.nextData(doc)?.let(::add)
            doc.select("script[type=application/json]").forEach { s -> JsonTools.parseOrNull(s.data())?.let(::add) }
            doc.select("script:not([type]), script[type=text/javascript]").forEach { s ->
                stateAssignment.find(s.data())?.groupValues?.get(1)?.let(JsonTools::parseOrNull)?.let(::add)
            }
        }
        val found = LinkedHashMap<String, Listing>()
        for (blob in blobs) {
            for (o in JsonTools.objects(blob)) {
                val l = fromObject(o, sourceId, baseUrl, urlForRef) ?: continue
                found.putIfAbsent(l.id, l)
            }
        }
        if (found.isEmpty()) {
            JsonTools.jsonLd(doc).flatMap(::ldItems).forEach { o ->
                fromJsonLd(o, sourceId, baseUrl)?.let { found.putIfAbsent(it.id, it) }
            }
        }
        return found.values.toList()
    }

    // ---- Embedded application JSON ----

    private val nestedKeys = listOf("vehicle", "item", "car", "ad", "listing", "classified", "characteristics", "attributes", "specs")

    /** Value of the first of [keys] found in the object or in one of its nested vehicle-like objects. */
    private fun JsonObject.pick(vararg keys: String): JsonElement? {
        for (k in keys) this[k]?.let { if (it !is JsonPrimitive || it.text() != null) return it }
        for (n in nestedKeys) {
            val child = this[n] as? JsonObject ?: continue
            for (k in keys) child[k]?.let { if (it !is JsonPrimitive || it.text() != null) return it }
        }
        return null
    }

    private fun numberOf(e: JsonElement?): Int? = when (e) {
        is JsonPrimitive -> e.text()?.let { it.toDoubleOrNull()?.toInt() ?: Text.digits(it) }
        is JsonObject -> numberOf(e["value"] ?: e["amount"] ?: e["price"] ?: e["raw"] ?: e["priceFormatted"])
        is JsonArray -> numberOf(e.firstOrNull())
        else -> null
    }

    private fun textOf(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.text()
        is JsonObject -> e.str("label") ?: e.str("name") ?: e.str("value")
        else -> null
    }

    private fun fromObject(o: JsonObject, sourceId: String, baseUrl: String, urlForRef: (String) -> String?): Listing? {
        val price = numberOf(o.pick("price", "prix", "priceValue", "customerPrice", "sellingPrice")) ?: return null
        if (price !in 300..2_000_000) return null
        val mileage = numberOf(o.pick("mileage", "mileageInKm", "kilometrage", "km", "odometer"))
        val year = numberOf(o.pick("year", "annee", "modelYear", "firstRegistrationYear"))?.takeIf { it in 1950..2100 }
            ?: Text.year(textOf(o.pick("firstRegistration", "firstRegistrationDate", "registrationDate", "regdate")))
        if (mileage == null && year == null) return null
        val make = textOf(o.pick("make", "brand", "marque"))?.let(::titleCase)
        val model = textOf(o.pick("model", "modele", "commercialName"))
        val version = textOf(o.pick("version", "modelVersion", "finition", "trim"))
        val title = textOf(o.pick("title", "subject", "name"))
            ?: listOfNotNull(make, model?.let(::titleCase), version?.let(::titleCase)).joinToString(" ").takeIf { it.isNotBlank() }
            ?: return null
        val ref = textOf(o.pick("reference", "classifiedId", "adId", "listId", "id", "uuid"))
            ?: "${Text.compact(title)}-$price-${mileage ?: year}"
        val url = textOf(o.pick("url", "link", "href", "permalink", "canonicalUrl"))
            ?.let { if (it.startsWith("http")) it else if (it.startsWith("/")) baseUrl + it else null }
            ?: urlForRef(ref) ?: return null
        val images = o.pick("photoUrl", "photos", "images", "pictures", "image", "thumbnail", "mainPhoto", "photo")
            .strings().map { if (it.startsWith("//")) "https:$it" else it }.filter { it.startsWith("http") }
        val description = textOf(o.pick("description", "body", "comment")).orEmpty()
        return Listing(
            id = "$sourceId:$ref",
            source = sourceId,
            title = title,
            price = price,
            url = url,
            make = make,
            model = model,
            mileageKm = mileage,
            year = year,
            fuel = Fuel.parse(textOf(o.pick("energy", "fuel", "fuelType", "energie", "carburant"))),
            gearbox = Gearbox.parse(textOf(o.pick("gearbox", "transmission", "boite", "gearboxType"))),
            sellerType = SellerType.parse(textOf(o.pick("customerType", "sellerType", "customerFamilyCode", "ownerType"))
                ?: (o["seller"] as? JsonObject)?.let { textOf(it["type"]) }),
            city = textOf(o.pick("city", "ville")) ?: (o["location"] as? JsonObject)?.let { textOf(it["city"]) },
            postalCode = textOf(o.pick("zipcode", "zipCode", "postalCode", "visitPlace", "department"))
                ?: (o["location"] as? JsonObject)?.let { textOf(it["zipcode"] ?: it["postalCode"] ?: it["zip"] ?: it["visitPlace"]) },
            imageUrls = images,
            description = description,
            powerHp = numberOf(o.pick("powerDIN", "horsePower", "powerHp", "puissanceDin")),
            options = VehicleOptions.detect(title, description),
        ).withHistory(StructuredHistory.fromJson(o))
    }

    /** "PEUGEOT 308 1.2 PURETECH" → "Peugeot 308 1.2 Puretech"; mixed-case text is kept as is. */
    internal fun titleCase(s: String): String =
        if (s.any { it.isLowerCase() }) s
        else s.split(' ').joinToString(" ") { w ->
            if (w.length <= 3 && w.any { !it.isLetter() } || w.any { it.isDigit() }) w
            else w.lowercase().replaceFirstChar { it.uppercase() }
        }

    // ---- JSON-LD (schema.org Car / Vehicle / Product) ----

    private fun ldItems(root: JsonElement): List<JsonObject> = buildList {
        fun visit(e: JsonElement?) {
            when (e) {
                is JsonArray -> e.forEach(::visit)
                is JsonObject -> {
                    val type = e["@type"].strings().joinToString(" ")
                    when {
                        "ItemList" in type -> e["itemListElement"].arr()?.forEach { el ->
                            visit(el.obj()?.get("item") ?: el)
                        }
                        type.contains("Car") || type.contains("Vehicle") || type.contains("Product") -> add(e)
                        else -> e["@graph"]?.let(::visit)
                    }
                }
                else -> Unit
            }
        }
        visit(root)
    }

    private fun fromJsonLd(o: JsonObject, sourceId: String, baseUrl: String): Listing? {
        val offers = o["offers"].let { it.obj() ?: it.arr()?.firstOrNull().obj() }
        val price = numberOf(offers?.get("price") ?: offers?.get("lowPrice") ?: o["price"]) ?: return null
        val title = o.str("name") ?: return null
        val url = (o.str("url") ?: offers?.str("url"))?.let { if (it.startsWith("/")) baseUrl + it else it } ?: return null
        val description = o.str("description").orEmpty()
        return Listing(
            id = "$sourceId:${o.str("sku") ?: o.str("productID") ?: url.substringAfterLast('/').substringBefore('?')}",
            source = sourceId,
            title = title,
            price = price,
            url = url,
            make = textOf(o["brand"]) ?: textOf(o["manufacturer"]),
            model = textOf(o["model"]),
            mileageKm = numberOf(o["mileageFromOdometer"]),
            year = Text.year(o.str("vehicleModelDate") ?: o.str("productionDate") ?: o.str("dateVehicleFirstRegistered")),
            fuel = Fuel.parse(textOf(o["fuelType"]) ?: textOf(o["vehicleEngine"].obj()?.get("fuelType"))),
            gearbox = Gearbox.parse(textOf(o["vehicleTransmission"])),
            imageUrls = o["image"].strings(),
            description = description,
            options = VehicleOptions.detect(title, description),
        ).withHistory()
    }
}
