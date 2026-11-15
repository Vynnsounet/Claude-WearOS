package com.medipote.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.Treatment
import com.medipote.app.notifications.ReminderScheduler
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** End to end: plan → AlarmManager → receiver → notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    private val doseTime = now.plusHours(3)

    private val state = AppState(
        onboarded = true,
        treatments = listOf(
            Treatment(
                "t", name = "Amlodipine", strength = "5 mg",
                doses = listOf(DoseTime(doseTime.hour * 60 + doseTime.minute)),
                startDate = now.toLocalDate().minusDays(1), createdAt = now.minusDays(1),
            ),
        ),
    )

    @Before
    fun setUp() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.repository.update { state }
    }

    // The first dose alarm (other reminders, such as the evening ritual, may fall earlier in the day).
    private fun doseAlarm() = alarms.scheduledAlarms
        .filter { shadowOf(it.operation).savedIntent.getStringExtra(ReminderScheduler.EXTRA_KEY).orEmpty().startsWith("DOSE|") }
        .minBy { it.triggerAtTime }

    @Test
    fun exactAlarmsWhenAllowed() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ReminderScheduler.reschedule(context, state, now)
        val alarm = doseAlarm()
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
        assertTrue(alarm.isAllowWhileIdle)
    }

    @Test
    fun withoutExactPermissionTheDelayIsBounded() {
        // Android 14+ denies exact alarms by default: reminders must still come within minutes,
        // not hours late (the default inexact window is 75 % of the time left).
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        ReminderScheduler.reschedule(context, state, now)
        val alarm = doseAlarm()
        assertTrue("window ${alarm.windowLengthMs} ms", alarm.windowLengthMs in 0..10 * 60_000L)
    }

    @Test
    fun firedAlarmPostsTheNotification() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ReminderScheduler.reschedule(context, state, now)
        val alarm = doseAlarm()
        alarms.fireAlarm(alarm)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, posted.size)
        val n = posted.single()
        assertEquals("C'est l'heure des médicaments 💊", n.extras.getCharSequence("android.title").toString())
        assertTrue(n.extras.getCharSequence("android.text").toString().contains("Amlodipine 5 mg"))
    }

    @Test
    fun testNotificationIsPostedImmediately() {
        com.medipote.app.notifications.Notifications.showTest(context, state)
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, posted.size)
    }

    @Test
    fun remindersUseLoudPopUpChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        // A channel left by an older version is removed.
        manager.createNotificationChannel(android.app.NotificationChannel("dose_reminders", "old", NotificationManager.IMPORTANCE_LOW))
        com.medipote.app.notifications.Notifications.createChannels(context)
        assertEquals(null, manager.getNotificationChannel("dose_reminders"))
        for (id in com.medipote.app.notifications.Notifications.ALL_CHANNELS) {
            val c = manager.getNotificationChannel(id)
            assertEquals(id, NotificationManager.IMPORTANCE_HIGH, c.importance)
            assertTrue(id, c.sound != null)
            assertTrue(id, c.shouldVibrate())
        }
        assertTrue(com.medipote.app.notifications.Notifications.quietChannels(context).isEmpty())
        // The user lowers the dose channel: the app notices and can point to its settings.
        val doses = manager.getNotificationChannel(com.medipote.app.notifications.Notifications.CHANNEL_DOSES)
        doses.importance = NotificationManager.IMPORTANCE_DEFAULT
        manager.createNotificationChannel(doses) // Robolectric stores the change; on a phone only the user can do this
        assertEquals(
            listOf(com.medipote.app.notifications.Notifications.CHANNEL_DOSES),
            com.medipote.app.notifications.Notifications.quietChannels(context),
        )
    }

    @Test
    fun notificationButtonsSnoozeAndGive() {
        val repo = context.repository
        val zone = java.time.ZoneId.systemDefault()
        repo.clock = java.time.Clock.fixed(doseTime.plusMinutes(5).atZone(zone).toInstant(), zone)
        try {
            com.medipote.app.notifications.Notifications.showDoses(context, repo.state.value, doseTime, followUp = false)
            val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
            val n = manager.allNotifications.single()
            assertEquals(listOf("Pris ✓", "+10 min", "+30 min"), n.actions.map { it.title.toString() })
            val receiver = com.medipote.app.notifications.NotificationActionReceiver()
            // "+10 min": the reminder comes back 10 minutes later.
            receiver.onReceive(context, shadowOf(n.actions[1].actionIntent).savedIntent)
            assertEquals(doseTime.plusMinutes(15), repo.state.value.snoozes.single().until)
            assertTrue(manager.allNotifications.isEmpty())
            val snoozeAlarm = alarms.scheduledAlarms.map { shadowOf(it.operation).savedIntent.getStringExtra(ReminderScheduler.EXTRA_KEY) }
            assertTrue(snoozeAlarm.toString(), snoozeAlarm.any { it!!.startsWith("SNOOZE|") })
            // "Pris": the dose is given (the pet is fed) without opening the app.
            receiver.onReceive(context, shadowOf(n.actions[0].actionIntent).savedIntent)
            val log = repo.state.value.doseLogs.single()
            assertEquals(com.medipote.app.data.TakeStatus.ON_TIME, log.status)
            assertTrue(repo.state.value.snoozes.isEmpty())
            assertTrue(repo.state.value.game.points >= 10)
            val confirm = manager.allNotifications.single()
            assertTrue(confirm.extras.getCharSequence("android.title").toString().contains("a bien reçu ses médicaments"))
        } finally {
            repo.clock = java.time.Clock.systemDefaultZone()
        }
    }

    @Test
    fun periodProductReminderAndButtons() {
        val repo = context.repository
        val start = now.minusHours(4).minusMinutes(5)
        repo.update { it.copy(productSession = com.medipote.app.data.ProductSession(com.medipote.app.data.PeriodProduct.TAMPON, start, 4 * 60)) }
        com.medipote.app.notifications.Notifications.showProduct(context, repo.state.value, "remind", now)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val n = manager.allNotifications.single()
        assertEquals("🔸 Pense à changer ton tampon", n.extras.getCharSequence("android.title").toString())
        assertEquals(listOf("J'ai changé ✓", "J'ai retiré"), n.actions.map { it.title.toString() })
        // "J'ai changé": the timer starts again.
        com.medipote.app.notifications.NotificationActionReceiver().onReceive(context, shadowOf(n.actions[0].actionIntent).savedIntent)
        val session = repo.state.value.productSession!!
        assertTrue(session.since.isAfter(start))
        assertEquals(1, repo.state.value.productLog.size)
        // Already changed: a late alarm says nothing.
        context.getSystemService(NotificationManager::class.java).cancelAll()
        com.medipote.app.notifications.Notifications.showProduct(context, repo.state.value, "remind", session.since.plusMinutes(10))
        assertTrue(manager.allNotifications.isEmpty())
    }

    @Test
    fun withTheWatchAppRemindersStayOnThePhoneAndWatchActionsAreRecordedOnce() {
        val repo = context.repository
        val link = com.medipote.app.wear.WatchLink
        link.forget(context)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val doseTime = repo.state.value.let { s -> com.medipote.app.wear.WatchBridge.snapshot(s, now).doses.first().at }

        // No watch app: the reminder may be mirrored on a watch by the system.
        com.medipote.app.notifications.Notifications.showDoses(context, repo.state.value, doseTime, followUp = false)
        assertEquals(0, manager.allNotifications.single().flags and android.app.Notification.FLAG_LOCAL_ONLY)
        context.getSystemService(NotificationManager::class.java).cancelAll()

        // The watch app said it rings by itself: the phone's copy stays on the phone.
        link.onHello(context, handlesReminders = true)
        assertTrue(link.watchHandlesReminders(context))
        com.medipote.app.notifications.Notifications.showDoses(context, repo.state.value, doseTime, followUp = false)
        assertTrue(manager.allNotifications.single().flags and android.app.Notification.FLAG_LOCAL_ONLY != 0)
        // Without news of the watch for days, the phone goes back to mirroring.
        assertTrue(!link.watchHandlesReminders(context, System.currentTimeMillis() + 4 * 24 * 3600_000L))

        // A glass noted on the watch counts once, even if the action is delivered twice.
        val glass = com.medipote.sync.WatchAction("w1", com.medipote.sync.WatchAction.WATER, now.toString(), value = 1.0)
        assertTrue(link.record(context, glass))
        assertTrue(!link.record(context, glass))
        assertEquals(1, repo.state.value.entryFor(now.toLocalDate()).waterGlasses)

        // The dose validated on the watch: recorded, and its reminder leaves the phone.
        val keys = com.medipote.app.wear.WatchBridge.snapshot(repo.state.value, now).doses.filter { it.at == doseTime }.map { it.key }
        assertTrue(link.record(context, com.medipote.sync.WatchAction("t1", com.medipote.sync.WatchAction.TAKE, now.toString(), doseKeys = keys)))
        assertEquals(keys.size, repo.state.value.doseLogs.size)
        assertTrue(manager.allNotifications.isEmpty())
        link.forget(context)
    }
}
