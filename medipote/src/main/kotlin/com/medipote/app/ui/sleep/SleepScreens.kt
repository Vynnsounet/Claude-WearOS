package com.medipote.app.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.Awakening
import com.medipote.app.data.SleepNight
import com.medipote.app.domain.Naps
import com.medipote.app.domain.Reports
import com.medipote.app.domain.Rules
import com.medipote.app.domain.Sleep
import com.medipote.app.domain.SleepReport
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.TimePickerModal
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.dayMonth
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.stats.BarChart
import com.medipote.app.ui.stats.InsightsCard
import com.medipote.app.ui.stats.PeriodSelector
import com.medipote.app.ui.stats.RankedBars
import com.medipote.app.ui.stats.StatCard
import com.medipote.app.ui.stats.StatTile
import com.medipote.app.ui.stats.decimal
import com.medipote.app.ui.stats.pct
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private val nightTitle = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)
private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)

/** "Nuit du 11 au 12 novembre" */
fun nightLabel(date: LocalDate): String {
    val before = date.minusDays(1)
    return if (before.month == date.month) "Nuit du ${before.dayOfMonth} au ${date.format(nightTitle)}"
    else "Nuit du ${before.format(nightTitle)} au ${date.format(nightTitle)}"
}

@Composable
private fun sleepColor() = MaterialTheme.colorScheme.primary

/**
 * Awake time must stand out from sleep whatever the accent: the theme's second colour when it is
 * clearly different, else the most different of a few warm and cool alternatives.
 */
@Composable
private fun awakeColor(): androidx.compose.ui.graphics.Color =
    distinctFrom(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)

private val AWAKE_ALTERNATIVES = listOf(0xFFFFB300, 0xFFFB8C00, 0xFF4FC3F7, 0xFFF06292, 0xFF26A69A, 0xFFFFE082)
    .map { androidx.compose.ui.graphics.Color(it) }

/** Perceptual-ish distance between two colours ("redmean"), 0 for the same colour up to about 765. */
internal fun colorDistance(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
    val r1 = a.red * 255f; val g1 = a.green * 255f; val b1 = a.blue * 255f
    val r2 = b.red * 255f; val g2 = b.green * 255f; val b2 = b.blue * 255f
    val rmean = (r1 + r2) / 2f
    val dr = r1 - r2; val dg = g1 - g2; val db = b1 - b2
    return kotlin.math.sqrt((2f + rmean / 256f) * dr * dr + 4f * dg * dg + (2f + (255f - rmean) / 256f) * db * db)
}

internal const val MIN_AWAKE_DISTANCE = 220f

/** [preferred] when it differs enough from [from], else the most different alternative. */
internal fun distinctFrom(from: androidx.compose.ui.graphics.Color, preferred: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color =
    if (colorDistance(from, preferred) >= MIN_AWAKE_DISTANCE) preferred else AWAKE_ALTERNATIVES.maxBy { colorDistance(from, it) }

/** The night from bedtime to waking up: asleep in the accent colour, awake (falling asleep, awakenings) in a second hue. */
@Composable
fun NightTimeline(night: SleepNight, modifier: Modifier = Modifier, height: Int = 22, labels: Boolean = true) {
    val asleep = sleepColor()
    val awake = awakeColor()
    val gapColor = MaterialTheme.colorScheme.surfaceContainerLow
    val segments = remember(night) { Sleep.segments(night) }
    val total = night.inBedMinutes.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape((height / 3).dp))
                .semantics {
                    contentDescription = if (night.noSleep) {
                        "Nuit blanche, au lit ${night.bedtime.format(hourMinute)}, levé ou levée ${night.wakeTime.format(hourMinute)}"
                    } else {
                        "Coucher ${night.bedtime.format(hourMinute)}, réveil ${night.wakeTime.format(hourMinute)}, " +
                            "${night.awakenings.size} réveil(s) dans la nuit"
                    }
                },
        ) {
            if (segments.isEmpty()) return@Canvas
            segments.forEach { seg ->
                val from = java.time.Duration.between(night.bedtime, seg.start).toMinutes().toFloat() / total
                val w = seg.minutes.toFloat() / total
                // Short awakenings stay visible: at least 2 dp wide.
                val width = (size.width * w).coerceAtLeast(if (seg.asleep) 0f else 2.dp.toPx())
                drawRect(if (seg.asleep) asleep else awake, Offset(size.width * from, 0f), Size(width, size.height))
                // A thin line of background on each side keeps an awakening apart from the sleep around it.
                if (!seg.asleep) {
                    val gap = 1.dp.toPx()
                    drawRect(gapColor, Offset(size.width * from - gap, 0f), Size(gap, size.height))
                    drawRect(gapColor, Offset(size.width * from + width, 0f), Size(gap, size.height))
                }
            }
        }
        if (labels) {
            Row(Modifier.fillMaxWidth()) {
                Text(night.bedtime.format(hourMinute), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(night.wakeTime.format(hourMinute), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TimelineLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Sommeil" to sleepColor(), "Éveil" to awakeColor()).forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).let { it }) { Canvas(Modifier.size(10.dp)) { drawRect(color) } }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "🙂 bonne · 😄 très reposante · 😐 ni agréable ni désagréable" */
private fun feelings(n: SleepNight): String? = listOfNotNull(
    n.quality?.let { "${Sleep.FACES[it - 1]} ${Sleep.QUALITY_LABELS[it - 1].lowercase()}" },
    n.rested?.let { "${Sleep.FACES[it - 1]} ${Sleep.RESTED_LABELS[it - 1].lowercase()}" },
    n.pleasant?.let { "${Sleep.FACES[it - 1]} ${if (it == 3) "ni agréable ni désagréable" else Sleep.PLEASANT_LABELS[it - 1].lowercase()}" },
).joinToString(" · ").ifEmpty { null }

private fun nightSummary(n: SleepNight): String = buildString {
    if (n.noSleep) {
        append("Au lit ${n.bedtime.format(hourMinute)} · levé·e ${n.wakeTime.format(hourMinute)} · pas dormi")
        return@buildString
    }
    append("Coucher ${n.bedtime.format(hourMinute)} · réveil ${n.wakeTime.format(hourMinute)}")
    append(
        when (n.awakenings.size) {
            0 -> " · aucun réveil"
            1 -> " · 1 réveil (${Sleep.format(n.awakeMinutes)})"
            else -> " · ${n.awakenings.size} réveils (${Sleep.format(n.awakeMinutes)})"
        },
    )
}

private enum class SleepPeriod(val days: Int, val label: String) { WEEK(7, "7 j"), MONTH(30, "30 j"), QUARTER(90, "90 j") }

@Composable
fun SleepScreen(
    state: AppState,
    now: LocalDateTime,
    onLog: () -> Unit,
    onOpen: (SleepNight) -> Unit,
    onNapStart: () -> Unit = {},
    onNapWake: () -> Unit = {},
    onNapCancel: () -> Unit = {},
    onNapLog: () -> Unit = {},
    onOpenNap: (com.medipote.app.data.Nap) -> Unit = {},
    onNightHelp: () -> Unit = {},
    onBlankNight: () -> Unit = {},
) {
    val today = now.toLocalDate()
    var period by rememberSaveable { mutableStateOf(SleepPeriod.WEEK) }
    val report = remember(state.sleep, state.journal, state.settings.sleep, today, period) { Sleep.report(state, today, period.days) }
    val lastNight = state.sleep.firstOrNull { it.date == today }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // One calm card for the nights that are hard.
        Card(
            onClick = onNightHelp,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🌙", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nuit difficile ?", style = MaterialTheme.typography.titleSmall)
                    Text("Des idées douces pour t'endormir ou te rendormir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (lastNight == null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Comment as-tu dormi cette nuit ?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Heure du coucher, réveils dans la nuit, heure du réveil : ça prend 20 secondes. +${Rules.SLEEP_BONUS} pts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onLog) {
                        Icon(Icons.Outlined.Bedtime, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Noter ma nuit")
                    }
                    TextButton(onClick = onBlankNight) { Text("🌙  Nuit blanche, je n'ai pas dormi") }
                }
            }
        } else {
            Card(
                onClick = { onOpen(lastNight) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(nightLabel(lastNight.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (lastNight.noSleep) "Nuit blanche" else Sleep.format(lastNight.asleepMinutes), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (lastNight.noSleep) "" else "de sommeil",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(nightSummary(lastNight), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    feelings(lastNight)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    NightTimeline(lastNight)
                    TimelineLegend()
                }
            }
        }

        // Naps: the timer when one is running, else the two ways to note one; today's under the night.
        val napSince = Naps.running(state, now)
        if (napSince != null) NapTimerCard(napSince, now, onWake = onNapWake, onCancel = onNapCancel)
        else NapActions(onStart = onNapStart, onLog = onNapLog)
        Naps.on(state, today).forEach { NapLine(it) { onOpenNap(it) } }

        PeriodSelector(SleepPeriod.entries.map { it to it.label }, period, { period = it })

        if (report.count == 0) {
            StatCard("Pas encore de nuit sur cette période") {
                Text(
                    "Note tes nuits pour voir ici ta durée de sommeil moyenne, tes horaires, tes réveils et ce qui les cause.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NapsCard(report)
        } else {
            // The essentials first; the detailed figures and past nights one tap away.
            SleepTiles(report, more = false)
            NapsCard(report)
            InsightsCard(report.insights)
            DurationChart(report, today)
            com.medipote.app.ui.components.MoreOptions(
                "Horaires, efficacité, réveils, causes",
                label = "Plus de détails",
            ) {
                SleepTiles(report, more = true)
                AwakeningsCard(report)
                if (report.tags.isNotEmpty()) {
                    StatCard("Ce qui a accompagné tes nuits") { RankedBars(report.tags, report.count) }
                }
            }
            if (report.naps.naps.isNotEmpty()) {
                com.medipote.app.ui.components.MoreOptions(
                    "${report.naps.naps.size} sieste${if (report.naps.naps.size > 1) "s" else ""} sur la période",
                    label = "Voir mes siestes",
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column {
                            report.naps.naps.reversed().forEachIndexed { i, nap ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                Row(
                                    Modifier.fillMaxWidth().clickable { onOpenNap(nap) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(nap.date.format(shortDay).capitalized() + " · " + nap.start.format(hourMinute), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Text(Sleep.format(nap.minutes), style = MaterialTheme.typography.titleSmall)
                                    nap.feeling?.let { Text("  " + it.emoji, style = MaterialTheme.typography.titleSmall) }
                                }
                            }
                        }
                    }
                }
            }
            com.medipote.app.ui.components.MoreOptions(
                "${report.count} nuit${if (report.count > 1) "s" else ""} sur la période",
                label = "Voir mes nuits précédentes",
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column {
                        report.nights.reversed().forEachIndexed { i, n ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            NightRow(n, report.goalMinutes, naps = Naps.minutesOn(state, n.date)) { onOpen(n) }
                        }
                    }
                }
            }
        }
        if (lastNight != null) {
            TextButton(onClick = onLog, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Noter une autre nuit") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Naps of the period, apart from the night's figures; and the day's total (night + naps). */
@Composable
private fun NapsCard(r: SleepReport) {
    val naps = r.naps
    if (naps.naps.isEmpty()) return
    StatCard("😴 Siestes", subtitle = "Comptées à part : elles ne changent pas les chiffres de ta nuit") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NapFigure("${naps.naps.size}", if (naps.naps.size > 1) "siestes" else "sieste")
            NapFigure(naps.averageMinutes?.let(Sleep::format) ?: "—", "en moyenne")
            NapFigure(Sleep.format(naps.totalMinutes), "au total")
        }
        r.averageDailyTotal?.let { total ->
            Text(
                "Nuit + siestes : ${Sleep.format(total)} de sommeil par jour en moyenne.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NapFigure(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SleepTiles(r: SleepReport, more: Boolean) {
    if (!more) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            "Sommeil moyen",
            r.averageAsleep?.let(Sleep::format) ?: "—",
            "objectif ${Sleep.format(r.goalMinutes)} · ${r.goalNights}/${r.count} nuits",
            Modifier.weight(1f),
        )
        StatTile(
            "Réveils / nuit",
            r.averageAwakenings?.let { decimal(it) } ?: "—",
            "${r.calmNights} nuit${if (r.calmNights > 1) "s" else ""} sans réveil",
            Modifier.weight(1f),
        )
    }
    if (!more) {
        // How the nights felt stays in sight.
        if (r.averageQuality != null || r.averageRested != null || r.averagePleasant != null) {
            StatCard("Ressenti des nuits", subtitle = "Moyennes sur 5 · un visage par niveau") {
                FeelingRow("Bonnes", r.averageQuality)
                FeelingRow("Reposantes", r.averageRested)
                FeelingRow("Agréables", r.averagePleasant)
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            "Coucher",
            r.typicalBedtime?.let(Reports::formatTime) ?: "—",
            r.typicalWake?.let { "réveil vers ${Reports.formatTime(it)}" } ?: "horaires variables",
            Modifier.weight(1f),
        )
        StatTile(
            "Efficacité",
            pct(r.averageEfficiency),
            "du temps au lit passé à dormir",
            Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            "D'une traite",
            r.averageLongestStretch?.let(Sleep::format) ?: "—",
            "plus long sommeil sans réveil",
            Modifier.weight(1f),
        )
        StatTile(
            "Endormissement",
            r.averageLatency?.let(Sleep::format) ?: "—",
            "en moyenne",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun FeelingRow(label: String, value: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            com.medipote.app.ui.stats.Meter(((value ?: 0f) - 1f) / 4f, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceContainerHighest)
        }
        Text(
            value?.let { "${Sleep.FACES[(it.roundToInt() - 1).coerceIn(0, 4)]} ${decimal(it)}" } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(72.dp).padding(start = 8.dp),
        )
    }
}

/** One question of the night, answered with a face from 1 to 5 (tap again to clear). */
@Composable
private fun RatingRow(question: String, value: Int?, labels: List<String>, onChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                value?.let { labels[it - 1] } ?: "non noté",
                style = MaterialTheme.typography.labelLarge,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sleep.FACES.forEachIndexed { i, face ->
                val v = i + 1
                val selected = value == v
                androidx.compose.material3.Surface(
                    onClick = { onChange(if (selected) null else v) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.weight(1f).height(44.dp).semantics { contentDescription = "$question ${labels[i]}" },
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text(face, style = MaterialTheme.typography.titleLarge, modifier = Modifier.alpha(if (value == null || selected) 1f else 0.45f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationChart(r: SleepReport, today: LocalDate) {
    val byDate = r.nights.associateBy { it.date }
    val goalHours = r.goalMinutes / 60f
    if (r.days <= 30) {
        val dates = (r.days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val values = dates.map { d -> (byDate[d]?.asleepMinutes ?: 0) / 60f }
        val top = ceil(maxOf(values.max(), goalHours * 1.1f) / 2f) * 2f
        StatCard("Durée de sommeil", subtitle = "Par nuit · pointillés : objectif de ${Sleep.format(r.goalMinutes)}") {
            BarChart(
                values = values,
                axisLabels = if (dates.size <= 7) dates.indices.associateWith { dates[it].format(DateTimeFormatter.ofPattern("EEE", Locale.FRANCE)).trimEnd('.').capitalized() }
                else mapOf(0 to dates.first().format(dayMonth), dates.size / 2 to dates[dates.size / 2].format(dayMonth), dates.lastIndex to "Auj."),
                describe = { i ->
                    val n = byDate[dates[i]]
                    "${dates[i].format(shortDay).capitalized()} · " + (n?.let { if (it.noSleep) "nuit blanche" else "${Sleep.format(it.asleepMinutes)} de sommeil, ${it.awakenings.size} réveil${if (it.awakenings.size > 1) "s" else ""}" } ?: "pas de nuit notée")
                },
                initialSelected = dates.indices.lastOrNull { byDate[dates[it]] != null } ?: dates.lastIndex,
                maxValue = top,
                gridLabel = { "${it.roundToInt()} h" },
                reference = goalHours,
            )
        }
    } else {
        // 90 days: one bar per week (average of the nights logged that week).
        val weeks = (12 downTo 0).map { w ->
            val from = today.minusDays(w * 7L + 6)
            val nights = r.nights.filter { !it.date.isBefore(from) && !it.date.isAfter(from.plusDays(6)) }
            Triple(from, nights.takeIf { it.isNotEmpty() }?.map { it.asleepMinutes }?.average()?.toFloat()?.div(60f), nights.size)
        }
        val values = weeks.map { it.second ?: 0f }
        val top = ceil(maxOf(values.max(), goalHours * 1.1f) / 2f) * 2f
        StatCard("Durée de sommeil", subtitle = "Moyenne par semaine · pointillés : objectif") {
            BarChart(
                values = values,
                axisLabels = mapOf(0 to weeks.first().first.format(dayMonth), 6 to weeks[6].first.format(dayMonth), 12 to "Cette sem."),
                describe = { i ->
                    val (from, avg, n) = weeks[i]
                    "Semaine du ${from.format(dayMonth)} · " + (avg?.let { "${Sleep.format((it * 60).roundToInt())} en moyenne ($n nuit${if (n > 1) "s" else ""})" } ?: "pas de nuit notée")
                },
                maxValue = top,
                gridLabel = { "${it.roundToInt()} h" },
                reference = goalHours,
            )
        }
    }
}

@Composable
private fun AwakeningsCard(r: SleepReport) {
    val total = r.awakeningCount
    StatCard(
        "Réveils dans la nuit",
        subtitle = if (total == 0) null else "$total réveil${if (total > 1) "s" else ""} sur ${r.count} nuit${if (r.count > 1) "s" else ""}" +
            (r.averageAwakeMinutes?.let { " · ${Sleep.format(it)} d'éveil par nuit en moyenne" } ?: ""),
    ) {
        if (total == 0) {
            Text("Aucun réveil noté : des nuits d'une traite 🌙", style = MaterialTheme.typography.bodyMedium)
            return@StatCard
        }
        // From 8 pm to 7 pm so that the night sits in the middle of the chart.
        val order = (0 until 24).map { (it + 20) % 24 }
        val values = order.map { r.awakeningHours[it].toFloat() }
        BarChart(
            values = values,
            axisLabels = mapOf(0 to "20 h", 4 to "0 h", 8 to "4 h", 12 to "8 h", 16 to "12 h"),
            describe = { i ->
                val h = order[i]
                val n = r.awakeningHours[h]
                "Entre $h h et ${(h + 1) % 24} h · $n réveil${if (n > 1) "s" else ""}"
            },
            initialSelected = values.indices.maxBy { values[it] },
            color = awakeColor(),
            height = 110.dp,
        )
        if (r.reasons.isNotEmpty()) {
            HorizontalDivider()
            Text("Causes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RankedBars(r.reasons, total)
        }
    }
}

@Composable
private fun NightRow(n: SleepNight, goal: Int, naps: Int = 0, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.date.format(shortDay).capitalized(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (n.noSleep) "Nuit blanche" else Sleep.format(n.asleepMinutes),
                style = MaterialTheme.typography.titleSmall,
                color = if (n.asleepMinutes >= goal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NightTimeline(n, height = 8, labels = false)
        Text(
            nightSummary(n) + (feelings(n)?.let { " · $it" } ?: "") + if (naps > 0) " · 😴 +${Sleep.format(naps)} de sieste" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────── Editor ───────────────────────────

private data class AwakeningDraft(val minuteOfDay: Int, val minutes: Int, val reason: String)

private fun step(options: List<Int>, value: Int, up: Boolean): Int {
    val i = options.indexOfFirst { it >= value }.let { if (it < 0) options.lastIndex else it }
    return if (up) options[(i + 1).coerceAtMost(options.lastIndex)] else options[(i - 1).coerceAtLeast(0)]
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepEditorScreen(
    state: AppState,
    existing: SleepNight?,
    date: LocalDate,
    today: LocalDate,
    onSave: (SleepNight) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val initial = remember { existing ?: Sleep.newNight(state, date) }
    var day by rememberSaveable { mutableStateOf(initial.date) }
    var bedMinute by rememberSaveable { mutableIntStateOf(Sleep.minuteOf(initial.bedtime)) }
    var wakeMinute by rememberSaveable { mutableIntStateOf(Sleep.minuteOf(initial.wakeTime)) }
    var latency by rememberSaveable { mutableIntStateOf(initial.latencyMinutes) }
    var blank by rememberSaveable { mutableStateOf(initial.noSleep) }
    val awakenings = remember {
        mutableStateListOf<AwakeningDraft>().apply { addAll(initial.awakenings.map { AwakeningDraft(Sleep.minuteOf(it.at), it.minutes, it.reason) }) }
    }
    var quality by rememberSaveable { mutableStateOf(initial.quality) }
    var rested by rememberSaveable { mutableStateOf(initial.rested) }
    var pleasant by rememberSaveable { mutableStateOf(initial.pleasant) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(initial.tags) } }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var picking by remember { mutableStateOf<Pair<String, Int>?>(null) } // "bed" / "wake" / "a<index>"
    var confirmDelete by remember { mutableStateOf(false) }

    val wake = day.atTime(wakeMinute / 60, wakeMinute % 60)
    val bed = Sleep.bedtimeFor(day, bedMinute)
    val night = SleepNight(
        date = day,
        bedtime = bed,
        latencyMinutes = latency,
        wakeTime = wake,
        awakenings = if (blank) emptyList() else awakenings.map { Awakening(Sleep.inNight(wake, it.minuteOfDay), it.minutes, it.reason) },
        noSleep = blank,
        quality = quality,
        rested = rested,
        pleasant = pleasant,
        tags = tags.toList(),
        notes = notes.trim(),
    )
    val problems = Sleep.problems(night)
    val replaces = existing?.date != day && state.sleep.any { it.date == day }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (existing == null) "Noter ma nuit" else "Modifier la nuit")
                        Text(nightLabel(day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        // Awakenings in time order; a night of the same date is replaced.
                        onSave(night.copy(awakenings = night.awakenings.sortedBy { it.at }))
                    },
                    enabled = problems.isEmpty(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live summary
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (problems.isEmpty() && blank) {
                        Text("Nuit blanche", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Au lit ${Sleep.format(night.inBedMinutes)}, sans dormir. Si le sommeil finit par venir dans la matinée, tu pourras la modifier.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (problems.isEmpty()) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Sleep.format(night.asleepMinutes), style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(8.dp))
                            Text("de sommeil", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text(
                            "Au lit ${Sleep.format(night.inBedMinutes)} · efficacité ${pct(night.efficiency)} · " +
                                "plus long sommeil d'une traite : ${Sleep.format(Sleep.longestStretch(night))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        NightTimeline(night)
                        TimelineLegend()
                    } else {
                        problems.forEach {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.WarningAmber, null, tint = AppTheme.status.critical, modifier = Modifier.size(18.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            DateField("Réveil le", day, { day = it }, maxDate = today, supporting = if (replaces) "Une nuit est déjà notée pour ce jour : elle sera remplacée." else null)

            // « Nuit blanche »: no sleep at all; editable later if sleep finally comes.
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.medipote.app.ui.treatments.SwitchRow(
                        "🌙  Nuit blanche", blank,
                        supporting = "Je n'ai pas dormi de la nuit. Tu pourras la modifier si tu t'endors plus tard dans la matinée.",
                    ) { blank = it }
                    if (blank) {
                        OutlinedButton(onClick = { picking = "onset" to ((wakeMinute + 60) % (24 * 60)) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Finalement, je me suis endormi·e plus tard…")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeCard(if (blank) "Au lit" else "Coucher", bedMinute, Icons.Outlined.Bedtime, Modifier.weight(1f)) { picking = "bed" to bedMinute }
                TimeCard(if (blank) "Levé·e" else "Réveil", wakeMinute, Icons.Outlined.WbSunny, Modifier.weight(1f)) { picking = "wake" to wakeMinute }
            }

            if (!blank) SectionTitle("Réveils dans la nuit") {
                Text("${awakenings.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!blank && awakenings.isEmpty()) {
                Text("Aucun réveil : une nuit d'une traite. Sinon, ajoute chaque réveil avec son heure et sa durée.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!blank) awakenings.forEachIndexed { i, a ->
                AwakeningCard(
                    a,
                    onTime = { picking = "a$i" to a.minuteOfDay },
                    onChange = { awakenings[i] = it },
                    onRemove = { awakenings.removeAt(i) },
                )
            }
            if (!blank) TextButton(onClick = {
                // Halfway through the night, or an hour after the last awakening.
                val sleepStart = bed.plusMinutes(latency.toLong())
                val last = awakenings.lastOrNull()?.let { Sleep.inNight(wake, it.minuteOfDay).plusMinutes(it.minutes + 60L) }
                val at = (last ?: sleepStart.plusMinutes(java.time.Duration.between(sleepStart, wake).toMinutes() / 2)).let { if (it.isBefore(wake)) it else wake.minusMinutes(30) }
                awakenings.add(AwakeningDraft(Sleep.minuteOf(at) / 5 * 5, 15, ""))
            }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Ajouter un réveil")
            }

            SectionTitle("Comment était ta nuit ?")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RatingRow("Bonne ?", quality, Sleep.QUALITY_LABELS) { quality = it }
                    RatingRow("Reposante ?", rested, Sleep.RESTED_LABELS) { rested = it }
                    RatingRow("Agréable ?", pleasant, Sleep.PLEASANT_LABELS) { pleasant = it }
                }
            }

            com.medipote.app.ui.components.MoreOptions(
                listOfNotNull(
                    "endormissement : ".takeIf { !blank }?.plus(if (latency == 0) "tout de suite" else Sleep.format(latency)),
                    tags.size.takeIf { it > 0 }?.let { "$it facteur${if (it > 1) "s" else ""}" },
                    "notes".takeIf { notes.isNotBlank() },
                ).joinToString(" · "),
                initiallyOpen = tags.isNotEmpty() || notes.isNotBlank(),
                label = "Endormissement, facteurs, notes",
            ) {
                if (!blank) SectionTitle("Temps pour t'endormir")
                if (!blank) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sleep.LATENCY_OPTIONS.forEach { m ->
                        FilterChip(
                            selected = latency == m,
                            onClick = { latency = m },
                            label = { Text(if (m == 0) "Tout de suite" else Sleep.format(m)) },
                        )
                    }
                }
                SectionTitle("Ce qui a pu jouer")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Sleep.TAGS + tags.filter { it !in Sleep.TAGS }).forEach { t ->
                        val on = t in tags
                        FilterChip(
                            selected = on,
                            onClick = { if (on) tags.remove(t) else tags.add(t) },
                            label = { Text(t) },
                            leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (rêves, sieste…)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }
    }

    picking?.let { (what, minute) ->
        TimePickerModal(
            minuteOfDay = minute,
            title = when (what) { "bed" -> "Heure du coucher"; "wake" -> "Heure du réveil"; "onset" -> "Heure à laquelle tu t'es endormi·e"; else -> "Heure du réveil nocturne" },
            onDismiss = { picking = null },
        ) { m ->
            when {
                what == "bed" -> bedMinute = m
                what == "wake" -> wakeMinute = m
                what == "onset" -> {
                    // Sleep finally came: the night becomes a normal one, the time before counting as falling asleep.
                    val updated = Sleep.fellAsleepLater(night, m)
                    blank = false
                    latency = updated.latencyMinutes
                    wakeMinute = Sleep.minuteOf(updated.wakeTime)
                }
                else -> what.drop(1).toIntOrNull()?.let { i -> awakenings.getOrNull(i)?.let { awakenings[i] = it.copy(minuteOfDay = m) } }
            }
            picking = null
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette nuit ?") },
            text = { Text("Elle disparaîtra de tes statistiques de sommeil.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun TimeCard(label: String, minute: Int, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%02d:%02d".format(minute / 60, minute % 60), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AwakeningCard(a: AwakeningDraft, onTime: () -> Unit, onChange: (AwakeningDraft) -> Unit, onRemove: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onTime) {
                    Text("Vers %02d:%02d".format(a.minuteOfDay / 60, a.minuteOfDay % 60), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = { onChange(a.copy(minutes = step(Sleep.DURATION_OPTIONS, a.minutes, false))) }, enabled = a.minutes > Sleep.DURATION_OPTIONS.first(), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Remove, "Plus court", Modifier.size(18.dp))
                }
                Text(Sleep.format(a.minutes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp))
                FilledTonalIconButton(onClick = { onChange(a.copy(minutes = step(Sleep.DURATION_OPTIONS, a.minutes, true))) }, enabled = a.minutes < Sleep.DURATION_OPTIONS.last(), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Add, "Plus long", Modifier.size(18.dp))
                }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Retirer ce réveil") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 8.dp)) {
                Sleep.AWAKENING_REASONS.forEach { r ->
                    FilterChip(
                        selected = a.reason == r,
                        onClick = { onChange(a.copy(reason = if (a.reason == r) "" else r)) },
                        label = { Text(r) },
                    )
                }
            }
        }
    }
}

/** The Sommeil sub-tab of the statistics: the essentials and the chart; the detail stays in Suivi › Sommeil. */
@Composable
fun SleepStatsTab(state: AppState, today: LocalDate, days: Int, periodLabel: String) {
    val r = remember(state.sleep, state.naps, state.settings.sleep, today, days) { Sleep.report(state, today, days) }
    if (r.count == 0) {
        StatCard("Sommeil") {
            Text(
                "Note quelques nuits pour voir ici ta durée de sommeil, tes horaires et tes réveils.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            "Sommeil moyen", r.averageAsleep?.let(Sleep::format) ?: "—",
            "objectif ${Sleep.format(r.goalMinutes)} · ${r.goalNights}/${r.count} nuits", Modifier.weight(1f),
        )
        StatTile(
            "Réveils / nuit", r.averageAwakenings?.let { decimal(it) } ?: "—",
            "${r.calmNights} nuit${if (r.calmNights > 1) "s" else ""} sans réveil", Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Coucher habituel", r.typicalBedtime?.let(Reports::formatTime) ?: "—", r.bedtimeSpread?.let { "écart moyen : $it min" }, Modifier.weight(1f))
        StatTile("Ressenti", r.averageQuality?.let { "${decimal(it)}/5" } ?: "—", "qualité des nuits", Modifier.weight(1f))
    }
    DurationChart(r, today)
    NapsCard(r)
    com.medipote.app.ui.stats.InsightsCard(r.insights)
    Text(
        "Sur $periodLabel · le détail (réveils, causes, nuits une à une) est dans Suivi › Sommeil.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Short summary for the Stats tab. */
@Composable
fun SleepSummaryCard(state: AppState, today: LocalDate, days: Int, periodLabel: String) {
    val r = remember(state.sleep, state.settings.sleep, today, days) { Sleep.report(state, today, days) }
    if (r.count == 0) return
    StatCard("Sommeil", subtitle = "Sur $periodLabel · ${r.count} nuit${if (r.count > 1) "s" else ""} notée${if (r.count > 1) "s" else ""} · détail dans Suivi › Sommeil") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mini("par nuit", r.averageAsleep?.let(Sleep::format) ?: "—", Modifier.weight(1f))
            Mini("réveils / nuit", r.averageAwakenings?.let { decimal(it) } ?: "—", Modifier.weight(1f))
            Mini("coucher", r.typicalBedtime?.let(Reports::formatTime) ?: "—", Modifier.weight(1f))
        }
        r.insights.firstOrNull { it.tone != com.medipote.app.domain.Tone.INFO }?.let {
            Text(it.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
