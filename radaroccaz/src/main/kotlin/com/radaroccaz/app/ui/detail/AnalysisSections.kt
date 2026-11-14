package com.radaroccaz.app.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.CarCrash
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radaroccaz.app.analysis.Analysis
import com.radaroccaz.app.analysis.Confidence
import com.radaroccaz.app.analysis.Finding
import com.radaroccaz.app.analysis.Severity
import com.radaroccaz.app.analysis.VehicleAnalysis
import com.radaroccaz.app.data.AccidentStatus
import com.radaroccaz.app.data.CtStatus
import com.radaroccaz.app.data.Fact
import com.radaroccaz.app.data.FactSource
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.data.MileageReading
import com.radaroccaz.app.ui.DeepState
import com.radaroccaz.app.ui.DeepStatus
import com.radaroccaz.app.ui.components.Pill
import com.radaroccaz.app.ui.components.ScoreRing
import com.radaroccaz.app.ui.components.SectionCard
import com.radaroccaz.app.ui.components.verdictColors
import com.radaroccaz.app.ui.theme.LocalExtraColors
import com.radaroccaz.app.domain.Verdict

@Composable
fun ReliabilityCard(
    analysis: Analysis,
    deep: DeepStatus?,
    detailsLoaded: Boolean,
    onRetryDeep: () -> Unit,
    onEditHistory: () -> Unit,
) {
    val (color, container) = verdictColors(Verdict.of(analysis.reliability))
    SectionCard("Fiabilité et historique", icon = Icons.Rounded.VerifiedUser) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(analysis.reliability, size = 60.dp, stroke = 6.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Pill(analysis.confidence.label, color, container)
                Spacer(Modifier.height(4.dp))
                Text(
                    when (analysis.confidence) {
                        Confidence.LOW -> "L'annonce dit peu de choses sur le passé de la voiture. Demandez le rapport HistoVec."
                        Confidence.MEDIUM -> "Une partie de l'historique est connue. Complétez-la avec le rapport HistoVec."
                        Confidence.HIGH -> "Propriétaires, sinistres et entretien sont renseignés."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val counts = listOf(
            Triple(analysis.critical.size, "critique", Severity.CRITICAL),
            Triple(analysis.warnings.size, "à vérifier", Severity.WARNING),
            Triple(analysis.positives.size, "point(s) fort(s)", Severity.POSITIVE),
        ).filter { it.first > 0 }
        if (counts.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                counts.forEach { (n, label, sev) ->
                    val (fg, bg) = severityColors(sev)
                    Pill("$n $label", fg, bg, icon = severityIcon(sev))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        // State of the full-page reading
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                deep?.state == DeepState.LOADING -> {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Lecture de l'annonce complète…", style = MaterialTheme.typography.bodySmall)
                }
                detailsLoaded -> {
                    Icon(Icons.Rounded.CheckCircle, null, tint = LocalExtraColors.current.great, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Annonce complète analysée", style = MaterialTheme.typography.bodySmall)
                }
                deep?.state == DeepState.FAILED -> {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(deep.message.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetryDeep) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Réessayer")
                    }
                }
                else -> Text("Analyse faite sur les données de la recherche.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onEditHistory, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Compléter l'historique (HistoVec)")
        }
    }
}

@Composable
fun severityColors(sev: Severity): Pair<Color, Color> {
    val x = LocalExtraColors.current
    return when (sev) {
        Severity.CRITICAL -> x.weak to x.weakContainer
        Severity.WARNING -> x.fair to x.fairContainer
        Severity.POSITIVE -> x.great to x.greatContainer
        Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHighest
    }
}

fun severityIcon(sev: Severity): ImageVector = when (sev) {
    Severity.CRITICAL -> Icons.Rounded.Error
    Severity.WARNING -> Icons.Rounded.Warning
    Severity.POSITIVE -> Icons.Rounded.CheckCircle
    Severity.INFO -> Icons.Rounded.Info
}

/** A list of findings, each with its icon, explanation and where it comes from. */
@Composable
fun FindingsCard(title: String, icon: ImageVector, findings: List<Finding>) {
    if (findings.isEmpty()) return
    SectionCard(title, icon = icon) {
        findings.forEachIndexed { i, f ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            FindingRow(f)
        }
    }
}

@Composable
private fun FindingRow(f: Finding) {
    val (fg, bg) = severityColors(f.severity)
    Row {
        Box(Modifier.size(30.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(severityIcon(f.severity), contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (f.impact != 0) {
                    Text(
                        (if (f.impact > 0) "+" else "−") + kotlin.math.abs(f.impact),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            f.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (f.evidence != null || f.source != null) {
                Text(
                    listOfNotNull(f.evidence?.let { "« $it »" }, f.source?.label).joinToString(" — "),
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** What is known, field by field, with "Non renseigné" for gaps. */
@Composable
fun HistoryFactsCard(analysis: Analysis) {
    val h = analysis.history
    SectionCard("Historique du véhicule", icon = Icons.Rounded.History) {
        FactRow(Icons.Rounded.People, "Propriétaires", h.owners) { if (it == 1) "1 (première main)" else "$it" }
        FactRow(Icons.Rounded.CarCrash, "Accidents / sinistres", h.accident, bad = { it == AccidentStatus.DAMAGED || it == AccidentStatus.SEVERE }) { it.label }
        FactRow(Icons.Rounded.Build, "Entretien", h.serviceRecords, bad = { !it }) { if (it) "Suivi (carnet / factures)" else "Non suivi" }
        FactRow(Icons.Rounded.FactCheck, "Contrôle technique", h.technicalInspection, bad = { it == CtStatus.COUNTER_VISIT || it == CtStatus.TO_DO }) { ct ->
            ct.label + (h.lastInspection?.let { " · ${VehicleAnalysis.monthLabel(it.value)}" } ?: "")
        }
        FactRow(Icons.Rounded.Speed, "Kilométrage garanti", h.mileageGuaranteed, bad = { !it }) { if (it) "Oui" else "Non garanti" }
        FactRow(Icons.Rounded.Public, "Origine", h.imported) { if (it) "Importée" else "France" }
        FactRow(Icons.Rounded.Shield, "Garantie", h.warrantyMonths) { if (it == 0) "Aucune" else "$it mois" }
        h.nonSmoker?.let { FactRow(Icons.Rounded.SmokeFree, "Fumeur", it) { "Non-fumeur" } }
    }
}

@Composable
private fun <T> FactRow(icon: ImageVector, label: String, fact: Fact<T>?, bad: (T) -> Boolean = { false }, format: (T) -> String) {
    val isBad = fact != null && bad(fact.value)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (isBad) LocalExtraColors.current.weak else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                fact?.let { format(it.value) } ?: "Non renseigné",
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    fact == null -> MaterialTheme.colorScheme.outline
                    isBad -> LocalExtraColors.current.weak
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        fact?.let { SourceTag(it.source) }
    }
}

@Composable
fun SourceTag(source: FactSource) {
    val strong = source == FactSource.RAPPORT || source == FactSource.VOUS
    Text(
        source.label,
        style = MaterialTheme.typography.labelSmall,
        color = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Odometer readings over time, as a line and as a table. */
@Composable
fun MileageCard(analysis: Analysis) {
    val m = analysis.mileage
    SectionCard("Relevés kilométriques", icon = Icons.Rounded.Speed) {
        m.annualKm?.let {
            Text(
                "≈ ${Format.km(it)} par an · moyenne pour cette énergie ~${Format.km(m.expectedAnnualKm)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (m.rollback) {
            val x = LocalExtraColors.current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.weakContainer).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Error, null, tint = x.weak)
                Spacer(Modifier.width(8.dp))
                Text("Un relevé ancien est plus élevé que le kilométrage actuel.", color = x.weak, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (m.readings.size >= 2) {
            MileageChart(m.readings, Modifier.fillMaxWidth().height(150.dp))
            Spacer(Modifier.height(8.dp))
        }
        if (m.readings.size <= 1) {
            Text(
                "Un seul relevé connu (l'annonce). Les relevés des contrôles techniques, dans le rapport HistoVec, permettent de vérifier le compteur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        m.readings.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(VehicleAnalysis.monthLabel(r.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
                Text(Format.km(r.km), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(r.label.ifEmpty { r.source.label }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MileageChart(readings: List<MileageReading>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val bad = LocalExtraColors.current.weak
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val axis = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = ink)
    fun monthIndex(d: String) = d.take(4).toInt() * 12 + (d.drop(5).take(2).toIntOrNull() ?: 6)
    Canvas(modifier) {
        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val xs = readings.map { monthIndex(it.date) }
        val minX = xs.min()
        val spanX = (xs.max() - minX).coerceAtLeast(1)
        val maxKm = readings.maxOf { it.km } * 1.08f
        val minKm = (readings.minOf { it.km } * 0.85f).coerceAtLeast(0f)
        fun px(i: Int) = Offset(
            left + (right - left) * (xs[i] - minX) / spanX,
            bottom - (bottom - top) * ((readings[i].km - minKm) / (maxKm - minKm).coerceAtLeast(1f)),
        )
        drawLine(axis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        for (i in 0 until readings.lastIndex) {
            val down = readings[i + 1].km < readings[i].km - 1_000
            drawLine(if (down) bad else line, px(i), px(i + 1), strokeWidth = 2.dp.toPx())
        }
        readings.indices.forEach { i ->
            val p = px(i)
            val down = i > 0 && readings[i].km < readings.take(i).maxOf { it.km } - 1_000
            drawCircle(surface, radius = 6.dp.toPx(), center = p)
            drawCircle(if (down) bad else line, radius = 4.dp.toPx(), center = p)
        }
        // Selective labels: first and last date under the axis, last mileage above its point.
        val first = measurer.measure(VehicleAnalysis.monthLabel(readings.first().date), labelStyle)
        drawText(first, topLeft = Offset(left, bottom + 4.dp.toPx()))
        val last = measurer.measure(VehicleAnalysis.monthLabel(readings.last().date), labelStyle)
        drawText(last, topLeft = Offset(right - last.size.width, bottom + 4.dp.toPx()))
        val lastKm = measurer.measure(Format.km(readings.last().km), labelStyle)
        val lp = px(readings.lastIndex)
        drawText(lastKm, topLeft = Offset((lp.x - lastKm.size.width).coerceAtLeast(left), (lp.y - lastKm.size.height - 6.dp.toPx()).coerceAtLeast(0f)))
    }
}

@Composable
fun QuestionsCard(questions: List<String>) {
    if (questions.isEmpty()) return
    SectionCard("Questions à poser au vendeur", icon = Icons.Rounded.QuestionAnswer) {
        questions.forEachIndexed { i, q ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                Text(q, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Link to the official report when the listing gives one. */
@Composable
fun ReportLink(url: String, onOpenUrl: (String) -> Unit) {
    TextButton(onClick = { onOpenUrl(url) }) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Ouvrir le rapport HistoVec de l'annonce")
    }
}
