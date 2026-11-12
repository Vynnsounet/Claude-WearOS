package com.medipote.app.ui.treatments

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Drug
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.Treatment
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.Pill
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.shortDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

fun daysSummary(days: Set<Int>): String = when {
    days.size == 7 -> "Tous les jours"
    days.isEmpty() -> "Aucun jour"
    days == setOf(1, 2, 3, 4, 5) -> "En semaine"
    else -> days.sorted().joinToString(", ") {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, java.util.Locale.FRANCE).trimEnd('.')
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentsScreen(
    treatments: List<Treatment>,
    today: LocalDate,
    onAdd: () -> Unit,
    onEdit: (Treatment) -> Unit,
    onboarding: Boolean = false,
    onFinishOnboarding: () -> Unit = {},
    embedded: Boolean = false,
    onRefill: (Treatment) -> Unit = {},
) {
    val (current, finished) = treatments.partition { it.endDate == null || !it.endDate.isBefore(today) }
    Scaffold(
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!embedded) TopAppBar(
                title = { Text(if (onboarding) "Tes traitements en cours" else "Mes traitements") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Ajouter") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onboarding) {
                item {
                    Text(
                        "Recherche chacun de tes médicaments dans le guide intégré et indique sa posologie. " +
                            "Tu pourras en ajouter d'autres plus tard.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (treatments.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd),
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Medication, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("Aucun traitement", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Touche « Ajouter » pour chercher un médicament.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(current, key = { it.id }) { TreatmentCard(it, onClick = { onEdit(it) }, onRefill = { onRefill(it) }) }
            if (finished.isNotEmpty()) {
                item { SectionTitle("Terminés") }
                items(finished, key = { it.id }) { TreatmentCard(it, onClick = { onEdit(it) }, onRefill = { onRefill(it) }, finished = true) }
            }
            if (onboarding) {
                item {
                    androidx.compose.material3.Button(
                        onClick = onFinishOnboarding,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (treatments.isEmpty()) "Passer cette étape" else "C'est parti !") }
                }
            }
            item { Disclaimer("MédiPote est un aide-mémoire : il ne remplace pas l'avis de ton médecin ou de ton pharmacien.") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreatmentCard(t: Treatment, onClick: () -> Unit, onRefill: () -> Unit, finished: Boolean = false) {
    val drug = t.drugId?.let(DrugGuide::byId)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (finished) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Medication, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        drug?.let { "${it.dci} · ${it.drugClass}" } ?: "Saisie manuelle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.Edit, "Modifier", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                t.doses.sortedBy { it.minuteOfDay }.forEach {
                    Pill(
                        "${it.timeLabel} · ${t.unit.format(it.quantity)}",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            val period = buildString {
                append(t.pill?.let { com.medipote.app.data.PillRegimen.of(it).label } ?: daysSummary(t.daysOfWeek))
                append(" · ")
                append(t.endDate?.let { "jusqu'au ${it.format(shortDate)}" } ?: "au long cours")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (t.withFood) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Restaurant, "Pendant le repas", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            t.stock?.let { stock -> StockLine(t, stock, onRefill) }
        }
    }
}

@Composable
private fun StockLine(t: Treatment, stock: Double, onRefill: () -> Unit) {
    val low = t.isLowStock
    val color = if (low) com.medipote.app.ui.theme.AppTheme.status.critical else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Inventory2, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(
            buildString {
                append("Stock : ${t.unit.format(stock)}")
                t.daysLeft?.let { append(" · environ $it jour${if (it > 1) "s" else ""}") }
                if (low) append(" · à racheter")
            },
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (t.packSize != null) {
            androidx.compose.material3.TextButton(onClick = onRefill) { Text("+ ${t.packSize}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugSearchScreen(onBack: () -> Unit, onPick: (Drug) -> Unit, onCustom: (String) -> Unit, autoFocus: Boolean = true) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { DrugGuide.search(query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guide des médicaments") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Nom, molécule, classe…", maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Effacer") }
                },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            )
            Text(
                "${results.size} médicament${if (results.size > 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Médicament introuvable ?", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(if (query.isBlank()) "Le saisir manuellement" else "Ajouter « ${query.trim()} » manuellement")
                        },
                        leadingContent = { Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable { onCustom(query.trim()) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                items(results, key = { it.id }) { drug ->
                    ListItem(
                        overlineContent = { Text(drug.drugClass, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        headlineContent = { Text(drug.dci, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text("${drug.name} · ${drug.strengths.joinToString(", ")}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!drug.prescriptionOnly) {
                                    Pill("Sans ordo.", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable { onPick(drug) },
                    )
                }
                item {
                    Disclaimer(DrugGuide.DISCLAIMER, Modifier.padding(16.dp))
                }
            }
        }
    }
}
