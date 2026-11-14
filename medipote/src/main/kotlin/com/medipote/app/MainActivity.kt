package com.medipote.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.medipote.app.notifications.Notifications
import com.medipote.app.ui.AppViewModel
import com.medipote.app.ui.MediPoteApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRoute(intent)
        setContent { MediPoteApp(vm) }
        CrashReporter.offerReport(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRoute(intent)
    }

    /** A notification can ask for a given screen (e.g. rating an as-needed intake). */
    private fun openRoute(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_ROUTE)?.let(vm::open)
        intent?.removeExtra(Notifications.EXTRA_ROUTE)
    }
}
