package com.radaroccaz.app

import com.radaroccaz.app.analysis.Confidence
import com.radaroccaz.app.analysis.HistoryText
import com.radaroccaz.app.analysis.Severity
import com.radaroccaz.app.analysis.VehicleAnalysis
import com.radaroccaz.app.data.AccidentStatus
import com.radaroccaz.app.data.Fact
import com.radaroccaz.app.data.FactSource
import com.radaroccaz.app.data.MileageReading
import com.radaroccaz.app.data.Priorities
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.VehicleHistory
import com.radaroccaz.app.domain.Ranking
import com.radaroccaz.app.domain.SortOrder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {
    private val today = LocalDate.of(2026, 11, 14)

    private fun described(text: String, km: Int = 85_000, year: Int = 2019) =
        car("x", 12_000, km = km, year = year, images = List(8) { "img$it" }).let {
            it.copy(description = text).copy(history = HistoryText.extract(it.title, text))
        }

    @Test
    fun wellDocumentedCarScoresHigh() {
        val good = described("Première main, jamais accidentée, carnet d'entretien complet, CT OK de mars 2026 à 80 000 km, distribution faite.")
        val a = VehicleAnalysis.analyze(good, today = today)
        assertTrue("reliability ${a.reliability}", a.reliability >= 90)
        assertEquals(Confidence.HIGH, a.confidence)
        assertTrue(a.critical.isEmpty())
        assertTrue(a.positives.any { it.title == "Première main" })
    }

    @Test
    fun problemsLowerTheScoreWithReasons() {
        val bad = described("4 propriétaires, accidentée à l'avant, embrayage à prévoir, CT avec contre-visite.")
        val a = VehicleAnalysis.analyze(bad, today = today)
        assertTrue("reliability ${a.reliability}", a.reliability <= 25)
        assertTrue(a.critical.any { it.title == "Véhicule accidenté" })
        assertTrue(a.warnings.any { it.title == "Contre-visite au contrôle technique" })
        assertTrue(a.findings.all { it.evidence == null || it.source != null })
        assertTrue(a.questions.any { "devis" in it })
    }

    @Test
    fun odometerRollbackIsDetected() {
        val l = car("x", 12_000, km = 85_000, year = 2019).copy(
            publishedAt = "2026-11-10",
            history = VehicleHistory(readings = listOf(MileageReading("2025-03", 112_000, FactSource.RAPPORT, "CT"))),
        )
        val a = VehicleAnalysis.analyze(l, today = today)
        assertTrue(a.mileage.rollback)
        assertTrue(a.critical.any { it.title == "Kilométrage incohérent" })
        val ok = l.copy(history = VehicleHistory(readings = listOf(MileageReading("2025-03", 70_000, FactSource.RAPPORT))))
        val b = VehicleAnalysis.analyze(ok, today = today)
        assertFalse(b.mileage.rollback)
        assertTrue(b.positives.any { it.title == "Relevés kilométriques cohérents" })
    }

    @Test
    fun yearlyMileageIsChecked() {
        val tiny = VehicleAnalysis.analyze(car("a", 9_000, km = 12_000, year = 2015), today = today)
        assertTrue(tiny.warnings.any { it.title == "Kilométrage très faible pour l'âge" })
        val heavy = VehicleAnalysis.analyze(car("b", 9_000, km = 210_000, year = 2021), today = today)
        assertTrue(heavy.warnings.any { it.title == "Usage intensif" })
        assertTrue(heavy.questions.any { "distribution" in it })
    }

    @Test
    fun scamAndSuspiciousPrice() {
        val scam = described("Voiture située en Belgique, livraison par transporteur, paiement par coupons PCS.")
        val a = VehicleAnalysis.analyze(scam, dealPercent = -40, today = today)
        assertTrue(a.critical.any { it.title == "Signes d'arnaque" })
        assertTrue(a.critical.any { it.title == "Prix anormalement bas" })
        assertTrue(a.reliability < 15)
    }

    @Test
    fun userEntriesOverrideTheListing() {
        val l = described("Première main, jamais accidentée")
        val user = VehicleHistory(owners = Fact(3, FactSource.VOUS), accident = Fact(AccidentStatus.SEVERE, FactSource.VOUS))
        val a = VehicleAnalysis.analyze(l, user, today = today)
        assertEquals(3, a.history.owners?.value)
        assertTrue(a.critical.any { it.title.startsWith("Sinistre grave") })
    }

    @Test
    fun historyFiltersAndRanking() {
        val clean = described("Première main, jamais accidentée, carnet d'entretien à jour").copy(id = "leboncoin:clean")
        val crashed = described("Accidentée, réparée").copy(id = "leboncoin:crashed")
        val many = described("5 propriétaires").copy(id = "leboncoin:many")
        val unknown = car("unknown", 12_000)
        val c = SearchCriteria(make = "Peugeot", model = "308", excludeDamaged = true, maxOwners = 2)
        val kept = listOf(clean, crashed, many, unknown).filter { Ranking.matches(it, c) }.map { it.id }
        assertEquals(listOf("leboncoin:clean", "leboncoin:unknown"), kept)

        val ranked = Ranking.sort(
            Ranking.score(listOf(clean, crashed, unknown), SearchCriteria(make = "Peugeot", model = "308", priorities = Priorities(history = 3)), today = today),
            SortOrder.BEST,
        )
        assertEquals(listOf("leboncoin:clean", "leboncoin:unknown", "leboncoin:crashed"), ranked.map { it.listing.id })
        assertEquals(Severity.CRITICAL, ranked.last().analysis.headline?.severity)
    }
}
