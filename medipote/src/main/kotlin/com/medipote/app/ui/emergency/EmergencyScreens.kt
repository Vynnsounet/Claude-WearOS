package com.medipote.app.ui.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.medipote.app.domain.EmergencyCard

/** The card itself: big, calm, readable by someone who does not know the app. Used in the app and on its lock screen. */
@Composable
fun EmergencyCardContent(card: EmergencyCard, onCall: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🚑  Fiche d'urgence", style = MaterialTheme.typography.titleLarge)
                if (card.name.isNotBlank()) Text(card.name, style = MaterialTheme.typography.headlineSmall)
                val line = listOf(card.ageSex, card.bloodGroup.takeIf { it.isNotBlank() }?.let { "groupe $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (card.isEmpty) {
            Text("Rien n'est encore renseigné. Ajoute tes allergies, tes antécédents et les personnes à prévenir dans ton profil médical.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Block("⚠️  Allergies", card.allergies, strong = true)
        Block("📋  Antécédents et conditions", card.conditions)
        Block("💊  Traitements", card.treatments)
        if (card.contacts.isNotEmpty() || card.doctor != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📞  À prévenir", style = MaterialTheme.typography.titleMedium)
                    card.contacts.forEach { c -> Person(c.name, c.phone, onCall) }
                    card.doctor?.let { Person("${it.name} (médecin)", it.phone, onCall) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onCall("15") }) { Text("📞  15 (SAMU)") }
            OutlinedButton(onClick = { onCall("112") }) { Text("112") }
        }
    }
}

@Composable
private fun Block(title: String, lines: List<String>, strong: Boolean = false) {
    if (lines.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { Text("•  $it", style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun Person(name: String, phone: String, onCall: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (phone.isNotBlank()) Text(phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (phone.isNotBlank()) TextButton(onClick = { onCall(phone) }) { Text("Appeler") }
    }
}

/** « Fiche d'urgence » from the medical profile: the same card, with a way to share it and to complete the profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(card: EmergencyCard, onCall: (String) -> Unit, onShare: () -> Unit, onEditProfile: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fiche d'urgence") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, "Partager la fiche") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EmergencyCardContent(card, onCall)
            OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) { Text("✏️  Compléter mon profil médical") }
            Text(
                "Pour que les secours la voient sans ton code, active-la dans Paramètres › Confidentialité (écran de verrouillage de l'appli). Android a aussi ses propres « infos d'urgence » dans les réglages du téléphone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
