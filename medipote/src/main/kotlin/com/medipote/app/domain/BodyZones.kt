package com.medipote.app.domain

import com.medipote.app.data.AppState
import java.time.LocalDate

enum class BodyView { FRONT, BACK }

/** A place on the body one can touch to say where it hurts. Right and left are the person's own. */
enum class BodyZone(val id: String, val label: String, val views: Set<BodyView>) {
    HEAD("head", "Tête", setOf(BodyView.FRONT, BodyView.BACK)),
    NECK("neck", "Cou", setOf(BodyView.FRONT, BodyView.BACK)),
    SHOULDER_R("shoulder_r", "Épaule droite", setOf(BodyView.FRONT, BodyView.BACK)),
    SHOULDER_L("shoulder_l", "Épaule gauche", setOf(BodyView.FRONT, BodyView.BACK)),
    CHEST("chest", "Poitrine", setOf(BodyView.FRONT)),
    ABDOMEN("abdomen", "Ventre", setOf(BodyView.FRONT)),
    PELVIS("pelvis", "Bas-ventre", setOf(BodyView.FRONT)),
    ARM_R("arm_r", "Bras droit", setOf(BodyView.FRONT, BodyView.BACK)),
    ARM_L("arm_l", "Bras gauche", setOf(BodyView.FRONT, BodyView.BACK)),
    LEG_R("leg_r", "Jambe droite", setOf(BodyView.FRONT, BodyView.BACK)),
    LEG_L("leg_l", "Jambe gauche", setOf(BodyView.FRONT, BodyView.BACK)),
    UPPER_BACK("upper_back", "Haut du dos", setOf(BodyView.BACK)),
    LOWER_BACK("lower_back", "Bas du dos", setOf(BodyView.BACK)),
    GLUTES("glutes", "Fessiers", setOf(BodyView.BACK)),
}

object BodyZones {
    fun byId(id: String): BodyZone? = BodyZone.entries.firstOrNull { it.id == id }

    fun label(id: String) = byId(id)?.label ?: id

    fun labels(ids: List<String>): String = ids.mapNotNull { byId(it)?.label }.joinToString(", ")

    /** Where [symptom] was felt over the last [days] days: the zones, most often first, with the number of days. */
    fun counts(state: AppState, symptom: String, today: LocalDate, days: Int): List<Pair<BodyZone, Int>> =
        state.symptomLog
            .filter { it.symptom == symptom && !it.date.isBefore(today.minusDays(days - 1L)) && !it.date.isAfter(today) }
            .flatMap { it.zones }.mapNotNull(::byId).groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
}
