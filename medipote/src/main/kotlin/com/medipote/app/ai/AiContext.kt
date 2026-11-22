package com.medipote.app.ai

import com.medipote.app.data.AiShare
import com.medipote.app.data.AppState
import com.medipote.app.data.MeasureType
import com.medipote.app.domain.Cycle
import com.medipote.app.domain.CycleStats
import com.medipote.app.domain.Measures
import com.medipote.app.domain.Sleep
import com.medipote.app.domain.Understand
import com.medipote.app.domain.Wellbeing
import com.medipote.app.domain.WellbeingLibrary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What the assistant is asked about. */
sealed interface AiTopic {
    data class Symptom(val id: String) : AiTopic
    data object Checkup : AiTopic
    data object Cycle : AiTopic
    data object Sleep : AiTopic

    /** A short, stable text for a route: "symptom:fatigue", "checkup"… */
    val key: String
        get() = when (this) {
            is Symptom -> "symptom:$id"
            Checkup -> "checkup"
            Cycle -> "cycle"
            Sleep -> "sleep"
        }

    companion object {
        fun parse(key: String?): AiTopic = when {
            key == null -> Checkup
            key.startsWith("symptom:") -> Symptom(key.removePrefix("symptom:"))
            key == "cycle" -> Cycle
            key == "sleep" -> Sleep
            else -> Checkup
        }
    }
}

/**
 * The text sent to the AI: built here, on the phone, from the categories the person allowed. This exact text is
 * what the person can read before and after: there is nothing else in the request. It never holds the name, the
 * contacts, the birth date or the free text of notes and journal.
 */
object AiContext {
    private val DAY = DateTimeFormatter.ofPattern("d/MM")
    private fun f1(x: Double) = "%.1f".format(java.util.Locale.FRANCE, x)

    fun title(topic: AiTopic): String = when (topic) {
        is AiTopic.Symptom -> "${Wellbeing.emoji(topic.id)}  ${Wellbeing.name(topic.id)}"
        AiTopic.Checkup -> "🔎  Mon bilan"
        AiTopic.Cycle -> "🩸  Mon cycle"
        AiTopic.Sleep -> "🌙  Mon sommeil"
    }

    private fun subject(topic: AiTopic) = when (topic) {
        is AiTopic.Symptom -> "le symptôme « ${Wellbeing.name(topic.id)} » : ce qui peut l'expliquer, comment le soulager sans médicament, et s'il faut consulter"
        AiTopic.Checkup -> "un bilan général : ce que montrent ces notes, ce qui mérite un avis médical, et des conseils sans médicament"
        AiTopic.Cycle -> "des conseils pour le cycle menstruel en ce moment, sans médicament, selon ces notes"
        AiTopic.Sleep -> "des conseils pour le sommeil selon ces nuits, sans médicament"
    }

    fun build(state: AppState, topic: AiTopic, shares: Set<AiShare>, today: LocalDate): String = buildString {
        appendLine("Date : ${today}")
        appendLine("Sujet demandé : ${subject(topic)}")

        val profile = state.profile
        if (AiShare.PROFILE in shares && profile != null) {
            val parts = listOfNotNull(profile.age?.let { "$it ans" }, profile.sex?.label?.lowercase())
            if (parts.isNotEmpty()) appendLine("Personne : ${parts.joinToString(", ")}")
        }
        if (AiShare.CONDITIONS in shares) {
            val c = state.settings.wellbeing.conditions
            if (c.isNotEmpty()) appendLine("Ce que la personne vit : ${c.joinToString(", ") { it.name }}")
        }

        if (AiShare.TREATMENTS in shares) {
            val t = state.treatments.filter { it.endDate == null || !it.endDate.isBefore(today) }
            if (t.isNotEmpty()) {
                appendLine("Traitements :")
                t.forEach { x ->
                    val times = if (x.asNeeded) "si besoin" else x.doses.joinToString(", ") { "%dh%02d".format(it.minuteOfDay / 60, it.minuteOfDay % 60) }
                    appendLine("- ${x.displayName} ($times)")
                }
                val missed = Understand.missedDoses(state, today, 7)
                if (missed > 0) appendLine("Prises non notées sur les 7 derniers jours : $missed")
            }
        }

        if (AiShare.SYMPTOMS in shares) {
            val from = today.minusDays(13)
            val notes = state.symptomLog.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
                .let { l -> if (topic is AiTopic.Symptom) l else l }
            if (notes.isNotEmpty()) {
                appendLine("Symptômes notés (14 derniers jours, intensité sur 5) :")
                notes.groupBy { it.symptom }.forEach { (id, list) ->
                    appendLine("- ${Wellbeing.name(id)} : " + list.sortedBy { it.date }.joinToString(", ") { "${it.date.format(DAY)} ${it.intensity}/5" })
                }
            }
            val mood = (13 downTo 0).mapNotNull { back ->
                val d = today.minusDays(back.toLong())
                state.entryFor(d).mental?.let { "${d.format(DAY)} $it/5" }
            }
            if (mood.isNotEmpty()) appendLine("Humeur (1 très basse à 5 très bonne) : ${mood.joinToString(", ")}")
        }

        if (AiShare.SLEEP in shares) {
            val nights = state.sleep.filter { !it.date.isBefore(today.minusDays(6)) && !it.date.isAfter(today) }.sortedBy { it.date }
            if (nights.isNotEmpty()) {
                appendLine("Sommeil (7 dernières nuits, objectif ${Sleep.format(state.settings.sleep.goalMinutes)}) :")
                nights.forEach { n ->
                    appendLine(
                        "- ${n.date.format(DAY)} : " + if (n.noSleep) "nuit blanche"
                        else "${Sleep.format(n.asleepMinutes)} dormi, endormissement ${Sleep.format(n.latencyMinutes)}, ${n.awakenings.size} réveil${if (n.awakenings.size > 1) "s" else ""}",
                    )
                }
            }
        }

        if (AiShare.CYCLE in shares && state.profile?.menstruating == true) {
            val info = state.profile.cycle
            val status = info?.let { Cycle.status(it, today) }
            if (info != null && status != null) {
                val phase = CycleStats.phaseOn(info, today)
                appendLine(
                    "Cycle : jour ${status.dayOfCycle} sur environ ${status.cycleLength}" +
                        (phase?.let { ", phase « ${it.label.lowercase()} »" } ?: "") +
                        ", prochaines règles dans ${status.daysUntilNextPeriod} jour${if (status.daysUntilNextPeriod > 1) "s" else ""}" +
                        if (status.lateDays > 0) " (en retard de ${status.lateDays} jours)" else "",
                )
                val lengths = info.periodStarts.sorted().zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b) }.takeLast(4)
                if (lengths.isNotEmpty()) appendLine("Durée des derniers cycles : ${lengths.joinToString(", ") { "$it j" }}")
            }
        }

        if (AiShare.MEASURES in shares) {
            val lines = MeasureType.entries.mapNotNull { type ->
                Measures.latest(state, type)?.takeIf { !it.at.toLocalDate().isBefore(today.minusDays(14)) }?.let { m ->
                    val v = if (type == MeasureType.BLOOD_PRESSURE) "${m.value.toInt()}/${m.value2?.toInt() ?: "?"}" else type.format(m.value)
                    "- ${type.label} : $v ${type.unit} (${m.at.toLocalDate().format(DAY)})"
                }
            }
            if (lines.isNotEmpty()) { appendLine("Mesures récentes :"); lines.forEach(::appendLine) }
        }

        // What the app's own rules found: the AI can build on it but must not play it down.
        val rules = rulesFor(redact(state, shares), topic, today)
        if (rules.isNotEmpty()) {
            appendLine("Constats de l'application (règles écrites à la main, à ne pas minimiser) :")
            rules.forEach { appendLine("- $it") }
        }
    }.trimEnd()

    /** The state as the AI is allowed to see it: what the person did not share is gone, names and free text always are. */
    fun redact(state: AppState, shares: Set<AiShare>): AppState = state.copy(
        profile = state.profile?.let { p ->
            p.copy(
                name = "", age = p.age.takeIf { AiShare.PROFILE in shares }, sex = p.sex.takeIf { AiShare.PROFILE in shares },
                weightKg = null, heightCm = null, medicalHistory = emptyList(), allergies = emptyList(),
                menstruating = p.menstruating && AiShare.CYCLE in shares, cycle = p.cycle.takeIf { AiShare.CYCLE in shares },
            )
        },
        treatments = if (AiShare.TREATMENTS in shares) state.treatments else emptyList(),
        doseLogs = if (AiShare.TREATMENTS in shares) state.doseLogs else emptyList(),
        sleep = if (AiShare.SLEEP in shares) state.sleep.map { it.copy(notes = "", tags = emptyList()) } else emptyList(),
        symptomLog = if (AiShare.SYMPTOMS in shares) state.symptomLog.map { it.copy(note = "") } else emptyList(),
        journal = if (AiShare.SYMPTOMS in shares) state.journal.map { com.medipote.app.data.DayEntry(it.date, physical = it.physical, mental = it.mental) } else emptyList(),
        measurements = if (AiShare.MEASURES in shares) state.measurements.map { it.copy(notes = "") } else emptyList(),
        settings = state.settings.copy(
            wellbeing = state.settings.wellbeing.copy(
                conditions = if (AiShare.CONDITIONS in shares) state.settings.wellbeing.conditions else emptyList(),
                customMissions = emptyList(),
            ),
        ),
        // Everything else the app holds (documents, contacts, appointments, ideas…) is never part of a request.
        documents = emptyList(), appointments = emptyList(), support = com.medipote.app.data.SupportBox(), ideas = emptyList(),
    )

    /** What the app's own rules found for [topic]: shown first on the AI screen, and the lowest level the AI may give. */
    fun concerns(state: AppState, topic: AiTopic, today: LocalDate): List<com.medipote.app.domain.Concern> =
        if (topic is AiTopic.Symptom) Understand.symptomConcerns(state, topic.id, today) + Understand.measureConcerns(state, today)
        else Understand.scan(state, today).filter { c ->
            when (topic) {
                AiTopic.Cycle -> c.key.startsWith("cycle")
                AiTopic.Sleep -> c.key.startsWith("sleep")
                else -> true
            }
        }

    /** The app's own findings relevant to [topic], as short lines. Only about categories the person shares. */
    fun rulesFor(state: AppState, topic: AiTopic, today: LocalDate): List<String> = buildList {
        if (topic is AiTopic.Symptom) Understand.factors(state, topic.id, today).forEach { add("${it.title} : ${it.evidence}") }
        concerns(state, topic, today).forEach { add("${it.urgency.label} — ${it.title} : ${it.reasons.joinToString(" ")}") }
    }
}
