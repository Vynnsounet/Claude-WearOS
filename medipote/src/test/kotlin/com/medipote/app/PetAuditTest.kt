package com.medipote.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.medipote.app.domain.PetMood
import com.medipote.app.domain.Shop
import com.medipote.app.domain.Slot
import com.medipote.app.domain.Stage
import com.medipote.app.ui.pet.drawPetForPreview
import com.medipote.app.ui.theme.MediPoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Not a check: draws every item on the companion, to look at where it sits (build/screenshots/pet_audit_*.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1100dp-h2600dp-xhdpi")
class PetAuditTest {
    @get:Rule
    val compose = createComposeRule()

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @OptIn(ExperimentalLayoutApi::class)
    private fun sheet(name: String, cells: List<Pair<String, Map<String, String>>>, mood: PetMood = PetMood.CONTENT, stage: Stage = Stage.BUD) {
        compose.setContent {
            MediPoteTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        cells.forEach { (label, equipped) ->
                            Column {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                                Canvas(Modifier.size(170.dp)) { drawPetForPreview(equipped, mood, stage) }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        save(name)
    }

    private fun items(slot: Slot) = Shop.items.filter { it.slot == slot }.map { it.id to mapOf("COLOR" to "color_mint", slot.name to it.id) }

    @Test fun hats() = sheet("pet_audit_hats", items(Slot.HAT))
    @Test fun glasses() = sheet("pet_audit_glasses", items(Slot.GLASSES))
    @Test fun necks() = sheet("pet_audit_necks", items(Slot.NECK))
    @Test fun toys() = sheet("pet_audit_toys", items(Slot.TOY))
    @Test fun effects() = sheet("pet_audit_effects", items(Slot.EFFECT).map { (id, m) -> id to (m + ("BACKGROUND" to "bg_night")) })
    @Test fun faces() = sheet("pet_audit_faces", items(Slot.FACE))
    @Test fun feet() = sheet("pet_audit_feet", items(Slot.FEET))
    @Test fun backs() = sheet("pet_audit_backs", items(Slot.BACK))
    @Test fun patterns() = sheet("pet_audit_patterns", items(Slot.PATTERN))
    @Test fun backgrounds() = sheet("pet_audit_backgrounds", items(Slot.BACKGROUND).map { (id, m) -> id to mapOf("COLOR" to "color_peach", "BACKGROUND" to id) })
    @Test fun colors() = sheet("pet_audit_colors", items(Slot.COLOR).map { (id, m) -> id to mapOf("COLOR" to id) })

    private fun mix(vararg ids: String) = ids.map { id -> val slot = Shop.item(id)!!.slot.name; id to mapOf("COLOR" to "color_peach", slot to id) }

    @Test fun moodsSleeping() = sheet("pet_audit_sleeping", mix("glasses_swim", "glasses_ski", "face_lashes", "face_eyepatch", "hat_viking", "hat_sombrero", "feet_slippers", "back_wings_angel", "back_backpack", "pattern_stars", "neck_bell", "toy_ukulele"), PetMood.SLEEPING)
    @Test fun moodsSick() = sheet("pet_audit_sick", mix("glasses_cateye", "glasses_flower", "face_plaster", "face_clownnose", "hat_leaf", "hat_unicorn", "feet_rainboots", "back_jetpack", "pattern_flames", "neck_lei", "neck_lifebuoy", "toy_snowman"), PetMood.SICK)
    @Test fun moodsHungry() = sheet("pet_audit_hungry", mix("glasses_moon", "face_fangs", "face_mustache", "hat_antlers", "hat_graduation", "hat_chef", "feet_skates", "back_wings_bat", "back_shell", "pattern_waves", "neck_bib", "toy_cupcake"), PetMood.HUNGRY)
    @Test fun stages() = sheet("pet_audit_stage_seed", mix("glasses_round", "hat_crown", "hat_headband", "neck_scarf", "toy_balloon", "toy_trophy"), PetMood.CONTENT, Stage.SEED)
    @Test fun stagesBloom() = sheet("pet_audit_stage_bloom", mix("glasses_round", "hat_crown", "hat_headband", "neck_scarf", "toy_balloon", "toy_trophy"), PetMood.CONTENT, Stage.BLOOM)
}
