package com.medipote.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medipote.app.domain.DayStats
import com.medipote.app.domain.Insight
import com.medipote.app.domain.Rate
import com.medipote.app.domain.Tone
import com.medipote.app.ui.theme.AppTheme
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

internal fun pct(v: Float?) = v?.let { "${(it * 100).roundToInt()} %" } ?: "—"

internal fun decimal(v: Float, digits: Int = 1) = "%.${digits}f".format(Locale.FRANCE, v)

@Composable
fun StatCard(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, caption: String?, modifier: Modifier = Modifier, captionColor: Color? = null) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            caption?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = captionColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PeriodSelector(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
fun InsightsCard(insights: List<Insight>, title: String = "Ce qu'on remarque") {
    if (insights.isEmpty()) return
    val status = AppTheme.status
    StatCard(title) {
        insights.forEach { insight ->
            val (icon, tint) = when (insight.tone) {
                Tone.GOOD -> Icons.Rounded.ThumbUp to status.good
                Tone.WARN -> Icons.Rounded.WarningAmber to status.warning
                Tone.INFO -> Icons.Rounded.Info to MaterialTheme.colorScheme.primary
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.size(18.dp).padding(top = 1.dp), tint = tint)
                Text(insight.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Single-series vertical bars. Tapping a bar shows its value in the line above the chart.
 * [axisLabels] maps a bar index to the label drawn under it (only a few, so they never collide).
 */
@Composable
fun BarChart(
    values: List<Float>,
    axisLabels: Map<Int, String>,
    describe: (Int) -> String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 132.dp,
    initialSelected: Int = values.lastIndex,
    maxValue: Float? = null,
    gridLabel: ((Float) -> String)? = null,
    accessibility: String = "",
) {
    if (values.isEmpty()) return
    var selected by remember(values) { mutableIntStateOf(initialSelected.coerceIn(0, values.lastIndex)) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val muted = color.copy(alpha = 0.55f)
    val top = (maxValue ?: values.max()).coerceAtLeast(0.0001f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(describe(selected), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.Top) {
            Canvas(
                Modifier
                    .weight(1f)
                    .height(height)
                    .semantics { contentDescription = accessibility.ifEmpty { values.indices.joinToString("; ") { describe(it) } } }
                    .pointerInput(values) {
                        detectTapGestures { pos ->
                            val slot = size.width / values.size.toFloat()
                            selected = (pos.x / slot).toInt().coerceIn(0, values.lastIndex)
                        }
                    },
            ) {
                val slot = size.width / values.size
                val barW = (slot * 0.6f).coerceAtMost(24.dp.toPx())
                val bottom = size.height
                for (f in listOf(0.5f, 1f)) {
                    val y = bottom - bottom * f * 0.94f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                drawLine(grid, Offset(0f, bottom - 0.5f), Offset(size.width, bottom - 0.5f), 1.dp.toPx())
                values.forEachIndexed { i, v ->
                    if (i == selected) drawRect(highlight, Offset(i * slot, 0f), Size(slot, bottom))
                    if (v <= 0f) return@forEachIndexed
                    val h = (bottom * 0.94f * v / top).coerceAtLeast(2.dp.toPx())
                    val x = i * slot + (slot - barW) / 2
                    val r = minOf(4.dp.toPx(), barW / 2, h)
                    // Rounded top, square bottom sitting on the baseline.
                    val path = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                x, bottom - h, x + barW, bottom,
                                topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                            ),
                        )
                    }
                    drawPath(path, if (i == selected) color else muted)
                }
            }
            gridLabel?.let { fmt ->
                Box(Modifier.height(height).widthIn(min = 28.dp).padding(start = 6.dp)) {
                    Text(fmt(top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.TopStart))
                    Text(fmt(top / 2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterStart))
                }
            }
        }
        AxisLabels(values.size, axisLabels, if (gridLabel != null) 34.dp else 0.dp)
    }
}

@Composable
private fun AxisLabels(count: Int, labels: Map<Int, String>, endPadding: Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(end = endPadding).height(16.dp)) {
        val slot = maxWidth / count
        val w = 64.dp
        labels.forEach { (i, label) ->
            val x = (slot * i + slot / 2 - w / 2).coerceIn(0.dp, (maxWidth - w).coerceAtLeast(0.dp))
            val align = when {
                slot * i + slot / 2 - w / 2 < 0.dp -> TextAlign.Start
                slot * i + slot / 2 + w / 2 > maxWidth -> TextAlign.End
                else -> TextAlign.Center
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                textAlign = align,
                modifier = Modifier.width(w).offset(x = x),
            )
        }
    }
}

/** Horizontal bars for a ranked list of counts (reasons, contexts…). */
@Composable
fun RankedBars(items: List<Pair<String, Int>>, total: Int, modifier: Modifier = Modifier, max: Int = 6) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val top = items.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.take(max).forEach { (label, n) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$n · ${(n * 100f / total.coerceAtLeast(1)).roundToInt()} %", style = MaterialTheme.typography.labelLarge)
                }
                Meter(n.toFloat() / top, color, track)
            }
        }
    }
}

/** One row per group with its rate, e.g. "Matin · 18/20 · 90 %". */
@Composable
fun RateRows(rows: List<Pair<String, Rate>>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, rate) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f)) { Meter(rate.value ?: 0f, color, track) }
                Text(
                    if (rate.decided == 0) "—" else pct(rate.value),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
    }
}

@Composable
fun Meter(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
        drawRect(track)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, Offset.Zero, Size(w.coerceAtLeast(size.height), size.height), CornerRadius(size.height / 2))
    }
}

/**
 * Calendar of the last 12 weeks, one column per week (Monday on top).
 * Single hue: the darker the cell, the more doses were taken that day.
 */
@Composable
fun AdherenceHeatmap(days: List<DayStats>, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val ring = MaterialTheme.colorScheme.onSurface
    val first = days.first().date
    val lead = first.dayOfWeek.value - 1
    val cells = List(lead) { null } + days
    val weeks = (cells.size + 6) / 7
    var selected by remember(days) { mutableIntStateOf(days.lastIndex) }
    val sel = days[selected]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            buildString {
                append(sel.date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)).replaceFirstChar { it.uppercase() })
                append(" · ")
                append(
                    when {
                        sel.planned == 0 -> "aucune prise prévue"
                        sel.taken + sel.missed == 0 -> "${sel.pending} à venir"
                        else -> "${sel.taken}/${sel.taken + sel.missed} prise${if (sel.taken > 1) "s" else ""}"
                    },
                )
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row {
            Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (1..7).forEach { d ->
                    Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (d % 2 == 1) java.time.DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.FRANCE) else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(18.dp * 7 + 3.dp * 6)
                    .semantics { contentDescription = "Calendrier des prises sur 12 semaines" }
                    .pointerInput(days) {
                        detectTapGestures { pos ->
                            val colW = size.width / weeks.toFloat()
                            val rowH = size.height / 7f
                            val i = (pos.x / colW).toInt() * 7 + (pos.y / rowH).toInt() - lead
                            if (i in days.indices) selected = i
                        }
                    },
            ) {
                val gap = 3.dp.toPx()
                val colW = size.width / weeks
                val cell = minOf(colW - gap, (size.height - gap * 6) / 7)
                cells.forEachIndexed { index, d ->
                    if (d == null) return@forEachIndexed
                    val x = (index / 7) * colW + (colW - cell) / 2
                    val y = (index % 7) * (cell + gap)
                    val decided = d.taken + d.missed
                    val fill = when {
                        decided == 0 -> empty
                        else -> color.copy(alpha = 0.18f + 0.82f * d.taken / decided)
                    }
                    drawRoundRect(fill, Offset(x, y), Size(cell, cell), CornerRadius(3.dp.toPx()))
                    if (index - lead == selected) {
                        val inset = 1.dp.toPx()
                        drawRoundRect(
                            ring,
                            Offset(x - inset, y - inset),
                            Size(cell + 2 * inset, cell + 2 * inset),
                            CornerRadius(4.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.weight(1f))
            Text("Moins", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0.18f, 0.45f, 0.72f, 1f).forEach { a ->
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).let { m -> m }) {
                    Canvas(Modifier.size(10.dp)) { drawRect(color.copy(alpha = a)) }
                }
            }
            Text("Plus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
