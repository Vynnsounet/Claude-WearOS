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

    private fun doseAlarm() = alarms.scheduledAlarms.minBy { it.triggerAtTime }

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
}
