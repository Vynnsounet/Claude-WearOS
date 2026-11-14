package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.PeriodProduct
import com.medipote.app.domain.PeriodCare
import com.medipote.app.domain.WearLevel
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodCareTest {

    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    @Test
    fun timerRemindsThenWarnsAtTheLongestWearingTime() {
        var state = PeriodCare.start(AppState(onboarded = true), PeriodProduct.TAMPON, at(8))
        val s = state.productSession!!
        assertEquals(4 * 60, s.remindMinutes)
        assertEquals(WearLevel.OK, PeriodCare.level(s, at(11, 59)))
        assertEquals(WearLevel.CHANGE, PeriodCare.level(s, at(12)))
        assertEquals(WearLevel.OVERDUE, PeriodCare.level(s, at(16)))
        val alarms = ReminderPlanner.plan(state, at(9)).filter { it.kind == AlarmKind.PRODUCT }
        assertEquals(listOf(at(12) to "remind", at(16) to "max"), alarms.map { it.time to it.ref })

        // Changed at 11:30: a new timer, the previous one goes to the history.
        state = PeriodCare.change(state, at(11, 30))
        assertEquals(at(11, 30), state.productSession!!.since)
        assertEquals(PeriodProduct.TAMPON, state.productLog.single().product)
        assertEquals(at(8) to at(11, 30), state.productLog.single().let { it.start to it.end })
        assertEquals(2, PeriodCare.usesOn(state, day))

        // Switched to a pad for the night, then removed in the morning.
        state = PeriodCare.start(state, PeriodProduct.PAD, at(22))
        assertEquals(PeriodProduct.PAD, state.productSession!!.product)
        state = PeriodCare.stop(state, at(23))
        assertNull(state.productSession)
        assertEquals(3, state.productLog.size)
        assertTrue(ReminderPlanner.plan(state, at(23)).none { it.kind == AlarmKind.PRODUCT })
    }

    @Test
    fun remindersNeverExceedTheLongestWearingTime() {
        var state = AppState()
        state = PeriodCare.setReminder(state, PeriodProduct.TAMPON, 10 * 60)
        assertEquals(8 * 60, PeriodCare.reminderFor(state, PeriodProduct.TAMPON))
        state = PeriodCare.setReminder(state, PeriodProduct.CUP, 6 * 60)
        assertEquals(6 * 60, PeriodCare.start(state, PeriodProduct.CUP, at(8)).productSession!!.remindMinutes)
        // Changing the reminder of the product being worn updates the timer.
        val worn = PeriodCare.start(AppState(), PeriodProduct.UNDERWEAR, at(8))
        assertEquals(5 * 60, PeriodCare.setReminder(worn, PeriodProduct.UNDERWEAR, 5 * 60).productSession!!.remindMinutes)
        PeriodProduct.entries.forEach { assertTrue(it.name, it.defaultMinutes <= it.maxMinutes) }
        assertTrue(PeriodProduct.entries.filter { it.internal }.all { it.maxMinutes <= 12 * 60 })
    }
}
