package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.CycleInfo
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DoseTime
import com.medipote.app.data.PrnIntake
import com.medipote.app.data.Profile
import com.medipote.app.data.Treatment
import com.medipote.app.domain.Game
import com.medipote.app.domain.Prn
import com.medipote.app.domain.Reports
import com.medipote.app.domain.Schedule
import com.medipote.app.domain.Stats
import com.medipote.app.domain.TimeSlot
import com.medipote.app.domain.Tone
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrnTest {

    private val day = LocalDate.of(2026, 11, 12) // a Thursday
    private val now = day.atTime(20, 0)

    private val ibu = Treatment(
        "ibu", name = "Ibuprofène", strength = "400 mg", doses = emptyList(), startDate = day.minusDays(100),
        asNeeded = true, maxPerDay = 3, minIntervalHours = 6, stock = 10.0, lowStockUnits = 4,
    )
    private val daily = Treatment(
        "levo", name = "Lévothyroxine", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(100),
        createdAt = day.minusDays(100).atStartOfDay(),
    )

    private fun intake(id: String, at: LocalDateTime, reason: String = "Mal de tête", contexts: List<String> = emptyList(), relief: Int? = null) =
        PrnIntake(id, "ibu", at, reason = reason, contexts = contexts, relief = relief)

    @Test
    fun asNeededMedicationsHaveNoSchedule() {
        val state = AppState(treatments = listOf(ibu, daily), onboarded = true)
        assertFalse(ibu.isActiveOn(day))
        assertTrue(Schedule.occurrencesOn(state.treatments, day).all { it.treatment.id == "levo" })
        assertEquals(listOf("Lévothyroxine"), Stats.perTreatment(state, now).map { it.name })
        // No dose reminder and no "missed" dose for it.
        assertTrue(ReminderPlanner.plan(state, now).none { it.ref.contains("ibu") })
    }

    @Test
    fun savingEditingAndDeletingFollowTheStock() {
        var state = AppState(treatments = listOf(ibu), onboarded = true)
        state = Prn.save(state, intake("a", now).copy(quantity = 2.0))
        assertEquals(8.0, state.treatments.single().stock!!, 0.001)
        state = Prn.save(state, state.prnIntakes.single().copy(quantity = 1.0))
        assertEquals(9.0, state.treatments.single().stock!!, 0.001)
        assertEquals(1, state.prnIntakes.size)
        state = Prn.save(state, intake("b", now.minusHours(8)).copy(quantity = 5.0))
        assertTrue(state.treatments.single().isLowStock) // 4 left ≤ 4
        state = Prn.delete(state, "b")
        assertEquals(9.0, state.treatments.single().stock!!, 0.001)
        assertEquals(listOf("a"), state.prnIntakes.map { it.id })
        // No points: the game state is untouched.
        assertEquals(AppState().game, state.game)
    }

    @Test
    fun warningsFollowTheLimits() {
        val state = AppState(
            treatments = listOf(ibu),
            prnIntakes = listOf(intake("1", now.minusHours(2)), intake("2", now.minusHours(9)), intake("3", now.minusHours(16))),
        )
        val w = Prn.warnings(state, ibu, now)
        assertEquals(2, w.size)
        assertTrue(w[0], w[0].contains("4e prise en 24 h"))
        assertTrue(w[1], w[1].contains("Seulement 2 h depuis"))
        // Editing intake "1" itself: only the 24 h count could matter, and it's now 3 → fine.
        assertTrue(Prn.warnings(state, ibu, now.minusHours(2), excludeId = "1").isEmpty())
        assertTrue(Prn.warnings(state, ibu.copy(maxPerDay = null, minIntervalHours = null), now).isEmpty())
    }

    @Test
    fun reliefCheckIsPlannedAnHourLater() {
        val asked = intake("x", now.minusMinutes(10)).copy(askRelief = true)
        val state = AppState(treatments = listOf(ibu), onboarded = true, prnIntakes = listOf(asked))
        val checks = ReminderPlanner.plan(state, now).filter { it.kind == AlarmKind.PRN_CHECK }
        assertEquals(listOf(now.plusMinutes(50)), checks.map { it.time })
        assertEquals("x", checks.single().ref)
        val rated = state.copy(prnIntakes = listOf(asked.copy(relief = 4)))
        assertTrue(ReminderPlanner.plan(rated, now).none { it.kind == AlarmKind.PRN_CHECK })
    }

    @Test
    fun reportFindsWhenWhyAndWhere() {
        // 12 intakes over 30 days: mostly evenings around 21:00, often at work with a headache.
        val intakes = (0 until 12).map { i ->
            val d = day.minusDays(i * 2L + 1)
            intake(
                "i$i", d.atTime(if (i % 4 == 0) 13 else 21, 0),
                reason = if (i % 3 == 0) "Règles douloureuses" else "Mal de tête",
                contexts = if (i % 2 == 0) listOf("Au travail", "Stress") else listOf("Écrans"),
                relief = if (i < 6) 4 else null,
            )
        }
        val state = AppState(treatments = listOf(ibu), prnIntakes = intakes)
        val r = Reports.prn(state, "ibu", now, 30)
        assertEquals(12, r.count)
        assertEquals(12, r.daysWithIntake)
        assertEquals(12 * 7f / 30, r.perWeek, 0.01f)
        assertEquals(520f / 11, r.averageIntervalHours!!, 0.01f) // day-23 21:00 → day-1 13:00, 11 gaps
        assertEquals(9, r.hourCounts[21])
        assertEquals(9, r.slotCounts[TimeSlot.EVENING])
        assertEquals("Mal de tête" to 8, r.reasons.first())
        assertEquals(setOf("Au travail", "Stress", "Écrans"), r.contexts.map { it.first }.toSet())
        assertEquals(1f, r.effectiveShare!!, 0.001f)
        assertEquals(6, r.reliefRated)
        assertEquals(12, r.weekly.sumOf { it.second })
        // 12 days out of 30: the report suggests talking to a professional.
        assertTrue(r.insights.any { it.tone == Tone.WARN && it.text.contains("12 jours sur les 30") })
        assertTrue(r.insights.any { it.text.contains("le soir") })
    }

    @Test
    fun typicalTimeWrapsAroundMidnight() {
        val intakes = listOf(
            intake("a", day.minusDays(1).atTime(23, 30)),
            intake("b", day.minusDays(2).atTime(0, 30)),
            intake("c", day.minusDays(3).atTime(23, 50)),
            intake("d", day.minusDays(4).atTime(0, 10)),
        )
        val r = Reports.prn(AppState(treatments = listOf(ibu), prnIntakes = intakes), "ibu", now, 30)
        val t = r.typicalMinuteOfDay!!
        assertTrue("got $t", t >= 1430 || t <= 10)
        assertEquals("0 h 00", Reports.formatTime(0))
        // Spread evenly over the day: no "usual" time.
        val spread = (0 until 4).map { intake("s$it", day.minusDays(it + 1L).atTime(it * 6, 0)) }
        assertNull(Reports.prn(AppState(treatments = listOf(ibu), prnIntakes = spread), "ibu", now, 30).typicalMinuteOfDay)
    }

    @Test
    fun periodComparisonAndCycleShare() {
        val profile = Profile(name = "A", menstruating = true, cycle = CycleInfo(listOf(day.minusDays(30), day.minusDays(2)), 28, 5))
        val intakes = listOf(
            intake("p1", day.minusDays(1).atTime(9, 0)), intake("p2", day.minusDays(2).atTime(9, 0)),
            intake("p3", day.minusDays(29).atTime(9, 0)), intake("o1", day.minusDays(10).atTime(9, 0)),
            // previous 30-day period
            intake("old", day.minusDays(45).atTime(9, 0)),
        )
        val state = AppState(profile = profile, treatments = listOf(ibu), prnIntakes = intakes)
        val r = Reports.prn(state, "ibu", now, 30)
        assertEquals(4, r.count)
        assertEquals(1, r.previousCount)
        val (during, days) = r.cycleShare!!
        assertEquals(0.75f, during, 0.01f)
        assertTrue(days < 0.4f)
        // "All time" starts at the first intake.
        val all = Reports.prn(state, "ibu", now, null)
        assertEquals(5, all.count)
        assertEquals(46, all.days)
        assertNull(all.previousCount)
    }

    @Test
    fun adherenceReportGroupsBySlotAndWeekday() {
        val evening = daily.copy(id = "eve", name = "Statine", doses = listOf(DoseTime(20 * 60)))
        var state = AppState(treatments = listOf(daily, evening), onboarded = true)
        // Morning dose always taken on time, evening dose taken only on weekends.
        for (offset in 1..28) {
            val date = day.minusDays(offset.toLong())
            val logs = state.doseLogs.associateBy { it.key }
            val items = Schedule.itemsOn(state.treatments, logs, date, date.atTime(8, 5))
            state = Game.give(state, items.first { it.occurrence.treatment.id == "levo" }, date.atTime(8, 5))!!.state
            if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                val eve = Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, date, date.atTime(20, 30))
                state = Game.give(state, eve.first { it.occurrence.treatment.id == "eve" }, date.atTime(20, 30))!!.state
            }
        }
        state = state.copy(journal = (1..10).map { DayEntry(day.minusDays(it.toLong()), physical = 4, waterGlasses = if (it % 2 == 0) 8 else 4) })
        // 29 days: today (still to come) and the 28 days logged above.
        val r = Reports.adherence(state, day.atTime(7, 0), 29)
        assertEquals(1f, r.bySlot[TimeSlot.MORNING]!!.value!!, 0.001f)
        assertEquals(8f / 28, r.bySlot[TimeSlot.EVENING]!!.value!!, 0.001f)
        assertEquals(1f, r.byWeekday[DayOfWeek.SUNDAY]!!.value!!, 0.001f)
        assertEquals(0.5f, r.byWeekday[DayOfWeek.MONDAY]!!.value!!, 0.001f)
        assertEquals(Pair(36, 56), r.rate.taken to r.rate.decided)
        assertEquals(8, r.completeDays)
        // 28 morning doses 5 min late, 8 evening doses 30 min late: 380 / 36 ≈ 11 min.
        assertEquals(11, r.averageDelayMinutes)
        assertEquals(6f, r.waterAverage!!, 0.001f)
        assertEquals(5, r.waterGoalDays)
        assertTrue(r.insights.any { it.text.contains("du soir") && it.tone == Tone.WARN })
        assertTrue(r.insights.any { it.text.contains("Statine") })
        assertEquals(12, r.weekly.size)
        assertEquals(84, r.heatmap.size)
        assertNotNull(r.punctuality)
    }
}
