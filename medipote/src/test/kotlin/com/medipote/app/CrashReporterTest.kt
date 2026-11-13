package com.medipote.app

import android.app.AlertDialog
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashReporterTest {

    private val context: MediPoteApplication = ApplicationProvider.getApplicationContext()

    @Test
    fun crashIsRecordedWithDeviceInfo() {
        CrashReporter.record(context, Thread.currentThread(), IllegalStateException("Boom"))
        val report = CrashReporter.pending(context)!!
        assertTrue(report, report.contains("IllegalStateException: Boom"))
        assertTrue(report, report.contains("Android "))
        CrashReporter.clear(context)
        assertNull(CrashReporter.pending(context))
    }

    @Test
    fun nextLaunchOffersToShareTheReport() {
        CrashReporter.record(context, Thread.currentThread(), RuntimeException("Au lancement"))
        ShadowChoreographer.setPaused(true) // the pet's endless animations would never let the looper idle
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        assertNull("the report is cleared once shared", CrashReporter.pending(context))
        val chooser = shadowOf(activity).nextStartedActivity ?: shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Au lancement"))
        assertNull(CrashReporter.pending(context))
    }
}
