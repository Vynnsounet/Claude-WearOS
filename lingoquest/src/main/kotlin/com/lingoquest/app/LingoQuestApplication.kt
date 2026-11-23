package com.lingoquest.app

import android.app.Application
import android.content.Context
import com.lingoquest.app.data.ProfileStore
import java.io.File

class LingoQuestApplication : Application() {
    val store: ProfileStore by lazy { ProfileStore(File(filesDir, "lingoquest.json")) }
}

val Context.profileStore: ProfileStore get() = (applicationContext as LingoQuestApplication).store
