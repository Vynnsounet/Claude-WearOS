package com.medipote.app.ui.motivation

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Schedule
import com.medipote.app.ui.stats.PeriodSelector
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** What a pixel shows. */
enum class PixelMode(val label: String) { MOOD("Humeur"), FORM("Forme"), SLEEP("Sommeil"), DOSES("Prises") }

/** Five levels, from a hard day to a great one. Never red: a hard day isn't a fault. */
private val MOOD_PALETTE = listOf(Color(0xFF8E7CC3), Color(0xFF9FB7E0), Color(0xFFF2D68A), Color(0xFF9DD49A), Color(0xFF4CAF7A))

/** Level 1..5 of a day in a mode, or null when nothing was noted. */
fun pixelLevel(state: AppState, date: LocalDate, mode: PixelMode, now: LocalDateTime, logs: Map<String, com.medipote.app.data.DoseLog>): Int? = when (mode) {
    PixelMode.MOOD -> state.entryFor(date).mental
    PixelMode.FORM -> state.entryFor(date).physical
    PixelMode.SLEEP -> state.sleep.firstOrNull { it.date == date }?.let { n ->
        val h = n.asleepMinutes / 60f
        when {
            h < 5 -> 1
            h < 6 -> 2
            h < 7 -> 3
            h < 8 -> 4
            else -> 5
        }
    }
    PixelMode.DOSES -> {
        val items = Schedule.itemsOn(state.treatments, logs, date, now).filter { it.state == DoseState.TAKEN || it.state == DoseState.MISSED }
        if (items.isEmpty()) null else {
            val r = items.count { it.state == DoseState.TAKEN }.toFloat() / items.size
            when {
                r >= 1f -> 5
                r >= 0.75f -> 4
                r >= 0.5f -> 3
                r > 0f -> 2
                else -> 1
            }
        }
    }
}

private fun legend(mode: PixelMode) = when (mode) {
    PixelMode.MOOD, PixelMode.FORM -> listOf("Très mal", "Pas terrible", "Bof", "Bien", "Très bien")
    PixelMode.SLEEP -> listOf("< 5 h", "5–6 h", "6–7 h", "7–8 h", "8 h et +")
    PixelMode.DOSES -> listOf("Aucune", "Quelques-unes", "La moitié", "Presque toutes", "Toutes")
}

/** The year in pixels: one square per day, coloured by the day's mood, form, sleep or doses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearScreen(state: AppState, now: LocalDateTime, onOpenDay: (LocalDate) -> Unit, onBack: () -> Unit) {
    val today = now.toLocalDate()
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var mode by rememberSaveable { mutableStateOf(PixelMode.MOOD) }
    val logs = remember(state.doseLogs) { state.doseLogs.associateBy { it.key } }
    val levels = remember(state, year, mode) {
        (1..12).associateWith { m ->
            val month = java.time.YearMonth.of(year, m)
            (1..month.lengthOfMonth()).map { d ->
                val date = month.atDay(d)
                if (date.isAfter(today)) -1 else pixelLevel(state, date, mode, now, logs) ?: 0
            }
        }
    }
    val noted = levels.values.sumOf { l -> l.count { it > 0 } }
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val future = MaterialTheme.colorScheme.surfaceContainerLow
    val ring = MaterialTheme.colorScheme.onSurface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mon année en pixels") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeriodSelector(PixelMode.entries.map { it to it.label }, mode, { mode = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Année précédente") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$year", style = MaterialTheme.typography.headlineSmall)
                    Text("$noted jour${if (noted > 1) "s" else ""} en couleur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { year++ }, enabled = year < today.year) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Année suivante") }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Spacer(Modifier.width(22.dp))
                        Month.entries.forEach { m ->
                            Text(
                                m.getDisplayName(TextStyle.NARROW, Locale.FRANCE),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row {
                        Column(Modifier.width(22.dp)) {
                            (1..31).forEach { d ->
                                Box(Modifier.height(15.dp), contentAlignment = Alignment.CenterStart) {
                                    if (d == 1 || d % 5 == 0) Text("$d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Canvas(
                            Modifier
                                .weight(1f)
                                .height(15.dp * 31)
                                .semantics { contentDescription = "Année $year en pixels, mode ${mode.label} : $noted jours notés" }
                                .pointerInput(year, mode) {
                                    detectTapGestures { p ->
                                        val m = (p.x / (size.width / 12f)).toInt() + 1
                                        val d = (p.y / (size.height / 31f)).toInt() + 1
                                        val month = java.time.YearMonth.of(year, m.coerceIn(1, 12))
                                        if (d <= month.lengthOfMonth()) {
                                            val date = month.atDay(d)
                                            if (!date.isAfter(today)) onOpenDay(date)
                                        }
                                    }
                                },
                        ) {
                            val cw = size.width / 12
                            val ch = size.height / 31
                            val gap = 2.dp.toPx()
                            levels.forEach { (m, days) ->
                                days.forEachIndexed { i, level ->
                                    val color = when {
                                        level < 0 -> future
                                        level == 0 -> empty
                                        else -> MOOD_PALETTE[level - 1]
                                    }
                                    val tl = Offset((m - 1) * cw + gap / 2, i * ch + gap / 2)
                                    drawRoundRect(color, tl, Size(cw - gap, ch - gap), CornerRadius(3.dp.toPx()))
                                    if (year == today.year && m == today.monthValue && i + 1 == today.dayOfMonth) {
                                        drawRoundRect(ring, tl, Size(cw - gap, ch - gap), CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                legend(mode).forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))) { Canvas(Modifier.fillMaxSize()) { drawRect(MOOD_PALETTE[i]) } }
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "Touche un jour pour revoir tout ce que tu as noté. Un jour gris : rien de noté, et ce n'est pas grave.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
