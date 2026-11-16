package com.medipote.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
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
import com.medipote.app.domain.Insight
import com.medipote.app.domain.Sleep
import com.medipote.app.domain.Wellbeing
import com.medipote.app.domain.Prn
import com.medipote.app.domain.PrnReport
import com.medipote.app.domain.Reports
import com.medipote.app.domain.Tone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.abs
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

private enum class Period(val days: Int, val label: String) { WEEK(7, "7 j"), MONTH(30, "30 j"), QUARTER(90, "90 j") }

private fun signedMinutes(m: Int) = when {
    m == 0 -> "0 min"
    abs(m) >= 120 -> (if (m > 0) "+" else "−") + Prn.formatDuration(java.time.Duration.ofMinutes(abs(m).toLong()))
    else -> (if (m > 0) "+" else "−") + "${abs(m)} min"
}

/** The sub-tabs of the statistics: one subject each, so each page stays short and clear. */
private enum class StatsTab(val emoji: String, val label: String) {
    OVERVIEW("📊", "Vue d'ensemble"),
    TREATMENTS("💊", "Traitements"),
    SLEEP("🌙", "Sommeil"),
    SYMPTOMS("🌡️", "Symptômes"),
    WELLBEING("😊", "Bien-être"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: AppState,
    now: LocalDateTime,
    embedded: Boolean = false,
    onOpenPrn: (String) -> Unit = {},
    onReport: () -> Unit = {},
    onManageSymptoms: () -> Unit = {},
) {
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var tab by rememberSaveable { mutableStateOf(StatsTab.OVERVIEW) }
    val wellbeing = Wellbeing.enabled(state)
    val tabs = StatsTab.entries.filter { it != StatsTab.SYMPTOMS || wellbeing }
    val today = now.toLocalDate()
    val shown = if (tab in tabs) tab else StatsTab.OVERVIEW

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
            // One subject at a time; the period is shared by all of them.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { t ->
                    FilterChip(selected = shown == t, onClick = { tab = t }, label = { Text("${t.emoji}  ${t.label}", maxLines = 1) })
                }
            }
            PeriodSelector(Period.entries.map { it to it.label }, period, { period = it })

            when (shown) {
                StatsTab.OVERVIEW -> OverviewTab(state, now, period, onReport)
                StatsTab.TREATMENTS -> TreatmentsTab(state, now, period, onOpenPrn)
                StatsTab.SLEEP -> com.medipote.app.ui.sleep.SleepStatsTab(state, today, period.days, period.label)
                StatsTab.SYMPTOMS -> SymptomsTab(state, today, period, onManageSymptoms)
                StatsTab.WELLBEING -> WellbeingTab(state, today, period)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A glance at everything: one tile per subject, what stands out, and the points. */
@Composable
private fun OverviewTab(state: AppState, now: LocalDateTime, period: Period, onReport: () -> Unit) {
    val status = AppTheme.status
    val today = now.toLocalDate()
    val report = remember(state, today, now.hour, period) { Reports.adherence(state, now, period.days) }
    val sleep = remember(state.sleep, state.settings.sleep, today, period) { Sleep.report(state, today, period.days) }
    val wellbeing = Wellbeing.enabled(state)
    val prnInsights = remember(state, today, now.hour, period) {
        state.treatments.filter { it.asNeeded }.map { Reports.prn(state, it.id, now, period.days) }
            .flatMap { r -> r.insights.filter { it.tone == Tone.WARN }.map { it.copy(text = "${r.name} : ${it.text.replaceFirstChar { c -> c.lowercase() }}") } }
    }

    val rate = report.rate.value
    val before = report.previousRate.value
    val delta = if (rate != null && before != null && report.previousRate.decided >= 3) ((rate - before) * 100).roundToInt() else null
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            "Traitements",
            pct(rate),
            when {
                delta == null -> if (report.rate.decided == 0) "pas encore de données" else "${report.rate.taken}/${report.rate.decided} prises"
                delta > 0 -> "▲ $delta pts vs période préc."
                delta < 0 -> "▼ ${-delta} pts vs période préc."
                else -> "= période précédente"
            },
            Modifier.weight(1f),
            captionColor = when {
                delta == null || delta == 0 -> null
                delta > 0 -> status.good
                else -> status.critical
            },
        )
        StatTile("Série", "${state.game.streak} j", "record : ${state.game.bestStreak} j", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            "Sommeil", sleep.averageAsleep?.let(Sleep::format) ?: "—",
            if (sleep.count == 0) "pas encore de nuit" else "par nuit · ${sleep.count} nuit${if (sleep.count > 1) "s" else ""}",
            Modifier.weight(1f),
        )
        StatTile(
            "Moral", report.mentalAverage?.let { "${decimal(it)}/5" } ?: "—",
            if (report.mentalAverage == null) "pas encore noté" else "humeur moyenne", Modifier.weight(1f),
        )
    }
    if (wellbeing) {
        val tracked = Wellbeing.tracked(state)
        val from = today.minusDays(period.days - 1L)
        val hardDays = state.symptomLog
            .filter { it.symptom in tracked && it.intensity >= Wellbeing.HEAVY && !it.date.isBefore(from) && !it.date.isAfter(today) }
            .map { it.date }.distinct().size
        val wins = remember(state.wins, state.tasks, today, period) { Wellbeing.winsReport(state, today, period.days) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Jours difficiles", if (tracked.isEmpty()) "—" else "$hardDays",
                if (tracked.isEmpty()) "aucun symptôme suivi" else "symptôme fort (4 ou 5)", Modifier.weight(1f),
            )
            StatTile("Petites victoires", "${wins.wins}", if (wins.wins == 0) "à ton rythme" else "sur ${wins.daysWithWins} jour${if (wins.daysWithWins > 1) "s" else ""} · +${wins.points} pts", Modifier.weight(1f))
        }
    }

    val notes = report.insights +
        prnInsights +
        sleep.insights.filter { it.tone != Tone.INFO }.map { it.copy(text = "Sommeil : ${it.text.replaceFirstChar { c -> c.lowercase() }}") } +
        (if (wellbeing) Wellbeing.insights(state, today).map { Insight(it, Tone.INFO) } else emptyList())
    InsightsCard(notes)

    androidx.compose.material3.OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) {
        Icon(androidx.compose.material.icons.Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Rapport pour le médecin (PDF)")
    }
    PointsCard(state)
}

/** Doses taken, on time or not, by moment, by day and by treatment; as-needed medications. */
@Composable
private fun TreatmentsTab(state: AppState, now: LocalDateTime, period: Period, onOpenPrn: (String) -> Unit) {
    val status = AppTheme.status
    val report = remember(state, now.toLocalDate(), now.hour, period) { Reports.adherence(state, now, period.days) }
    val prn = state.treatments.filter { it.asNeeded }
    val prnReports = remember(state, now.toLocalDate(), now.hour, period) { prn.map { Reports.prn(state, it.id, now, period.days) } }
    val hasRegular = state.treatments.any { !it.asNeeded }
    if (!hasRegular && prn.isEmpty()) {
        StatCard("Traitements") { EmptyText() }
        return
    }

    if (hasRegular) {
        val rate = report.rate.value
        val before = report.previousRate.value
        val delta = if (rate != null && before != null && report.previousRate.decided >= 3) ((rate - before) * 100).roundToInt() else null
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Observance",
                pct(rate),
                when {
                    delta == null -> if (report.rate.decided == 0) "pas encore de données" else "${report.rate.taken}/${report.rate.decided} prises"
                    delta > 0 -> "▲ $delta pts vs période préc."
                    delta < 0 -> "▼ ${-delta} pts vs période préc."
                    else -> "= période précédente"
                },
                Modifier.weight(1f),
                captionColor = when {
                    delta == null || delta == 0 -> null
                    delta > 0 -> status.good
                    else -> status.critical
                },
            )
            StatTile("Ponctualité", pct(report.punctuality), "prises à l'heure", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Écart moyen",
                report.averageDelayMinutes?.let(::signedMinutes) ?: "—",
                when {
                    report.averageDelayMinutes == null -> "avec l'heure prévue"
                    report.averageDelayMinutes > 0 -> "après l'heure prévue"
                    report.averageDelayMinutes < 0 -> "avant l'heure prévue"
                    else -> "pile à l'heure"
                },
                Modifier.weight(1f),
            )
            StatTile("Série", "${state.game.streak} j", "record : ${state.game.bestStreak} j", Modifier.weight(1f))
        }

        InsightsCard(report.insights + prnReports.flatMap { r -> r.insights.filter { it.tone == Tone.WARN }.map { it.copy(text = "${r.name} : ${it.text.replaceFirstChar { c -> c.lowercase() }}") } })

        ChartCard(report.heatmap.takeLast(14))
        StatCard("Évolution sur 12 semaines", subtitle = "Part des prises faites, semaine par semaine") {
            val weeks = report.weekly
            BarChart(
                values = weeks.map { it.second.value ?: 0f },
                axisLabels = mapOf(0 to weeks.first().first.format(dayMonth), 6 to weeks[6].first.format(dayMonth), 11 to "Cette sem."),
                describe = { i ->
                    val (start, r) = weeks[i]
                    "Semaine du ${start.format(dayMonth)} · " + if (r.decided == 0) "aucune prise prévue" else "${pct(r.value)} (${r.taken}/${r.decided})"
                },
                maxValue = 1f,
                gridLabel = { "${(it * 100).roundToInt()} %" },
            )
        }
        StatCard("Calendrier", subtitle = "12 dernières semaines · touche un jour pour le détail") {
            AdherenceHeatmap(report.heatmap)
        }
        BreakdownCard(report.onTime, report.offTime, report.missed, period.label)
        StatCard("Selon le moment", subtitle = "Part des prises faites sur ${period.label}") {
            RateRows(report.bySlot.filter { it.value.decided > 0 }.map { it.key.label to it.value })
            if (report.bySlot.all { it.value.decided == 0 }) EmptyText()
        }
        StatCard("Selon le jour de la semaine") {
            if (report.byWeekday.all { it.value.decided == 0 }) EmptyText()
            else RateRows(report.byWeekday.map { it.key.getDisplayName(TextStyle.FULL, Locale.FRANCE).capitalized() to it.value })
        }
        if (report.perTreatment.isNotEmpty()) {
            StatCard("Par traitement", subtitle = "Sur ${period.label}") {
                report.perTreatment.forEach { t ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (t.rate.decided == 0) "—" else "${t.rate.taken}/${t.rate.decided} · ${pct(t.rate.value)}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Meter(t.rate.value ?: 0f, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceContainerHighest)
                        t.averageDelayMinutes?.let {
                            Text(
                                "Écart moyen : ${signedMinutes(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (prn.isNotEmpty()) {
        SectionTitle("Si besoin")
        prnReports.forEach { r -> PrnSummaryCard(r, state, now, period.label) { onOpenPrn(r.treatmentId) } }
    }
}

/** Each symptom followed: how strong, how often, better or worse than before, day by day. */
@Composable
private fun SymptomsTab(state: AppState, today: java.time.LocalDate, period: Period, onManage: () -> Unit) {
    val tracked = Wellbeing.tracked(state)
    if (tracked.isEmpty()) {
        StatCard("Symptômes") {
            Text(
                "Choisis les symptômes à suivre et note-les chaque jour (Suivi › Symptômes) : leurs statistiques apparaîtront ici.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedButton(onClick = onManage) { Text("Choisir mes symptômes") }
        }
        return
    }
    tracked.forEach { id ->
        val p = remember(state.symptomLog, today, period, id) { Wellbeing.period(state, id, today, period.days) }
        StatCard("${Wellbeing.emoji(id)}  ${Wellbeing.name(id)}", subtitle = "Sur ${period.label}") {
            if (p.noted == 0) {
                Text("Pas encore noté sur cette période.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@StatCard
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniValue("force moyenne", p.average?.let { "${decimal(it.toFloat())}/5" } ?: "—", Modifier.weight(1f))
                MiniValue("jours notés", "${p.noted}/${p.days}", Modifier.weight(1f))
                MiniValue("jours forts", "${p.strongDays}", Modifier.weight(1f))
            }
            val avg = p.average
            val prev = p.previousAverage
            if (avg != null && prev != null) {
                val diff = avg - prev
                Text(
                    when {
                        diff >= 0.5 -> "▲ Plus fort que sur la période d'avant (${decimal(prev.toFloat())}/5)."
                        diff <= -0.5 -> "▼ Moins fort que sur la période d'avant (${decimal(prev.toFloat())}/5)."
                        else -> "= Comme sur la période d'avant (${decimal(prev.toFloat())}/5)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        diff <= -0.5 -> AppTheme.status.good
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            val last = p.bars.lastIndex
            BarChart(
                values = p.bars,
                axisLabels = mapOf(0 to p.barDays.first().first.format(dayMonth), last / 2 to p.barDays[last / 2].first.format(dayMonth), last to if (period.days > 30) "Cette sem." else "Auj."),
                describe = { i ->
                    val (a, b) = p.barDays[i]
                    val label = if (a == b) a.format(dayMonth) else "Semaine du ${a.format(dayMonth)}"
                    label + " · " + if (p.bars[i] == 0f) "pas noté" else "force ${decimal(p.bars[i])}/5"
                },
                maxValue = 5f,
                gridLabel = { "${it.roundToInt()}" },
                initialSelected = p.bars.indexOfLast { it > 0f }.takeIf { it >= 0 } ?: last,
            )
        }
    }
    val links = remember(state.symptomLog, state.sleep, today) { Wellbeing.insights(state, today) }
    InsightsCard(links.map { Insight(it, Tone.INFO) }, title = "Liens avec ton sommeil")
    Text(
        "Ce sont des observations de tes propres notes, pas un diagnostic : montre-les à ton médecin si tu veux.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Mood, physical shape, water and the small victories of hard days. */
@Composable
private fun WellbeingTab(state: AppState, today: java.time.LocalDate, period: Period) {
    val report = remember(state, today, period) { Reports.adherence(state, LocalDateTime.of(today, java.time.LocalTime.of(23, 59)), period.days) }
    var anything = false
    if (report.waterDays > 0 || report.physicalAverage != null || report.mentalAverage != null) {
        anything = true
        StatCard("Bien-être et hydratation", subtitle = "Moyennes sur ${period.label}") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniValue("Verres d'eau / jour", report.waterAverage?.let { decimal(it) } ?: "—", Modifier.weight(1f))
                MiniValue("Objectif atteint", if (report.waterDays == 0) "—" else "${report.waterGoalDays}/${report.waterDays} j", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniValue("Forme physique", report.physicalAverage?.let { "${decimal(it)}/5" } ?: "—", Modifier.weight(1f))
                MiniValue("Moral", report.mentalAverage?.let { "${decimal(it)}/5" } ?: "—", Modifier.weight(1f))
            }
        }
    }
    val mood = remember(state.journal, today, period) { moodBars(state, today, period.days) }
    if (mood.first.any { it > 0f }) {
        anything = true
        val (bars, ranges) = mood
        StatCard("Moral au fil du temps", subtitle = if (period.days > 30) "Moyenne par semaine" else "Jour par jour") {
            val last = bars.lastIndex
            BarChart(
                values = bars,
                axisLabels = mapOf(0 to ranges.first().first.format(dayMonth), last / 2 to ranges[last / 2].first.format(dayMonth), last to if (period.days > 30) "Cette sem." else "Auj."),
                describe = { i ->
                    val (a, b) = ranges[i]
                    (if (a == b) a.format(dayMonth) else "Semaine du ${a.format(dayMonth)}") + " · " + if (bars[i] == 0f) "pas noté" else "${decimal(bars[i])}/5"
                },
                maxValue = 5f,
                gridLabel = { "${it.roundToInt()}" },
                initialSelected = bars.indexOfLast { it > 0f }.takeIf { it >= 0 } ?: last,
            )
        }
    }
    if (Wellbeing.enabled(state)) {
        anything = true
        val wins = remember(state.wins, state.tasks, today, period) { Wellbeing.winsReport(state, today, period.days) }
        StatCard("💛  Petites victoires", subtitle = "Sur ${period.label} · les missions et les étapes faites") {
            if (wins.wins == 0) {
                Text(
                    "Pas encore de petite victoire sur cette période. Elles apparaîtront ici, sans pression.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniValue("victoires", "${wins.wins}", Modifier.weight(1f))
                    MiniValue("jours avec", "${wins.daysWithWins}", Modifier.weight(1f))
                    MiniValue("points gagnés", "+${wins.points}", Modifier.weight(1f))
                }
                if (wins.stepsDone > 0 || wins.tasksFinished > 0) {
                    Text(
                        "${wins.stepsDone} étape${if (wins.stepsDone > 1) "s" else ""} de tâche" + if (wins.tasksFinished > 0) " · ${wins.tasksFinished} tâche${if (wins.tasksFinished > 1) "s" else ""} terminée${if (wins.tasksFinished > 1) "s" else ""}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (wins.top.isNotEmpty()) {
                    Text("Ce qui t'a le plus aidé", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    wins.top.forEach { (label, n) -> Text("•  $label" + if (n > 1) "  ×$n" else "", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
    if (!anything) StatCard("Bien-être") { Text("Note ton humeur, ton eau ou ta forme : les statistiques apparaîtront ici.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Mood per day (per week beyond 30 days): 0 when not noted. */
private fun moodBars(state: AppState, today: java.time.LocalDate, days: Int): Pair<List<Float>, List<Pair<java.time.LocalDate, java.time.LocalDate>>> {
    val ranges = if (days <= 30) (days - 1 downTo 0).map { today.minusDays(it.toLong()).let { d -> d to d } }
    else (12 downTo 0).map { w -> today.minusDays(w * 7L + 6) to today.minusDays(w * 7L) }
    val byDate = state.journal.filter { it.mental != null }.associate { it.date to it.mental!! }
    val bars = ranges.map { (a, b) ->
        val values = generateSequence(a) { it.plusDays(1) }.takeWhile { !it.isAfter(b) }.mapNotNull { byDate[it] }.toList()
        if (values.isEmpty()) 0f else values.average().toFloat()
    }
    return bars to ranges
}

@Composable
private fun EmptyText() {
    Text(
        "Les statistiques apparaîtront après tes premières prises.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PrnSummaryCard(r: PrnReport, state: AppState, now: LocalDateTime, periodLabel: String, onOpen: () -> Unit) {
    val last = Prn.last(state, r.treatmentId, now)
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Voir le détail", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniValue("prises sur $periodLabel", "${r.count}", Modifier.weight(1f))
                MiniValue("par semaine", decimal(r.perWeek), Modifier.weight(1f))
                MiniValue("heure habituelle", r.typicalMinuteOfDay?.let { Reports.formatTime(it) } ?: "—", Modifier.weight(1f))
            }
            val line = listOfNotNull(
                last?.let { "Dernière prise ${com.medipote.app.ui.treatments.sinceLabel(it.takenAt, now)}" },
                r.reasons.firstOrNull()?.let { "Motif principal : ${it.first.lowercase()}" },
            )
            line.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (r.count == 0 && last == null) {
                Text("Aucune prise notée pour l'instant.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PointsCard(state: AppState) {
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
private fun BreakdownCard(onTime: Int, late: Int, missed: Int, periodLabel: String) {
    val status = AppTheme.status
    val total = onTime + late + missed
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    Card(colors = CardDefaults.cardColors(containerColor = surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Répartition sur $periodLabel", style = MaterialTheme.typography.titleMedium)
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Count("À l'heure", onTime, total, status.good, Modifier.weight(1f))
                Count("Décalées", late, total, status.warning, Modifier.weight(1f))
                Count("Oubliées", missed, total, status.critical, Modifier.weight(1f))
                com.medipote.app.ui.components.InfoButton("Décalées", "Prises plus de 30 min avant ou après l'heure prévue.")
            }
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
