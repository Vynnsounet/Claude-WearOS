package com.medipote.app.domain

import com.medipote.app.data.UsageStats
import java.time.LocalDate

/**
 * What the person opens, counted on the phone and nowhere else: it lets the app order the cards of a page
 * by use and offer to put away the ones never opened. Nothing is ever hidden without the person saying yes.
 */
object CardUse {
    /** Days of use before the app dares to reorder or suggest anything. */
    const val MIN_DAYS = 14

    /** Never suggested for tidying: the heart of the app. */
    private val ESSENTIAL = setOf("soins:treatments", "soins:agenda", "suivi:journal", "moi:stats")

    const val MAX_PINS = 4

    /** One more open of [key], on [today]. */
    fun record(usage: UsageStats, key: String, today: LocalDate): UsageStats = usage.copy(
        opens = usage.opens + (key to (usage.opens[key] ?: 0) + 1),
        days = if (usage.lastDay == today) usage.days else usage.days + 1,
        lastDay = today,
    )

    val UsageStats.ready: Boolean get() = days >= MIN_DAYS

    /** [keys] from the most to the least opened; equal or unknown ones keep their place. Nothing changes before [MIN_DAYS]. */
    fun ordered(usage: UsageStats, keys: List<String>, enabled: Boolean = true): List<String> {
        if (!enabled || !usage.ready) return keys
        return keys.withIndex().sortedWith(compareByDescending<IndexedValue<String>> { usage.opens[it.value] ?: 0 }.thenBy { it.index }).map { it.value }
    }

    /** The card to offer to put away: shown, never opened, not an essential and not already kept. One at a time. */
    fun suggestion(usage: UsageStats, shown: List<String>): String? {
        if (!usage.ready) return null
        return shown.firstOrNull { it !in ESSENTIAL && it !in usage.kept && (usage.opens[it] ?: 0) == 0 }
    }

    fun keep(usage: UsageStats, key: String): UsageStats = usage.copy(kept = usage.kept + key)

    /** Pins: added at the end, no duplicate, [MAX_PINS] at the most (a fifth is refused). */
    fun pin(pins: List<String>, key: String): List<String> =
        if (key in pins || pins.size >= MAX_PINS) pins else pins + key

    fun unpin(pins: List<String>, key: String): List<String> = pins - key
}
