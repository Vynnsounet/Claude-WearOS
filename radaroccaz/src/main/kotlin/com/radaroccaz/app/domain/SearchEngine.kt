package com.radaroccaz.app.domain

import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.sources.ListingSource
import com.radaroccaz.app.sources.SourceBlockedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

enum class SourceState { LOADING, OK, BLOCKED, ERROR }

data class SourceStatus(
    val sourceId: String,
    val name: String,
    val state: SourceState,
    /** Listings this source returned that match the criteria. */
    val count: Int = 0,
    val message: String? = null,
    /** The search on the site itself, to open in the browser. */
    val searchUrl: String,
)

data class SearchProgress(
    val statuses: List<SourceStatus>,
    val results: List<ScoredListing>,
) {
    val done: Boolean get() = statuses.none { it.state == SourceState.LOADING }
}

/** Queries every source in parallel and re-ranks the combined results each time one answers. */
class SearchEngine(
    private val sources: List<ListingSource>,
    private val timeoutMs: Long = 30_000,
) {
    fun source(id: String): ListingSource? = sources.firstOrNull { it.id == id }

    fun search(criteria: SearchCriteria, sourceIds: Collection<String>): Flow<SearchProgress> = channelFlow {
        val active = sources.filter { it.id in sourceIds }
        val lock = Mutex()
        val statuses = active.associate {
            it.id to SourceStatus(it.id, it.name, SourceState.LOADING, searchUrl = it.searchUrl(criteria))
        }.toMutableMap()
        val raw = mutableListOf<Listing>()
        send(SearchProgress(statuses.values.toList(), emptyList()))
        if (active.isEmpty()) { close(); return@channelFlow }

        val jobs = active.map { source ->
            launch {
                val status = try {
                    val found = withTimeout(timeoutMs) { source.search(criteria) }
                    lock.withLock { raw += found }
                    statuses.getValue(source.id).copy(state = SourceState.OK, count = found.count { Ranking.matches(it, criteria) })
                } catch (e: SourceBlockedException) {
                    statuses.getValue(source.id).copy(state = SourceState.BLOCKED, message = e.message)
                } catch (e: TimeoutCancellationException) {
                    statuses.getValue(source.id).copy(state = SourceState.ERROR, message = "Le site met trop de temps à répondre.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    statuses.getValue(source.id).copy(state = SourceState.ERROR, message = describe(e))
                }
                lock.withLock {
                    statuses[source.id] = status
                    send(SearchProgress(statuses.values.toList(), Ranking.rank(raw, criteria)))
                }
            }
        }
        launch { jobs.forEach { it.join() }; close() }
        awaitClose()
    }

    /** Waits for every source; used by the background alert checks. */
    suspend fun searchAll(criteria: SearchCriteria, sourceIds: Collection<String>): SearchProgress =
        search(criteria, sourceIds).last()

    private fun describe(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Pas de connexion Internet."
        is java.net.SocketTimeoutException -> "Le site met trop de temps à répondre."
        is java.io.IOException -> e.message ?: "Erreur réseau."
        else -> "Page illisible : le site a peut-être changé (${e::class.simpleName})."
    }
}
