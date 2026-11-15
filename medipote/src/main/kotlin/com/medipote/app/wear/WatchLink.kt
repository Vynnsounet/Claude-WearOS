package com.medipote.app.wear

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.edit
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.medipote.app.domain.Evolution
import com.medipote.app.domain.Game
import com.medipote.app.notifications.Notifications
import com.medipote.app.notifications.ReminderScheduler
import com.medipote.app.repository
import com.medipote.app.widget.Widgets
import com.medipote.app.widget.renderPet
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchSync
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The link with the Wear OS companion, through Google's phone-watch data layer (Bluetooth;
 * the app still has no Internet permission). Everything here is best effort: without a watch,
 * or without Google Play services, the calls simply fail and the app carries on.
 */
object WatchLink {
    private const val PREFS = "watch"
    private const val KEY_HELLO = "hello"
    private const val KEY_HANDLES = "handles_reminders"
    private const val KEY_SEEN = "seen_actions"
    private const val HELLO_VALID_MS = 3 * 24 * 3600_000L
    private const val PET_PX = 240

    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var lastSent: String? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** When the watch app last said hello (0 = never: nothing is sent to a watch that isn't there). */
    fun lastSeen(context: Context): Long = prefs(context).getLong(KEY_HELLO, 0L)

    /**
     * True when the watch app rings the reminders itself: the phone's reminders then stay on the
     * phone, so the watch doesn't buzz twice. Forgotten after a few days without news of the watch.
     */
    fun watchHandlesReminders(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_HANDLES, false) && nowMs - p.getLong(KEY_HELLO, 0L) < HELLO_VALID_MS
    }

    fun onHello(context: Context, handlesReminders: Boolean, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit { putLong(KEY_HELLO, nowMs).putBoolean(KEY_HANDLES, handlesReminders) }
    }

    fun forget(context: Context) {
        prefs(context).edit { clear() }
        lastSent = null
    }

    /** Sends the current state to the watch, when one has been seen and something changed. */
    fun publish(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (lastSeen(app) == 0L && !force) return
        io.execute {
            runCatching {
                val repo = app.repository
                val state = repo.state.value
                if (!state.onboarded) return@runCatching
                val now = repo.now()
                val snapshot = WatchBridge.snapshot(state, now)
                val condition = Game.petCondition(state, now)
                val fingerprint = WatchSync.encode(snapshot.copy(generatedAt = "")) + condition.mood + state.game.equipped
                if (!force && fingerprint == lastSent) return@runCatching
                val pet = renderPet(condition.mood, state.game.equipped, PET_PX, PET_PX, Evolution.growth(state).stage)
                val request = PutDataMapRequest.create(WatchSync.PATH_SNAPSHOT).apply {
                    dataMap.putString(WatchSync.KEY_JSON, WatchSync.encode(snapshot))
                    dataMap.putAsset(WatchSync.KEY_PET, Asset.createFromBytes(png(pet)))
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(app).putDataItem(request), 10, TimeUnit.SECONDS)
                lastSent = fingerprint
            }
        }
    }

    private fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    /** Records one action from the watch; false when it was already recorded. */
    fun record(context: Context, action: WatchAction): Boolean {
        val app = context.applicationContext
        val p = prefs(app)
        val seen = p.getStringSet(KEY_SEEN, emptySet())!!
        if (action.id in seen) return false
        val repo = app.repository
        val now = repo.now()
        repo.update { WatchBridge.apply(it, action, now) }
        // Keep the last few hundred ids: enough to ignore an action delivered twice.
        p.edit { putStringSet(KEY_SEEN, (seen.toList().takeLast(300) + action.id).toSet()) }
        val state = repo.state.value
        // What was done on the watch no longer needs a reminder on the phone.
        runCatching {
            when (action.type) {
                WatchAction.TAKE -> state.doseLogs.filter { it.key in action.doseKeys }.forEach { Notifications.cancel(app, it.scheduled) }
                WatchAction.WATER -> Notifications.cancelWater(app)
                WatchAction.PRODUCT_CHANGED -> Notifications.cancelProduct(app)
            }
        }
        ReminderScheduler.reschedule(app, state, now)
        repo.flush()
        runCatching { Widgets.requestUpdate(app) }
        return true
    }

    /** Actions the watch left while the phone wasn't listening. */
    fun drain(context: Context) {
        val app = context.applicationContext
        if (lastSeen(app) == 0L) return
        io.execute {
            runCatching {
                val client = Wearable.getDataClient(app)
                val items = Tasks.await(client.getDataItems(Uri.parse("wear://*${WatchSync.PATH_ACTIONS}"), com.google.android.gms.wearable.DataClient.FILTER_PREFIX), 10, TimeUnit.SECONDS)
                try {
                    items.forEach { item -> handle(app, item) }
                } finally {
                    items.release()
                }
                publish(app)
            }
        }
    }

    internal fun handle(context: Context, item: DataItem) {
        if (!item.uri.path.orEmpty().startsWith(WatchSync.PATH_ACTIONS)) return
        val action = DataMapItem.fromDataItem(item).dataMap.getString(WatchSync.KEY_JSON)?.let(WatchSync::decodeAction)
        if (action != null) record(context, action)
        // Recorded (or unreadable): either way it has no reason to stay.
        runCatching { Wearable.getDataClient(context).deleteDataItems(item.uri) }
    }
}

/** Woken by Google Play services when the watch has something to say. */
class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        var changed = false
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            runCatching { WatchLink.handle(applicationContext, event.dataItem) }
            changed = true
        }
        if (changed) WatchLink.publish(applicationContext)
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchSync.PATH_HELLO) return
        WatchLink.onHello(applicationContext, handlesReminders = event.data.firstOrNull() == '1'.code.toByte())
        // The watch just opened or restarted: give it a fresh state.
        WatchLink.publish(applicationContext, force = true)
    }
}
