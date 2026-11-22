package com.medipote.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.medipote.app.data.TravelPlan
import com.medipote.app.domain.Travel
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.NumberStepper
import com.medipote.app.ui.components.SectionTitle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)

/**
 * « Voyage » (Paramètres › Rappels): the doses follow the body, not the clock, when the time zone changes: they stay at home
 * time on arrival and come back to the local clock a little each day. The same way back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TravelScreen(
    plan: TravelPlan?,
    today: LocalDate,
    sampleMinute: Int,
    onSave: (TravelPlan) -> Unit,
    onCancelTrip: () -> Unit,
    onBack: () -> Unit,
) {
    var arrival by remember { mutableStateOf(plan?.arrival ?: today.plusDays(1)) }
    var offset by rememberSaveable { mutableStateOf(plan?.offsetHours ?: 6) }
    var step by rememberSaveable { mutableStateOf(plan?.stepHours ?: 1) }
    var hasReturn by rememberSaveable { mutableStateOf(plan?.returnDate != null) }
    var back by remember { mutableStateOf(plan?.returnDate ?: arrival.plusDays(10)) }
    val draft = TravelPlan(arrival, offset, step, back.takeIf { hasReturn && it.isAfter(arrival) })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voyage") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "Quand tu changes de fuseau, ton téléphone change d'heure mais pas ton corps. Les prises restent d'abord à ton heure de chez toi, " +
                    "puis se rapprochent de l'heure locale un peu chaque jour. Au retour, c'est la même chose en sens inverse.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SectionTitle("Mon voyage")
            DateField("Arrivée", arrival, { arrival = it; if (!back.isAfter(it)) back = it.plusDays(7) }, minDate = today)
            NumberStepper("Décalage avec chez moi", offset, { offset = it }, -Travel.MAX_OFFSET..Travel.MAX_OFFSET, display = { if (it > 0) "+$it h" else if (it < 0) "−${-it} h" else "aucun" })
            Text("Par rapport à chez toi : +6 h si là-bas il est 14 h quand il est 8 h chez toi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Se rapprocher de l'heure locale de…", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { h -> FilterChip(selected = step == h, onClick = { step = h }, label = { Text("$h h par jour") }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Je reviens à une date précise", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = hasReturn, onCheckedChange = { hasReturn = it })
            }
            if (hasReturn) DateField("Retour", back, { back = it }, minDate = arrival.plusDays(1))

            if (offset != 0) {
                SectionTitle("Pour une prise de ${"%d h %02d".format(sampleMinute / 60, sampleMinute % 60)}")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Travel.preview(draft, sampleMinute, 8).forEach { (d, t) ->
                            Row { Text(d.format(DAY).replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); Text(t, style = MaterialTheme.typography.titleSmall) }
                        }
                    }
                }
            }
            Text(
                "Pour la pilule, le Lévothyrox, un anticoagulant ou tout traitement sensible à l'horaire, demande conseil à ton médecin ou à ton pharmacien avant de décaler : ce plan est une proposition, pas un avis médical.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onSave(draft) }, enabled = offset != 0, modifier = Modifier.fillMaxWidth()) { Text(if (plan == null) "Enregistrer mon voyage" else "Mettre à jour") }
            if (plan != null) OutlinedButton(onClick = onCancelTrip, modifier = Modifier.fillMaxWidth()) { Text("Annuler le voyage : tout reprend ses heures") }
        }
    }
}
