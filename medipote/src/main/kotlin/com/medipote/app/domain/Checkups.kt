package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.RegularCheckup
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Something to do from time to time. The person sets the rhythm; the suggestions are only ideas. */
object Checkups {
    data class Suggestion(val emoji: String, val title: String, val everyMonths: Int)

    val SUGGESTIONS = listOf(
        Suggestion("🩸", "Prise de sang", 12),
        Suggestion("🦷", "Dentiste", 12),
        Suggestion("🌸", "Contrôle gynécologique", 12),
        Suggestion("👁️", "Ophtalmologue", 24),
        Suggestion("🧴", "Dermatologue (grains de beauté)", 12),
        Suggestion("🩺", "Bilan avec mon médecin", 12),
    )

    enum class State { NEVER, OK, SOON, LATE }

    /** The day the next one is due; null when it has never been done (the person is asked to say when). */
    fun due(c: RegularCheckup): LocalDate? = c.lastDone?.plusMonths(c.everyMonths.toLong())

    fun state(c: RegularCheckup, today: LocalDate): State {
        val due = due(c) ?: return State.NEVER
        return when {
            due.isBefore(today) -> State.LATE
            !due.isAfter(today.plusDays(SOON_DAYS)) -> State.SOON
            else -> State.OK
        }
    }

    const val SOON_DAYS = 30L

    fun label(c: RegularCheckup, today: LocalDate): String {
        val due = due(c) ?: return "Pas encore fait : note quand tu l'as fait pour la dernière fois"
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            days < -60 -> "À faire depuis ${ChronoUnit.MONTHS.between(due, today)} mois"
            days < 0 -> "À faire depuis ${-days} jour${if (days < -1) "s" else ""}"
            days == 0L -> "À faire aujourd'hui"
            days <= 60 -> "Dans $days jour${if (days > 1) "s" else ""}"
            else -> "Dans ${ChronoUnit.MONTHS.between(today, due)} mois"
        }
    }

    /** The most pressing first: late, then soon, then the others by date; never done last. */
    fun sorted(list: List<RegularCheckup>, today: LocalDate): List<RegularCheckup> =
        list.sortedWith(compareBy({ state(it, today).let { s -> if (s == State.NEVER) 9 else s.ordinal.let { o -> 3 - o } } }, { due(it) ?: LocalDate.MAX }))

    /** What home says: only what is late or due within the month, gently. */
    fun pending(state: AppState, today: LocalDate): List<RegularCheckup> =
        state.checkups.filter { it.remind && state(it, today).let { s -> s == State.LATE || s == State.SOON } }.let { sorted(it, today) }

    fun done(list: List<RegularCheckup>, id: String, today: LocalDate): List<RegularCheckup> = list.map { if (it.id == id) it.copy(lastDone = today) else it }

    fun add(list: List<RegularCheckup>, title: String, everyMonths: Int, lastDone: LocalDate?, now: java.time.LocalDateTime): List<RegularCheckup> {
        val clean = title.trim()
        if (clean.isEmpty() || list.any { it.title.equals(clean, ignoreCase = true) }) return list
        return list + RegularCheckup("chk-${now.toString().replace(Regex("[^0-9]"), "")}-${list.size}", clean, everyMonths.coerceIn(1, 120), lastDone)
    }
}
