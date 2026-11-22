package com.medipote.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.medipote.app.ai.AiAnswer
import com.medipote.app.ai.AiFailureKind
import com.medipote.app.ai.AiSessionState
import com.medipote.app.data.AiSettings
import com.medipote.app.data.AiShare
import com.medipote.app.domain.Concern
import com.medipote.app.domain.Urgency
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.theme.AppTheme
import com.medipote.app.ui.understand.ConcernCard

/** The label of the button that opens the assistant from the other screens. */
const val ASK_AI = "🤖  Demander à l'IA"

@Composable
fun AskAiButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) { Text(ASK_AI) }
}

/**
 * « Intelligence artificielle »: what the assistant is, exactly what it reads, how it is paid for, and the choice to
 * turn it on. Nothing is sent before the person has read this page, chosen what to share and given their own key.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSetupScreen(
    ai: AiSettings,
    hasKey: Boolean,
    preview: String,
    onShares: (Set<AiShare>) -> Unit,
    onActivate: (key: String?, (AiFailureKind?) -> Unit) -> Unit,
    onDisable: () -> Unit,
    onOpenConsole: () -> Unit,
    onBack: () -> Unit,
) {
    var key by rememberSaveable { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var understood by rememberSaveable { mutableStateOf(ai.enabled) }
    var checking by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AiFailureKind?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intelligence artificielle") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (ai.enabled && hasKey) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Text("✅  L'IA est activée. Touche « Demander à l'IA » sur un symptôme, ton bilan, ton cycle ou ton sommeil.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
                }
            }
            Text(
                "Un assistant, Claude (une IA d'Anthropic), peut lire tes notes pour t'expliquer ce que tu ressens dans ton contexte, " +
                    "proposer des façons de te soulager sans médicament et dire quand voir un médecin.",
                style = MaterialTheme.typography.bodyLarge,
            )

            SectionTitle("Ce qui part sur Internet")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Seulement quand tu touches « Demander à l'IA », jamais en arrière-plan.", style = MaterialTheme.typography.bodyMedium)
                    Text("Un texte écrit par l'appli à partir de ce que tu autorises ci-dessous part vers Anthropic, avec ta propre clé. La politique de confidentialité d'Anthropic s'applique à ce texte.", style = MaterialTheme.typography.bodyMedium)
                    Text("Jamais : ton nom, tes contacts, ta date de naissance, tes documents, ni le texte libre de tes notes et de ton journal.", style = MaterialTheme.typography.bodyMedium)
                    Text("L'IA peut se tromper. Ce qu'elle écrit n'est pas un avis médical, et les règles de l'appli passent toujours avant elle.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionTitle("Ce que l'IA a le droit de lire")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AiShare.entries.forEach { share ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(share.label, style = MaterialTheme.typography.bodyLarge)
                                Text(share.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = share in ai.shares,
                                onCheckedChange = { on -> onShares(if (on) ai.shares + share else ai.shares - share) },
                            )
                        }
                    }
                    Text("Moins tu partages, moins les explications sont précises.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = { showPreview = !showPreview }) { Text(if (showPreview) "Masquer l'exemple" else "👁️  Voir exactement ce qui serait envoyé") }
            if (showPreview) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                    Text(preview.ifBlank { "Rien à envoyer pour l'instant : tu n'as pas encore de notes." }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                }
            }

            SectionTitle("Ta clé")
            Text(
                "L'appli n'a pas de serveur : l'IA se paie avec ta propre clé d'accès, que tu crées sur la console d'Anthropic. " +
                    "Une demande coûte quelques centimes. La clé est gardée chiffrée sur ce téléphone, jamais dans une sauvegarde.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenConsole, modifier = Modifier.fillMaxWidth()) { Text("🔑  Ouvrir la console d'Anthropic") }
            OutlinedTextField(
                value = key, onValueChange = { key = it.trim(); failure = null },
                label = { Text(if (hasKey) "Remplacer ma clé (facultatif)" else "Coller ma clé (sk-ant-…)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Cacher" else "Voir") } },
            )
            failure?.let { Text(it.message, style = MaterialTheme.typography.bodyMedium, color = AppTheme.status.critical) }

            if (!ai.enabled) {
                Row(
                    Modifier.fillMaxWidth().toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = understood, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "J'ai compris : ces informations de santé seront envoyées à Anthropic quand je demanderai de l'aide à l'IA.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            if (!ai.enabled || key.isNotBlank()) {
                Button(
                    onClick = {
                        checking = true; failure = null
                        onActivate(key.takeIf { it.isNotBlank() }) { result -> checking = false; failure = result; if (result == null) key = "" }
                    },
                    enabled = !checking && (ai.enabled || understood) && (key.isNotBlank() || hasKey),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (ai.enabled) "Vérifier et enregistrer la clé" else "Vérifier ma clé et activer l'IA")
                }
            }
            if (ai.enabled || hasKey) {
                OutlinedButton(onClick = { confirmDisable = true }, modifier = Modifier.fillMaxWidth()) { Text("Désactiver l'IA et supprimer ma clé") }
            }
            Disclaimer(
                "Pas de nom de médicament, pas de dose, pas de diagnostic : l'appli retient toute réponse qui en contient. En cas de malaise ou de doute : le 15. Si ça ne va pas moralement : le 3114.",
                important = true,
            )
        }
    }
    if (confirmDisable) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Désactiver l'IA ?") },
            text = { Text("Ta clé sera supprimée de ce téléphone. Rien d'autre ne change : tes notes restent là.") },
            confirmButton = { TextButton(onClick = { confirmDisable = false; onDisable() }) { Text("Désactiver") } },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Annuler") } },
        )
    }
}

/**
 * The assistant on a topic: what the app's own rules found first (always, and whatever the AI says), then what the
 * AI answers, and a field to ask more. The conversation lives in memory only.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    title: String,
    session: AiSessionState,
    rules: List<Concern>,
    onAsk: (String) -> Unit,
    onRetry: () -> Unit,
    onCall: (String) -> Unit,
    onBook: () -> Unit,
    onPrepare: (() -> Unit)?,
    onDismiss: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var question by rememberSaveable { mutableStateOf("") }
    var showSent by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Assistant IA"); Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (rules.isNotEmpty()) {
                SectionTitle("Ce que l'appli a repéré")
                rules.forEach { c -> ConcernCard(c, onCall, onBook, onPrepare, onDismiss) }
            }
            SectionTitle("🤖  Ce que dit l'IA")
            session.exchanges.forEach { ex ->
                ex.question?.let { q ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                    }
                }
                AnswerCards(ex.answer, onCall, onBook, onPrepare)
            }
            if (session.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("L'IA lit tes notes… quelques secondes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            session.failure?.let { kind ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(kind.message, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (kind != AiFailureKind.NO_KEY) FilledTonalButton(onClick = onRetry) { Text("Réessayer") }
                            if (kind in setOf(AiFailureKind.NO_KEY, AiFailureKind.AUTH, AiFailureKind.BILLING)) OutlinedButton(onClick = onOpenSettings) { Text("Réglages de l'IA") }
                        }
                    }
                }
            }
            if (session.refused) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("L'IA n'a pas voulu répondre à cette demande. Ce que l'appli a repéré reste valable ; en cas de doute, parles-en à un professionnel.", style = MaterialTheme.typography.bodyLarge)
                        FilledTonalButton(onClick = onRetry) { Text("Réessayer") }
                    }
                }
            }

            val canAsk = session.exchanges.isNotEmpty() && !session.busy && session.exchanges.size <= com.medipote.app.ai.AiAssistant.MAX_FOLLOW_UPS
            if (canAsk) {
                session.exchanges.last().answer.questions.takeIf { it.isNotEmpty() }?.let { suggestions ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { q -> AssistChip(onClick = { onAsk(q) }, label = { Text(q) }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = question, onValueChange = { question = it.take(500) }, label = { Text("Poser une question") },
                        modifier = Modifier.weight(1f), maxLines = 3,
                    )
                    IconButton(onClick = { onAsk(question); question = "" }, enabled = question.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer ma question") }
                }
            }
            if (session.sent.isNotBlank()) {
                TextButton(onClick = { showSent = !showSent }) { Text(if (showSent) "Masquer ce qui a été envoyé" else "👁️  Voir ce qui a été envoyé") }
                if (showSent) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                        Text(session.sent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            Disclaimer(
                "Réponse écrite par une IA (Claude, d'Anthropic) à partir de ce que tu partages. Elle peut se tromper : ce n'est ni un diagnostic ni un avis médical, et rien ici ne touche à ton traitement. En cas de malaise ou de doute : le 15. Si ça ne va pas moralement : le 3114.",
                important = true,
            )
        }
    }
}

@Composable
private fun AnswerCards(answer: AiAnswer, onCall: (String) -> Unit, onBook: () -> Unit, onPrepare: (() -> Unit)?) {
    if (answer.summary.isNotBlank()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Text(answer.summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
        }
    }
    if (answer.explanations.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Ce qui peut l'expliquer chez toi", style = MaterialTheme.typography.titleMedium)
                answer.explanations.forEach { p ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (p.title.isNotBlank()) Text(p.title, style = MaterialTheme.typography.titleSmall)
                        if (p.detail.isNotBlank()) Text(p.detail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (answer.relief.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ce qui peut aider", style = MaterialTheme.typography.titleMedium)
                answer.relief.forEach { Row { Text("•  ", style = MaterialTheme.typography.bodyMedium); Text(it, style = MaterialTheme.typography.bodyMedium) } }
            }
        }
    }
    val level = answer.level
    if (level != null) {
        ConcernCard(Concern("ai", level, "Avis de l'IA", listOf(answer.reason.ifBlank { level.label })), onCall, onBook, onPrepare, onDismiss = {}, showDismiss = false)
    } else if (answer.summary.isNotBlank() || answer.explanations.isNotEmpty()) {
        Text("Selon l'IA, rien d'inquiétant dans ces notes. Si tu t'inquiètes quand même, parles-en à ton médecin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (answer.filtered) {
        Text("Quelques lignes de la réponse ont été retenues par l'appli (médicament, diagnostic).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
