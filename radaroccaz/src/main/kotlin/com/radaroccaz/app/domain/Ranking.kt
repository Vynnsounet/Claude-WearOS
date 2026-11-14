package com.radaroccaz.app.domain

import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerFilter
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.Text
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** Each part of the score, from 0 to 1 (null when it does not apply). */
data class ScoreBreakdown(
    val price: Double,
    val mileage: Double,
    val age: Double,
    val options: Double?,
    /** Value for money compared with similar cars in the results (0.5 = market price). */
    val deal: Double?,
)

data class ScoredListing(
    val listing: Listing,
    /** 0–100. */
    val score: Int,
    val breakdown: ScoreBreakdown,
    /** Estimated market price for its year and mileage, from the other results. */
    val marketPrice: Int?,
    /** Percent below (negative) or above (positive) [marketPrice]. */
    val dealPercent: Int?,
    val matchedOptions: Set<String>,
) {
    val verdict: Verdict get() = Verdict.of(score)
}

enum class Verdict(val label: String) {
    EXCELLENT("Excellente offre"), GOOD("Bonne offre"), FAIR("Offre correcte"), WEAK("Offre moyenne");

    companion object {
        fun of(score: Int) = when {
            score >= 80 -> EXCELLENT
            score >= 65 -> GOOD
            score >= 50 -> FAIR
            else -> WEAK
        }
    }
}

enum class SortOrder(val label: String) {
    BEST("Pertinence"),
    PRICE_ASC("Prix croissant"),
    DEAL("Meilleure affaire"),
    MILEAGE("Kilométrage"),
    RECENT("Plus récentes"),
}

object Ranking {

    /** Drops listings that contradict the criteria. Unknown values (no mileage given...) are kept. */
    fun matches(l: Listing, c: SearchCriteria): Boolean {
        if (l.price < MIN_PLAUSIBLE_PRICE) return false
        if (c.budgetMax != null && l.price > c.budgetMax) return false
        if (c.budgetMin != null && l.price < c.budgetMin) return false
        if (c.mileageMax != null && l.mileageKm != null && l.mileageKm > c.mileageMax) return false
        if (c.yearMin != null && l.year != null && l.year < c.yearMin) return false
        if (c.fuels.isNotEmpty() && l.fuel != null && l.fuel !in c.fuels &&
            !(l.fuel == Fuel.HYBRIDE_RECHARGEABLE && Fuel.HYBRIDE in c.fuels)
        ) return false
        if (c.gearbox != null && l.gearbox != null && l.gearbox != c.gearbox) return false
        when (c.seller) {
            SellerFilter.PARTICULIER -> if (l.sellerType == SellerType.PRO) return false
            SellerFilter.PRO -> if (l.sellerType == SellerType.PARTICULIER) return false
            SellerFilter.ALL -> Unit
        }
        val dep = c.department.trim()
        if (dep.isNotEmpty() && l.postalCode != null && !l.postalCode.startsWith(dep)) return false
        return isRelevant(l, c)
    }

    /** The listing is about the requested car (sites' free-text search returns accessories, other models...). */
    fun isRelevant(l: Listing, c: SearchCriteria): Boolean {
        val haystack = Text.normalize(listOfNotNull(l.make, l.model, l.title).joinToString(" ")).replace('-', ' ')
        val compactHay = Text.compact(haystack)
        fun contains(term: String): Boolean {
            val compact = Text.compact(term)
            if (compact.isEmpty()) return true
            if (compact.length <= 2 || compact.all { it.isDigit() }) {
                val words = Text.normalize(term).replace('-', ' ')
                return Regex("(?<![a-z0-9])${Regex.escape(words)}(?![a-z0-9])").containsMatchIn(haystack)
            }
            return compact in compactHay
        }
        val makeOk = c.make.isBlank() || contains(c.make) ||
            // "Mercedes-Benz" is often just "Mercedes"
            contains(c.make.substringBefore('-').substringBefore(' '))
        val modelOk = c.model.isBlank() || contains(c.model)
        // A title may omit the make ("308 SW GT Line") when the model is explicit.
        return modelOk && (makeOk || (c.model.isNotBlank() && l.make == null))
    }

    /** The same car posted on several sites: keeps the copy with the most photos and notes the others. */
    fun deduplicate(listings: List<Listing>): List<Listing> {
        val groups = LinkedHashMap<String, MutableList<Listing>>()
        val singles = mutableListOf<Listing>()
        for (l in listings) {
            if (l.mileageKm == null || l.year == null) { singles += l; continue }
            val key = "${l.price}|${l.mileageKm / 500}|${l.year}|${Text.compact(l.make ?: l.title.substringBefore(' '))}"
            groups.getOrPut(key) { mutableListOf() } += l
        }
        val merged = groups.values.flatMap { group ->
            val bySource = group.distinctBy { it.source }
            if (bySource.size < 2) return@flatMap group
            val best = bySource.maxBy { it.imageUrls.size + it.description.length / 500 }
            listOf(best.copy(alsoOn = (bySource - best).map { it.source })) + (group - bySource.toSet())
        }
        return merged + singles
    }

    fun score(listings: List<Listing>, c: SearchCriteria): List<ScoredListing> {
        if (listings.isEmpty()) return emptyList()
        val market = MarketModel.fit(listings)
        val prices = listings.map { it.price }
        val pMin = prices.min()
        val pMax = maxOf(prices.max(), c.budgetMax ?: 0)
        val kmRef = c.mileageMax ?: listings.mapNotNull { it.mileageKm }.maxOrNull() ?: 0
        val years = listings.mapNotNull { it.year }
        val yMax = years.maxOrNull()
        val yMin = c.yearMin?.let { minOf(it, yMax ?: it) } ?: years.minOrNull()
        val p = c.priorities

        return listings.map { l ->
            val cheapness = if (pMax > pMin) 1.0 - (l.price - pMin).toDouble() / (pMax - pMin) else 1.0
            val expected = market?.expected(l)
            val dealPercent = expected?.let { ((l.price - it) * 100.0 / it).roundToInt() }
            val deal = dealPercent?.let { (0.5 - it / 50.0).coerceIn(0.0, 1.0) }
            val priceScore = if (deal != null) 0.45 * cheapness + 0.55 * deal else cheapness
            val km = l.mileageKm?.let { if (kmRef > 0) (1.0 - it.toDouble() / kmRef).coerceIn(0.0, 1.0) else 1.0 } ?: UNKNOWN
            val age = l.year?.let { y ->
                if (yMax == null || yMin == null || yMax == yMin) 1.0 else ((y - yMin).toDouble() / (yMax - yMin)).coerceIn(0.0, 1.0)
            } ?: UNKNOWN
            val matched = l.options intersect c.wantedOptions
            val options = if (c.wantedOptions.isEmpty()) null else matched.size.toDouble() / c.wantedOptions.size

            val parts = listOfNotNull(
                priceScore to p.price,
                km to p.mileage,
                age to p.age,
                options?.let { it to p.options },
            )
            val totalWeight = parts.sumOf { it.second }
            val value = if (totalWeight == 0) parts.sumOf { it.first } / parts.size
            else parts.sumOf { it.first * it.second } / totalWeight
            ScoredListing(
                listing = l,
                score = (value * 100).roundToInt().coerceIn(0, 100),
                breakdown = ScoreBreakdown(priceScore, km, age, options, deal),
                marketPrice = expected,
                dealPercent = dealPercent,
                matchedOptions = matched,
            )
        }
    }

    fun sort(list: List<ScoredListing>, order: SortOrder): List<ScoredListing> = when (order) {
        SortOrder.BEST -> list.sortedWith(compareByDescending<ScoredListing> { it.score }.thenBy { it.listing.price })
        SortOrder.PRICE_ASC -> list.sortedWith(compareBy<ScoredListing> { it.listing.price }.thenByDescending { it.score })
        SortOrder.DEAL -> list.sortedWith(compareBy<ScoredListing> { it.dealPercent ?: Int.MAX_VALUE }.thenByDescending { it.score })
        SortOrder.MILEAGE -> list.sortedWith(compareBy<ScoredListing> { it.listing.mileageKm ?: Int.MAX_VALUE }.thenBy { it.listing.price })
        SortOrder.RECENT -> list.sortedWith(compareByDescending<ScoredListing> { it.listing.year ?: 0 }.thenBy { it.listing.price })
    }

    /** Filter, merge duplicates and score in one go. */
    fun rank(raw: List<Listing>, c: SearchCriteria): List<ScoredListing> =
        score(deduplicate(raw.filter { matches(it, c) }.distinctBy { it.id }), c)

    const val MIN_PLAUSIBLE_PRICE = 500
    private const val UNKNOWN = 0.35
}

/**
 * ln(price) = a + b·age + c·mileage, fitted on the results themselves, gives a market price
 * for each car's year and mileage. Needs a handful of listings with both values.
 */
class MarketModel private constructor(private val a: Double, private val b: Double, private val c: Double, private val refYear: Int) {

    fun expected(l: Listing): Int? {
        val year = l.year ?: return null
        val km = l.mileageKm ?: return null
        return exp(a + b * (refYear - year) + c * km / 10_000.0).roundToInt()
    }

    companion object {
        const val MIN_SAMPLES = 6

        fun fit(listings: List<Listing>): MarketModel? {
            val pts = listings.filter { it.year != null && it.mileageKm != null && it.price > 0 }
            if (pts.size < MIN_SAMPLES) return null
            val refYear = pts.maxOf { it.year!! }
            val xs = pts.map { doubleArrayOf(1.0, (refYear - it.year!!).toDouble(), it.mileageKm!! / 10_000.0) }
            val ys = pts.map { ln(it.price.toDouble()) }
            val ageVaries = xs.map { it[1] }.distinct().size > 1
            val kmVaries = xs.map { it[2] }.distinct().size > 1
            val cols = listOfNotNull(0, if (ageVaries) 1 else null, if (kmVaries) 2 else null)
            val beta = leastSquares(xs.map { row -> cols.map { row[it] }.toDoubleArray() }, ys) ?: return null
            val coef = DoubleArray(3).also { arr -> cols.forEachIndexed { i, col -> arr[col] = beta[i] } }
            // Cars lose value with age and mileage; a positive slope means the data is too noisy.
            if (coef[1] > 0.05 || coef[2] > 0.05) return null
            return MarketModel(coef[0], coef[1], coef[2], refYear)
        }

        /** Solves the normal equations XᵀX β = Xᵀy by Gaussian elimination. */
        private fun leastSquares(x: List<DoubleArray>, y: List<Double>): DoubleArray? {
            val n = x.first().size
            val m = Array(n) { DoubleArray(n + 1) }
            for (r in x.indices) for (i in 0 until n) {
                for (j in 0 until n) m[i][j] += x[r][i] * x[r][j]
                m[i][n] += x[r][i] * y[r]
            }
            for (col in 0 until n) {
                val pivot = (col until n).maxBy { abs(m[it][col]) }
                if (abs(m[pivot][col]) < 1e-9) return null
                val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
                for (r in 0 until n) if (r != col) {
                    val f = m[r][col] / m[col][col]
                    for (k in col..n) m[r][k] -= f * m[col][k]
                }
            }
            return DoubleArray(n) { m[it][n] / m[it][it] }
        }
    }
}
