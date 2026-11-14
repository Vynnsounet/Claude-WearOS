package com.radaroccaz.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.radaroccaz.app.analysis.HistoryText
import com.radaroccaz.app.analysis.VehicleAnalysis
import com.radaroccaz.app.data.AccidentStatus
import com.radaroccaz.app.data.CtStatus
import com.radaroccaz.app.data.Fact
import com.radaroccaz.app.data.FactSource
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.MileageReading
import com.radaroccaz.app.data.VehicleHistory
import com.radaroccaz.app.ui.components.SectionCard

const val HISTOVEC_URL = "https://histovec.interieur.gouv.fr"

/**
 * Lets the user record what the official HistoVec report (or the seller) says. These
 * entries take precedence over what was read in the listing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryEditorScreen(
    listing: Listing,
    initial: VehicleHistory,
    onSave: (VehicleHistory) -> Unit,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val src = FactSource.VOUS
    var owners by remember { mutableStateOf(initial.owners?.value) }
    var accident by remember { mutableStateOf(initial.accident?.value) }
    var service by remember { mutableStateOf(initial.serviceRecords?.value) }
    var ct by remember { mutableStateOf(initial.technicalInspection?.value) }
    var ctDate by remember { mutableStateOf(initial.lastInspection?.value?.let(::toInput).orEmpty()) }
    var guaranteed by remember { mutableStateOf(initial.mileageGuaranteed?.value) }
    var readings by remember { mutableStateOf(initial.readings) }
    var newDate by remember { mutableStateOf("") }
    var newKm by remember { mutableStateOf("") }
    val newDateParsed = HistoryText.parseDate(newDate)
    val newKmValue = newKm.filter(Char::isDigit).toIntOrNull()

    fun build() = VehicleHistory(
        owners = owners?.let { Fact(it, src) },
        accident = accident?.let { Fact(it, src) },
        serviceRecords = service?.let { Fact(it, src) },
        technicalInspection = ct?.let { Fact(it, src) },
        lastInspection = HistoryText.parseDate(ctDate)?.let { Fact(it, src) },
        mileageGuaranteed = guaranteed?.let { Fact(it, src) },
        readings = readings,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Historique du véhicule") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text(listing.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Le rapport officiel HistoVec est gratuit : le vendeur le génère et vous partage un lien. " +
                                "Il indique le nombre de titulaires, les sinistres (procédures VE / VGA), les contrôles techniques " +
                                "avec leur kilométrage, les vols et les gages. Reportez ici ce qu'il contient : l'analyse en tiendra compte en priorité.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { onOpenUrl(listing.history.reportUrl ?: HISTOVEC_URL) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (listing.history.reportUrl != null) "Ouvrir le rapport de l'annonce" else "Ouvrir HistoVec")
                        }
                    }
                }

                SectionCard("Ce que dit le rapport", icon = Icons.Rounded.History) {
                    Choice("Nombre de propriétaires (titulaires)", listOf(null to "Inconnu", 1 to "1", 2 to "2", 3 to "3", 4 to "4 ou +"), owners) { owners = it }
                    Choice(
                        "Accidents / sinistres",
                        listOf(null to "Inconnu", AccidentStatus.NONE to "Aucun", AccidentStatus.MINOR to "Dégâts légers",
                            AccidentStatus.DAMAGED to "Accidenté", AccidentStatus.SEVERE to "VE / VGA"),
                        accident,
                    ) { accident = it }
                    Choice("Entretien", listOf(null to "Inconnu", true to "Suivi (carnet, factures)", false to "Non suivi"), service) { service = it }
                    Choice(
                        "Dernier contrôle technique",
                        listOf(null to "Inconnu", CtStatus.OK to "Favorable", CtStatus.MINOR_DEFECTS to "Défauts mineurs",
                            CtStatus.COUNTER_VISIT to "Contre-visite", CtStatus.TO_DO to "À faire"),
                        ct,
                    ) { ct = it }
                    OutlinedTextField(
                        value = ctDate,
                        onValueChange = { ctDate = it.take(10) },
                        label = { Text("Date du dernier CT (MM/AAAA)") },
                        singleLine = true,
                        isError = ctDate.isNotBlank() && HistoryText.parseDate(ctDate) == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Choice("Kilométrage garanti", listOf(null to "Inconnu", true to "Oui", false to "Non"), guaranteed) { guaranteed = it }
                }

                SectionCard("Relevés kilométriques", icon = Icons.Rounded.Speed) {
                    Text(
                        "Chaque contrôle technique enregistre le kilométrage. Ajoutez ceux du rapport : un relevé plus ancien mais plus élevé trahit un compteur trafiqué.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    readings.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(VehicleAnalysis.monthLabel(r.date), modifier = Modifier.width(96.dp))
                            Text(Format.km(r.km), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { readings = readings - r }) { Icon(Icons.Rounded.Delete, contentDescription = "Supprimer le relevé") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newDate, onValueChange = { newDate = it.take(10) },
                            label = { Text("MM/AAAA") }, singleLine = true,
                            isError = newDate.isNotBlank() && newDateParsed == null,
                            modifier = Modifier.weight(1f).testTag("reading_date"),
                        )
                        OutlinedTextField(
                            value = newKm, onValueChange = { newKm = it.filter(Char::isDigit).take(7) },
                            label = { Text("km") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("reading_km"),
                        )
                        IconButton(
                            onClick = {
                                readings = (readings + MileageReading(newDateParsed!!, newKmValue!!, src, "Relevé saisi")).sortedBy { it.date }
                                newDate = ""; newKm = ""
                            },
                            enabled = newDateParsed != null && newKmValue != null,
                            modifier = Modifier.testTag("reading_add"),
                        ) { Icon(Icons.Rounded.Add, contentDescription = "Ajouter le relevé") }
                    }
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = { onSave(build()) },
                    contentPadding = PaddingValues(vertical = 14.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).testTag("history_save"),
                ) { Text("Enregistrer et recalculer", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(label: String, options: List<Pair<T?, String>>, selected: T?, onSelect: (T?) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, text) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(text) })
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** "2024-03" → "03/2024". */
private fun toInput(date: String): String = date.drop(5).take(2) + "/" + date.take(4)
