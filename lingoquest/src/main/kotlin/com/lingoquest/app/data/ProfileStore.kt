package com.lingoquest.app.data

import android.util.AtomicFile
import android.util.Log
import com.lingoquest.engine.Profile
import java.io.File
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json

/** Keeps the player's progress (XP, level, records) in one JSON file in private storage. */
class ProfileStore(private val file: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val writer = Executors.newSingleThreadExecutor()

    fun load(): Profile {
        if (!file.exists()) return Profile()
        return try {
            json.decodeFromString(Profile.serializer(), AtomicFile(file).readFully().decodeToString())
        } catch (e: Exception) {
            Log.e("LingoQuest", "Could not read saved progress, starting fresh", e)
            Profile()
        }
    }

    /** Writes off the main thread; the last call always wins. */
    fun save(profile: Profile) {
        val text = json.encodeToString(Profile.serializer(), profile)
        writer.execute {
            val atomic = AtomicFile(file)
            val out = atomic.startWrite()
            try {
                out.write(text.encodeToByteArray())
                atomic.finishWrite(out)
            } catch (e: Exception) {
                atomic.failWrite(out)
                Log.e("LingoQuest", "Could not save progress", e)
            }
        }
    }
}
