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

@Composable
private fun awakeColor() = MaterialTheme.colorScheme.tertiary

/** The night from bedtime to waking up: asleep in the accent colour, awake (falling asleep, awakenings) in a second hue. */
@Composable
fun NightTimeline(night: SleepNight, modifier: Modifier = Modifier, height: Int = 22, labels: Boolean = true) {
    val asleep = sleepColor()
    val awake = awakeColor()
    val segments = remember(night) { Sleep.segments(night) }
    val total = night.inBedMinutes.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape((height / 3).dp))
                .semantics {
                    contentDescription = "Coucher ${night.bedtime.format(hourMinute)}, réveil ${night.wakeTime.format(hourMinute)}, " +
                        "${night.awakenings.size} réveil(s) dans la nuit"
                },
        ) {
            if (segments.isEmpty()) return@Canvas
            segments.forEach { seg ->
                val from = java.time.Duration.between(night.bedtime, seg.start).toMinutes().toFloat() / total
                val w = seg.minutes.toFloat() / total
                // Short awakenings stay visible: at least 2 dp wide.
                val width = (size.width * w).coerceAtLeast(if (seg.asleep) 0f else 2.dp.toPx())
                drawRect(if (seg.asleep) asleep else awake, Offset(size.width * from, 0f), Size(width, size.height))
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

private fun nightSummary(n: SleepNight): String = buildString {
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
fun SleepScreen(state: AppState, now: LocalDateTime, onLog: () -> Unit, onOpen: (SleepNight) -> Unit) {
    val today = now.toLocalDate()
    var period by rememberSaveable { mutableStateOf(SleepPeriod.WEEK) }
    val report = remember(state.sleep, state.journal, state.settings.sleep, today, period) { Sleep.report(state, today, period.days) }
    val lastNight = state.sleep.firstOrNull { it.date == today }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (lastNight == null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Comment as-tu dormi cette nuit ?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "Heure du coucher, réveils dans la nuit, heure du réveil : ça prend 20 secondes. +${Rules.SLEEP_BONUS} pts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Button(onClick = onLog) {
                        Icon(Icons.Outlined.Bedtime, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Noter ma nuit")
                    }
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
                        Text(Sleep.format(lastNight.asleepMinutes), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "de sommeil" + (lastNight.quality?.let { " · nuit ${Sleep.QUALITY_LABELS[it - 1].lowercase()}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(nightSummary(lastNight), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NightTimeline(lastNight)
                    TimelineLegend()
                }
            }
        }

        PeriodSelector(SleepPeriod.entries.map { it to it.label }, period, { period = it })

        if (report.count == 0) {
            StatCard("Pas encore de nuit sur cette période") {
                Text(
                    "Note tes nuits pour voir ici ta durée de sommeil moyenne, tes horaires, tes réveils et ce qui les cause.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SleepTiles(report)
            InsightsCard(report.insights)
            DurationChart(report, today)
            AwakeningsCard(report)
            if (report.tags.isNotEmpty()) {
                StatCard("Ce qui a accompagné tes nuits") { RankedBars(report.tags, report.count) }
            }
            SectionTitle("Historique")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column {
                    report.nights.reversed().forEachIndexed { i, n ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        NightRow(n, report.goalMinutes) { onOpen(n) }
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

@Composable
private fun SleepTiles(r: SleepReport) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            "Qualité",
            r.averageQuality?.let { "${decimal(it)}/5" } ?: "—",
            r.averageLatency?.let { "endormissement : ${Sleep.format(it)}" },
            Modifier.weight(1f),
        )
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
                    "${dates[i].format(shortDay).capitalized()} · " + (n?.let { "${Sleep.format(it.asleepMinutes)} de sommeil, ${it.awakenings.size} réveil${if (it.awakenings.size > 1) "s" else ""}" } ?: "pas de nuit notée")
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
private fun NightRow(n: SleepNight, goal: Int, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.date.format(shortDay).capitalized(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                Sleep.format(n.asleepMinutes),
                style = MaterialTheme.typography.titleSmall,
                color = if (n.asleepMinutes >= goal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NightTimeline(n, height = 8, labels = false)
        Text(
            nightSummary(n) + (n.quality?.let { " · ${Sleep.QUALITY_LABELS[it - 1].lowercase()}" } ?: ""),
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
    val awakenings = remember {
        mutableStateListOf<AwakeningDraft>().apply { addAll(initial.awakenings.map { AwakeningDraft(Sleep.minuteOf(it.at), it.minutes, it.reason) }) }
    }
    var quality by rememberSaveable { mutableStateOf(initial.quality) }
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
        awakenings = awakenings.map { Awakening(Sleep.inNight(wake, it.minuteOfDay), it.minutes, it.reason) },
        quality = quality,
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
                    if (problems.isEmpty()) {
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeCard("Coucher", bedMinute, Icons.Outlined.Bedtime, Modifier.weight(1f)) { picking = "bed" to bedMinute }
                TimeCard("Réveil", wakeMinute, Icons.Outlined.WbSunny, Modifier.weight(1f)) { picking = "wake" to wakeMinute }
            }

            SectionTitle("Temps pour t'endormir")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sleep.LATENCY_OPTIONS.forEach { m ->
                    FilterChip(
                        selected = latency == m,
                        onClick = { latency = m },
                        label = { Text(if (m == 0) "Tout de suite" else Sleep.format(m)) },
                    )
                }
            }

            SectionTitle("Réveils dans la nuit") {
                Text("${awakenings.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (awakenings.isEmpty()) {
                Text("Aucun réveil : une nuit d'une traite. Sinon, ajoute chaque réveil avec son heure et sa durée.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            awakenings.forEachIndexed { i, a ->
                AwakeningCard(
                    a,
                    onTime = { picking = "a$i" to a.minuteOfDay },
                    onChange = { awakenings[i] = it },
                    onRemove = { awakenings.removeAt(i) },
                )
            }
            TextButton(onClick = {
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

            SectionTitle("Qualité de la nuit")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sleep.QUALITY_LABELS.forEachIndexed { i, label ->
                    val v = i + 1
                    FilterChip(selected = quality == v, onClick = { quality = if (quality == v) null else v }, label = { Text(label) })
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

    picking?.let { (what, minute) ->
        TimePickerModal(
            minuteOfDay = minute,
            title = when (what) { "bed" -> "Heure du coucher"; "wake" -> "Heure du réveil"; else -> "Heure du réveil nocturne" },
            onDismiss = { picking = null },
        ) { m ->
            when {
                what == "bed" -> bedMinute = m
                what == "wake" -> wakeMinute = m
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
