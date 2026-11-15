package com.medipote.wear

import android.app.Application
import android.content.Context

class WearApp : Application() {
    val store: WearStore by lazy { WearStore(filesDir) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}

val Context.store: WearStore get() = (applicationContext as WearApp).store
