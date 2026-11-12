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
}
