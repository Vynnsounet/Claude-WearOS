package com.medipote.app.ui.motivation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.medipote.app.data.DayEntry
import com.medipote.app.domain.Bloom
import com.medipote.app.domain.ChallengeStatus
import com.medipote.app.domain.DailyQuestions
import com.medipote.app.domain.RitualKind
import com.medipote.app.ui.components.SectionTitle
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val FACES = listOf("😫", "🙁", "😐", "🙂", "😄")
val MOOD_LABELS = listOf("Très mal", "Pas terrible", "Bof", "Bien", "Très bien")

/** Five faces, one tap. [value] 1..5 or null. */
@Composable
fun FacePicker(value: Int?, label: String, onPick: (Int?) -> Unit, modifier: Modifier = Modifier, height: Int = 48) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FACES.forEachIndexed { i, face ->
            val v = i + 1
            val selected = value == v
            Surface(
                onClick = { onPick(if (selected) null else v) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.weight(1f).height(height.dp).semantics { contentDescription = "$label : ${MOOD_LABELS[i]}" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(face, style = MaterialTheme.typography.titleLarge, modifier = Modifier.alpha(if (value == null || selected) 1f else 0.45f))
                }
            }
        }
    }
}

/** Home: today's mood in one tap, and the question of the day. */
@Composable
fun MoodCard(entry: DayEntry, onMood: (Int?) -> Unit, onAnswer: (String) -> Unit) {
    var answering by remember { mutableStateOf(false) }
    val question = DailyQuestions.of(entry.date)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Comment ça va ?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                entry.mental?.let { Text(MOOD_LABELS[it - 1], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            FacePicker(entry.mental, "Humeur", onMood)
            Surface(
                onClick = { answering = true },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("💭 $question", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        entry.reflection.ifBlank { "Touche pour répondre en une ligne" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.reflection.isBlank()) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
    if (answering) {
        var text by remember { mutableStateOf(entry.reflection) }
        AlertDialog(
            onDismissRequest = { answering = false },
            title = { Text("Question du jour") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(question, style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(value = text, onValueChange = { text = it.take(280) }, placeholder = { Text("Quelques mots…") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { answering = false; onAnswer(text.trim()) }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { answering = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun RitualCard(kind: RitualKind, onStart: () -> Unit) {
    Card(
        onClick = onStart,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(kind.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    if (kind == RitualKind.MORNING) "Ta nuit, tes prises du matin et ton humeur, en 30 secondes."
                    else "Tes prises du soir, ton journal et ton eau, en 30 secondes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            FilledTonalButton(onClick = onStart) { Text("C'est parti") }
        }
    }
}

@Composable
fun ChallengesCard(challenges: List<ChallengeStatus>, onOpen: () -> Unit) {
    if (challenges.isEmpty()) return
    SectionTitle("Défis de la semaine") {
        TextButton(onClick = onOpen) { Text("Badges") }
    }
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            challenges.forEach { ChallengeRow(it) }
            Text(
                "Facultatifs : des points en plus si tu les réussis, rien si tu ne les fais pas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ChallengeRow(c: ChallengeStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.challenge.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(c.challenge.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (c.done || c.complete) "✓ +${c.challenge.points}" else "${c.progress}/${c.challenge.target} · +${c.challenge.points}",
                style = MaterialTheme.typography.labelLarge,
                color = if (c.done || c.complete) com.medipote.app.ui.theme.AppTheme.status.good else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { c.progress.toFloat() / c.challenge.target },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            drawStopIndicator = {},
        )
    }
}

/** A little garden: one flower per day where the journal or the night was noted. */
@Composable
fun GardenCard(garden: List<Pair<LocalDate, Bloom>>, petName: String, title: Boolean = true) {
    val count = garden.count { it.second != Bloom.NONE }
    val primary = MaterialTheme.colorScheme.primary
    val soil = MaterialTheme.colorScheme.surfaceContainerHighest
    if (title) SectionTitle("Mon jardin") {
        Text("$count fleur${if (count > 1) "s" else ""} en ${garden.size / 7} semaines", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(48.dp * ((garden.size + 13) / 14)).semantics { contentDescription = "Jardin : $count fleurs sur les ${garden.size} derniers jours" },
            ) {
                val cols = 14
                val cw = size.width / cols
                val rowH = size.height / ((garden.size + cols - 1) / cols)
                garden.forEachIndexed { i, (_, bloom) ->
                    val col = i % cols
                    val row = i / cols
                    val c = Offset(cw * col + cw / 2, rowH * row + rowH * 0.62f)
                    drawOval(soil, Offset(c.x - cw * 0.32f, c.y + rowH * 0.18f), Size(cw * 0.64f, rowH * 0.16f))
                    when (bloom) {
                        Bloom.NONE -> drawCircle(soil, cw * 0.07f, c + Offset(0f, rowH * 0.18f))
                        Bloom.SLEEP -> drawGardenFlower(c, cw, rowH, Color(0xFF7986CB), Color(0xFFFFF59D))
                        Bloom.JOURNAL -> drawGardenFlower(c, cw, rowH, Color(0xFFF48FB1), Color(0xFFFFD54F))
                        Bloom.BOTH -> drawGardenFlower(c, cw, rowH, primary, Color(0xFFFFD54F), big = true)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(Color(0xFFF48FB1), "journal")
                Legend(Color(0xFF7986CB), "nuit")
                Legend(primary, "les deux")
            }
            Text(
                "Chaque jour où tu notes ton journal ou ta nuit, une fleur pousse dans le jardin de $petName.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DrawScope.drawGardenFlower(c: Offset, cw: Float, rowH: Float, petal: Color, heart: Color, big: Boolean = false) {
    val stemTop = c.y - rowH * (if (big) 0.42f else 0.34f)
    drawLine(Color(0xFF66BB6A), Offset(c.x, c.y + rowH * 0.2f), Offset(c.x, stemTop), cw * 0.06f)
    drawOval(Color(0xFF81C784), Offset(c.x, c.y - rowH * 0.05f), Size(cw * 0.22f, cw * 0.12f))
    val r = cw * (if (big) 0.17f else 0.13f)
    for (k in 0 until 5) {
        val a = k * 2 * PI / 5 - PI / 2
        drawCircle(petal, r * 0.75f, Offset(c.x + (cos(a) * r).toFloat(), stemTop + (sin(a) * r).toFloat()))
    }
    drawCircle(heart, r * 0.6f, Offset(c.x, stemTop))
}

@Composable
fun RecapReadyCard(onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎁", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Ton récap de la semaine est prêt", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Tes prises, tes nuits, ton humeur, en quelques cartes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

/** The garden tab: eight weeks of flowers, and what they mean. */
@Composable
fun GardenScreen(state: com.medipote.app.data.AppState, today: LocalDate) {
    val garden = remember(state.journal, state.sleep, today) { com.medipote.app.domain.Garden.of(state, today, 56) }
    val month = garden.takeLast(28)
    Column(
        Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "${garden.count { it.second != Bloom.NONE }} fleurs en 8 semaines",
            style = MaterialTheme.typography.headlineSmall,
        )
        GardenCard(garden, state.game.petName, title = false)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ces 4 dernières semaines", style = MaterialTheme.typography.titleMedium)
                Text("📖 ${month.count { it.second == Bloom.JOURNAL || it.second == Bloom.BOTH }} jours de journal", style = MaterialTheme.typography.bodyLarge)
                Text("🌙 ${month.count { it.second == Bloom.SLEEP || it.second == Bloom.BOTH }} nuits notées", style = MaterialTheme.typography.bodyLarge)
                Text("🌷 ${month.count { it.second == Bloom.BOTH }} jours avec les deux", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
