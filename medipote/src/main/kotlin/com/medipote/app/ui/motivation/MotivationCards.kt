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
import com.medipote.app.domain.ChallengeStatus
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
fun MoodCard(entry: DayEntry, onMood: (Int?) -> Unit, foldWhenDone: Boolean = false) {
    var unfolded by remember { mutableStateOf(false) }
    if (foldWhenDone && !unfolded && entry.mental != null) {
        // Mood noted: one quiet line, tap to change.
        Surface(
            onClick = { unfolded = true },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(FACES[entry.mental - 1], style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Humeur du jour : ${MOOD_LABELS[entry.mental - 1].lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("Modifier", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Comment ça va ?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                entry.mental?.let { Text(MOOD_LABELS[it - 1], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            FacePicker(entry.mental, "Humeur", { v -> unfolded = false; onMood(v) })
        }
    }
}

@Composable
fun RitualCard(kind: RitualKind, onStart: () -> Unit) {
    Card(
        onClick = onStart,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(kind.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (kind == RitualKind.MORNING) "Ta nuit, tes prises du matin et ton humeur, en 30 secondes."
                    else "Tes prises du soir, ton journal et ton eau, en 30 secondes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        com.medipote.app.ui.components.InfoButton("Défis de la semaine", "Facultatifs : des points en plus si tu les réussis, rien si tu ne les fais pas. Nouveaux défis chaque lundi.")
        TextButton(onClick = onOpen) { Text("Badges") }
    }
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            challenges.forEach { ChallengeRow(it) }
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

