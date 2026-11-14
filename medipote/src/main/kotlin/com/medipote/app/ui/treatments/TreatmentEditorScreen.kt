package com.medipote.app.ui.treatments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medipote.app.data.DoseTime
import com.medipote.app.data.DoseUnit
import com.medipote.app.data.Drug
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.Treatment
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.TimePickerModal
import com.medipote.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val PRESETS = listOf(
    "1×/jour" to listOf(8 * 60),
    "2×/jour" to listOf(8 * 60, 20 * 60),
    "3×/jour" to listOf(8 * 60, 13 * 60, 20 * 60),
    "4×/jour" to listOf(8 * 60, 12 * 60, 16 * 60, 20 * 60),
)

private fun nextQuantity(q: Double, up: Boolean): Double {
    val steps = listOf(0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0)
    val i = steps.indexOfFirst { it >= q }.let { if (it < 0) steps.lastIndex else it }
    return if (up) steps[(i + 1).coerceAtMost(steps.lastIndex)] else steps[(i - 1).coerceAtLeast(0)]
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TreatmentEditorScreen(
    existing: Treatment?,
    drug: Drug?,
    customName: String,
    asNeededDefault: Boolean = false,
    allergies: List<String>,
    today: LocalDate,
    onSave: (Treatment) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: drug?.dci ?: customName) }
    var strength by remember { mutableStateOf(existing?.strength ?: drug?.strengths?.firstOrNull().orEmpty()) }
    var unit by remember { mutableStateOf(existing?.unit ?: drug?.unit ?: DoseUnit.TABLET) }
    val doses = remember { mutableStateListOf<DoseTime>().apply { addAll(existing?.doses ?: listOf(DoseTime(8 * 60))) } }
    var days by remember { mutableStateOf(existing?.daysOfWeek ?: Treatment.ALL_DAYS) }
    var startDate by remember { mutableStateOf(existing?.startDate ?: today) }
    var endDate by remember { mutableStateOf(existing?.endDate) }
    var withFood by remember { mutableStateOf(existing?.withFood ?: false) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var editingTime by remember { mutableStateOf<Int?>(null) }
    var showInfo by remember { mutableStateOf(existing == null && drug != null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var trackStock by remember { mutableStateOf(existing?.stock != null) }
    var stockText by remember { mutableStateOf(existing?.stock?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',') }.orEmpty()) }
    var packText by remember { mutableStateOf(existing?.packSize?.toString().orEmpty()) }
    var lowDays by remember { mutableIntStateOf(existing?.lowStockDays ?: 7) }
    var asNeeded by remember { mutableStateOf(existing?.asNeeded ?: asNeededDefault) }
    var prnQuantity by remember { mutableStateOf(existing?.prnQuantity ?: 1.0) }
    var hasMax by remember { mutableStateOf(existing?.maxPerDay != null) }
    var maxPerDay by remember { mutableIntStateOf(existing?.maxPerDay ?: 3) }
    var hasInterval by remember { mutableStateOf(existing?.minIntervalHours != null) }
    var minInterval by remember { mutableIntStateOf(existing?.minIntervalHours ?: 6) }
    var lowUnits by remember { mutableIntStateOf(existing?.lowStockUnits ?: 4) }
    // 0 = every day, 1 = some days of the week, 2 = every N days
    var rhythm by remember {
        mutableIntStateOf(
            when {
                (existing?.everyDays ?: 1) > 1 -> 2
                (existing?.daysOfWeek?.size ?: 7) < 7 -> 1
                else -> 0
            },
        )
    }
    var everyDays by remember { mutableIntStateOf((existing?.everyDays ?: 2).coerceAtLeast(2)) }
    // 0 = long term, 1 = until a date, 2 = for N days
    var durationMode by remember { mutableIntStateOf(if (existing?.endDate == null) 0 else 1) }
    var durationDays by remember {
        mutableIntStateOf(existing?.endDate?.let { (java.time.temporal.ChronoUnit.DAYS.between(existing.startDate, it) + 1).toInt().coerceIn(1, 365) } ?: 7)
    }
    var useSteps by remember { mutableStateOf(existing?.steps?.isNotEmpty() == true) }
    val steps = remember { mutableStateListOf<com.medipote.app.data.DoseStep>().apply { addAll(existing?.steps.orEmpty()) } }
    val stockValue = stockText.replace(',', '.').toDoubleOrNull()

    val allergyHits = remember(drug, allergies) { drug?.let { DrugGuide.allergyMatches(it, allergies) }.orEmpty() }
    val effectiveEnd = when (durationMode) {
        0 -> null
        2 -> startDate.plusDays(durationDays - 1L)
        else -> endDate
    }
    val valid = name.isNotBlank() && (!trackStock || stockValue != null) &&
        (asNeeded || (doses.isNotEmpty() && (rhythm != 1 || days.isNotEmpty()) && (durationMode == 0 || (effectiveEnd != null && !effectiveEnd.isBefore(startDate)))))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nouveau traitement" else "Modifier le traitement") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        onSave(
                            Treatment(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                drugId = existing?.drugId ?: drug?.id,
                                name = name.trim(),
                                strength = strength.trim(),
                                unit = unit,
                                doses = if (asNeeded) emptyList() else doses.sortedBy { it.minuteOfDay }.distinctBy { it.minuteOfDay },
                                daysOfWeek = if (rhythm == 1) days else Treatment.ALL_DAYS,
                                startDate = startDate,
                                endDate = if (asNeeded) null else effectiveEnd,
                                everyDays = if (rhythm == 2 && !asNeeded) everyDays else 1,
                                steps = if (useSteps && !asNeeded) steps.sortedBy { it.from }.distinctBy { it.from } else emptyList(),
                                withFood = withFood,
                                notes = notes.trim(),
                                createdAt = existing?.createdAt,
                                stock = if (trackStock) stockValue else null,
                                packSize = if (trackStock) packText.toIntOrNull() else null,
                                lowStockDays = lowDays,
                                pill = if (asNeeded) null else existing?.pill,
                                asNeeded = asNeeded,
                                prnQuantity = prnQuantity,
                                maxPerDay = if (asNeeded && hasMax) maxPerDay else null,
                                minIntervalHours = if (asNeeded && hasInterval) minInterval else null,
                                lowStockUnits = lowUnits,
                            ),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (drug != null) DrugInfoCard(drug, showInfo) { showInfo = !showInfo }

            if (allergyHits.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = AppTheme.status.criticalContainer)) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = AppTheme.status.critical)
                        Text(
                            "Attention : tu as signalé une allergie (${allergyHits.joinToString()}) qui pourrait concerner ce médicament. " +
                                "Vérifie avec ton médecin ou ton pharmacien.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom du médicament") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StrengthField(strength, drug?.strengths.orEmpty(), { strength = it }, Modifier.weight(1f))
                UnitField(unit, { unit = it }, Modifier.weight(1f))
            }

            if (existing?.pill == null) {
                SectionTitle("Type de prise")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !asNeeded,
                        onClick = { asNeeded = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("À heures fixes") }
                    SegmentedButton(
                        selected = asNeeded,
                        onClick = { asNeeded = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Si besoin") }
                }
                Text(
                    if (asNeeded) "Pas de rappel ni d'horaire : tu notes chaque prise quand tu en as besoin, avec son motif et son contexte."
                    else "Des rappels aux heures prévues, et ton compagnon est nourri à chaque prise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(asNeeded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Prise habituelle")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Quantité par prise", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { prnQuantity = nextQuantity(prnQuantity, false) }, enabled = prnQuantity > 0.25) {
                                Icon(Icons.Rounded.Remove, "Moins")
                            }
                            Text(unit.format(prnQuantity), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            IconButton(onClick = { prnQuantity = nextQuantity(prnQuantity, true) }) { Icon(Icons.Rounded.Add, "Plus") }
                        }
                    }
                    SectionTitle("Limites")
                    SwitchRow("Nombre maximum de prises", hasMax, supporting = "Sur 24 heures glissantes") { hasMax = it }
                    AnimatedVisibility(hasMax) {
                        com.medipote.app.ui.components.NumberStepper("Maximum par 24 h", maxPerDay, { maxPerDay = it }, 1..12)
                    }
                    SwitchRow("Délai minimum entre deux prises", hasInterval) { hasInterval = it }
                    AnimatedVisibility(hasInterval) {
                        com.medipote.app.ui.components.NumberStepper("Au moins", minInterval, { minInterval = it }, 1..48, suffix = " h")
                    }
                    Text(
                        "Reprends les limites de ton ordonnance ou de la notice. L'appli te prévient si une prise les dépasse, sans jamais t'empêcher de la noter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(!asNeeded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Posologie")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { (label, times) ->
                    FilterChip(
                        selected = doses.map { it.minuteOfDay } == times,
                        onClick = {
                            val q = doses.firstOrNull()?.quantity ?: 1.0
                            doses.clear()
                            doses.addAll(times.map { DoseTime(it, q) })
                        },
                        label = { Text(label) },
                    )
                }
            }
            doses.forEachIndexed { index, dose ->
                DoseRow(
                    dose = dose,
                    unit = unit,
                    onTime = { editingTime = index },
                    onQuantity = { doses[index] = dose.copy(quantity = it) },
                    onRemove = if (doses.size > 1) ({ doses.removeAt(index) }) else null,
                )
            }
            TextButton(onClick = {
                val last = doses.maxOfOrNull { it.minuteOfDay } ?: (7 * 60)
                doses.add(DoseTime(((last + 4 * 60).coerceAtMost(23 * 60)), doses.lastOrNull()?.quantity ?: 1.0))
            }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Ajouter une prise")
            }

            // Dose steps: the quantity changes on given dates (e.g. tapering).
            SwitchRow(
                "La dose change avec le temps",
                useSteps,
                supporting = "Paliers : par exemple 1 comprimé la 1re semaine, puis ½ la 2e…",
            ) {
                useSteps = it
                if (it && steps.isEmpty()) {
                    val q = doses.firstOrNull()?.quantity ?: 1.0
                    steps.add(com.medipote.app.data.DoseStep(startDate.plusDays(7), nextQuantity(q, false)))
                }
            }
            AnimatedVisibility(useSteps) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Jusqu'au premier palier : la quantité indiquée pour chaque prise ci-dessus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    steps.forEachIndexed { i, step ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DateField("À partir du", step.from, { steps[i] = step.copy(from = it) }, Modifier.weight(1f), minDate = startDate.plusDays(1))
                                    IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Rounded.Close, "Retirer ce palier") }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Par prise", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { steps[i] = step.copy(quantity = nextQuantity(step.quantity, false)) }, enabled = step.quantity > 0.25) {
                                        Icon(Icons.Rounded.Remove, "Moins")
                                    }
                                    Text(unit.format(step.quantity), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    IconButton(onClick = { steps[i] = step.copy(quantity = nextQuantity(step.quantity, true)) }) { Icon(Icons.Rounded.Add, "Plus") }
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        val last = steps.maxByOrNull { it.from }
                        steps.add(
                            com.medipote.app.data.DoseStep(
                                (last?.from ?: startDate).plusDays(7),
                                nextQuantity(last?.quantity ?: doses.firstOrNull()?.quantity ?: 1.0, false),
                            ),
                        )
                    }) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ajouter un palier")
                    }
                }
            }

            SectionTitle("Jours de prise")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Tous les jours", "Certains jours", "Tous les X jours").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = rhythm == i,
                        onClick = {
                            rhythm = i
                            if (i == 1 && days.size == 7) days = setOf(1, 2, 3, 4, 5)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        icon = {},
                    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
            AnimatedVisibility(rhythm == 2) {
                Column {
                    com.medipote.app.ui.components.NumberStepper("Tous les", everyDays, { everyDays = it }, 2..60, suffix = " jours")
                    Text(
                        "À partir du début du traitement (${startDate.format(com.medipote.app.ui.components.dayMonth)}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(rhythm == 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = d in days,
                            onClick = { days = if (d in days) days - d else days + d },
                            label = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.FRANCE).trimEnd('.')) },
                        )
                    }
                }
            }

            SectionTitle("Durée")
            DateField("Début du traitement", startDate, { startDate = it })
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Au long cours", "Jusqu'au…", "Pendant…").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = durationMode == i,
                        onClick = { durationMode = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        icon = {},
                    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
            AnimatedVisibility(durationMode == 1) {
                DateField(
                    "Fin du traitement",
                    endDate,
                    { endDate = it },
                    minDate = startDate,
                    supporting = "Dernier jour de prise inclus",
                )
            }
            AnimatedVisibility(durationMode == 2) {
                Column {
                    com.medipote.app.ui.components.NumberStepper("Pendant", durationDays, { durationDays = it }, 1..365, suffix = " jours")
                    Text(
                        "Dernier jour : ${startDate.plusDays(durationDays - 1L).format(com.medipote.app.ui.components.dayMonthYear)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            }

            SectionTitle("Stock")
            SwitchRow("Suivre mon stock", trackStock, supporting = "Le stock diminue à chaque prise et l'appli te prévient avant d'en manquer") { trackStock = it }
            AnimatedVisibility(trackStock) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { v -> stockText = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                            label = { Text("Quantité restante") },
                            suffix = { Text(unit.plural) },
                            isError = stockText.isNotEmpty() && stockValue == null,
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = packText,
                            onValueChange = { v -> packText = v.filter { it.isDigit() }.take(4) },
                            label = { Text("Par boîte") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (asNeeded) {
                        com.medipote.app.ui.components.NumberStepper("Me prévenir sous", lowUnits, { lowUnits = it }, 1..50, display = { unit.format(it.toDouble()) })
                    } else {
                        com.medipote.app.ui.components.NumberStepper("Me prévenir à", lowDays, { lowDays = it }, 1..30, suffix = " j")
                    }
                }
            }

            SectionTitle("Conseils")
            if (!asNeeded) SwitchRow("À prendre pendant le repas", withFood) { withFood = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (facultatif)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Disclaimer("Respecte toujours l'ordonnance de ton médecin. En cas de doute, demande conseil à ton pharmacien.")
        }
    }

    editingTime?.let { index ->
        TimePickerModal(
            minuteOfDay = doses.getOrNull(index)?.minuteOfDay ?: (8 * 60),
            onDismiss = { editingTime = null },
            onConfirm = {
                doses[index] = doses[index].copy(minuteOfDay = it)
                editingTime = null
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce traitement ?") },
            text = { Text("Les prises passées restent dans tes statistiques.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun DrugInfoCard(drug: Drug, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(drug.drugClass.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(drug.dci, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(drug.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = onToggle, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (expanded) "Masquer la fiche" else "Voir la fiche")
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    InfoLine("Indication", drug.indication)
                    InfoLine("Posologie habituelle (adulte)", drug.usualDosage)
                    InfoLine("Précautions", drug.warnings.joinToString("\n") { "• $it" })
                    InfoLine("Délivrance", if (drug.prescriptionOnly) "Sur ordonnance" else "Disponible sans ordonnance")
                    Text(
                        DrugGuide.DISCLAIMER,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun DoseRow(dose: DoseTime, unit: DoseUnit, onTime: () -> Unit, onQuantity: (Double) -> Unit, onRemove: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onTime) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(dose.timeLabel, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onQuantity(nextQuantity(dose.quantity, false)) }, enabled = dose.quantity > 0.25) {
                Icon(Icons.Rounded.Remove, "Moins")
            }
            Text(unit.format(dose.quantity), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            IconButton(onClick = { onQuantity(nextQuantity(dose.quantity, true)) }) { Icon(Icons.Rounded.Add, "Plus") }
            if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Retirer cette prise") }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrengthField(value: String, options: List<String>, onChange: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && options.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("Dosage") },
            singleLine = true,
            trailingIcon = { if (options.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && options.isNotEmpty(), onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = { onChange(it); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitField(value: DoseUnit, onChange: (DoseUnit) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value.singular.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Forme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DoseUnit.entries.forEach {
                DropdownMenuItem(text = { Text(it.singular.replaceFirstChar { c -> c.uppercase() }) }, onClick = { onChange(it); expanded = false })
            }
        }
    }
}
