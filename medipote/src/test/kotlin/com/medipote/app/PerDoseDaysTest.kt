package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.Settings
import com.medipote.app.data.Treatment
import com.medipote.app.domain.DoctorReports
import com.medipote.app.domain.Game
import com.medipote.app.domain.ReportBlock
import com.medipote.app.domain.ReportPart
import com.medipote.app.domain.Schedule
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One treatment taken at different times on different days: 10:00 on weekdays, 12:00 at weekends… */
class PerDoseDaysTest {
    // Monday 2026-11-16 … Sunday 2026-11-22.
    private val monday = LocalDate.of(2026, 11, 16)
    private fun d(offset: Int) = monday.plusDays(offset.toLong())
    private fun at(h: Int, m: Int = 0, date: LocalDate = monday) = date.atTime(h, m)

    private val weekdays = setOf(1, 2, 3, 4, 5)
    private val weekend = setOf(6, 7)

    private fun levo(vararg doses: DoseTime, daysOfWeek: Set<Int> = Treatment.ALL_DAYS) =
        Treatment("levo", name = "Lévothyrox", strength = "75 µg", doses = doses.toList(), daysOfWeek = daysOfWeek, startDate = monday.minusDays(60), createdAt = monday.minusDays(60).atStartOfDay())

    private fun times(t: Treatment, date: LocalDate) = Schedule.occurrencesOn(listOf(t), date).map { it.dose.minuteOfDay }

    @Test
    fun tenOnWeekdaysAndTwelveAtWeekends() {
        val t = levo(DoseTime(10 * 60, days = weekdays), DoseTime(12 * 60, days = weekend))
        (0..4).forEach { assertEquals("weekday $it", listOf(10 * 60), times(t, d(it))) }
        assertEquals(listOf(12 * 60), times(t, d(5)))
        assertEquals(listOf(12 * 60), times(t, d(6)))
        // Unchanged treatments (no days on the dose) keep working as before.
        assertEquals(listOf(8 * 60, 20 * 60), times(levo(DoseTime(8 * 60), DoseTime(20 * 60)), d(2)))
    }

    @Test
    fun everyDayExceptWednesdayAndOnlyMondayAndFridayStillWork() {
        val noWednesday = levo(DoseTime(10 * 60), daysOfWeek = setOf(1, 2, 4, 5, 6, 7))
        assertEquals(emptyList<Int>(), times(noWednesday, d(2)))
        assertEquals(listOf(600), times(noWednesday, d(3)))
        val mondayFriday = levo(DoseTime(10 * 60), daysOfWeek = setOf(1, 5))
        assertEquals(listOf(600), times(mondayFriday, d(0)))
        assertEquals(emptyList<Int>(), times(mondayFriday, d(1)))
        assertEquals(listOf(600), times(mondayFriday, d(4)))
        // A time on its own days inside a treatment that is not taken every day: both filters apply.
        val both = levo(DoseTime(10 * 60, days = setOf(1, 6)), DoseTime(12 * 60), daysOfWeek = setOf(1, 5, 6))
        assertEquals(listOf(600, 720), times(both, d(0)))
        assertEquals(listOf(720), times(both, d(4)))
        assertEquals(listOf(600, 720), times(both, d(5)))
        assertEquals(emptyList<Int>(), times(both, d(6)))
    }

    @Test
    fun remindersFollowTheDayTheyAreFor() {
        val t = levo(DoseTime(10 * 60, days = weekdays), DoseTime(12 * 60, days = weekend))
        val state = AppState(onboarded = true, treatments = listOf(t))
        // Friday evening: the next dose is Saturday at 12:00, and no Saturday 10:00 alarm exists.
        val plan = ReminderPlanner.plan(state, at(21, 0, d(4))).filter { it.kind == AlarmKind.DOSE }.map { it.time }
        assertTrue(plan.contains(at(12, 0, d(5))))
        assertFalse(plan.contains(at(10, 0, d(5))))
    }

    @Test
    fun theStockIsUsedAsTheWeekReallyGoes() {
        // One tablet at 10:00 on weekdays, one at 12:00 at weekends: seven a week, one a day.
        assertEquals(1.0, levo(DoseTime(10 * 60, days = weekdays), DoseTime(12 * 60, days = weekend)).dailyUse, 0.001)
        // A daily morning tablet plus an evening one at weekends: (7 + 2) / 7.
        assertEquals(9.0 / 7, levo(DoseTime(8 * 60), DoseTime(20 * 60, days = weekend)).dailyUse, 0.001)
        // Unchanged: two daily doses = 2 a day; three days a week = 3/7.
        assertEquals(2.0, levo(DoseTime(8 * 60), DoseTime(20 * 60)).dailyUse, 0.001)
        assertEquals(3.0 / 7, levo(DoseTime(8 * 60), daysOfWeek = setOf(1, 3, 5)).dailyUse, 0.001)
    }

    @Test
    fun undoingADoseGivesBackTheQuantityOfTheDoseOfThatDay() {
        // Same hour, weekdays one tablet and weekends two.
        val t = levo(DoseTime(10 * 60, 1.0, days = weekdays), DoseTime(10 * 60, 2.0, days = weekend)).copy(stock = 10.0)
        var state = AppState(treatments = listOf(t))
        val saturday = d(5)
        val item = Schedule.itemsOn(state.treatments, emptyMap(), saturday, at(10, 5, saturday)).single()
        assertEquals(2.0, item.occurrence.quantity, 0.001)
        state = Game.give(state, item, at(10, 5, saturday))!!.state
        assertEquals(8.0, state.treatments.single().stock!!, 0.001)
        val back = Game.undo(state, item.occurrence.key, at(10, 10, saturday))
        assertEquals(10.0, back.treatments.single().stock!!, 0.001)
    }

    @Test
    fun changingTheDaysOfADoseDoesNotRewriteThePast() {
        val wednesday = d(2)
        val before = levo(DoseTime(10 * 60, days = weekdays))
        val state = AppState(treatments = listOf(before))
        // Wednesday: the weekend is added to the 10:00 dose.
        val edited = before.copy(doses = listOf(before.doses.single().copy(days = null)))
        val after = Schedule.applyEdit(state, edited, at(9, 0, wednesday)).treatments.single()
        val dose = after.doses.single()
        assertEquals(wednesday, dose.daysFrom)
        assertEquals(weekdays, dose.daysBefore)
        // The Saturday before the change was not owed, and still is not; today's and next weekend's are.
        assertEquals(emptyList<Int>(), times(after, d(-2)))
        assertEquals(listOf(600), times(after, d(0)))
        assertEquals(listOf(600), times(after, d(5)))
        assertEquals(listOf(600), times(after, d(6)))
        // Changing again keeps only the latest change's "before".
        val twice = Schedule.applyEdit(AppState(treatments = listOf(after)), after.copy(doses = listOf(dose.copy(days = weekend))), at(9, 0, d(3))).treatments.single().doses.single()
        assertEquals(d(3), twice.daysFrom)
        assertNull(twice.daysBefore) // it was every day just before
        // Nothing about the days changed: nothing is touched.
        val same = Schedule.applyEdit(AppState(treatments = listOf(after)), after, at(9, 0, d(3))).treatments.single().doses.single()
        assertEquals(dose.daysFrom, same.daysFrom)
        assertEquals(dose.daysBefore, same.daysBefore)
    }

    @Test
    fun daysAreSaidInWordsAndKeepTheirOldSaveCompatible() {
        assertEquals("", DoseTime(600).daysText)
        assertEquals("", DoseTime(600, days = (1..7).toSet()).daysText)
        assertEquals("en semaine", DoseTime(600, days = weekdays).daysText)
        assertEquals("le week-end", DoseTime(600, days = weekend).daysText)
        assertEquals("lun, ven", DoseTime(600, days = setOf(5, 1)).daysText)
        // An old save without the field loads with "every day"; a new one comes back as it was.
        val old = AppRepository.JSON.decodeFromString<DoseTime>("""{"minuteOfDay":480}""")
        assertNull(old.days)
        val t = levo(DoseTime(10 * 60, days = weekdays, daysFrom = monday, daysBefore = null), DoseTime(12 * 60, days = weekend))
        assertEquals(t, AppRepository.JSON.decodeFromString<Treatment>(AppRepository.JSON.encodeToString(t)))
    }

    @Test
    fun theDoctorReportSaysWhichTimeAppliesOnWhichDays() {
        val t = levo(DoseTime(10 * 60, days = weekdays), DoseTime(12 * 60, days = weekend))
        val state = AppState(onboarded = true, treatments = listOf(t), settings = Settings())
        val table = DoctorReports.build(state, at(12), 30, setOf(ReportPart.TREATMENTS)).sections.single().blocks.filterIsInstance<ReportBlock.Table>().single()
        val posology = table.rows.single()[1]
        assertTrue(posology, posology.contains("10:00 (1 comprimé) en semaine"))
        assertTrue(posology, posology.contains("12:00 (1 comprimé) le week-end"))
    }

    @Test
    fun theWatchOnlyHearsOfTheDosesOfTheDay() {
        val t = levo(DoseTime(10 * 60, days = weekdays), DoseTime(12 * 60, days = weekend))
        val state = AppState(onboarded = true, treatments = listOf(t))
        val saturday = d(5)
        val snapshot = com.medipote.app.wear.WatchBridge.snapshot(state, at(8, 0, saturday))
        val today = snapshot.doses.filter { it.at.toLocalDate() == saturday }.map { it.at.hour }
        assertEquals(listOf(12), today)
        // And on Monday, the 10:00 one.
        val monday = com.medipote.app.wear.WatchBridge.snapshot(state, at(8, 0, d(7)))
        assertEquals(listOf(10), monday.doses.filter { it.at.toLocalDate() == d(7) }.map { it.at.hour })
    }
}
