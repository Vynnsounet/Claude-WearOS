package com.radaroccaz.app

import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.sources.HtmlListingSource
import com.radaroccaz.app.sources.HttpFetcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A site whose pages list "id;price" lines. */
private class PagedSource(private val base: String, override val maxPages: Int) : HtmlListingSource(HttpFetcher()) {
    override val id = "paged"
    override val name = "Paged"
    override val description = ""
    override fun searchUrls(criteria: SearchCriteria) = listOf("$base/peugeot/308/?x=1")
    override fun pageUrl(firstUrl: String, page: Int) = "$firstUrl&page=$page"
    override fun parse(html: String): List<Listing> = html.lines().filter { ';' in it }.map { line ->
        val (id, price) = line.split(';')
        car(id, price.trim().toInt(), source = "paged")
    }
}

class PaginationTest {
    private val server = MockWebServer()
    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun page(vararg ids: String) = MockResponse.Builder().body(ids.joinToString("\n") { "$it;10000" }).build()
    private val c = SearchCriteria(make = "Peugeot", model = "308")

    @Test
    fun readsFollowingPagesUntilARepeatedOne() = runTest {
        server.enqueue(page("1", "2"))
        server.enqueue(page("3", "4"))
        server.enqueue(page("3", "4")) // the site repeats its last page
        val result = PagedSource(server.url("/").toString().trimEnd('/'), maxPages = 6).search(c)
        assertEquals(listOf("1", "2", "3", "4"), result.listings.map { it.id.substringAfter(':') })
        assertEquals(3, server.requestCount)
        assertEquals("/peugeot/308/?x=1&page=2", server.takeRequest().let { server.takeRequest() }.target)
    }

    @Test
    fun keepsWhatWasReadWhenALaterPageFails() = runTest {
        server.enqueue(page("1", "2"))
        server.enqueue(MockResponse.Builder().code(500).build())
        val result = PagedSource(server.url("/").toString().trimEnd('/'), maxPages = 4).search(c)
        assertEquals(2, result.listings.size)
    }

    @Test
    fun respectsMaxPages() = runTest {
        repeat(5) { i -> server.enqueue(page("a$i", "b$i")) }
        val result = PagedSource(server.url("/").toString().trimEnd('/'), maxPages = 2).search(c)
        assertEquals(4, result.listings.size)
        assertEquals(2, server.requestCount)
    }
}
