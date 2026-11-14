package com.radaroccaz.app

import androidx.test.core.app.ApplicationProvider
import com.radaroccaz.app.data.Alert
import com.radaroccaz.app.data.AlertFrequency
import com.radaroccaz.app.data.AppRepository
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.domain.SearchEngine
import com.radaroccaz.app.sources.SourceBlockedException
import com.radaroccaz.app.work.AlertChecker
import com.radaroccaz.app.work.AlertScheduler
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AlertCheckerTest {
    private val criteria = SearchCriteria(make = "Peugeot", model = "308", budgetMax = 15_000)
    private var listings: List<Listing> = emptyList()
    private var blocked = false
    private var clock = 1_000_000_000L

    private fun repo(): AppRepository {
        val dir = ApplicationProvider.getApplicationContext<android.content.Context>().filesDir
        return AppRepository(File(dir, "test-${System.nanoTime()}.json"))
    }

    private fun checker(repo: AppRepository) = AlertChecker(
        repo,
        SearchEngine(listOf(FakeSource("leboncoin") {
            if (blocked) throw SourceBlockedException("captcha") else listings
        })),
        { setOf("leboncoin") },
        { clock },
    )

    @Test
    fun reportsOnlyNewListingsOnce() = runTest {
        val repo = repo()
        listings = listOf(car("1", 12_000), car("2", 13_000))
        repo.update { it.copy(alerts = listOf(Alert("a", "308", criteria, AlertFrequency.HOUR_1, seenIds = listOf("leboncoin:1")))) }
        val checker = checker(repo)

        val first = checker.check()
        assertEquals(listOf("leboncoin:2"), first.single().newListings.map { it.listing.id })
        val alert = repo.state.value.alerts.single()
        assertEquals(clock, alert.lastCheckedAt)
        assertEquals(listOf("leboncoin:2"), alert.unread.map { it.id })
        assertTrue("leboncoin:2" in alert.seenIds)

        // Not due yet: nothing runs.
        listings = listings + car("3", 9_000)
        clock += 20 * 60_000
        assertTrue(checker.check().isEmpty())

        // An hour later the new one is found; the old ones are not reported again.
        clock += 45 * 60_000
        val second = checker.check()
        assertEquals(listOf("leboncoin:3"), second.single().newListings.map { it.listing.id })
        assertEquals(listOf("leboncoin:3", "leboncoin:2"), repo.state.value.alerts.single().unread.map { it.id })
    }

    @Test
    fun disabledAlertsAreSkippedAndErrorsRecorded() = runTest {
        val repo = repo()
        listings = listOf(car("1", 12_000))
        repo.update {
            it.copy(alerts = listOf(
                Alert("off", "off", criteria, enabled = false),
                Alert("on", "on", criteria),
            ))
        }
        blocked = true
        assertTrue(checker(repo).check(force = true).isEmpty())
        val byId = repo.state.value.alerts.associateBy { it.id }
        assertNull(byId.getValue("off").lastCheckedAt)
        assertNotNull(byId.getValue("on").lastError)
        assertTrue(byId.getValue("on").seenIds.isEmpty())

        blocked = false
        assertEquals(1, checker(repo).check(force = true).size)
        assertNull(repo.state.value.alerts.first { it.id == "on" }.lastError)
    }

    @Test
    fun schedulerUsesTheShortestFrequency() {
        assertNull(AlertScheduler.intervalMinutes(emptyList()))
        val alerts = listOf(
            Alert("a", "a", criteria, AlertFrequency.DAY_1),
            Alert("b", "b", criteria, AlertFrequency.HOUR_3),
            Alert("c", "c", criteria, AlertFrequency.MIN_30, enabled = false),
        )
        assertEquals(180L, AlertScheduler.intervalMinutes(alerts))
        assertFalse(AlertScheduler.intervalMinutes(listOf(Alert("d", "d", criteria, AlertFrequency.MIN_30)))!! < 15)
    }

    @Test
    fun repositoryPersistsState() {
        val dir = ApplicationProvider.getApplicationContext<android.content.Context>().filesDir
        val file = File(dir, "persist.json").apply { delete() }
        val repo = AppRepository(file)
        repo.update { it.copy(favorites = listOf(car("fav", 10_000)), lastCriteria = criteria) }
        Thread.sleep(300)
        val reloaded = AppRepository(file)
        assertEquals("leboncoin:fav", reloaded.state.value.favorites.single().id)
        assertEquals(criteria, reloaded.state.value.lastCriteria)
    }
}
