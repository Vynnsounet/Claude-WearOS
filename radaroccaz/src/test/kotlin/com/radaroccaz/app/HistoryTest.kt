package com.radaroccaz.app

import com.radaroccaz.app.analysis.HistoryText
import com.radaroccaz.app.analysis.StructuredHistory
import com.radaroccaz.app.data.AccidentStatus
import com.radaroccaz.app.data.CtStatus
import com.radaroccaz.app.data.Fact
import com.radaroccaz.app.data.FactSource
import com.radaroccaz.app.data.VehicleHistory
import com.radaroccaz.app.sources.AutoScout24Source
import com.radaroccaz.app.sources.HttpFetcher
import com.radaroccaz.app.sources.LeboncoinSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    @Test
    fun ownersFromText() {
        assertEquals(1, HistoryText.extract("Vends ma 308, première main, révisée").owners?.value)
        assertEquals(1, HistoryText.extract("1ère main, carnet ok").owners?.value)
        assertEquals(1, HistoryText.extract("un seul propriétaire depuis neuve").owners?.value)
        assertEquals(2, HistoryText.extract("2 propriétaires, entretien suivi").owners?.value)
        assertEquals(3, HistoryText.extract("Voiture de troisième main").owners?.value)
        assertEquals(2, HistoryText.extract("deux propriétaires").owners?.value)
        assertNull(HistoryText.extract("Moteur 1.2 130 ch").owners)
        assertTrue(HistoryText.extract("Vends ma 308, première main, révisée").owners!!.evidence!!.contains("première main"))
    }

    @Test
    fun accidentsAndNegations() {
        assertEquals(AccidentStatus.NONE, HistoryText.extract("Jamais accidentée, très bon état").accident?.value)
        assertEquals(AccidentStatus.NONE, HistoryText.extract("Non accidenté, aucun choc").accident?.value)
        assertEquals(AccidentStatus.DAMAGED, HistoryText.extract("Accidentée à l'avant, réparée").accident?.value)
        assertEquals(AccidentStatus.MINOR, HistoryText.extract("Petit choc sur la portière arrière").accident?.value)
        assertEquals(AccidentStatus.SEVERE, HistoryText.extract("Ancien VGA, réparée par expert").accident?.value)
        assertEquals(AccidentStatus.SEVERE, HistoryText.extract("Procédure VE levée en 2023").accident?.value)
        // A bumper is not a crash.
        assertNull(HistoryText.extract("Pare-chocs avant repeint").accident)
        // Worst statement wins even if another one is denied.
        assertEquals(AccidentStatus.MINOR, HistoryText.extract("Jamais accidentée mais un choc arrière léger").accident?.value)
    }

    @Test
    fun maintenanceInspectionAndReadings() {
        val h = HistoryText.extract(
            "Carnet d'entretien à jour. Distribution faite à 120 000 km. " +
                "CT OK, dernier CT de mars 2025 à 128 400 km, CT du 04/2023 à 101 000 km.",
        )
        assertEquals(true, h.serviceRecords?.value)
        assertEquals(CtStatus.OK, h.technicalInspection?.value)
        assertEquals("2025-03", h.lastInspection?.value)
        assertEquals(listOf("2025-03" to 128_400, "2023-04" to 101_000), h.readings.map { it.date to it.km })
        assertTrue(h.positives.any { it.value == "Distribution faite à 120 000 km" })

        assertEquals(CtStatus.COUNTER_VISIT, HistoryText.extract("CT avec contre-visite pour les pneus").technicalInspection?.value)
        assertEquals(CtStatus.TO_DO, HistoryText.extract("Vendue sans CT").technicalInspection?.value)
        assertEquals(false, HistoryText.extract("Pas de carnet d'entretien").serviceRecords?.value)
    }

    @Test
    fun problemsAndGoodPoints() {
        val h = HistoryText.extract("Embrayage à prévoir, voyant moteur allumé, aucune fuite. Pneus neufs, non fumeur.")
        assertEquals(setOf("Embrayage à changer", "Voyant allumé"), h.issues.map { it.value }.toSet())
        assertTrue(h.positives.any { it.value == "Pneus récents" })
        assertEquals(true, h.nonSmoker?.value)
        // "aucun frais à prévoir" is good news, not "travaux à prévoir".
        val ok = HistoryText.extract("Aucun frais à prévoir")
        assertTrue(ok.issues.isEmpty())
        assertTrue(ok.positives.any { it.value == "Aucun frais à prévoir" })
        assertEquals(false, HistoryText.extract("Kilométrage non garanti, compteur changé").mileageGuaranteed?.value)
        assertEquals(true, HistoryText.extract("Véhicule importé d'Allemagne").imported?.value)
        assertEquals(false, HistoryText.extract("Véhicule non importé, origine France").imported?.value)
        assertEquals(12, HistoryText.extract("Garantie 12 mois").warrantyMonths?.value)
        assertEquals(24, HistoryText.extract("garantie 2 ans pièces et main d'oeuvre").warrantyMonths?.value)
    }

    @Test
    fun scamWording() {
        val h = HistoryText.extract("Voiture située en Espagne, livraison par transporteur, paiement par mandat cash")
        assertEquals(3, h.scamSignals.size)
        assertTrue(HistoryText.extract("Voiture visible à Bordeaux, paiement par virement à la remise des clés").scamSignals.isEmpty())
    }

    @Test
    fun datesAndReportLinks() {
        assertEquals("2024-03", HistoryText.parseDate("03/2024"))
        assertEquals("2024-03", HistoryText.parseDate("12/03/2024"))
        assertEquals("2024-03", HistoryText.parseDate("mars 2024"))
        assertEquals("2024-06", HistoryText.parseDate("2024"))
        assertNull(HistoryText.parseDate("13/2024"))
        assertEquals(
            "https://histovec.interieur.gouv.fr/histovec/report?id=a&key=b",
            HistoryText.extract("Rapport : https://histovec.interieur.gouv.fr/histovec/report?id=a&key=b merci").reportUrl,
        )
    }

    @Test
    fun structuredFields() {
        val h = StructuredHistory.fromPairs(listOf(
            "numberOfPreviousOwners" to "2", "hadAccident" to "false", "hasFullServiceHistory" to "true",
            "nonSmoking" to "true", "vehicle_damage" to "Non endommagé", "warranty" to "12",
        ))
        assertEquals(2, h.owners?.value)
        assertEquals(AccidentStatus.NONE, h.accident?.value)
        assertEquals(true, h.serviceRecords?.value)
        assertEquals(12, h.warrantyMonths?.value)
        assertEquals(FactSource.SITE, h.owners?.source)
    }

    @Test
    fun mergeKeepsTheMostTrustedSource() {
        val fromText = VehicleHistory(owners = Fact(1, FactSource.ANNONCE), accident = Fact(AccidentStatus.NONE, FactSource.ANNONCE))
        val fromUser = VehicleHistory(owners = Fact(3, FactSource.VOUS))
        val merged = fromText.merge(fromUser)
        assertEquals(3, merged.owners?.value)
        assertEquals(AccidentStatus.NONE, merged.accident?.value)
        assertEquals(3, fromUser.merge(fromText).owners?.value)
    }

    @Test
    fun searchResultsCarryHistory() {
        val ad = LeboncoinSource(HttpFetcher()).parse(fixture("leboncoin_search.html")).first()
        assertEquals(1, ad.history.owners?.value)
        assertEquals(true, ad.history.serviceRecords?.value)
    }

    @Test
    fun leboncoinAdPageIsReadInFull() {
        val source = LeboncoinSource(HttpFetcher())
        val fromSearch = source.parse(fixture("leboncoin_search.html")).first()
        val full = source.enrich(fromSearch, fixture("leboncoin_ad.html"))
        assertTrue(full.detailsLoaded)
        assertTrue("Distribution faite" in full.description)
        val h = full.history
        assertEquals(1, h.owners?.value)
        // The site says "Non endommagé": site data outranks the seller's words.
        assertEquals(AccidentStatus.NONE, h.accident?.value)
        assertEquals(FactSource.SITE, h.accident?.source)
        assertEquals(listOf("2024-03" to 61_000, "2026-03" to 84_200), h.readings.map { it.date to it.km })
        assertEquals("https://histovec.interieur.gouv.fr/histovec/report?id=abc123&key=def456", h.reportUrl)
        assertEquals(5, full.imageUrls.size)
    }

    @Test
    fun autoscoutDetailUsesTheMainListingOnly() {
        val source = AutoScout24Source(HttpFetcher())
        val fromSearch = source.parse(fixture("autoscout24_search.html")).first()
        val full = source.enrich(fromSearch, fixture("autoscout24_detail.html"))
        val h = full.history
        assertEquals(2, h.owners?.value) // not the 5 of the recommended car
        assertEquals(AccidentStatus.NONE, h.accident?.value)
        assertEquals(true, h.serviceRecords?.value)
        assertEquals(true, h.nonSmoker?.value)
        assertEquals(false, h.imported?.value)
        assertNotNull(h.warrantyMonths)
        assertFalse('<' in full.description)
    }
}
