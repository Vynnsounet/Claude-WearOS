package com.medipote.app

import com.medipote.app.data.Anchor
import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseLog
import com.medipote.app.data.DoseTime
import com.medipote.app.data.Routine
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.domain.DoseOccurrence
import com.medipote.app.domain.Habits
import com.medipote.app.domain.Routines
import com.medipote.app.domain.Schedule
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.35: doses tied to the wake-up, meals and bedtime; reminders that follow real habits. */
class V35Test {
    private val day = LocalDate.of(2026, 11, 30)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m)

    private fun levo(anchor: Anchor? = Anchor.BREAKFAST, offset: Int = -30, vararg extra: DoseTime) = Treatment(
        "levo", name = "Lévothyrox", strength = "75 µg",
        doses = listOf(DoseTime(7 * 60, anchor = anchor?.id, offsetMinutes = offset)) + extra,
        startDate = day.minusDays(60), createdAt = day.minusDays(60).atStartOfDay(),
    )

    private fun state(t: Treatment, routine: Routine = Routine()) = AppState(onboarded = true, treatments = listOf(t), settings = Settings(routine = routine))

    // ───────────── The words ─────────────
    @Test
    fun anAnchorIsSaidLikeAPerson() {
        assertEquals("30 min avant le petit-déjeuner", Anchor.BREAKFAST.text(-30))
        assertEquals("au petit-déjeuner", Anchor.BREAKFAST.text(0))
        assertEquals("1 h après le dîner", Anchor.DINNER.text(60))
        assertEquals("au coucher", Anchor.BED.text(0))
        assertEquals("30 min avant le coucher", Anchor.BED.text(-30))
        assertEquals("au réveil", Anchor.WAKE.text(0))
        assertEquals("15 min après le réveil", Anchor.WAKE.text(15))
        val d = DoseTime(7 * 60, anchor = "breakfast", offsetMinutes = -30)
        assertTrue(d.isAnchored); assertEquals("30 min avant le petit-déjeuner", d.anchorText)
        assertFalse(DoseTime(7 * 60).isAnchored); assertEquals("", DoseTime(7 * 60).anchorText)
        assertFalse("an unknown anchor is a fixed dose", DoseTime(7 * 60, anchor = "brunch").isAnchored)
    }

    @Test
    fun theTimeFollowsTheAnchor() {
        val r = Routine(breakfast = 8 * 60)
        assertEquals(7 * 60 + 30, Routines.minuteFor(r, Anchor.BREAKFAST, -30))
        assertEquals(23 * 60, Routines.minuteFor(Routine(), Anchor.BED, 0))
        assertEquals(0, Routines.minuteFor(Routine(wake = 20), Anchor.WAKE, -60).coerceAtLeast(0))
        assertEquals(24 * 60 - 1, Routines.minuteFor(Routine(bed = 23 * 60 + 50), Anchor.BED, 60 + 0).coerceAtMost(24 * 60 - 1))
    }

    // ───────────── Following the routine ─────────────
    @Test
    fun anAnchoredDoseMovesWhenTheRoutineChanges_andTakesItsGivenDosesWithIt() {
        val t = levo(extra = emptyArray())
        var s = state(t)
        // the anchored dose sits at 7:00 (7:30 - 30 min): given yesterday at 7:05
        val yesterday = day.minusDays(1)
        val key = DoseOccurrence.keyOf("levo", yesterday, 7 * 60)
        s = s.copy(doseLogs = listOf(DoseLog(key, "levo", at(7, 0, yesterday), at(7, 5, yesterday), TakeStatus.ON_TIME, 10)))
        val moved = Routines.sync(s.copy(settings = s.settings.copy(routine = Routine(breakfast = 8 * 60 + 30))), at(9))
        assertEquals(8 * 60, moved.treatments.single().doses.single().minuteOfDay)
        // yesterday's dose is still given, at its new time
        assertEquals(DoseOccurrence.keyOf("levo", yesterday, 8 * 60), moved.doseLogs.single().key)
        // nothing to do when it already follows
        assertEquals(moved, Routines.sync(moved, at(9)))
    }

    @Test
    fun theRepositoryKeepsAnchoredDosesInStep() {
        val repo = AppRepository(java.io.File(kotlin.io.path.createTempDirectory().toFile(), "state.json"))
        repo.update { state(levo()) }
        assertEquals(7 * 60, repo.state.value.treatments.single().doses.single().minuteOfDay)
        repo.update { it.copy(settings = it.settings.copy(routine = it.settings.routine.copy(breakfast = 9 * 60))) }
        assertEquals(8 * 60 + 30, repo.state.value.treatments.single().doses.single().minuteOfDay)
    }

    @Test
    fun twoDosesNeverShareATime() {
        val t = levo(Anchor.BREAKFAST, 0, DoseTime(7 * 60 + 30, anchor = "wake", offsetMinutes = 30))
        // breakfast and wake + 30 both give 7:30: the second is nudged a minute
        val doses = Routines.sync(state(t.copy(doses = listOf(DoseTime(0, anchor = "breakfast"), DoseTime(0, anchor = "wake", offsetMinutes = 30)))), at(9)).treatments.single().doses
        assertEquals(setOf(7 * 60 + 30, 7 * 60 + 31), doses.map { it.minuteOfDay }.toSet())
    }

    @Test
    fun aFixedDoseIsLeftAlone() {
        val t = levo(anchor = null).copy(doses = listOf(DoseTime(20 * 60)))
        assertEquals(state(t), Routines.sync(state(t), at(9)))
    }

    @Test
    fun theReminderRingsAtTheComputedTime() {
        val s = state(levo(), Routine(breakfast = 8 * 60))
        val synced = Routines.sync(s, at(6))
        val alarms = ReminderPlanner.plan(synced, at(6)).filter { it.kind == AlarmKind.DOSE && it.time.toLocalDate() == day }
        assertEquals(listOf(at(7, 30)), alarms.map { it.time })
    }

    @Test
    fun theWakeTimeCanComeFromTheNights() {
        val nights = (0 until 8).map { i -> val d = day.minusDays(i.toLong()); SleepNight(d, d.minusDays(1).atTime(23, 0), 15, d.atTime(6, 40 + i % 2)) }
        val s = AppState(sleep = nights)
        assertEquals(6 * 60 + 40, Routines.usualWake(s, day))
        assertNull("too few nights", Routines.usualWake(AppState(sleep = nights.take(3)), day))
    }

    @Test
    fun oldSavesLoadAsFixedTimes() {
        val d = AppRepository.JSON.decodeFromString(DoseTime.serializer(), """{"minuteOfDay":480}""")
        assertNull(d.anchor); assertEquals(0, d.offsetMinutes)
        val s = AppRepository.JSON.decodeFromString(AppState.serializer(), """{"onboarded":true}""")
        assertEquals(Routine(), s.settings.routine)
        val back = AppRepository.JSON.decodeFromString(DoseTime.serializer(), AppRepository.JSON.encodeToString(DoseTime.serializer(), DoseTime(450, anchor = "bed", offsetMinutes = -30)))
        assertEquals("bed", back.anchor); assertEquals(-30, back.offsetMinutes)
    }

    // ───────────── Habits ─────────────
    private fun fixed(minute: Int = 8 * 60) = Treatment("t", name = "Vitamine D", doses = listOf(DoseTime(minute)), startDate = day.minusDays(60), createdAt = day.minusDays(60).atStartOfDay())

    /** Doses given [late] minutes after their time, one a day for [n] days. */
    private fun taken(minute: Int, late: List<Int>): List<DoseLog> = late.mapIndexed { i, l ->
        val d = day.minusDays(i + 1L)
        val scheduled = d.atTime(minute / 60, minute % 60)
        DoseLog(DoseOccurrence.keyOf("t", d, minute), "t", scheduled, scheduled.plusMinutes(l.toLong()), TakeStatus.LATE, 6)
    }

    private fun withLogs(logs: List<DoseLog>, t: Treatment = fixed()) = AppState(onboarded = true, treatments = listOf(t), doseLogs = logs)

    @Test
    fun aSteadyLateHabitIsNoticed() {
        val s = withLogs(taken(8 * 60, listOf(38, 42, 40, 45, 35, 40, 41, 39)))
        val h = Habits.suggestion(s, at(12))!!
        assertEquals("t", h.treatmentId); assertEquals(8 * 60, h.minute); assertEquals(8 * 60 + 40, h.usualMinute)
        assertEquals("08:40", h.usualText); assertEquals("08:00", h.currentText); assertEquals(8, h.logs)
    }

    @Test
    fun anEarlyHabitToo() {
        val h = Habits.suggestion(withLogs(taken(8 * 60, listOf(-30, -28, -33, -35, -30, -31, -29))), at(12))!!
        assertEquals(7 * 60 + 30, h.usualMinute)
    }

    @Test
    fun nothingIsSaidWithoutEnoughSteadyEvidence() {
        assertNull("too few doses", Habits.suggestion(withLogs(taken(8 * 60, listOf(40, 40, 40))), at(12)))
        assertNull("close enough already", Habits.suggestion(withLogs(taken(8 * 60, listOf(5, 8, 3, 10, 6, 4, 7))), at(12)))
        assertNull("all over the place", Habits.suggestion(withLogs(taken(8 * 60, listOf(10, 90, 20, 120, 5, 100, 60, 30))), at(12)))
        assertNull("a dose noted hours late says nothing", Habits.suggestion(withLogs(taken(8 * 60, List(8) { 240 })), at(12)))
        assertNull("old doses", Habits.suggestion(withLogs(taken(8 * 60, listOf(40, 40, 40, 40, 40, 40, 40)).map { it.copy(takenAt = it.takenAt.minusDays(40)) }), at(12)))
    }

    @Test
    fun windowsAnchorsAndAsNeededAreNeverMoved() {
        val logs = taken(8 * 60, List(8) { 40 })
        assertNull(Habits.suggestion(withLogs(logs, fixed().copy(doses = listOf(DoseTime(8 * 60, untilMinute = 10 * 60)))), at(12)))
        assertNull(Habits.suggestion(withLogs(logs, fixed().copy(doses = listOf(DoseTime(8 * 60, anchor = "breakfast")))), at(12)))
        assertNull(Habits.suggestion(withLogs(logs, fixed().copy(asNeeded = true)), at(12)))
    }

    @Test
    fun applyingMovesTheReminderAndTheGivenDoses() {
        val s = withLogs(taken(8 * 60, List(8) { 40 }))
        val h = Habits.suggestion(s, at(12))!!
        val moved = Habits.apply(s, h, at(12))
        assertEquals(8 * 60 + 40, moved.treatments.single().doses.single().minuteOfDay)
        assertTrue(moved.doseLogs.all { it.key.endsWith("@${8 * 60 + 40}") })
        assertNull("it is done: nothing more to say", Habits.suggestion(moved, at(12)))
    }

    @Test
    fun notNowKeepsQuietForAMonth() {
        val s = withLogs(taken(8 * 60, List(8) { 40 }))
        val h = Habits.suggestion(s, at(12))!!
        val dismissed = Habits.dismiss(s, h, day)
        assertNull(Habits.suggestion(dismissed, at(12)))
        assertNull(Habits.suggestion(dismissed, at(12, 0, day.plusDays(29))))
        assertEquals(day.toString(), dismissed.settings.dismissedChecks[h.key])
        // a month later, with fresh doses, it can be offered again
        val later = day.plusDays(31)
        val fresh = dismissed.copy(doseLogs = taken(8 * 60, List(8) { 40 }).map { it.copy(scheduled = it.scheduled.plusDays(31), takenAt = it.takenAt.plusDays(31), key = DoseOccurrence.keyOf("t", it.scheduled.toLocalDate().plusDays(31), 8 * 60)) })
        assertNotNull(Habits.suggestion(fresh, at(12, 0, later)))
    }
}
