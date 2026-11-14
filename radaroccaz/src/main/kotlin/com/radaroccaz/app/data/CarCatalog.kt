package com.radaroccaz.app.data

import java.io.InputStream

data class EngineSpec(
    val name: String,
    val fuel: Fuel,
    val powerHp: Int,
    /** Four-wheel-drive version (4Motion, quattro, xDrive, 4x4...). */
    val allWheel: Boolean,
)

data class ModelSpec(
    val make: String,
    val model: String,
    val bodies: List<BodyType>,
    val years: String,
    val drives: List<Drive>,
    val engines: List<EngineSpec>,
) {
    val fuels: List<Fuel> get() = engines.map { it.fuel }.distinct()
    val powerRange: IntRange? get() = engines.takeIf { it.isNotEmpty() }?.let { e -> e.minOf { it.powerHp }..e.maxOf { it.powerHp } }
}

data class MakeInfo(val name: String, val as24Slug: String, val models: List<String>)

/**
 * Makes and models (from AutoScout24's own list, 1 300+ models) and technical sheets of
 * the common models: bodies, drive, engines with energy and power. Loaded once from
 * the app's assets; a small built-in list is used until then.
 */
object CarCatalog {
    @Volatile
    private var makesList: List<MakeInfo> = fallbackMakes()

    @Volatile
    private var specs: Map<String, ModelSpec> = emptyMap()

    private fun key(make: String, model: String) = Text.compact(make) + "|" + Text.compact(model)

    /** Reads `catalog/models.txt` and `catalog/specs.txt`. */
    fun load(models: InputStream, specsText: InputStream) {
        val makes = models.bufferedReader().readLines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 3 || line.startsWith("#")) null
            else MakeInfo(p[0], p[1], p[2].split(';').filter { it.isNotBlank() })
        }
        val parsed = specsText.bufferedReader().readLines().mapNotNull(::parseSpec)
        val bySpec = parsed.associateBy { key(it.make, it.model) }
        // Models with a technical sheet first (the common ones), then the rest of the make's range.
        makesList = makes.map { m ->
            val detailed = parsed.filter { Text.compact(it.make) == Text.compact(m.name) }.map { it.model }
            m.copy(models = detailed + m.models.filter { name -> detailed.none { Text.compact(it) == Text.compact(name) } })
        }.sortedWith(compareBy<MakeInfo> { mk -> if (POPULAR.indexOf(mk.name) >= 0) POPULAR.indexOf(mk.name) else 1000 }.thenBy { it.name.lowercase() })
        specs = bySpec
    }

    private fun parseSpec(line: String): ModelSpec? {
        if (line.isBlank() || line.startsWith("#")) return null
        val p = line.split('|')
        if (p.size != 6) return null
        val engines = p[5].split(';').mapNotNull { e ->
            val q = e.split(':')
            if (q.size != 3) return@mapNotNull null
            val fuel = when (q[1]) {
                "E" -> Fuel.ESSENCE
                "D" -> Fuel.DIESEL
                "H" -> Fuel.HYBRIDE
                "P" -> Fuel.HYBRIDE_RECHARGEABLE
                "EL" -> Fuel.ELECTRIQUE
                "G" -> Fuel.GPL
                else -> return@mapNotNull null
            }
            val awd = q[2].endsWith("@4")
            val hp = q[2].removeSuffix("@4").toIntOrNull() ?: return@mapNotNull null
            EngineSpec(q[0], fuel, hp, awd)
        }
        return ModelSpec(
            make = p[0],
            model = p[1],
            bodies = p[2].split(' ').mapNotNull(BodyType::fromCode),
            years = p[3],
            drives = p[4].split(' ').mapNotNull(Drive::fromCode),
            engines = engines,
        )
    }

    val makes: List<String> get() = makesList.map { it.name }

    private val aliases = mapOf("ds" to "DS Automobiles", "vw" to "Volkswagen", "mercedes" to "Mercedes-Benz", "merco" to "Mercedes-Benz", "landrover" to "Land Rover", "range" to "Land Rover", "alfa" to "Alfa Romeo")

    fun makeInfo(make: String): MakeInfo? =
        makesList.firstOrNull { Text.compact(it.name) == Text.compact(make) }
            ?: aliases[Text.compact(make)]?.let { alias -> makesList.firstOrNull { it.name == alias } }
            ?: makesList.firstOrNull { Text.compact(make).startsWith(Text.compact(it.name)) || Text.compact(it.name).startsWith(Text.compact(make)) && make.length >= 3 }

    fun spec(make: String, model: String): ModelSpec? {
        if (model.isBlank()) return null
        specs[key(canonicalMake(make), model)]?.let { return it }
        // Model typed without its make, or a make written differently ("Mercedes").
        val m = Text.compact(model)
        return specs.values.firstOrNull { Text.compact(it.model) == m && (make.isBlank() || Text.compact(it.make).startsWith(Text.compact(make).take(4))) }
    }

    fun suggestMakes(query: String): List<String> {
        val q = Text.compact(query)
        if (q.isEmpty()) return makes
        return makes.filter { Text.compact(it).startsWith(q) } + makes.filter { q in Text.compact(it) && !Text.compact(it).startsWith(q) }
    }

    fun suggestModels(make: String, query: String): List<String> {
        val list = makeInfo(make)?.models ?: makesList.flatMap { it.models.take(8) }
        val q = Text.compact(query)
        if (q.isEmpty()) return list
        return list.filter { Text.compact(it).startsWith(q) } + list.filter { q in Text.compact(it) && !Text.compact(it).startsWith(q) }
    }

    /** "peugeot" → "Peugeot", "mercedes" → "Mercedes-Benz"; keeps the user's text when the make is unknown. */
    fun canonicalMake(make: String): String = makeInfo(make)?.name ?: make.trim()

    /** Most common makes on the French used-car market, shown first. */
    private val POPULAR = listOf(
        "Peugeot", "Renault", "Citroën", "Volkswagen", "Dacia", "Toyota", "Ford", "Opel", "BMW", "Mercedes-Benz",
        "Audi", "Fiat", "Nissan", "Kia", "Hyundai", "Skoda", "SEAT", "CUPRA", "Tesla", "MINI", "DS Automobiles",
        "Suzuki", "Mazda", "Volvo", "Jeep", "MG", "Land Rover", "Honda", "Mitsubishi", "Alfa Romeo",
    )

    private fun fallbackMakes() = listOf(
        MakeInfo("Peugeot", "peugeot", listOf("208", "2008", "308", "3008", "5008")),
        MakeInfo("Renault", "renault", listOf("Clio", "Captur", "Megane", "Austral")),
        MakeInfo("Citroën", "citroen", listOf("C3", "C4", "C5 Aircross")),
        MakeInfo("Volkswagen", "volkswagen", listOf("Polo", "Golf", "T-Roc", "Tiguan")),
    )
}
