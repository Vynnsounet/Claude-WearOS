package com.medipote.app.ui.pill

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.DoseUnit
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.PillPack
import com.medipote.app.data.PillRegimen
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.domain.DoseItem
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Schedule
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.DatePickerModal
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.NumberStepper
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.TimePickerModal
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val PILL_PINK = Color(0xFFF59AB9)
private val PILL_PINK_DARK = Color(0xFFD9668F)

enum class PillCell { TAKEN, TAKEN_LATE, UNTRACKED, TODAY, MISSED, FUTURE, PLACEBO, BREAK }

data class BlisterDay(val date: LocalDate, val index: Int, val cell: PillCell, val item: DoseItem?)

/** The current pack, day by day, with what happened to each pill. */
fun blister(state: AppState, pill: Treatment, now: LocalDateTime): List<BlisterDay> {
    val pack = pill.pill ?: return emptyList()
    val today = now.toLocalDate()
    val start = pack.currentPackStart(today)
    val logs = state.doseLogs.associateBy { it.key }
    return (0 until pack.cycleLength).map { i ->
        val date = start.plusDays(i.toLong())
        val item = Schedule.itemsOn(listOf(pill), logs, date, now).firstOrNull()
        val cell = when {
            i >= pack.activeDays && !pack.placebo -> PillCell.BREAK
            item == null -> if (date.isAfter(today)) (if (i >= pack.activeDays) PillCell.PLACEBO else PillCell.FUTURE) else PillCell.UNTRACKED
            item.state == DoseState.TAKEN ->
                if (item.log?.status == TakeStatus.LATE || item.log?.status == TakeStatus.VERY_LATE) PillCell.TAKEN_LATE else PillCell.TAKEN
            item.state == DoseState.MISSED -> PillCell.MISSED
            item.canGive -> PillCell.TODAY
            i >= pack.activeDays -> PillCell.PLACEBO
            else -> PillCell.FUTURE
        }
        BlisterDay(date, i, cell, item)
    }
}

/** Embedded content of the "Pilule" tab. */
@Composable
fun PillScreen(
    state: AppState,
    now: LocalDateTime,
    onSetup: () -> Unit,
    onTake: (DoseItem) -> Unit,
    onNewPack: (LocalDate) -> Unit,
    animate: Boolean = true,
    onTakeEarly: (DoseItem) -> Unit = {},
) {
    val pill = state.treatments.firstOrNull { it.pill != null }
    var confirmEarly by remember { mutableStateOf<DoseItem?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (pill == null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniBlister()
                    Text("Ta pilule, jour après jour", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Indique ta pilule et son rythme (21 jours + 7 jours de pause, 24 + 4, en continu…) : " +
                            "tu verras ta plaquette, les jours de pause, et tu recevras un rappel chaque jour.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onSetup) { Text("Configurer ma pilule") }
                }
            }
            return@Column
        }
        val pack = pill.pill!!
        val days = remember(state, now) { blister(state, pill, now) }
        val today = now.toLocalDate()
        val todayDay = days.firstOrNull { it.date == today }
        val nextPack = pack.currentPackStart(today).plusDays(pack.cycleLength.toLong())
        var pickPack by remember { mutableStateOf(false) }

        // ── Header ──
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(pill.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${PillRegimen.of(pack).label} · chaque jour à ${pill.doses.first().timeLabel}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val pos = pack.position(today) ?: 0
                when {
                    todayDay?.cell == PillCell.BREAK -> {
                        Text("Semaine de pause 🌙", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Pas de comprimé aujourd'hui. Nouvelle plaquette le ${nextPack.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE))}.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    else -> {
                        Text(
                            if (pos >= pack.activeDays) "Placebo ${pos - pack.activeDays + 1} sur ${pack.breakDays}"
                            else "Comprimé ${pos + 1} sur ${pack.activeDays}",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        val item = todayDay?.item
                        when {
                            item?.state == DoseState.TAKEN -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = AppTheme.status.good)
                                Spacer(Modifier.width(6.dp))
                                Text("Pris aujourd'hui à ${item.log!!.takenAt.format(hourMinute)}", style = MaterialTheme.typography.bodyLarge)
                            }
                            item != null && item.canGive -> Button(onClick = { onTake(item) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Text(if (item.state == DoseState.OVERDUE) "Prendre mon comprimé (en retard)" else "Prendre mon comprimé")
                            }
                            item != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(6.dp))
                                    Text("À prendre à ${pill.doses.first().timeLabel}", style = MaterialTheme.typography.bodyLarge)
                                }
                                if (item.canMarkEarly(now)) {
                                    OutlinedButton(onClick = { confirmEarly = item }) { Text("Je l'ai déjà prise") }
                                }
                            }
                        }
                        if (pack.breakDays > 0 && !pack.placebo) {
                            val breakStart = pack.currentPackStart(today).plusDays(pack.activeDays.toLong())
                            Text(
                                "Pause du ${breakStart.format(com.medipote.app.ui.components.dayMonth)} au ${nextPack.minusDays(1).format(com.medipote.app.ui.components.dayMonth)}",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ── The blister ──
        Blister(days, today, animate, onTake)
        Legend()

        val taken = days.count { it.cell == PillCell.TAKEN || it.cell == PillCell.TAKEN_LATE }
        val owed = days.count { it.item != null && (it.item.state == DoseState.TAKEN || it.item.state == DoseState.MISSED) }
        if (owed > 0) {
            Text(
                "Cette plaquette : $taken comprimé${if (taken > 1) "s" else ""} pris sur $owed prévus jusqu'ici.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        pill.stock?.let {
            Text(
                "Réserve : ${it.toInt()} comprimés" + (pill.daysLeft?.let { d -> " · environ $d jours" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pill.isLowStock) AppTheme.status.critical else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickPack = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Autorenew, null)
                Spacer(Modifier.width(6.dp))
                Text("Nouvelle plaquette")
            }
            OutlinedButton(onClick = onSetup, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Edit, null)
                Spacer(Modifier.width(6.dp))
                Text("Modifier")
            }
        }
        Disclaimer(
            "Oubli ou retard important ? Suis les consignes de la notice de ta pilule (souvent : plus de 12 h de retard) " +
                "et demande conseil à ton pharmacien ou ton médecin : la protection peut être diminuée.",
        )
        if (pickPack) {
            DatePickerModal(today, onDismiss = { pickPack = false }, onConfirm = { onNewPack(it); pickPack = false }, maxDate = today.plusDays(7))
        }
        confirmEarly?.let { early ->
            com.medipote.app.ui.home.EarlyDialog(
                name = pill.name,
                time = early.occurrence.dose.timeLabel,
                onConfirm = { confirmEarly = null; onTakeEarly(early) },
                onDismiss = { confirmEarly = null },
            )
        }
    }
}

@Composable
private fun MiniBlister() {
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE9EDF0), Color(0xFFCBD3D9))), cornerRadius = CornerRadius(14.dp.toPx()))
        val r = size.height * 0.28f
        for (i in 0 until 7) {
            val c = Offset(size.width * (i + 0.5f) / 7, size.height / 2)
            drawPillDot(c, r, if (i < 5) PILL_PINK else Color.White)
        }
    }
}

private fun DrawScope.drawPillDot(c: Offset, r: Float, color: Color) {
    drawCircle(Color.Black.copy(alpha = 0.12f), r * 1.12f, c + Offset(0f, r * 0.12f))
    drawCircle(color, r, c)
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.32f, c + Offset(-r * 0.35f, -r * 0.35f))
    drawCircle(Color.Black.copy(alpha = 0.1f), r, c, style = Stroke(r * 0.08f))
}

@Composable
private fun Blister(days: List<BlisterDay>, today: LocalDate, animate: Boolean, onTake: (DoseItem) -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by if (animate) transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    else remember { androidx.compose.runtime.mutableFloatStateOf(0.6f) }
    val ring = MaterialTheme.colorScheme.primary
    val good = AppTheme.status.good
    val warn = AppTheme.status.warning
    val bad = AppTheme.status.critical
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(2.dp),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    Brush.linearGradient(listOf(Color(0xFFF1F4F6), Color(0xFFC9D1D7), Color(0xFFE8ECEF)), Offset.Zero, Offset(size.width, size.height)),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                )
                drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(6.dp.toPx(), 6.dp.toPx()), Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()), CornerRadius(22.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    days.take(7).forEach { d ->
                        Text(
                            d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRANCE).trimEnd('.').capitalized(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF4A5560),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { d ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                val desc = "${d.date.dayOfMonth} : " + when (d.cell) {
                                    PillCell.TAKEN -> "pris à l'heure"
                                    PillCell.TAKEN_LATE -> "pris en retard"
                                    PillCell.UNTRACKED -> "avant le suivi"
                                    PillCell.TODAY -> "à prendre aujourd'hui"
                                    PillCell.MISSED -> "oublié"
                                    PillCell.FUTURE -> "à venir"
                                    PillCell.PLACEBO -> "placebo"
                                    PillCell.BREAK -> "pause"
                                }
                                Canvas(
                                    Modifier.fillMaxWidth().aspectRatio(1f).padding(3.dp).semantics { contentDescription = desc }
                                        .let { m -> if (d.cell == PillCell.TODAY && d.item != null) m.clickable { onTake(d.item) } else m },
                                ) {
                                    val c = center
                                    val r = size.minDimension * 0.36f
                                    when (d.cell) {
                                        PillCell.FUTURE -> drawPillDot(c, r, PILL_PINK)
                                        PillCell.PLACEBO -> drawPillDot(c, r, Color.White)
                                        PillCell.TODAY -> {
                                            drawCircle(ring.copy(alpha = 0.25f + 0.35f * pulse), r * (1.35f + 0.1f * pulse), c)
                                            drawPillDot(c, r, PILL_PINK_DARK)
                                        }
                                        PillCell.TAKEN, PillCell.TAKEN_LATE, PillCell.UNTRACKED -> {
                                            // Empty, torn foil
                                            drawCircle(Color(0xFFB7C0C7), r, c)
                                            drawCircle(Color(0xFF8E99A2), r, c, style = Stroke(r * 0.1f))
                                            drawLine(Color.White.copy(alpha = 0.7f), c + Offset(-r * 0.5f, -r * 0.2f), c + Offset(r * 0.1f, r * 0.4f), r * 0.08f)
                                            if (d.cell != PillCell.UNTRACKED) {
                                                val col = if (d.cell == PillCell.TAKEN) good else warn
                                                drawCircle(col, r * 0.5f, c + Offset(r * 0.6f, r * 0.6f))
                                                drawLine(Color.White, c + Offset(r * 0.38f, r * 0.62f), c + Offset(r * 0.55f, r * 0.8f), r * 0.12f, StrokeCap.Round)
                                                drawLine(Color.White, c + Offset(r * 0.55f, r * 0.8f), c + Offset(r * 0.85f, r * 0.42f), r * 0.12f, StrokeCap.Round)
                                            }
                                        }
                                        PillCell.MISSED -> {
                                            drawPillDot(c, r, PILL_PINK.copy(alpha = 0.5f))
                                            drawCircle(bad, r, c, style = Stroke(r * 0.14f))
                                            drawLine(bad, c + Offset(-r * 0.45f, -r * 0.45f), c + Offset(r * 0.45f, r * 0.45f), r * 0.14f, StrokeCap.Round)
                                            drawLine(bad, c + Offset(r * 0.45f, -r * 0.45f), c + Offset(-r * 0.45f, r * 0.45f), r * 0.14f, StrokeCap.Round)
                                        }
                                        PillCell.BREAK -> drawCircle(
                                            Color(0xFF8E99A2), r, c,
                                            style = Stroke(r * 0.1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.3f, r * 0.22f))),
                                        )
                                    }
                                }
                                Text(
                                    "${d.date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4A5560),
                                    fontWeight = if (d.date == today) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val items = listOf(
        PILL_PINK to "À prendre", Color.White to "Placebo", Color(0xFFB7C0C7) to "Pris",
        AppTheme.status.critical to "Oublié", Color.Transparent to "Pause",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (color, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(12.dp)) {
                    if (text == "Pause") drawCircle(Color(0xFF8E99A2), size.minDimension / 2.4f, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f))))
                    else {
                        drawCircle(color, size.minDimension / 2)
                        drawCircle(Color(0xFF8E99A2), size.minDimension / 2, style = Stroke(1.dp.toPx()))
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────── Setup ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PillSetupScreen(existing: Treatment?, today: LocalDate, onSave: (Treatment) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    val guidePills = remember { DrugGuide.drugs.filter { it.drugClass.startsWith("Contraceptif") } }
    var drugId by remember { mutableStateOf(existing?.drugId) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var regimen by remember { mutableStateOf(existing?.pill?.let(PillRegimen::of) ?: PillRegimen.R21_7) }
    var packStart by remember { mutableStateOf(existing?.pill?.currentPackStart(today) ?: today) }
    var minute by remember { mutableIntStateOf(existing?.doses?.firstOrNull()?.minuteOfDay ?: (21 * 60)) }
    var packs by remember {
        mutableIntStateOf(existing?.stock?.let { s -> existing.packSize?.let { p -> kotlin.math.ceil(s / p).toInt() } } ?: 0)
    }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ma pilule") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        val pack = PillPack(regimen.activeDays, regimen.breakDays, regimen.placebo, packStart)
                        val perPack = if (regimen.placebo) pack.cycleLength else regimen.activeDays
                        val usedInPack = (pack.position(today) ?: 0).coerceAtMost(perPack)
                        val stock = if (packs > 0) ((packs - 1) * perPack + (perPack - usedInPack)).toDouble() else null
                        onSave(
                            Treatment(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                drugId = drugId,
                                name = name.trim(),
                                unit = DoseUnit.TABLET,
                                doses = listOf(DoseTime(minute)),
                                startDate = minOf(existing?.startDate ?: packStart, packStart),
                                createdAt = existing?.createdAt,
                                pill = pack,
                                stock = stock,
                                packSize = if (packs > 0) perPack else null,
                                lowStockDays = 7,
                            ),
                        )
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Ta pilule")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                guidePills.forEach { d ->
                    FilterChip(
                        selected = drugId == d.id,
                        onClick = {
                            drugId = d.id
                            name = d.name.substringBefore(',')
                            regimen = if (d.id == "desogestrel") PillRegimen.CONTINUOUS else PillRegimen.R21_7
                        },
                        label = { Text(d.dci) },
                    )
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40); if (guidePills.none { g -> g.name.substringBefore(',') == it }) drugId = null },
                label = { Text("Nom de ta pilule *") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("Rythme de la plaquette")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillRegimen.entries.forEach { r -> FilterChip(selected = regimen == r, onClick = { regimen = r }, label = { Text(r.label) }) }
            }
            Text(
                when {
                    regimen.breakDays == 0 -> "Un comprimé chaque jour, sans interruption entre les plaquettes."
                    regimen.placebo -> "Un comprimé chaque jour ; les ${regimen.breakDays} derniers sont des placebos."
                    else -> "${regimen.activeDays} jours de comprimés puis ${regimen.breakDays} jours sans, et la plaquette suivante recommence."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionTitle("Plaquette en cours")
            DateField("Premier comprimé de la plaquette en cours", packStart, { packStart = it }, maxDate = today.plusDays(7))
            OutlinedButton(onClick = { pickTime = true }) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(6.dp))
                Text("Heure de prise : %02d:%02d".format(minute / 60, minute % 60))
            }
            SectionTitle("Stock")
            NumberStepper("Plaquettes restantes (dont celle en cours)", packs, { packs = it }, 0..13)
            Text(
                if (packs == 0) "0 = stock non suivi." else "Tu seras prévenu·e une semaine avant d'en manquer.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Disclaimer("Respecte toujours la notice de ta pilule et l'avis de ton médecin, notamment pour le démarrage et les oublis.")
        }
    }
    if (pickTime) TimePickerModal(minute, onDismiss = { pickTime = false }) { minute = it; pickTime = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Arrêter le suivi de la pilule ?") },
            text = { Text("Les prises passées restent dans tes statistiques.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Arrêter") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

