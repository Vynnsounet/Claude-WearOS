package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.CycleInfo
import com.medipote.app.data.DoseTime
import com.medipote.app.data.DoseUnit
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.GameState
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.domain.Cycle
import com.medipote.app.domain.CycleDayType
import com.medipote.app.domain.CyclePhase
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Game
import com.medipote.app.domain.PetMood
import com.medipote.app.domain.Rules
import com.medipote.app.domain.Schedule
import com.medipote.app.domain.Shop
import com.medipote.app.domain.Stats
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {

    private val day = LocalDate.of(2026, 11, 12) // a Thursday
    private fun at(h: Int, m: Int = 0, date: LocalDate = day) = date.atTime(h, m)

    private val doli = Treatment(
        id = "t1", drugId = "paracetamol", name = "Paracétamol", strength = "1 g",
        doses = listOf(DoseTime(8 * 60), DoseTime(20 * 60)), startDate = day.minusDays(10),
        createdAt = day.minusDays(1).atStartOfDay(),
    )
    private val state = AppState(treatments = listOf(doli), onboarded = true)

    private fun item(state: AppState, now: LocalDateTime, minute: Int, date: LocalDate = now.toLocalDate()) =
        Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, date, now)
            .first { it.occurrence.dose.minuteOfDay == minute }

    // ── Schedule ──

    @Test
    fun doseStatesFollowTheTimeline() {
        assertEquals(DoseState.UPCOMING, item(state, at(7, 29), 480).state)
        assertEquals(DoseState.DUE, item(state, at(7, 30), 480).state) // 30 min early is on time
        assertEquals(DoseState.DUE, item(state, at(8, 30), 480).state)
        assertEquals(DoseState.OVERDUE, item(state, at(8, 31), 480).state)
        assertEquals(DoseState.OVERDUE, item(state, at(23, 59), 480).state)
        assertEquals(DoseState.MISSED, item(state, at(0, 1, day.plusDays(1)), 480, day).state)
    }

    @Test
    fun lateEveningDoseCanStillBeGivenAfterMidnight() {
        val late = state.copy(treatments = listOf(doli.copy(doses = listOf(DoseTime(23 * 60)))))
        val carried = Schedule.carriedOver(late.treatments, emptyMap(), at(1, 30, day.plusDays(1)))
        assertEquals(1, carried.size)
        assertEquals(DoseState.OVERDUE, carried.single().state)
    }

    @Test
    fun treatmentOnlyActiveOnChosenDaysAndPeriod() {
        val weekly = doli.copy(daysOfWeek = setOf(1), endDate = day.plusDays(30)) // Mondays only
        assertTrue(Schedule.occurrencesOn(listOf(weekly), day).isEmpty())
        assertEquals(2, Schedule.occurrencesOn(listOf(weekly), LocalDate.of(2026, 11, 16)).size)
        assertTrue(Schedule.occurrencesOn(listOf(weekly), LocalDate.of(2027, 1, 4)).isEmpty())
    }

    @Test
    fun upcomingCoversNextTwoDays() {
        val times = Schedule.upcoming(state.treatments, at(9), 48).map { it.scheduled }
        assertEquals(listOf(at(20), at(8, 0, day.plusDays(1)), at(20, 0, day.plusDays(1)), at(8, 0, day.plusDays(2))), times)
    }

    // ── Points ──

    @Test
    fun pointsDependOnPunctuality() {
        assertEquals(TakeStatus.ON_TIME, Rules.statusFor(at(8), at(7, 30)))
        assertEquals(TakeStatus.EARLY, Rules.statusFor(at(8), at(7, 10)))
        assertEquals(TakeStatus.ON_TIME, Rules.statusFor(at(8), at(8, 30)))
        assertEquals(TakeStatus.LATE, Rules.statusFor(at(8), at(9, 45)))
        assertEquals(TakeStatus.VERY_LATE, Rules.statusFor(at(8), at(12)))
        assertTrue(Rules.POINTS_ON_TIME > Rules.POINTS_LATE && Rules.POINTS_LATE > Rules.POINTS_VERY_LATE)
    }

    @Test
    fun givingADoseLogsItAndAwardsPoints() {
        val r = Game.give(state, item(state, at(8, 5), 480), at(8, 5))!!
        assertEquals(TakeStatus.ON_TIME, r.log.status)
        assertEquals(Rules.POINTS_ON_TIME, r.state.game.points)
        assertEquals(DoseState.TAKEN, item(r.state, at(8, 6), 480).state)
        // Can't give the same dose twice
        assertNull(Game.give(r.state, item(r.state, at(8, 6), 480), at(8, 6)))
    }

    @Test
    fun cannotGiveAnUpcomingDose() {
        assertNull(Game.give(state, item(state, at(5), 480), at(5)))
    }

    @Test
    fun perfectDayBonusOnlyWhenAllDosesOnTime() {
        var s = Game.give(state, item(state, at(8), 480), at(8))!!.state
        val evening = Game.give(s, item(s, at(20, 10), 1200), at(20, 10))!!
        assertEquals(Rules.PERFECT_DAY_BONUS, evening.perfectDayBonus)
        assertEquals(2 * Rules.POINTS_ON_TIME + Rules.PERFECT_DAY_BONUS, evening.state.game.points)

        s = Game.give(state, item(state, at(10), 480), at(10))!!.state // late
        val noBonus = Game.give(s, item(s, at(20), 1200), at(20))!!
        assertEquals(0, noBonus.perfectDayBonus)
    }

    // ── Streak ──

    @Test
    fun dailyStreakGrowsThenCaps() {
        var game = GameState()
        var rewards = mutableListOf<Int>()
        for (i in 0 until 12) {
            val (g, r) = Game.checkIn(game, at(9, 0, day.plusDays(i.toLong())))!!
            game = g
            rewards += r.points
        }
        assertEquals(listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 50, 50), rewards)
        assertEquals(12, game.streak)
        // Milestone at 7 days
        assertEquals(rewards.sum() + 30, game.points)
        // Same day: nothing more
        assertNull(Game.checkIn(game, at(18, 0, day.plusDays(11))))
        // Skipping a day resets the streak
        val (reset, r) = Game.checkIn(game, at(9, 0, day.plusDays(13)))!!
        assertEquals(1, reset.streak)
        assertEquals(5, r.points)
        assertEquals(12, reset.bestStreak)
    }

    // ── Pet ──

    @Test
    fun petMoodReflectsForgottenDoses() {
        assertEquals(PetMood.HUNGRY, Game.petCondition(state, at(8)).mood)
        assertEquals(PetMood.UNWELL, Game.petCondition(state, at(9)).mood)
        assertEquals(PetMood.SICK, Game.petCondition(state, at(11)).mood)
        val given = Game.give(state, item(state, at(8), 480), at(8))!!.state
        // Yesterday's two forgotten doses still weigh on its health
        val c = Game.petCondition(given, at(12))
        assertEquals(PetMood.CONTENT, c.mood)
        assertEquals(76, c.health)
    }

    @Test
    fun dosesBeforeTheTreatmentWasAddedAreNotOwed() {
        val added = state.copy(treatments = listOf(doli.copy(createdAt = at(15))))
        val today = Schedule.itemsOn(added.treatments, emptyMap(), day, at(15))
        assertEquals(listOf(1200), today.map { it.occurrence.dose.minuteOfDay })
        assertEquals(100, Game.petCondition(added, at(15)).health)
        // Added at 8:20: the 8:00 dose can still be given on time
        val justAfter = state.copy(treatments = listOf(doli.copy(createdAt = at(8, 20))))
        assertEquals(DoseState.DUE, item(justAfter, at(8, 20), 480).state)
    }

    @Test
    fun petIsHealthyWithoutTreatments() {
        val c = Game.petCondition(AppState(), at(12))
        assertEquals(100, c.health)
        assertEquals(PetMood.HAPPY, c.mood)
        assertEquals(PetMood.SLEEPING, Game.petCondition(AppState(), at(23, 30)).mood)
    }

    // ── Shop ──

    @Test
    fun buyingAndEquipping() {
        val crown = Shop.item("hat_crown")!!
        assertNull(Shop.buy(GameState(points = 10), crown))
        val bought = Shop.buy(GameState(points = 500), crown)!!
        assertEquals(100, bought.points)
        assertEquals("hat_crown", bought.equipped["HAT"])
        val off = Shop.equip(bought, crown)
        assertNull(off.equipped["HAT"])
        // Colours and backgrounds can't be taken off, only swapped
        val mint = Shop.item("color_mint")!!
        assertEquals("color_mint", Shop.equip(GameState(), mint).equipped["COLOR"])
    }

    // ── Stats ──

    @Test
    fun statsCountOnTimeLateAndMissed() {
        var s = Game.give(state, item(state, at(8, 0, day.minusDays(1)), 480, day.minusDays(1)), at(8, 0, day.minusDays(1)))!!.state
        s = Game.give(s, item(s, at(21, 0, day.minusDays(1)), 1200, day.minusDays(1)), at(21, 0, day.minusDays(1)))!!.state
        val now = at(12)
        val days = Stats.daily(s, now, 2)
        assertEquals(1, days[0].onTime)
        assertEquals(1, days[0].late)
        assertEquals(0, days[0].missed)
        assertEquals(2, days[1].pending)
        val summary = Stats.summary(s, now)
        assertEquals(1f, summary.adherence7!!, 0.001f)
        assertEquals(0.5f, summary.punctuality30!!, 0.001f)
    }

    @Test
    fun todaysOverdueDoseIsPendingNotMissed() {
        val days = Stats.daily(state, at(12), 1)
        assertEquals(0, days.single().missed)
        assertEquals(2, days.single().pending)
        val yesterday = Stats.daily(state, at(12), 2).first()
        assertEquals(2, yesterday.missed)
        assertEquals(0f, Stats.summary(state, at(12)).adherence7!!, 0.001f)
    }

    // ── Cycle ──

    private val cycle = CycleInfo(periodStarts = listOf(LocalDate.of(2026, 11, 1)), cycleLength = 28, periodLength = 5)

    @Test
    fun cycleStatusAndPredictions() {
        val s = Cycle.status(cycle, day)!!
        assertEquals(12, s.dayOfCycle)
        assertEquals(LocalDate.of(2026, 11, 29), s.nextPeriod)
        assertEquals(LocalDate.of(2026, 11, 15), s.ovulation)
        assertEquals(CyclePhase.OVULATION, s.phase)
        assertEquals(CyclePhase.MENSTRUAL, Cycle.status(cycle, LocalDate.of(2026, 11, 3))!!.phase)
        assertEquals(CyclePhase.LUTEAL, Cycle.status(cycle, LocalDate.of(2026, 11, 20))!!.phase)
    }

    @Test
    fun latePeriodIsReported() {
        val s = Cycle.status(cycle, LocalDate.of(2026, 12, 1))!!
        assertEquals(3, s.lateDays)
    }

    @Test
    fun averageLengthUsesLoggedCycles() {
        var info = cycle.copy(periodStarts = listOf(LocalDate.of(2026, 9, 6)))
        info = Cycle.logPeriod(info, LocalDate.of(2026, 10, 6)) // 30 days
        info = Cycle.logPeriod(info, LocalDate.of(2026, 11, 1)) // 26 days
        assertEquals(28, Cycle.averageLength(info))
        // A correction within a week replaces the entry instead of adding one
        assertEquals(3, Cycle.logPeriod(info, LocalDate.of(2026, 11, 2)).periodStarts.size)
    }

    @Test
    fun calendarDayTypes() {
        assertEquals(CycleDayType.PERIOD, Cycle.dayType(cycle, LocalDate.of(2026, 11, 3), day))
        assertEquals(CycleDayType.OVULATION, Cycle.dayType(cycle, LocalDate.of(2026, 11, 15), day))
        assertEquals(CycleDayType.FERTILE, Cycle.dayType(cycle, LocalDate.of(2026, 11, 12), day))
        assertEquals(CycleDayType.PREDICTED_PERIOD, Cycle.dayType(cycle, LocalDate.of(2026, 11, 30), day))
        assertEquals(CycleDayType.NONE, Cycle.dayType(cycle, LocalDate.of(2026, 11, 22), day))
    }

    // ── Drug guide ──

    @Test
    fun searchIsAccentAndCaseInsensitive() {
        assertEquals("paracetamol", DrugGuide.search("doliprane").first().id)
        assertEquals("paracetamol", DrugGuide.search("PARACETAMOL").first().id)
        assertEquals("levothyroxine", DrugGuide.search("lévothyrox").first().id)
        assertTrue(DrugGuide.search("statine").size >= 3)
        assertTrue(DrugGuide.search("zzzz").isEmpty())
        assertEquals(DrugGuide.drugs.size, DrugGuide.search("").size)
    }

    @Test
    fun drugIdsAreUnique() {
        assertEquals(DrugGuide.drugs.size, DrugGuide.drugs.map { it.id }.toSet().size)
        assertTrue(DrugGuide.drugs.size >= 100)
    }

    @Test
    fun allergyWarning() {
        val amox = DrugGuide.byId("amoxicilline")!!
        assertEquals(listOf("Pénicilline"), DrugGuide.allergyMatches(amox, listOf("Pénicilline", "Latex")))
        assertTrue(DrugGuide.allergyMatches(DrugGuide.byId("ibuprofene")!!, listOf("AINS")).isNotEmpty())
        assertTrue(DrugGuide.allergyMatches(DrugGuide.byId("levonorgestrel_ee")!!, listOf("oestrogène")).isNotEmpty())
        assertTrue(DrugGuide.allergyMatches(amox, listOf("Latex")).isEmpty())
    }

    @Test
    fun quantitiesReadNaturally() {
        assertEquals("½ comprimé", DoseUnit.TABLET.format(0.5))
        assertEquals("2 gélules", DoseUnit.CAPSULE.format(2.0))
        assertEquals("1,5 comprimés", DoseUnit.TABLET.format(1.5))
        assertEquals("10 mL", DoseUnit.ML.format(10.0))
    }

    @Test
    fun profileDefaults() {
        val p = com.medipote.app.data.Profile(name = "A", weightKg = 70.0, heightCm = 175)
        assertFalse(p.menstruating)
    }
}
