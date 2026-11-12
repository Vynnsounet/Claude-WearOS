package com.medipote.app.ui.cycle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.CycleInfo
import com.medipote.app.domain.Cycle
import com.medipote.app.domain.CycleDayType
import com.medipote.app.domain.CyclePhase
import com.medipote.app.ui.components.DatePickerModal
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.NumberStepper
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.dayMonth
import com.medipote.app.ui.components.dayMonthYear
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(
    info: CycleInfo?,
    today: LocalDate,
    onLogPeriod: (LocalDate) -> Unit,
    onUpdate: ((CycleInfo) -> CycleInfo) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val status = remember(info, today) { info?.let { Cycle.status(it, today) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mon cycle") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (info == null || status == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Commence ton suivi", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Indique la date de début de tes dernières règles pour estimer ton cycle.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { picking = true }) { Text("Renseigner mes dernières règles") }
                    }
                }
            } else {
                PhaseCard(status.dayOfCycle, status.cycleLength, status.phase, status.lateDays, status.daysUntilNextPeriod, status.nextPeriod)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoRow(AppTheme.status.period, "Prochaines règles", status.nextPeriod.format(dayMonthYear))
                        InfoRow(
                            AppTheme.status.fertile, "Fenêtre de fertilité estimée",
                            "${status.fertileStart.format(dayMonth)} – ${status.fertileEnd.format(dayMonth)}",
                        )
                        InfoRow(AppTheme.status.fertile, "Ovulation estimée", status.ovulation.format(dayMonthYear))
                    }
                }
                Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.WaterDrop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mes règles ont commencé")
                }
                CalendarCard(info, today)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Réglages", style = MaterialTheme.typography.titleMedium)
                        val logged = info.periodStarts.size >= 2
                        NumberStepper("Durée du cycle", info.cycleLength, { v -> onUpdate { it.copy(cycleLength = v) } }, 20..45, suffix = " j")
                        if (logged) {
                            Text(
                                "Moyenne calculée sur tes cycles : ${Cycle.averageLength(info)} jours (utilisée pour les prévisions).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        NumberStepper("Durée des règles", info.periodLength, { v -> onUpdate { it.copy(periodLength = v) } }, 2..10, suffix = " j")
                    }
                }
                if (info.periodStarts.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Historique", style = MaterialTheme.typography.titleMedium)
                            val sorted = info.periodStarts.sortedDescending()
                            sorted.take(6).forEachIndexed { i, d ->
                                if (i > 0) HorizontalDivider()
                                Row {
                                    Text(d.format(dayMonthYear), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    sorted.getOrNull(i + 1)?.let {
                                        Text(
                                            "cycle de ${ChronoUnit.DAYS.between(it, d)} j",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Disclaimer(
                "Estimations basées sur la méthode du calendrier, à titre indicatif. " +
                    "Ce suivi n'est pas une méthode de contraception ni un outil de diagnostic.",
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (picking) {
        DatePickerModal(
            initial = today,
            maxDate = today,
            onDismiss = { picking = false },
            onConfirm = {
                onLogPeriod(it)
                picking = false
            },
        )
    }
}

@Composable
private fun PhaseCard(day: Int, length: Int, phase: CyclePhase, late: Long, until: Long, next: LocalDate) {
    val ring = AppTheme.status.period
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                    drawArc(
                        ring, -90f, 360f * (day.coerceAtMost(length).toFloat() / length), false,
                        Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Jour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$day", style = MaterialTheme.typography.headlineLarge)
                    Text("sur $length", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(phase.label, style = MaterialTheme.typography.titleLarge)
                Text(phase.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        late > 0 -> "Règles en retard de $late jour${if (late > 1) "s" else ""}"
                        until == 0L -> "Règles prévues aujourd'hui"
                        until == 1L -> "Règles prévues demain"
                        else -> "Règles dans $until jours"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.status.period,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun CalendarCard(info: CycleInfo, today: LocalDate) {
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val ym = YearMonth.parse(month)
    val status = AppTheme.status
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = ym.minusMonths(1).toString() }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mois précédent")
                }
                Text(
                    ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)).capitalized(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = ym.plusMonths(1).toString() }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Mois suivant")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                    Text(
                        it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            val first = ym.atDay(1)
            val lead = first.dayOfWeek.value - 1
            val cells = lead + ym.lengthOfMonth()
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..ym.lengthOfMonth()) {
                                val date = ym.atDay(dayNum)
                                val type = Cycle.dayType(info, date, today)
                                val (bg, fg, outlined) = when (type) {
                                    CycleDayType.PERIOD -> Triple(status.period, Color.White, false)
                                    CycleDayType.PREDICTED_PERIOD -> Triple(status.period.copy(alpha = 0.16f), MaterialTheme.colorScheme.onSurface, true)
                                    CycleDayType.OVULATION -> Triple(status.fertile, Color.White, false)
                                    CycleDayType.FERTILE -> Triple(status.fertile.copy(alpha = 0.2f), MaterialTheme.colorScheme.onSurface, false)
                                    CycleDayType.NONE -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurface, false)
                                }
                                var m = Modifier.fillMaxSize().clip(CircleShape).background(bg)
                                if (outlined) m = m.border(1.5.dp, status.period, CircleShape)
                                if (date == today) m = m.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                Box(m, contentAlignment = Alignment.Center) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = fg,
                                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                LegendDot(status.period, "Règles")
                LegendDot(status.period.copy(alpha = 0.3f), "Prévues")
                LegendDot(status.fertile.copy(alpha = 0.35f), "Fertilité")
                LegendDot(status.fertile, "Ovulation")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
