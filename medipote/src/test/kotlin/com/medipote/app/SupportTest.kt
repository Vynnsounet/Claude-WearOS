package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Settings
import com.medipote.app.data.SupportSettings
import com.medipote.app.data.TrustedContact
import com.medipote.app.domain.BreathPhase
import com.medipote.app.domain.Support
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportTest {
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    @Test
    fun theBreathOutIsLongerThanTheBreathInForTheCalmingPattern() {
        val calm = Support.PATTERNS.first()
        assertTrue(calm.exhale > calm.inhale)
        assertEquals(10_000L, calm.cycleMillis)
        // 0–4 s in, 4–10 s out, then it starts again.
        assertEquals(BreathPhase.INHALE, calm.at(0).phase)
        assertEquals(4, calm.at(0).secondsLeft)
        assertEquals(BreathPhase.INHALE, calm.at(3_999).phase)
        assertEquals(BreathPhase.EXHALE, calm.at(4_000).phase)
        assertEquals(0.5f, calm.at(7_000).progress, 0.001f)
        assertEquals(BreathPhase.INHALE, calm.at(10_000).phase)
        // The square one holds its breath in and out.
        val box = Support.PATTERNS.first { it.id == "box" }
        assertEquals(listOf(BreathPhase.INHALE, BreathPhase.HOLD_IN, BreathPhase.EXHALE, BreathPhase.HOLD_OUT), (0..3).map { box.at(it * 4_000L).phase })
        assertEquals(16_000L, box.cycleMillis)
    }

    @Test
    fun theHelpLinesAreTheNationalOnes() {
        assertEquals(listOf("3114", "15", "112"), Support.HELP_LINES.map { it.number })
        assertTrue(Support.HELP_LINES.first().detail.contains("jour et nuit"))
    }

    @Test
    fun phoneNumbersAreCleanedForTheDialer() {
        assertEquals("0612345678", Support.cleanPhone("06 12 34 56 78"))
        assertEquals("+33612345678", Support.cleanPhone(" +33 6 12-34-56-78 "))
        assertTrue(Support.validPhone("06 12 34 56 78"))
        assertFalse(Support.validPhone("abc"))
        assertFalse(Support.validPhone("12"))
        val state = AppState(settings = Settings(support = SupportSettings(contacts = listOf(TrustedContact("Mam", "06 12 34 56 78"), TrustedContact("Test", "x")))))
        assertEquals(listOf("Mam"), Support.contacts(state).map { it.name })
    }

    @Test
    fun supportIsOfferedOnlyAfterTheVeryWorstMoodAndOnlyIfAsked() {
        val bad = AppState(journal = listOf(DayEntry(day, mental = 1)))
        assertTrue(Support.nudge(bad, day))
        assertFalse(Support.nudge(AppState(journal = listOf(DayEntry(day, mental = 2))), day))
        assertFalse(Support.nudge(AppState(), day))
        val off = bad.copy(settings = Settings(support = SupportSettings(suggestOnLowMood = false)))
        assertFalse(Support.nudge(off, day))
    }

    @Test
    fun askingAgainSchedulesOneGentleNotificationAndOpeningTheScreenAnswersIt() {
        var state = AppState(onboarded = true)
        assertTrue(ReminderPlanner.plan(state, at(15)).none { it.kind == AlarmKind.SUPPORT })
        state = Support.askAgain(state, at(15, 20))
        assertEquals(at(17, 20), state.supportFollowUp)
        val alarm = ReminderPlanner.plan(state, at(15, 21)).single { it.kind == AlarmKind.SUPPORT }
        assertEquals(at(17, 20), alarm.time)
        // Answered (the screen opened): no alarm any more.
        state = Support.clearFollowUp(state)
        assertNull(state.supportFollowUp)
        assertTrue(ReminderPlanner.plan(state, at(15, 22)).none { it.kind == AlarmKind.SUPPORT })
        // A follow-up already past rings nothing.
        assertTrue(ReminderPlanner.plan(Support.askAgain(state, at(10)), at(14)).none { it.kind == AlarmKind.SUPPORT })
    }

    @Test
    fun whatTheUserPreparedSurvivesTheBackupFormat() {
        val box = com.medipote.app.data.SupportBox(
            messageToSelf = "Ça passera.", comfort = listOf("Ma chanson"), signs = listOf("Je m'isole"), helps = listOf("Marcher"), matters = "Mes proches",
            thoughts = listOf(com.medipote.app.data.ThoughtNote("t", at(9), "Trop lourd")),
        )
        val state = AppState(support = box, settings = Settings(support = SupportSettings(contacts = listOf(TrustedContact("Mam", "0612345678")), professionalPhone = "0102030405")))
        val json = com.medipote.app.data.AppRepository.JSON
        val back = json.decodeFromString(AppState.serializer(), json.encodeToString(AppState.serializer(), state))
        assertEquals(state.support, back.support)
        assertEquals(state.settings.support, back.settings.support)
        // An old save has none of it, with the support on by default.
        val old = json.decodeFromString(AppState.serializer(), """{"onboarded":true}""")
        assertTrue(old.support.thoughts.isEmpty())
        assertTrue(old.settings.support.homeButton)
        assertTrue(old.settings.support.suggestOnLowMood)
    }

    @Test
    fun theCompanionJustKeepsCompanyOnAVeryHardDay() {
        val state = AppState(onboarded = true, journal = listOf(DayEntry(day, mental = 1)))
        repeat(20) {
            val line = com.medipote.app.domain.PetTalk.chatter(state, at(14), kotlin.random.Random(it))
            assertFalse(line, "série" in line || "points" in line || "jours d'affilée" in line)
        }
        assertEquals("Je suis là, tout près de toi 💚", com.medipote.app.domain.PetTalk.reactionTo(state, at(10)))
    }
}
