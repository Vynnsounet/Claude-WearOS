package com.medipote.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.TakeStatus
import com.medipote.app.domain.DoseItem
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Game
import com.medipote.app.domain.Schedule
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.Pill
import com.medipote.app.ui.components.TimePickerModal
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.fullDay
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

fun dayTitle(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(date, today)) {
    0L -> "Aujourd'hui"
    1L -> "Hier"
    2L -> "Avant-hier"
    else -> date.format(fullDay).capitalized()
}

/** Doses given to note afterwards, for a day that already started. */
fun forgottenDoses(state: AppState, date: LocalDate, now: LocalDateTime): List<DoseItem> =
    Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, date, now).filter { it.state == DoseState.MISSED }

/**
 * Past days, dose by dose: a dose taken but forgotten in the app can be noted with the time it
 * was taken, and a dose noted by mistake can be cancelled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoseHistoryScreen(
    state: AppState,
    date: LocalDate,
    now: LocalDateTime,
    onDate: (LocalDate) -> Unit,
    onCatchUp: (DoseItem, LocalDateTime) -> Unit,
    onUndo: (String) -> Unit,
    onBack: () -> Unit,
) {
    val today = now.toLocalDate()
    val oldest = today.minusDays(Game.CATCH_UP_DAYS)
    val items = remember(state.treatments, state.doseLogs, date, now) {
        Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, date, now)
    }
    var picking by remember { mutableStateOf<DoseItem?>(null) }
    var undoing by remember { mutableStateOf<DoseItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prises des jours passés") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDate(date.minusDays(1)) }, enabled = date.isAfter(oldest)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Jour précédent")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayTitle(date, today), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    if (ChronoUnit.DAYS.between(date, today) in 0..2) {
                        Text(date.format(fullDay).capitalized(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { onDate(date.plusDays(1)) }, enabled = date.isBefore(today)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Jour suivant")
                }
            }

            val taken = items.count { it.state == DoseState.TAKEN }
            if (items.isEmpty()) {
                Text("Aucune prise prévue ce jour-là.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("$taken / ${items.size} prises notées", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { taken.toFloat() / items.size },
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    drawStopIndicator = {},
                )
            }

            items.forEach { item -> HistoryCard(item, now, onTaken = { picking = item }, onUndo = { undoing = item }) }

            Disclaimer(
                "Une prise oubliée dans l'appli peut être notée jusqu'à ${Game.CATCH_UP_DAYS} jours en arrière : elle compte avec l'heure " +
                    "à laquelle tu l'as prise, et ton compagnon s'en remet. Si tu as vraiment oublié un médicament, suis la notice ou demande à ton pharmacien.",
            )
            Spacer(Modifier.size(8.dp))
        }
    }

    picking?.let { item ->
        val occ = item.occurrence
        TimePickerModal(
            minuteOfDay = occ.dose.minuteOfDay,
            title = "Pris à quelle heure ?",
            onDismiss = { picking = null },
        ) { m ->
            picking = null
            var at = occ.date.atTime(LocalTime.of(m / 60, m % 60))
            // A late-evening dose taken after midnight belongs to the next morning.
            if (m < occ.dose.minuteOfDay - 12 * 60) at = at.plusDays(1)
            onCatchUp(item, minOf(at, now))
        }
    }
    undoing?.let { item ->
        AlertDialog(
            onDismissRequest = { undoing = null },
            title = { Text("Annuler cette prise ?") },
            text = {
                Text(
                    "${item.occurrence.treatment.displayName} de ${item.occurrence.dose.timeLabel} redeviendra « non prise » : " +
                        "ses ${item.log?.points ?: 0} points sont retirés et la quantité revient dans le stock.",
                )
            },
            confirmButton = { TextButton(onClick = { undoing = null; onUndo(item.occurrence.key) }) { Text("Annuler la prise") } },
            dismissButton = { TextButton(onClick = { undoing = null }) { Text("Garder") } },
        )
    }
}

@Composable
private fun HistoryCard(item: DoseItem, now: LocalDateTime, onTaken: () -> Unit, onUndo: () -> Unit) {
    val occ = item.occurrence
    val t = occ.treatment
    val status = AppTheme.status
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(occ.dose.timeLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.displayName, style = MaterialTheme.typography.titleSmall)
                Text(t.unit.format(occ.quantity), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (item.state) {
                    DoseState.TAKEN -> item.log?.let { log ->
                        val (label, colors) = when (log.status) {
                            TakeStatus.ON_TIME -> "Pris à ${log.takenAt.format(hourMinute)} · +${log.points}" to (status.goodContainer to status.good)
                            else -> "Pris à ${log.takenAt.format(hourMinute)} · +${log.points}" to (status.warningContainer to status.warning)
                        }
                        Pill(label, container = colors.first, content = MaterialTheme.colorScheme.onSurface, icon = {
                            Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = colors.second)
                        })
                    }
                    DoseState.MISSED -> Pill("Non notée", container = status.criticalContainer, content = MaterialTheme.colorScheme.onSurface)
                    DoseState.UPCOMING -> Pill("À venir", container = MaterialTheme.colorScheme.surfaceContainerHighest, content = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Pill("À prendre", container = status.warningContainer, content = MaterialTheme.colorScheme.onSurface)
                }
            }
            when {
                item.state == DoseState.TAKEN -> TextButton(onClick = onUndo) { Text("Annuler") }
                item.state != DoseState.UPCOMING -> FilledTonalButton(onClick = onTaken) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Je l'ai pris")
                }
            }
        }
    }
}
