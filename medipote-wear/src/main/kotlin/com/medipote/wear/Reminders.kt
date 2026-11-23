package com.medipote.wear

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchPlanner
import com.medipote.sync.WatchReminder
import java.time.LocalDateTime
import java.time.ZoneId

/** The watch rings its reminders by itself, from the phone's last snapshot: it works out of range too. */
object Reminders {
    const val CHANNEL = "reminders"
    private const val PREFS = "alarms"
    private const val KEY_SET = "keys"
    const val EXTRA_KEY = "key"
    const val EXTRA_ID = "id"
    const val EXTRA_REF = "ref"
    const val ACTION_TAKE = "com.medipote.wear.TAKE"
    const val ACTION_SNOOZE = "com.medipote.wear.SNOOZE"
    const val ACTION_WATER = "com.medipote.wear.WATER"
    const val ACTION_PRODUCT = "com.medipote.wear.PRODUCT"
    const val WATER_ID = 2001
    const val PRODUCT_ID = 2010
    const val SNOOZE_MINUTES = 10L
    private const val MAX_ALARMS = 60
    private const val FALLBACK_WINDOW_MS = 5 * 60_000L
    private val VIBRATION = longArrayOf(0, 400, 200, 400, 200, 400)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Rappels", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Prises de médicaments, eau, protection"
            enableVibration(true)
            vibrationPattern = VIBRATION
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Whether reminders can show on this watch: the phone is told, and keeps its own copies off the watch. */
    fun canNotify(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        val channelOn = manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        return permission && manager.areNotificationsEnabled() && channelOn
    }

    fun notificationId(reminder: WatchReminder): Int = when (reminder.kind) {
        WatchReminder.WATER -> WATER_ID
        WatchReminder.PRODUCT -> PRODUCT_ID
        // A dose reminder and its follow-up share an id: the second replaces the first.
        else -> reminder.ref.hashCode()
    }

    /** Replaces the alarms with the ones the current snapshot calls for. */
    fun schedule(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_SET, emptySet())!!.forEach { key ->
            pendingIntent(context, key, create = false)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
        val snapshot = context.store.snapshot
        val plan = snapshot?.let { WatchPlanner.pending(it, now) }.orEmpty().take(MAX_ALARMS)
        for (reminder in plan) set(alarms, reminder.at, pendingIntent(context, reminder.key, create = true)!!)
        prefs.edit { putStringSet(KEY_SET, plan.mapTo(HashSet()) { it.key }) }
        // What no longer needs doing shouldn't stay on the wrist.
        if (snapshot != null) {
            val manager = NotificationManagerCompat.from(context)
            snapshot.reminders.filter { it.kind == WatchReminder.DOSE && !it.at.isAfter(now) && WatchPlanner.dosesOf(snapshot, it).isEmpty() }
                .forEach { manager.cancel(notificationId(it)) }
            if (WatchPlanner.glasses(snapshot, now.toLocalDate()) >= snapshot.goal) manager.cancel(WATER_ID)
            if (snapshot.product == null) manager.cancel(PRODUCT_ID)
        }
    }

    /** "+10 min" on a dose reminder: one more ring, on the watch only. */
    fun snooze(context: Context, ref: String, now: LocalDateTime = LocalDateTime.now()) {
        val reminder = WatchReminder(WatchReminder.FOLLOW_UP, now.plusMinutes(SNOOZE_MINUTES).withSecond(0).withNano(0).toString(), ref)
        set(context.getSystemService(AlarmManager::class.java), reminder.at, pendingIntent(context, reminder.key, create = true)!!)
    }

    private fun set(alarms: AlarmManager, time: LocalDateTime, intent: PendingIntent) {
        val millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, FALLBACK_WINDOW_MS, intent)
        }
    }

    private fun pendingIntent(context: Context, key: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.medipote.wear.REMINDER")
            .setData(Uri.parse("medipote://alarm/" + Uri.encode(key)))
            .putExtra(EXTRA_KEY, key)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun action(context: Context, action: String, id: Int, ref: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, id * 4 + code,
            Intent(context, ActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id).putExtra(EXTRA_REF, ref),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Shows a reminder that is still useful; returns false when there was nothing left to say. */
    fun show(context: Context, reminder: WatchReminder, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val snapshot = context.store.snapshot ?: return false
        if (!WatchPlanner.relevant(snapshot, reminder, now) || !canNotify(context)) return false
        val (title, text) = WatchPlanner.message(snapshot, reminder, now)
        val id = notificationId(reminder)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(VIBRATION)
            .setContentIntent(open)
            .setAutoCancel(true)
        when (reminder.kind) {
            WatchReminder.DOSE, WatchReminder.FOLLOW_UP, WatchReminder.WINDOW_END -> {
                builder.addAction(R.drawable.ic_notification, "Pris ✓", action(context, ACTION_TAKE, id, reminder.ref, 1))
                builder.addAction(R.drawable.ic_notification, "+${SNOOZE_MINUTES} min", action(context, ACTION_SNOOZE, id, reminder.ref, 2))
            }
            WatchReminder.WATER -> builder.addAction(R.drawable.ic_notification, "J'ai bu 💧", action(context, ACTION_WATER, id, "", 1))
            WatchReminder.PRODUCT -> builder.addAction(R.drawable.ic_notification, "J'ai changé ✓", action(context, ACTION_PRODUCT, id, "", 1))
        }
        return try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** A short tick on the wrist: "noted". */
    fun tick(context: Context) {
        runCatching { context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = intent.getStringExtra(Reminders.EXTRA_KEY)?.split('|', limit = 3) ?: return
        if (parts.size < 3) return
        val key = intent.getStringExtra(Reminders.EXTRA_KEY)!!
        val snapshot = context.store.snapshot ?: return
        // The snapshot's own entry carries the title and text (period product); else rebuild it.
        val reminder = snapshot.reminders.firstOrNull { it.key == key } ?: WatchReminder(parts[0], parts[1], parts[2])
        Reminders.show(context, reminder)
        // Keep the phone informed that the watch still rings by itself.
        WearLink.hello(context, force = false)
    }
}

/** Buttons of a reminder: done without opening the app. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = LocalDateTime.now()
        val id = intent.getIntExtra(Reminders.EXTRA_ID, 0)
        val ref = intent.getStringExtra(Reminders.EXTRA_REF).orEmpty()
        val snapshot = context.store.snapshot
        when (intent.action) {
            Reminders.ACTION_TAKE -> {
                val keys = snapshot?.doses.orEmpty().filter { it.time == ref && !it.taken }.map { it.key }
                if (keys.isNotEmpty()) WearLink.send(context, WearLink.action(WatchAction.TAKE, now, doseKeys = keys))
            }
            Reminders.ACTION_SNOOZE -> Reminders.snooze(context, ref, now)
            Reminders.ACTION_WATER -> WearLink.send(context, WearLink.action(WatchAction.WATER, now, value = 1.0))
            Reminders.ACTION_PRODUCT -> WearLink.send(context, WearLink.action(WatchAction.PRODUCT_CHANGED, now))
        }
        NotificationManagerCompat.from(context).cancel(id)
        Reminders.tick(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    private val expected = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in expected) return
        Reminders.schedule(context)
        WearLink.hello(context)
    }
}
