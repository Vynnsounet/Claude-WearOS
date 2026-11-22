package com.medipote.app.ui.understand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.AdviceItem
import com.medipote.app.domain.Concern
import com.medipote.app.domain.Factor
import com.medipote.app.domain.Understand
import com.medipote.app.domain.Urgency
import com.medipote.app.domain.Wellbeing
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDate

/** Why it may be there, in the person's own context: each line shows the notes it comes from. */
@Composable
fun FactorsCard(factors: List<Factor>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (factors.isEmpty()) {
                Text(
                    "Rien dans tes notes des derniers jours ne l'explique clairement. Plus tu notes (sommeil, eau, humeur, cycle), plus cette page peut t'aider.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            factors.forEach { f ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(f.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(f.title, style = MaterialTheme.typography.titleSmall)
                        Text(f.evidence, style = MaterialTheme.typography.bodyMedium)
                        f.tip?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
fun AdviceCard(title: String, items: List<AdviceItem>, onAskAi: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.forEach { a ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(a.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(a.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            onAskAi?.let { com.medipote.app.ui.ai.AskAiButton(it) }
        }
    }
}

/** Something worth an opinion, with what to do about it: call, book, prepare, or set it aside for a week. */
@Composable
fun ConcernCard(
    concern: Concern,
    onCall: (String) -> Unit,
    onBook: () -> Unit,
    onPrepare: (() -> Unit)?,
    onDismiss: (String) -> Unit,
    onOpenSymptom: ((String) -> Unit)? = null,
    /** « Je l'ai vu » : not for what the AI said, which is not kept. */
    showDismiss: Boolean = true,
) {
    val tone = when (concern.urgency) {
        Urgency.URGENT -> AppTheme.status.critical
        Urgency.SOON -> AppTheme.status.warning
        Urgency.MENTION -> MaterialTheme.colorScheme.primary
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(concern.urgency.label, style = MaterialTheme.typography.labelLarge, color = tone)
            Text(concern.title, style = MaterialTheme.typography.titleMedium)
            concern.reasons.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            when (concern.urgency) {
                Urgency.URGENT -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCall("15") }) { Text("📞  Appeler le 15") }
                    OutlinedButton(onClick = { onCall("112") }) { Text("112") }
                }
                Urgency.SOON -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = onBook) { Text("📅  Prendre rendez-vous") }
                    if (showDismiss) TextButton(onClick = { onDismiss(concern.key) }) { Text("Je l'ai vu") }
                }
                Urgency.MENTION -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onPrepare != null) FilledTonalButton(onClick = onPrepare) { Text("📝  Préparer mon rendez-vous") }
                    if (showDismiss) TextButton(onClick = { onDismiss(concern.key) }) { Text("Je l'ai vu") }
                }
            }
            val symptom = concern.symptom
            if (symptom != null && onOpenSymptom != null) {
                TextButton(onClick = { onOpenSymptom(symptom) }) { Text("Voir les conseils pour ${Wellbeing.name(symptom).lowercase()}") }
            }
        }
    }
}

/**
 * « Comprendre »: what the notes let one see, what deserves a professional's opinion, and advice for the cycle and the
 * sleep. Rules written by hand, read only on this phone: it is not a generative AI and never invents anything.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CheckupScreen(
    state: AppState,
    today: LocalDate,
    onCall: (String) -> Unit,
    onBook: () -> Unit,
    onPrepare: (() -> Unit)?,
    onDismiss: (String) -> Unit,
    onOpenSymptom: (String) -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false,
    onAskAi: (() -> Unit)? = null,
) {
    val concerns = Understand.visible(Understand.scan(state, today), state.settings.dismissedChecks, today)
    val cycle = Understand.cycleAdvice(state, today)
    val sleep = Understand.sleepAdvice(state, today)
    val symptoms = (state.symptomLog.map { it.symptom } + state.settings.wellbeing.symptoms).distinct()
    val body: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "Ce que tes notes laissent voir, et ce qui mérite peut-être l'avis d'un professionnel. " +
                    "Ce n'est pas une IA qui devine : ce sont des règles écrites à la main, qui lisent seulement tes notes, sur ce téléphone.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionTitle("À regarder")
            if (concerns.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "🌿  Rien d'inhabituel dans tes notes pour l'instant. Ça ne remplace pas un rendez-vous : si quelque chose t'inquiète, parles-en à ton médecin.",
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp),
                    )
                }
            }
            concerns.forEach { c -> ConcernCard(c, onCall, onBook, onPrepare, onDismiss, onOpenSymptom) }
            onAskAi?.let { com.medipote.app.ui.ai.AskAiButton(it) }

            if (symptoms.isNotEmpty()) {
                SectionTitle("Comprendre un symptôme")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    symptoms.forEach { s ->
                        AssistChip(onClick = { onOpenSymptom(s) }, label = { Text("${Wellbeing.emoji(s)}  ${Wellbeing.name(s)}") })
                    }
                }
            }
            if (cycle.isNotEmpty()) AdviceCard("🩸  Pour ton cycle, en ce moment", cycle, onAskAi = onAskAi?.let { { onAskAi() } })
            if (sleep.isNotEmpty()) AdviceCard("🌙  Pour ton sommeil", sleep)
            Disclaimer(
                "Ce sont des repères généraux, pas un diagnostic ni un avis médical : ils ne remplacent pas ton médecin et ne touchent jamais à ton traitement. " +
                    "En cas de malaise, de douleur forte ou de doute, appelle le 15 (ou le 112). Si ça ne va pas moralement : le 3114, jour et nuit.",
                important = true,
            )
        }
    }
    if (embedded) body(Modifier.fillMaxSize()) else Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comprendre") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding -> body(Modifier.fillMaxSize().padding(padding)) }
}
