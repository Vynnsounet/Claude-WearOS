package com.radaroccaz.app.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radaroccaz.app.data.Alert
import com.radaroccaz.app.data.AlertFrequency
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.ui.components.EmptyState
import com.radaroccaz.app.ui.components.Pill
import com.radaroccaz.app.ui.theme.LocalExtraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    alerts: List<Alert>,
    checking: Boolean,
    notificationsAllowed: Boolean,
    onOpen: (Alert) -> Unit,
    onToggle: (Alert, Boolean) -> Unit,
    onFrequency: (Alert, AlertFrequency) -> Unit,
    onDelete: (Alert) -> Unit,
    onCheckNow: () -> Unit,
    onAllowNotifications: () -> Unit,
    onNewSearch: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mes alertes") },
                actions = {
                    if (alerts.any { it.enabled }) {
                        if (checking) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.5.dp)
                        else IconButton(onClick = onCheckNow) { Icon(Icons.Rounded.Refresh, contentDescription = "Vérifier maintenant") }
                    }
                },
            )
        },
    ) { padding ->
        if (alerts.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.NotificationsActive,
                title = "Aucune alerte pour l'instant",
                message = "Lancez une recherche, puis touchez la cloche : vous serez prévenu dès qu'une nouvelle annonce correspond à vos critères.",
                actionLabel = "Nouvelle recherche",
                onAction = onNewSearch,
                modifier = Modifier.padding(padding).padding(top = 40.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!notificationsAllowed) {
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Notifications désactivées : les nouveautés n'apparaîtront qu'ici.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onAllowNotifications) { Text("Activer") }
                        }
                    }
                }
            }
            items(alerts, key = { it.id }) { alert ->
                AlertCard(alert, now, onOpen, onToggle, onFrequency, onDelete)
            }
            item {
                Text(
                    "Les alertes sont vérifiées en arrière-plan quand le téléphone est connecté (au plus souvent toutes les 15 min, selon Android).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertCard(
    alert: Alert,
    now: Long,
    onOpen: (Alert) -> Unit,
    onToggle: (Alert, Boolean) -> Unit,
    onFrequency: (Alert, AlertFrequency) -> Unit,
    onDelete: (Alert) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val x = LocalExtraColors.current
    Surface(
        onClick = { onOpen(alert) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().testTag("alert_${alert.id}"),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(alert.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (alert.unread.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                Text("${alert.unread.size} nouvelle${if (alert.unread.size > 1) "s" else ""}")
                            }
                        }
                    }
                    Text(
                        alert.criteria.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = alert.enabled, onCheckedChange = { onToggle(alert, it) })
                Column {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Plus d'options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        AlertFrequency.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.label) },
                                leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                                trailingIcon = { if (f == alert.frequency) Text("✓") },
                                onClick = { onFrequency(alert, f); menu = false },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(alert.frequency.label, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer,
                    icon = Icons.Rounded.Schedule)
                Pill(
                    alert.lastCheckedAt?.let { "Vérifiée ${ago(now - it)}" } ?: "Pas encore vérifiée",
                    MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                if (alert.lastError != null) {
                    Pill("Sites indisponibles", x.weak, x.weakContainer, icon = Icons.Rounded.ErrorOutline)
                }
            }
            alert.unread.firstOrNull()?.let { l ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "Dernière : ${l.title} · ${Format.price(l.price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer l'alerte ?") },
            text = { Text("« ${alert.name} » ne sera plus vérifiée.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(alert) }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

fun ago(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "à l'instant"
        min < 60 -> "il y a $min min"
        min < 24 * 60 -> "il y a ${min / 60} h"
        else -> "il y a ${min / (24 * 60)} j"
    }
}

/** Name and frequency of a new alert. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateAlertDialog(
    defaultName: String,
    summary: String,
    onDismiss: () -> Unit,
    onCreate: (String, AlertFrequency) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    var frequency by remember { mutableStateOf(AlertFrequency.HOUR_1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.NotificationsActive, null) },
        title = { Text("Créer une alerte") },
        text = {
            Column {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nom de l'alerte") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Vérifier", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertFrequency.entries.forEach { f ->
                        FilterChip(selected = frequency == f, onClick = { frequency = f }, label = { Text(f.label) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Les annonces déjà affichées ne seront pas signalées.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, frequency) }, modifier = Modifier.testTag("confirm_alert")) { Text("Créer l'alerte") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
