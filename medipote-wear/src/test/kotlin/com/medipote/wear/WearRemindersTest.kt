package com.medipote.wear

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchReminder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The watch rings by itself from the phone's snapshot, and its buttons become actions for the phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearRemindersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.store.onSnapshot(sampleSnapshot(day))
    }

    @Test
    fun alarmsFollowTheSnapshot() {
        Reminders.schedule(context, at(7))
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        // 8:00, its follow-up, 11:00 water and 12:00.
        assertEquals(4, alarms.scheduledAlarms.size)
        // The 8:00 doses taken on the watch: their two alarms go away.
        val keys = context.store.snapshot!!.doses.filter { it.time == at(8).toString() }.map { it.key }
        WearLink.send(context, WearLink.action(WatchAction.TAKE, at(7, 50), doseKeys = keys))
        Reminders.schedule(context, at(7, 51))
        assertEquals(2, alarms.scheduledAlarms.size)
    }

    @Test
    fun aDoseReminderVibratesAndCanBeValidatedFromTheWrist() {
        val reminder = WatchReminder(WatchReminder.DOSE, at(8).toString(), at(8).toString())
        assertTrue(Reminders.show(context, reminder, at(8)))
        val n = notifications.allNotifications.single()
        assertEquals("C'est l'heure 💊", n.extras.getCharSequence("android.title").toString())
        assertEquals("Metformine 1000 mg (1 comprimé), Vitamine D3 (1 ampoule)", n.extras.getCharSequence("android.text").toString())
        assertEquals(listOf("Pris ✓", "+10 min"), n.actions.map { it.title.toString() })
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(Reminders.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(channel.shouldVibrate())

        // "Pris ✓": both doses are marked here at once and wait for the phone.
        ActionReceiver().onReceive(context, shadowOf(n.actions[0].actionIntent).savedIntent)
        val state = context.store.state.value
        assertTrue(state.snapshot!!.doses.filter { it.time == at(8).toString() }.all { it.taken })
        val action = state.pending.single()
        assertEquals(WatchAction.TAKE, action.type)
        assertEquals(listOf("b", "b2"), action.doseKeys)
        assertTrue(notifications.allNotifications.isEmpty())
        // The follow-up has nothing left to say.
        assertFalse(Reminders.show(context, WatchReminder(WatchReminder.FOLLOW_UP, at(8, 30).toString(), at(8).toString()), at(8, 30)))

        // The phone recorded it (it deletes the action): nothing is waiting any more,
        // and its next snapshot says the same thing.
        context.store.confirm(action.id)
        assertTrue(context.store.state.value.pending.isEmpty())
    }

    @Test
    fun waterReminderAddsAGlass() {
        assertTrue(Reminders.show(context, WatchReminder(WatchReminder.WATER, at(11).toString()), at(11)))
        val n = notifications.allNotifications.single()
        assertEquals("3 / 8 verres aujourd'hui", n.extras.getCharSequence("android.text").toString())
        ActionReceiver().onReceive(context, shadowOf(n.actions.single().actionIntent).savedIntent)
        assertEquals(WatchAction.WATER, context.store.state.value.pending.single().type)
    }

    @Test
    fun theStoreSurvivesARestart() {
        WearLink.send(context, WearLink.action(WatchAction.WATER, at(9), value = 1.0))
        val reloaded = WearStore(context.filesDir)
        assertEquals("Pilou", reloaded.snapshot?.petName)
        assertEquals(1, reloaded.state.value.pending.size)
        assertEquals(4, reloaded.snapshot?.glasses)
        // An older snapshot arriving late doesn't replace a newer one.
        reloaded.onSnapshot(sampleSnapshot(day).copy(generatedAt = at(6).toString(), petName = "Vieux"))
        assertEquals("Pilou", reloaded.snapshot?.petName)
    }
}
