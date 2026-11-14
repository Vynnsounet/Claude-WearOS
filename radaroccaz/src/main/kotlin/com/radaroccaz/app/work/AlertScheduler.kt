package com.radaroccaz.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.radaroccaz.app.RadarApplication
import com.radaroccaz.app.data.Alert
import java.util.concurrent.TimeUnit

/** One periodic job checks every alert; each alert keeps its own frequency (see [AlertChecker.isDue]). */
object AlertScheduler {
    private const val WORK_NAME = "radar-occaz-alerts"

    /** Android runs periodic work at most every 15 minutes. */
    fun intervalMinutes(alerts: List<Alert>): Long? =
        alerts.filter { it.enabled }.minOfOrNull { it.frequency.minutes }?.coerceAtLeast(15)

    fun sync(context: Context, alerts: List<Alert>) {
        val wm = WorkManager.getInstance(context)
        val interval = intervalMinutes(alerts)
        if (interval == null) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AlertWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as RadarApplication
        return try {
            app.alertChecker.check().forEach { Notifications.showNewListings(applicationContext, it) }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
