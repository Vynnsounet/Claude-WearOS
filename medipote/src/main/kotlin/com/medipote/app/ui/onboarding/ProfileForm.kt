package com.medipote.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medipote.app.data.CycleInfo
import com.medipote.app.data.Profile
import com.medipote.app.data.Sex
import com.medipote.app.domain.Cycle
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.NumberStepper
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.treatments.SwitchRow
import java.time.LocalDate

val HISTORY_SUGGESTIONS = listOf(
    "Hypertension artérielle", "Diabète de type 1", "Diabète de type 2", "Asthme", "BPCO", "Hypercholestérolémie",
    "Hypothyroïdie", "Insuffisance cardiaque", "Fibrillation atriale", "Insuffisance rénale", "Épilepsie", "Migraine",
    "Dépression", "Trouble anxieux", "Arthrose", "Reflux gastro-œsophagien", "Ostéoporose", "Antécédent de cancer",
)
val ALLERGY_SUGGESTIONS = listOf("Pénicilline", "Aspirine", "AINS", "Sulfamides", "Iode", "Codéine", "Latex", "Arachide")

/** Editable copy of the profile; numbers are kept as text while typing. */
class ProfileDraft(profile: Profile?, private val today: LocalDate) {
    var name by mutableStateOf(profile?.name.orEmpty())
    var age by mutableStateOf(profile?.age?.toString().orEmpty())
    var weight by mutableStateOf(profile?.weightKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',') }.orEmpty())
    var height by mutableStateOf(profile?.heightCm?.toString().orEmpty())
    var sex by mutableStateOf(profile?.sex)
    var history by mutableStateOf(profile?.medicalHistory.orEmpty())
    var allergies by mutableStateOf(profile?.allergies.orEmpty())
    var menstruating by mutableStateOf(profile?.menstruating ?: false)
    var menstruatingTouched by mutableStateOf(profile != null)
    var lastPeriod by mutableStateOf(profile?.cycle?.periodStarts?.maxOrNull())
    var cycleLength by mutableIntStateOf(profile?.cycle?.cycleLength ?: 28)
    var periodLength by mutableIntStateOf(profile?.cycle?.periodLength ?: 5)
    private val previousCycle = profile?.cycle

    val ageValue get() = age.toIntOrNull()?.takeIf { it in 0..125 }
    val weightValue get() = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..400.0 }
    val heightValue get() = height.toIntOrNull()?.takeIf { it in 30..250 }
    val ageError get() = age.isNotBlank() && ageValue == null
    val weightError get() = weight.isNotBlank() && weightValue == null
    val heightError get() = height.isNotBlank() && heightValue == null
    val valid get() = name.isNotBlank() && !ageError && !weightError && !heightError

    fun toProfile(): Profile {
        val cycle = if (menstruating) {
            val base = previousCycle ?: CycleInfo(emptyList())
            val withDate = lastPeriod?.let { Cycle.logPeriod(base, it) } ?: base
            withDate.copy(cycleLength = cycleLength, periodLength = periodLength)
        } else previousCycle
        return Profile(
            name = name.trim(),
            age = ageValue,
            weightKg = weightValue,
            heightCm = heightValue,
            sex = sex,
            medicalHistory = history,
            allergies = allergies,
            menstruating = menstruating,
            cycle = cycle,
        )
    }

    val maxDate: LocalDate get() = today
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileForm(draft: ProfileDraft, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Identité")
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft.name = it },
            label = { Text("Prénom *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Âge", draft.age, { draft.age = it }, "ans", draft.ageError, Modifier.weight(1f))
            NumberField("Poids", draft.weight, { draft.weight = it }, "kg", draft.weightError, Modifier.weight(1f), decimal = true)
            NumberField("Taille", draft.height, { draft.height = it }, "cm", draft.heightError, Modifier.weight(1f))
        }

        Text("Sexe", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = draft.sex == s,
                    onClick = {
                        draft.sex = s
                        if (!draft.menstruatingTouched) draft.menstruating = s == Sex.FEMALE
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, Sex.entries.size),
                ) { Text(s.label) }
            }
        }

        SectionTitle("Cycle menstruel")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(
                    "J'ai des règles",
                    draft.menstruating,
                    supporting = "Active le suivi de ton cycle dans un onglet dédié",
                ) {
                    draft.menstruating = it
                    draft.menstruatingTouched = true
                }
                AnimatedVisibility(draft.menstruating) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField(
                            "Début des dernières règles",
                            draft.lastPeriod,
                            { draft.lastPeriod = it },
                            maxDate = draft.maxDate,
                        )
                        NumberStepper("Durée habituelle du cycle", draft.cycleLength, { draft.cycleLength = it }, 20..45, suffix = " j")
                        NumberStepper("Durée des règles", draft.periodLength, { draft.periodLength = it }, 2..10, suffix = " j")
                    }
                }
            }
        }

        SectionTitle("Antécédents médicaux")
        TagPicker(
            suggestions = HISTORY_SUGGESTIONS,
            selected = draft.history,
            onChange = { draft.history = it },
            placeholder = "Autre antécédent",
        )

        SectionTitle("Allergies")
        TagPicker(
            suggestions = ALLERGY_SUGGESTIONS,
            selected = draft.allergies,
            onChange = { draft.allergies = it },
            placeholder = "Autre allergie",
        )
        Text(
            "Tes allergies servent à t'alerter quand tu ajoutes un médicament qui pourrait être concerné.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String,
    error: Boolean,
    modifier: Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(6)) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPicker(suggestions: List<String>, selected: List<String>, onChange: (List<String>) -> Unit, placeholder: String) {
    var custom by remember { mutableStateOf("") }
    val all = (suggestions + selected).distinct()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        all.forEach { tag ->
            val on = tag in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - tag else selected + tag) },
                label = { Text(tag) },
                leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
            )
        }
    }
    fun add() {
        val t = custom.trim()
        if (t.isNotEmpty() && selected.none { it.equals(t, ignoreCase = true) }) onChange(selected + t.replaceFirstChar { it.uppercase() })
        custom = ""
    }
    OutlinedTextField(
        value = custom,
        onValueChange = { custom = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        trailingIcon = { IconButton(onClick = ::add, enabled = custom.isNotBlank()) { Icon(Icons.Rounded.Add, "Ajouter") } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        modifier = Modifier.fillMaxWidth(),
    )
}
