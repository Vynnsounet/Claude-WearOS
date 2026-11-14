package com.radaroccaz.app.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.domain.SortOrder
import com.radaroccaz.app.domain.SourceState
import com.radaroccaz.app.domain.SourceStatus
import com.radaroccaz.app.ui.ResultsState
import com.radaroccaz.app.ui.components.EmptyState
import com.radaroccaz.app.ui.components.ListingCard
import com.radaroccaz.app.ui.theme.LocalExtraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    state: ResultsState,
    favorites: Set<String>,
    hasAlert: Boolean,
    onBack: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onOpen: (Listing) -> Unit,
    onToggleFavorite: (Listing) -> Unit,
    onCreateAlert: () -> Unit,
    onRetry: () -> Unit,
    onUseDemo: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val criteria = state.criteria
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(criteria?.title ?: "Résultats", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            criteria?.summary.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Modifier la recherche") }
                },
                actions = {
                    IconButton(onClick = onCreateAlert, enabled = !hasAlert, modifier = Modifier.testTag("alert_button")) {
                        Icon(
                            if (hasAlert) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationAdd,
                            contentDescription = if (hasAlert) "Alerte active" else "Créer une alerte",
                            tint = if (hasAlert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        val results = state.sorted
        val okCount = state.statuses.count { it.state == SourceState.OK }
        val allFailed = !state.running && state.statuses.isNotEmpty() && okCount == 0
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "sources") { SourceStatusRow(state.statuses, onOpenUrl) }
            if (state.running) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
            if (state.demo) item(key = "demo") { DemoBanner() }

            val blocked = state.statuses.filter { it.state == SourceState.BLOCKED || it.state == SourceState.ERROR }
            if (blocked.isNotEmpty() && okCount > 0) {
                item(key = "blocked") { BlockedNotice(blocked, onOpenUrl) }
            }

            when {
                results.isNotEmpty() -> {
                    item(key = "summary") { SummaryCard(state, hasAlert, onCreateAlert) }
                    item(key = "sort") { SortRow(state.sort, onSort) }
                    items(results, key = { it.listing.id }) { s ->
                        ListingCard(
                            listing = s.listing,
                            scored = s,
                            favorite = s.listing.id in favorites,
                            isNew = s.listing.id in state.newIds,
                            wantedOptions = criteria?.wantedOptions?.size ?: 0,
                            onClick = { onOpen(s.listing) },
                            onToggleFavorite = { onToggleFavorite(s.listing) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                state.running -> items(3, key = { "skeleton$it" }) { SkeletonCard() }
                allFailed -> item(key = "failed") {
                    EmptyState(
                        icon = Icons.Rounded.CloudOff,
                        title = "Les sites n'ont pas répondu",
                        message = state.statuses.firstNotNullOfOrNull { it.message }
                            ?: "Vérifiez votre connexion puis réessayez.",
                        actionLabel = "Réessayer",
                        onAction = onRetry,
                        secondary = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                state.statuses.forEach { s ->
                                    TextButton(onClick = { onOpenUrl(s.searchUrl) }) {
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Ouvrir la recherche sur ${s.name}")
                                    }
                                }
                                if (!state.demo) {
                                    OutlinedButton(onClick = onUseDemo) {
                                        Icon(Icons.Rounded.Science, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Essayer avec des annonces de démo")
                                    }
                                }
                            }
                        },
                    )
                }
                else -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "Aucune annonce pour ces critères",
                        message = "Essayez d'élargir le budget, le kilométrage ou l'année, ou retirez un filtre.",
                        actionLabel = "Modifier la recherche",
                        onAction = onBack,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceStatusRow(statuses: List<SourceStatus>, onOpenUrl: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        statuses.forEach { s ->
            val x = LocalExtraColors.current
            AssistChip(
                onClick = { onOpenUrl(s.searchUrl) },
                label = {
                    Text(
                        when (s.state) {
                            SourceState.LOADING -> s.name
                            SourceState.OK -> "${s.name} · ${s.count}"
                            SourceState.BLOCKED -> "${s.name} · bloqué"
                            SourceState.ERROR -> "${s.name} · erreur"
                        },
                    )
                },
                leadingIcon = {
                    when (s.state) {
                        SourceState.LOADING -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        SourceState.OK -> Icon(Icons.Rounded.CheckCircle, null, tint = x.great, modifier = Modifier.size(16.dp))
                        SourceState.BLOCKED -> Icon(Icons.Rounded.Shield, null, tint = x.fair, modifier = Modifier.size(16.dp))
                        SourceState.ERROR -> Icon(Icons.Rounded.ErrorOutline, null, tint = x.weak, modifier = Modifier.size(16.dp))
                    }
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
    }
}

@Composable
private fun DemoBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(
                "Mode démo : annonces fictives générées sur l'appareil. Désactivez-le dans les réglages pour interroger les vrais sites.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun BlockedNotice(blocked: List<SourceStatus>, onOpenUrl: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
            Text(
                blocked.joinToString(", ") { it.name } +
                    if (blocked.size > 1) " n'ont pas pu être consultés." else " n'a pas pu être consulté.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                blocked.first().message ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                blocked.forEach { s ->
                    TextButton(onClick = { onOpenUrl(s.searchUrl) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Voir sur ${s.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: ResultsState, hasAlert: Boolean, onCreateAlert: () -> Unit) {
    val r = state.results
    val prices = r.map { it.listing.price }.sorted()
    val median = prices[prices.size / 2]
    val bestDeal = r.mapNotNull { it.dealPercent }.minOrNull()
    val x = LocalExtraColors.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "${r.size} annonce${if (r.size > 1) "s" else ""} retenue${if (r.size > 1) "s" else ""}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Prix médian", Format.price(median), Modifier.weight(1f))
                Stat("À partir de", Format.price(prices.first()), Modifier.weight(1f))
                Stat(
                    "Top affaire",
                    bestDeal?.let { if (it < 0) "−${-it} %" else "—" } ?: "—",
                    Modifier.weight(1f),
                    valueColor = if ((bestDeal ?: 0) < 0) x.great else null,
                )
            }
            if (!hasAlert && state.alertId == null) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onCreateAlert, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.NotificationAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("M'alerter des nouvelles annonces")
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun SortRow(sort: SortOrder, onSort: (SortOrder) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SortOrder.entries.forEach { o ->
            FilterChip(selected = sort == o, onClick = { onSort(o) }, label = { Text(o.label) })
        }
    }
}

@Composable
private fun SkeletonCard() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(10.dp)) {
            Box(Modifier.size(116.dp, 118.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf(0.9f, 0.5f, 0.7f, 0.4f).forEach { w ->
                    Box(Modifier.fillMaxWidth(w).height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                }
            }
        }
    }
}
