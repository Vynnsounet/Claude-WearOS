package com.medipote.app.data

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Single source of truth. The whole state lives in memory and is written to one
 * JSON file in the app's private storage after every change (off the main thread).
 */
class AppRepository(private val file: File, clock: Clock = Clock.systemDefaultZone()) {

    /** Replaceable in tests to freeze time. */
    @androidx.annotation.VisibleForTesting
    var clock: Clock = clock


    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val writer = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun now(): LocalDateTime = LocalDateTime.now(clock)

    @Synchronized
    fun update(transform: (AppState) -> AppState): AppState {
        val updated = transform(_state.value)
        if (updated == _state.value) return updated
        _state.value = updated
        val snapshot = prune(updated)
        writer.execute { save(snapshot) }
        return updated
    }

    @Synchronized
    fun reset() {
        _state.value = AppState()
        writer.execute { file.delete() }
    }

    /** Keeps the file small: dose history older than ~13 months is dropped. */
    private fun prune(state: AppState): AppState {
        val cutoff = now().minusDays(400)
        return if (state.doseLogs.none { it.scheduled.isBefore(cutoff) }) state
        else state.copy(doseLogs = state.doseLogs.filter { !it.scheduled.isBefore(cutoff) })
    }

    private fun load(): AppState {
        if (!file.exists()) return AppState()
        return try {
            json.decodeFromString(AppState.serializer(), AtomicFile(file).readFully().decodeToString())
        } catch (e: Exception) {
            Log.e("MediPote", "Could not read saved data, starting fresh", e)
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
            Log.e("MediPote", "Could not save data", e)
        }
    }
}
