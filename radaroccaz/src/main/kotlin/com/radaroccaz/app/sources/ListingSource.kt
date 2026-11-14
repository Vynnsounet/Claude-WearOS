package com.radaroccaz.app.sources

import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.domain.Ranking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Listings found on one site, and the page they came from (to open it in the browser). */
data class SourcePage(val listings: List<Listing>, val url: String)

/** One classified-ads website. */
interface ListingSource {
    val id: String
    val name: String
    /** Short line shown in the settings, e.g. "Particuliers et professionnels". */
    val description: String

    /**
     * The site's rules (robots.txt, terms) forbid automated reading of its search pages.
     * Such a source is only opened in the browser, unless the user turns automatic
     * reading on explicitly.
     */
    val restricted: Boolean get() = false

    /** The site's own search page for these criteria, opened in the browser. */
    fun searchUrl(criteria: SearchCriteria): String

    /**
     * Raw listings for the criteria. They are filtered, de-duplicated and ranked afterwards,
     * so a source may return a few that do not match exactly.
     *
     * @throws SourceBlockedException when the site refuses automated access (captcha, 403).
     * @throws java.io.IOException on network errors, [SourceUnavailableException] when no page exists for the model.
     */
    suspend fun search(criteria: SearchCriteria): SourcePage

    /** The listing completed from its own page (full description, history). Returns it unchanged when not supported. */
    suspend fun details(listing: Listing): Listing = listing
}

class SourceBlockedException(message: String) : Exception(message)

/** Downloads the site's search page (trying several URL spellings) and parses it. */
abstract class HtmlListingSource(protected val fetcher: HttpFetcher) : ListingSource {
    companion object {
        /** At most this many spellings are tried for one search on one site. */
        const val MAX_TRIES = 6
        /** Pause between two result pages of the same site, to stay gentle with it. */
        const val PAGE_DELAY_MS = 700L
        /** Enough listings matching the criteria: stop turning pages. */
        const val ENOUGH_MATCHES = 150
    }

    /** Result pages read at most per search (1 = no pagination). */
    open val maxPages: Int = 1

    /** URL of page [page] (2, 3...) of the results whose first page is [firstUrl]; null when the site has no pagination. */
    open fun pageUrl(firstUrl: String, page: Int): String? = null


    /** URLs to try in order; the first one that exists is read. */
    open fun searchUrls(criteria: SearchCriteria): List<String> = listOf(searchUrl(criteria))

    override fun searchUrl(criteria: SearchCriteria): String = searchUrls(criteria).first()

    /**
     * Tries the candidate URLs in order: a page that does not exist (404) or that holds no
     * listing of the requested model leads to the next one (another spelling, then the
     * make alone). The first relevant page wins; failing that, the first page that existed.
     */
    override suspend fun search(criteria: SearchCriteria): SourcePage {
        val urls = searchUrls(criteria).take(MAX_TRIES)
        var fallback: SourcePage? = null
        var missing: NotFoundException? = null
        for (url in urls) {
            val html = try {
                fetcher.get(url)
            } catch (e: NotFoundException) {
                missing = e
                continue
            }
            val page = SourcePage(parse(html), url)
            if (page.listings.any { Ranking.isRelevant(it, criteria) }) return SourcePage(morePages(page, criteria), url)
            if (fallback == null) fallback = page
        }
        return fallback ?: throw SourceUnavailableException("Ce modèle est introuvable sur ce site (${urls.size} adresses essayées).", missing)
    }

    abstract fun parse(html: String): List<Listing>

    /**
     * Reads the following result pages until [maxPages], an empty or repeated page, an error
     * (what was read is kept) or [ENOUGH_MATCHES] listings matching the criteria.
     */
    private suspend fun morePages(first: SourcePage, criteria: SearchCriteria): List<Listing> {
        val all = LinkedHashMap<String, Listing>()
        first.listings.forEach { all[it.id] = it }
        for (p in 2..maxPages) {
            if (all.values.count { Ranking.matches(it, criteria) } >= ENOUGH_MATCHES) break
            val url = pageUrl(first.url, p) ?: break
            delay(PAGE_DELAY_MS)
            val listings = try {
                parse(fetcher.get(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                break
            }
            val fresh = listings.filter { it.id !in all }
            if (fresh.isEmpty()) break
            fresh.forEach { all[it.id] = it }
        }
        return all.values.toList()
    }

    override suspend fun details(listing: Listing): Listing = enrich(listing, fetcher.get(listing.url))

    /** Reads a listing page; sources with a known layout override it. */
    open fun enrich(listing: Listing, html: String): Listing = DetailExtractor.enrich(listing, html)
}
