package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import com.medipote.app.data.DoseTime
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.Treatment
import com.medipote.app.data.WaterSettings
import com.medipote.app.domain.HomeRules
import com.medipote.app.domain.PeriodCare
import com.medipote.app.domain.Schedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRulesTest {
    private val day = LocalDate.of(2026, 11, 12) // a Thursday

    @Test
    fun theNextDoseIsTheOneToTakeNowElseTheNextOneToday() {
        val t = Treatment("t", null, "Médoc", doses = listOf(DoseTime(8 * 60), DoseTime(12 * 60), DoseTime(20 * 60)), startDate = day.minusDays(3))
        val at10 = day.atTime(10, 0)
        val items = Schedule.itemsOn(listOf(t), emptyMap(), day, at10)
        // 08:00 is still owed (late): it comes first.
        assertEquals(8 * 60, HomeRules.nextDose(items, at10)!!.occurrence.dose.minuteOfDay)
        assertNull(HomeRules.nextDose(emptyList(), at10))
        val early = day.atTime(6, 0)
        assertEquals(8 * 60, HomeRules.nextDose(Schedule.itemsOn(listOf(t), emptyMap(), day, early), early)!!.occurrence.dose.minuteOfDay)
    }

    @Test
    fun blocksShowOnlyWhenUseful() {
        // Recap: Sunday evening and Monday only.
        assertFalse(HomeRules.recapTime(day.atTime(19, 0)))
        assertFalse(HomeRules.recapTime(day.plusDays(3).atTime(12, 0)))
        assertTrue(HomeRules.recapTime(day.plusDays(3).atTime(18, 30)))
        assertTrue(HomeRules.recapTime(day.plusDays(4).atTime(9, 0)))
        // Appointments within 3 days.
        val soon = Appointment("a", "Dentiste", dateTime = day.plusDays(3).atTime(9, 0))
        val later = Appointment("b", "Ophtalmo", dateTime = day.plusDays(5).atTime(9, 0))
        assertEquals("a", HomeRules.nextAppointment(AppState(appointments = listOf(later, soon)), day.atTime(8, 0))?.id)
        assertNull(HomeRules.nextAppointment(AppState(appointments = listOf(later)), day.atTime(8, 0)))
        // Water only when followed.
        assertFalse(HomeRules.showWater(AppState(), day))
        assertTrue(HomeRules.showWater(AppState(settings = Settings(water = WaterSettings(enabled = true))), day))
    }

    @Test
    fun periodDaysComeFromTheCycleOrAProductInUse() {
        val profile = Profile(name = "Alex", menstruating = true, cycle = com.medipote.app.data.CycleInfo(periodStarts = listOf(day.minusDays(1)), periodLength = 5))
        assertTrue(PeriodCare.inPeriod(AppState(profile = profile), day))
        assertFalse(PeriodCare.inPeriod(AppState(profile = profile), day.plusDays(10)))
        assertFalse(PeriodCare.inPeriod(AppState(profile = profile.copy(menstruating = false)), day))
        val wearing = PeriodCare.start(AppState(profile = profile), com.medipote.app.data.PeriodProduct.PAD, day.plusDays(10).atTime(8, 0))
        assertTrue(PeriodCare.inPeriod(wearing, day.plusDays(10)))
    }
}
