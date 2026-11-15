package com.medipote.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Nap
import com.medipote.app.data.NapFeeling
import com.medipote.app.domain.Naps
import com.medipote.app.domain.Sleep
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.MoreOptions
import com.medipote.app.ui.components.TimePickerModal
import com.medipote.app.ui.components.hourMinute
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** "Sieste en cours": how long it has been, and one button to wake up. */
@Composable
fun NapTimerCard(since: LocalDateTime, now: LocalDateTime, onWake: () -> Unit, onCancel: () -> Unit, compact: Boolean = false) {
    val minutes = Naps.elapsedMinutes(since, now)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Sieste en cours depuis $minutes minutes" },
    ) {
        Column(Modifier.padding(if (compact) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("😴", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sieste en cours", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "depuis ${since.format(hourMinute)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
                Text(Sleep.format(minutes), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onWake, modifier = Modifier.weight(1f)) { Text("Je me réveille") }
                TextButton(onClick = onCancel) { Text("Annuler") }
            }
        }
    }
}

/** The two ways to note a nap, side by side. */
@Composable
fun NapActions(onStart: () -> Unit, onLog: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(onClick = onStart, modifier = Modifier.weight(1f)) { Text("😴 Je m'endors") }
        OutlinedButton(onClick = onLog, modifier = Modifier.weight(1f)) { Text("Noter une sieste") }
    }
}

/** "😴 Sieste · 14:10 · 25 min": one line, touch to edit. */
@Composable
fun NapLine(nap: Nap, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("😴", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Text(
                "Sieste · ${nap.start.format(hourMinute)} · ${Sleep.format(nap.minutes)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            nap.feeling?.let { Text(it.emoji, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** Noting a nap: how long, when it began, and (optionally) how it left you. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NapEditorScreen(
    existing: Nap?,
    initialStart: LocalDateTime?,
    initialMinutes: Int?,
    now: LocalDateTime,
    onSave: (Nap) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onNight: () -> Unit = {},
) {
    // By default: a nap that just ended, 30 minutes long.
    val startDefault = initialStart ?: now.minusMinutes(30).withSecond(0).withNano(0).let { it.withMinute(it.minute / 5 * 5) }
    val first = existing?.start ?: startDefault
    var day by rememberSaveable { mutableStateOf(first.toLocalDate()) }
    var startMinute by rememberSaveable { mutableIntStateOf(first.hour * 60 + first.minute) }
    var minutes by rememberSaveable { mutableIntStateOf((existing?.minutes ?: initialMinutes ?: 30).coerceIn(Naps.MIN_MINUTES, Naps.MAX_MINUTES)) }
    var feeling by rememberSaveable { mutableStateOf(existing?.feeling) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val start = day.atTime(startMinute / 60, startMinute % 60)
    val problem = when {
        start.plusMinutes(minutes.toLong()).isAfter(now.plusMinutes(1)) -> "Une sieste ne peut pas finir dans le futur : avance son début ou raccourcis-la."
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Noter une sieste" else "Modifier la sieste") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onSave(Nap(existing?.id ?: UUID.randomUUID().toString(), start, minutes, feeling, notes.trim())) },
                    enabled = problem == null,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // How long: big, with steps and shortcuts.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Durée", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    FilledTonalIconButton(onClick = { minutes = (minutes - 5).coerceAtLeast(Naps.MIN_MINUTES) }, enabled = minutes > Naps.MIN_MINUTES, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Remove, "5 minutes de moins")
                    }
                    Text(Sleep.format(minutes), style = MaterialTheme.typography.displaySmall)
                    FilledTonalIconButton(onClick = { minutes = (minutes + 5).coerceAtMost(Naps.MAX_MINUTES) }, enabled = minutes < Naps.MAX_MINUTES, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Add, "5 minutes de plus")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Naps.SHORTCUTS.forEach { m ->
                        FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(Sleep.format(m)) })
                    }
                }
                if (minutes >= Naps.MAX_MINUTES) {
                    TextButton(onClick = onNight) { Text("Plus long ? Note plutôt une nuit") }
                }
            }

            OutlinedCard(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Début", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%02d:%02d".format(startMinute / 60, startMinute % 60), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Et après ?", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NapFeeling.entries.forEach { f ->
                        FilterChip(
                            selected = feeling == f,
                            onClick = { feeling = if (feeling == f) null else f },
                            label = { Text("${f.emoji} ${f.label}") },
                        )
                    }
                }
            }

            MoreOptions("jour, note", initiallyOpen = notes.isNotBlank() || day != now.toLocalDate()) {
                DateField("Jour", day, { day = it }, maxDate = now.toLocalDate())
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text("Note (facultatif)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (picking) {
        TimePickerModal(startMinute, onDismiss = { picking = false }, title = "Début de la sieste") { startMinute = it; picking = false }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette sieste ?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}
