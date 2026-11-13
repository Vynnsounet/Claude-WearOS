package com.medipote.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.DayStats
import com.medipote.app.domain.Stats
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.dayMonth
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private fun pct(v: Float?) = v?.let { "${(it * 100).roundToInt()} %" } ?: "—"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: AppState, now: LocalDateTime, embedded: Boolean = false) {
    val days = remember(state, now.toLocalDate(), now.hour) { Stats.daily(state, now, 14) }
    val summary = remember(state, now.toLocalDate(), now.hour) { Stats.summary(state, now) }
    val perTreatment = remember(state, now.toLocalDate()) { Stats.perTreatment(state, now) }

    Scaffold(
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!embedded) TopAppBar(
                title = { Text("Statistiques") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Observance 7 j", pct(summary.adherence7), "prises données", Modifier.weight(1f))
                StatTile("Observance 30 j", pct(summary.adherence30), "prises données", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Ponctualité", pct(summary.punctuality30), "à l'heure sur 30 j", Modifier.weight(1f))
                StatTile("Série", "${state.game.streak} j", "record : ${state.game.bestStreak} j", Modifier.weight(1f))
            }

            ChartCard(days)
            BreakdownCard(summary.onTime30, summary.late30, summary.missed30)

            if (perTreatment.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Par traitement · 30 jours", style = MaterialTheme.typography.titleMedium)
                        perTreatment.forEach { t ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (t.planned == 0) "—" else "${t.taken}/${t.planned} · ${pct(t.adherence)}",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { if (t.planned == 0) 0f else t.adherence },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                    drawStopIndicator = {},
                                )
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Points", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiniValue("Solde", "${state.game.points}", Modifier.weight(1f))
                        MiniValue("Gagnés", "${state.game.totalEarned}", Modifier.weight(1f))
                        MiniValue("Dépensés", "${(state.game.totalEarned - state.game.points).coerceAtLeast(0)}", Modifier.weight(1f))
                    }
                    if (state.game.history.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("Derniers gains", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.game.history.take(8).forEach { e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${e.at.format(dayMonth)} ${e.at.format(hourMinute)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(96.dp),
                                )
                                Text(e.reason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (if (e.amount >= 0) "+" else "") + e.amount,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (e.amount >= 0) AppTheme.status.good else AppTheme.status.critical,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, caption: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Legend(items: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Stacked bars for the last 14 days; tap a bar to read its numbers. */
@Composable
private fun ChartCard(days: List<DayStats>) {
    val status = AppTheme.status
    val onTimeColor = status.good
    val lateColor = status.warning
    val missedColor = status.critical
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    var selected by remember(days) { mutableIntStateOf(days.lastIndex) }
    val maxPlanned = (days.maxOfOrNull { it.planned } ?: 0).coerceAtLeast(1)
    val description = days.joinToString("; ") { "${it.date.format(dayMonth)} : ${it.onTime} à l'heure, ${it.late} décalées, ${it.missed} oubliées" }

    Card(colors = CardDefaults.cardColors(containerColor = surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("14 derniers jours", style = MaterialTheme.typography.titleMedium)
            Legend(listOf("À l'heure" to onTimeColor, "Décalées" to lateColor, "Oubliées" to missedColor))
            val sel = days.getOrNull(selected)
            Text(
                sel?.let {
                    val d = it.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)).capitalized()
                    val parts = listOfNotNull(
                        "${it.onTime} à l'heure".takeIf { _ -> it.onTime > 0 },
                        "${it.late} décalée${if (it.late > 1) "s" else ""}".takeIf { _ -> it.late > 0 },
                        "${it.missed} oubliée${if (it.missed > 1) "s" else ""}".takeIf { _ -> it.missed > 0 },
                        "${it.pending} à venir".takeIf { _ -> it.pending > 0 },
                    )
                    if (it.planned == 0) "$d · aucune prise prévue" else "$d · ${parts.joinToString(", ")}"
                } ?: "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .semantics { contentDescription = description }
                    .pointerInput(days) {
                        detectTapGestures { pos ->
                            val slot = size.width / days.size.toFloat()
                            selected = (pos.x / slot).toInt().coerceIn(0, days.lastIndex)
                        }
                    },
            ) {
                val slot = size.width / days.size
                val barW = slot * 0.56f
                val bottom = size.height
                // Recessive gridlines at 50 % and 100 % of the busiest day.
                for (f in listOf(0.5f, 1f)) {
                    val y = bottom - bottom * f * 0.96f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                days.forEachIndexed { i, d ->
                    val x = i * slot + (slot - barW) / 2
                    if (i == selected) drawRect(highlight, Offset(i * slot, 0f), Size(slot, bottom))
                    val unit = bottom * 0.96f / maxPlanned
                    var y = bottom
                    val segments = listOf(d.onTime to onTimeColor, d.late to lateColor, d.missed to missedColor).filter { it.first > 0 }
                    segments.forEachIndexed { si, (count, color) ->
                        val h = count * unit
                        val top = y - h
                        val gap = if (si > 0) 2.dp.toPx() else 0f
                        drawSegment(Offset(x, top), Size(barW, h - gap), color, roundTop = si == segments.lastIndex)
                        y = top
                    }
                    if (d.pending > 0) {
                        val h = d.pending * unit
                        drawSegment(Offset(x, y - h), Size(barW, h - 2.dp.toPx()), grid, roundTop = true)
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, d ->
                    Text(
                        if (i % 2 == days.lastIndex % 2) d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.FRANCE) + d.date.dayOfMonth else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawSegment(topLeft: Offset, size: Size, color: Color, roundTop: Boolean) {
    if (size.height <= 0f) return
    val r = if (roundTop) minOf(4.dp.toPx(), size.width / 2, size.height) else 0f
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = topLeft.x, top = topLeft.y, right = topLeft.x + size.width, bottom = topLeft.y + size.height,
                topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, color)
}

@Composable
private fun BreakdownCard(onTime: Int, late: Int, missed: Int) {
    val status = AppTheme.status
    val total = onTime + late + missed
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    Card(colors = CardDefaults.cardColors(containerColor = surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Répartition sur 30 jours", style = MaterialTheme.typography.titleMedium)
            if (total == 0) {
                Text(
                    "Les statistiques apparaîtront après tes premières prises.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Canvas(
                Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp))
                    .semantics { contentDescription = "$onTime à l'heure, $late décalées, $missed oubliées" },
            ) {
                var x = 0f
                listOf(onTime to status.good, late to status.warning, missed to status.critical).filter { it.first > 0 }.forEach { (n, c) ->
                    val w = size.width * n / total
                    drawRect(c, Offset(x, 0f), Size((w - 2.dp.toPx()).coerceAtLeast(1f), size.height))
                    x += w
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Count("À l'heure", onTime, total, status.good, Modifier.weight(1f))
                Count("Décalées", late, total, status.warning, Modifier.weight(1f))
                Count("Oubliées", missed, total, status.critical, Modifier.weight(1f))
            }
            Text(
                "Décalées : prises en retard, ou notées plus d'une heure en avance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Count(label: String, n: Int, total: Int, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp)) { drawCircle(color) }
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$n · ${(n * 100f / total).roundToInt()} %", style = MaterialTheme.typography.titleSmall)
    }
}
