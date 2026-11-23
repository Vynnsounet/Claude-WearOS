package com.lingoquest.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingoquest.app.ui.theme.game
import com.lingoquest.engine.Cefr
import com.lingoquest.engine.Direction
import com.lingoquest.engine.Progression

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, vm: GameViewModel) {
    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("LingoQuest", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

            LevelCard(state)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Meilleure série", "${state.profile.bestStep}", Modifier.weight(1f))
                StatCard("Mots débloqués", "${state.unlockedWords}/${state.totalWords}", Modifier.weight(1f))
                StatCard("Séries jouées", "${state.profile.runs}", Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Je traduis", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(Direction.FR_TO_EN to "Français → English", Direction.EN_TO_FR to "English → Français")
                    options.forEachIndexed { index, (direction, label) ->
                        SegmentedButton(
                            selected = state.profile.direction == direction,
                            onClick = { vm.setDirection(direction) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(label) }
                    }
                }
            }

            Button(onClick = vm::startRun, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Jouer", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = vm::openWords, modifier = Modifier.fillMaxWidth()) { Text("Mes mots") }

            HowToPlay()
        }
    }
}

@Composable
private fun LevelCard(state: UiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Niveau ${state.level}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) {
                    Text(
                        state.cefr.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            LinearProgressIndicator(progress = { state.levelProgress }, modifier = Modifier.fillMaxWidth().height(10.dp))
            val xpLine = if (state.xpForNext > 0) "${state.xpInLevel} / ${state.xpForNext} XP" else "Niveau maximum — ${state.profile.xp} XP"
            Text(xpLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (state.level < Progression.MAX_LEVEL) {
                val toNextCefr = Progression.LEVELS_PER_CEFR * (state.cefr.ordinal + 1) + 1 - state.level
                val cefrLine = if (state.cefr != Cefr.C1) {
                    "Niveau de langue ${Cefr.entries[state.cefr.ordinal + 1].name} dans $toNextCefr niveau" + if (toNextCefr > 1) "x" else ""
                } else "Dernier niveau de langue atteint : C1"
                Text(
                    "+${state.nextLevelWords} mots au prochain niveau · $cefrLine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.game.xp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HowToPlay() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Comment jouer", style = MaterialTheme.typography.titleMedium)
            val rules = listOf(
                "Traduis chaque mot, groupe nominal ou phrase. Les défis s'allongent à chaque bonne réponse.",
                "Chaque bonne réponse rapporte de l'XP, de plus en plus loin dans la série.",
                "3 erreurs : la série s'arrête et tu repars de zéro (ton XP et ton niveau sont conservés).",
                "Chaque niveau débloque de nouveaux mots ; tous les 25 niveaux, le niveau de langue monte (A1 → A2 → B1 → B2 → C1).",
            )
            rules.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
