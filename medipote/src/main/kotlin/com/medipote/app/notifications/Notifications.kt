package com.medipote.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medipote.app.MainActivity
import com.medipote.app.R
import com.medipote.app.data.AppState
import com.medipote.app.domain.DoseOccurrence
import com.medipote.app.domain.Schedule
import com.medipote.app.repository
import java.time.LocalDateTime
import java.time.ZoneId

object Notifications {
    const val CHANNEL_ID = "dose_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notificationId(time: LocalDateTime): Int = (time.atZone(ZoneId.systemDefault()).toEpochSecond() / 60).toInt()

    fun show(context: Context, state: AppState, time: LocalDateTime, doses: List<DoseOccurrence>, followUp: Boolean) {
        if (!canPost(context) || doses.isEmpty()) return
        val pet = state.game.petName
        val list = doses.joinToString(", ") {
            "${it.treatment.displayName} (${it.treatment.unit.format(it.dose.quantity)})"
        }
        val title = if (followUp) "$pet ne se sent pas très bien…" else "C'est l'heure des médicaments 💊"
        val text = if (followUp) "Tu n'as pas encore donné : $list. Mieux vaut tard que jamais !"
        else "$pet attend : $list"
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2E9E83.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(time), notification)
        } catch (_: SecurityException) {
            // Permission revoked in the meantime.
        }
    }

    fun cancel(context: Context, time: LocalDateTime) {
        NotificationManagerCompat.from(context).cancel(notificationId(time))
    }
}

/**
 * Keeps one alarm per dose time for the next 48 hours, plus a follow-up alarm 30 minutes
 * later. Every alarm that fires reschedules the window, so the chain never runs out.
 */
object ReminderScheduler {
    private const val PREFS = "alarms"
    private const val KEY_CODES = "codes"
    private const val FOLLOW_UP_MINUTES = 30L
    const val EXTRA_TIME = "time"
    const val EXTRA_FOLLOW_UP = "follow_up"

    fun reschedule(context: Context, state: AppState, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_CODES, emptySet())!!.forEach { code ->
            val (time, follow) = code.split('|')
            pendingIntent(context, LocalDateTime.parse(time), follow.toBoolean(), create = false)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
        val codes = mutableSetOf<String>()
        if (state.settings.notificationsEnabled && state.onboarded) {
            val times = Schedule.upcoming(state.treatments, now.minusMinutes(FOLLOW_UP_MINUTES), 48)
                .map { it.scheduled }.distinct()
            // Doses already given are filtered out by the receiver when the alarm fires.
            for (time in times) {
                if (time.isAfter(now)) {
                    set(alarms, time, pendingIntent(context, time, false, create = true)!!)
                    codes += "$time|false"
                }
                val follow = time.plusMinutes(FOLLOW_UP_MINUTES)
                if (state.settings.followUpReminder && follow.isAfter(now)) {
                    set(alarms, follow, pendingIntent(context, time, true, create = true)!!)
                    codes += "$time|true"
                }
            }
        }
        prefs.edit().putStringSet(KEY_CODES, codes).apply()
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun set(alarms: AlarmManager, time: LocalDateTime, intent: PendingIntent) {
        val millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    private fun pendingIntent(context: Context, time: LocalDateTime, followUp: Boolean, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.medipote.app.REMINDER")
            .putExtra(EXTRA_TIME, time.toString())
            .putExtra(EXTRA_FOLLOW_UP, followUp)
        val code = Notifications.notificationId(time) * 2 + if (followUp) 1 else 0
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val time = intent.getStringExtra(ReminderScheduler.EXTRA_TIME)?.let(LocalDateTime::parse) ?: return
        val followUp = intent.getBooleanExtra(ReminderScheduler.EXTRA_FOLLOW_UP, false)
        val repo = context.repository
        val state = repo.state.value
        val taken = state.doseLogs.mapTo(HashSet()) { it.key }
        val pending = Schedule.occurrencesOn(state.treatments, time.toLocalDate())
            .filter { it.scheduled == time && it.key !in taken }
        if (state.settings.notificationsEnabled) {
            Notifications.show(context, state, time, pending, followUp)
        }
        ReminderScheduler.reschedule(context, state, repo.now())
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = context.repository
        ReminderScheduler.reschedule(context, repo.state.value, repo.now())
    }
}
