package com.medipote.app

import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.Profile
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {

    private val dir: File = Files.createTempDirectory("repo").toFile()

    @Test
    fun savedDataIsReadBack() {
        val file = File(dir, "data.json")
        AppRepository(file).update { AppState(profile = Profile("Sam"), onboarded = true) }
        Thread.sleep(300) // writes happen off the main thread
        val reloaded = AppRepository(file).state.value
        assertEquals("Sam", reloaded.profile?.name)
        assertTrue(reloaded.onboarded)
    }

    @Test
    fun unreadableDataIsKeptAside() {
        val file = File(dir, "data.json").apply { writeText("{ not json") }
        val state = AppRepository(file).state.value
        assertEquals(AppState(), state)
        val aside = dir.listFiles()!!.single { it.name.startsWith("data.json.unreadable-") }
        assertEquals("{ not json", aside.readText())
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() {
        val file = File(dir, "data.json").apply { writeText("""{"onboarded":true,"someFutureField":42}""") }
        assertTrue(AppRepository(file).state.value.onboarded)
    }

    @Test
    fun asNeededDataSurvivesAReload() {
        val file = File(dir, "data.json")
        val at = java.time.LocalDateTime.of(2026, 11, 12, 21, 5)
        val t = com.medipote.app.data.Treatment(
            "ibu", name = "Ibuprofène", doses = emptyList(), startDate = at.toLocalDate(),
            asNeeded = true, prnQuantity = 2.0, maxPerDay = 3, minIntervalHours = 6, lowStockUnits = 5,
        )
        val intake = com.medipote.app.data.PrnIntake(
            "i", "ibu", at, 2.0, "Mal de tête", 6, listOf("Stress", "Écrans"), 4, askRelief = true, notes = "après le travail",
        )
        AppRepository(file).update { AppState(treatments = listOf(t), prnIntakes = listOf(intake), onboarded = true) }
        Thread.sleep(300)
        val reloaded = AppRepository(file).state.value
        assertEquals(listOf(t), reloaded.treatments)
        assertEquals(listOf(intake), reloaded.prnIntakes)
    }

    @Test
    fun dataFromVersion103LoadsWithoutAsNeededFields() {
        val file = File(dir, "data.json").apply {
            writeText("""{"onboarded":true,"treatments":[{"id":"a","name":"Doliprane","doses":[{"minuteOfDay":480}],"startDate":"2026-11-01"}]}""")
        }
        val state = AppRepository(file).state.value
        assertEquals(false, state.treatments.single().asNeeded)
        assertTrue(state.prnIntakes.isEmpty())
    }

    @Test
    fun sleepDataSurvivesAReload() {
        val file = File(dir, "data.json")
        val date = java.time.LocalDate.of(2026, 11, 12)
        val night = com.medipote.app.data.SleepNight(
            date, date.minusDays(1).atTime(23, 20), 25, date.atTime(6, 50),
            listOf(com.medipote.app.data.Awakening(date.atTime(3, 10), 20, "Toilettes")), 4, rested = 3, pleasant = 5,
            tags = listOf("Caféine"), notes = "rêve bizarre",
        )
        val settings = com.medipote.app.data.Settings(sleep = com.medipote.app.data.SleepSettings(7 * 60 + 30, true, 8 * 60))
        AppRepository(file).update { AppState(sleep = listOf(night), settings = settings, onboarded = true) }
        Thread.sleep(300)
        val reloaded = AppRepository(file).state.value
        assertEquals(listOf(night), reloaded.sleep)
        assertEquals(settings.sleep, reloaded.settings.sleep)
    }
}
