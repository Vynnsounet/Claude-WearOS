package com.radaroccaz.app.data

/** Makes and models most sold second-hand in France, used for suggestions while typing. */
object CarCatalog {
    val models: Map<String, List<String>> = linkedMapOf(
        "Peugeot" to listOf("108", "208", "2008", "308", "3008", "408", "508", "5008", "Partner", "Rifter", "Expert"),
        "Renault" to listOf("Twingo", "Clio", "Captur", "Mégane", "Arkana", "Austral", "Kadjar", "Scénic", "Espace", "Zoe", "Kangoo", "Trafic"),
        "Citroën" to listOf("C1", "C3", "C3 Aircross", "C4", "C4 Picasso", "C5 Aircross", "Berlingo", "DS3", "Jumpy"),
        "Dacia" to listOf("Sandero", "Duster", "Jogger", "Spring", "Logan", "Lodgy"),
        "Volkswagen" to listOf("Polo", "Golf", "T-Roc", "Tiguan", "Passat", "Touran", "T-Cross", "ID.3", "ID.4", "Up"),
        "Toyota" to listOf("Aygo", "Yaris", "Yaris Cross", "Corolla", "C-HR", "RAV4", "Auris", "Prius"),
        "Ford" to listOf("Fiesta", "Focus", "Puma", "Kuga", "Mondeo", "EcoSport", "Ranger", "Transit"),
        "Opel" to listOf("Corsa", "Astra", "Mokka", "Crossland", "Grandland", "Zafira", "Insignia"),
        "Fiat" to listOf("500", "500X", "Panda", "Tipo", "Punto", "Ducato"),
        "BMW" to listOf("Série 1", "Série 2", "Série 3", "Série 4", "Série 5", "X1", "X2", "X3", "X5", "i3"),
        "Mercedes-Benz" to listOf("Classe A", "Classe B", "Classe C", "Classe E", "CLA", "GLA", "GLC", "Vito"),
        "Audi" to listOf("A1", "A3", "A4", "A5", "A6", "Q2", "Q3", "Q5", "e-tron"),
        "Nissan" to listOf("Micra", "Juke", "Qashqai", "X-Trail", "Leaf", "Note"),
        "Kia" to listOf("Picanto", "Rio", "Ceed", "Stonic", "Niro", "Sportage", "EV6"),
        "Hyundai" to listOf("i10", "i20", "i30", "Kona", "Tucson", "Ioniq", "Bayon"),
        "Skoda" to listOf("Fabia", "Octavia", "Kamiq", "Karoq", "Kodiaq", "Superb", "Scala"),
        "Seat" to listOf("Ibiza", "Leon", "Arona", "Ateca", "Tarraco"),
        "Cupra" to listOf("Formentor", "Born", "Leon"),
        "Tesla" to listOf("Model 3", "Model Y", "Model S", "Model X"),
        "MINI" to listOf("Cooper", "One", "Countryman", "Clubman"),
        "Suzuki" to listOf("Swift", "Ignis", "Vitara", "S-Cross", "Jimny"),
        "Mazda" to listOf("2", "3", "CX-3", "CX-30", "CX-5", "MX-5"),
        "Volvo" to listOf("XC40", "XC60", "XC90", "V40", "V60"),
        "DS" to listOf("DS 3", "DS 4", "DS 7"),
        "Jeep" to listOf("Renegade", "Compass", "Wrangler"),
        "MG" to listOf("MG4", "ZS", "HS"),
        "Honda" to listOf("Jazz", "Civic", "HR-V", "CR-V"),
        "Mitsubishi" to listOf("Space Star", "ASX", "Outlander"),
        "Land Rover" to listOf("Range Rover Evoque", "Discovery Sport", "Defender"),
        "Porsche" to listOf("Macan", "Cayenne", "911"),
    )

    val makes: List<String> get() = models.keys.toList()

    fun suggestMakes(query: String): List<String> {
        val q = Text.compact(query)
        if (q.isEmpty()) return makes
        return makes.filter { Text.compact(it).startsWith(q) } + makes.filter { q in Text.compact(it) && !Text.compact(it).startsWith(q) }
    }

    fun suggestModels(make: String, query: String): List<String> {
        val list = models.entries.firstOrNull { Text.compact(it.key) == Text.compact(make) }?.value
            ?: models.values.flatten()
        val q = Text.compact(query)
        if (q.isEmpty()) return list
        return list.filter { Text.compact(it).startsWith(q) } + list.filter { q in Text.compact(it) && !Text.compact(it).startsWith(q) }
    }

    /** "peugeot" → "Peugeot", keeps the user's text when the make is unknown. */
    fun canonicalMake(make: String): String =
        makes.firstOrNull { Text.compact(it) == Text.compact(make) } ?: make.trim()
}
