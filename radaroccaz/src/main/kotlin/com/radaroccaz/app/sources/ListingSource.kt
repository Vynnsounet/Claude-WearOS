package com.radaroccaz.app.sources

import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria

/** One classified-ads website. */
interface ListingSource {
    val id: String
    val name: String
    /** Short line shown in the settings, e.g. "Particuliers et professionnels". */
    val description: String

    /** The site's own search page for these criteria, also offered to the user when scraping fails. */
    fun searchUrl(criteria: SearchCriteria): String

    /**
     * Raw listings for the criteria. They are filtered, de-duplicated and ranked afterwards,
     * so a source may return a few that do not match exactly.
     *
     * @throws SourceBlockedException when the site refuses automated access (captcha, 403).
     * @throws java.io.IOException on network errors.
     */
    suspend fun search(criteria: SearchCriteria): List<Listing>

    /** The listing completed from its own page (full description, history). Returns it unchanged when not supported. */
    suspend fun details(listing: Listing): Listing = listing
}

class SourceBlockedException(message: String) : Exception(message)

/** Downloads the site's search page and parses it. */
abstract class HtmlListingSource(private val fetcher: HttpFetcher) : ListingSource {
    override suspend fun search(criteria: SearchCriteria): List<Listing> =
        parse(fetcher.get(searchUrl(criteria)))

    abstract fun parse(html: String): List<Listing>

    override suspend fun details(listing: Listing): Listing = enrich(listing, fetcher.get(listing.url))

    /** Reads a listing page; sources with a known layout override it. */
    open fun enrich(listing: Listing, html: String): Listing = DetailExtractor.enrich(listing, html)
}
