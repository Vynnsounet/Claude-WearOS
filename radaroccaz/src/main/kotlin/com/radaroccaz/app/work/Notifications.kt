package com.radaroccaz.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.radaroccaz.app.MainActivity
import com.radaroccaz.app.R
import com.radaroccaz.app.data.Format

object Notifications {
    const val CHANNEL_ID = "new_listings"
    const val EXTRA_ALERT_ID = "alertId"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "Nouvelles annonces", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Annonces qui correspondent à vos alertes"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun showNewListings(context: Context, hit: AlertHit) {
        if (!canNotify(context)) return
        val n = hit.newListings.size
        val title = if (n == 1) "Nouvelle annonce · ${hit.alert.name}" else "$n nouvelles annonces · ${hit.alert.name}"
        val lines = hit.newListings.take(4).map { s ->
            val l = s.listing
            listOfNotNull(Format.price(l.price), l.year?.toString(), l.mileageKm?.let(Format::km), l.location).joinToString(" · ") +
                (s.dealPercent?.takeIf { it <= -5 }?.let { "  (${it} % vs marché)" } ?: "")
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALERT_ID, hit.alert.id)
        }
        val pending = PendingIntent.getActivity(
            context, hit.alert.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach(style::addLine)
        if (n > lines.size) style.setSummaryText("et ${n - lines.size} autre(s)")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull())
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(hit.alert.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
