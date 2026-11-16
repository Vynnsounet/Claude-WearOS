package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import com.medipote.app.data.MeasureType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

/** One block of the page "Préparer mon rendez-vous": a title and short lines. */
data class PrepSection(val title: String, val lines: List<String>)

data class AppointmentPrep(val title: String, val days: Int, val questions: List<String>, val sections: List<PrepSection>) {
    /** The page as plain text, to copy or share. */
    fun asText(): String = buildString {
        appendLine(title)
        appendLine("(sur les $days derniers jours)")
        if (questions.isNotEmpty()) {
            appendLine()
            appendLine("Mes questions :")
            questions.forEach { appendLine("- $it") }
        }
        sections.forEach { s ->
            appendLine()
            appendLine("${s.title} :")
            s.lines.forEach { appendLine("- $it") }
        }
    }.trim()
}

/**
 * What to bring to an appointment: the person's own questions, then a short summary of the last weeks
 * worked out on the phone (doses, sleep, symptoms, cycle, measures). Figures and observations only.
 */
object AppointmentPrepper {
    const val DAYS = 30
    private fun f(x: Double) = "%.1f".format(Locale.FRANCE, x)

    fun build(state: AppState, appointment: Appointment, now: LocalDateTime): AppointmentPrep {
        val today = now.toLocalDate()
        val sections = buildList {
            doses(state, now)?.let(::add)
            sleep(state, today)?.let(::add)
            symptoms(state, today)?.let(::add)
            mood(state, today)?.let(::add)
            cycle(state, today)?.let(::add)
            asNeeded(state, now)?.let(::add)
            measures(state, today)?.let(::add)
        }
        val head = buildString {
            append(appointment.title)
            if (appointment.doctor.isNotBlank()) append(" · ${appointment.doctor}")
        }
        return AppointmentPrep(head, DAYS, appointment.questions.filter { it.isNotBlank() }, sections)
    }

    private fun doses(state: AppState, now: LocalDateTime): PrepSection? {
        if (state.treatments.none { !it.asNeeded }) return null
        val r = Reports.adherence(state, now, DAYS)
        if (r.rate.decided == 0) return null
        val worst = r.perTreatment.filter { it.rate.decided >= 3 }.minByOrNull { it.rate.value ?: 1f }
        return PrepSection(
            "Mes prises",
            listOfNotNull(
                "Prises faites : ${((r.rate.value ?: 0f) * 100).roundToInt()} % (${r.rate.taken} sur ${r.rate.decided}), ${r.missed} oubliée${if (r.missed > 1) "s" else ""}.",
                worst?.takeIf { (it.rate.value ?: 1f) < 0.85f }?.let { "Le plus oublié : ${it.name} (${((it.rate.value ?: 0f) * 100).roundToInt()} %)." },
            ),
        )
    }

    private fun sleep(state: AppState, today: LocalDate): PrepSection? {
        val r = Sleep.report(state, today, DAYS)
        if (r.count == 0) return null
        return PrepSection(
            "Mon sommeil",
            listOfNotNull(
                r.averageAsleep?.let { "${Sleep.format(it)} par nuit en moyenne (objectif ${Sleep.format(r.goalMinutes)}), ${r.count} nuits notées." },
                r.averageAwakenings?.let { "${f(it.toDouble())} réveil${if (it >= 2f) "s" else ""} par nuit." },
            ),
        )
    }

    private fun symptoms(state: AppState, today: LocalDate): PrepSection? {
        val ids = state.symptomLog.map { it.symptom }.distinct().filter { Wellbeing.period(state, it, today, DAYS).noted > 0 }
        if (ids.isEmpty()) return null
        val lines = ids.map { id ->
            val p = Wellbeing.period(state, id, today, DAYS)
            val trend = if (p.average != null && p.previousAverage != null) {
                val d = p.average - p.previousAverage
                when { d >= 0.5 -> ", plus fort qu'avant"; d <= -0.5 -> ", moins fort qu'avant"; else -> "" }
            } else ""
            "${Wellbeing.name(id)} : ${p.noted} jour${if (p.noted > 1) "s" else ""} noté${if (p.noted > 1) "s" else ""}, force moyenne ${f(p.average ?: 0.0)}/5, ${p.strongDays} jour${if (p.strongDays > 1) "s" else ""} fort${if (p.strongDays > 1) "s" else ""}$trend."
        } + Wellbeing.insights(state, today)
        return PrepSection("Mes symptômes", lines)
    }

    private fun mood(state: AppState, today: LocalDate): PrepSection? {
        val from = today.minusDays(DAYS - 1L)
        val entries = state.journal.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        val mental = entries.mapNotNull { it.mental }
        val physical = entries.mapNotNull { it.physical }
        if (mental.isEmpty() && physical.isEmpty()) return null
        return PrepSection(
            "Mon moral et ma forme",
            listOfNotNull(
                mental.takeIf { it.isNotEmpty() }?.let { "Moral moyen : ${f(it.average())}/5." },
                physical.takeIf { it.isNotEmpty() }?.let { "Forme physique moyenne : ${f(it.average())}/5." },
            ),
        )
    }

    private fun cycle(state: AppState, today: LocalDate): PrepSection? {
        val r = CycleStats.report(state, today)?.takeIf { it.lengths.isNotEmpty() } ?: return null
        return PrepSection(
            "Mon cycle",
            listOfNotNull(
                "Cycle moyen : ${r.averageLength!!.roundToInt()} jours sur ${r.lengths.size} cycles" + (r.spread?.let { " (écart de $it jour${if (it > 1) "s" else ""})" } ?: "") + ".",
                r.averagePeriodDays?.let { "Règles : ${f(it)} jours en moyenne." },
            ) + r.insights.filter { it.tone != Tone.GOOD }.map { it.text },
        )
    }

    private fun asNeeded(state: AppState, now: LocalDateTime): PrepSection? {
        val lines = state.treatments.filter { it.asNeeded }.mapNotNull { t ->
            val r = Reports.prn(state, t.id, now, DAYS)
            if (r.count == 0) null else "${t.displayName} : ${r.count} prise${if (r.count > 1) "s" else ""} (${f(r.perWeek.toDouble())} par semaine)."
        }
        return lines.takeIf { it.isNotEmpty() }?.let { PrepSection("Mes médicaments « si besoin »", it) }
    }

    private fun measures(state: AppState, today: LocalDate): PrepSection? {
        val lines = MeasureType.entries.mapNotNull { type ->
            val r = Measures.report(state, type, today, DAYS)
            val avg = r.average ?: return@mapNotNull null
            val shown = if (type == MeasureType.BLOOD_PRESSURE) "${avg.roundToInt()}/${r.average2?.roundToInt() ?: "?"}" else type.format(avg)
            "${type.label} : moyenne $shown ${type.unit} (${r.count} mesure${if (r.count > 1) "s" else ""})."
        }
        return lines.takeIf { it.isNotEmpty() }?.let { PrepSection("Mes mesures", it) }
    }
}
