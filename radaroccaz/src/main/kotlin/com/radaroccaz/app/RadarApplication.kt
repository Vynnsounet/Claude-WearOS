package com.radaroccaz.app

import android.app.Application
import com.radaroccaz.app.data.AppRepository
import com.radaroccaz.app.data.Settings
import com.radaroccaz.app.domain.SearchEngine
import com.radaroccaz.app.data.CarCatalog
import com.radaroccaz.app.sources.AramisAutoSource
import com.radaroccaz.app.sources.AutoScout24Source
import com.radaroccaz.app.sources.AutoheroSource
import com.radaroccaz.app.sources.LargusSource
import com.radaroccaz.app.sources.ParuVenduSource
import com.radaroccaz.app.sources.DemoSource
import com.radaroccaz.app.sources.HttpFetcher
import com.radaroccaz.app.sources.LaCentraleSource
import com.radaroccaz.app.sources.LeboncoinSource
import com.radaroccaz.app.sources.ListingSource
import com.radaroccaz.app.work.AlertChecker
import com.radaroccaz.app.work.AlertScheduler
import com.radaroccaz.app.work.Notifications
import java.io.File

class RadarApplication : Application() {
    val repository by lazy { AppRepository(File(filesDir, "radar-occaz.json")) }
    private val fetcher by lazy { HttpFetcher() }

    /** Real websites, in the order they are shown. */
    val sources: List<ListingSource> by lazy {
        listOf(
            AutoScout24Source(fetcher),
            AramisAutoSource(fetcher),
            AutoheroSource(fetcher),
            LargusSource(fetcher),
            ParuVenduSource(fetcher),
            LeboncoinSource(fetcher),
            LaCentraleSource(fetcher),
        )
    }
    val engine by lazy { SearchEngine(sources + DemoSource()) }
    val alertChecker by lazy { AlertChecker(repository, engine, ::activeSources) }

    fun activeSources(settings: Settings = repository.state.value.settings): Set<String> =
        if (settings.demoMode) setOf(DemoSource.ID) else settings.enabledSources

    override fun onCreate() {
        super.onCreate()
        try {
            CarCatalog.load(assets.open("catalog/models.txt"), assets.open("catalog/specs.txt"))
        } catch (e: java.io.IOException) {
            android.util.Log.e("RadarOccaz", "Catalog not loaded", e)
        }
        // Sites added by an update are switched on once.
        repository.update { s ->
            val added = Settings.DEFAULT_SOURCES - s.settings.knownSources
            if (added.isEmpty()) s
            else s.copy(settings = s.settings.copy(enabledSources = s.settings.enabledSources + added, knownSources = s.settings.knownSources + added))
        }
        Notifications.createChannel(this)
        val alerts = repository.state.value.alerts
        if (alerts.isNotEmpty()) AlertScheduler.sync(this, alerts)
    }
}
