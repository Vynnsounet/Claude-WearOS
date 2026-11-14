package com.medipote.app.ui.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.medipote.app.domain.PetMood
import com.medipote.app.domain.PetReaction
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The companion you can play with:
 * - tap: it hops and says something useful (next dose, water, appointment…);
 * - several quick taps: it giggles; too many: it gets dizzy;
 * - stroke it sideways: it purrs and hearts appear;
 * - long press: a big hug;
 * - it sneezes when unwell and yawns when woken up;
 * - its eyes follow your finger.
 *
 * [talk] gives the line for a reaction (null = say nothing); [onCuddle] is called on petting and hugs.
 */
@Composable
fun InteractivePet(
    mood: PetMood,
    equipped: Map<String, String>,
    talk: (PetReaction) -> String?,
    modifier: Modifier = Modifier,
    petName: String = "",
    feedSignal: Int = 0,
    animate: Boolean = true,
    description: String = "",
    greeting: String? = null,
    onCuddle: () -> Unit = {},
    stage: com.medipote.app.domain.Stage = com.medipote.app.domain.Stage.BUD,
) {
    var reaction by remember { mutableStateOf(PetReaction.NONE) }
    var signal by remember { mutableIntStateOf(0) }
    var bubble by remember { mutableStateOf<String?>(null) }
    var bubbleId by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var greeted by rememberSaveable { mutableStateOf(false) }
    val look = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun say(text: String?) {
        if (text.isNullOrBlank()) return
        bubble = text
        bubbleId++
    }

    fun react(r: PetReaction, speak: Boolean = true) {
        reaction = r
        signal++
        if (speak) say(talk(r))
        if (r == PetReaction.PETTED || r == PetReaction.HUG) onCuddle()
    }

    fun lookAt(p: Offset, size: IntSize) {
        if (size.width == 0 || size.height == 0) return
        val target = Offset((p.x / size.width - 0.5f) * 2.4f, (p.y / size.height - 0.55f) * 2.4f)
        scope.launch { look.snapTo(Offset(target.x.coerceIn(-1f, 1f), target.y.coerceIn(-1f, 1f))) }
    }

    fun lookBack() {
        scope.launch {
            delay(600)
            look.animateTo(Offset.Zero, spring())
        }
    }

    fun tap() {
        val now = System.currentTimeMillis()
        taps = if (now - lastTap < 1200) taps + 1 else 1
        lastTap = now
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when {
            taps >= 7 -> {
                taps = 0
                react(PetReaction.DIZZY)
            }
            taps >= 3 -> react(PetReaction.GIGGLE, speak = taps == 3)
            mood == PetMood.SLEEPING -> react(PetReaction.YAWN)
            mood == PetMood.SICK || mood == PetMood.UNWELL -> react(PetReaction.SNEEZE)
            taps == 2 -> react(PetReaction.JUMP, speak = false)
            else -> react(PetReaction.JUMP)
        }
    }

    // Say hello once when the screen opens.
    LaunchedEffect(greeting) {
        if (greeting != null && !greeted && animate) {
            greeted = true
            delay(700)
            say(greeting)
        }
    }
    // Bubbles fade away on their own.
    LaunchedEffect(bubbleId) {
        if (bubble != null) {
            delay(3500)
            bubble = null
        }
    }

    Box(
        modifier
            .pointerInput(mood) {
                detectTapGestures(
                    onPress = { lookAt(it, size) },
                    onTap = { tap(); lookBack() },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        react(PetReaction.HUG)
                        lookBack()
                    },
                )
            }
            .pointerInput(Unit) {
                // Horizontal strokes pet it; vertical drags are left to the page scroll.
                var stroked = 0f
                detectHorizontalDragGestures(
                    onDragStart = { stroked = 0f; lookAt(it, size) },
                    onDragEnd = { lookBack() },
                    onDragCancel = { lookBack() },
                ) { change, dx ->
                    change.consume()
                    lookAt(change.position, size)
                    stroked += abs(dx)
                    if (stroked > size.width * 0.35f) {
                        stroked = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        react(PetReaction.PETTED, speak = reaction != PetReaction.PETTED || bubble == null)
                    }
                }
            }
            .semantics {
                onClick(label = "Toucher ${petName.ifBlank { "le compagnon" }}") { tap(); true }
                customActions = listOf(
                    CustomAccessibilityAction("Caresser") { react(PetReaction.PETTED); true },
                    CustomAccessibilityAction("Faire un câlin") { react(PetReaction.HUG); true },
                )
            },
    ) {
        PetView(
            mood = mood,
            equipped = equipped,
            feedSignal = feedSignal,
            animate = animate,
            description = description,
            reaction = reaction,
            reactionSignal = signal,
            look = look.value,
            stage = stage,
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = bubble != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            SpeechBubble(bubble.orEmpty())
        }
    }
}

@Composable
private fun SpeechBubble(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = Modifier.widthIn(max = 320.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = text },
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}
