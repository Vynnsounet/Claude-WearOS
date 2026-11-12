package com.medipote.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Profile
import com.medipote.app.domain.PetMood
import com.medipote.app.ui.components.LabeledValue
import com.medipote.app.ui.components.dayMonthYear
import com.medipote.app.ui.pet.PetView
import java.time.LocalDate

@Composable
fun WelcomeScreen(onStart: () -> Unit, animate: Boolean = true) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(MaterialTheme.shapes.extraLarge)) {
            PetView(PetMood.HAPPY, mapOf("COLOR" to "color_mint", "BACKGROUND" to "bg_meadow"), Modifier.fillMaxSize(), animate = animate)
        }
        Text("Bienvenue sur MédiPote", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Ton compagnon t'aide à ne plus oublier tes traitements. Prends soin de toi… et de lui !",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Feature(Icons.Rounded.Medication, "Tes traitements", "Cherche tes médicaments dans le guide intégré et indique ta posologie.")
        Feature(Icons.Rounded.NotificationsActive, "Des rappels à l'heure", "Ton compagnon te prévient quand c'est le moment.")
        Feature(Icons.Rounded.Pets, "Un compagnon à soigner", "Donne-lui ses médicaments à l'heure : un oubli et il tombe malade.")
        Feature(Icons.Rounded.EmojiEvents, "Points et récompenses", "Gagne des points, enchaîne les jours et offre-lui des accessoires.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Commencer") }
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepScaffold(
    step: Int,
    title: String,
    onBack: (() -> Unit)?,
    buttonText: String,
    buttonEnabled: Boolean,
    onNext: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                LinearProgressIndicator(
                    progress = { step / 3f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    drawStopIndicator = {},
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onNext,
                    enabled = buttonEnabled,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text(buttonText) }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) { content() }
    }
}

@Composable
fun ProfileSetupScreen(profile: Profile?, today: LocalDate, onBack: () -> Unit, onSave: (Profile) -> Unit) {
    val draft = remember { ProfileDraft(profile, today) }
    StepScaffold(1, "Ton profil médical", onBack, "Continuer", draft.valid, { onSave(draft.toProfile()) }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "Ces informations restent sur ton téléphone. Seul le prénom est obligatoire.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProfileForm(draft)
        }
    }
}

@Composable
fun PetNamingScreen(currentName: String, onBack: () -> Unit, animate: Boolean = true, onDone: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    StepScaffold(2, "Ton compagnon", onBack, "Continuer", name.isNotBlank(), { onDone(name) }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(MaterialTheme.shapes.extraLarge)) {
                PetView(PetMood.HAPPY, mapOf("COLOR" to "color_mint", "BACKGROUND" to "bg_room"), Modifier.fillMaxSize(), animate = animate)
            }
            Text(
                "Voici ton compagnon ! Il compte sur toi pour lui donner tes médicaments à l'heure. Comment veux-tu l'appeler ?",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = { Text("Nom du compagnon") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Shows the medical profile, with an edit mode using the same form as the onboarding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(profile: Profile, today: LocalDate, onBack: () -> Unit, onSave: (Profile) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val draft = remember(editing) { ProfileDraft(profile, today) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Modifier le profil" else "Profil médical") },
                navigationIcon = {
                    IconButton(onClick = { if (editing) editing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour")
                    }
                },
                actions = {
                    if (editing) {
                        TextButton(onClick = { onSave(draft.toProfile()); editing = false }, enabled = draft.valid) { Text("Enregistrer") }
                    } else {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Modifier") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) {
                ProfileForm(draft)
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.headlineSmall)
                        Row {
                            LabeledValue("Âge", profile.age?.let { "$it ans" } ?: "—", Modifier.weight(1f))
                            LabeledValue("Sexe", profile.sex?.label ?: "—", Modifier.weight(1f))
                        }
                        Row {
                            LabeledValue("Poids", profile.weightKg?.let { "${it.toString().removeSuffix(".0").replace('.', ',')} kg" } ?: "—", Modifier.weight(1f))
                            LabeledValue("Taille", profile.heightCm?.let { "$it cm" } ?: "—", Modifier.weight(1f))
                        }
                        profile.bmi?.let { LabeledValue("IMC", "%.1f".format(java.util.Locale.FRANCE, it)) }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabeledValue("Antécédents médicaux", profile.medicalHistory.ifEmpty { listOf("Aucun renseigné") }.joinToString(" · "))
                        HorizontalDivider()
                        LabeledValue("Allergies", profile.allergies.ifEmpty { listOf("Aucune renseignée") }.joinToString(" · "))
                        HorizontalDivider()
                        LabeledValue(
                            "Suivi du cycle",
                            if (!profile.menstruating) "Désactivé"
                            else profile.cycle?.periodStarts?.maxOrNull()?.let { "Dernières règles le ${it.format(dayMonthYear)}" } ?: "Activé",
                        )
                    }
                }
            }
        }
    }
}
