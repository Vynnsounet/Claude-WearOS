package com.medipote.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Backup
import com.medipote.app.data.BackupManager
import com.medipote.app.data.BackupSettings
import com.medipote.app.data.Settings
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.dayMonthYear
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.theme.AppTheme
import com.medipote.app.ui.treatments.SwitchRow
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun stamp(t: LocalDateTime) = "le ${t.format(dayMonthYear)} à ${t.format(hourMinute)}"

/** What the user is being asked, one dialog at a time. */
private sealed interface Ask {
    data object ExportPassword : Ask
    data class RestorePassword(val bytes: ByteArray, val error: String? = null, val test: Boolean = false) : Ask
    /** A backup opened only to check it is readable: nothing is changed. */
    data class TestResult(val content: Backup.Content) : Ask
    data class ConfirmRestore(val content: Backup.Content) : Ask
    data object AutoPassword : Ask
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    settings: Settings,
    now: LocalDateTime,
    onChange: ((Settings) -> Settings) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = settings.backup
    var ask by remember { mutableStateOf<Ask?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    fun updateBackup(f: (BackupSettings) -> BackupSettings) = onChange { it.copy(backup = f(it.backup)) }

    fun work(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri: Uri? ->
        val password = exportPassword
        exportPassword = null
        if (uri == null) return@rememberLauncherForActivityResult
        work {
            val result = withContext(Dispatchers.IO) { runCatching { BackupManager.export(context, uri, password) } }
            onMessage(if (result.isSuccess) "Sauvegarde enregistrée" else "La sauvegarde a échoué : ${result.exceptionOrNull()?.message}")
        }
    }

    fun decode(bytes: ByteArray, password: CharArray?, test: Boolean = false) = work {
        val result = withContext(Dispatchers.IO) { runCatching { Backup.decode(bytes, password) } }
        result.fold(
            { ask = if (test) Ask.TestResult(it) else Ask.ConfirmRestore(it) },
            { e ->
                ask = when (e) {
                    is Backup.WrongPassword -> Ask.RestorePassword(bytes, "Mot de passe incorrect", test)
                    is Backup.NeedsPassword -> Ask.RestorePassword(bytes, test = test)
                    else -> null.also { onMessage(e.message ?: "Fichier illisible") }
                }
            },
        )
    }

    var testing by remember { mutableStateOf(false) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val test = testing
        testing = false
        if (uri == null) return@rememberLauncherForActivityResult
        work {
            val bytes = withContext(Dispatchers.IO) { runCatching { BackupManager.read(context, uri) } }.getOrNull()
            when {
                bytes == null || !Backup.isBackup(bytes) -> onMessage("Ce fichier n'est pas une sauvegarde MédiPote.")
                Backup.isEncrypted(bytes) -> ask = Ask.RestorePassword(bytes, test = test)
                else -> decode(bytes, null, test)
            }
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree == null) return@rememberLauncherForActivityResult
        val name = runCatching { BackupManager.useFolder(context, tree) }.getOrNull()
        if (name == null) {
            onMessage("Impossible d'utiliser ce dossier")
            return@rememberLauncherForActivityResult
        }
        updateBackup { it.copy(auto = true, folderUri = tree.toString(), folderName = name, lastError = null) }
        BackupManager.schedule(context, true)
        BackupManager.runNow(context)
        onMessage("Sauvegarde automatique activée")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sauvegarde") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Tes données restent sur ton téléphone. Une sauvegarde est un fichier qui contient tout (traitements, historique, " +
                    "journal, sommeil, mesures, documents, compagnon) : garde-le en lieu sûr pour changer de téléphone ou en cas de problème.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PhoneChangeGuide(onTest = { testing = true; openFile.launch(arrayOf("*/*")) }, busy = busy)

            SectionTitle("Sauvegarde manuelle")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { ask = Ask.ExportPassword }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Créer une sauvegarde")
                    }
                    b.lastManual?.let { Text("Dernière sauvegarde manuelle ${stamp(it)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    HorizontalDivider()
                    OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restaurer une sauvegarde")
                    }
                    Text(
                        "Restaurer remplace toutes les données actuelles de l'appli par celles de la sauvegarde.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("Sauvegarde automatique")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SwitchRow(
                        "Chaque semaine",
                        b.auto && b.folderUri != null,
                        supporting = "Dans un dossier de ton choix (téléphone, carte SD, ou dossier synchronisé avec ton cloud). Les 4 dernières sont gardées.",
                    ) { on ->
                        if (on) {
                            if (b.folderUri == null) pickFolder.launch(null) else {
                                updateBackup { it.copy(auto = true) }
                                BackupManager.schedule(context, true)
                            }
                        } else {
                            updateBackup { it.copy(auto = false) }
                            BackupManager.schedule(context, false)
                        }
                    }
                    if (b.folderUri != null) {
                        TextButton(onClick = { pickFolder.launch(null) }) {
                            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Dossier : ${b.folderName.ifBlank { "choisi" }} · changer")
                        }
                        SwitchRow(
                            "Protéger par un mot de passe",
                            b.encrypted,
                            supporting = "Recommandé si le dossier est synchronisé avec un cloud. Sans ce mot de passe, la sauvegarde ne pourra pas être restaurée.",
                        ) { on ->
                            if (on) ask = Ask.AutoPassword else {
                                BackupManager.setPassword(context, null)
                                updateBackup { it.copy(encrypted = false) }
                            }
                        }
                        OutlinedButton(onClick = { BackupManager.runNow(context); onMessage("Sauvegarde en cours…") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.CloudUpload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sauvegarder maintenant")
                        }
                        Text(
                            b.lastAuto?.let { "Dernière sauvegarde automatique ${stamp(it)}." } ?: "Pas encore de sauvegarde automatique.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        b.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.status.critical) }
                    }
                }
            }
            Disclaimer("Avec un mot de passe, la sauvegarde est chiffrée (AES-256). Personne, pas même MédiPote, ne peut la lire ni la restaurer sans lui : note-le bien.", important = true)
        }
    }

    when (val a = ask) {
        Ask.ExportPassword -> PasswordDialog(
            title = "Protéger la sauvegarde ?",
            text = "Choisis un mot de passe pour chiffrer le fichier, ou laisse vide pour une sauvegarde sans mot de passe.",
            allowEmpty = true,
            confirmLabel = "Continuer",
            onDismiss = { ask = null },
        ) { pw ->
            ask = null
            exportPassword = pw.takeIf { it.isNotEmpty() }?.toCharArray()
            createFile.launch(Backup.fileName(now))
        }
        is Ask.RestorePassword -> PasswordDialog(
            title = "Sauvegarde protégée",
            text = "Entre le mot de passe choisi lors de la sauvegarde.",
            allowEmpty = false,
            confirm = false,
            error = a.error,
            confirmLabel = "Ouvrir",
            onDismiss = { ask = null },
        ) { pw ->
            ask = null
            decode(a.bytes, pw.toCharArray(), a.test)
        }
        is Ask.ConfirmRestore -> {
            val s = a.content.state
            AlertDialog(
                onDismissRequest = { ask = null },
                title = { Text("Restaurer cette sauvegarde ?") },
                text = {
                    Text(
                        buildString {
                            a.content.createdAt?.let { append("Sauvegarde du ${it.format(dayMonthYear)} à ${it.format(hourMinute)}.\n\n") }
                            append(Backup.summary(a.content)).append("\n\n")
                            append("Toutes les données actuelles de l'appli seront remplacées.")
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        ask = null
                        work {
                            val r = withContext(Dispatchers.IO) { runCatching { BackupManager.restore(context, a.content) } }
                            onMessage(if (r.isSuccess) "Données restaurées" else "La restauration a échoué : ${r.exceptionOrNull()?.message}")
                        }
                    }) { Text("Restaurer") }
                },
                dismissButton = { TextButton(onClick = { ask = null }) { Text("Annuler") } },
            )
        }
        is Ask.TestResult -> AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text("✅ Cette sauvegarde est bonne") },
            text = {
                Text(
                    buildString {
                        a.content.createdAt?.let { append("Faite le ${it.format(dayMonthYear)} à ${it.format(hourMinute)}.\n\n") }
                        append(Backup.summary(a.content)).append("\n\nRien n'a été changé sur ton téléphone : c'était juste un essai.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { ask = null }) { Text("Super") } },
        )
        Ask.AutoPassword -> PasswordDialog(
            title = "Mot de passe des sauvegardes automatiques",
            text = "Il sera demandé pour restaurer une sauvegarde automatique. Il est gardé sur ce téléphone uniquement.",
            allowEmpty = false,
            confirmLabel = "Enregistrer",
            onDismiss = { ask = null },
        ) { pw ->
            ask = null
            BackupManager.setPassword(context, pw)
            updateBackup { it.copy(encrypted = true) }
        }
        null -> Unit
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    text: String,
    allowEmpty: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    confirm: Boolean = true,
    error: String? = null,
    onConfirm: (String) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val mismatch = confirm && pw.isNotEmpty() && pw != pw2
    val tooShort = pw.isNotEmpty() && pw.length < 6
    val ok = (pw.isEmpty() && allowEmpty) || (pw.isNotEmpty() && !mismatch && (!confirm || !tooShort))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, label = { Text("Mot de passe") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error != null || (confirm && tooShort),
                    supportingText = (error ?: if (confirm && tooShort) "Au moins 6 caractères" else null)?.let { { Text(it) } },
                )
                if (confirm && pw.isNotEmpty()) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it }, label = { Text("Confirmer") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = mismatch && pw2.isNotEmpty(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }, enabled = ok) { Text(if (pw.isEmpty() && allowEmpty) "Sans mot de passe" else confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Changing phone, step by step, and a way to be sure the backup works before the old phone is wiped. */
@Composable
private fun PhoneChangeGuide(onTest: () -> Unit, busy: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text("📱  Je change de téléphone", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (open) "Masquer" else "Voir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (open) {
                listOf(
                    "1️⃣  Sur l'ancien téléphone : « Créer une sauvegarde ». Un mot de passe est conseillé ; note-le bien.",
                    "2️⃣  Envoie le fichier vers le nouveau téléphone (mail à toi-même, clé USB, dossier synchronisé…). MédiPote n'envoie rien tout seul.",
                    "3️⃣  Teste-le ici, sur l'ancien téléphone, avec le bouton ci-dessous : il vérifie que le fichier se lit et ce qu'il contient, sans rien changer.",
                    "4️⃣  Sur le nouveau : installe MédiPote, crée un profil rapide, puis Paramètres › Sauvegarde › « Restaurer une sauvegarde ». Tout est remplacé par ta sauvegarde.",
                    "5️⃣  Vérifie tes traitements et tes rappels, puis seulement après efface l'ancien téléphone.",
                ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            OutlinedButton(onClick = onTest, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("🔍  Tester une sauvegarde (sans rien changer)")
            }
        }
    }
}
