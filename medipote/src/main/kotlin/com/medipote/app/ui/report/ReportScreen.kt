package com.medipote.app.ui.report

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.medipote.app.data.AppState
import com.medipote.app.domain.DoctorReports
import com.medipote.app.domain.ReportPart
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.stats.PeriodSelector
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(state: AppState, now: LocalDateTime, onMessage: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember(state) { DoctorReports.availableParts(state) }
    var days by rememberSaveable { mutableStateOf(90) }
    var parts by remember { mutableStateOf(available.toSet()) }
    var busy by remember { mutableStateOf(false) }
    var pdf by remember { mutableStateOf<File?>(null) }

    fun uri(f: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        val f = pdf ?: return@rememberLauncherForActivityResult
        if (target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(target, "wt")!!.use { out -> f.inputStream().use { it.copyTo(out) } } }.isSuccess
            }
            onMessage(if (ok) "Rapport enregistré" else "Impossible d'enregistrer le rapport")
        }
    }

    fun generate(then: (File) -> Unit) {
        busy = true
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val report = DoctorReports.build(state, now, days, parts)
                    File(File(context.cacheDir, "reports").apply { mkdirs() }, "MediPote-rapport-${now.toLocalDate()}.pdf").also {
                        ReportPdf.render(report, it)
                    }
                }.getOrNull()
            }
            busy = false
            if (file == null) onMessage("Impossible de créer le rapport") else {
                pdf = file
                then(file)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rapport pour le médecin") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            generate { f ->
                                val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                                    .putExtra(Intent.EXTRA_STREAM, uri(f))
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, "Partager le rapport"))
                            }
                        },
                        enabled = parts.isNotEmpty() && !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Créer et partager le PDF")
                    }
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                generate { f ->
                                    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), "application/pdf")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    runCatching { context.startActivity(view) }.onFailure { onMessage("Aucune appli pour ouvrir un PDF") }
                                }
                            },
                            enabled = parts.isNotEmpty() && !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Aperçu")
                        }
                        OutlinedButton(
                            onClick = { generate { f -> saveAs.launch(f.name) } },
                            enabled = parts.isNotEmpty() && !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enregistrer")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Un résumé clair de ton suivi, à montrer ou à envoyer à ton médecin : traitements, prises, médicaments « si besoin », mesures, sommeil, symptômes…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionTitle("Période")
            PeriodSelector(listOf(30 to "1 mois", 90 to "3 mois", 180 to "6 mois", 365 to "1 an"), days, { days = it })
            SectionTitle("Contenu")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column {
                    available.forEach { part ->
                        ListItem(
                            headlineContent = { Text(part.label) },
                            leadingContent = {
                                Checkbox(checked = part in parts, onCheckedChange = { on -> parts = if (on) parts + part else parts - part })
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            Disclaimer("Le rapport reste sur ton téléphone tant que tu ne le partages pas. Il contient des données de santé : envoie-le seulement à des personnes de confiance.")
        }
    }
}
