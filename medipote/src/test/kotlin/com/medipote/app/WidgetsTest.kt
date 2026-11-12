package com.medipote.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseTime
import com.medipote.app.data.GameState
import com.medipote.app.data.Settings
import com.medipote.app.data.Treatment
import com.medipote.app.data.WaterSettings
import com.medipote.app.domain.Schedule
import com.medipote.app.widget.DosesWidget
import com.medipote.app.widget.PetWidget
import com.medipote.app.widget.WaterWidget
import com.medipote.app.widget.WidgetActions
import com.medipote.app.widget.Widgets
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each widget as the launcher would (RemoteViews), saves PNGs, and checks the widget actions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi")
class WidgetsTest {

    private val context: MediPoteApplication = ApplicationProvider.getApplicationContext()
    /** Frozen at 12:10 today so the widgets always show the same situation. */
    private val now = java.time.LocalDate.now().atTime(12, 10)

    @Before
    fun setUp() {
        context.repository.clock = java.time.Clock.fixed(now.atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault())
        val start = now.toLocalDate().minusDays(5)
        // Added this morning: a realistic, healthy pet waiting for its noon dose.
        val created = now.toLocalDate().atTime(7, 0)
        val state = AppState(
            onboarded = true,
            treatments = listOf(
                Treatment("a", name = "Lévothyroxine", strength = "75 µg", doses = listOf(DoseTime(8 * 60)), startDate = start, createdAt = created),
                Treatment("b", name = "Amlodipine", strength = "5 mg", doses = listOf(DoseTime(12 * 60)), startDate = start, createdAt = created),
                Treatment("c", name = "Metformine", strength = "1000 mg", doses = listOf(DoseTime(20 * 60)), startDate = start, createdAt = created),
            ),
            game = GameState(petName = "Pilou", equipped = mapOf("COLOR" to "color_mint", "BACKGROUND" to "bg_meadow", "HAT" to "hat_cap")),
            settings = Settings(water = WaterSettings(goalGlasses = 8)),
        )
        context.repository.update { state }
        // The 8:00 dose was given on time.
        val morning = Schedule.itemsOn(state.treatments, emptyMap(), now.toLocalDate(), now.toLocalDate().atTime(8, 0))
            .first { it.occurrence.treatment.id == "a" }
        context.repository.update { com.medipote.app.domain.Game.give(it, morning, now.toLocalDate().atTime(8, 5))!!.state }
        context.repository.update { com.medipote.app.domain.Game.addWater(it, 5, now).first }
    }

    @org.junit.After
    fun tearDown() {
        context.repository.clock = java.time.Clock.systemDefaultZone()
    }

    @OptIn(ExperimentalGlanceApi::class)
    private fun render(widget: GlanceAppWidget, name: String, w: Int, h: Int): View = runBlocking {
        val views = widget.compose(context, size = DpSize(w.dp, h.dp))
        val density = context.resources.displayMetrics.density
        val wPx = (w * density).toInt()
        val hPx = (h * density).toInt()
        val root = FrameLayout(context)
        val view = views.apply(context, root)
        root.addView(view, FrameLayout.LayoutParams(wPx, hPx))
        root.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, wPx, hPx)
        val bitmap = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0) // transparent: also used as the widget picker preview
        root.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "widget_$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        view
    }

    private fun texts(view: View): List<String> = when (view) {
        is android.widget.TextView -> listOf(view.text.toString())
        is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun petWidgetSizes() {
        val wide = texts(render(PetWidget(), "pet_wide", 300, 130))
        assertTrue(wide.toString(), wide.any { it.startsWith("Pilou") })
        assertTrue(wide.toString(), wide.any { it == "Donner 💊" })
        assertTrue(wide.toString(), wide.any { it.contains("Amlodipine") })
        render(PetWidget(), "pet_small", 130, 130)
        render(PetWidget(), "pet_tall", 200, 260)
    }

    @Test
    fun dosesWidget() {
        val t = texts(render(DosesWidget(), "doses", 300, 220))
        assertTrue(t.toString(), t.any { it.contains("Amlodipine") })
        assertTrue(t.toString(), t.contains("Donner"))
    }

    @Test
    fun waterWidget() {
        val t = texts(render(WaterWidget(), "water", 130, 130))
        assertTrue(t.toString(), t.contains("5 / 8"))
        render(WaterWidget(), "water_wide", 250, 130)
    }

    @Test
    fun widgetActionsGiveAndDrink() = runBlocking {
        val before = context.repository.state.value
        val due = Widgets.todayItems(before, context.repository.now()).filter { it.canGive }
        assertTrue(due.isNotEmpty())
        WidgetActions.give(context, due.first().occurrence.key)
        val after = context.repository.state.value
        assertEquals(before.doseLogs.size + 1, after.doseLogs.size)
        assertTrue(after.game.points > before.game.points)

        WidgetActions.water(context, 1)
        assertEquals(6, context.repository.state.value.entryFor(now.toLocalDate()).waterGlasses)
    }
}
