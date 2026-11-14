package com.radaroccaz.app.work

import com.radaroccaz.app.data.Alert
import com.radaroccaz.app.data.AppRepository
import com.radaroccaz.app.data.Settings
import com.radaroccaz.app.domain.Ranking
import com.radaroccaz.app.domain.ScoredListing
import com.radaroccaz.app.domain.SearchEngine
import com.radaroccaz.app.domain.SortOrder
import com.radaroccaz.app.domain.SourceState

data class AlertHit(val alert: Alert, val newListings: List<ScoredListing>)

/** Runs the searches of the alerts that are due and records the listings never seen before. */
class AlertChecker(
    private val repository: AppRepository,
    private val engine: SearchEngine,
    private val sourceIds: (Settings) -> Set<String>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun isDue(alert: Alert, at: Long): Boolean {
        val last = alert.lastCheckedAt ?: return true
        // A little slack so a 30-min alert is not skipped when WorkManager runs a few minutes early.
        return at - last >= alert.frequency.minutes * 60_000 - 5 * 60_000
    }

    suspend fun check(force: Boolean = false): List<AlertHit> {
        val state = repository.state.value
        val ids = sourceIds(state.settings)
        val hits = mutableListOf<AlertHit>()
        for (alert in state.alerts.filter { it.enabled && (force || isDue(it, now())) }) {
            val progress = engine.searchAll(alert.criteria, ids, state.userHistory)
            val checkedAt = now()
            if (progress.statuses.isNotEmpty() && progress.statuses.none { it.state == SourceState.OK }) {
                val reason = progress.statuses.firstNotNullOfOrNull { it.message } ?: "Aucun site n'a répondu."
                repository.updateAlert(alert.id) { it.copy(lastCheckedAt = checkedAt, lastError = reason) }
                continue
            }
            var fresh: List<ScoredListing> = emptyList()
            repository.updateAlert(alert.id) { current ->
                val seen = current.seenIds.toHashSet()
                fresh = Ranking.sort(progress.results.filter { it.listing.id !in seen }, SortOrder.BEST)
                current.copy(
                    lastCheckedAt = checkedAt,
                    lastError = null,
                    seenIds = (fresh.map { it.listing.id } + current.seenIds).take(Alert.MAX_SEEN),
                    unread = (fresh.map { it.listing } + current.unread).distinctBy { it.id }.take(Alert.MAX_UNREAD),
                )
            }
            if (fresh.isNotEmpty()) hits += AlertHit(alert, fresh)
        }
        return hits
    }
}
