package com.medipote.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.medipote.app.data.Accent
import com.medipote.app.data.Backdrop
import com.medipote.app.data.Corners
import com.medipote.app.data.FontChoice
import com.medipote.app.data.HomeSection
import com.medipote.app.data.Look
import com.medipote.app.data.Settings
import com.medipote.app.data.ThemeMode
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.theme.accentSwatch
import com.medipote.app.ui.theme.customSwatch
import com.medipote.app.ui.theme.fontSample
import com.medipote.app.ui.treatments.SwitchRow
import kotlin.math.roundToInt

private fun hslColor(hue: Float, saturation: Float): Long =
    ColorUtils.HSLToColor(floatArrayOf(hue, saturation, 0.45f)).toLong() and 0xFFFFFFFFL

private fun hslOf(color: Long): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(color.toInt(), it) }

/** Everything to make the app one's own: colours, fonts, sizes, home screen, little touches. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(
    settings: Settings,
    userName: String,
    dynamicColorAvailable: Boolean,
    onChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
) {
    val look = settings.look
    fun updateLook(f: (Look) -> Look) = onChange { it.copy(look = f(it.look)) }
    var showCustom by remember { mutableStateOf(look.customColor != null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personnaliser") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Preview(look, userName)

            // ── Colours ──
            SectionTitle("Couleur")
            Panel {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Accent.entries.forEach { a ->
                        Swatch(
                            color = accentSwatch(a),
                            label = a.label,
                            selected = look.customColor == null && settings.accent == a && !settings.dynamicColor,
                        ) {
                            showCustom = false
                            onChange { it.copy(accent = a, dynamicColor = false, look = it.look.copy(customColor = null)) }
                        }
                    }
                    Swatch(
                        brush = Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                        label = "À toi",
                        selected = look.customColor != null && !settings.dynamicColor,
                    ) {
                        showCustom = true
                        val seed = look.customColor ?: hslColor(200f, 0.6f)
                        onChange { it.copy(dynamicColor = false, look = it.look.copy(customColor = seed)) }
                    }
                }
                Text(
                    when {
                        settings.dynamicColor -> "Couleurs du fond d'écran"
                        look.customColor != null -> "Ta couleur"
                        else -> settings.accent.label
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (showCustom && look.customColor != null) {
                    val hsl = hslOf(look.customColor)
                    Text("Teinte", style = MaterialTheme.typography.bodyMedium)
                    Box(
                        Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(
                            Brush.horizontalGradient((0..12).map { customSwatch(hslColor(it * 30f, 0.7f)) }),
                        ),
                    )
                    Slider(
                        value = hsl[0], valueRange = 0f..359f,
                        onValueChange = { h -> updateLook { it.copy(customColor = hslColor(h, hsl[1].coerceIn(0.2f, 0.9f))) } },
                        modifier = Modifier.semantics { contentDescription = "Teinte" },
                    )
                    Text("Intensité", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = hsl[1].coerceIn(0.15f, 0.9f), valueRange = 0.15f..0.9f,
                        onValueChange = { sat -> updateLook { it.copy(customColor = hslColor(hsl[0], sat)) } },
                        modifier = Modifier.semantics { contentDescription = "Intensité" },
                    )
                    Text(
                        "Les nuances sont ajustées pour que les textes restent lisibles, en clair comme en sombre.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (dynamicColorAvailable) {
                    HorizontalDivider()
                    SwitchRow("Couleurs du fond d'écran", settings.dynamicColor, supporting = "Reprend les couleurs de ton téléphone") { v ->
                        onChange { it.copy(dynamicColor = v) }
                    }
                }
            }

            // ── Theme and background ──
            SectionTitle("Thème et fond")
            Panel {
                Segmented(ThemeMode.entries.map { it.label }, ThemeMode.entries.indexOf(settings.themeMode)) { i -> onChange { it.copy(themeMode = ThemeMode.entries[i]) } }
                Text("Fond", style = MaterialTheme.typography.bodyMedium)
                Segmented(Backdrop.entries.map { it.label }, Backdrop.entries.indexOf(look.backdrop)) { i -> updateLook { it.copy(backdrop = Backdrop.entries[i]) } }
                if (settings.themeMode != ThemeMode.LIGHT) {
                    SwitchRow("Noir pur en mode sombre", look.pureBlack, supporting = "Un vrai noir, reposant pour les yeux et économe sur les écrans OLED") { v ->
                        updateLook { it.copy(pureBlack = v) }
                    }
                }
            }

            // ── Text ──
            SectionTitle("Texte")
            Panel {
                Text("Police des titres", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontChoice.entries.forEach { f ->
                        FilterChip(
                            selected = look.headingFont == f,
                            onClick = { updateLook { it.copy(headingFont = f) } },
                            label = {
                                Text(
                                    f.label,
                                    fontFamily = fontSample(f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Taille du texte", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${(look.textScale * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = look.textScale, valueRange = 0.85f..1.3f, steps = 8,
                    onValueChange = { v -> updateLook { it.copy(textScale = (v * 20).roundToInt() / 20f) } },
                    modifier = Modifier.semantics { contentDescription = "Taille du texte" },
                )
                Text("Les coins", style = MaterialTheme.typography.bodyMedium)
                Segmented(Corners.entries.map { it.label }, Corners.entries.indexOf(look.corners)) { i -> updateLook { it.copy(corners = Corners.entries[i]) } }
            }

            // ── You ──
            SectionTitle("Ta touche")
            Panel {
                Text("Ton emoji, à côté de « Bonjour »", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Look.AVATARS.forEach { e ->
                        val on = look.avatar == e
                        Surface(
                            onClick = { updateLook { it.copy(avatar = e) } },
                            shape = CircleShape,
                            color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = if (on) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.size(44.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Text(e, style = MaterialTheme.typography.titleLarge) } }
                    }
                }
                OutlinedTextField(
                    value = look.motto,
                    onValueChange = { v -> updateLook { it.copy(motto = v.take(90)) } },
                    label = { Text("Ma phrase du jour") },
                    placeholder = { Text("Une phrase qui te fait du bien…") },
                    supportingText = { Text("Affichée en haut de l'accueil. Laisse vide pour ne rien afficher.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    "Animations de célébration",
                    look.celebrations,
                    supporting = "La série du jour à la première ouverture, et les petites fêtes",
                ) { v -> updateLook { it.copy(celebrations = v) } }
            }

            // ── Home screen ──
            SectionTitle("Accueil")
            Panel(padding = 8) {
                Text(
                    "Choisis ce qui s'affiche et dans quel ordre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                val order = (look.homeOrder + HomeSection.entries.filter { it !in look.homeOrder }).distinct()
                order.forEachIndexed { i, section ->
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(section.emoji, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Text(section.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { updateLook { it.copy(homeOrder = order.move(i, i - 1)) } }, enabled = i > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Monter ${section.label}")
                        }
                        IconButton(onClick = { updateLook { it.copy(homeOrder = order.move(i, i + 1)) } }, enabled = i < order.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Descendre ${section.label}")
                        }
                        Switch(
                            checked = section !in look.hiddenSections,
                            onCheckedChange = { on -> updateLook { it.copy(hiddenSections = if (on) it.hiddenSections - section else it.hiddenSections + section) } },
                            modifier = Modifier.semantics { contentDescription = "Afficher ${section.label}" },
                        )
                    }
                }
                TextButton(onClick = { updateLook { it.copy(homeOrder = HomeSection.entries, hiddenSections = emptySet()) } }) {
                    Text("Ordre par défaut")
                }
            }

            FilledTonalButton(
                onClick = { onChange { it.copy(accent = Accent.MINT, dynamicColor = false, look = Look(avatar = it.look.avatar, motto = it.look.motto)) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Revenir à l'apparence d'origine") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun <T> List<T>.move(from: Int, to: Int): List<T> {
    if (to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
private fun Panel(padding: Int = 16, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(padding.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                icon = {},
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun Swatch(color: Color? = null, brush: Brush? = null, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .let { m -> if (brush != null) m.background(brush) else m.background(color ?: Color.Gray) }
                .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Couleur $label" + if (selected) ", sélectionnée" else "" },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small sample of the app with the current choices. */
@Composable
private fun Preview(look: Look, userName: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Aperçu", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Bonjour ${userName.ifBlank { "toi" }} ${look.avatar}", style = MaterialTheme.typography.headlineSmall)
            if (look.motto.isNotBlank()) {
                Text("« ${look.motto} »", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("08:00 · Lévothyroxine", style = MaterialTheme.typography.titleMedium)
                        Text("1 comprimé", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {}) { Text("Donner") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("🔥 12 jours", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("⭐ 340 pts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}
