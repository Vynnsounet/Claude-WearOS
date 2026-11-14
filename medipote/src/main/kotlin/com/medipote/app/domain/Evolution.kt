package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.TakeStatus

/** The companion grows with regularity: each dose given on time makes it a bit bigger. */
enum class Stage(val label: String, val from: Int, val description: String) {
    SEED("Graine", 0, "tout petit, avec une pousse"),
    SPROUT("Pousse", 20, "un peu plus grand, sa pousse a deux feuilles"),
    BUD("Bouton", 70, "adulte, avec un bouton de fleur"),
    FLOWER("Fleur", 200, "sa fleur s'est ouverte"),
    BLOOM("Floraison", 500, "en pleine floraison, avec des étincelles");

    val next: Stage? get() = entries.getOrNull(ordinal + 1)
}

data class Growth(val stage: Stage, val points: Int) {
    /** Progress towards the next stage, 0..1 (1 at the last stage). */
    val progress: Float get() = stage.next?.let { (points - stage.from).toFloat() / (it.from - stage.from) } ?: 1f
    val toNext: Int? get() = stage.next?.let { it.from - points }
}

object Evolution {
    /** Growth points: the counter kept by the game, or the on-time doses still in the history if larger. */
    fun points(state: AppState): Int =
        maxOf(state.game.growth, state.doseLogs.count { it.status == TakeStatus.ON_TIME })

    fun stageFor(points: Int): Stage = Stage.entries.last { points >= it.from }

    fun growth(state: AppState): Growth = points(state).let { Growth(stageFor(it), it) }
}
