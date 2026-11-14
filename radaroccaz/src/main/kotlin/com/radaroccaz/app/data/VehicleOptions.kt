package com.radaroccaz.app.data

data class VehicleOption(val id: String, val label: String, val keywords: List<String>)

/**
 * Equipment the user can ask for. Listings rarely have structured options, so they are
 * found by keywords in the title and description (accents and case ignored, whole words).
 */
object VehicleOptions {
    val all = listOf(
        VehicleOption("gps", "GPS", listOf("gps", "navigation", "navi", "systeme de navigation", "nav")),
        VehicleOption("camera", "Caméra de recul", listOf("camera de recul", "camera recul", "camera arriere", "camera 360", "cameras 360", "camera de stationnement", "camera")),
        VehicleOption("radar", "Radars de stationnement", listOf("radar de recul", "radars de recul", "radar recul", "radars av", "radars ar", "radars avant", "radars arriere", "aide au stationnement", "aide au parking", "capteurs de stationnement", "park assist", "radars de stationnement", "radar de stationnement")),
        VehicleOption("clim", "Clim automatique", listOf("climatisation automatique", "clim auto", "clim automatique", "climatisation bi zone", "clim bi zone", "bizone", "bi zone", "climatronic", "climatisation tri zone")),
        VehicleOption("regulateur", "Régulateur adaptatif", listOf("regulateur adaptatif", "regulateur de vitesse adaptatif", "acc", "cruise control adaptatif", "regulateur actif", "distronic")),
        VehicleOption("carplay", "CarPlay / Android Auto", listOf("carplay", "car play", "android auto", "mirror link", "mirrorlink", "mirror screen")),
        VehicleOption("toit", "Toit ouvrant / pano", listOf("toit ouvrant", "toit panoramique", "toit pano", "toit vitre", "toit en verre")),
        VehicleOption("cuir", "Sellerie cuir", listOf("cuir", "alcantara", "sellerie cuir")),
        VehicleOption("sieges_chauffants", "Sièges chauffants", listOf("sieges chauffants", "siege chauffant", "sieges avant chauffants", "sieges chauffant")),
        VehicleOption("attelage", "Attelage", listOf("attelage", "crochet d'attelage", "boule d'attelage")),
        VehicleOption("led", "Phares LED", listOf("full led", "phares led", "feux led", "matrix led", "projecteurs led", "led matrix")),
        VehicleOption("keyless", "Accès sans clé", listOf("keyless", "sans cle", "acces mains libres", "demarrage mains libres", "kessy", "hands free")),
        VehicleOption("jantes", "Jantes alliage", listOf("jantes alu", "jantes alliage", "jantes aluminium", "jantes 16", "jantes 17", "jantes 18", "jantes 19", "jantes 20")),
        VehicleOption("premiere_main", "Première main", listOf("premiere main", "1ere main", "1re main", "1 ere main", "1er main", "1 main")),
        VehicleOption("carnet", "Carnet d'entretien", listOf("carnet d'entretien", "carnet entretien", "historique d'entretien", "entretien suivi", "entretiens suivis", "entretien a jour", "factures")),
        VehicleOption("garantie", "Garantie", listOf("garantie", "garantie 12 mois", "garantie 6 mois", "garantie constructeur")),
    )

    private val byId = all.associateBy { it.id }
    fun label(id: String): String = byId[id]?.label ?: id

    private val patterns: Map<String, Regex> = all.associate { option ->
        val alternatives = option.keywords.joinToString("|") { Regex.escape(Text.normalize(it)) }
        option.id to Regex("(?<![a-z0-9])($alternatives)(?![a-z0-9])")
    }

    fun detect(vararg texts: String?): Set<String> {
        val text = texts.filterNotNull().joinToString(" \n ") { Text.normalize(it).replace('-', ' ') }
        if (text.isBlank()) return emptySet()
        return patterns.filterValues { it.containsMatchIn(text) }.keys
    }
}
