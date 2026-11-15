package com.medipote.app

import androidx.test.core.app.ApplicationProvider
import com.medipote.app.data.AppState
import com.medipote.app.data.GameState
import com.medipote.app.data.Profile
import com.medipote.app.ui.AppViewModel
import com.medipote.app.ui.UiEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The streak screen on the first opening of a day, and events sent before the screen listens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreakOnLaunchTest {
    private val app get() = ApplicationProvider.getApplicationContext<MediPoteApplication>()
    private val repo get() = app.repository

    // The data file outlives a test: leave it as found, or the next test starts onboarded.
    @Before
    fun before() = clean()

    @After
    fun clean() {
        repo.reset()
        repo.flush()
    }

    private fun seed(streak: Int, lastCheckIn: java.time.LocalDate) =
        repo.update { AppState(onboarded = true, profile = Profile(name = "Sam"), game = GameState(streak = streak, bestStreak = streak, lastCheckIn = lastCheckIn)) }

    @Test
    fun theFirstOpeningOfANewDayCountsTheStreakAndKeepsTheScreenForLater() {
        seed(4, repo.now().toLocalDate().minusDays(1))
        val vm = AppViewModel(app)
        // The app comes to the foreground before any screen is listening: nothing may be lost.
        vm.onResume()
        assertEquals(5, repo.state.value.game.streak)
        val shown = vm.checkIn.value
        assertNotNull(shown)
        assertEquals(5, shown!!.reward.streak)
        // Still there for a screen that appears later (or after a rotation), until it is dismissed.
        assertEquals(shown, vm.checkIn.value)
        vm.checkInShown()
        assertNull(vm.checkIn.value)

        // Second opening the same day: nothing more.
        vm.onResume()
        assertEquals(5, repo.state.value.game.streak)
        assertNull(vm.checkIn.value)
    }

    @Test
    fun theScreenCanBeSeenAgainOnDemandWithoutCountingAnything() {
        seed(4, repo.now().toLocalDate())
        val vm = AppViewModel(app)
        vm.onResume()
        assertNull(vm.checkIn.value)
        vm.previewStreak()
        assertEquals(4, vm.checkIn.value!!.reward.streak)
        assertEquals(4, repo.state.value.game.streak)
        assertEquals(0, repo.state.value.game.points)
    }

    @Test
    fun eventsSentBeforeAScreenListensAreDeliveredOnceInOrder() = runBlocking {
        seed(1, repo.now().toLocalDate())
        val vm = AppViewModel(app)
        vm.message("premier")
        vm.message("second")
        assertEquals("premier", (vm.events.first() as UiEvent.Message).text)
        assertEquals("second", (vm.events.first() as UiEvent.Message).text)
    }
}
