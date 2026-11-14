package com.radaroccaz.app

import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.domain.SearchEngine
import com.radaroccaz.app.domain.SourceState
import com.radaroccaz.app.sources.DemoSource
import com.radaroccaz.app.sources.ListingSource
import com.radaroccaz.app.sources.SourceBlockedException
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSource(
    override val id: String,
    private val delayMs: Long = 0,
    private val result: () -> List<Listing>,
) : ListingSource {
    override val name = id.replaceFirstChar { it.uppercase() }
    override val description = ""
    override fun searchUrl(criteria: SearchCriteria) = "https://$id.example/search"
    override suspend fun search(criteria: SearchCriteria): List<Listing> {
        delay(delayMs)
        return result()
    }
}

class SearchEngineTest {
    private val criteria = SearchCriteria(make = "Peugeot", model = "308", budgetMax = 15_000)

    @Test
    fun combinesSourcesAndReportsEachStatus() = runTest {
        val engine = SearchEngine(listOf(
            FakeSource("a", 10) { listOf(car("1", 12_000, source = "a"), car("2", 18_000, source = "a")) },
            FakeSource("b", 20) { listOf(car("3", 11_000, source = "b")) },
            FakeSource("blocked", 5) { throw SourceBlockedException("captcha") },
            FakeSource("broken", 5) { throw IOException("HTTP 500") },
            FakeSource("off") { error("must not be called") },
        ))
        val steps = engine.search(criteria, setOf("a", "b", "blocked", "broken")).toList()
        assertTrue(steps.first().statuses.all { it.state == SourceState.LOADING })
        assertEquals(5, steps.size) // initial + one per source
        val last = steps.last()
        assertTrue(last.done)
        val byId = last.statuses.associateBy { it.sourceId }
        assertEquals(SourceState.OK, byId.getValue("a").state)
        assertEquals(1, byId.getValue("a").count) // 18 000 € is over budget
        assertEquals(SourceState.BLOCKED, byId.getValue("blocked").state)
        assertEquals(SourceState.ERROR, byId.getValue("broken").state)
        assertEquals("HTTP 500", byId.getValue("broken").message)
        assertEquals(setOf("a:1", "b:3"), last.results.map { it.listing.id }.toSet())
        assertFalse("off" in byId)
    }

    @Test
    fun slowSourceTimesOut() = runTest {
        val engine = SearchEngine(listOf(FakeSource("slow", 60_000) { emptyList() }), timeoutMs = 1_000)
        val last = engine.searchAll(criteria, setOf("slow"))
        assertEquals(SourceState.ERROR, last.statuses.single().state)
    }

    @Test
    fun demoSourceIsStableAndMatchesCriteria() = runTest {
        val demo = DemoSource(today = LocalDate.of(2026, 11, 14), latencyMs = 0)
        val c = criteria.copy(mileageMax = 100_000, yearMin = 2017)
        assertEquals(demo.generate(c), demo.generate(c))
        val engine = SearchEngine(listOf(demo))
        val results = engine.searchAll(c, setOf(DemoSource.ID)).results
        assertTrue(results.size >= 5)
        results.forEach {
            assertTrue(it.listing.price <= 15_000)
            assertTrue(it.listing.mileageKm!! <= 100_000)
            assertTrue(it.listing.year!! >= 2017)
        }
    }
}
