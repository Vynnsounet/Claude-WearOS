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
import androidx.core.content.edit
import androidx.core.net.toUri
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

enum class AlarmKind { DOSE, FOLLOW_UP, APPOINTMENT, WATER, STOCK, TEST, PRN_CHECK, SLEEP, SNOOZE, RENEWAL }

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

        // Reminders put off from their notification, while their doses aren't given.
        val given = state.doseLogs.mapTo(HashSet()) { it.key }
        for (s in state.snoozes) {
            if (!s.until.isAfter(now)) continue
            val pending = Schedule.occurrencesOn(state.treatments, s.doseTime.toLocalDate()).any { it.scheduled == s.doseTime && it.key !in given }
            if (pending) alarms += AlarmSpec(AlarmKind.SNOOZE, s.until, s.doseTime.toString())
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

        // "Did it help?" an hour after an as-needed intake, when the user asked for it.
        for (intake in state.prnIntakes) {
            if (!intake.askRelief || intake.relief != null) continue
            val t = intake.takenAt.plusMinutes(com.medipote.app.domain.Prn.RELIEF_CHECK_MINUTES)
            if (t.isAfter(now)) alarms += AlarmSpec(AlarmKind.PRN_CHECK, t, intake.id)
        }

        // "How did you sleep?" in the morning, while the night isn't logged.
        val sleep = state.settings.sleep
        if (sleep.reminder) {
            for (d in 0..1) {
                val date = now.toLocalDate().plusDays(d.toLong())
                val t = date.atTime(sleep.reminderMinute / 60, sleep.reminderMinute % 60)
                if (t.isAfter(now) && state.sleep.none { it.date == date }) alarms += AlarmSpec(AlarmKind.SLEEP, t)
            }
        }

        // Prescriptions to renew: at 10:00 on the reminder day.
        for (doc in state.documents) {
            val day = com.medipote.app.domain.Renewals.reminderDate(doc) ?: continue
            val t = day.atTime(STOCK_CHECK)
            if (t.isAfter(now)) alarms += AlarmSpec(AlarmKind.RENEWAL, t, doc.id)
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
    // Version 2 channels: high importance (pop-up), sound and vibration. Android never lets an app
    // raise the importance of an existing channel, so the old ones are replaced, not updated.
    const val CHANNEL_DOSES = "doses_v2"
    const val CHANNEL_APPOINTMENTS = "appointments_v2"
    const val CHANNEL_WATER = "water_v2"
    const val CHANNEL_STOCK = "stock_v2"
    const val CHANNEL_SLEEP = "sleep_v2"
    private val OLD_CHANNELS = listOf("dose_reminders", "appointments", "water", "stock", "sleep")
    val ALL_CHANNELS = listOf(CHANNEL_DOSES, CHANNEL_APPOINTMENTS, CHANNEL_WATER, CHANNEL_STOCK, CHANNEL_SLEEP)
    private const val SLEEP_ID = 2004
    private const val WATER_ID = 2001
    private const val STOCK_ID = 2002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        OLD_CHANNELS.forEach(manager::deleteNotificationChannel)
        val sound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val audio = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun channel(id: String, name: String, desc: String) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = desc
                setSound(sound, audio)
                enableVibration(true)
                vibrationPattern = VIBRATION
                enableLights(true)
                lightColor = ACCENT
                setShowBadge(true)
            }
        listOf(
            channel(CHANNEL_DOSES, context.getString(R.string.channel_reminders), context.getString(R.string.channel_reminders_desc)),
            channel(CHANNEL_APPOINTMENTS, "Rendez-vous médicaux", "Rappels avant chaque rendez-vous"),
            channel(CHANNEL_WATER, "Hydratation", "Rappels pour boire de l'eau"),
            channel(CHANNEL_STOCK, "Stock de médicaments", "Médicaments à racheter"),
            channel(CHANNEL_SLEEP, "Sommeil", "Rappel du matin pour noter sa nuit"),
        ).forEach(manager::createNotificationChannel)
    }

    private val VIBRATION = longArrayOf(0, 350, 200, 350)
    private const val ACCENT = 0xFF2E9E83.toInt()

    /**
     * Channels whose notifications no longer pop up on screen (importance lowered by the user
     * or by the phone): their reminders arrive silently in the notification shade.
     */
    fun quietChannels(context: Context): List<String> {
        val manager = NotificationManagerCompat.from(context)
        return ALL_CHANNELS.filter { id ->
            val importance = manager.getNotificationChannel(id)?.importance ?: return@filter false
            importance != NotificationManager.IMPORTANCE_NONE && importance < NotificationManager.IMPORTANCE_HIGH
        }
    }

    /** Opens the settings of one channel (to turn "pop-up" and sound back on). */
    fun channelSettingsIntent(context: Context, channel: String = CHANNEL_DOSES): Intent =
        Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channel)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Permission granted (Android 13+) and notifications not turned off for the app or its reminder channel. */
    fun canPost(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        val channelOn = manager.getNotificationChannel(CHANNEL_DOSES)?.importance != NotificationManager.IMPORTANCE_NONE
        return permission && manager.areNotificationsEnabled() && channelOn
    }

    /** Opens the system screen where the user can turn the app's notifications back on. */
    fun settingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationId(time: LocalDateTime): Int = (time.atZone(ZoneId.systemDefault()).toEpochSecond() / 60).toInt()

    /** Extra telling the app which screen to open when the notification is tapped. */
    const val EXTRA_ROUTE = "route"

    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        route: String? = null,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (route != null) intent.putExtra(EXTRA_ROUTE, route)
        val open = PendingIntent.getActivity(
            context, if (route != null) id else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(if (channel == CHANNEL_APPOINTMENTS) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(VIBRATION)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            // On the lock screen: a reminder without medication names, until the phone is unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ACCENT)
                    .setContentTitle("MédiPote")
                    .setContentText("Tu as un rappel. Déverrouille pour le voir.")
                    .build(),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply { actions.forEach(::addAction) }
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
        val list = doses.joinToString(", ") { "${it.treatment.displayName} (${it.treatment.unit.format(it.quantity)})" }
        val title = if (followUp) "$pet ne se sent pas très bien…" else "C'est l'heure des médicaments 💊"
        val text = if (followUp) "Tu n'as pas encore donné : $list. Mieux vaut tard que jamais !" else "$pet attend : $list"
        val id = notificationId(time)
        post(
            context, CHANNEL_DOSES, id, title, text,
            actions = listOf(
                NotificationActions.action(context, "Pris ✓", NotificationActions.take(context, time, id)),
                NotificationActions.action(context, "+10 min", NotificationActions.snooze(context, time, id, 10)),
                NotificationActions.action(context, "+30 min", NotificationActions.snooze(context, time, id, 30)),
            ),
        )
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
        post(
            context, CHANNEL_WATER, WATER_ID, "Un verre d'eau ? 💧",
            "${state.game.petName} a soif ! Tu en es à $glasses / ${water.goalGlasses} verres aujourd'hui.",
            actions = listOf(NotificationActions.action(context, "J'ai bu un verre", NotificationActions.water(context, WATER_ID))),
        )
    }

    fun showPrnCheck(context: Context, state: AppState, intakeId: String) {
        val intake = state.prnIntakes.firstOrNull { it.id == intakeId } ?: return
        if (intake.relief != null) return
        val name = state.treatments.firstOrNull { it.id == intake.treatmentId }?.name ?: return
        post(
            context, CHANNEL_DOSES, intakeId.hashCode(),
            "Est-ce que $name a fait effet ?",
            "Note son efficacité en un geste : tes statistiques t'aideront à savoir ce qui marche pour toi.",
            route = "prn_log/${intake.treatmentId}?intake=${intake.id}",
        )
    }

    fun showStock(context: Context, state: AppState) {
        val low = state.treatments.filter { it.isLowStock }
        if (low.isEmpty()) return
        val list = low.joinToString(", ") { t ->
            val left = t.daysLeft?.let { "$it j" } ?: t.stock?.let { t.unit.format(it) }
            if (left == null) t.displayName else "${t.displayName} ($left)"
        }
        post(context, CHANNEL_STOCK, STOCK_ID, "Pense à racheter tes médicaments", "Stock bas : $list.")
    }

    fun showRenewal(context: Context, state: AppState, id: String, now: LocalDateTime) {
        val doc = state.documents.firstOrNull { it.id == id } ?: return
        val until = doc.validUntil ?: return
        post(
            context, CHANNEL_APPOINTMENTS, "renewal:$id".hashCode(),
            "Ordonnance à renouveler 📄",
            "« ${doc.title} » n'est plus valable après le ${until.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE))}. " +
                "Pense à prendre rendez-vous avec ton médecin.",
            route = "document/$id",
        )
    }

    fun showSleep(context: Context, state: AppState, now: LocalDateTime) {
        if (!state.settings.sleep.reminder || state.sleep.any { it.date == now.toLocalDate() }) return
        post(
            context, CHANNEL_SLEEP, SLEEP_ID, "Bien dormi ? 🌙",
            "Note ta nuit et tes réveils en quelques secondes : +${com.medipote.app.domain.Rules.SLEEP_BONUS} pts pour ${state.game.petName} et toi.",
            route = "sleep_log",
        )
    }

    /** A short confirmation after a notification button, without sound; it goes away by itself. */
    fun confirm(context: Context, id: Int, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_DOSES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(text)
            .setSilent(true)
            .setTimeoutAfter(5_000)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    /** Posted from the settings so the user can check notifications reach them. */
    fun showTest(context: Context, state: AppState) {
        post(context, CHANNEL_DOSES, 2003, "Test de notification ✅", "${state.game.petName} te dit coucou ! Les rappels de MédiPote arrivent bien.")
    }

    fun cancel(context: Context, time: LocalDateTime) {
        NotificationManagerCompat.from(context).cancel(notificationId(time))
    }

    fun cancelWater(context: Context) = NotificationManagerCompat.from(context).cancel(WATER_ID)
    fun cancelSleep(context: Context) = NotificationManagerCompat.from(context).cancel(SLEEP_ID)
}

/** Turns the plan into AlarmManager alarms, replacing the previous set. */
object ReminderScheduler {
    private const val PREFS = "alarms"
    private const val KEY_SET = "keys"
    const val EXTRA_KEY = "key"
    private const val FALLBACK_WINDOW_MS = 10 * 60_000L

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
        prefs.edit { putStringSet(KEY_SET, plan.mapTo(HashSet()) { it.key }) }
    }

    /** Fires a test notification in one minute, through the same alarm path as real reminders. */
    fun scheduleTest(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val spec = AlarmSpec(AlarmKind.TEST, now.plusMinutes(1).withSecond(0).withNano(0))
        set(context.getSystemService(AlarmManager::class.java), spec.time, pendingIntent(context, spec.key, create = true)!!)
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun set(alarms: AlarmManager, time: LocalDateTime, intent: PendingIntent) {
        val millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            // Never use set()/setAndAllowWhileIdle() here: their default window is 75 % of the
            // time left, so a reminder planned 10 h ahead could arrive 7 h late.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, FALLBACK_WINDOW_MS, intent)
        }
    }

    /** Each alarm gets its own data URI, so pending intents never collide. */
    private fun pendingIntent(context: Context, key: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.medipote.app.REMINDER")
            .setData(("medipote://alarm/" + Uri.encode(key)).toUri())
            .putExtra(EXTRA_KEY, key)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val spec = intent.getStringExtra(ReminderScheduler.EXTRA_KEY)?.let(AlarmSpec::parse) ?: return
        if (spec.kind == AlarmKind.TEST) {
            Notifications.showTest(context, context.repository.state.value)
            return
        }
        val repo = context.repository
        val state = repo.state.value
        if (state.settings.notificationsEnabled) {
            when (spec.kind) {
                AlarmKind.DOSE -> Notifications.showDoses(context, state, spec.time, followUp = false)
                AlarmKind.FOLLOW_UP -> Notifications.showDoses(context, state, spec.time, followUp = true)
                AlarmKind.APPOINTMENT -> Notifications.showAppointment(context, state, spec.ref)
                AlarmKind.WATER -> Notifications.showWater(context, state, repo.now())
                AlarmKind.STOCK -> Notifications.showStock(context, state)
                AlarmKind.TEST -> Unit
                AlarmKind.PRN_CHECK -> Notifications.showPrnCheck(context, state, spec.ref)
                AlarmKind.SLEEP -> Notifications.showSleep(context, state, repo.now())
                AlarmKind.RENEWAL -> Notifications.showRenewal(context, state, spec.ref, repo.now())
                AlarmKind.SNOOZE -> {
                    val time = LocalDateTime.parse(spec.ref)
                    repo.update { s -> s.copy(snoozes = s.snoozes.filterNot { it.doseTime == time && !it.until.isAfter(spec.time) }) }
                    Notifications.showDoses(context, repo.state.value, time, followUp = true)
                }
            }
        }
        ReminderScheduler.reschedule(context, state, repo.now())
        // Dose states changed with the time: refresh the home-screen widgets.
        com.medipote.app.widget.Widgets.requestUpdate(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    private val expected = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED", // API 31+
    )

    override fun onReceive(context: Context, intent: Intent) {
        // Exported for system broadcasts only: ignore anything else.
        if (intent.action !in expected) return
        val repo = context.repository
        ReminderScheduler.reschedule(context, repo.state.value, repo.now())
    }
}
