package com.medipote.app.ui.wellbeing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.medipote.app.data.SplitTask
import com.medipote.app.domain.Wellbeing
import com.medipote.app.domain.WellbeingLibrary
import kotlinx.coroutines.delay

/**
 * Cutting a task into small steps, guided by questions: what is it, what kind, what gets in the way.
 * The person keeps what helps and adds their own; nothing is decided for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(onSave: (String, List<String>) -> Unit, onBack: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var kindId by rememberSaveable { mutableStateOf<String?>(null) }
    var blockers by rememberSaveable(stateSaver = listSaver(save = { it }, restore = { it })) { mutableStateOf(emptyList<String>()) }
    var own by rememberSaveable(stateSaver = listSaver(save = { it }, restore = { it })) { mutableStateOf(emptyList<String>()) }
    var picked by rememberSaveable(stateSaver = listSaver(save = { it }, restore = { it })) { mutableStateOf(emptyList<String>()) }
    var newStep by rememberSaveable { mutableStateOf("") }

    val kind = WellbeingLibrary.TASK_KINDS.firstOrNull { it.id == kindId }
    val blockerSteps = WellbeingLibrary.BLOCKERS.filter { it.id in blockers }.map { it.step }
    val suggestions = (blockerSteps + (kind?.steps ?: emptyList()) + own).distinct()

    // A kind (or a blocker) picked: its first steps are ticked, the rest are left to choose.
    LaunchedEffect(kindId, blockers) {
        val first = (blockerSteps + (kind?.steps ?: emptyList())).distinct().take(4)
        picked = (first + own.filter { it in picked }).distinct()
    }

    WbPage("Découper une tâche", embedded = false, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Qu'est-ce que tu dois faire ?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                title, { title = it.take(120) },
                label = { Text("Par exemple : faire ma lessive") }, modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ça ressemble plutôt à…", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WellbeingLibrary.TASK_KINDS.forEach { k ->
                    FilterChip(selected = kindId == k.id, onClick = { kindId = k.id }, label = { Text("${k.emoji}  ${k.label}") })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Qu'est-ce qui bloque ? (si tu veux)", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WellbeingLibrary.BLOCKERS.forEach { b ->
                    FilterChip(
                        selected = b.id in blockers,
                        onClick = { blockers = if (b.id in blockers) blockers - b.id else blockers + b.id },
                        label = { Text(b.label) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Les étapes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Garde celles qui t'aident, ajoute les tiennes. 💛 Commencer est le plus dur : la première étape rapporte un peu plus de points.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (suggestions.isEmpty()) {
                Text("Choisis un type de tâche ci-dessus, ou écris tes propres étapes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            suggestions.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s in picked, onCheckedChange = { on -> picked = if (on) picked + s else picked - s })
                    Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    newStep, { newStep = it.take(120) },
                    label = { Text("Ma propre étape") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        val text = newStep.trim()
                        if (text.isNotEmpty() && text !in own) {
                            own = own + text
                            picked = picked + text
                        }
                        newStep = ""
                    },
                    enabled = newStep.isNotBlank(),
                ) { Text("Ajouter") }
            }
        }

        // Kept in the order shown: the first one ticked is the first step.
        val steps = suggestions.filter { it in picked }
        Button(
            onClick = { onSave(title, steps) },
            enabled = title.isNotBlank() && steps.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Enregistrer · ${steps.size} étape${if (steps.size > 1) "s" else ""}") }
    }
}

/** A task and its steps: tick them one by one, with a five-minute timer if it helps to start. */
@Composable
fun TaskScreen(task: SplitTask?, onTick: (Int) -> Unit, onDelete: () -> Unit, onBack: () -> Unit, animate: Boolean = true) {
    if (task == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var confirm by remember { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(false) }
    var left by rememberSaveable { mutableIntStateOf(FIVE_MINUTES) }
    LaunchedEffect(running, animate) {
        if (!running || !animate) return@LaunchedEffect
        while (left > 0) {
            delay(1000)
            left--
        }
        running = false
    }
    val firstOpen = task.steps.indexOfFirst { !it.done }

    WbPage(task.title, embedded = false, onBack = onBack) {
        if (task.finished) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Text("🎉  Tout est fait, bravo !", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${task.doneCount} sur ${task.steps.size} étapes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { task.doneCount / task.steps.size.toFloat() },
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            task.steps.forEachIndexed { i, step ->
                Surface(
                    color = if (i == firstOpen) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (i == firstOpen) Text(if (i == 0) "La toute première étape" else "Prochaine étape", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                step.text,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (step.done) TextDecoration.LineThrough else null,
                                color = if (step.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (step.done) {
                            Icon(Icons.Rounded.Check, "Fait", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
                        } else {
                            FilledTonalButton(onClick = { onTick(i) }) {
                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fait")
                            }
                        }
                    }
                }
            }
        }

        if (!task.finished) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⏱️  Cinq minutes seulement", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            running -> "%d:%02d".format(left / 60, left % 60)
                            left == 0 -> "Cinq minutes, c'est fait. Tu peux t'arrêter ou continuer. 💛"
                            else -> "Un petit chrono pour commencer. Quand il sonne, tu peux t'arrêter sans culpabiliser."
                        },
                        style = if (running) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyMedium,
                        color = if (running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = {
                        if (running) { running = false } else { if (left == 0) left = FIVE_MINUTES; running = true }
                    }) { Text(if (running) "Pause" else if (left == FIVE_MINUTES) "Lancer 5 minutes" else "Reprendre") }
                }
            }
        }

        TextButton(onClick = { confirm = true }) { Text("Supprimer cette tâche") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Supprimer cette tâche ?") },
            text = { Text("Les points déjà gagnés sont gardés.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Garder") } },
        )
    }
}

private const val FIVE_MINUTES = 300
