package com.radaroccaz.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class Fuel(val label: String) {
    ESSENCE("Essence"),
    DIESEL("Diesel"),
    HYBRIDE("Hybride"),
    HYBRIDE_RECHARGEABLE("Hybride rechargeable"),
    ELECTRIQUE("Électrique"),
    GPL("GPL");

    companion object {
        /** Maps the many labels used by the sites ("Gazole", "Hybride essence électrique", "e"...) to a [Fuel]. */
        fun parse(raw: String?): Fuel? {
            val s = Text.normalize(raw ?: return null)
            return when {
                s.isBlank() -> null
                "rechargeable" in s || "plug" in s || "phev" in s -> HYBRIDE_RECHARGEABLE
                "hybr" in s || s == "hyb" || s == "2" || s == "3" -> HYBRIDE
                "elec" in s && ("essence" in s || "diesel" in s) -> HYBRIDE
                "elec" in s || s == "e" -> ELECTRIQUE
                "diesel" in s || "gazole" in s || "gasoil" in s || s == "dies" || s == "d" -> DIESEL
                "gpl" in s || "lpg" in s || s == "l" -> GPL
                "essence" in s || "petrol" in s || "sans plomb" in s || "benzin" in s || s == "ess" || s == "b" -> ESSENCE
                else -> null
            }
        }
    }
}

@Serializable
enum class Gearbox(val label: String) {
    MANUELLE("Manuelle"),
    AUTOMATIQUE("Automatique");

    companion object {
        fun parse(raw: String?): Gearbox? {
            val s = Text.normalize(raw ?: return null)
            return when {
                s.isBlank() -> null
                "auto" in s || "semi" in s || "robot" in s || "dsg" in s || "edc" in s || "eat" in s || s == "a" || s == "s" -> AUTOMATIQUE
                "manu" in s || "meca" in s || s == "m" || s == "man" || s == "bvm" -> MANUELLE
                else -> null
            }
        }
    }
}

@Serializable
enum class BodyType(val label: String) {
    CITADINE("Citadine"),
    COMPACTE("Compacte"),
    BERLINE("Berline"),
    BREAK("Break"),
    SUV("SUV / 4x4"),
    MONOSPACE("Monospace"),
    COUPE("Coupé"),
    CABRIOLET("Cabriolet"),
    UTILITAIRE("Utilitaire"),
    PICKUP("Pick-up");

    companion object {
        /** Body labels used by the sites ("Berline Compacte", "SUV/4x4/Pick-Up", "Break"...). */
        fun parse(raw: String?): BodyType? {
            val s = Text.normalize(raw ?: return null)
            return when {
                s.isBlank() -> null
                "compact" in s || "hatchback" in s -> COMPACTE
                "citadine" in s || "city" in s -> CITADINE
                "break" in s || "estate" in s || "wagon" in s || "touring" in s -> BREAK
                "pick" in s -> PICKUP
                "suv" in s || "4x4" in s || "tout-terrain" in s || "tout terrain" in s || "crossover" in s -> SUV
                "monospace" in s || "ludospace" in s || "minivan" in s || s == "van" -> MONOSPACE
                "cabrio" in s || "roadster" in s || "decapotable" in s -> CABRIOLET
                "coupe" in s -> COUPE
                "utilitaire" in s || "fourgon" in s || "commercial" in s -> UTILITAIRE
                "berline" in s || "sedan" in s || "limousine" in s -> BERLINE
                else -> null
            }
        }

        /** Catalog codes: citadine, compacte, berline, break, suv, monospace, coupe, cabriolet, utilitaire, pickup. */
        fun fromCode(code: String): BodyType? = entries.firstOrNull { it.name.equals(code, ignoreCase = true) }
    }
}

@Serializable
enum class Drive(val label: String, val short: String) {
    TRACTION("Traction (avant)", "Traction"),
    PROPULSION("Propulsion (arrière)", "Propulsion"),
    INTEGRALE("4 roues motrices", "4x4");

    companion object {
        fun fromCode(code: String): Drive? = when (code.trim()) {
            "F" -> TRACTION
            "R" -> PROPULSION
            "4" -> INTEGRALE
            else -> null
        }

        /** "front-wheel", "Avant", "4x4", "all-wheel"... */
        fun parse(raw: String?): Drive? {
            val s = Text.normalize(raw ?: return null)
            return when {
                s.isBlank() -> null
                "all" in s || "4x4" in s || "4wd" in s || "awd" in s || "integral" in s || "four" in s -> INTEGRALE
                "front" in s || "avant" in s || "traction" in s || s == "f" || s == "fwd" -> TRACTION
                "rear" in s || "arriere" in s || "propulsion" in s || s == "r" || s == "rwd" -> PROPULSION
                else -> null
            }
        }
    }
}

@Serializable
enum class SellerType(val label: String) {
    PARTICULIER("Particulier"),
    PRO("Professionnel");

    companion object {
        fun parse(raw: String?): SellerType? {
            val s = Text.normalize(raw ?: return null)
            return when {
                s.isBlank() -> null
                "priv" in s || "part" in s || s == "p" -> PARTICULIER
                "pro" in s || "dealer" in s || "garage" in s || "concession" in s || s == "d" -> PRO
                else -> null
            }
        }
    }
}

@Serializable
enum class SellerFilter(val label: String) { ALL("Tous"), PARTICULIER("Particuliers"), PRO("Pros") }

/** How much each criterion matters to the user, from 0 (not at all) to 3 (essential). */
@Serializable
data class Priorities(
    val price: Int = 3,
    val mileage: Int = 2,
    val age: Int = 2,
    val options: Int = 1,
    val history: Int = 2,
) {
    companion object {
        const val MAX = 3
        val LABELS = listOf("Indifférent", "Un peu", "Important", "Essentiel")
    }
}

@Serializable
data class SearchCriteria(
    val make: String = "",
    val model: String = "",
    val budgetMin: Int? = null,
    val budgetMax: Int? = null,
    val mileageMax: Int? = null,
    val yearMin: Int? = null,
    val fuels: Set<Fuel> = emptySet(),
    val gearbox: Gearbox? = null,
    val seller: SellerFilter = SellerFilter.ALL,
    /** Department number ("33", "2A") or the start of a postal code; empty = all of France. */
    val department: String = "",
    val wantedOptions: Set<String> = emptySet(),
    /** Engines from the catalog ("1.5 BlueHDi 130"); empty = all. */
    val engines: Set<String> = emptySet(),
    val bodies: Set<BodyType> = emptySet(),
    val drives: Set<Drive> = emptySet(),
    val powerMin: Int? = null,
    val powerMax: Int? = null,
    /** Drop cars known to have had more owners than this. */
    val maxOwners: Int? = null,
    /** Drop cars declared accidented, damaged or sold for parts. */
    val excludeDamaged: Boolean = false,
    val priorities: Priorities = Priorities(),
) {
    val isValid: Boolean get() = make.isNotBlank() || model.isNotBlank()

    val title: String get() = listOf(make.trim(), model.trim()).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "Recherche" }

    /** One-line summary of the filters, e.g. "≤ 15 000 € · ≤ 100 000 km · 2016+ · Diesel". */
    val summary: String
        get() = buildList {
            when {
                budgetMin != null && budgetMax != null -> add("${Format.price(budgetMin)} – ${Format.price(budgetMax)}")
                budgetMax != null -> add("≤ ${Format.price(budgetMax)}")
                budgetMin != null -> add("≥ ${Format.price(budgetMin)}")
            }
            mileageMax?.let { add("≤ ${Format.km(it)}") }
            yearMin?.let { add("$it+") }
            if (fuels.isNotEmpty()) add(fuels.joinToString("/") { it.label })
            gearbox?.let { add(it.label) }
            if (engines.isNotEmpty()) add(if (engines.size == 1) engines.first() else "${engines.size} motorisations")
            if (bodies.isNotEmpty()) add(bodies.joinToString("/") { it.label })
            if (drives.isNotEmpty()) add(drives.joinToString("/") { it.short })
            when {
                powerMin != null && powerMax != null -> add("$powerMin–$powerMax ch")
                powerMin != null -> add("≥ $powerMin ch")
                powerMax != null -> add("≤ $powerMax ch")
            }
            if (seller != SellerFilter.ALL) add(seller.label)
            if (department.isNotBlank()) add("Dép. ${department.trim()}")
            maxOwners?.let { add(if (it == 1) "1re main" else "≤ $it propriétaires") }
            if (excludeDamaged) add("Non accidentée")
            if (wantedOptions.isNotEmpty()) add("${wantedOptions.size} option${if (wantedOptions.size > 1) "s" else ""}")
        }.joinToString(" · ").ifEmpty { "Tous critères" }
}

@Serializable
data class Listing(
    /** Unique across sources: "<sourceId>:<id on the site>". */
    val id: String,
    val source: String,
    val title: String,
    val price: Int,
    val url: String,
    val make: String? = null,
    val model: String? = null,
    val mileageKm: Int? = null,
    val year: Int? = null,
    val fuel: Fuel? = null,
    val gearbox: Gearbox? = null,
    val sellerType: SellerType? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val imageUrls: List<String> = emptyList(),
    val description: String = "",
    /** ISO-8601 date or date-time, as given by the site. */
    val publishedAt: String? = null,
    val powerHp: Int? = null,
    val body: BodyType? = null,
    val drive: Drive? = null,
    /** Engine of the catalog this listing was matched to ("1.5 BlueHDi 130"). */
    val engine: String? = null,
    /** Option ids (see [VehicleOptions]) found in the title, description or attributes. */
    val options: Set<String> = emptySet(),
    /** Other sources where the same car was found. */
    val alsoOn: List<String> = emptyList(),
    /** What the site and the seller's text say about the car's past. */
    val history: VehicleHistory = VehicleHistory(),
    /** True once the full listing page has been read (complete description, all attributes). */
    val detailsLoaded: Boolean = false,
) {
    val location: String?
        get() = when {
            city != null && postalCode != null -> "$city (${postalCode.take(2)})"
            else -> city ?: postalCode
        }
}

@Serializable
enum class AlertFrequency(val minutes: Long, val label: String) {
    MIN_30(30, "Toutes les 30 min"),
    HOUR_1(60, "Toutes les heures"),
    HOUR_3(180, "Toutes les 3 h"),
    HOUR_12(720, "Deux fois par jour"),
    DAY_1(1440, "Une fois par jour"),
}

@Serializable
data class Alert(
    val id: String,
    val name: String,
    val criteria: SearchCriteria,
    val frequency: AlertFrequency = AlertFrequency.HOUR_1,
    val enabled: Boolean = true,
    val createdAt: Long = 0,
    val lastCheckedAt: Long? = null,
    /** Ids of every listing already known for this alert, so only new ones are reported. */
    val seenIds: List<String> = emptyList(),
    /** New listings found by the background checks and not yet opened in the app (newest first). */
    val unread: List<Listing> = emptyList(),
    val lastError: String? = null,
) {
    companion object {
        const val MAX_SEEN = 1500
        const val MAX_UNREAD = 60
    }
}

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val enabledSources: Set<String> = DEFAULT_SOURCES,
    /**
     * Sites whose rules forbid automated reading (Leboncoin, La Centrale) are opened in the
     * browser unless the user explicitly turns automatic reading on for them here.
     */
    val autoReadRestricted: Set<String> = emptySet(),
    /** Sources the user has already seen in the settings; new ones get switched on once. */
    val knownSources: Set<String> = emptySet(),
    /** Uses generated listings instead of the network, to try the app. */
    val demoMode: Boolean = false,
) {
    companion object {
        val DEFAULT_SOURCES = setOf("autoscout24", "aramisauto", "autohero", "largus", "paruvendu", "leboncoin", "lacentrale")
    }
}

@Serializable
data class AppState(
    val settings: Settings = Settings(),
    val favorites: List<Listing> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val recentSearches: List<SearchCriteria> = emptyList(),
    val lastCriteria: SearchCriteria? = null,
    /** History entered by the user (from a HistoVec report, the seller...), by listing id. */
    val userHistory: Map<String, VehicleHistory> = emptyMap(),
)
