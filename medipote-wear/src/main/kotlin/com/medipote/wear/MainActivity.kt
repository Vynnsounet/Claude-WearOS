package com.medipote.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.medipote.wear.ui.MediPoteWearApp

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Granted or not, the phone must know whether the watch rings by itself.
        WearLink.hello(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MediPoteWearApp() }
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app asks the phone for a fresh state.
        WearLink.hello(this)
        Reminders.schedule(this)
    }
}
