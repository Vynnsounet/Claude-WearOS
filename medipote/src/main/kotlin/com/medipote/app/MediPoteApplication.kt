package com.medipote.app

import android.app.Application
import android.content.Context
import com.medipote.app.data.AppRepository
import com.medipote.app.notifications.Notifications
import java.io.File

class MediPoteApplication : Application() {

    val repository: AppRepository by lazy { AppRepository(File(filesDir, "medipote.json")) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

val Context.repository: AppRepository get() = (applicationContext as MediPoteApplication).repository
