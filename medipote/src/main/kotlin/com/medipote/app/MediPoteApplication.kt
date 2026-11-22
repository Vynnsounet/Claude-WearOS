package com.medipote.app

import android.app.Application
import android.content.Context
import com.medipote.app.data.AppRepository
import com.medipote.app.notifications.Notifications
import com.medipote.app.widget.Widgets
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class MediPoteApplication : Application() {

    val repository: AppRepository by lazy { AppRepository(File(filesDir, "medipote.json")) }

    /** Work that outlives a screen: widget refreshes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // As early as possible: library initializers (androidx.startup) run before onCreate.
        CrashReporter.install(this)
    }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        forgetRemovedAiKey()
        // Any change to the data (from the app, a notification or a widget) refreshes the widgets.
        appScope.launch {
            repository.state.drop(1).debounce(300).collect {
                runCatching { Widgets.updateAll(this@MediPoteApplication) }
                // ...and the watch, when there is one.
                com.medipote.app.wear.WatchLink.publish(this@MediPoteApplication)
            }
        }
        com.medipote.app.wear.WatchLink.drain(this)
    }
}

/**
 * Version 1.29.0 briefly offered an AI assistant that kept the person's API key, encrypted, in a private file of the phone.
 * The assistant is gone: a key it may have kept is erased, once, at the next launch.
 */
private fun Context.forgetRemovedAiKey() {
    val flag = getSharedPreferences("housekeeping", Context.MODE_PRIVATE)
    if (flag.getBoolean("ai_key_erased", false)) return
    runCatching {
        getSharedPreferences("ai_key", Context.MODE_PRIVATE).edit().clear().apply()
        deleteSharedPreferences("ai_key")
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("medipote_ai_key")
    }
    flag.edit().putBoolean("ai_key_erased", true).apply()
}

val Context.repository: AppRepository get() = (applicationContext as MediPoteApplication).repository
