package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.ReminderPause
import com.medipote.app.data.Settings
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.domain.DoseOccurrence
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Game
import com.medipote.app.domain.Precautions
import com.medipote.app.domain.Rules
import com.medipote.app.domain.Schedule
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import com.medipote.app.wear.WatchBridge
import com.medipote.sync.WatchDose
import com.medipote.sync.WatchDoseState
import com.medipote.sync.WatchPlanner
import com.medipote.sync.WatchReminder
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.32: a dose at a fixed time, or any time within a window. */
class V32Test {
    private val day = LocalDate.of(2026, 11, 23)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    private fun window(start: Int = 10, end: Int = 12, remind: Boolean = true) = DoseTime(start * 60, untilMinute = until(end), remindEnd = remind)
    private fun until(h: Int) = h * 60
    private fun treatment(vararg doses: DoseTime, id: String = "t", drug: String? = null) =
        Treatment(id, name = "Vitamine", drugId = drug, doses = doses.toList(), startDate = day.minusDays(30), createdAt = day.minusDays(30).atStartOfDay())
    private fun state(t: Treatment, notifications: Boolean = true) = AppState(onboarded = true, treatments = listOf(t), settings = Settings(notificationsEnabled = notifications))

    // ───────────── The dose time ─────────────
    @Test
    fun aWindowHasALabelAndAnEnd() {
        val d = window()
        assertTrue(d.isWindow)
        assertEquals("10:00", d.timeLabel); assertEquals("10:00–12:00", d.label); assertEquals("12:00", d.untilLabel)
        assertEquals("entre 10:00 et 12:00", d.whenText)
        val fixed = DoseTime(8 * 60)
        assertFalse(fixed.isWindow); assertEquals("08:00", fixed.label); assertEquals("à 08:00", fixed.whenText); assertEquals("", fixed.untilLabel)
    }

    @Test
    fun anEndAtOrBeforeTheStartIsJustAFixedTime() {
        assertFalse(DoseTime(10 * 60, untilMinute = 10 * 60).isWindow)
        assertFalse(DoseTime(10 * 60, untilMinute = 9 * 60).isWindow)
        assertEquals("10:00", DoseTime(10 * 60, untilMinute = 9 * 60).label)
    }

    @Test
    fun oldSavesLoadAsFixedTimes_andWindowsSurviveASave() {
        val old = """{"minuteOfDay":480,"quantity":1.0}"""
        val d = AppRepository.JSON.decodeFromString(DoseTime.serializer(), old)
        assertNull(d.untilMinute); assertFalse(d.remindEnd)
        val back = AppRepository.JSON.decodeFromString(DoseTime.serializer(), AppRepository.JSON.encodeToString(DoseTime.serializer(), window()))
        assertEquals(window(), back)
    }

    // ───────────── When it is due ─────────────
    @Test
    fun aWindowIsDueFromItsStartToItsEnd() {
        val o = DoseOccurrence(treatment(window()), window(), day)
        assertEquals(at(12), o.windowEnd)
        fun state(now: LocalDateTime) = Schedule.stateOf(o, null, now)
        assertEquals(DoseState.UPCOMING, state(at(9, 20)))
        assertEquals(DoseState.DUE, state(at(9, 30)))
        assertEquals(DoseState.DUE, state(at(11, 0)))
        assertEquals(DoseState.DUE, state(at(12, 30)))
        assertEquals(DoseState.OVERDUE, state(at(12, 31)))
        assertEquals(DoseState.OVERDUE, state(day.atTime(23, 59)))
        assertEquals(DoseState.MISSED, state(day.plusDays(1).atStartOfDay()))
    }

    @Test
    fun aFixedDoseIsUnchanged() {
        val fixed = DoseTime(8 * 60)
        val o = DoseOccurrence(treatment(fixed), fixed, day)
        assertNull(o.windowEnd)
        assertEquals(DoseState.DUE, Schedule.stateOf(o, null, at(8, 30)))
        assertEquals(DoseState.OVERDUE, Schedule.stateOf(o, null, at(8, 31)))
    }

    @Test
    fun takingItAnywhereInTheWindowIsOnTime() {
        val start = at(10); val end = at(12)
        assertEquals(TakeStatus.ON_TIME, Rules.statusFor(start, end, at(10, 0)))
        assertEquals(TakeStatus.ON_TIME, Rules.statusFor(start, end, at(11, 40)))
        assertEquals(TakeStatus.ON_TIME, Rules.statusFor(start, end, at(12, 30)))
        assertEquals(TakeStatus.ON_TIME, Rules.statusFor(start, end, at(9, 40)))
        assertEquals(TakeStatus.EARLY, Rules.statusFor(start, end, at(9, 0)))
        assertEquals(TakeStatus.LATE, Rules.statusFor(start, end, at(13, 0)))
        assertEquals(TakeStatus.VERY_LATE, Rules.statusFor(start, end, at(15, 0)))
        // a fixed dose keeps its own rule
        assertEquals(TakeStatus.LATE, Rules.statusFor(start, null, at(11, 40)))
    }

    @Test
    fun givingADoseInTheWindowScoresOnTime() {
        val t = treatment(window())
        val s = state(t)
        val item = Schedule.itemsOn(s.treatments, emptyMap(), day, at(11, 40)).single()
        val result = Game.give(s, item, at(11, 40))!!
        assertEquals(TakeStatus.ON_TIME, result.state.doseLogs.single().status)
    }

    @Test
    fun theDeadlineStartsFromTheEndOfTheWindow() {
        val d = DoseTime(20 * 60, untilMinute = 22 * 60)
        val o = DoseOccurrence(treatment(d), d, day)
        assertEquals(day.plusDays(1).atTime(2, 0), o.deadline)
    }

    @Test
    fun aTravelShiftMovesTheWholeWindow() {
        val t = treatment(window()).copy(shifts = mapOf(day.toString() to 120))
        val o = DoseOccurrence(t, t.doses.single(), day)
        assertEquals(at(12), o.scheduled); assertEquals(at(14), o.windowEnd)
    }

    // ───────────── Reminders ─────────────
    private fun kinds(s: AppState, now: LocalDateTime) = ReminderPlanner.plan(s, now).filter { it.kind in setOf(AlarmKind.DOSE, AlarmKind.FOLLOW_UP, AlarmKind.WINDOW_END) }

    @Test
    fun aWindowRingsAtItsStart_andOnceBeforeItCloses_butHasNoFollowUp() {
        val alarms = kinds(state(treatment(window())), at(7)).filter { it.time.toLocalDate() == day }
        assertEquals(listOf(AlarmKind.DOSE to at(10), AlarmKind.WINDOW_END to at(11, 30)), alarms.map { it.kind to it.time })
        assertEquals(at(10).toString(), alarms.last().ref)
    }

    @Test
    fun theEndReminderIsAChoice() {
        val alarms = kinds(state(treatment(window(remind = false))), at(7))
        assertTrue(alarms.none { it.kind == AlarmKind.WINDOW_END })
        assertTrue(alarms.any { it.kind == AlarmKind.DOSE })
    }

    @Test
    fun theEndReminderStillRingsInsideTheWindow_andStopsOnceTaken() {
        val t = treatment(window())
        val s = state(t)
        assertTrue(kinds(s, at(11, 0)).any { it.kind == AlarmKind.WINDOW_END && it.time == at(11, 30) })
        val item = Schedule.itemsOn(s.treatments, emptyMap(), day, at(11, 0)).single()
        val taken = Game.give(s, item, at(11, 0))!!.state
        assertTrue(kinds(taken, at(11, 5)).none { it.kind == AlarmKind.WINDOW_END && it.time.toLocalDate() == day })
        assertTrue("past its time it is not planned again", kinds(s, at(11, 45)).none { it.kind == AlarmKind.WINDOW_END && it.time.toLocalDate() == day })
    }

    @Test
    fun aVeryShortWindowHasNoEndReminder() {
        val short = DoseTime(10 * 60, untilMinute = 10 * 60 + 30, remindEnd = true)
        assertTrue(kinds(state(treatment(short)), at(7)).none { it.kind == AlarmKind.WINDOW_END })
    }

    @Test
    fun aFixedDoseKeepsItsFollowUp() {
        val alarms = kinds(state(treatment(DoseTime(10 * 60))), at(7)).filter { it.time.toLocalDate() == day }
        assertEquals(listOf(AlarmKind.DOSE, AlarmKind.FOLLOW_UP), alarms.map { it.kind })
    }

    @Test
    fun aPauseWithDosesSilencesTheEndReminderToo() {
        val paused = state(treatment(window())).let { it.copy(settings = it.settings.copy(pause = ReminderPause(until = at(23), doses = true))) }
        assertTrue(kinds(paused, at(7)).none { it.time.isBefore(at(23)) })
        val gentle = state(treatment(window())).let { it.copy(settings = it.settings.copy(pause = ReminderPause(until = at(23), doses = false))) }
        assertTrue(kinds(gentle, at(7)).any { it.kind == AlarmKind.WINDOW_END })
    }

    // ───────────── Spacing between treatments ─────────────
    @Test
    fun spacingConsidersTheWindowsAtTheirWorst() {
        val calcium = { d: DoseTime -> treatment(d, id = "c", drug = "calcium_vitd") }
        val levo = listOf(DoseTime(8 * 60))
        // 10:00–13:00 can be taken 2 h after: too close for 4 h
        assertTrue(Precautions.spacingOf("levothyroxine", levo, listOf(calcium(window(10, 13)))).single().warn)
        // 13:00–16:00: at least 5 h away
        assertFalse(Precautions.spacingOf("levothyroxine", levo, listOf(calcium(window(13, 16)))).single().warn)
        // a window that holds the other dose
        val same = Precautions.spacingOf("levothyroxine", levo, listOf(calcium(window(7, 9)))).single()
        assertTrue(same.warn); assertTrue(same.text, same.text.contains("en même temps"))
        // the window of the levothyroxine itself
        assertTrue(Precautions.spacingOf("levothyroxine", listOf(window(6, 9)), listOf(calcium(DoseTime(12 * 60)))).single().warn)
    }

    // ───────────── The watch ─────────────
    @Test
    fun theWatchGetsTheWindow() {
        val snapshot = WatchBridge.snapshot(state(treatment(window())), at(9, 45))
        val dose = snapshot.doses.single { it.time == at(10).toString() }
        assertEquals(at(12).toString(), dose.until)
        assertEquals("10:00–12:00", dose.label)
        assertTrue(snapshot.reminders.any { it.kind == WatchReminder.WINDOW_END && it.time == at(11, 30).toString() })
        assertTrue(snapshot.reminders.none { it.kind == WatchReminder.FOLLOW_UP && it.ref == at(10).toString() })
    }

    @Test
    fun theWatchCountsTheWindowAsDueUntilItsEnd() {
        val d = WatchDose("k", "Vitamine", "1 comprimé", at(10).toString(), until = at(12).toString())
        assertEquals(WatchDoseState.UPCOMING, WatchPlanner.state(d, at(9, 20)))
        assertEquals(WatchDoseState.DUE, WatchPlanner.state(d, at(11, 50)))
        assertEquals(WatchDoseState.DUE, WatchPlanner.state(d, at(12, 30)))
        assertEquals(WatchDoseState.LATE, WatchPlanner.state(d, at(12, 31)))
        assertEquals("08:00", WatchDose("k", "x", "1", at(8).toString()).label)
    }

    @Test
    fun theWatchSaysHowMuchTimeIsLeft() {
        val snapshot = WatchBridge.snapshot(state(treatment(window())), at(11))
        val reminder = snapshot.reminders.first { it.kind == WatchReminder.WINDOW_END }
        assertTrue(WatchPlanner.relevant(snapshot, reminder, at(11)))
        val (title, text) = WatchPlanner.message(snapshot, reminder, at(11, 30))
        assertEquals("Il reste un peu de temps ⏳", title)
        assertTrue(text, text.endsWith("jusqu'à 12:00"))
        val start = snapshot.reminders.first { it.kind == WatchReminder.DOSE }
        assertTrue(WatchPlanner.message(snapshot, start, at(10)).second.endsWith("jusqu'à 12:00"))
    }
}
