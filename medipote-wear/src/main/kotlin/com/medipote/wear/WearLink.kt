package com.medipote.wear

import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchSync
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The link with the phone app. Everything is best effort: the data layer delivers when the phone is back. */
object WearLink {
    private const val PREFS = "link"
    private const val KEY_HELLO = "hello"
    private const val HELLO_EVERY_MS = 6 * 3600_000L
    private val io = Executors.newSingleThreadExecutor()

    fun action(type: String, now: LocalDateTime, doseKeys: List<String> = emptyList(), treatmentId: String = "", value: Double = 0.0) =
        WatchAction(UUID.randomUUID().toString(), type, now.withNano(0).toString(), doseKeys, treatmentId, value)

    /** Shows the action on the watch at once and hands it to the data layer for the phone. */
    fun send(context: Context, action: WatchAction) {
        val app = context.applicationContext
        app.store.add(action)
        Reminders.schedule(app)
        io.execute {
            runCatching {
                val request = PutDataMapRequest.create("${WatchSync.PATH_ACTIONS}/${action.id}").apply {
                    dataMap.putString(WatchSync.KEY_JSON, WatchSync.encode(action))
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(app).putDataItem(request), 10, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * Tells the phone the watch app is there, and whether it can show reminders itself (the phone
     * then keeps its own reminders off the watch). The phone answers with a fresh snapshot.
     */
    fun hello(context: Context, force: Boolean = true) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_HELLO, 0L) < HELLO_EVERY_MS) return
        io.execute {
            runCatching {
                val payload = byteArrayOf(if (Reminders.canNotify(app)) '1'.code.toByte() else '0'.code.toByte())
                val nodes = Tasks.await(Wearable.getNodeClient(app).connectedNodes, 10, TimeUnit.SECONDS)
                val messages = Wearable.getMessageClient(app)
                nodes.forEach { node -> runCatching { Tasks.await(messages.sendMessage(node.id, WatchSync.PATH_HELLO, payload), 10, TimeUnit.SECONDS) } }
                if (nodes.isNotEmpty()) prefs.edit { putLong(KEY_HELLO, now) }
            }
        }
    }
}

/** Woken by Google Play services when the phone has something new. */
class WearListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val store = applicationContext.store
        for (event in events) {
            val path = event.dataItem.uri.path.orEmpty()
            when {
                path == WatchSync.PATH_SNAPSHOT && event.type == DataEvent.TYPE_CHANGED -> runCatching {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    map.getString(WatchSync.KEY_JSON)?.let(WatchSync::decodeSnapshot)?.let(store::onSnapshot)
                    map.getAsset(WatchSync.KEY_PET)?.let { asset ->
                        val fd = Tasks.await(Wearable.getDataClient(this).getFdForAsset(asset), 10, TimeUnit.SECONDS)
                        fd.inputStream.use { store.onPet(it.readBytes()) }
                    }
                }
                // The phone deletes an action once it is recorded.
                path.startsWith(WatchSync.PATH_ACTIONS) && event.type == DataEvent.TYPE_DELETED ->
                    store.confirm(path.substringAfterLast('/'))
            }
        }
        Reminders.schedule(applicationContext)
        WearLink.hello(applicationContext, force = false)
    }
}
