package com.radaroccaz.app.data

/**
 * Works out a listing's engine, body and drive from its text and the catalog: the sites
 * rarely give them as data, but the title usually says "308 SW 1.5 BlueHDi 130 EAT8".
 */
object VehicleSpecs {

    private val allWheelWords = Regex("(?<![a-z0-9])(4x4|4wd|awd|4motion|quattro|xdrive|4matic|all4|allgrip|4drive|q4|hybrid4|4xe|e-?four|awd-i|integrale|4 roues motrices|transmission integrale|dual motor|twin motor)(?![a-z0-9])")
    private val rearWords = Regex("(?<![a-z0-9])(propulsion|rwd|sdrive)(?![a-z0-9])")
    private val frontWords = Regex("(?<![a-z0-9])(traction avant|fwd)(?![a-z0-9])")

    private val bodyWords: List<Pair<BodyType, Regex>> = listOf(
        BodyType.BREAK to Regex("(?<![a-z0-9])(sw|break|touring|estate|avant|variant|combi|sports? ?tourer|sportbrake|shooting brake|tourer|allroad|alltrack|wagon|kombi)(?![a-z0-9])"),
        BodyType.CABRIOLET to Regex("(?<![a-z0-9])(cabriolet|cabrio|roadster|spider|spyder|decapotable)(?![a-z0-9])"),
        BodyType.COUPE to Regex("(?<![a-z0-9])(coupe)(?![a-z0-9])"),
        BodyType.UTILITAIRE to Regex("(?<![a-z0-9])(fourgon|fourgonnette|utilitaire|tole|tolee|plateau|benne|chassis cabine|l1h1|l2h1|l2h2|l3h2)(?![a-z0-9])"),
        BodyType.PICKUP to Regex("(?<![a-z0-9])(pick-?up|double cabine|simple cabine)(?![a-z0-9])"),
        BodyType.MONOSPACE to Regex("(?<![a-z0-9])(monospace|7 places)(?![a-z0-9])"),
    )

    private val automatic = Regex("(?<![a-z0-9])(bva\\d?|bvr|eat\\d|edc\\d?|dsg\\d?|dct|\\d?g ?dct|\\dg ?tronic|tiptronic|steptronic|s ?tronic|multitronic|cvt|e ?cvt|powershift|easytronic|automatique|boite auto|auto|geartronic|e ?dcs\\d|dkg\\d?|aut\\.?)(?![a-z0-9])")
    private val manual = Regex("(?<![a-z0-9])(bvm\\d?|manuelle|boite manuelle|mecanique)(?![a-z0-9])")

    fun detectGearbox(t: String): Gearbox? = when {
        manual.containsMatchIn(t) -> Gearbox.MANUELLE
        automatic.containsMatchIn(t) -> Gearbox.AUTOMATIQUE
        else -> null
    }

    private fun text(l: Listing) = Text.normalize(listOfNotNull(l.title, l.description.take(400)).joinToString(" ")).replace('-', ' ')

    /** Fills in engine, body and drive when the listing does not already give them. */
    fun annotate(l: Listing, spec: ModelSpec?): Listing {
        val t = text(l)
        val engine = spec?.let { matchEngine(l, it, t) }
        val body = l.body ?: detectBody(t, spec)
        val drive = l.drive ?: detectDrive(t, spec, engine)
        val power = l.powerHp ?: engine?.powerHp?.takeIf { engine.name.any(Char::isDigit) && Regex("\\b${engine.powerHp}\\b").containsMatchIn(t) }
        val gearbox = l.gearbox ?: Text.normalize(l.title).replace('-', ' ').let(::detectGearbox)
        return l.copy(engine = l.engine ?: engine?.name, body = body, drive = drive, powerHp = power, gearbox = gearbox)
    }

    fun detectBody(t: String, spec: ModelSpec?): BodyType? {
        val found = bodyWords.firstOrNull { (_, rx) -> rx.containsMatchIn(t) }?.first
        if (found != null && (spec == null || found in spec.bodies || found == BodyType.UTILITAIRE)) return found
        // No hint: the model's main body (a 308 without "SW" is the hatchback).
        return spec?.bodies?.firstOrNull()
    }

    fun detectDrive(t: String, spec: ModelSpec?, engine: EngineSpec?): Drive? {
        if (allWheelWords.containsMatchIn(t)) return Drive.INTEGRALE
        if (engine?.allWheel == true) return Drive.INTEGRALE
        if (rearWords.containsMatchIn(t)) return Drive.PROPULSION
        if (frontWords.containsMatchIn(t)) return Drive.TRACTION
        val twoWheel = spec?.drives?.filter { it != Drive.INTEGRALE }.orEmpty()
        return when {
            spec == null -> null
            spec.drives.size == 1 -> spec.drives.first()
            // The model also exists in 4x4 but nothing says so: its usual two-wheel drive.
            twoWheel.size == 1 -> twoWheel.first()
            else -> null
        }
    }

    private val familyStop = setOf("hybrid", "plug", "in", "tous", "electric", "s", "the", "e")

    /** Distinctive words of an engine name: "1.5 BlueHDi 130" → [bluehdi]; numbers handled apart. */
    private fun families(name: String): List<String> =
        Text.normalize(name).replace('-', ' ').split(' ', '.', '/', '+')
            .filter { w -> w.length >= 2 && w.any(Char::isLetter) && w !in familyStop }

    /**
     * Best engine of the model for this listing: its family word ("puretech"), power ("130")
     * and displacement ("1.2") found in the text, the listing's power and energy. Returns
     * null when nothing distinguishes one engine from another.
     */
    fun matchEngine(l: Listing, spec: ModelSpec, t: String = text(l)): EngineSpec? {
        val numbers = Regex("(?<![0-9.])(\\d{2,3})(?![0-9.])").findAll(t).map { it.value.toInt() }.toSet()
        val compactText = Text.compact(t)
        val scored = spec.engines.map { e ->
            var score = 0
            val fams = families(e.name)
            fams.forEach { f -> if (Regex("(?<![a-z0-9])${Regex.escape(f)}").containsMatchIn(t) || (f.length >= 5 && f in compactText)) score += 2 }
            // Numbers of the name: power ("130") and designations ("A 180", "GLC 300").
            Regex("(?<![0-9.])(\\d{2,3})(?![0-9.])").findAll(e.name).map { it.value.toInt() }.toSet()
                .plus(e.powerHp).forEach { n -> if (n in numbers) score += 2 }
            Regex("(\\d\\.\\d)").find(e.name)?.value?.let { if (Regex("(?<![0-9])${Regex.escape(it)}(?![0-9])").containsMatchIn(t)) score += 1 }
            l.powerHp?.let { p -> if (kotlin.math.abs(p - e.powerHp) <= 6) score += 3 else if (kotlin.math.abs(p - e.powerHp) > 25) score -= 3 }
            l.fuel?.let { f -> if (f == e.fuel || (f == Fuel.HYBRIDE && e.fuel == Fuel.HYBRIDE_RECHARGEABLE)) score += 1 else score -= 5 }
            if (e.allWheel && !allWheelWords.containsMatchIn(t)) score -= 1
            e to score
        }.sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        val second = scored.getOrNull(1)?.second ?: Int.MIN_VALUE
        return best.first.takeIf { best.second >= 4 && best.second > second }
    }

    /** Engines of the user's choice that this listing can be (unknown engine but known power). */
    fun compatible(l: Listing, chosen: Collection<EngineSpec>): Boolean {
        if (l.engine != null) return chosen.any { it.name == l.engine }
        val p = l.powerHp ?: return true
        return chosen.any { kotlin.math.abs(it.powerHp - p) <= 6 && (l.fuel == null || l.fuel == it.fuel || (l.fuel == Fuel.HYBRIDE && it.fuel == Fuel.HYBRIDE_RECHARGEABLE)) }
    }
}
