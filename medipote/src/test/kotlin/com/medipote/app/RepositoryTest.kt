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
}
