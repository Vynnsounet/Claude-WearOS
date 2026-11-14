package com.medipote.app.ui.cycle

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.PeriodProduct
import com.medipote.app.domain.PeriodCare
import com.medipote.app.domain.Prn
import com.medipote.app.domain.WearLevel
import com.medipote.app.ui.components.NumberStepper
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDateTime

private fun hours(minutes: Int) = if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"

/**
 * When to change one's pad, tampon, cup, disc or period underwear: a timer from the moment it's
 * put on, a reminder, and the longest time it should be worn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodCareCard(
    state: AppState,
    now: LocalDateTime,
    onStart: (PeriodProduct) -> Unit,
    onChanged: () -> Unit,
    onRemoved: () -> Unit,
    onSetReminder: (PeriodProduct, Int) -> Unit,
    compact: Boolean = false,
) {
    val session = state.productSession
    val status = AppTheme.status
    var editing by remember { mutableStateOf(false) }
    val today = PeriodCare.usesOn(state, now.toLocalDate())
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (session == null) {
                Text("🩸 Ma protection", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Je viens de mettre :",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PeriodProduct.entries.forEach { p ->
                        AssistChip(onClick = { onStart(p) }, label = { Text("${p.emoji} ${p.label}") })
                    }
                }
                Text(
                    "Un minuteur démarre et te rappelle quand la changer." + if (today > 0) " Aujourd'hui : $today protection${if (today > 1) "s" else ""}." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val p = session.product
            val elapsed = PeriodCare.elapsed(session, now)
            val level = PeriodCare.level(session, now)
            val color = when (level) {
                WearLevel.OK -> MaterialTheme.colorScheme.primary
                WearLevel.CHANGE -> status.warning
                WearLevel.OVERDUE -> status.critical
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.emoji, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Mise en place à ${session.since.format(hourMinute)} · depuis ${Prn.formatDuration(elapsed)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { (elapsed.toMinutes().toFloat() / p.maxMinutes).coerceIn(0f, 1f) },
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            )
            Text(
                when (level) {
                    WearLevel.OK -> "À changer vers ${PeriodCare.changeAt(session).format(hourMinute)} · au plus tard ${PeriodCare.maxAt(session).format(hourMinute)}"
                    WearLevel.CHANGE -> "C'est le moment de changer ${p.yours} (au plus tard ${PeriodCare.maxAt(session).format(hourMinute)})."
                    WearLevel.OVERDUE -> "Plus de ${p.maxMinutes / 60} h : change ${p.yours} maintenant."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (level == WearLevel.OK) MaterialTheme.colorScheme.onSurface else color,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onChanged) { Text("J'ai changé") }
                OutlinedButton(onClick = onRemoved) { Text("J'ai retiré") }
                Spacer(Modifier.weight(1f))
                if (!compact) TextButton(onClick = { editing = true }) { Text("Rappel : ${hours(session.remindMinutes)}") }
            }
            if (!compact && p.internal) {
                Text(
                    "Avec une protection interne : en cas de fièvre, de malaise ou d'éruption, retire-la et appelle le 15. Respecte toujours la durée de la notice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!compact && today > 1) {
                Text("Aujourd'hui : $today protections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (editing && session != null) {
        val p = session.product
        var halfHours by remember { mutableIntStateOf(session.remindMinutes / 30) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Rappel pour ${p.yours}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberStepper("Après", halfHours, { halfHours = it }, 1..p.maxMinutes / 30, display = { hours(it * 30) })
                    Text(
                        "Au plus tard ${p.maxMinutes / 60} h pour ${p.label.lowercase()}, quoi qu'il arrive. Suis la notice de ta protection.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { editing = false; onSetReminder(p, halfHours * 30) }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Annuler") } },
        )
    }
}

/** Medication for painful periods: logged in two taps, with the reason already filled in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodMedsCard(state: AppState, now: LocalDateTime, onLog: (String) -> Unit, onAddCommon: (String) -> Unit) {
    val prn = state.treatments.filter { it.asNeeded && (it.endDate == null || !it.endDate.isBefore(now.toLocalDate())) }
    val todayIntakes = PeriodCare.intakesOn(state, now.toLocalDate())
    val missing = PeriodCare.COMMON_MEDS.filter { id -> prn.none { it.drugId == id } }.mapNotNull(DrugGuide::byId)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("💊 Un médicament pour tes règles ?", style = MaterialTheme.typography.titleMedium)
            prn.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.displayName, style = MaterialTheme.typography.bodyLarge)
                        Prn.last(state, t.id, now)?.let {
                            Text(
                                "Dernière prise ${com.medipote.app.ui.treatments.sinceLabel(it.takenAt, now)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FilledTonalButton(onClick = { onLog(t.id) }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Noter")
                    }
                }
            }
            if (missing.isNotEmpty()) {
                Text(
                    if (prn.isEmpty()) "Ajoute ce que tu prends quand tu as mal :" else "Ou ajoute :",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    missing.forEach { d ->
                        AssistChip(
                            onClick = { onAddCommon(d.id) },
                            label = { Text(if (d.id == "phloroglucinol") "${d.dci} (Spasfon)" else d.dci) },
                            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            if (todayIntakes.isNotEmpty()) {
                Text(
                    "Aujourd'hui : " + todayIntakes.joinToString(", ") { i ->
                        "${state.treatments.firstOrNull { it.id == i.treatmentId }?.name ?: "?"} à ${i.takenAt.format(hourMinute)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Notées avec le motif « ${PeriodCare.REASON} » : tu verras dans les stats si elles reviennent surtout pendant tes règles. " +
                    "Respecte la notice et demande conseil à ton pharmacien en cas de doute.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
