package com.radaroccaz.app

import android.app.Application
import com.radaroccaz.app.data.AppRepository
import com.radaroccaz.app.data.Settings
import com.radaroccaz.app.domain.SearchEngine
import com.radaroccaz.app.sources.AutoScout24Source
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
        listOf(LeboncoinSource(fetcher), LaCentraleSource(fetcher), AutoScout24Source(fetcher))
    }
    val engine by lazy { SearchEngine(sources + DemoSource()) }
    val alertChecker by lazy { AlertChecker(repository, engine, ::activeSources) }

    fun activeSources(settings: Settings = repository.state.value.settings): Set<String> =
        if (settings.demoMode) setOf(DemoSource.ID) else settings.enabledSources

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        val alerts = repository.state.value.alerts
        if (alerts.isNotEmpty()) AlertScheduler.sync(this, alerts)
    }
}
