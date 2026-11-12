package com.medipote.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Settings
import com.medipote.app.data.ThemeMode
import com.medipote.app.domain.Rules
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.theme.AppTheme
import com.medipote.app.ui.treatments.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    petName: String,
    notificationsAllowed: Boolean,
    exactAlarmsAllowed: Boolean,
    dynamicColorAvailable: Boolean,
    onBack: () -> Unit,
    onChange: ((Settings) -> Settings) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onRenamePet: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onReset: () -> Unit,
    onTestNotifications: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    batteryRestricted: Boolean = false,
    onOpenBatterySettings: () -> Unit = {},
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Apparence")
            SettingsCard {
                Text("Thème", style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { onChange { it.copy(themeMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
                Text("Couleur d'accent", style = MaterialTheme.typography.bodyLarge)
                AccentPicker(settings.accent, enabled = !settings.dynamicColor) { a -> onChange { it.copy(accent = a) } }
                if (dynamicColorAvailable) {
                    SwitchRow(
                        "Couleurs du fond d'écran",
                        settings.dynamicColor,
                        supporting = "Adapte les couleurs de l'app à ton téléphone",
                    ) { v -> onChange { it.copy(dynamicColor = v) } }
                }
            }

            SectionTitle("Rappels")
            SettingsCard {
                SwitchRow("Notifications de prise", settings.notificationsEnabled && notificationsAllowed, supporting = "Une notification à l'heure de chaque prise") { v ->
                    if (v && !notificationsAllowed) onRequestNotifications()
                    onChange { it.copy(notificationsEnabled = v) }
                }
                SwitchRow(
                    "Relance en cas d'oubli",
                    settings.followUpReminder,
                    supporting = "Nouveau rappel 30 minutes après si la prise n'est pas validée",
                ) { v -> onChange { it.copy(followUpReminder = v) } }
                if (!notificationsAllowed) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Notifications bloquées") },
                        supportingContent = { Text("Autorise les notifications de MédiPote dans les réglages du téléphone.") },
                        leadingContent = { Icon(Icons.Outlined.NotificationsOff, null, tint = AppTheme.status.critical) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onOpenNotificationSettings),
                    )
                }
                if (batteryRestricted) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Optimisation de la batterie") },
                        supportingContent = { Text("Sur certains téléphones, elle retarde ou bloque les rappels. Choisis « Non restreinte » pour MédiPote.") },
                        leadingContent = { Icon(Icons.Outlined.BatteryAlert, null, tint = AppTheme.status.warning) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onOpenBatterySettings),
                    )
                }
                HorizontalDivider()
                androidx.compose.material3.OutlinedButton(onClick = onTestNotifications, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.NotificationsActive, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Text("Tester les notifications")
                }
                Text(
                    "Une notification tout de suite, puis une autre dans 1 minute pour vérifier les rappels programmés.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!exactAlarmsAllowed) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Autoriser les alarmes exactes") },
                        supportingContent = { Text("Sans cette autorisation, les rappels peuvent arriver avec quelques minutes de retard.") },
                        leadingContent = { Icon(Icons.Outlined.Alarm, null, tint = AppTheme.status.warning) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onRequestExactAlarms),
                    )
                }
            }

            SectionTitle("Hydratation")
            SettingsCard {
                val w = settings.water
                fun update(f: (com.medipote.app.data.WaterSettings) -> com.medipote.app.data.WaterSettings) = onChange { it.copy(water = f(it.water)) }
                SwitchRow("Rappels pour boire de l'eau", w.enabled, supporting = "Une notification régulière tant que l'objectif du jour n'est pas atteint") { v -> update { it.copy(enabled = v) } }
                com.medipote.app.ui.components.NumberStepper("Objectif", w.goalGlasses, { v -> update { it.copy(goalGlasses = v) } }, 1..20, suffix = " verres")
                com.medipote.app.ui.components.NumberStepper("Taille d'un verre", w.glassMl / 50, { v -> update { it.copy(glassMl = v * 50) } }, 2..10, display = { "${it * 50} mL" })
                Text(
                    "Soit ${"%.1f".format(java.util.Locale.FRANCE, w.goalGlasses * w.glassMl / 1000.0)} L par jour.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (w.enabled) {
                    com.medipote.app.ui.components.NumberStepper("De", w.startMinute / 60, { v -> update { it.copy(startMinute = v * 60) } }, 5..(w.endMinute / 60 - 1), suffix = " h")
                    com.medipote.app.ui.components.NumberStepper("À", w.endMinute / 60, { v -> update { it.copy(endMinute = v * 60) } }, (w.startMinute / 60 + 1)..23, suffix = " h")
                    Text("Fréquence", style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val options = listOf(60 to "1 h", 90 to "1 h 30", 120 to "2 h", 180 to "3 h")
                        options.forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = w.intervalMinutes == m,
                                onClick = { update { it.copy(intervalMinutes = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) { Text(label) }
                        }
                    }
                }
            }

            SectionTitle("Compte")
            SettingsCard(padding = 0) {
                ListItem(
                    headlineContent = { Text("Profil médical") },
                    supportingContent = { Text("Infos personnelles, antécédents, allergies, cycle") },
                    leadingContent = { Icon(Icons.Outlined.AccountCircle, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenProfile),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Nom du compagnon") },
                    supportingContent = { Text(petName) },
                    leadingContent = { Icon(Icons.Outlined.Pets, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { renaming = true },
                )
            }

            SectionTitle("Règles du jeu")
            SettingsCard {
                Rule("Prise à l'heure (jusqu'à ${Rules.ON_TIME_MINUTES} min de retard)", "+${Rules.POINTS_ON_TIME} pts")
                Rule("En retard (jusqu'à ${Rules.LATE_MINUTES / 60} h)", "+${Rules.POINTS_LATE} pts (malus −${Rules.POINTS_ON_TIME - Rules.POINTS_LATE})")
                Rule("Très en retard (plus de ${Rules.LATE_MINUTES / 60} h)", "+${Rules.POINTS_VERY_LATE} pts (malus −${Rules.POINTS_ON_TIME - Rules.POINTS_VERY_LATE})")
                Rule("Journée parfaite (tout à l'heure)", "+${Rules.PERFECT_DAY_BONUS} pts")
                Rule("Verre d'eau (jusqu'à l'objectif du jour)", "+${Rules.WATER_GLASS_POINTS} pts")
                Rule("Objectif d'hydratation atteint", "+${Rules.WATER_BONUS} pts")
                Rule("Journal du jour rempli", "+${Rules.JOURNAL_BONUS} pts")
                Rule("Premier câlin du jour (caresse ou appui long sur ton compagnon)", "+${Rules.CUDDLE_BONUS} pts")
                Rule("Connexion quotidienne", "+${Rules.STREAK_POINTS_PER_DAY} pts × jours de série, jusqu'à +${Rules.STREAK_POINTS_CAP}")
                Rule("Paliers de série", Rules.STREAK_MILESTONES.entries.joinToString(" · ") { "${it.key} j : +${it.value}" })
                Text(
                    "Une prise peut être donnée jusqu'à 1 h avant l'heure prévue. Une prise oubliée affaiblit ton compagnon pendant deux jours, et un gros retard le rend malade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("À propos")
            SettingsCard {
                Text(
                    "MédiPote est un aide-mémoire. Il ne remplace pas l'avis d'un professionnel de santé. " +
                        "Tes données restent sur ton téléphone et ne sont pas sauvegardées dans le cloud.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SettingsCard(padding = 0) {
                ListItem(
                    headlineContent = { Text("Effacer toutes les données", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { confirmReset = true },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(petName) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nom du compagnon") },
            text = { OutlinedTextField(name, { name = it.take(20) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRenamePet(name); renaming = false }, enabled = name.isNotBlank()) { Text("Valider") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annuler") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Tout effacer ?") },
            text = { Text("Profil, traitements, historique, points et accessoires seront supprimés définitivement.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("Effacer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun AccentPicker(selected: com.medipote.app.data.Accent, enabled: Boolean, onPick: (com.medipote.app.data.Accent) -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        com.medipote.app.data.Accent.entries.forEach { a ->
            val on = a == selected
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.foundation.layout.Box(
                    Modifier.padding(2.dp).size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(com.medipote.app.ui.theme.accentSwatch(a).copy(alpha = if (enabled) 1f else 0.35f))
                        .border(if (on) 3.dp else 0.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                        .clickable(enabled = enabled) { onPick(a) }
                        .semantics { contentDescription = "Accent ${a.label}" + if (on) ", sélectionné" else "" },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    if (on) Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, tint = Color.White)
                }
                Text(a.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingsCard(padding: Int = 16, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(padding.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Rule(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
