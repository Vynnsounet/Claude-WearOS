package com.medipote.app.domain

import java.time.LocalDateTime

/** The companion's sky follows the clock: dawn 6–8 h, day 8–16 h, sunset 16–19 h, night 19–6 h. */
enum class TimeOfDay(val label: String) {
    DAWN("Aube"), DAY("Jour"), DUSK("Coucher de soleil"), NIGHT("Nuit");

    companion object {
        fun of(hour: Int): TimeOfDay = when (hour) {
            in 6 until 8 -> DAWN
            in 8 until 16 -> DAY
            in 16 until 19 -> DUSK
            else -> NIGHT
        }

        fun of(now: LocalDateTime): TimeOfDay = of(now.hour)
    }
}
