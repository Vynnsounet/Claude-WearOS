package com.medipote.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.medipote.app.notifications.Notifications

/**
 * The shortcuts of a long press on the app icon: the things one needs in a hurry. They open the same
 * screens as a notification does. "Symptômes" and "Journée douce" appear only once turned on.
 */
object Shortcuts {
    fun update(context: Context, wellbeing: Boolean) {
        runCatching {
            fun shortcut(id: String, label: String, route: String) = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(Notifications.EXTRA_ROUTE, route),
                )
                .build()
            val list = buildList {
                add(shortcut("support", "Ça ne va pas", "support"))
                if (wellbeing) add(shortcut("symptoms", "Noter un symptôme", "symptoms"))
                add(shortcut("night", "Nuit difficile", "sleep_help"))
                add(shortcut("journal", "Mon journal", "journal_today"))
                if (wellbeing) add(shortcut("softday", "Journée douce", "softday"))
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, list.take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1)))
        }
    }
}
