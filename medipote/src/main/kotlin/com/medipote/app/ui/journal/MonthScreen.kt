package com.medipote.app.ui.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.DayDots
import com.medipote.app.domain.DoseDots
import com.medipote.app.domain.MonthCalendar
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** The month at a glance: small dots for the doses, the mood and the symptoms; a tap opens the day. Never a score. */
@Composable
fun MonthScreen(state: AppState, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val weeks = remember(month) { MonthCalendar.weeks(month) }
    val dots = remember(state, month, today) { weeks.flatten().associateWith { MonthCalendar.dots(state, it, month, today) } }
    val title = month.month.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar(Char::uppercase) + " " + month.year
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Mois précédent") }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = MonthCalendar.canGoNext(month, today)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Mois suivant")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date -> DayCell(dots.getValue(date), today, Modifier.weight(1f)) { onOpenDay(date) } }
                    }
                }
            }
        }
        Legend()
    }
}

private fun describe(d: DayDots): String = buildString {
    append(d.date.dayOfMonth).append(' ').append(d.date.month.getDisplayName(TextStyle.FULL, Locale.FRENCH))
    when (d.doses) {
        DoseDots.ALL -> append(", prises toutes notées")
        DoseDots.SOME -> append(", prises en partie notées")
        DoseDots.NOT_NOTED -> append(", prises non notées")
        DoseDots.NONE -> Unit
    }
    if (d.mood != null) append(", humeur notée")
    if (d.symptoms) append(", symptômes notés")
    if (d.period) append(", règles")
}

@Composable
private fun DayCell(d: DayDots, today: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    val future = d.date.isAfter(today)
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .aspectRatio(0.85f)
            .clip(shape)
            .background(if (d.period) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(enabled = !future, onClick = onClick)
            .semantics { contentDescription = describe(d) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = if (!d.inMonth || future) 0.35f else 1f)
        Box(
            Modifier.size(26.dp).then(
                if (d.date == today) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                d.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge,
                color = if (d.date == today) MaterialTheme.colorScheme.onPrimary else ink,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 1.dp)) {
            when (d.doses) {
                DoseDots.ALL -> Dot(MaterialTheme.colorScheme.primary)
                DoseDots.SOME -> Dot(MaterialTheme.colorScheme.primary, hollow = true)
                // A day without a note is just an empty day: no mark, no verdict.
                DoseDots.NOT_NOTED, DoseDots.NONE -> Unit
            }
            if (d.mood != null) Dot(MaterialTheme.colorScheme.tertiary)
            if (d.symptoms) Dot(MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun Dot(color: Color, hollow: Boolean = false) {
    Box(
        Modifier.size(7.dp).clip(CircleShape).then(
            if (hollow) Modifier.border(BorderStroke(1.5.dp, color), CircleShape) else Modifier.background(color),
        ),
    )
}

@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendRow(MaterialTheme.colorScheme.primary, "Prises toutes notées (rond vide : en partie)")
        LegendRow(MaterialTheme.colorScheme.tertiary, "Humeur notée")
        LegendRow(MaterialTheme.colorScheme.secondary, "Symptômes notés")
        Text(
            "Un jour sans point, c'est juste un jour sans note. Touche un jour pour le voir ou le compléter.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Dot(color)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
