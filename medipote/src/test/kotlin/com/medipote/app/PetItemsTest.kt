package com.medipote.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.medipote.app.domain.PetMood
import com.medipote.app.domain.Shop
import com.medipote.app.domain.Slot
import com.medipote.app.domain.Stage
import com.medipote.app.ui.pet.drawPetForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every item of the shop is drawn, changes the companion, and stays inside the picture. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PetItemsTest {
    private val px = 240

    private fun render(equipped: Map<String, String>, mood: PetMood = PetMood.CONTENT, stage: Stage = Stage.BUD): IntArray {
        val bitmap = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) { drawPetForPreview(equipped, mood, stage) }
        val out = IntArray(px * px)
        bitmap.asAndroidBitmap().getPixels(out, 0, px, 0, 0, px, px)
        return out
    }

    private val plain = mapOf("COLOR" to "color_mint")

    @Test
    fun everyItemChangesTheCompanion() {
        val base = render(plain)
        val baseNight = render(plain + ("BACKGROUND" to "bg_night"))
        Shop.items.forEach { item ->
            val withItem = when (item.slot) {
                Slot.COLOR -> render(mapOf("COLOR" to item.id))
                Slot.BACKGROUND -> render(plain + ("BACKGROUND" to item.id))
                else -> render(plain + (item.slot.name to item.id))
            }
            val reference = if (item.slot == Slot.BACKGROUND) base else base
            val differing = withItem.indices.count { withItem[it] != reference[it] }
            // The default colour and room are the references themselves.
            if (item.id == "color_mint" || item.id == "bg_room") return@forEach
            assertTrue("${item.id} is not drawn (or changes nothing)", differing > 30)
        }
        assertTrue(baseNight.isNotEmpty())
    }

    @Test
    fun whatIsWornStaysInsideThePicture() {
        val base = render(plain)
        val wearable = setOf(Slot.HAT, Slot.GLASSES, Slot.NECK, Slot.FACE, Slot.FEET, Slot.PATTERN, Slot.TOY, Slot.BACK)
        val problems = mutableListOf<String>()
        Shop.items.filter { it.slot in wearable }.forEach { item ->
            val img = render(plain + (item.slot.name to item.id))
            var minX = px; var maxX = -1; var minY = px; var maxY = -1
            for (y in 0 until px) for (x in 0 until px) if (img[y * px + x] != base[y * px + x]) {
                minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
            }
            if (maxX < 0) problems += "${item.id}: nothing drawn"
            else if (!(minX > 1 && maxX < px - 2 && minY > 1 && maxY < px - 2)) problems += "${item.id} touches the edge: x $minX..$maxX, y $minY..$maxY"
        }
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    @Test
    fun theWholeWardrobeAtOnceAndInEveryMood() {
        val all = Slot.entries.associate { slot -> slot.name to Shop.items.last { it.slot == slot }.id }
        PetMood.entries.forEach { mood -> Stage.entries.forEach { stage -> assertEquals(px * px, render(all, mood, stage).size) } }
        // and no two items of one slot share an id
        assertEquals(Shop.items.size, Shop.items.map { it.id }.toSet().size)
    }

    @Test
    fun theShopHasPlentyToChooseFrom() {
        Slot.entries.forEach { slot -> assertTrue("${slot.label}: ${Shop.items.count { it.slot == slot }}", Shop.items.count { it.slot == slot } >= 8) }
        assertTrue(Shop.items.size >= 180)
        // the dearest is out of reach for a while, so there is always something to save for
        assertTrue(Shop.items.maxOf { it.price } >= 500)
    }
}
