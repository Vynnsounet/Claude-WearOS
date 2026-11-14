package com.medipote.app.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.medipote.app.R
import com.medipote.app.data.Snooze
import com.medipote.app.domain.Game
import com.medipote.app.repository
import com.medipote.app.widget.Widgets
import java.time.LocalDateTime

/** Buttons on the reminders: give the doses, remind again later, log a glass of water. */
object NotificationActions {
    const val ACTION_TAKE = "com.medipote.app.action.TAKE"
    const val ACTION_SNOOZE = "com.medipote.app.action.SNOOZE"
    const val ACTION_WATER = "com.medipote.app.action.WATER"
    const val EXTRA_TIME = "time"
    const val EXTRA_ID = "notification_id"
    const val EXTRA_MINUTES = "minutes"

    private fun intent(context: Context, action: String, id: Int) =
        Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)

    private fun pending(context: Context, intent: Intent, request: Int): PendingIntent =
        PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun take(context: Context, time: LocalDateTime, id: Int) =
        pending(context, intent(context, ACTION_TAKE, id).putExtra(EXTRA_TIME, time.toString()), id * 4)

    fun snooze(context: Context, time: LocalDateTime, id: Int, minutes: Int) =
        pending(context, intent(context, ACTION_SNOOZE, id).putExtra(EXTRA_TIME, time.toString()).putExtra(EXTRA_MINUTES, minutes), id * 4 + if (minutes <= 10) 1 else 2)

    fun water(context: Context, id: Int) = pending(context, intent(context, ACTION_WATER, id), id * 4 + 3)

    fun action(context: Context, label: String, intent: PendingIntent): NotificationCompat.Action =
        NotificationCompat.Action.Builder(R.drawable.ic_notification, label, intent).build()
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Null when called directly (tests): nothing to finish then.
        val pending: PendingResult? = goAsync()
        try {
            handle(context, intent)
        } finally {
            pending?.finish()
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val repo = context.repository
        val now = repo.now()
        val id = intent.getIntExtra(NotificationActions.EXTRA_ID, 0)
        val manager = NotificationManagerCompat.from(context)
        when (intent.action) {
            NotificationActions.ACTION_TAKE -> {
                val time = intent.getStringExtra(NotificationActions.EXTRA_TIME)?.let(LocalDateTime::parse) ?: return
                var points: Int? = null
                repo.update { s -> Game.giveAllAt(s, time, now)?.let { (u, p) -> points = p; u } ?: s }
                manager.cancel(id)
                val pet = repo.state.value.game.petName
                Notifications.confirm(context, id, points?.let { "$pet a bien reçu ses médicaments : +$it pts ✓" } ?: "Déjà donné ✓")
                if (repo.state.value.treatments.any { it.isLowStock }) Notifications.showStock(context, repo.state.value)
            }
            NotificationActions.ACTION_SNOOZE -> {
                val time = intent.getStringExtra(NotificationActions.EXTRA_TIME)?.let(LocalDateTime::parse) ?: return
                val minutes = intent.getIntExtra(NotificationActions.EXTRA_MINUTES, 10).toLong()
                repo.update { s -> s.copy(snoozes = s.snoozes.filterNot { it.doseTime == time } + Snooze(time, now.plusMinutes(minutes))) }
                manager.cancel(id)
            }
            NotificationActions.ACTION_WATER -> {
                var total = 0
                repo.update { s -> Game.addWater(s, 1, now).let { (u, r) -> total = r.total; u } }
                manager.cancel(id)
                val water = repo.state.value
                val glasses = water.entryFor(now.toLocalDate()).waterGlasses
                Notifications.confirm(context, id, "Verre noté : $glasses / ${water.settings.water.goalGlasses}" + if (total > 0) " · +$total pts 💧" else " 💧")
            }
        }
        val state = repo.state.value
        ReminderScheduler.reschedule(context, state, now)
        repo.flush()
        runCatching { Widgets.requestUpdate(context) }
    }
}
