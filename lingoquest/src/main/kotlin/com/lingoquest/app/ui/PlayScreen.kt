package com.lingoquest.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lingoquest.app.ui.theme.game
import com.lingoquest.engine.Challenge
import com.lingoquest.engine.Lang
import com.lingoquest.engine.Stage


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(state: UiState, vm: GameViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Série : ${state.step}", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.Rounded.Close, contentDescription = "Quitter") }
                },
                actions = {
                    Text("+${state.runXp} XP", color = MaterialTheme.game.xp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Lives(state.lives)
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val summary = state.runOver
            if (summary != null) {
                SummaryContent(state, summary, onReplay = vm::startRun, onHome = vm::back)
            } else {
                QuestionContent(state, vm)
            }
        }
    }
}

@Composable
private fun Lives(lives: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i ->
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = if (i < lives) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun QuestionContent(state: UiState, vm: GameViewModel) {
    val feedback = state.feedback
    // While the answer is being reviewed, keep showing the question that was answered.
    val challenge: Challenge = feedback?.challenge ?: state.challenge ?: return
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                challenge.stage.label(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            PromptCard(challenge)
            if (challenge.typed) {
                TypedAnswer(challenge, state, vm, enabled = feedback == null)
            } else {
                TileAnswer(challenge, state, vm, enabled = feedback == null)
            }
        }
        if (feedback != null) {
            FeedbackPanel(feedback, onNext = vm::next)
        } else {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!challenge.typed) {
                    OutlinedButton(onClick = vm::clearTiles, enabled = state.picked.isNotEmpty()) { Text("Effacer") }
                }
                val ready = if (challenge.typed) state.typed.isNotBlank() else state.picked.isNotEmpty()
                Button(onClick = vm::submit, enabled = ready, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text("Valider", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PromptCard(challenge: Challenge) {
    val from = challenge.direction.from
    val to = challenge.direction.to
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${from.label().replaceFirstChar { it.uppercase() }} → ${to.label()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(challenge.prompt, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun TypedAnswer(challenge: Challenge, state: UiState, vm: GameViewModel, enabled: Boolean) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(challenge) {
        try {
            focus.requestFocus()
        } catch (e: IllegalStateException) {
            // The field is not on screen yet; the player can tap it.
        }
    }
    OutlinedTextField(
        value = state.typed,
        onValueChange = vm::onTyped,
        enabled = enabled,
        singleLine = true,
        label = { Text("Ta traduction en ${challenge.direction.to.label()}") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.submit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileAnswer(challenge: Challenge, state: UiState, vm: GameViewModel, enabled: Boolean) {
    // The sentence being built: tap a tile to take it back.
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        FlowRow(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.picked.isEmpty()) {
                Text(
                    "Touche les mots dans l'ordre",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.picked.forEachIndexed { position, index ->
                Tile(challenge.bank[index], enabled = enabled) { vm.unpickTile(position) }
            }
        }
    }
    // The tiles to choose from.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        challenge.bank.forEachIndexed { index, tile ->
            val used = index in state.picked
            Tile(tile, enabled = enabled && !used, faded = used) { vm.pickTile(index) }
        }
    }
}

@Composable
private fun Tile(text: String, enabled: Boolean, faded: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = if (faded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (faded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = if (faded) 0.dp else 1.dp,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun FeedbackPanel(feedback: Feedback, onNext: () -> Unit) {
    val outcome = feedback.outcome
    val container = if (outcome.correct) MaterialTheme.game.goodContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (outcome.correct) MaterialTheme.game.onGoodContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (outcome.correct) Icons.Rounded.Check else Icons.Rounded.Close, contentDescription = null)
                Text(
                    if (outcome.correct) "Bravo !  +${outcome.xpGained} XP" else "Raté…",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (outcome.milestoneBonus > 0) {
                Text("Bonus de série : +${outcome.milestoneBonus} XP", style = MaterialTheme.typography.titleMedium)
            }
            if (!outcome.correct) {
                Text("La bonne réponse :", style = MaterialTheme.typography.labelLarge)
                Text(outcome.expected, style = MaterialTheme.typography.titleLarge)
                if (outcome.runOver) {
                    Text("Dernière vie perdue : la série s'arrête.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Il te reste ${outcome.lives} vie" + if (outcome.lives > 1) "s." else ".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (outcome.accentWarning) {
                Text("Attention aux accents : ${outcome.expected}", style = MaterialTheme.typography.bodyMedium)
            }
            outcome.levelUp?.let { up ->
                Text(
                    "Niveau ${up.level} !" + if (up.cefrChanged) "  Tu passes au niveau ${up.cefr.name} !" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (up.newWords.isNotEmpty()) {
                    val shown = up.newWords.take(8).joinToString(", ") { it.pairText() }
                    val more = up.newWords.size - 8
                    Text(
                        "Nouveaux mots : $shown" + if (more > 0) " (+$more)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = content,
                    contentColor = container,
                ),
            ) { Text(if (outcome.runOver) "Voir le bilan" else "Continuer", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun SummaryContent(state: UiState, summary: RunSummary, onReplay: () -> Unit, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Série terminée", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${summary.reached}",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (summary.reached == 1) "bonne réponse d'affilée" else "bonnes réponses d'affilée",
            style = MaterialTheme.typography.titleMedium,
        )
        if (summary.newBest) {
            Text("Nouveau record !", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.game.xp)
        } else {
            Text("Record : ${summary.best}", style = MaterialTheme.typography.bodyLarge)
        }
        Text("+${summary.xp} XP gagnés dans cette série", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.game.xp)
        Text(
            "Niveau ${state.level} · ${state.cefr.name}",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            "Ta prochaine série repart des mots simples.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onReplay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Rejouer") }
        TextButton(onClick = onHome) { Text("Accueil") }
    }
}
