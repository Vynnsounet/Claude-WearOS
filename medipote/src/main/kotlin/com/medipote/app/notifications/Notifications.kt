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
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medipote.app.MainActivity
import com.medipote.app.R
import com.medipote.app.data.AppState
import com.medipote.app.domain.Schedule
import com.medipote.app.repository
import com.medipote.app.ui.components.hourMinute
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AlarmKind { DOSE, FOLLOW_UP, APPOINTMENT, WATER, STOCK }

/** One planned alarm. [ref] identifies what it is about (an appointment id, for instance). */
data class AlarmSpec(val kind: AlarmKind, val time: LocalDateTime, val ref: String = "") {
    val key: String get() = "$kind|$time|$ref"

    companion object {
        fun parse(key: String): AlarmSpec? = runCatching {
            val (kind, time, ref) = key.split('|', limit = 3)
            AlarmSpec(AlarmKind.valueOf(kind), LocalDateTime.parse(time), ref)
        }.getOrNull()
    }
}

/** Decides which alarms should exist; pure so it can be unit-tested. */
object ReminderPlanner {
    const val FOLLOW_UP_MINUTES = 30L
    private const val WINDOW_HOURS = 48L
    private val STOCK_CHECK: LocalTime = LocalTime.of(10, 0)

    fun plan(state: AppState, now: LocalDateTime): List<AlarmSpec> {
        if (!state.onboarded || !state.settings.notificationsEnabled) return emptyList()
        val alarms = mutableListOf<AlarmSpec>()

        // Doses (pill included), grouped by time, plus a follow-up 30 min later.
        val times = Schedule.upcoming(state.treatments, now.minusMinutes(FOLLOW_UP_MINUTES), WINDOW_HOURS)
            .map { it.scheduled }.distinct()
        for (time in times) {
            if (time.isAfter(now)) alarms += AlarmSpec(AlarmKind.DOSE, time)
            val follow = time.plusMinutes(FOLLOW_UP_MINUTES)
            if (state.settings.followUpReminder && follow.isAfter(now)) alarms += AlarmSpec(AlarmKind.FOLLOW_UP, time)
        }

        // Appointments: every reminder still ahead.
        for (a in state.appointments) {
            for (minutes in a.reminderMinutes) {
                val t = a.dateTime.minusMinutes(minutes.toLong())
                if (t.isAfter(now)) alarms += AlarmSpec(AlarmKind.APPOINTMENT, t, "${a.id}:$minutes")
            }
        }

        // Water, over the next two days.
        val water = state.settings.water
        if (water.enabled) {
            for (d in 0..1) {
                val date = now.toLocalDate().plusDays(d.toLong())
                water.reminderMinutes.map { date.atTime(it / 60, it % 60) }
                    .filter { it.isAfter(now) }
                    .forEach { alarms += AlarmSpec(AlarmKind.WATER, it) }
            }
        }

        // Low stock: a daily reminder at 10:00 until a box is bought.
        if (state.treatments.any { it.isLowStock }) {
            val today = now.toLocalDate().atTime(STOCK_CHECK)
            alarms += AlarmSpec(AlarmKind.STOCK, if (today.isAfter(now)) today else today.plusDays(1))
        }
        return alarms
    }
}

object Notifications {
    const val CHANNEL_DOSES = "dose_reminders"
    const val CHANNEL_APPOINTMENTS = "appointments"
    const val CHANNEL_WATER = "water"
    const val CHANNEL_STOCK = "stock"
    private const val WATER_ID = 2001
    private const val STOCK_ID = 2002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CHANNEL_DOSES, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_reminders_desc) },
            NotificationChannel(CHANNEL_APPOINTMENTS, "Rendez-vous médicaux", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_WATER, "Hydratation", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_STOCK, "Stock de médicaments", NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach(manager::createNotificationChannel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notificationId(time: LocalDateTime): Int = (time.atZone(ZoneId.systemDefault()).toEpochSecond() / 60).toInt()

    private fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2E9E83.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(if (channel == CHANNEL_APPOINTMENTS) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked in the meantime.
        }
    }

    fun showDoses(context: Context, state: AppState, time: LocalDateTime, followUp: Boolean) {
        val taken = state.doseLogs.mapTo(HashSet()) { it.key }
        val doses = Schedule.occurrencesOn(state.treatments, time.toLocalDate())
            .filter { it.scheduled == time && it.key !in taken }
        if (doses.isEmpty()) return
        val pet = state.game.petName
        val list = doses.joinToString(", ") { "${it.treatment.displayName} (${it.treatment.unit.format(it.dose.quantity)})" }
        val title = if (followUp) "$pet ne se sent pas très bien…" else "C'est l'heure des médicaments 💊"
        val text = if (followUp) "Tu n'as pas encore donné : $list. Mieux vaut tard que jamais !" else "$pet attend : $list"
        post(context, CHANNEL_DOSES, notificationId(time), title, text)
    }

    fun showAppointment(context: Context, state: AppState, ref: String) {
        val id = ref.substringBefore(':')
        val a = state.appointments.firstOrNull { it.id == id } ?: return
        val day = when (a.dateTime.toLocalDate()) {
            LocalDateTime.now().toLocalDate() -> "aujourd'hui"
            LocalDateTime.now().toLocalDate().plusDays(1) -> "demain"
            else -> a.dateTime.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE))
        }
        val details = listOf(a.doctor, a.location).filter { it.isNotBlank() }.joinToString(" · ")
        post(
            context, CHANNEL_APPOINTMENTS, id.hashCode(),
            "Rendez-vous : ${a.title}",
            "$day à ${a.dateTime.format(hourMinute)}" + if (details.isNotEmpty()) " · $details" else "",
        )
    }

    fun showWater(context: Context, state: AppState, now: LocalDateTime) {
        val water = state.settings.water
        val glasses = state.entryFor(now.toLocalDate()).waterGlasses
        if (!water.enabled || glasses >= water.goalGlasses) return
        post(context, CHANNEL_WATER, WATER_ID, "Un verre d'eau ? 💧", "${state.game.petName} a soif ! Tu en es à $glasses / ${water.goalGlasses} verres aujourd'hui.")
    }

    fun showStock(context: Context, state: AppState) {
        val low = state.treatments.filter { it.isLowStock }
        if (low.isEmpty()) return
        val list = low.joinToString(", ") { "${it.displayName} (${it.daysLeft} j)" }
        post(context, CHANNEL_STOCK, STOCK_ID, "Pense à racheter tes médicaments", "Stock bas : $list.")
    }

    fun cancel(context: Context, time: LocalDateTime) {
        NotificationManagerCompat.from(context).cancel(notificationId(time))
    }

    fun cancelWater(context: Context) = NotificationManagerCompat.from(context).cancel(WATER_ID)
}

/** Turns the plan into AlarmManager alarms, replacing the previous set. */
object ReminderScheduler {
    private const val PREFS = "alarms"
    private const val KEY_SET = "keys"
    const val EXTRA_KEY = "key"

    fun reschedule(context: Context, state: AppState, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_SET, emptySet())!!.forEach { key ->
            pendingIntent(context, key, create = false)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
        val plan = ReminderPlanner.plan(state, now)
        for (spec in plan) set(alarms, spec.time, pendingIntent(context, spec.key, create = true)!!)
        prefs.edit().putStringSet(KEY_SET, plan.mapTo(HashSet()) { it.key }).apply()
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

    /** Each alarm gets its own data URI, so pending intents never collide. */
    private fun pendingIntent(context: Context, key: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.medipote.app.REMINDER")
            .setData(Uri.parse("medipote://alarm/" + Uri.encode(key)))
            .putExtra(EXTRA_KEY, key)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val spec = intent.getStringExtra(ReminderScheduler.EXTRA_KEY)?.let(AlarmSpec::parse) ?: return
        val repo = context.repository
        val state = repo.state.value
        if (state.settings.notificationsEnabled) {
            when (spec.kind) {
                AlarmKind.DOSE -> Notifications.showDoses(context, state, spec.time, followUp = false)
                AlarmKind.FOLLOW_UP -> Notifications.showDoses(context, state, spec.time, followUp = true)
                AlarmKind.APPOINTMENT -> Notifications.showAppointment(context, state, spec.ref)
                AlarmKind.WATER -> Notifications.showWater(context, state, repo.now())
                AlarmKind.STOCK -> Notifications.showStock(context, state)
            }
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
