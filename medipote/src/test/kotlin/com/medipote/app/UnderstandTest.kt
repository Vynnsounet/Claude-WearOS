package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.Awakening
import com.medipote.app.data.CycleInfo
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DoseLog
import com.medipote.app.data.DoseTime
import com.medipote.app.data.MeasureType
import com.medipote.app.data.Measurement
import com.medipote.app.data.MyCondition
import com.medipote.app.data.PillPack
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.SleepNight
import com.medipote.app.data.SymptomNote
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.data.WaterSettings
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.AppSearch
import com.medipote.app.domain.Concern
import com.medipote.app.domain.DoseOccurrence
import com.medipote.app.domain.Understand
import com.medipote.app.domain.Urgency
import com.medipote.app.ui.HubCatalog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** « Comprendre »: rules written by hand. These tests are the contract: what each rule says, and what it never says. */
class UnderstandTest {
    private val day = LocalDate.of(2026, 11, 19)

    private fun night(daysAgo: Int, minutes: Int, blank: Boolean = false, latency: Int = 15, awake: List<Awakening> = emptyList()): SleepNight {
        val date = day.minusDays(daysAgo.toLong())
        val bed = date.minusDays(1).atTime(23, 0)
        val wake = bed.plusMinutes(minutes.toLong() + latency + awake.sumOf { it.minutes })
        return SleepNight(date, bed, latency, wake, awake.map { it.copy(at = bed.plusHours(4)) }, noSleep = blank)
    }

    private fun note(daysAgo: Int, symptom: String, intensity: Int) = SymptomNote(day.minusDays(daysAgo.toLong()), symptom, intensity)
    private fun ids(list: List<Concern>) = list.map { it.key }

    private val levo = Treatment("levo", name = "Lévothyrox", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(200))
    private fun taken(daysAgo: Int) = DoseLog(
        DoseOccurrence.keyOf("levo", day.minusDays(daysAgo.toLong()), 8 * 60), "levo",
        day.minusDays(daysAgo.toLong()).atTime(8, 0), day.minusDays(daysAgo.toLong()).atTime(8, 5), TakeStatus.ON_TIME, 10,
    )
    private fun bp(daysAgo: Int, sys: Int, dia: Int) = Measurement("bp$daysAgo", MeasureType.BLOOD_PRESSURE, day.minusDays(daysAgo.toLong()).atTime(9, 0), sys.toDouble(), dia.toDouble())
    private fun measure(type: MeasureType, daysAgo: Int, v: Double) = Measurement("${type}$daysAgo", type, day.minusDays(daysAgo.toLong()).atTime(9, 0), v)

    // ───────────── What can explain it ─────────────
    @Test
    fun aShortNightCanExplainTiredness_withItsNumbers() {
        val s = AppState(sleep = listOf(night(0, 5 * 60), night(1, 5 * 60 + 30), night(2, 6 * 60)))
        val f = Understand.factors(s, "fatigue", day)
        val sleep = f.single { it.title == "Peu de sommeil" }
        assertTrue(sleep.evidence, "5 h 30" in sleep.evidence)
        assertTrue("objectif" in sleep.evidence)
        // A symptom that sleep does not usually explain does not get it.
        assertTrue(Understand.factors(s, "joints", day).none { it.title == "Peu de sommeil" })
        // Enough sleep: nothing to say.
        assertTrue(Understand.factors(AppState(sleep = listOf(night(0, 8 * 60))), "fatigue", day).isEmpty())
    }

    @Test
    fun aSleeplessNightIsNamed() {
        val f = Understand.factors(AppState(sleep = listOf(night(1, 0, blank = true))), "headache", day)
        assertTrue(f.any { it.title == "Une nuit blanche" })
        assertFalse(f.any { it.title == "Peu de sommeil" })
    }

    private fun cycleState(startedDaysAgo: Int) = AppState(
        profile = Profile("Alex", menstruating = true, cycle = CycleInfo(listOf(day.minusDays(startedDaysAgo + 28L), day.minusDays(startedDaysAgo.toLong())))),
    )

    @Test
    fun thePeriodCanExplainPain_andOnlyWhenThereIsACycle() {
        assertTrue(Understand.factors(cycleState(1), "pain", day).any { it.title == "Tes règles" })
        assertTrue(Understand.factors(AppState(), "pain", day).isEmpty())
        // The days before the period too.
        assertTrue(Understand.factors(cycleState(25), "irritability", day).any { it.title == "Les jours avant tes règles" })
        // Mid-cycle: nothing from the cycle.
        assertTrue(Understand.factors(cycleState(10), "irritability", day).none { it.title.contains("règles") })
    }

    @Test
    fun littleWaterCanExplainAHeadache() {
        val s = AppState(settings = Settings(water = WaterSettings(enabled = true, goalGlasses = 8)), journal = listOf(DayEntry(day, waterGlasses = 1), DayEntry(day.minusDays(1), waterGlasses = 2)))
        assertTrue(Understand.factors(s, "headache", day).any { it.title == "Peu d'eau" })
        val enough = s.copy(journal = listOf(DayEntry(day, waterGlasses = 7), DayEntry(day.minusDays(1), waterGlasses = 8)))
        assertTrue(Understand.factors(enough, "headache", day).none { it.title == "Peu d'eau" })
        // Not tracking water at all: no accusation.
        assertTrue(Understand.factors(AppState(), "headache", day).none { it.title == "Peu d'eau" })
    }

    @Test
    fun aRecentTreatmentIsMentioned_withTheAdviceToAskAPharmacist() {
        val fresh = AppState(treatments = listOf(levo.copy(startDate = day.minusDays(5))))
        val f = Understand.factors(fresh, "nausea", day).single { it.title.startsWith("Un traitement commencé") }
        assertTrue(f.evidence.contains("Lévothyrox"))
        assertTrue(f.tip!!.contains("pharmacien"))
        assertTrue(f.tip!!.contains("N'arrête rien"))
        assertTrue(Understand.factors(AppState(treatments = listOf(levo)), "nausea", day).none { it.title.startsWith("Un traitement commencé") })
    }

    @Test
    fun forgottenDosesAreMentionedKindly() {
        val s = AppState(treatments = listOf(levo), doseLogs = listOf(taken(3)))
        assertEquals(2, Understand.missedDoses(s, day, 3))
        val f = Understand.factors(s, "fatigue", day).single { it.title == "Des prises manquées" }
        assertTrue(f.tip!!.contains("astuces"))
        assertEquals(0, Understand.missedDoses(s.copy(doseLogs = listOf(taken(1), taken(2), taken(3))), day, 3))
    }

    @Test
    fun feverAndBloodPressureAndIllnessCanExplain() {
        val fever = AppState(measurements = listOf(measure(MeasureType.TEMPERATURE, 0, 38.6)))
        assertTrue(Understand.factors(fever, "headache", day).any { it.title == "De la fièvre" })
        assertTrue(Understand.factors(fever, "dizzy", day).none { it.title == "De la fièvre" })
        val pressure = AppState(measurements = listOf(bp(0, 150, 95)))
        assertTrue(Understand.factors(pressure, "headache", day).any { it.title == "Une tension élevée" })
        val ill = AppState(journal = listOf(DayEntry(day.minusDays(1), illness = "Grippe")))
        assertTrue(Understand.factors(ill, "fatigue", day).single { it.title == "Une maladie en cours" }.evidence.contains("Grippe"))
    }

    @Test
    fun theConditionsFollowedCanExplain() {
        val s = AppState(settings = Settings(wellbeing = WellbeingSettings(enabled = true, conditions = listOf(MyCondition("Hashimoto", "hashimoto")))))
        assertTrue(Understand.factors(s, "fatigue", day).any { it.title.contains("Hashimoto") })
    }

    @Test
    fun patternsInThePastNotesAreShown() {
        // Fatigue noted on 6 days, three after short nights (strong) and three after good ones (light).
        val nights = (1..3).map { night(it, 5 * 60) } + (4..6).map { night(it, 8 * 60) }
        val notes = (1..3).map { note(it, "fatigue", 4) } + (4..6).map { note(it, "fatigue", 2) } + (1..4).map { note(it, "headache", 3) }
        val f = Understand.factors(AppState(sleep = nights, symptomLog = notes), "fatigue", day.minusDays(10).plusDays(10))
        assertTrue(f.any { it.title == "Plus fort après une courte nuit" })
        assertTrue(f.any { it.title.startsWith("Souvent avec") && it.evidence.contains("4 fois") })
    }

    @Test
    fun aSymptomThatComesBackInAPhaseIsShown() {
        val start = day.minusDays(120)
        val starts = (0..4).map { start.plusDays(28L * it) }
        val s = AppState(
            profile = Profile("Alex", menstruating = true, cycle = CycleInfo(starts, periodLength = 5)),
            // Luteal days: cycle days 20 to 24 of each cycle.
            symptomLog = starts.dropLast(1).flatMap { st -> (20..23).map { SymptomNote(st.plusDays(it.toLong()), "irritability", 3) } },
        )
        val f = Understand.factors(s, "irritability", day)
        assertTrue(f.toString(), f.any { it.title == "Surtout en phase lutéale" })
    }

    // ───────────── Should I see someone? ─────────────
    @Test
    fun calmNotesSayNothing() {
        val s = AppState(symptomLog = listOf(note(0, "fatigue", 2), note(3, "headache", 1)), sleep = (0..6).map { night(it, 7 * 60 + 30) })
        assertTrue(Understand.scan(s, day).isEmpty())
    }

    @Test
    fun aStrongSymptomForDaysIsWorthAnAppointment() {
        val s = AppState(symptomLog = listOf(note(0, "pain", 4), note(1, "pain", 5)))
        val c = Understand.symptomConcerns(s, "pain", day).single()
        assertEquals(Urgency.SOON, c.urgency)
        assertTrue(c.reasons.single().contains("2 des 3"))
        // A single strong day: not yet.
        assertTrue(Understand.symptomConcerns(AppState(symptomLog = listOf(note(0, "pain", 5))), "pain", day).isEmpty())
    }

    @Test
    fun theMaximumBreathlessnessAsksToCall() {
        val c = Understand.symptomConcerns(AppState(symptomLog = listOf(note(0, "breath", 5))), "breath", day).first { it.urgency == Urgency.URGENT }
        assertTrue(c.reasons.single().contains("15"))
        // The same mark on another symptom is not an emergency by itself.
        assertTrue(Understand.symptomConcerns(AppState(symptomLog = listOf(note(0, "fatigue", 5))), "fatigue", day).none { it.urgency == Urgency.URGENT })
    }

    @Test
    fun aSymptomAlmostEveryDayIsWorthAnAppointment_oneForTwoWeeksIsToMention() {
        val almost = AppState(symptomLog = (0..4).map { note(it, "fatigue", 3) })
        assertEquals(Urgency.SOON, Understand.symptomConcerns(almost, "fatigue", day).single { it.key.endsWith("frequent") }.urgency)
        val long = AppState(symptomLog = (0..13 step 1).filter { it % 3 != 2 }.map { note(it, "fatigue", 2) })
        val c = Understand.symptomConcerns(long, "fatigue", day).single()
        assertEquals(Urgency.MENTION, c.urgency)
        assertTrue(c.key.endsWith("long"))
    }

    @Test
    fun aWorseningSymptomIsToMention() {
        val s = AppState(symptomLog = listOf(note(0, "pain", 4), note(1, "pain", 3), note(2, "pain", 3)) + (3..9).map { note(it, "pain", 1) })
        assertTrue(Understand.symptomConcerns(s, "pain", day).any { it.key.endsWith("worse") && it.urgency == Urgency.MENTION })
    }

    @Test
    fun severalSymptomsAtOnceAreWorthAnAppointment() {
        val s = AppState(symptomLog = listOf("fatigue", "pain", "headache").flatMap { listOf(note(0, it, 3), note(1, it, 3)) })
        assertTrue(Understand.scan(s, day).any { it.key == "many" && it.urgency == Urgency.SOON })
    }

    @Test
    fun aVeryHighPressureSaysWhatToDo_andWithAHeadacheAsksToCall() {
        val high = AppState(measurements = listOf(bp(0, 185, 112)))
        val c = Understand.scan(high, day).single()
        assertEquals(Urgency.SOON, c.urgency)
        assertTrue(c.reasons.single().contains("15"))
        val withHeadache = high.copy(symptomLog = listOf(note(0, "headache", 4)))
        assertTrue(Understand.scan(withHeadache, day).any { it.urgency == Urgency.URGENT && it.symptom == "headache" })
        // A usual pressure says nothing.
        assertTrue(Understand.scan(AppState(measurements = listOf(bp(0, 125, 80))), day).isEmpty())
        // An old measure says nothing now.
        assertTrue(Understand.scan(AppState(measurements = listOf(bp(5, 190, 115))), day).isEmpty())
    }

    @Test
    fun aLowOxygenLevelAsksToCall() {
        assertEquals(Urgency.URGENT, Understand.scan(AppState(measurements = listOf(measure(MeasureType.OXYGEN, 0, 90.0))), day).single().urgency)
        assertEquals(Urgency.SOON, Understand.scan(AppState(measurements = listOf(measure(MeasureType.OXYGEN, 0, 94.0))), day).single().urgency)
        assertTrue(Understand.scan(AppState(measurements = listOf(measure(MeasureType.OXYGEN, 0, 97.0))), day).isEmpty())
    }

    @Test
    fun feverPulseAndWeight() {
        assertEquals("fever:high", Understand.scan(AppState(measurements = listOf(measure(MeasureType.TEMPERATURE, 0, 39.2))), day).single().key)
        val longFever = AppState(measurements = (0..2).map { measure(MeasureType.TEMPERATURE, it, 38.3) })
        assertEquals("fever:long", Understand.scan(longFever, day).single().key)
        assertTrue(Understand.scan(AppState(measurements = listOf(measure(MeasureType.TEMPERATURE, 0, 37.2))), day).isEmpty())
        assertEquals("pulse", Understand.scan(AppState(measurements = listOf(measure(MeasureType.HEART_RATE, 0, 135.0))), day).single().key)
        assertTrue(Understand.scan(AppState(measurements = listOf(measure(MeasureType.HEART_RATE, 0, 72.0))), day).isEmpty())
        val weight = AppState(measurements = listOf(measure(MeasureType.WEIGHT, 25, 70.0), measure(MeasureType.WEIGHT, 1, 65.0)))
        assertEquals(Urgency.MENTION, Understand.scan(weight, day).single().urgency)
        assertTrue(Understand.scan(AppState(measurements = listOf(measure(MeasureType.WEIGHT, 25, 70.0), measure(MeasureType.WEIGHT, 1, 69.0))), day).isEmpty())
    }

    @Test
    fun sleepConcerns() {
        val blank = AppState(sleep = listOf(night(1, 0, blank = true), night(3, 0, blank = true)) + (4..6).map { night(it, 7 * 60) })
        assertEquals("sleep:blank", Understand.scan(blank, day).single().key)
        val short = AppState(sleep = (0..5).map { night(it, 4 * 60 + 30) })
        assertEquals("sleep:short", Understand.scan(short, day).single().key)
        // One bad night is just a bad night.
        assertTrue(Understand.scan(AppState(sleep = listOf(night(1, 0, blank = true)) + (2..6).map { night(it, 7 * 60) }), day).isEmpty())
    }

    @Test
    fun cycleConcerns_andThePillSilencesThem() {
        fun st(last: Int, vararg earlier: Int) = AppState(profile = Profile("Alex", menstruating = true, cycle = CycleInfo((earlier.toList() + last).map { day.minusDays(it.toLong()) }.sorted())))
        assertEquals("cycle:absent", Understand.scan(st(100, 130, 160), day).single().key)
        assertEquals("cycle:late", Understand.scan(st(60, 88, 116), day).single().key)
        assertTrue(Understand.scan(st(20, 48, 76), day).isEmpty())
        // A pill can stop periods: nothing said.
        val pill = st(100, 130, 160).copy(treatments = listOf(levo.copy(id = "p", pill = PillPack(21, 7, false, day.minusDays(60)))))
        assertTrue(Understand.scan(pill, day).none { it.key.startsWith("cycle") })
        // Three cycles outside the usual range.
        val irregular = AppState(profile = Profile("Alex", menstruating = true, cycle = CycleInfo(listOf(day.minusDays(75), day.minusDays(45), day.minusDays(10), day.minusDays(0).minusDays(0)).let { listOf(day.minusDays(140), day.minusDays(100), day.minusDays(55), day.minusDays(10)) })))
        assertTrue(Understand.scan(irregular, day).any { it.key == "cycle:irregular" })
        // Without a cycle, nothing.
        assertTrue(Understand.cycleConcerns(AppState(), day).isEmpty())
    }

    @Test
    fun aLowMoodForAWhileAsksForSupport_missedDosesAreToMention() {
        val low = AppState(journal = (0..9).map { DayEntry(day.minusDays(it.toLong()), mental = 2) })
        val c = Understand.scan(low, day).single { it.key == "mood" }
        assertEquals(Urgency.SOON, c.urgency)
        assertTrue(c.reasons.single().contains("personne de confiance"))
        assertTrue(Understand.scan(AppState(journal = (0..9).map { DayEntry(day.minusDays(it.toLong()), mental = 4) }), day).isEmpty())
        val missed = AppState(treatments = listOf(levo))
        assertEquals(Urgency.MENTION, Understand.scan(missed, day).single { it.key == "missed" }.urgency)
    }

    @Test
    fun theMostUrgentComesFirst() {
        val s = AppState(measurements = listOf(measure(MeasureType.OXYGEN, 0, 90.0)), treatments = listOf(levo), symptomLog = listOf(note(0, "pain", 4), note(1, "pain", 4)))
        assertEquals(Urgency.URGENT, Understand.scan(s, day).first().urgency)
        assertEquals(Urgency.MENTION, Understand.scan(s, day).last().urgency)
    }

    // ───────────── Setting aside ─────────────
    @Test
    fun aConcernSetAsideComesBackAfterAWeek_anUrgentOneNever() {
        val soon = Concern("a", Urgency.SOON, "t", listOf("r"))
        val urgent = Concern("b", Urgency.URGENT, "t", listOf("r"))
        val set = Understand.dismiss(emptyMap(), "a", day.minusDays(3)) + Understand.dismiss(emptyMap(), "b", day.minusDays(3))
        assertEquals(listOf("b"), ids(Understand.visible(listOf(soon, urgent), set, day)))
        assertEquals(listOf("a", "b"), ids(Understand.visible(listOf(soon, urgent), set, day.plusDays(5))))
        // Old set-asides are forgotten.
        assertFalse(Understand.dismiss(mapOf("old" to day.minusDays(100).toString()), "a", day).containsKey("old"))
    }

    // ───────────── Advice ─────────────
    @Test
    fun cycleAdviceFollowsThePhase_andThereIsNoneWithoutACycle() {
        assertTrue(Understand.cycleAdvice(AppState(), day).isEmpty())
        val period = Understand.cycleAdvice(cycleState(1), day).joinToString(" ") { it.text }
        assertTrue(period.contains("chaleur") || period.contains("Chaleur") || period.contains("bouillotte"))
        val luteal = Understand.cycleAdvice(cycleState(26), day).joinToString(" ") { it.text }
        assertTrue(luteal, luteal.contains("caféine"))
        assertTrue(luteal, luteal.contains("règles sont attendues"))
        assertTrue(Understand.cycleAdvice(cycleState(10), day).isNotEmpty())
    }

    @Test
    fun sleepAdviceIsDrawnFromTheNights() {
        assertTrue(Understand.sleepAdvice(AppState(), day).isEmpty())
        val s = AppState(sleep = (0..9).map { night(it, 5 * 60, latency = 50, awake = listOf(Awakening(day.atStartOfDay(), 30, "Bruit"))) })
        val text = Understand.sleepAdvice(s, day).joinToString(" ") { it.text }
        assertTrue(text, text.contains("à t'endormir"))
        assertTrue(text, text.contains("de moins que ton objectif"))
        assertTrue(text, text.contains("bruit") || text.contains("Bruit"))
        assertTrue(Understand.sleepAdvice(s, day).size <= 5)
        assertTrue(Understand.sleepAdvice(AppState(sleep = listOf(night(0, 0, blank = true))), day).any { it.text.startsWith("Après une nuit blanche") })
    }

    // ───────────── What it never says ─────────────
    @Test
    fun itNeverTalksAboutMedicationOrDiagnoses() {
        val everything = AppState(
            profile = Profile("Alex", menstruating = true, cycle = CycleInfo(listOf(day.minusDays(26), day.minusDays(0).minusDays(26 + 28)).sorted())),
            treatments = listOf(levo, levo.copy(id = "n", startDate = day.minusDays(3))),
            sleep = (0..9).map { night(it, 4 * 60, latency = 60, awake = listOf(Awakening(day.atStartOfDay(), 40, "Toilettes"))) } + night(0, 0, blank = true),
            symptomLog = listOf("fatigue", "headache", "pain", "nausea", "dizzy", "fog", "lowmood", "anxiety", "irritability", "breath", "palpitations").flatMap { s -> (0..9).map { note(it, s, 5) } },
            measurements = listOf(bp(0, 190, 115), measure(MeasureType.TEMPERATURE, 0, 39.5), measure(MeasureType.OXYGEN, 0, 90.0), measure(MeasureType.HEART_RATE, 0, 140.0)),
            journal = (0..13).map { DayEntry(day.minusDays(it.toLong()), mental = 1, illness = "Grippe") },
            settings = Settings(water = WaterSettings(enabled = true)),
        )
        val texts = buildList {
            listOf("fatigue", "headache", "pain", "nausea", "dizzy", "fog", "lowmood", "anxiety", "irritability", "breath", "palpitations", "joints", "digestion", "sleep", "cold").forEach { s ->
                Understand.factors(everything, s, day).forEach { add(it.title); add(it.evidence); it.tip?.let(::add) }
            }
            Understand.scan(everything, day).forEach { add(it.title); addAll(it.reasons) }
            Understand.cycleAdvice(everything, day).forEach { add(it.text) }
            Understand.sleepAdvice(everything, day).forEach { add(it.text) }
        }
        assertTrue(texts.size > 30)
        val banned = listOf(
            "paracétamol", "doliprane", "ibuprofène", "aspirine", "antibiotique", "anti-inflammatoire", "dafalgan", "spasfon", "magnésium", "mélatonine", "somnifère",
            "double ta dose", "augmente ta dose", "diminue ta dose", "arrête ton traitement", "tu as un", "tu souffres de", "diagnostic de", "c'est sûrement", "c'est certainement",
        )
        texts.forEach { t -> banned.forEach { b -> assertFalse("« $b » dans « $t »", t.lowercase().contains(b)) } }
        // And the red lines always point to help.
        Understand.scan(everything, day).filter { it.urgency == Urgency.URGENT }.forEach { c ->
            assertTrue(c.title, c.reasons.joinToString(" ").contains("15"))
        }
    }

    // ───────────── Wiring ─────────────
    @Test
    fun theCardIsInTheCatalogAndTheSearch() {
        assertTrue(HubCatalog.EVERYTHING.any { it.key == "suivi:checkup" })
        assertTrue(AppSearch.query(AppState(), "dois-je consulter").any { it.id == "suivi:checkup" })
        assertTrue(AppSearch.query(AppState(), "pourquoi je me sens mal").any { it.id == "suivi:checkup" })
    }

    @Test
    fun anOldSaveLoadsWithTheNewSettings() {
        val state = AppRepository.JSON.decodeFromString(AppState.serializer(), """{"onboarded":true,"settings":{"themeMode":"DARK"}}""")
        assertTrue(state.settings.checkups)
        assertTrue(state.settings.dismissedChecks.isEmpty())
        val saved = state.copy(settings = state.settings.copy(checkups = false, dismissedChecks = Understand.dismiss(emptyMap(), "mood", day)))
        val back = AppRepository.JSON.decodeFromString(AppState.serializer(), AppRepository.JSON.encodeToString(AppState.serializer(), saved))
        assertEquals(saved.settings, back.settings)
        assertNotNull(back.settings.dismissedChecks["mood"])
        assertNull(back.settings.dismissedChecks["other"])
    }
}
