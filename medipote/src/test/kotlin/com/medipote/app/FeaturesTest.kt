package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DoseTime
import com.medipote.app.data.PillPack
import com.medipote.app.data.PillRegimen
import com.medipote.app.data.Settings
import com.medipote.app.data.Treatment
import com.medipote.app.data.WaterSettings
import com.medipote.app.domain.Cycle
import com.medipote.app.domain.CyclePhase
import com.medipote.app.domain.Game
import com.medipote.app.domain.Rules
import com.medipote.app.domain.Schedule
import com.medipote.app.domain.Shop
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.AlarmSpec
import com.medipote.app.notifications.ReminderPlanner
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {

    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0, date: LocalDate = day) = date.atTime(h, m)

    // ── Pill ──

    private val pack = PillPack(21, 7, placebo = false, packStart = LocalDate.of(2026, 11, 1))
    private val pill = Treatment(
        "pill", "levonorgestrel_ee", "Leeloo", doses = listOf(DoseTime(21 * 60)),
        startDate = pack.packStart, pill = pack, createdAt = pack.packStart.atStartOfDay(),
    )

    @Test
    fun pillFollowsTheBreakWeek() {
        assertEquals(11, pack.position(day))
        assertTrue(pill.isActiveOn(LocalDate.of(2026, 11, 21))) // pill 21
        assertFalse(pill.isActiveOn(LocalDate.of(2026, 11, 22))) // break
        assertFalse(pill.isActiveOn(LocalDate.of(2026, 11, 28)))
        assertTrue(pill.isActiveOn(LocalDate.of(2026, 11, 29))) // next pack, no gap
        assertEquals(LocalDate.of(2026, 11, 29), pack.currentPackStart(LocalDate.of(2026, 12, 3)))
        assertTrue(Schedule.occurrencesOn(listOf(pill), LocalDate.of(2026, 11, 25)).isEmpty())
    }

    @Test
    fun placeboAndContinuousPillsAreTakenEveryDay() {
        val placebo = pill.copy(pill = pack.copy(placebo = true))
        assertTrue(placebo.isActiveOn(LocalDate.of(2026, 11, 25)))
        assertTrue(placebo.pill!!.isBreak(LocalDate.of(2026, 11, 25)))
        val continuous = pill.copy(pill = PillPack(28, 0, packStart = pack.packStart))
        assertTrue((0..60).all { continuous.isActiveOn(pack.packStart.plusDays(it.toLong())) })
        assertEquals(PillRegimen.CONTINUOUS, PillRegimen.of(continuous.pill!!))
    }

    // ── Stock ──

    @Test
    fun stockDecreasesAndWarnsOnce() {
        val t = Treatment(
            "m", name = "Metformine", doses = listOf(DoseTime(8 * 60), DoseTime(20 * 60)), startDate = day,
            stock = 16.0, packSize = 30, lowStockDays = 7, createdAt = day.atStartOfDay(),
        )
        assertEquals(2.0, t.dailyUse, 0.001)
        assertEquals(8, t.daysLeft)
        assertFalse(t.isLowStock)
        var state = AppState(treatments = listOf(t), onboarded = true)
        val first = Game.give(state, Schedule.itemsOn(state.treatments, emptyMap(), day, at(8)).first(), at(8))!!
        assertEquals(15.0, first.state.treatments.single().stock!!, 0.001)
        assertNotNull(first.lowStock) // 7 days left: warn now
        state = first.state
        val logs = state.doseLogs.associateBy { it.key }
        val second = Game.give(state, Schedule.itemsOn(state.treatments, logs, day, at(20)).last(), at(20))!!
        assertNull(second.lowStock) // already warned
        assertEquals(14.0, second.state.treatments.single().stock!!, 0.001)
    }

    @Test
    fun pillStockAccountsForBreaks() {
        val withStock = pill.copy(stock = 42.0)
        assertEquals(0.75, withStock.dailyUse, 0.001)
        assertEquals(56, withStock.daysLeft)
    }

    // ── Journal and water ──

    @Test
    fun journalBonusOncePerDay() {
        val state = AppState(onboarded = true)
        val (s1, b1) = Game.saveEntry(state, DayEntry(day, physical = 4, mental = 3), at(9))
        assertEquals(Rules.JOURNAL_BONUS, b1)
        val (s2, b2) = Game.saveEntry(s1, s1.entryFor(day).copy(notes = "Mieux"), at(18))
        assertEquals(0, b2)
        assertEquals("Mieux", s2.entryFor(day).notes)
        assertEquals(4, s2.entryFor(day).physical)
        assertEquals(Rules.JOURNAL_BONUS, s2.game.points)
        // Past days don't earn the bonus
        assertEquals(0, Game.saveEntry(s2, DayEntry(day.minusDays(2), mental = 2), at(19)).second)
        // An emptied entry is removed
        assertTrue(Game.saveEntry(s2, DayEntry(day), at(20)).first.journal.isEmpty())
    }

    @Test
    fun waterGoalBonus() {
        var state = AppState(onboarded = true, settings = Settings(water = WaterSettings(goalGlasses = 3)))
        var bonuses = 0
        repeat(4) {
            val (s, b) = Game.addWater(state, 1, at(10 + it))
            state = s
            bonuses += b
        }
        assertEquals(4, state.entryFor(day).waterGlasses)
        assertEquals(Rules.WATER_BONUS, bonuses)
        assertEquals(0, state.game.journalRewardDays.size)
        state = Game.addWater(state, -10, at(16)).first
        assertEquals(0, state.entryFor(day).waterGlasses)
    }

    @Test
    fun waterReminderTimes() {
        assertEquals(listOf(540, 660, 780, 900, 1020, 1140, 1260), WaterSettings().reminderMinutes)
        assertTrue(WaterSettings(startMinute = 600, endMinute = 500).reminderMinutes.isEmpty())
    }

    // ── Reminder planner ──

    @Test
    fun plannerCoversAllReminderKinds() {
        val med = Treatment(
            "m", name = "Amlodipine", doses = listOf(DoseTime(12 * 60)), startDate = day,
            stock = 3.0, createdAt = day.atStartOfDay(),
        )
        val state = AppState(
            onboarded = true,
            treatments = listOf(med),
            appointments = listOf(Appointment("a1", "Dentiste", dateTime = at(15, 0, day.plusDays(3)), reminderMinutes = listOf(1440, 60))),
            settings = Settings(water = WaterSettings(enabled = true, startMinute = 9 * 60, endMinute = 13 * 60, intervalMinutes = 120)),
        )
        val plan = ReminderPlanner.plan(state, at(11, 50))
        val kinds = plan.groupBy { it.kind }
        assertEquals(listOf(at(12), at(12, 0, day.plusDays(1))), kinds[AlarmKind.DOSE]!!.map { it.time })
        assertEquals(2, kinds[AlarmKind.APPOINTMENT]!!.size)
        assertTrue(kinds[AlarmKind.APPOINTMENT]!!.any { it.time == at(15, 0, day.plusDays(2)) })
        assertEquals(listOf(at(13), at(9, 0, day.plusDays(1)), at(11, 0, day.plusDays(1)), at(13, 0, day.plusDays(1))), kinds[AlarmKind.WATER]!!.map { it.time })
        assertEquals(listOf(at(10, 0, day.plusDays(1))), kinds[AlarmKind.STOCK]!!.map { it.time })
        // Keys round-trip
        plan.forEach { assertEquals(it, AlarmSpec.parse(it.key)) }
        // Nothing when notifications are off
        assertTrue(ReminderPlanner.plan(state.copy(settings = state.settings.copy(notificationsEnabled = false)), at(11)).isEmpty())
    }

    // ── Cycle phases ──

    @Test
    fun fourPhasesCoverTheCycle() {
        val phases = Cycle.phases(28, 5)
        assertEquals(listOf(CyclePhase.MENSTRUAL, CyclePhase.FOLLICULAR, CyclePhase.OVULATION, CyclePhase.LUTEAL), phases.map { it.phase })
        assertEquals(1, phases.first().firstDay)
        assertEquals(28, phases.last().lastDay)
        assertEquals(28, phases.sumOf { it.days })
        assertEquals(10..16, phases[2].firstDay..phases[2].lastDay)
        // Very short cycle: no gap and no overlap
        val short = Cycle.phases(21, 7)
        assertEquals(21, short.sumOf { it.days })
    }

    // ── Shop ──

    @Test
    fun shopHasAllSlotsAndUniqueIds() {
        assertEquals(Shop.items.size, Shop.items.map { it.id }.toSet().size)
        assertTrue(Shop.items.size >= 55)
        com.medipote.app.domain.Slot.entries.forEach { slot -> assertTrue(Shop.items.any { it.slot == slot }) }
    }
}
