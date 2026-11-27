package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.Vaccination
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class VaccineStatus { OK, SOON, DUE, CHECK }

/** One vaccine of the calendar for this person: where they are with it, in a sentence. */
data class VaccineCard(
    val id: String,
    val emoji: String,
    val name: String,
    val why: String,
    val status: VaccineStatus,
    val headline: String,
    val last: LocalDate?,
    val doses: Int,
)

/**
 * A simplified calendar of the French vaccine recommendations for adults (and the teenage catch-up of the HPV): what is advised at
 * this age, what the person noted, what is due. It is a reminder, not a medical act: the vaccination record and the doctor or
 * pharmacist decide. The rules are written down here for a professional to check.
 */
object Vaccines {
    const val DISCLAIMER = "Calendrier simplifié d'après les recommandations françaises (calendrier des vaccinations, Haute Autorité de santé), pour t'aider à y penser. Ton carnet de vaccination, ton médecin ou ton pharmacien ont le dernier mot."
    private val DTP_AGES = listOf(25, 45, 65, 75, 85, 95, 105)

    /** The year of birth to count the age from: the one given, else deduced from the age of the profile. */
    fun birthYear(state: AppState, today: LocalDate): Int? = state.profile?.birthYear ?: state.profile?.age?.let { today.year - it }

    fun age(state: AppState, today: LocalDate): Int? = birthYear(state, today)?.let { today.year - it }

    fun doses(state: AppState, id: String): List<Vaccination> = state.vaccinations.filter { it.vaccine == id }.sortedBy { it.date }

    /** The start of the season a seasonal vaccine counts in: September of this year, or of last year until August. */
    private fun seasonStart(today: LocalDate): LocalDate = LocalDate.of(if (today.monthValue >= 9) today.year else today.year - 1, 9, 1)

    private fun seasonal(state: AppState, today: LocalDate, id: String, emoji: String, name: String, why: String): VaccineCard {
        val all = doses(state, id)
        val done = all.lastOrNull { !it.date.isBefore(seasonStart(today)) }
        val m = today.monthValue
        val (status, headline) = when {
            done != null -> VaccineStatus.OK to "Fait cet automne (${done.date.dayOfMonth}/${done.date.monthValue}) : prochain à l'automne prochain."
            m in 10..12 || m == 1 -> VaccineStatus.DUE to "C'est la saison : la campagne a lieu d'octobre à janvier."
            m == 9 -> VaccineStatus.SOON to "La campagne commence en octobre."
            else -> VaccineStatus.OK to "Rien à faire avant l'automne."
        }
        return VaccineCard(id, emoji, name, why, status, headline, all.lastOrNull()?.date, all.size)
    }

    /** The cards that make sense for this person's age, in the order of urgency. Empty when the age is not known. */
    fun cards(state: AppState, today: LocalDate): List<VaccineCard> {
        val birth = birthYear(state, today) ?: return emptyList()
        val age = today.year - birth
        val risk = state.profile?.vaccineRisk == true
        val out = mutableListOf<VaccineCard>()

        if (age >= 18) {
            val all = doses(state, "dtp")
            val last = all.lastOrNull()?.date
            val card = if (last == null) {
                if (age < 25) VaccineCard("dtp", "💉", "Diphtérie, tétanos, polio", "Rappel à 25, 45 et 65 ans, puis tous les 10 ans ; la coqueluche est ajoutée à 25 ans.", VaccineStatus.SOON, "Prochain rappel à 25 ans (en ${birth + 25}).", null, 0)
                else VaccineCard("dtp", "💉", "Diphtérie, tétanos, polio", "Rappel à 25, 45 et 65 ans, puis tous les 10 ans ; la coqueluche est ajoutée à 25 ans.", VaccineStatus.CHECK, "Date du dernier rappel inconnue : regarde ton carnet de vaccination ou demande à ton médecin.", null, 0)
            } else {
                val nextAge = DTP_AGES.first { birth + it > last.year }
                val dueYear = birth + nextAge
                val (status, headline) = when {
                    dueYear < today.year -> VaccineStatus.DUE to "Le rappel de $nextAge ans (en $dueYear) est à faire."
                    dueYear == today.year -> VaccineStatus.DUE to "Le rappel de $nextAge ans est à faire cette année."
                    dueYear == today.year + 1 -> VaccineStatus.SOON to "Prochain rappel à $nextAge ans, l'an prochain ($dueYear)."
                    else -> VaccineStatus.OK to "À jour : prochain rappel à $nextAge ans (en $dueYear)."
                }
                VaccineCard("dtp", "💉", "Diphtérie, tétanos, polio", "Rappel à 25, 45 et 65 ans, puis tous les 10 ans ; la coqueluche est ajoutée à 25 ans.", status, headline, last, all.size)
            }
            out += card
        }
        if (age >= 65 || (risk && age >= 18)) {
            out += seasonal(state, today, "flu", "🤧", "Grippe", "Chaque automne à partir de 65 ans et pour les personnes à risque.")
            out += seasonal(state, today, "covid", "🦠", "Covid-19", "Rappel d'automne, en même temps que la grippe, à partir de 65 ans et pour les personnes à risque.")
            val p = doses(state, "pneumo")
            out += VaccineCard("pneumo", "🫁", "Pneumocoque", "Une dose à partir de 65 ans ou si tu es à risque : à voir avec ton médecin.",
                if (p.isNotEmpty()) VaccineStatus.OK else VaccineStatus.DUE,
                if (p.isNotEmpty()) "Fait le ${p.last().date.dayOfMonth}/${p.last().date.monthValue}/${p.last().date.year}." else "Pas noté : à discuter avec ton médecin.", p.lastOrNull()?.date, p.size)
        }
        if (age >= 65 || (risk && age >= 18)) {
            val z = doses(state, "zona")
            val (status, headline) = when {
                z.size >= 2 -> VaccineStatus.OK to "Les 2 doses sont faites."
                z.size == 1 && z.first().date.plusMonths(2).isAfter(today) -> VaccineStatus.SOON to "2e dose à faire 2 à 6 mois après la 1re (à partir du ${z.first().date.plusMonths(2).let { "${it.dayOfMonth}/${it.monthValue}" }})."
                z.size == 1 -> VaccineStatus.DUE to "La 2e dose est à faire (2 à 6 mois après la 1re)."
                else -> VaccineStatus.DUE to "Pas noté : 2 doses, à 2 à 6 mois d'intervalle."
            }
            out += VaccineCard("zona", "🔥", "Zona", "Deux doses à partir de 65 ans, ou si ton système immunitaire est fragile.", status, headline, z.lastOrNull()?.date, z.size)
        }
        if (birth >= 1980 && age in 18..60) {
            val r = doses(state, "ror")
            out += VaccineCard("ror", "🧒", "Rougeole, oreillons, rubéole (ROR)", "Deux doses pour les personnes nées depuis 1980 : souvent faites enfant.",
                if (r.size >= 2) VaccineStatus.OK else VaccineStatus.CHECK,
                if (r.size >= 2) "Les 2 doses sont notées." else "Regarde ton carnet : 2 doses sont recommandées, ${r.size} notée${if (r.size > 1) "s" else ""} ici.", r.lastOrNull()?.date, r.size)
        }
        if (age in 11..19) {
            val h = doses(state, "hpv")
            out += VaccineCard("hpv", "🛡️", "Papillomavirus (HPV)", "Recommandé de 11 à 14 ans, rattrapage jusqu'à 19 ans : 2 ou 3 doses selon l'âge.",
                when { h.size >= 2 -> VaccineStatus.OK; h.size == 1 -> VaccineStatus.CHECK; else -> VaccineStatus.DUE },
                when { h.size >= 2 -> "${h.size} doses notées : vérifie avec ton médecin si le schéma est complet."; h.size == 1 -> "1 dose notée : la suite est à voir avec ton médecin."; else -> "Pas noté : à voir avec ton médecin." }, h.lastOrNull()?.date, h.size)
        }
        return out.sortedBy { when (it.status) { VaccineStatus.DUE -> 0; VaccineStatus.CHECK -> 1; VaccineStatus.SOON -> 2; VaccineStatus.OK -> 3 } }
    }

    /** What is due to be done (not just to check): for the home page. Empty when the person turned the reminders off. */
    fun pending(state: AppState, today: LocalDate): List<VaccineCard> =
        if (!state.settings.vaccineReminders) emptyList() else cards(state, today).filter { it.status == VaccineStatus.DUE }

    /** The vaccines of the calendar by id, for the names. */
    fun name(id: String): String = when (id) {
        "dtp" -> "Diphtérie, tétanos, polio"; "flu" -> "Grippe"; "covid" -> "Covid-19"; "pneumo" -> "Pneumocoque"; "zona" -> "Zona"
        "ror" -> "ROR"; "hpv" -> "Papillomavirus (HPV)"; else -> id
    }

    val KNOWN = listOf("dtp", "flu", "covid", "pneumo", "zona", "ror", "hpv")

    /** The autumn reminder (15 October, 10:00) of the seasonal vaccines, while this season's is not done. */
    fun seasonReminders(state: AppState, now: LocalDateTime): List<Pair<LocalDateTime, String>> {
        if (!state.settings.vaccineReminders) return emptyList()
        val today = now.toLocalDate()
        val out = mutableListOf<Pair<LocalDateTime, String>>()
        for (year in listOf(today.year, today.year + 1)) {
            val at = LocalDateTime.of(year, 10, 15, 10, 0)
            if (!at.isAfter(now)) continue
            // The cards as they will be that day: this season's vaccine is not done yet, whatever happens before.
            val onThatDay = cards(state, at.toLocalDate()).filter { (it.id == "flu" || it.id == "covid") && it.status == VaccineStatus.DUE }
            onThatDay.forEach { out += at to it.id }
            // Only the next autumn is planned; the following one is planned when it comes.
            if (out.isNotEmpty()) break
        }
        return out
    }

    fun add(state: AppState, vaccine: String, date: LocalDate, note: String, id: String): AppState =
        state.copy(vaccinations = (state.vaccinations + Vaccination(id, vaccine.trim().ifBlank { return state }, date, note.trim().take(200))).sortedByDescending { it.date })

    fun remove(state: AppState, id: String): AppState = state.copy(vaccinations = state.vaccinations.filterNot { it.id == id })

    fun setProfile(state: AppState, birthYear: Int?, risk: Boolean): AppState {
        val p = state.profile ?: return state
        return state.copy(profile = p.copy(birthYear = birthYear?.takeIf { it in 1900..LocalDate.now().year }, vaccineRisk = risk))
    }

    val SEASON_TIME: LocalTime = LocalTime.of(10, 0)
}
