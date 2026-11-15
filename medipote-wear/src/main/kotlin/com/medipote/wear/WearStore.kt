package com.medipote.wear

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchPlanner
import com.medipote.sync.WatchSnapshot
import com.medipote.sync.WatchSync
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the watch shows: the phone's last snapshot, plus what was done here and not yet confirmed. */
data class WearState(
    /** As sent by the phone; null until the first sync. */
    val base: WatchSnapshot? = null,
    /** Done on the watch, waiting for the phone to record them. */
    val pending: List<WatchAction> = emptyList(),
    /** Changes whenever a new picture of the companion arrives. */
    val petVersion: Int = 0,
) {
    /** The snapshot with the pending actions already visible. */
    val snapshot: WatchSnapshot? get() = base?.let { pending.fold(it, WatchPlanner::apply) }
}

/** Keeps the watch's copy of the data on disk: reminders must survive a restart, far from the phone. */
class WearStore(dir: File) {
    private val snapshotFile = File(dir, "snapshot.json")
    private val pendingFile = File(dir, "pending.txt")
    private val petFile = File(dir, "pet.png")

    private val _state = MutableStateFlow(load())
    val state: StateFlow<WearState> = _state.asStateFlow()

    val snapshot: WatchSnapshot? get() = _state.value.snapshot

    private fun load(): WearState {
        val base = runCatching { snapshotFile.readText() }.getOrNull()?.let(WatchSync::decodeSnapshot)
        val pending = runCatching { pendingFile.readLines() }.getOrDefault(emptyList()).mapNotNull(WatchSync::decodeAction)
        return WearState(base, pending)
    }

    @Synchronized
    fun onSnapshot(snapshot: WatchSnapshot) {
        // An older snapshot arriving late must not undo a newer one.
        val current = _state.value.base
        if (current != null && snapshot.generated.isBefore(current.generated)) return
        runCatching { snapshotFile.writeText(WatchSync.encode(snapshot)) }
        _state.value = _state.value.copy(base = snapshot)
    }

    @Synchronized
    fun onPet(png: ByteArray) {
        runCatching { petFile.writeBytes(png) }
        _state.value = _state.value.copy(petVersion = _state.value.petVersion + 1)
    }

    fun pet(): Bitmap? = runCatching { BitmapFactory.decodeFile(petFile.path) }.getOrNull()

    /** Something done on the watch: visible at once, kept until the phone has recorded it. */
    @Synchronized
    fun add(action: WatchAction) {
        savePending(_state.value.pending + action)
    }

    /** The phone recorded (and deleted) this action. */
    @Synchronized
    fun confirm(id: String) {
        if (_state.value.pending.none { it.id == id }) return
        savePending(_state.value.pending.filterNot { it.id == id })
    }

    private fun savePending(list: List<WatchAction>) {
        runCatching { pendingFile.writeText(list.joinToString("\n") { WatchSync.encode(it) }) }
        _state.value = _state.value.copy(pending = list)
    }
}
