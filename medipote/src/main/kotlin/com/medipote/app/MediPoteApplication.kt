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

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Any change to the data (from the app, a notification or a widget) refreshes the widgets.
        appScope.launch {
            repository.state.drop(1).debounce(300).collect { runCatching { Widgets.updateAll(this@MediPoteApplication) } }
        }
    }
}

val Context.repository: AppRepository get() = (applicationContext as MediPoteApplication).repository
