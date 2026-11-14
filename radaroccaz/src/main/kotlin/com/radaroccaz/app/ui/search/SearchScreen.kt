package com.radaroccaz.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EuroSymbol
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.radaroccaz.app.data.CarCatalog
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Priorities
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerFilter
import com.radaroccaz.app.data.Text as TextUtil
import com.radaroccaz.app.data.VehicleOptions
import com.radaroccaz.app.ui.components.Pill
import com.radaroccaz.app.ui.components.SectionCard
import com.radaroccaz.app.ui.theme.LocalExtraColors
import java.time.LocalDate
import kotlin.math.roundToInt

private val MILEAGE_STEPS = listOf(20_000, 40_000, 60_000, 80_000, 100_000, 120_000, 150_000, 200_000, null)
private val BUDGET_PRESETS = listOf(5_000, 8_000, 10_000, 15_000, 20_000, 30_000)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    criteria: SearchCriteria,
    recent: List<SearchCriteria>,
    demoMode: Boolean,
    onChange: ((SearchCriteria) -> SearchCriteria) -> Unit,
    onSearch: () -> Unit,
    onPickRecent: (SearchCriteria) -> Unit,
    onClearRecent: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    currentYear: Int = LocalDate.now().year,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp),
        ) {
            Header(demoMode, onOpenSettings)
            Column(
                Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (recent.isNotEmpty()) RecentSearches(recent, onPickRecent, onClearRecent)

                SectionCard("Véhicule", icon = Icons.Rounded.DirectionsCar, trailing = {
                    if (criteria != SearchCriteria(priorities = criteria.priorities)) {
                        TextButton(onClick = onReset) { Text("Effacer") }
                    }
                }) {
                    AutocompleteField(
                        label = "Marque",
                        value = criteria.make,
                        placeholder = "ex. Peugeot",
                        suggestions = { CarCatalog.suggestMakes(it) },
                        onValueChange = { v -> onChange { it.copy(make = v) } },
                        tag = "field_make",
                    )
                    Spacer(Modifier.height(10.dp))
                    AutocompleteField(
                        label = "Modèle",
                        value = criteria.model,
                        placeholder = "ex. 308",
                        suggestions = { CarCatalog.suggestModels(criteria.make, it) },
                        onValueChange = { v -> onChange { it.copy(model = v) } },
                        tag = "field_model",
                    )
                }

                SectionCard("Budget", icon = Icons.Rounded.EuroSymbol) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Minimum", criteria.budgetMin, "€", Modifier.weight(1f)) { v -> onChange { it.copy(budgetMin = v) } }
                        NumberField("Maximum", criteria.budgetMax, "€", Modifier.weight(1f).testTag("field_budget_max")) { v -> onChange { it.copy(budgetMax = v) } }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BUDGET_PRESETS.forEach { amount ->
                            FilterChip(
                                selected = criteria.budgetMax == amount,
                                onClick = { onChange { it.copy(budgetMax = if (it.budgetMax == amount) null else amount) } },
                                label = { Text("≤ ${Format.price(amount)}") },
                            )
                        }
                    }
                }

                SectionCard("Critères", icon = Icons.Rounded.Tune) {
                    // Mileage
                    val kmIndex = MILEAGE_STEPS.indexOf(criteria.mileageMax).takeIf { it >= 0 } ?: MILEAGE_STEPS.lastIndex
                    LabeledValue("Kilométrage max", criteria.mileageMax?.let { Format.km(it) } ?: "Indifférent")
                    Slider(
                        value = kmIndex.toFloat(),
                        onValueChange = { f -> onChange { it.copy(mileageMax = MILEAGE_STEPS[f.roundToInt()]) } },
                        valueRange = 0f..MILEAGE_STEPS.lastIndex.toFloat(),
                        steps = MILEAGE_STEPS.size - 2,
                    )
                    // Year
                    val years = listOf<Int?>(null) + (currentYear - 18..currentYear).toList()
                    val yearIndex = years.indexOf(criteria.yearMin).takeIf { it >= 0 } ?: 0
                    LabeledValue("Année minimum", criteria.yearMin?.let { "$it et après" } ?: "Indifférent")
                    Slider(
                        value = yearIndex.toFloat(),
                        onValueChange = { f -> onChange { it.copy(yearMin = years[f.roundToInt()]) } },
                        valueRange = 0f..years.lastIndex.toFloat(),
                        steps = years.size - 2,
                    )
                    Spacer(Modifier.height(6.dp))
                    SubLabel("Carburant")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Fuel.entries.forEach { fuel ->
                            FilterChip(
                                selected = fuel in criteria.fuels,
                                onClick = { onChange { it.copy(fuels = if (fuel in it.fuels) it.fuels - fuel else it.fuels + fuel) } },
                                label = { Text(fuel.label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SubLabel("Boîte de vitesses")
                    val gearOptions = listOf<Gearbox?>(null, Gearbox.MANUELLE, Gearbox.AUTOMATIQUE)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        gearOptions.forEachIndexed { i, g ->
                            SegmentedButton(
                                selected = criteria.gearbox == g,
                                onClick = { onChange { it.copy(gearbox = g) } },
                                shape = SegmentedButtonDefaults.itemShape(i, gearOptions.size),
                                label = { Text(g?.label ?: "Toutes", maxLines = 1) },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SubLabel("Vendeur")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SellerFilter.entries.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = criteria.seller == s,
                                onClick = { onChange { it.copy(seller = s) } },
                                shape = SegmentedButtonDefaults.itemShape(i, SellerFilter.entries.size),
                                label = { Text(s.label, maxLines = 1) },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = criteria.department,
                        onValueChange = { v -> onChange { it.copy(department = v.filter { c -> c.isLetterOrDigit() }.take(5).uppercase()) } },
                        label = { Text("Département (facultatif)") },
                        placeholder = { Text("ex. 33 ou 75015") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard("Options souhaitées", icon = Icons.Rounded.Build, trailing = {
                    if (criteria.wantedOptions.isNotEmpty()) {
                        Text("${criteria.wantedOptions.size} choisie${if (criteria.wantedOptions.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }) {
                    Text(
                        "Les annonces qui les mentionnent remontent dans le classement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VehicleOptions.all.forEach { option ->
                            val on = option.id in criteria.wantedOptions
                            FilterChip(
                                selected = on,
                                onClick = { onChange { it.copy(wantedOptions = if (on) it.wantedOptions - option.id else it.wantedOptions + option.id) } },
                                label = { Text(option.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }

                SectionCard("Ce qui compte pour vous", icon = Icons.Rounded.Star) {
                    Text(
                        "Règle le poids de chaque critère dans la note sur 100.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    val p = criteria.priorities
                    PriorityRow("Prix et bonne affaire", p.price) { v -> onChange { it.copy(priorities = it.priorities.copy(price = v)) } }
                    PriorityRow("Faible kilométrage", p.mileage) { v -> onChange { it.copy(priorities = it.priorities.copy(mileage = v)) } }
                    PriorityRow("Véhicule récent", p.age) { v -> onChange { it.copy(priorities = it.priorities.copy(age = v)) } }
                    PriorityRow("Options souhaitées", p.options, enabled = criteria.wantedOptions.isNotEmpty()) { v ->
                        onChange { it.copy(priorities = it.priorities.copy(options = v)) }
                    }
                }
            }
        }

        // Sticky search button
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 8.dp,
        ) {
            Button(
                onClick = onSearch,
                enabled = criteria.isValid,
                contentPadding = PaddingValues(vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("search_button"),
            ) {
                Icon(Icons.Rounded.Search, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (criteria.isValid) "Rechercher · ${criteria.title}" else "Indiquez une marque ou un modèle",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Header(demoMode: Boolean, onOpenSettings: () -> Unit) {
    val x = LocalExtraColors.current
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(x.heroStart, x.heroEnd)))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 22.dp),
    ) {
        Column(Modifier.padding(end = 48.dp, top = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Radar Occaz", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                if (demoMode) {
                    Spacer(Modifier.width(8.dp))
                    Pill("Mode démo", Color(0xFF3B1E00), Color(0xFFFFC56B))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Trouvez la bonne occasion",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Leboncoin, La Centrale et AutoScout24 comparés et classés selon vos critères.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) { Icon(Icons.Rounded.Settings, contentDescription = "Réglages") }
    }
}

@Composable
private fun RecentSearches(recent: List<SearchCriteria>, onPick: (SearchCriteria) -> Unit, onClear: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Recherches récentes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Effacer l'historique", modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { c ->
                AssistChip(
                    onClick = { onPick(c) },
                    label = {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(c.title, fontWeight = FontWeight.SemiBold)
                            Text(c.summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AutocompleteField(
    label: String,
    value: String,
    placeholder: String,
    suggestions: (String) -> List<String>,
    onValueChange: (String) -> Unit,
    tag: String,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Effacer $label") }
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag(tag).onFocusChanged { focused = it.isFocused },
    )
    val list = suggestions(value).take(12)
    val exact = list.any { TextUtil.compact(it) == TextUtil.compact(value) }
    if (focused && list.isNotEmpty() && !exact) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            list.forEach { s -> SuggestionChip(onClick = { onValueChange(s) }, label = { Text(s) }) }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int?, suffix: String, modifier: Modifier, onValueChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.let(Format::number) ?: "",
        onValueChange = { s -> onValueChange(s.filter(Char::isDigit).take(7).toIntOrNull()) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun PriorityRow(label: String, value: Int, enabled: Boolean = true, onChange: (Int) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (enabled) Priorities.LABELS[value] else "Choisissez des options",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..Priorities.MAX.toFloat(),
            steps = Priorities.MAX - 1,
            enabled = enabled,
        )
    }
}
