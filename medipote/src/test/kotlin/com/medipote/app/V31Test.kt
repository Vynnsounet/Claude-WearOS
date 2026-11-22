package com.medipote.app

import com.medipote.app.data.AppState
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.DoseTime
import com.medipote.app.data.SymptomNote
import com.medipote.app.data.Treatment
import com.medipote.app.domain.AppSearch
import com.medipote.app.domain.BodyView
import com.medipote.app.domain.BodyZone
import com.medipote.app.domain.BodyZones
import com.medipote.app.domain.CipCode
import com.medipote.app.domain.DoctorReports
import com.medipote.app.domain.MedicineDirectory
import com.medipote.app.domain.Medicines
import com.medipote.app.domain.Precautions
import com.medipote.app.domain.ReportBlock
import com.medipote.app.domain.ReportPart
import com.medipote.app.domain.Wellbeing
import com.medipote.app.ui.wellbeing.zoneAt
import com.medipote.app.ui.wellbeing.zoneRect
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.31: where it hurts, precautions, scanning a box. */
class V31Test {
    private val day = LocalDate.of(2026, 11, 19)

    // ───────────── Body map ─────────────
    @Test
    fun zonesAreKeptOnlyForANotedSymptomAndOnlyIfValid() {
        val none = Wellbeing.setZones(AppState(), day, "headache", listOf("head"))
        assertTrue(none.symptomLog.isEmpty())
        val noted = Wellbeing.setIntensity(AppState(), day, "headache", 3)
        val s = Wellbeing.setZones(noted, day, "headache", listOf("head", "neck", "head", "nowhere"))
        assertEquals(listOf("head", "neck"), s.symptomLog.single().zones)
        // changing the strength keeps the places
        assertEquals(listOf("head", "neck"), Wellbeing.setIntensity(s, day, "headache", 4).symptomLog.single().zones)
        // removing the symptom removes them
        assertTrue(Wellbeing.setIntensity(s, day, "headache", null).symptomLog.isEmpty())
    }

    @Test
    fun theMonthShowsWhereMostOften() {
        val log = listOf(
            SymptomNote(day, "pain", 3, zones = listOf("lower_back", "leg_r")),
            SymptomNote(day.minusDays(1), "pain", 2, zones = listOf("lower_back")),
            SymptomNote(day.minusDays(40), "pain", 5, zones = listOf("head")),
            SymptomNote(day, "headache", 2, zones = listOf("head")),
        )
        val counts = BodyZones.counts(AppState(symptomLog = log), "pain", day, 30)
        assertEquals(listOf(BodyZone.LOWER_BACK to 2, BodyZone.LEG_R to 1), counts)
        assertEquals("Haut du dos, Cou", BodyZones.labels(listOf("upper_back", "neck", "zz")))
    }

    @Test
    fun theRightSideIsAlwaysThePersonsOwnRight() {
        val frontR = zoneRect(BodyZone.SHOULDER_R, BodyView.FRONT)
        val backR = zoneRect(BodyZone.SHOULDER_R, BodyView.BACK)
        assertTrue("seen from the front, the right is on the viewer's left", frontR.center.x < 50f)
        assertTrue("seen from behind, it is on the viewer's right", backR.center.x > 50f)
        assertEquals(zoneRect(BodyZone.HEAD, BodyView.FRONT), zoneRect(BodyZone.HEAD, BodyView.BACK))
    }

    @Test
    fun touchingTheDrawingFindsTheZone() {
        assertEquals(BodyZone.HEAD, zoneAt(BodyView.FRONT, 50f, 14f))
        assertEquals(BodyZone.CHEST, zoneAt(BodyView.FRONT, 50f, 55f))
        assertEquals(BodyZone.LOWER_BACK, zoneAt(BodyView.BACK, 50f, 80f))
        assertEquals(BodyZone.LEG_L, zoneAt(BodyView.FRONT, 57f, 160f))
        assertNull(zoneAt(BodyView.FRONT, 5f, 5f))
        assertNull("the chest is not on the back", zoneAt(BodyView.BACK, 50f, 100f)?.takeIf { it == BodyZone.CHEST })
        BodyZone.entries.forEach { z -> z.views.forEach { v -> assertEquals("${z.label} $v", z, zoneAt(v, zoneRect(z, v).center.x, zoneRect(z, v).center.y)) } }
    }

    @Test
    fun theDoctorReportSaysWhere() {
        val log = listOf(SymptomNote(day, "pain", 4, zones = listOf("lower_back")), SymptomNote(day.minusDays(2), "pain", 3, zones = listOf("lower_back", "leg_l")))
        val report = DoctorReports.build(AppState(symptomLog = log), day.atTime(10, 0), 30, setOf(ReportPart.SYMPTOMS))
        val text = report.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Paragraph>().joinToString("\n") { it.text }
        assertTrue(text, text.contains("où : bas du dos (2 j), jambe gauche (1 j)"))
    }

    // ───────────── Precautions ─────────────
    private fun t(id: String, drug: String, vararg minutes: Int) = Treatment(id, name = drug, drugId = drug, doses = minutes.map { DoseTime(it) }, startDate = day.minusDays(30), createdAt = day.minusDays(30).atStartOfDay())

    @Test
    fun everyMedicationOfThePrecautionsExistsInTheGuide() {
        val ids = DrugGuide.drugs.map { it.id }.toSet()
        Precautions.knownIds.forEach { assertTrue("$it is not in the guide", it in ids) }
    }

    @Test
    fun habitsOfOneMedication() {
        assertTrue(Precautions.notes("levothyroxine").any { it.text.contains("à jeun") })
        assertTrue(Precautions.notes("atorvastatine").any { it.text.contains("pamplemousse") })
        assertTrue(Precautions.notes("unknown").isEmpty())
        assertTrue(Precautions.notes(null).isEmpty())
    }

    @Test
    fun spacingWarnsWhenTooClose() {
        val calcium = t("c", "calcium_vitd", 8 * 60 + 30)
        val tooClose = Precautions.spacing("levothyroxine", listOf(8 * 60), listOf(calcium))
        assertEquals(1, tooClose.size); assertTrue(tooClose[0].warn); assertTrue(tooClose[0].text, tooClose[0].text.contains("30 min") && tooClose[0].text.contains("4 h"))
        val fine = Precautions.spacing("levothyroxine", listOf(8 * 60), listOf(t("c", "calcium_vitd", 13 * 60)))
        assertEquals(1, fine.size); assertFalse(fine[0].warn)
        // as needed treatments and unrelated ones are not concerned
        assertTrue(Precautions.spacing("levothyroxine", listOf(8 * 60), listOf(calcium.copy(asNeeded = true))).isEmpty())
        assertTrue(Precautions.spacing("levothyroxine", listOf(8 * 60), listOf(t("p", "paracetamol", 8 * 60))).isEmpty())
        // it works from the other side too, and wraps around midnight
        assertTrue(Precautions.spacing("calcium_vitd", listOf(8 * 60 + 30), listOf(t("l", "levothyroxine", 8 * 60))).single().warn)
        assertTrue(Precautions.spacing("fer", listOf(23 * 60 + 30), listOf(t("c", "calcium_vitd", 30))).single().warn)
    }

    @Test
    fun problemsListEachPairOnce() {
        val list = listOf(t("l", "levothyroxine", 8 * 60), t("c", "calcium_vitd", 8 * 60 + 15), t("p", "paracetamol", 8 * 60))
        assertEquals(1, Precautions.problems(list).size)
        assertTrue(Precautions.problems(listOf(t("l", "levothyroxine", 8 * 60), t("c", "calcium_vitd", 14 * 60))).isEmpty())
    }

    // ───────────── Scan ─────────────
    @Test
    fun readsTheCodesOfABox() {
        val cip = "3400930065556"
        assertTrue(CipCode.valid(cip))
        assertFalse(CipCode.valid("3400930065557"))
        assertEquals(cip, CipCode.parse(cip))
        assertEquals(cip, CipCode.parse("010$cip" + "21ABC123\u001d10LOT12" + "172812"))
        assertEquals(cip, CipCode.parse("0103400930065556" + "17271231" + "10A1"))
        assertNull(CipCode.parse("5449000000996"))
        assertNull(CipCode.parse("hello"))
        assertNull(CipCode.parse("3400930065557"))
    }

    private fun directory(vararg rows: String): MedicineDirectory {
        val bytes = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(("# BDPM test\n" + rows.joinToString("\n")).toByteArray()) } }.toByteArray()
        return MedicineDirectory.parse(ByteArrayInputStream(bytes))
    }

    @Test
    fun identifiesABoxFromItsName() {
        val dir = directory("3400930065556\tLEVOTHYROX 75 microgrammes, comprimé sécable", "3400935955838\tDOLIPRANE 1000 mg, comprimé", "3400939999999\tTRUCMUCHE 5 mg, gélule")
        assertEquals(3, dir.size)
        val levo = dir.identify("0103400930065556" + "21XYZ")!!
        assertEquals("levothyroxine", levo.drug?.id)
        assertEquals("75 µg", levo.strength)
        val doli = dir.identify("3400935955838")!!
        assertEquals("paracetamol", doli.drug?.id)
        assertEquals("1000 mg", doli.strength)
        assertNull(dir.identify("3400930000000"))
    }

    @Test
    fun anUnknownBoxStillGivesAName() {
        assertNull(Medicines.drugFor("TRUCMUCHE 5 mg, gélule"))
        assertEquals("Trucmuche", Medicines.displayName("TRUCMUCHE 5 mg, gélule"))
        assertEquals("5 mg", Medicines.strength("TRUCMUCHE 5 mg, gélule"))
        assertEquals("", Medicines.strength("TRUCMUCHE, gélule"))
        assertEquals("Paracetamol codeine arrow", Medicines.displayName("PARACETAMOL CODEINE ARROW 500 mg/30 mg, comprimé"))
    }

    @Test
    fun theDirectoryShippedInTheAppReadsAndKnowsCommonBoxes() {
        val asset = java.io.File("src/main/assets/cip.tsv.gz")
        assertTrue(asset.exists())
        val dir = asset.inputStream().use(MedicineDirectory::parse)
        assertTrue("${dir.size} boxes", dir.size > 15_000)
        assertTrue(dir.lookup("3400930065556")!!.startsWith("LEVOTHYROX"))
        val levo = dir.identify("3400930065556")!!
        assertEquals("levothyroxine", levo.drug?.id)
        assertEquals("25 µg", levo.strength)
    }

    // ───────────── Search ─────────────
    @Test
    fun scanAndBodyAreFindable() {
        assertTrue(AppSearch.CATALOG.any { it.id == "scan" })
        assertTrue(AppSearch.CATALOG.first { it.id == "suivi:symptoms" }.keywords.contains("où ça fait mal"))
    }
}
