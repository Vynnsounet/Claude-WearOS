package com.radaroccaz.app.data

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Single source of truth for saved data (settings, favorites, alerts, recent searches).
 * The state lives in memory and is written to one private JSON file after each change,
 * off the main thread.
 */
class AppRepository(private val file: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val writer = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (AppState) -> AppState): AppState {
        val updated = transform(_state.value)
        if (updated == _state.value) return updated
        _state.value = updated
        writer.execute { save(updated) }
        return updated
    }

    fun updateAlert(id: String, transform: (Alert) -> Alert) = update { s ->
        s.copy(alerts = s.alerts.map { if (it.id == id) transform(it) else it })
    }

    private fun load(): AppState {
        if (!file.exists()) return AppState()
        return try {
            json.decodeFromString(AppState.serializer(), AtomicFile(file).readFully().decodeToString())
        } catch (e: Exception) {
            Log.e(TAG, "Could not read saved data, starting fresh", e)
            AppState()
        }
    }

    private fun save(state: AppState) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(json.encodeToString(AppState.serializer(), state).encodeToByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            Log.e(TAG, "Could not save data", e)
        }
    }

    companion object {
        private const val TAG = "RadarOccaz"
    }
}
