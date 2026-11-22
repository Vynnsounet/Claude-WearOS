package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.TrustedContact

/** What the emergency services would want to know first, in one readable page. Built only from what the person already wrote. */
data class EmergencyCard(
    val name: String,
    val ageSex: String,
    val bloodGroup: String,
    val allergies: List<String>,
    val conditions: List<String>,
    val treatments: List<String>,
    val contacts: List<TrustedContact>,
    val doctor: TrustedContact?,
) {
    val isEmpty: Boolean get() = allergies.isEmpty() && conditions.isEmpty() && treatments.isEmpty() && contacts.isEmpty() && bloodGroup.isBlank()
}

object Emergency {
    val BLOOD_GROUPS = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−")

    fun card(state: AppState, today: java.time.LocalDate): EmergencyCard {
        val p = state.profile
        val ageSex = listOfNotNull(p?.age?.let { "$it ans" }, p?.sex?.label?.lowercase()).joinToString(", ")
        val conditions = (p?.medicalHistory.orEmpty() + state.settings.wellbeing.conditions.map { it.name }).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val treatments = state.treatments.filter { !it.asNeeded && (it.endDate == null || !it.endDate.isBefore(today)) }.map { t ->
            t.displayName + " — " + t.doses.map { it.minuteOfDay }.sorted().joinToString(", ") { "%d h %02d".format(it / 60, it % 60) }
        } + state.treatments.filter { it.asNeeded }.map { "${it.displayName} (si besoin)" }
        val support = state.settings.support
        val doctor = support.professionalName.takeIf { it.isNotBlank() }?.let { TrustedContact(it, support.professionalPhone) }
        return EmergencyCard(
            name = p?.name.orEmpty(), ageSex = ageSex, bloodGroup = p?.bloodGroup.orEmpty(),
            allergies = p?.allergies.orEmpty().map { it.trim() }.filter { it.isNotEmpty() },
            conditions = conditions, treatments = treatments, contacts = support.contacts, doctor = doctor,
        )
    }

    fun shareText(card: EmergencyCard): String = buildString {
        appendLine("FICHE D'URGENCE" + if (card.name.isNotBlank()) " — ${card.name}" else "")
        if (card.ageSex.isNotBlank()) appendLine(card.ageSex)
        if (card.bloodGroup.isNotBlank()) appendLine("Groupe sanguin : ${card.bloodGroup}")
        if (card.allergies.isNotEmpty()) appendLine("Allergies : ${card.allergies.joinToString(", ")}")
        if (card.conditions.isNotEmpty()) appendLine("Antécédents : ${card.conditions.joinToString(", ")}")
        if (card.treatments.isNotEmpty()) { appendLine("Traitements :"); card.treatments.forEach { appendLine("- $it") } }
        card.contacts.forEach { appendLine("À prévenir : ${it.name} ${it.phone}") }
        card.doctor?.let { appendLine("Médecin : ${it.name} ${it.phone}".trimEnd()) }
    }.trimEnd()
}
