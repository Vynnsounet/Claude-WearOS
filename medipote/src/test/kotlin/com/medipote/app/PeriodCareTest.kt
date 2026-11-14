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

    @Test
    fun periodListOnlyHoldsPeriodMedications() {
        fun prn(id: String, drug: String?, name: String) = com.medipote.app.data.Treatment(
            id, drug, name, doses = emptyList(), startDate = day.minusDays(3), asNeeded = true,
        )
        var state = AppState(
            treatments = listOf(prn("ibu", "ibuprofene", "Ibuprofène"), prn("alp", "alprazolam", "Alprazolam"), prn("own", null, "Antadys")),
            prnIntakes = listOf(
                com.medipote.app.data.PrnIntake("1", "ibu", at(9)),
                com.medipote.app.data.PrnIntake("2", "alp", at(10)),
                com.medipote.app.data.PrnIntake("3", "own", at(11), reason = PeriodCare.REASON),
            ),
        )
        // Painkillers by default; an anxiolytic or a medication typed by hand is not listed.
        assertEquals(listOf("ibu"), PeriodCare.periodMeds(state, day).map { it.id })
        assertEquals(listOf("alp", "own"), PeriodCare.otherMeds(state, day).map { it.id })
        // Today's period intakes: listed medications, or noted for painful periods.
        assertEquals(listOf("1", "3"), PeriodCare.intakesOn(state, day).map { it.id })
        // Choosing one's own, removing another.
        state = PeriodCare.setPeriodMed(state, "own", true)
        state = PeriodCare.setPeriodMed(state, "ibu", false)
        assertEquals(listOf("own"), PeriodCare.periodMeds(state, day).map { it.id })
        assertEquals(listOf("ibu", "alp"), PeriodCare.otherMeds(state, day).map { it.id })
    }
}
