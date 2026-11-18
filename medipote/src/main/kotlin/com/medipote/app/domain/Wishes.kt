package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.Season
import java.time.LocalDate

/**
 * The companion's wish of the week: one object it would love, with a progress bar in the shop. It is
 * picked once a week among what is not owned yet, leaning on the season (a bonnet in December, a mug
 * of tea in autumn). Never a duty: the wish can wait, and a new one comes next week.
 */
object Wishes {
    /** The seasons an item suits best; everything else suits any season. */
    private val SEASONS: Map<String, Set<Season>> = mapOf(
        "hat_santa" to setOf(Season.WINTER), "neck_plaid" to setOf(Season.AUTUMN, Season.WINTER),
        "toy_tea" to setOf(Season.AUTUMN, Season.WINTER), "toy_lantern" to setOf(Season.AUTUMN),
        "neck_scarf" to setOf(Season.AUTUMN, Season.WINTER), "hat_beanie" to setOf(Season.AUTUMN, Season.WINTER),
        "bg_autumn" to setOf(Season.AUTUMN), "bg_snow" to setOf(Season.WINTER),
        "hat_flower" to setOf(Season.SPRING), "toy_butterfly" to setOf(Season.SPRING, Season.SUMMER),
        "hat_straw" to setOf(Season.SUMMER), "glasses_sun" to setOf(Season.SUMMER), "bg_beach" to setOf(Season.SUMMER),
        "bg_meadow" to setOf(Season.SPRING),
    )

    fun fits(item: ShopItem, season: Season): Boolean = SEASONS[item.id]?.let { season in it } ?: false

    /** The item the companion wishes for in the week of [today], or null when it already has everything. */
    fun pick(state: AppState, today: LocalDate): ShopItem? {
        val candidates = Shop.items.filter { it.badge == null && it.price > 0 && it.id !in state.game.owned }.sortedBy { it.id }
        if (candidates.isEmpty()) return null
        val season = Seasons.of(today, state.settings.seasons.southern)
        val pool = candidates.filter { fits(it, season) }.ifEmpty { candidates }
        val week = (Challenges.weekStart(today).toEpochDay() / 7).toInt()
        return pool[Math.floorMod(week, pool.size)]
    }

    /** Chooses this week's wish if it has not been chosen yet; the same all week, whatever is bought. */
    fun refresh(state: AppState, today: LocalDate): AppState {
        val monday = Challenges.weekStart(today)
        if (state.game.wishWeek == monday) return state
        return state.copy(game = state.game.copy(wishId = pick(state, today)?.id, wishWeek = monday))
    }

    fun current(game: com.medipote.app.data.GameState): ShopItem? = game.wishId?.let(Shop::item)

    /** How close the points are to the price, 0 to 1. */
    fun progress(game: com.medipote.app.data.GameState, item: ShopItem): Float =
        if (item.price <= 0) 1f else (game.points.toFloat() / item.price).coerceIn(0f, 1f)
}
