package com.radaroccaz.app.sources

import com.radaroccaz.app.data.CarCatalog
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.Text

/**
 * Each site writes makes and models its own way in its URLs ("mercedes-benz/classe-a",
 * "mercedes/classe-a", "serie-3-tous"...). A source tries the candidates in order and
 * falls back to the make alone, then filters the models itself: an unusual name gives
 * a slightly wider page instead of an error 404.
 */
object SiteNames {

    /** "Série 3" → "serie-3", "ID.3" → "id-3", "C3 Aircross" → "c3-aircross". */
    fun slug(s: String): String = Text.normalize(s).replace("+", " plus ").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Make slugs to try, most likely first: canonical name, then common short forms. */
    fun makeSlugs(make: String, prefer: Map<String, String> = emptyMap()): List<String> {
        val canonical = CarCatalog.canonicalMake(make)
        val base = slug(canonical)
        return listOfNotNull(
            prefer[base],
            base,
            slug(make),
            base.removeSuffix("-benz").removeSuffix("-automobiles"),
            base.substringBefore('-').takeIf { it.length >= 3 },
        ).distinct().filter { it.isNotEmpty() }
    }

    /** Model slugs to try: exact, AutoScout24's "-tous" group, without the make's prefix ("classe-a" → "a"). */
    fun modelSlugs(model: String, prefer: Map<String, String> = emptyMap()): List<String> {
        if (model.isBlank()) return emptyList()
        val base = slug(model)
        return listOfNotNull(
            prefer[base],
            base,
            base.replace("-", ""),
            base.substringBefore('/').takeIf { '/' in model },
            slug(model.substringBefore('/')).takeIf { '/' in model },
        ).distinct().filter { it.isNotEmpty() }
    }

    /** Ordered URLs: every make × model candidate, then every make alone. */
    fun candidates(criteria: SearchCriteria, makePrefer: Map<String, String> = emptyMap(), modelPrefer: Map<String, String> = emptyMap(), build: (make: String, model: String?) -> String): List<String> {
        val makes = makeSlugs(criteria.make, makePrefer)
        val models = modelSlugs(criteria.model, modelPrefer).let { list ->
            // Several sites file Mercedes' GLA, GLC, CLA... under "classe-gla", "classe-glc".
            if (Text.compact(CarCatalog.canonicalMake(criteria.make)).startsWith("mercedes") && list.isNotEmpty() && !list.first().startsWith("classe"))
                list + "classe-${list.first()}" else list
        }
        return (makes.flatMap { mk -> models.map { build(mk, it) } } + makes.map { build(it, null) }).distinct()
    }
}

class SourceUnavailableException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
