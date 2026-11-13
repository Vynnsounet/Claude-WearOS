package com.medipote.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.medipote.app.MainActivity
import com.medipote.app.MediPoteApplication
import com.medipote.app.data.Accent
import com.medipote.app.data.AppState
import com.medipote.app.data.TakeStatus
import com.medipote.app.domain.DoseItem
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Game
import com.medipote.app.domain.PetMood
import com.medipote.app.domain.Schedule
import com.medipote.app.notifications.Notifications
import com.medipote.app.notifications.ReminderScheduler
import com.medipote.app.repository
import com.medipote.app.ui.pet.drawBackground
import com.medipote.app.ui.pet.drawPet
import com.medipote.app.ui.theme.appColorScheme
import java.time.LocalDateTime
import kotlinx.coroutines.launch

// ─────────────────────────── Shared ───────────────────────────

object Widgets {
    /** Refreshes every widget; called whenever the app's data or the time-dependent state changes. */
    suspend fun updateAll(context: Context) {
        PetWidget().updateAll(context)
        DosesWidget().updateAll(context)
        WaterWidget().updateAll(context)
    }

    fun requestUpdate(context: Context) {
        val app = context.applicationContext as MediPoteApplication
        app.appScope.launch { runCatching { updateAll(app) } }
    }

    /** Doses that can be given right now: today's, plus late-evening ones carried over from yesterday. */
    fun todayItems(state: AppState, now: LocalDateTime): List<DoseItem> {
        val logs = state.doseLogs.associateBy { it.key }
        return Schedule.carriedOver(state.treatments, logs, now) + Schedule.itemsOn(state.treatments, logs, now.toLocalDate(), now)
    }
}

/** The pet drawn into a bitmap with rounded corners, since widgets can't run Compose drawing. */
fun renderPet(mood: PetMood, equipped: Map<String, String>, widthPx: Int, heightPx: Int): Bitmap {
    val image = ImageBitmap(widthPx, heightPx)
    val size = Size(widthPx.toFloat(), heightPx.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), size) {
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.minDimension * 0.12f))) }
        clipPath(clip) {
            drawBackground(equipped["BACKGROUND"])
            drawPet(mood, equipped, 0.5f, 0f, 0.3f, 1f)
        }
    }
    return image.asAndroidBitmap()
}

@Composable
private fun WidgetTheme(accent: Accent, content: @Composable () -> Unit) {
    GlanceTheme(colors = ColorProviders(light = appColorScheme(accent, false), dark = appColorScheme(accent, true)), content = content)
}

@Composable
private fun Card(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>()),
        content = content,
    )
}

private val title @Composable get() = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
private val body @Composable get() = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)
private val muted @Composable get() = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)

// ─────────────────────────── Actions ───────────────────────────

val DoseKey = ActionParameters.Key<String>("dose")
val WaterDelta = ActionParameters.Key<Int>("delta")

/** Gives one dose (or every dose due now when no key is passed) from a widget. */
class GiveDoseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetActions.give(context, parameters[DoseKey])
    }
}

class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetActions.water(context, parameters[WaterDelta] ?: 1)
    }
}

object WidgetActions {
    suspend fun give(context: Context, key: String?) {
        val repo = context.repository
        val now = repo.now()
        var lowStock = false
        val times = mutableSetOf<LocalDateTime>()
        repo.update { s ->
            var state = s
            Widgets.todayItems(s, now).filter { it.canGive && (key == null || it.occurrence.key == key) }.forEach { item ->
                Game.give(state, item, now)?.let {
                    state = it.state
                    if (it.lowStock != null) lowStock = true
                    times += item.occurrence.scheduled
                }
            }
            state
        }
        val state = repo.state.value
        val taken = state.doseLogs.mapTo(HashSet()) { it.key }
        times.filter { t -> Schedule.occurrencesOn(state.treatments, t.toLocalDate()).filter { it.scheduled == t }.all { it.key in taken } }
            .forEach { Notifications.cancel(context, it) }
        if (lowStock) Notifications.showStock(context, state)
        ReminderScheduler.reschedule(context, state, now)
        Widgets.updateAll(context)
    }

    suspend fun water(context: Context, delta: Int) {
        val repo = context.repository
        var goal = false
        repo.update { s -> Game.addWater(s, delta, repo.now()).let { (updated, r) -> goal = r.goalBonus > 0; updated } }
        if (goal) Notifications.cancelWater(context)
        Widgets.updateAll(context)
    }
}

// ─────────────────────────── 1. Companion ───────────────────────────

class PetWidget : GlanceAppWidget() {
    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val TALL = DpSize(180.dp, 220.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.repository
        provideContent {
            val state by repo.state.collectAsState()
            WidgetTheme(state.settings.accent) { PetContent(state, repo.now()) }
        }
    }
}

@Composable
fun PetContent(state: AppState, now: LocalDateTime) {
    val size = LocalSize.current
    val condition = Game.petCondition(state, now)
    val items = Widgets.todayItems(state, now)
    val due = items.filter { it.canGive }
    val next = items.firstOrNull { it.state == DoseState.UPCOMING }
    val pet = remember(condition.mood, state.game.equipped) { renderPet(condition.mood, state.game.equipped, 300, 240) }
    val giveButton: @Composable () -> Unit = {
        if (due.isNotEmpty()) {
            Button(
                text = if (due.size == 1) "Donner 💊" else "Donner (${due.size}) 💊",
                onClick = actionRunCallback<GiveDoseAction>(),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }
    val status: @Composable () -> Unit = {
        Text(
            when {
                due.isNotEmpty() -> condition.message
                next != null -> "Prochaine prise à ${next.occurrence.dose.timeLabel} : ${next.occurrence.treatment.name}"
                else -> condition.message
            },
            style = body, maxLines = 3,
        )
    }
    Card {
        when {
            size.width < 200.dp && size.height < 180.dp -> Column(
                GlanceModifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(ImageProvider(pet), state.game.petName, GlanceModifier.defaultWeight().fillMaxWidth())
                if (due.isNotEmpty()) {
                    Spacer(GlanceModifier.height(6.dp))
                    Button("Donner", actionRunCallback<GiveDoseAction>(), GlanceModifier.fillMaxWidth())
                } else {
                    Text("${state.game.petName} · ${condition.health} %", style = muted, maxLines = 1)
                }
            }
            size.height >= 200.dp -> Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                Image(ImageProvider(pet), state.game.petName, GlanceModifier.defaultWeight().fillMaxWidth())
                Spacer(GlanceModifier.height(8.dp))
                Text("${state.game.petName} · santé ${condition.health} %", style = title, maxLines = 1)
                status()
                Spacer(GlanceModifier.height(6.dp))
                giveButton()
            }
            else -> Row(GlanceModifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(pet), state.game.petName, GlanceModifier.size(110.dp, 90.dp))
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("${state.game.petName} · santé ${condition.health} %", style = title, maxLines = 1)
                    status()
                    Spacer(GlanceModifier.height(4.dp))
                    giveButton()
                }
            }
        }
    }
}

class PetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetWidget()
}

// ─────────────────────────── 2. Today's doses ───────────────────────────

class DosesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.repository
        provideContent {
            val state by repo.state.collectAsState()
            WidgetTheme(state.settings.accent) { DosesContent(state, repo.now()) }
        }
    }
}

@Composable
fun DosesContent(state: AppState, now: LocalDateTime) {
    val items = Widgets.todayItems(state, now)
    val today = items.filter { it.occurrence.date == now.toLocalDate() }
    val done = today.count { it.state == DoseState.TAKEN }
    Card {
        Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("💊 Aujourd'hui", style = title, modifier = GlanceModifier.defaultWeight())
                Text("$done / ${today.size}", style = muted)
            }
            Spacer(GlanceModifier.height(6.dp))
            if (items.isEmpty()) {
                Text(
                    if (state.treatments.isEmpty()) "Ajoute tes traitements dans l'app." else "Aucune prise prévue aujourd'hui.",
                    style = muted,
                )
            } else {
                // A plain column (not a scrolling list) renders reliably on every launcher; the doses
                // still to give come first so they are always visible.
                val fit = ((LocalSize.current.height.value - 56f) / 44f).toInt().coerceAtLeast(1)
                val ordered = items.sortedBy { if (it.canGive) 0 else if (it.state == DoseState.UPCOMING) 1 else 2 }
                val shown = if (ordered.size > fit) ordered.take(fit - 1) else ordered
                shown.sortedBy { it.occurrence.scheduled }.forEach { DoseRow(it) }
                if (shown.size < items.size) Text("+ ${items.size - shown.size} autres", style = muted)
            }
        }
    }
}

@Composable
private fun DoseRow(item: DoseItem) {
    val t = item.occurrence.treatment
    val (label, color) = when (item.state) {
        DoseState.TAKEN -> when (item.log?.status) {
            TakeStatus.EARLY -> "✓ pris en avance"
            TakeStatus.LATE, TakeStatus.VERY_LATE -> "✓ en retard"
            else -> "✓ donné"
        } to GlanceTheme.colors.primary
        DoseState.MISSED -> "oublié" to GlanceTheme.colors.error
        DoseState.OVERDUE -> "en retard" to GlanceTheme.colors.error
        DoseState.DUE -> "c'est l'heure" to GlanceTheme.colors.primary
        DoseState.UPCOMING -> t.unit.format(item.occurrence.dose.quantity) to GlanceTheme.colors.onSurfaceVariant
    }
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(item.occurrence.dose.timeLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.width(48.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(t.displayName, style = body, maxLines = 1)
            Text(label, style = TextStyle(color = color, fontSize = 12.sp), maxLines = 1)
        }
        if (item.canGive) {
            Button(
                "Donner",
                actionRunCallback<GiveDoseAction>(actionParametersOf(DoseKey to item.occurrence.key)),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (item.state == DoseState.DUE) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer,
                    contentColor = if (item.state == DoseState.DUE) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer,
                ),
            )
        }
    }
}

class DosesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DosesWidget()
}

// ─────────────────────────── 3. Water ───────────────────────────

class WaterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.repository
        provideContent {
            val state by repo.state.collectAsState()
            WidgetTheme(state.settings.accent) { WaterContent(state, repo.now()) }
        }
    }
}

private val WaterBlue = ColorProvider(Color(0xFF3A9BDC))

@Composable
fun WaterContent(state: AppState, now: LocalDateTime) {
    val wide = LocalSize.current.width >= 200.dp
    val compact = LocalSize.current.height < 150.dp
    val goal = state.settings.water.goalGlasses
    val glasses = state.entryFor(now.toLocalDate()).waterGlasses
    Card {
        Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
            Text("💧 Hydratation", style = muted, maxLines = 1)
            Text(
                "$glasses / $goal",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (!compact) Text(if (glasses >= goal) "Objectif atteint 🎉" else "verres aujourd'hui", style = muted, maxLines = 1)
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = (glasses.toFloat() / goal).coerceAtMost(1f),
                color = WaterBlue,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            )
            Spacer(GlanceModifier.height(8.dp))
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                if (wide) {
                    Button(
                        "−", actionRunCallback<AddWaterAction>(actionParametersOf(WaterDelta to -1)),
                        colors = ButtonDefaults.buttonColors(GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.onSecondaryContainer),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                }
                Button(
                    "+1 verre", actionRunCallback<AddWaterAction>(actionParametersOf(WaterDelta to 1)),
                    modifier = GlanceModifier.defaultWeight(),
                    colors = ButtonDefaults.buttonColors(WaterBlue, ColorProvider(Color.White)),
                )
            }
        }
    }
}

class WaterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaterWidget()
}
