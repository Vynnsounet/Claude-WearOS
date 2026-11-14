package com.radaroccaz.app

import com.radaroccaz.app.sources.HttpFetcher
import com.radaroccaz.app.sources.LeboncoinSource
import com.radaroccaz.app.sources.SourceBlockedException
import java.io.IOException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpFetcherTest {
    private val server = MockWebServer()
    private val fetcher = HttpFetcher()

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    @Test
    fun returnsPageAndSendsBrowserHeaders() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("leboncoin_search.html")).build())
        val html = fetcher.get(server.url("/recherche").toString())
        assertEquals(3, LeboncoinSource(fetcher).parse(html).size)
        val request = server.takeRequest()
        assertTrue(request.headers["User-Agent"]!!.contains("Mozilla"))
        assertTrue(request.headers["Accept-Language"]!!.startsWith("fr-FR"))
    }

    @Test
    fun forbiddenIsBlocked() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("Forbidden").build())
        expectBlocked()
    }

    @Test
    fun captchaPageIsBlocked() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("captcha.html")).build())
        expectBlocked()
    }

    @Test
    fun serverErrorIsIoException() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        try {
            fetcher.get(server.url("/").toString())
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    private suspend fun expectBlocked() {
        try {
            fetcher.get(server.url("/").toString())
            fail("expected SourceBlockedException")
        } catch (_: SourceBlockedException) {
        }
    }
}
