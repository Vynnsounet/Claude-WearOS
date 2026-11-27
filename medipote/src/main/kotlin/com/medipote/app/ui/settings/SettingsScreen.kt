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
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Save
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    petName: String,
    /** The wake-up time the nights suggest, if there are enough of them. */
    usualWake: Int? = null,
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
    quietReminders: Int = 0,
    onFixQuietReminders: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenHomeLayout: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onOpenWellbeing: () -> Unit = {},
    onOpenTour: () -> Unit = {},
    onOpenIdeas: () -> Unit = {},
    onOpenTravel: () -> Unit = {},
    onOpenCards: () -> Unit = {},
    /** When the Wear OS app last got in touch (0 = never), and whether it rings the reminders itself. */
    watchLastSeen: Long = 0L,
    watchRings: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
    onSyncWatch: () -> Unit = {},
    lockEnabled: Boolean = false,
    onToggleLock: (Boolean) -> Unit = {},
    lastBackup: java.time.LocalDateTime? = null,
    openSection: String? = null,
) {
    var renaming by remember { mutableStateOf(false) }
    // One group open at a time; a search result can open the right one (« settings?section=reminders »).
    var openGroup by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(openSection) }
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
            SettingsGroup("look", "🎨", "Apparence", openGroup, { openGroup = it }) {
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
                androidx.compose.material3.FilledTonalButton(onClick = onOpenAppearance, modifier = Modifier.fillMaxWidth()) {
                    Text("🎨  Personnaliser : couleurs, police…")
                }
                androidx.compose.material3.FilledTonalButton(onClick = onOpenHomeLayout, modifier = Modifier.fillMaxWidth()) {
                    Text("🏠  Mon accueil : ce qui s'affiche")
                }
                androidx.compose.material3.FilledTonalButton(onClick = onOpenCards, modifier = Modifier.fillMaxWidth()) {
                    Text("🗂️  Mes cartes : ce qui s'affiche dans chaque onglet")
                }
            }

            }

            SettingsGroup("reminders", "🔔", "Rappels et notifications", openGroup, { openGroup = it }) {
            SectionTitle("Mes repas et mon réveil")
            SettingsCard { RoutineCard(settings.routine, usualWake) { r -> onChange { it.copy(routine = r) } } }
            SectionTitle("Pause")
            SettingsCard {
                val clock = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), java.time.ZoneId.systemDefault())
                val pause = settings.pause
                if (com.medipote.app.domain.Pause.active(pause, clock)) {
                    Text(com.medipote.app.domain.Pause.label(pause), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (pause.doses) "⚠️ Les rappels de prises sont aussi en pause. Les rendez-vous sonnent toujours."
                        else "Les prises et les rendez-vous sonnent toujours.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onChange { it.copy(pause = com.medipote.app.data.ReminderPause()) } }, modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reprendre les rappels maintenant") }
                } else {
                    var withDoses by remember { mutableStateOf(false) }
                    Text(
                        "Un voyage, une mauvaise semaine : l'eau, le stock, les rituels, le récap et le sommeil se taisent un moment. " +
                            "Les prises et les rendez-vous sonnent toujours.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.medipote.app.domain.Pause.Length.entries.forEach { length ->
                            androidx.compose.material3.FilterChip(
                                selected = false,
                                onClick = { onChange { it.copy(pause = com.medipote.app.domain.Pause.start(length, withDoses, clock)) } },
                                label = { Text("⏸️  ${length.label}") },
                            )
                        }
                    }
                    SwitchRow(
                        "Aussi les rappels de prises", withDoses,
                        supporting = if (withDoses) "⚠️ Pendant la pause, tu ne seras pas prévenu·e des prises : à n'utiliser que si tu es sûr·e de toi."
                        else "Déconseillé, sauf si tu sais que tu n'en as pas besoin.",
                    ) { withDoses = it }
                }
            }

            SectionTitle("Voyage")
            SettingsCard {
                Text(
                    "Quand tu changes de fuseau horaire, tes prises se décalent doucement au lieu de sonner à l'heure du bout du monde.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.FilledTonalButton(onClick = onOpenTravel, modifier = Modifier.fillMaxWidth()) { Text("✈️  Préparer un voyage") }
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
                if (quietReminders > 0) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Rappels sans pop-up") },
                        supportingContent = {
                            Text(
                                "Le téléphone affiche ${if (quietReminders > 1) "certains rappels" else "un type de rappel"} discrètement, sans pop-up ni son. " +
                                    "Active « Afficher en pop-up » (ou « Notifications contextuelles ») et le son.",
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.NotificationsOff, null, tint = AppTheme.status.warning) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onFixQuietReminders),
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

            SectionTitle("Heures calmes")
            SettingsCard {
                val q = settings.quiet
                fun update(f: (com.medipote.app.data.QuietHours) -> com.medipote.app.data.QuietHours) = onChange { it.copy(quiet = f(it.quiet)) }
                SwitchRow(
                    "Pas de rappel doux la nuit", q.enabled,
                    supporting = "L'eau, le stock, les rituels, le récap et « bien dormi ? » attendent le matin. Les prises et les rendez-vous sonnent toujours.",
                ) { v -> update { it.copy(enabled = v) } }
                if (q.enabled) {
                    com.medipote.app.ui.components.NumberStepper("À partir de", q.startHour, { v -> update { it.copy(startHour = v) } }, 18..23, suffix = " h")
                    com.medipote.app.ui.components.NumberStepper("Jusqu'à", q.endHour, { v -> update { it.copy(endHour = v) } }, 4..11, suffix = " h")
                }
            }

            SectionTitle("Hydratation")
            SettingsCard {
                val w = settings.water
                fun update(f: (com.medipote.app.data.WaterSettings) -> com.medipote.app.data.WaterSettings) = onChange { it.copy(water = f(it.water)) }
                SwitchRow("Rappels pour boire de l'eau", w.enabled, supporting = "Une notification régulière tant que l'objectif du jour n'est pas atteint") { v -> update { it.copy(enabled = v) } }
                com.medipote.app.ui.components.NumberStepper(
                    "Objectif par jour", (com.medipote.app.domain.Water.goalMl(w) / 250).coerceIn(2, 24),
                    { v -> update { it.copy(goalMl = v * 250) } }, 2..24, display = { com.medipote.app.domain.Water.format(it * 250) },
                )
                com.medipote.app.ui.components.NumberStepper("Taille de mon verre", w.glassMl / 50, { v -> update { it.copy(glassMl = v * 50) } }, 2..10, display = { "${it * 50} ml" })
                SwitchRow("Compter les boissons chaudes", w.countHotDrinks, supporting = "Thé, tisane, café sans sucre : tu peux les noter avec l'eau et elles comptent dans le total") { v -> update { it.copy(countHotDrinks = v) } }
                Text(
                    "Le bouton « verre » des rappels, des widgets et de la montre note un verre de ${w.glassMl} ml.",
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

            }

            SettingsGroup("sleep", "🌙", "Sommeil", openGroup, { openGroup = it }) {
            SettingsCard {
                val sl = settings.sleep
                fun update(f: (com.medipote.app.data.SleepSettings) -> com.medipote.app.data.SleepSettings) = onChange { it.copy(sleep = f(it.sleep)) }
                com.medipote.app.ui.components.NumberStepper(
                    "Objectif de sommeil", sl.goalMinutes / 30, { v -> update { it.copy(goalMinutes = v * 30) } }, 10..24,
                    display = { com.medipote.app.domain.Sleep.format(it * 30) },
                )
                SwitchRow(
                    "Compter les siestes dans mon objectif", sl.napsInGoal,
                    supporting = "Seulement pour l'objectif : les chiffres de la nuit ne changent jamais",
                ) { v -> update { it.copy(napsInGoal = v) } }
                SwitchRow("Rappel le matin", sl.reminder, supporting = "« Bien dormi ? » tant que la nuit n'est pas notée") { v -> update { it.copy(reminder = v) } }
                if (sl.reminder) {
                    com.medipote.app.ui.components.NumberStepper("À", sl.reminderMinute / 60, { v -> update { it.copy(reminderMinute = v * 60) } }, 5..14, suffix = " h")
                }
                SwitchRow("Un petit mot le soir pour me préparer à dormir", sl.windDown, supporting = "« Doucement vers la nuit », jamais pendant les heures calmes") { v -> update { it.copy(windDown = v) } }
                if (sl.windDown) {
                    com.medipote.app.ui.components.NumberStepper("À", sl.windDownMinute / 60, { v -> update { it.copy(windDownMinute = v * 60 + sl.windDownMinute % 60) } }, 18..23, suffix = " h")
                }
            }

            SectionTitle("Rituels et récap")
            SettingsCard {
                val r = settings.rituals
                fun update(f: (com.medipote.app.data.RitualSettings) -> com.medipote.app.data.RitualSettings) = onChange { it.copy(rituals = f(it.rituals)) }
                SwitchRow("Rappel du rituel du matin", r.morningReminder, supporting = "Ta nuit, tes prises du matin et ton humeur, en un seul moment") { v -> update { it.copy(morningReminder = v) } }
                if (r.morningReminder) {
                    com.medipote.app.ui.components.NumberStepper("À", r.morningMinute / 30, { v -> update { it.copy(morningMinute = v * 30) } }, 10..23, display = { "%d h %02d".format(it / 2, it % 2 * 30) })
                }
                SwitchRow("Rappel du rituel du soir", r.eveningReminder, supporting = "Tes prises du soir, ton journal et ton eau") { v -> update { it.copy(eveningReminder = v) } }
                if (r.eveningReminder) {
                    com.medipote.app.ui.components.NumberStepper("À", r.eveningMinute / 30, { v -> update { it.copy(eveningMinute = v * 30) } }, 34..47, display = { "%d h %02d".format(it / 2, it % 2 * 30) })
                }
                SwitchRow("Récap de la semaine", r.weeklyRecap, supporting = "Une notification le dimanche à 19 h") { v -> update { it.copy(weeklyRecap = v) } }
            }

            }

            SettingsGroup("health", "🩺", "Mesures de santé", openGroup, { openGroup = it }) {
            SettingsCard {
                Text(
                    "Choisis ce que tu veux suivre : seules les mesures cochées apparaissent (accueil, Suivi › Mesures, rapport).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.medipote.app.data.MeasureType.entries.forEach { type ->
                    SwitchRow("${type.emoji}  ${type.label}", type in settings.measures) { on ->
                        onChange { it.copy(measures = if (on) it.measures + type else it.measures - type) }
                    }
                }
            }

            }

            SettingsGroup("privacy", "🔒", "Confidentialité et sauvegarde", openGroup, { openGroup = it }) {
            SectionTitle("Confidentialité")
            SettingsCard {
                SwitchRow(
                    "Fiche d'urgence sur l'écran de verrouillage de l'appli", settings.emergencyOnLock,
                    supporting = "Allergies, traitements et personnes à prévenir visibles par les secours sans ton code. Désactivé tant que tu ne l'as pas choisi.",
                ) { v -> onChange { it.copy(emergencyOnLock = v) } }
                SwitchRow(
                    "Verrouiller l'appli",
                    lockEnabled,
                    supporting = "Avec l'empreinte, le visage ou le code du téléphone, à l'ouverture et après 1 min en arrière-plan. " +
                        "Les rappels n'affichent pas le nom des médicaments sur l'écran verrouillé ; les widgets, eux, restent visibles sur l'écran d'accueil.",
                    onChange = onToggleLock,
                )
            }

            SectionTitle("Compte")
            SettingsCard(padding = 0) {
                ListItem(
                    headlineContent = { Text("Sauvegarde et restauration") },
                    supportingContent = {
                        Text(lastBackup?.let { "Dernière sauvegarde le ${it.format(com.medipote.app.ui.components.dayMonthYear)}" } ?: "Aucune sauvegarde : pense à en faire une")
                    },
                    leadingContent = { Icon(Icons.Outlined.Save, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenBackup),
                )
                HorizontalDivider()
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

            }

            SettingsGroup("mind", "🌿", "Ce qui m'accompagne et soutien", openGroup, { openGroup = it }) {
            SectionTitle("Ce qui m'accompagne")
            SettingsCard {
                Text(
                    "Ce que tu vis, tes symptômes à suivre et tes petites missions pour les jours lourds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.FilledTonalButton(onClick = onOpenWellbeing, modifier = Modifier.fillMaxWidth()) {
                    Text("🌿  Mes conditions, symptômes et missions")
                }
            }

            SectionTitle("Comprendre")
            SettingsCard {
                SwitchRow(
                    "Me le dire sur l'accueil", settings.checkups,
                    supporting = "Quand mes notes montrent quelque chose qui mérite peut-être un avis médical (jamais un diagnostic). Calculé sur le téléphone, avec des règles écrites à la main.",
                ) { v -> onChange { it.copy(checkups = v) } }
            }

            SectionTitle("Soutien")
            SettingsCard {
                Text(
                    "Les personnes à prévenir, ton médecin ou ton psy, et ce que propose « Ça ne va pas ».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.FilledTonalButton(onClick = onOpenSupport, modifier = Modifier.fillMaxWidth()) {
                    Text("🤗  Mes personnes de confiance et réglages")
                }
            }

            }

            SettingsGroup("watch", "⌚", "Montre", openGroup, { openGroup = it }) {
            SettingsCard {
                if (watchLastSeen == 0L) {
                    Text("⌚ Aucune montre reliée", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Installe MédiPote sur ta montre Wear OS puis ouvre-la une fois : elle se relie toute seule. " +
                            "Tu pourras valider tes prises, noter ton eau et mesurer ton pouls au poignet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val minutes = ((nowMs - watchLastSeen) / 60_000L).coerceAtLeast(0)
                    val ago = when {
                        minutes < 1 -> "à l'instant"
                        minutes < 60 -> "il y a $minutes min"
                        minutes < 48 * 60 -> "il y a ${minutes / 60} h"
                        else -> "il y a ${minutes / (24 * 60)} jours"
                    }
                    Text("⌚ Montre reliée", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Dernier contact $ago. " +
                            if (watchRings) "Les rappels de prises et d'eau vibrent sur la montre."
                            else "Autorise les notifications de MédiPote sur la montre pour qu'elle vibre aux rappels.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.FilledTonalButton(onClick = onSyncWatch, modifier = Modifier.fillMaxWidth()) {
                    Text("Synchroniser la montre maintenant")
                }
            }

            }

            SettingsGroup("game", "🎮", "Jeu et récompenses", openGroup, { openGroup = it }) {
            SectionTitle("Mode sobre")
            SettingsCard {
                SwitchRow(
                    "Mode sobre", settings.sober,
                    supporting = "Plus de points, de série, de boutique ni de badges à l'écran. Le suivi reste le même, et tu peux revenir quand tu veux.",
                ) { v -> onChange { it.copy(sober = v) } }
            }

            SectionTitle("Règles du jeu")
            SettingsCard {
                var rulesOpen by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clickable { rulesOpen = !rulesOpen },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Points, série et compagnon : le barème", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (rulesOpen) "Masquer" else "Voir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (rulesOpen) {
                Rule("Prise à l'heure (de ${Rules.EARLY_WINDOW_MINUTES} min avant à ${Rules.ON_TIME_MINUTES} min après)", "+${Rules.POINTS_ON_TIME} pts")
                Rule("En retard (jusqu'à ${Rules.LATE_MINUTES / 60} h)", "+${Rules.POINTS_LATE} pts")
                Rule("Très en retard (plus de ${Rules.LATE_MINUTES / 60} h)", "+${Rules.POINTS_VERY_LATE} pts")
                Rule("Noté « Déjà pris » plus de ${Rules.EARLY_WINDOW_MINUTES} min en avance", "+${Rules.POINTS_EARLY} pts")
                Rule("Journée parfaite (tout à l'heure)", "+${Rules.PERFECT_DAY_BONUS} pts")
                Rule("Eau : chaque tranche de 250 ml (même après l'objectif, jusqu'au double)", "+${Rules.WATER_GLASS_POINTS} pts")
                Rule("Objectif d'hydratation atteint", "+${Rules.WATER_BONUS} pts")
                Rule("Journal du jour rempli", "+${Rules.JOURNAL_BONUS} pts")
                Rule("Nuit notée le matin même", "+${Rules.SLEEP_BONUS} pts")
                Rule("Rituel du matin ou du soir (une fois par jour chacun)", "+${com.medipote.app.domain.Rituals.BONUS} pts")
                Rule("Première mesure du jour (tension, poids…)", "+${com.medipote.app.domain.Care.MEASURE_POINTS} pts")
                Rule("Premier symptôme noté du jour (quelle que soit sa force)", "+${com.medipote.app.domain.Care.SYMPTOM_POINTS} pts")
                Rule("Rendez-vous préparé · rendez-vous honoré", "+${com.medipote.app.domain.Care.PREP_POINTS} · +${com.medipote.app.domain.Care.ATTENDED_POINTS} pts")
                Rule("Traitement racheté quand le stock était bas", "+${com.medipote.app.domain.Care.RESTOCK_POINTS} pts")
                Rule("Ordonnance renouvelée", "+${com.medipote.app.domain.Care.RENEWAL_POINTS} pts")
                Rule("Petite victoire ou « J'ai essayé » (3 par jour au plus)", "+${com.medipote.app.domain.Wellbeing.MISSION_POINTS} pts")
                Rule("Sept jours de suite à prendre soin de toi", "+${com.medipote.app.domain.Care.GIFT_POINTS} pts, cadeau-surprise")
                Rule("Défi de la semaine réussi", "+20 à +40 pts")
                Rule("Joker de série", "1 jour oublié par semaine ne casse pas la série")
                Rule("Premier câlin du jour (caresse ou appui long sur ton compagnon)", "+${Rules.CUDDLE_BONUS} pts")
                Rule("Connexion quotidienne (le jour change à ${Rules.DAY_START_HOUR} h du matin)", "+${Rules.STREAK_POINTS_PER_DAY} pts × jours de série, jusqu'à +${Rules.STREAK_POINTS_CAP}")
                Rule("Paliers de série", Rules.STREAK_MILESTONES.entries.joinToString(" · ") { "${it.key} j : +${it.value}" })
                Text(
                    "Une prise se donne à partir de ${Rules.EARLY_WINDOW_MINUTES} min avant l'heure prévue (plus tôt, avec « Déjà pris ? »). Une prise oubliée affaiblit ton compagnon pendant deux jours, et un gros retard le rend malade. Les points ne se retirent jamais.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }

            }

            SettingsGroup("about", "ℹ️", "Aide et à propos", openGroup, { openGroup = it }) {
            SectionTitle("Aide")
            SettingsCard {
                androidx.compose.material3.FilledTonalButton(onClick = onOpenTour, modifier = Modifier.fillMaxWidth()) {
                    Text("👋  Revoir le petit tour")
                }
                androidx.compose.material3.FilledTonalButton(onClick = onOpenIdeas, modifier = Modifier.fillMaxWidth()) {
                    Text("💡  Une idée, un souci ?")
                }
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

/** A group of settings that opens on a tap, one at a time, so the page is short and nothing is lost in a long scroll. */
@Composable
private fun SettingsGroup(id: String, emoji: String, title: String, open: String?, onOpen: (String?) -> Unit, content: @Composable () -> Unit) {
    val isOpen = open == id
    Card(
        onClick = { onOpen(if (isOpen) null else id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                if (isOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                if (isOpen) "Replier $title" else "Ouvrir $title",
            )
        }
    }
    if (isOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) { content() }
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
