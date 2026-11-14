package com.radaroccaz.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radaroccaz.app.RadarApplication
import com.radaroccaz.app.data.Alert
import com.radaroccaz.app.data.AlertFrequency
import com.radaroccaz.app.data.AppState
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.Settings
import com.radaroccaz.app.data.VehicleHistory
import com.radaroccaz.app.analysis.VehicleAnalysis
import com.radaroccaz.app.domain.Ranking
import com.radaroccaz.app.domain.ScoredListing
import com.radaroccaz.app.domain.SortOrder
import com.radaroccaz.app.domain.SourceStatus
import com.radaroccaz.app.sources.DemoSource
import com.radaroccaz.app.work.AlertScheduler
import com.radaroccaz.app.work.Notifications
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultsState(
    val criteria: SearchCriteria? = null,
    val statuses: List<SourceStatus> = emptyList(),
    val results: List<ScoredListing> = emptyList(),
    val sort: SortOrder = SortOrder.BEST,
    val running: Boolean = false,
    /** Set when the results come from an alert. */
    val alertId: String? = null,
    /** Listings to flag as "Nouveau" (found by the alert since the last visit). */
    val newIds: Set<String> = emptySet(),
    val demo: Boolean = false,
) {
    val sorted: List<ScoredListing> by lazy { Ranking.sort(results, sort) }
}

enum class DeepState { LOADING, DONE, FAILED }

/** Progress of the full-page analysis of one listing. */
data class DeepStatus(val state: DeepState, val message: String? = null)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RadarApplication
    private val repo = app.repository

    val state: StateFlow<AppState> = repo.state

    private val _draft = MutableStateFlow(repo.state.value.lastCriteria ?: SearchCriteria())
    val draft: StateFlow<SearchCriteria> = _draft.asStateFlow()

    private val _results = MutableStateFlow(ResultsState())
    val results: StateFlow<ResultsState> = _results.asStateFlow()

    private val _checkingAlerts = MutableStateFlow(false)
    val checkingAlerts: StateFlow<Boolean> = _checkingAlerts.asStateFlow()

    /** Alert to open, set by a notification tap and consumed by the navigation. */
    private val _pendingAlert = MutableStateFlow<String?>(null)
    val pendingAlert: StateFlow<String?> = _pendingAlert.asStateFlow()

    private val _deep = MutableStateFlow<Map<String, DeepStatus>>(emptyMap())
    val deep: StateFlow<Map<String, DeepStatus>> = _deep.asStateFlow()

    private var searchJob: Job? = null

    val sources get() = app.sources

    fun updateDraft(transform: (SearchCriteria) -> SearchCriteria) = _draft.update(transform)

    fun resetDraft() { _draft.value = SearchCriteria(priorities = _draft.value.priorities) }

    fun search(criteria: SearchCriteria = _draft.value, alertId: String? = null, newIds: Set<String> = emptySet()) {
        if (!criteria.isValid) return
        if (alertId == null) {
            repo.update { s ->
                s.copy(
                    lastCriteria = criteria,
                    recentSearches = (listOf(criteria) + s.recentSearches.filter { it != criteria }).take(MAX_RECENT),
                )
            }
        }
        val settings = repo.state.value.settings
        searchJob?.cancel()
        _results.value = ResultsState(criteria = criteria, running = true, alertId = alertId, newIds = newIds,
            sort = _results.value.sort, demo = settings.demoMode)
        searchJob = viewModelScope.launch {
            app.engine.search(criteria, app.activeSources(settings), repo.state.value.userHistory).collect { progress ->
                _results.update { it.copy(statuses = progress.statuses, results = progress.results, running = !progress.done) }
            }
            _results.update { it.copy(running = false) }
        }
    }

    fun retry() { _results.value.criteria?.let { search(it, _results.value.alertId, _results.value.newIds) } }

    fun setSort(order: SortOrder) = _results.update { it.copy(sort = order) }

    fun useDemoAndRetry() {
        updateSettings { it.copy(demoMode = true) }
        retry()
    }

    // ---- Favorites ----

    fun toggleFavorite(listing: Listing) = repo.update { s ->
        if (s.favorites.any { it.id == listing.id }) s.copy(favorites = s.favorites.filter { it.id != listing.id })
        else s.copy(favorites = listOf(listing) + s.favorites)
    }

    /** A listing shown anywhere in the app: current results, favorites or alert news. */
    fun findListing(id: String): Listing? =
        _results.value.results.firstOrNull { it.listing.id == id }?.listing
            ?: state.value.favorites.firstOrNull { it.id == id }
            ?: state.value.alerts.firstNotNullOfOrNull { a -> a.unread.firstOrNull { it.id == id } }

    fun scored(id: String): ScoredListing? = _results.value.results.firstOrNull { it.listing.id == id }

    // ---- Full analysis and history ----

    /** Reads the listing's own page (full text, every attribute) and re-analyses it. */
    fun analyzeDeep(id: String, force: Boolean = false) {
        val listing = findListing(id) ?: return
        val current = _deep.value[id]?.state
        if (!force && (listing.detailsLoaded || current == DeepState.LOADING || current == DeepState.DONE)) return
        _deep.update { it + (id to DeepStatus(DeepState.LOADING)) }
        viewModelScope.launch {
            try {
                val full = app.engine.details(listing)
                replaceListing(full)
                _deep.update { it + (id to DeepStatus(DeepState.DONE)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.radaroccaz.app.sources.SourceBlockedException) {
                _deep.update { it + (id to DeepStatus(DeepState.FAILED, "Le site a bloqué la lecture de l'annonce : analyse faite sur les données de la recherche.")) }
            } catch (e: Exception) {
                _deep.update { it + (id to DeepStatus(DeepState.FAILED, "Annonce complète illisible : analyse faite sur les données de la recherche.")) }
            }
        }
    }

    private fun replaceListing(full: Listing) {
        val r = _results.value
        if (r.criteria != null && r.results.any { it.listing.id == full.id }) {
            val listings = r.results.map { if (it.listing.id == full.id) full else it.listing }
            _results.update { it.copy(results = Ranking.score(listings, r.criteria, state.value.userHistory)) }
        }
        repo.update { s ->
            s.copy(
                favorites = s.favorites.map { if (it.id == full.id) full else it },
                alerts = s.alerts.map { a -> a.copy(unread = a.unread.map { if (it.id == full.id) full else it }) },
            )
        }
    }

    /** What the user entered from a HistoVec report or the seller's answers. */
    fun saveUserHistory(id: String, history: VehicleHistory) {
        repo.update { s ->
            s.copy(userHistory = if (history.isEmpty) s.userHistory - id else s.userHistory + (id to history))
        }
        val r = _results.value
        if (r.criteria != null && r.results.isNotEmpty()) {
            _results.update { it.copy(results = Ranking.score(r.results.map { s -> s.listing }, r.criteria, state.value.userHistory)) }
        }
    }

    /** Analysis of a listing outside the current results (favorites, alerts): no market comparison. */
    fun analysisFor(listing: Listing) =
        scored(listing.id)?.analysis ?: VehicleAnalysis.analyze(listing, state.value.userHistory[listing.id])

    // ---- Alerts ----

    /** Creates an alert for the current search; what is already listed will not be reported again. */
    fun createAlert(name: String, frequency: AlertFrequency): Alert? {
        val r = _results.value
        val criteria = r.criteria ?: return null
        val alert = Alert(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { criteria.title },
            criteria = criteria,
            frequency = frequency,
            createdAt = System.currentTimeMillis(),
            lastCheckedAt = if (r.running) null else System.currentTimeMillis(),
            seenIds = r.results.map { it.listing.id },
        )
        val updated = repo.update { it.copy(alerts = it.alerts + alert) }
        AlertScheduler.sync(app, updated.alerts)
        _results.update { it.copy(alertId = alert.id) }
        return alert
    }

    fun setAlertEnabled(id: String, enabled: Boolean) {
        val s = repo.updateAlert(id) { it.copy(enabled = enabled) }
        AlertScheduler.sync(app, s.alerts)
    }

    fun setAlertFrequency(id: String, frequency: AlertFrequency) {
        val s = repo.updateAlert(id) { it.copy(frequency = frequency) }
        AlertScheduler.sync(app, s.alerts)
    }

    fun deleteAlert(id: String) {
        val s = repo.update { st -> st.copy(alerts = st.alerts.filter { it.id != id }) }
        AlertScheduler.sync(app, s.alerts)
    }

    /** Shows the alert's search, flagging the listings found since the last visit. */
    fun openAlert(id: String) {
        val alert = state.value.alerts.firstOrNull { it.id == id } ?: return
        val newIds = alert.unread.map { it.id }.toSet()
        repo.updateAlert(id) { it.copy(unread = emptyList()) }
        search(alert.criteria, alertId = id, newIds = newIds)
    }

    fun requestOpenAlert(id: String) { _pendingAlert.value = id }
    fun consumePendingAlert() { _pendingAlert.value = null }

    fun checkAlertsNow() {
        if (_checkingAlerts.value) return
        _checkingAlerts.value = true
        viewModelScope.launch {
            try {
                app.alertChecker.check(force = true).forEach { Notifications.showNewListings(app, it) }
            } finally {
                _checkingAlerts.value = false
            }
        }
    }

    // ---- Settings ----

    fun updateSettings(transform: (Settings) -> Settings) = repo.update { it.copy(settings = transform(it.settings)) }

    fun clearRecentSearches() = repo.update { it.copy(recentSearches = emptyList()) }

    val demoSourceId = DemoSource.ID

    companion object {
        private const val MAX_RECENT = 6
    }
}
