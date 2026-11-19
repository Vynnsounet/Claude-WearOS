package com.medipote.app.domain

import com.medipote.app.data.HomeSection
import java.time.LocalDateTime

/** The part of the day, for the home screen that shows what suits the hour. */
enum class Moment(val label: String) {
    MORNING("ce matin"), DAY("en journée"), EVENING("ce soir");

    companion object {
        fun of(hour: Int): Moment = when (hour) {
            in 5 until 11 -> MORNING
            in 11 until 18 -> DAY
            else -> EVENING
        }

        fun of(now: LocalDateTime): Moment = of(now.hour)
    }
}

/**
 * "Accueil du moment": what the home screen puts forward at each hour. The rest is not hidden for good,
 * it waits behind « Voir plus ». Nothing here decides *whether* a block has something to say: each block
 * still decides that itself.
 */
object HomeMoment {
    /** Always in front: the day's doses, the companion, and what the person pinned. */
    private val CORE = setOf(
        HomeSection.STATUS, HomeSection.PHRASE, HomeSection.COMPANION, HomeSection.NEXT_DOSE, HomeSection.DOSES,
        HomeSection.SOFT_DAY, HomeSection.PERIOD, HomeSection.AS_NEEDED, HomeSection.SHORTCUTS, HomeSection.APPOINTMENT,
    )

    private val BY_MOMENT = mapOf(
        Moment.MORNING to setOf(HomeSection.RITUAL, HomeSection.PROMPTS, HomeSection.MOOD, HomeSection.MEASURES),
        Moment.DAY to setOf(HomeSection.WATER, HomeSection.MEASURES, HomeSection.MOOD),
        Moment.EVENING to setOf(HomeSection.RITUAL, HomeSection.PROMPTS, HomeSection.MOOD),
    )

    fun inFront(section: HomeSection, now: LocalDateTime): Boolean =
        section in CORE || section in BY_MOMENT.getValue(Moment.of(now))

    /** The sections to show now, and those kept behind « Voir plus ». */
    fun split(sections: List<HomeSection>, now: LocalDateTime): Pair<List<HomeSection>, List<HomeSection>> =
        sections.partition { inFront(it, now) }
}
