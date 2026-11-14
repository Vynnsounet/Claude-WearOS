package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.Backup
import com.medipote.app.data.DocCategory
import com.medipote.app.data.DoseStep
import com.medipote.app.data.DoseTime
import com.medipote.app.data.GameState
import com.medipote.app.data.HealthDocument
import com.medipote.app.data.MeasureType
import com.medipote.app.data.Measurement
import com.medipote.app.data.Profile
import com.medipote.app.data.Snooze
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.domain.DoctorReports
import com.medipote.app.domain.Evolution
import com.medipote.app.domain.Game
import com.medipote.app.domain.Level
import com.medipote.app.domain.Measures
import com.medipote.app.domain.Renewals
import com.medipote.app.domain.ReportBlock
import com.medipote.app.domain.ReportPart
import com.medipote.app.domain.Schedule
import com.medipote.app.domain.Stage
import com.medipote.app.domain.Tone
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Version 1.3: measurements, backups, reminders actions, report, renewals, posology, growth. */
class V13Test {

    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0, date: LocalDate = day) = date.atTime(h, m)
    private fun bp(id: String, h: Int, sys: Int, dia: Int, date: LocalDate = day) =
        Measurement(id, MeasureType.BLOOD_PRESSURE, at(h, 0, date), sys.toDouble(), dia.toDouble(), pulse = 70)

    // ── Measurements ──

    @Test
    fun measurementsAreComparedWithReferenceValues() {
        assertEquals(Level.NORMAL, Measures.assess(bp("a", 8, 124, 78))!!.level)
        assertEquals(Level.HIGH, Measures.assess(bp("a", 8, 138, 80))!!.level)
        assertEquals(Level.HIGH, Measures.assess(bp("a", 8, 128, 88))!!.level)
        assertTrue(Measures.assess(bp("a", 8, 185, 100))!!.text.startsWith("Très élevée"))
        assertEquals(Level.LOW, Measures.assess(bp("a", 8, 85, 55))!!.level)
        fun glucose(v: Double, ctx: String) = Measures.assess(Measurement("g", MeasureType.GLUCOSE, at(8), v, context = ctx))!!.level
        assertEquals(Level.NORMAL, glucose(0.95, "À jeun"))
        assertEquals(Level.HIGH, glucose(1.20, "À jeun"))
        assertEquals(Level.NORMAL, glucose(1.20, "2 h après un repas"))
        assertEquals(Level.LOW, glucose(0.60, "2 h après un repas"))
        assertEquals(Level.HIGH, Measures.assess(Measurement("t", MeasureType.TEMPERATURE, at(8), 38.4))!!.level)
        assertEquals(Level.LOW, Measures.assess(Measurement("o", MeasureType.OXYGEN, at(8), 93.0))!!.level)
        // Weight: BMI only when the height is known.
        assertNull(Measures.assess(Measurement("w", MeasureType.WEIGHT, at(8), 70.0)))
        val bmi = Measures.assess(Measurement("w", MeasureType.WEIGHT, at(8), 70.0), Profile("A", heightCm = 170))!!
        assertTrue(bmi.text, bmi.text.contains("IMC 24,2"))
    }

    @Test
    fun weightUpdatesTheProfileAndReportsFindTrends() {
        var state = AppState(profile = Profile("A", weightKg = 72.0, heightCm = 170))
        state = Measures.save(state, Measurement("w1", MeasureType.WEIGHT, at(8, 0, day.minusDays(20)), 72.0))
        state = Measures.save(state, Measurement("w2", MeasureType.WEIGHT, at(8), 70.6))
        assertEquals(70.6, state.profile!!.weightKg!!, 0.001)
        // An older measurement added later doesn't overwrite the profile.
        state = Measures.save(state, Measurement("w0", MeasureType.WEIGHT, at(8, 0, day.minusDays(25)), 73.0))
        assertEquals(70.6, state.profile!!.weightKg!!, 0.001)
        val w = Measures.report(state, MeasureType.WEIGHT, day, 30)
        assertEquals(-2.4, w.change!!, 0.001)
        assertTrue(w.insights.toString(), w.insights.any { it.text.startsWith("−2,4 kg en 25 jours") })

        state = (0 until 6).fold(state) { s, i -> Measures.save(s, bp("b$i", if (i % 2 == 0) 8 else 20, 140 + i, 86, day.minusDays(i.toLong()))) }
        val r = Measures.report(state, MeasureType.BLOOD_PRESSURE, day, 30)
        assertEquals(6, r.count)
        assertEquals(0f, r.inRange!!, 0.001f)
        assertEquals(70, r.averagePulse)
        assertTrue(r.insights.first().tone == Tone.WARN)
        assertTrue(Measures.delete(state, "b0").measurements.none { it.id == "b0" })
    }

    // ── Backups ──

    @Test
    fun backupsRoundTripWithOrWithoutPassword() {
        val dir = Files.createTempDirectory("docs").toFile()
        File(dir, "ordo.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(dir, "orphan.jpg").writeBytes(byteArrayOf(9))
        val doc = HealthDocument("d", "Ordonnance", DocCategory.PRESCRIPTION, day, "ordo.jpg", "image/jpeg", validUntil = day.plusMonths(3))
        val state = AppState(
            profile = Profile("Sam"), onboarded = true, documents = listOf(doc),
            measurements = listOf(bp("b", 8, 120, 80)), game = GameState(points = 42, growth = 33),
        )
        val plain = Backup.encode(state, dir, null, at(9))
        assertFalse(Backup.isEncrypted(plain))
        val back = Backup.decode(plain, null)
        assertEquals(state, back.state)
        assertEquals(setOf("ordo.jpg"), back.documents.keys) // only referenced documents
        assertTrue(byteArrayOf(1, 2, 3, 4).contentEquals(back.documents["ordo.jpg"]))
        assertEquals(at(9), back.createdAt)

        val secret = Backup.encode(state, dir, "très secret".toCharArray(), at(9))
        assertTrue(Backup.isEncrypted(secret))
        assertFalse(String(secret, Charsets.ISO_8859_1).contains("Sam")) // nothing readable
        assertThrows(Backup.NeedsPassword::class.java) { Backup.decode(secret, null) }
        assertThrows(Backup.WrongPassword::class.java) { Backup.decode(secret, "faux".toCharArray()) }
        assertEquals(state, Backup.decode(secret, "très secret".toCharArray()).state)
        assertThrows(Backup.NotABackup::class.java) { Backup.decode("bonjour".toByteArray(), null) }
        // A tampered file is refused.
        val tampered = secret.copyOf().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }
        assertThrows(Backup.WrongPassword::class.java) { Backup.decode(tampered, "très secret".toCharArray()) }

        val restored = Files.createTempDirectory("restore").toFile()
        File(restored, "old.jpg").writeBytes(byteArrayOf(7))
        Backup.restoreDocuments(restored, back.documents)
        assertEquals(listOf("ordo.jpg"), restored.list()!!.toList())
    }

    @Test
    fun backupsNeverWriteOutsideTheDocumentsFolder() {
        val zip = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zip).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("data.json"))
            z.write(com.medipote.app.data.AppRepository.JSON.encodeToString(AppState.serializer(), AppState()).toByteArray())
            z.putNextEntry(java.util.zip.ZipEntry("documents/../../evil.txt"))
            z.write(byteArrayOf(1))
            z.putNextEntry(java.util.zip.ZipEntry("documents/ok.jpg"))
            z.write(byteArrayOf(2))
        }
        val file = "MEDIPOTE-BACKUP".toByteArray() + byteArrayOf(1, 0) + zip.toByteArray()
        assertEquals(setOf("ok.jpg"), Backup.decode(file, null).documents.keys)
    }

    // ── Notification buttons ──

    private val two = listOf(
        Treatment("a", name = "Amlodipine", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(5), createdAt = day.minusDays(5).atStartOfDay()),
        Treatment("b", name = "Metformine", doses = listOf(DoseTime(8 * 60), DoseTime(20 * 60)), startDate = day.minusDays(5), createdAt = day.minusDays(5).atStartOfDay()),
    )

    @Test
    fun takenFromTheNotificationGivesEveryDoseOfThatTime() {
        val state = AppState(treatments = two, onboarded = true, snoozes = listOf(Snooze(at(8), at(8, 10))))
        val (after, points) = Game.giveAllAt(state, at(8), at(8, 12))!!
        assertEquals(setOf("a", "b"), after.doseLogs.map { it.treatmentId }.toSet())
        assertTrue(after.doseLogs.all { it.status == TakeStatus.ON_TIME })
        assertTrue(points >= 20)
        assertTrue(after.snoozes.isEmpty())
        assertNull(Game.giveAllAt(after, at(8), at(8, 13))) // nothing left to give
    }

    @Test
    fun snoozedRemindersRingAgainUntilGiven() {
        val state = AppState(treatments = two, onboarded = true, snoozes = listOf(Snooze(at(8), at(8, 40))))
        val plan = ReminderPlanner.plan(state, at(8, 5))
        assertEquals(listOf(at(8, 40)), plan.filter { it.kind == AlarmKind.SNOOZE }.map { it.time })
        assertEquals(at(8).toString(), plan.first { it.kind == AlarmKind.SNOOZE }.ref)
        val given = Game.giveAllAt(state, at(8), at(8, 6))!!.first.copy(snoozes = state.snoozes)
        assertTrue(ReminderPlanner.plan(given, at(8, 7)).none { it.kind == AlarmKind.SNOOZE })
    }

    // ── Prescriptions ──

    @Test
    fun prescriptionsAreRenewedInTime() {
        val doc = HealthDocument("o", "Ordonnance Dr Martin", DocCategory.PRESCRIPTION, day.minusMonths(3), "o.jpg", "image/jpeg", validUntil = day.plusDays(8), remindDays = 10)
        val other = doc.copy(id = "lab", category = DocCategory.LAB)
        val state = AppState(onboarded = true, documents = listOf(doc, other))
        assertEquals(listOf("o"), Renewals.due(state, day).map { it.id })
        assertTrue(Renewals.due(state, day.minusDays(3)).isEmpty())
        assertEquals("Valable encore 8 jours", Renewals.label(doc, day))
        assertEquals("Expirée depuis 2 jours", Renewals.label(doc, day.plusDays(10)))
        assertTrue(Renewals.due(state, day.plusDays(30)).isEmpty())
        val alarms = ReminderPlanner.plan(state, at(7, 0, day.minusDays(5)))
        assertEquals(listOf(at(10, 0, day.minusDays(2))), alarms.filter { it.kind == AlarmKind.RENEWAL }.map { it.time })
        assertTrue(ReminderPlanner.plan(state.copy(documents = listOf(doc.copy(remindDays = null))), at(7)).none { it.kind == AlarmKind.RENEWAL })
    }

    // ── Posology ──

    @Test
    fun everyNDaysAndDoseSteps() {
        val t = Treatment(
            "c", name = "Prednisolone", doses = listOf(DoseTime(8 * 60, 2.0)), startDate = day, everyDays = 2,
            steps = listOf(DoseStep(day.plusDays(4), 1.0), DoseStep(day.plusDays(8), 0.5)),
            createdAt = day.atStartOfDay(), stock = 20.0,
        )
        assertEquals(listOf(true, false, true, false, true), (0..4).map { t.isActiveOn(day.plusDays(it.toLong())) })
        assertEquals(2.0, t.quantityOn(day.plusDays(2), t.doses[0]), 0.0)
        assertEquals(1.0, t.quantityOn(day.plusDays(4), t.doses[0]), 0.0)
        assertEquals(0.5, t.quantityOn(day.plusDays(30), t.doses[0]), 0.0)
        // Stock follows the step of that day.
        var state = AppState(treatments = listOf(t), onboarded = true)
        val d4 = day.plusDays(4)
        val item = Schedule.itemsOn(state.treatments, emptyMap(), d4, d4.atTime(8, 0)).single()
        assertEquals(1.0, item.occurrence.quantity, 0.0)
        state = Game.give(state, item, d4.atTime(8, 0))!!.state
        assertEquals(19.0, state.treatments.single().stock!!, 0.001)
    }

    // ── Growth ──

    @Test
    fun theCompanionGrowsWithDosesOnTime() {
        assertEquals(Stage.SEED, Evolution.stageFor(0))
        assertEquals(Stage.SPROUT, Evolution.stageFor(20))
        assertEquals(Stage.BLOOM, Evolution.stageFor(5000))
        var state = AppState(treatments = two, onboarded = true, game = GameState(growth = 19))
        assertEquals(1, Evolution.growth(state).toNext)
        state = Game.giveAllAt(state, at(8), at(8, 0))!!.first
        assertEquals(21, state.game.growth)
        assertEquals(Stage.SPROUT, Evolution.growth(state).stage)
        // A late dose doesn't make it grow.
        val late = Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, day, at(21, 30)).first { it.canGive }
        assertEquals(21, Game.give(state, late, at(21, 30))!!.state.game.growth)
    }

    // ── Report ──

    @Test
    fun reportGathersTheChosenParts() {
        val state = AppState(
            profile = Profile("Alex", age = 34, heightCm = 168, allergies = listOf("Pénicilline")),
            treatments = two, onboarded = true,
            measurements = (0 until 4).map { bp("b$it", 8, 130 + it, 82, day.minusDays(it.toLong())) },
        )
        val parts = DoctorReports.availableParts(state)
        assertTrue(ReportPart.MEASURES in parts)
        assertFalse(ReportPart.SLEEP in parts)
        val r = DoctorReports.build(state, at(12), 30, parts.toSet())
        assertTrue(r.title.contains("Alex"))
        assertEquals(parts.map { it.label }, r.sections.map { it.title })
        val profile = r.sections.first().blocks.single() as ReportBlock.Facts
        assertTrue(profile.items.contains("Allergies" to "Pénicilline"))
        val measures = r.sections.first { it.title == ReportPart.MEASURES.label }
        assertNotNull(measures.blocks.firstOrNull { it is ReportBlock.Chart })
        val onlyTreatments = DoctorReports.build(state, at(12), 30, setOf(ReportPart.TREATMENTS))
        assertEquals(1, onlyTreatments.sections.size)
    }
}
