package com.medipote.app.ui.motivation

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.Badge
import com.medipote.app.domain.Badges
import com.medipote.app.domain.Challenges
import com.medipote.app.domain.Game
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.dayMonthYear
import com.medipote.app.ui.pet.ItemPreview
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(state: AppState, today: LocalDate, onBack: () -> Unit, embedded: Boolean = false) {
    val earned = state.game.badges
    Scaffold(
        contentWindowInsets = if (embedded) androidx.compose.foundation.layout.WindowInsets(0) else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!embedded) TopAppBar(
                title = { Text("Badges et défis") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🏅", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${earned.size} / ${Badges.all.size} badges", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (Game.jokerAvailable(state.game, today)) "🛡️ Joker de série disponible cette semaine : un jour oublié ne cassera pas ta série."
                            else "🛡️ Joker utilisé cette semaine, il revient lundi.",
                            style = MaterialTheme.typography.bodyMedium,
                            
                        )
                    }
                }
            }

            SectionTitle("Défis de la semaine")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Challenges.status(state, today).forEach { ChallengeRow(it) }
                    Text(
                        "Nouveaux défis chaque lundi. ${state.game.challengesDone.size} réussi${if (state.game.challengesDone.size > 1) "s" else ""} depuis le début.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Earned badges in sight; the ones still to win folded, closest first.
            val (won, locked) = Badges.all.partition { it.id in earned }
            SectionTitle("Ma collection")
            if (won.isEmpty()) {
                Text(
                    "Ton premier badge t'attend : regarde ci-dessous lesquels sont les plus proches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BadgeGrid(won.sortedByDescending { earned[it.id] }, state, earned)
            if (locked.isNotEmpty()) {
                com.medipote.app.ui.components.MoreOptions(
                    "Les plus proches d'abord",
                    initiallyOpen = won.isEmpty(),
                    label = "${locked.size} badge${if (locked.size > 1) "s" else ""} à débloquer",
                ) {
                    BadgeGrid(locked.sortedByDescending { it.progress(state).coerceAtMost(it.target).toFloat() / it.target }, state, earned)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BadgeGrid(badges: List<Badge>, state: AppState, earned: Map<String, LocalDate>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        badges.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { b -> BadgeTile(b, state, earned[b.id], Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeTile(b: Badge, state: AppState, earnedOn: LocalDate?, modifier: Modifier) {
    val reward = Badges.reward(b.id)
    val got = earnedOn != null
    Card(
        colors = CardDefaults.cardColors(containerColor = if (got) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                shape = CircleShape,
                color = if (got) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (got) b.emoji else "🔒", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.alpha(if (got) 1f else 0.6f))
                }
            }
            Text(b.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(b.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (got) {
                Text("le ${earnedOn!!.format(dayMonthYear)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } else {
                val p = b.progress(state).coerceAtMost(b.target)
                LinearProgressIndicator(
                    progress = { p.toFloat() / b.target },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    drawStopIndicator = {},
                )
                Text("$p / ${b.target}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            reward?.let { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape)) { ItemPreview(item.id, item.slot.name, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (got) "${item.name} débloqué" else "Débloque : ${item.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Shown when a badge is earned. */
@Composable
fun BadgeEarnedDialog(badge: Badge, onDismiss: () -> Unit, onOpenShop: () -> Unit) {
    val reward = Badges.reward(badge.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(badge.emoji, style = MaterialTheme.typography.displayMedium) },
        title = { Text("Nouveau badge : ${badge.title} !", textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(badge.description, textAlign = TextAlign.Center)
                reward?.let { item ->
                    Box(Modifier.size(96.dp).clip(MaterialTheme.shapes.large)) { ItemPreview(item.id, item.slot.name, Modifier.fillMaxSize()) }
                    Text("Objet exclusif débloqué : ${item.name}", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            if (reward != null) TextButton(onClick = onOpenShop) { Text("L'équiper") } else TextButton(onClick = onDismiss) { Text("Super !") }
        },
        dismissButton = { if (reward != null) TextButton(onClick = onDismiss) { Text("Plus tard") } },
    )
}
