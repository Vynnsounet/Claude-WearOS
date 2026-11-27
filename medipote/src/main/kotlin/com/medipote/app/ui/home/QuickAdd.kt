package com.medipote.app.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.MeasureType
import com.medipote.app.data.PeriodProduct
import com.medipote.app.data.Treatment
import java.time.LocalDateTime

/** What the "+" button can note, wired by the app. */
data class QuickAddActions(
    val onLogPrn: (Treatment) -> Unit = {},
    val onAddPrnTreatment: () -> Unit = {},
    val onMood: (Int?) -> Unit = {},
    val onSleep: () -> Unit = {},
    val onNapStart: () -> Unit = {},
    val onNapWake: () -> Unit = {},
    val onNapLog: () -> Unit = {},
    val onSupport: () -> Unit = {},
    val onWater: () -> Unit = {},
    val onProduct: (PeriodProduct) -> Unit = {},
    val onProductChanged: () -> Unit = {},
    val onMeasure: (MeasureType) -> Unit = {},
    val onJournal: () -> Unit = {},
    val onAppointment: () -> Unit = {},
    val onSymptoms: () -> Unit = {},
    val onSoftDay: () -> Unit = {},
)

enum class QuickPage { MAIN, PRN, PRODUCT, MEASURE, NAP }

/** The "+ Noter" button of the home screen and its panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddButton(state: AppState, now: LocalDateTime, actions: QuickAddActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    ExtendedFloatingActionButton(
        onClick = { open = true },
        icon = { Icon(Icons.Rounded.Add, null) },
        text = { Text("Noter") },
    )
    if (open) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet) {
            QuickAddContent(state, now, actions, onDone = { open = false })
        }
    }
}

/** Everything one can note, two taps away. */
@Composable
fun QuickAddContent(state: AppState, now: LocalDateTime, actions: QuickAddActions, onDone: () -> Unit, initial: QuickPage = QuickPage.MAIN) {
    var page by remember { mutableStateOf(initial) }
    val today = now.toLocalDate()
    val prn = state.treatments.filter { it.asNeeded && (it.endDate == null || !it.endDate.isBefore(today)) }
    val menstruating = state.profile?.menstruating == true
    val measures = MeasureType.entries.filter { it in state.settings.measures }
    fun done(action: () -> Unit) { onDone(); action() }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        when (page) {
            QuickPage.MAIN -> {
                Text("Que veux-tu noter ?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                val entry = state.entryFor(today)
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("😊  Mon humeur", style = MaterialTheme.typography.titleMedium)
                    com.medipote.app.ui.motivation.FacePicker(entry.mental, "Humeur", { v -> done { actions.onMood(v) } })
                }
                QuickRow("💊", "Un médicament « si besoin »", if (prn.isEmpty()) "En ajouter un" else null, more = prn.isNotEmpty()) {
                    if (prn.isEmpty()) done(actions.onAddPrnTreatment) else page = QuickPage.PRN
                }
                QuickRow("🌙", "Ma nuit", if (state.sleep.any { it.date == today }) "Déjà notée : la modifier" else null, onClick = { done(actions.onSleep) })
                val napRunning = com.medipote.app.domain.Naps.running(state, now) != null
                QuickRow(
                    "😴", if (napRunning) "Terminer ma sieste" else "Une sieste",
                    if (napRunning) "Elle est en cours" else null, more = !napRunning,
                ) { if (napRunning) done(actions.onNapWake) else page = QuickPage.NAP }
                QuickRow("💧", "Un verre d'eau", "${com.medipote.app.domain.Water.progress(com.medipote.app.domain.Water.ml(entry, state.settings.water), com.medipote.app.domain.Water.goalMl(state.settings.water))} aujourd'hui", onClick = { done(actions.onWater) })
                if (menstruating) {
                    QuickRow(
                        "🩸", "Une protection",
                        state.productSession?.let { "${it.product.label} en cours" },
                        more = true,
                    ) { page = QuickPage.PRODUCT }
                }
                if (measures.isNotEmpty()) {
                    QuickRow("🩺", "Une mesure", measures.joinToString(", ") { it.label.lowercase() }, more = measures.size > 1) {
                        if (measures.size == 1) done { actions.onMeasure(measures.single()) } else page = QuickPage.MEASURE
                    }
                }
                if (com.medipote.app.domain.Wellbeing.enabled(state)) {
                    QuickRow("🌡️", "Un symptôme", com.medipote.app.ui.HubPreviews.symptoms(state, now), onClick = { done(actions.onSymptoms) })
                    QuickRow("💛", "Journée douce", "Missions et tâches en petites étapes", onClick = { done(actions.onSoftDay) })
                }
                QuickRow("📖", "Mon journal", "Forme, symptômes, notes", onClick = { done(actions.onJournal) })
                QuickRow("📅", "Un rendez-vous", null, onClick = { done(actions.onAppointment) })
            }
            QuickPage.PRN -> {
                BackRow("Un médicament « si besoin »") { page = QuickPage.MAIN }
                prn.forEach { t ->
                    val last = com.medipote.app.domain.Prn.last(state, t.id, now)
                    QuickRow("💊", t.displayName, last?.let { "Dernière prise ${com.medipote.app.ui.treatments.sinceLabel(it.takenAt, now)}" }) {
                        done { actions.onLogPrn(t) }
                    }
                }
                QuickRow("➕", "Un autre médicament", null) { done(actions.onAddPrnTreatment) }
            }
            QuickPage.PRODUCT -> {
                BackRow("Une protection") { page = QuickPage.MAIN }
                state.productSession?.let { s ->
                    QuickRow("🔄", "J'ai changé ${s.product.yours}", "Le minuteur repart de zéro") { done(actions.onProductChanged) }
                    Text(
                        "Ou je viens de mettre :",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                PeriodProduct.entries.forEach { p -> QuickRow(p.emoji, p.label, null) { done { actions.onProduct(p) } } }
            }
            QuickPage.NAP -> {
                BackRow("Une sieste") { page = QuickPage.MAIN }
                QuickRow("▶️", "Je m'endors maintenant", "Un minuteur compte la sieste") { done(actions.onNapStart) }
                QuickRow("✍️", "Noter une sieste déjà faite", null) { done(actions.onNapLog) }
            }
            QuickPage.MEASURE -> {
                BackRow("Une mesure") { page = QuickPage.MAIN }
                measures.forEach { m -> QuickRow(m.emoji, m.label, null) { done { actions.onMeasure(m) } } }
            }
        }
    }
}

@Composable
private fun QuickRow(emoji: String, title: String, subtitle: String?, more: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (more) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Retour")
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
    Spacer(Modifier.height(4.dp))
}
