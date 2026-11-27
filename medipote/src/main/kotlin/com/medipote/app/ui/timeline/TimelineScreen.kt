package com.medipote.app.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.BeforeAfter
import com.medipote.app.domain.Change
import com.medipote.app.domain.DayPoint
import com.medipote.app.domain.EventKind
import com.medipote.app.domain.Timeline
import com.medipote.app.domain.TimelineEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Span(val label: String, val days: Int) { MONTH("1 mois", 30), THREE("3 mois", 90), SIX("6 mois", 180), YEAR("1 an", 365) }

private val MONTH_TITLE = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.FRANCE)

/**
 * « Ma période »: one line of time with the sleep, the mood and the symptoms of each day, the treatment changes, the blood tests and
 * the hard stretches marked on it, and under it the list of what happened. A treatment change opens its « avant / après ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(state: AppState, today: LocalDate, onOpenLab: (String) -> Unit = {}, onOpenTreatment: (String) -> Unit = {}) {
    var span by rememberSaveable { mutableStateOf(Span.THREE) }
    val from = today.minusDays(span.days - 1L)
    val points = remember(state, today, span) { Timeline.series(state, from, today) }
    val events = remember(state, today, span) { Timeline.events(state, from, today) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Span.entries.forEach { s -> FilterChip(selected = span == s, onClick = { span = s }, label = { Text(s.label) }) }
        }
        Text(Timeline.summary(events), style = MaterialTheme.typography.titleMedium)
        Strip(points, events, from, today)
        if (events.isEmpty()) {
            Text(
                "Rien de marquant sur cette période. Les débuts et changements de traitement, tes analyses, tes rendez-vous, tes maladies et tes périodes difficiles apparaîtront ici.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        var month = ""
        events.forEach { e ->
            val title = e.date.format(MONTH_TITLE).replaceFirstChar { it.uppercase() }
            if (title != month) { month = title; Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            EventRow(state, e, today, onOpenLab, onOpenTreatment)
        }
        Text(
            "Ce sont des repères tirés de tes notes. Plusieurs choses changent souvent en même temps : ça ne dit pas laquelle agit. À montrer à ton médecin si tu veux.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sleep as bars, mood as dots, symptoms as a heat line, and a mark for each event, all on the same days. */
@Composable
private fun Strip(points: List<DayPoint>, events: List<TimelineEvent>, from: LocalDate, today: LocalDate) {
    val bar = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val dot = MaterialTheme.colorScheme.tertiary
    val hot = MaterialTheme.colorScheme.error
    val grid = MaterialTheme.colorScheme.outlineVariant
    val treatment = MaterialTheme.colorScheme.primary
    val lab = MaterialTheme.colorScheme.secondary
    val other = MaterialTheme.colorScheme.onSurfaceVariant
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(170.dp).semantics {
                    contentDescription = "Frise : sommeil, moral et symptômes jour par jour, avec les événements marqués"
                },
            ) {
                val n = points.size.coerceAtLeast(1)
                val w = size.width / n
                // the events, on top
                events.forEach { e ->
                    val i = java.time.temporal.ChronoUnit.DAYS.between(from, e.date).toInt().coerceIn(0, n - 1)
                    val color = when (e.kind) {
                        EventKind.TREATMENT_START, EventKind.TREATMENT_END, EventKind.DOSE_CHANGE -> treatment
                        EventKind.LAB -> lab
                        else -> other
                    }
                    drawLine(color, Offset(i * w + w / 2, 14f), Offset(i * w + w / 2, size.height), strokeWidth = 2f)
                    drawCircle(color, 7f, Offset(i * w + w / 2, 10f))
                }
                val top = 24f
                val sleepH = 70.dp.toPx()
                val moodTop = top + sleepH + 10f
                val moodH = 40.dp.toPx()
                val heatTop = moodTop + moodH + 8f
                drawLine(grid, Offset(0f, top + sleepH), Offset(size.width, top + sleepH), strokeWidth = 1f)
                points.forEachIndexed { i, p ->
                    p.sleepMinutes?.let { m ->
                        val h = (m / 600f).coerceAtMost(1f) * sleepH
                        drawRoundRect(bar, Offset(i * w + w * 0.15f, top + sleepH - h), Size((w * 0.7f).coerceAtLeast(1f), h), CornerRadius(2f))
                    }
                    p.mood?.let { m ->
                        val y = moodTop + moodH - (m - 1) / 4f * moodH
                        drawCircle(dot, (w * 0.35f).coerceIn(1.5f, 5f), Offset(i * w + w / 2, y))
                    }
                    p.symptom?.let { s ->
                        if (s > 0) drawRect(hot.copy(alpha = 0.15f + 0.17f * s), Offset(i * w, heatTop), Size(w.coerceAtLeast(1f), 14.dp.toPx()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(Timeline.dayText(from), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(Timeline.dayText(today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Barres : sommeil · points : moral · bande rouge : symptômes · repères : traitements, analyses, événements", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventRow(state: AppState, e: TimelineEvent, today: LocalDate, onOpenLab: (String) -> Unit, onOpenTreatment: (String) -> Unit) {
    var open by rememberSaveable("${e.date}${e.title}") { mutableStateOf(false) }
    val comparison = remember(state, e, today) { if (Timeline.hasComparison(e)) Timeline.beforeAfter(state, e, today) else null }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(e.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        Timeline.dayText(e.date) + if (e.detail.isNotBlank()) " · ${e.detail}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (Timeline.hasComparison(e)) {
                if (comparison != null) {
                    TextButton(onClick = { open = !open }) { Text(if (open) "Masquer avant / après" else "Voir avant / après") }
                    if (open) BeforeAfterTable(comparison)
                } else {
                    Text("Avant / après : trop récent, ou pas assez de jours notés autour de cette date.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (e.kind == EventKind.LAB) TextButton(onClick = { onOpenLab(e.ref) }) { Text("Voir la courbe") }
            if (Timeline.hasComparison(e) && e.ref.isNotBlank() && state.treatments.any { it.id == e.ref }) TextButton(onClick = { onOpenTreatment(e.ref) }) { Text("Voir le traitement") }
        }
    }
}

@Composable
private fun BeforeAfterTable(b: BeforeAfter) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${b.beforeDays} jours avant → ${b.afterDays} jours depuis", style = MaterialTheme.typography.labelLarge)
        b.rows.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${r.before}  →  ${r.after}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (r.change) { Change.BETTER -> "▲"; Change.WORSE -> "▼"; Change.SAME -> "＝" },
                    color = when (r.change) { Change.BETTER -> MaterialTheme.colorScheme.primary; Change.WORSE -> MaterialTheme.colorScheme.error; Change.SAME -> MaterialTheme.colorScheme.onSurfaceVariant },
                    modifier = Modifier.semantics { contentDescription = when (r.change) { Change.BETTER -> "mieux"; Change.WORSE -> "moins bien"; Change.SAME -> "stable" } },
                )
            }
        }
        Text("Moyennes de ce qui a été noté : d'autres choses ont pu changer en même temps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
