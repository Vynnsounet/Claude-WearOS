package com.radaroccaz.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.radaroccaz.app.ui.AppViewModel
import com.radaroccaz.app.ui.RadarApp
import com.radaroccaz.app.work.Notifications

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { RadarApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A tap on an alert notification opens that alert's results. */
    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_ALERT_ID)?.let(vm::requestOpenAlert)
    }
}
