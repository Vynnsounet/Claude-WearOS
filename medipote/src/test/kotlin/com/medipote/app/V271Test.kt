package com.medipote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.Awakening
import com.medipote.app.data.SleepNight
import com.medipote.app.domain.Sleep
import com.medipote.app.ui.sleep.SleepEditorScreen
import com.medipote.app.ui.sleep.SleepScreen
import com.medipote.app.ui.theme.MediPoteTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 1.27.1: « nuit blanche », and what happens when sleep finally comes in the morning. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp-xhdpi")
class V271Test {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 19)
    private val bed = day.minusDays(1).atTime(23, 0)
    private val wake = day.atTime(7, 0)
    private val normal = SleepNight(day, bed, 15, wake, awakenings = listOf(Awakening(day.atTime(3, 0), 20, "Bruit")), quality = 3)
    private val blank = normal.copy(noSleep = true, awakenings = emptyList())

    @Test
    fun aSleeplessNightHasNoSleep_andSaysSo() {
        assertEquals(0, blank.asleepMinutes)
        assertEquals(0f, blank.efficiency, 0f)
        assertEquals(8 * 60, blank.inBedMinutes)
        assertEquals("nuit blanche", Sleep.label(blank))
        assertEquals("7 h 25", Sleep.label(normal.copy(awakenings = emptyList(), latencyMinutes = 35)))
        // One long awake stretch, nothing asleep.
        assertTrue(Sleep.segments(blank).none { it.asleep })
        assertEquals(0, Sleep.longestStretch(blank))
    }

    @Test
    fun aSleeplessNightHasNoTimeProblemsExceptTheClock() {
        assertTrue(Sleep.problems(blank.copy(latencyMinutes = 600)).isEmpty())
        assertEquals(1, Sleep.problems(blank.copy(wakeTime = bed.minusHours(1))).size)
    }

    @Test
    fun sleepFinallyCameLaterInTheMorning() {
        // Lights off at 23:00, asleep at 9:00: ten hours to fall asleep; the wake-up, not after, moves to 10:00.
        val later = Sleep.fellAsleepLater(blank, 9 * 60)
        assertFalse(later.noSleep)
        assertEquals(10 * 60, later.latencyMinutes)
        assertEquals(day.atTime(10, 0), later.wakeTime)
        assertEquals(60, later.asleepMinutes)
        assertTrue(Sleep.problems(later).isEmpty())
        // Asleep at 4:00 and up at 7:00: the wake-up stays.
        val early = Sleep.fellAsleepLater(blank, 4 * 60)
        assertEquals(wake, early.wakeTime)
        assertEquals(5 * 60, early.latencyMinutes)
        assertEquals(3 * 60, early.asleepMinutes)
        // With a later wake-up already chosen, it is kept.
        assertEquals(day.atTime(12, 0), Sleep.fellAsleepLater(blank.copy(wakeTime = day.atTime(12, 0)), 9 * 60).wakeTime)
    }

    @Test
    fun theWakeUpNeverLeavesTheMorning() {
        val late = Sleep.fellAsleepLater(blank, 23 * 60 + 40)
        assertEquals(day, late.wakeTime.toLocalDate())
    }

    @Test
    fun aSleeplessNightInOneTap() {
        val s = AppState(sleep = listOf(normal.copy(date = day.minusDays(1))))
        val night = Sleep.blankNight(s, day)
        assertTrue(night.noSleep)
        assertEquals(day, night.date)
        assertTrue(night.awakenings.isEmpty())
        assertNull(night.quality)
        assertTrue(Sleep.problems(night).isEmpty())
        // Saved like any night: one per date, and it can be replaced by a real one.
        val (saved, _) = Sleep.save(s, night, day.atTime(9, 0))
        assertEquals(1, saved.sleep.count { it.date == day })
        val (replaced, _) = Sleep.save(saved, Sleep.fellAsleepLater(night, 9 * 60), day.atTime(12, 0))
        assertFalse(replaced.sleep.first { it.date == day }.noSleep)
        assertEquals(1, replaced.sleep.count { it.date == day })
    }

    @Test
    fun aSleeplessNightCountsAsNoSleepButNothingMore() {
        val base = AppState(sleep = (1..4).map { normal.copy(date = day.minusDays(it.toLong()), bedtime = bed.minusDays(it.toLong()), wakeTime = wake.minusDays(it.toLong()), latencyMinutes = 20, awakenings = emptyList()) })
        val withBlank = base.copy(sleep = base.sleep + blank)
        val r0 = Sleep.report(base, day, 7)
        val r1 = Sleep.report(withBlank, day, 7)
        // The sleep average falls (it is real), but falling asleep, efficiency and usual times are untouched.
        assertTrue(r1.averageAsleep!! < r0.averageAsleep!!)
        assertEquals(r0.averageLatency, r1.averageLatency)
        assertEquals(r0.averageEfficiency!!.toDouble(), r1.averageEfficiency!!.toDouble(), 0.0001)
        assertEquals(r0.typicalBedtime, r1.typicalBedtime)
        assertEquals(r0.typicalWake, r1.typicalWake)
        assertEquals(r0.calmNights, r1.calmNights)
    }

    @Test
    fun theNextNightIsNotPrefilledWithASleeplessOne() {
        val s = AppState(sleep = listOf(blank.copy(date = day.minusDays(1), latencyMinutes = 600)))
        assertEquals(15, Sleep.newNight(s, day).latencyMinutes)
    }

    @Test
    fun anOldSaveLoadsAndASleeplessNightIsKept() {
        val old = AppRepository.JSON.encodeToString(AppState.serializer(), AppState(sleep = listOf(normal))).replace(",\"noSleep\":false", "")
        assertFalse(AppRepository.JSON.decodeFromString(AppState.serializer(), old).sleep.single().noSleep)
        val back = AppRepository.JSON.decodeFromString(AppState.serializer(), AppRepository.JSON.encodeToString(AppState.serializer(), AppState(sleep = listOf(blank))))
        assertTrue(back.sleep.single().noSleep)
    }

    @Test
    fun sleeplessNightsAreCountedAndNamed() {
        val base = AppState(sleep = (1..3).map { normal.copy(date = day.minusDays(it.toLong()), awakenings = emptyList()) })
        val s = base.copy(sleep = base.sleep + blank + blank.copy(date = day.minusDays(4)))
        val r = Sleep.report(s, day, 7)
        assertEquals(2, r.sleeplessNights)
        assertEquals(5, r.count)
        // They weigh in the average: three nights of 7 h 45 and two of nothing.
        assertEquals((3 * 465) / 5, r.averageAsleep)
        // And in the goal: they never reach it.
        assertEquals(0, Sleep.report(AppState(sleep = listOf(blank)), day, 7).goalNights)
        // The doctor's report says so.
        val report = com.medipote.app.domain.DoctorReports.build(s, day.atTime(12, 0), 7, setOf(com.medipote.app.domain.ReportPart.SLEEP))
        val text = report.sections.flatMap { it.blocks }.joinToString(" ") { it.toString() }
        assertTrue(text, "Nuits blanches" in text)
        // None, nothing said.
        assertEquals(0, Sleep.report(base, day, 7).sleeplessNights)
    }

    // ── Screens ──
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MediPoteTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }

    @Test
    fun theSleepPageOffersOneTapForASleeplessNight() {
        var blankTaps = 0
        show { SleepScreen(AppState(onboarded = true), day.atTime(9, 0), {}, {}, onBlankNight = { blankTaps++ }) }
        compose.onNodeWithText("🌙  Nuit blanche, je n'ai pas dormi").performScrollTo().performClick()
        assertEquals(1, blankTaps)
    }

    @Test
    fun aSleeplessNightIsShownAsSuch() {
        show { SleepScreen(AppState(onboarded = true, sleep = listOf(blank)), day.atTime(9, 0), {}, {}) }
        compose.onAllNodesWithText("Nuit blanche", substring = true)[0].assertExists()
        compose.onAllNodesWithText("de sommeil").assertCountEquals(0)
    }

    @Test
    fun theEditorShowsASleeplessNightWithItsWayOut() {
        var saved: SleepNight? = null
        show { SleepEditorScreen(AppState(onboarded = true, sleep = listOf(blank)), blank, day, day, onSave = { saved = it }, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("Finalement, je me suis endormi·e plus tard…").assertExists()
        compose.onAllNodesWithText("Réveils dans la nuit").assertCountEquals(0)
        compose.onNodeWithText("Enregistrer").performClick()
        assertTrue(saved!!.noSleep)
    }

    @Test
    fun theSleepPageAndTheStatsShowTheCount() {
        val s = AppState(onboarded = true, sleep = listOf(blank, normal.copy(date = day.minusDays(1), awakenings = emptyList())))
        show { SleepScreen(s, day.atTime(9, 0), {}, {}) }
        compose.onNodeWithText("Nuits blanches").performScrollTo().assertExists()
    }

    @Test
    fun theStatsTabShowsTheCountToo() {
        val s = AppState(onboarded = true, sleep = listOf(blank, normal.copy(date = day.minusDays(1), awakenings = emptyList())))
        show { com.medipote.app.ui.sleep.SleepStatsTab(s, day, 7, "7 jours") }
        compose.onNodeWithText("Nuits blanches").assertExists()
        compose.onNodeWithText("sur 2 nuits notées", substring = true).assertExists()
    }

    @Test
    fun aNormalNightKeepsItsEditor() {
        show { SleepEditorScreen(AppState(onboarded = true, sleep = listOf(normal)), normal, day, day, onSave = {}, onDelete = {}, onBack = {}) }
        compose.onNodeWithText("Réveils dans la nuit").performScrollTo().assertExists()
        compose.onAllNodesWithText("Finalement, je me suis endormi·e plus tard…").assertCountEquals(0)
        compose.onNodeWithText("🌙  Nuit blanche").assertExists()
    }
}
