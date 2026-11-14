package com.radaroccaz.app

import com.radaroccaz.app.data.CarCatalog
import java.io.File

/** Loads the real catalog from the app's assets (tests run from the module directory). */
object TestCatalog {
    @Volatile private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            CarCatalog.load(File("src/main/assets/catalog/models.txt").inputStream(), File("src/main/assets/catalog/specs.txt").inputStream())
            loaded = true
        }
    }
}
