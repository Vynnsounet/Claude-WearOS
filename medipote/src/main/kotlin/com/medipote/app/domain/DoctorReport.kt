package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.MeasureType
import com.medipote.app.data.Treatment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Parts of the report the user can include or leave out. */
enum class ReportPart(val label: String) {
    PROFILE("Profil et antécédents"),
    TREATMENTS("Traitements en cours"),
    ADHERENCE("Prises et observance"),
    PRN("Médicaments « si besoin »"),
    MEASURES("Mesures de santé"),
    SLEEP("Sommeil"),
    JOURNAL("Journal : forme et symptômes"),
    CYCLE("Cycle menstruel"),
}

sealed interface ReportBlock {
    data class Paragraph(val text: String, val muted: Boolean = false) : ReportBlock
    data class Facts(val items: List<Pair<String, String>>) : ReportBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>, val weights: List<Float>) : ReportBlock
    data class Chart(
        val title: String,
        val points: List<Pair<LocalDateTime, List<Double>>>,
        val format: (Double) -> String,
        val band: ClosedFloatingPointRange<Double>?,
    ) : ReportBlock
    data class Subtitle(val text: String) : ReportBlock
}

data class ReportSection(val title: String, val blocks: List<ReportBlock>)

data class DoctorReport(val title: String, val period: String, val generated: String, val sections: List<ReportSection>)

/** Builds the content of the PDF report for a doctor (rendering is done by the UI layer). */
object DoctorReports {
    private val FR = Locale.FRANCE
    private val date = DateTimeFormatter.ofPattern("d MMMM yyyy", FR)
    private val shortDate = DateTimeFormatter.ofPattern("dd/MM", FR)
    private fun pct(f: Float?) = f?.let { "${(it * 100).roundToInt()} %" } ?: "—"
    private fun dec(f: Float) = "%.1f".format(FR, f)

    fun availableParts(state: AppState): List<ReportPart> = ReportPart.entries.filter {
        when (it) {
            ReportPart.PRN -> state.treatments.any { t -> t.asNeeded }
            ReportPart.MEASURES -> state.measurements.isNotEmpty()
            ReportPart.SLEEP -> state.sleep.isNotEmpty()
            ReportPart.JOURNAL -> state.journal.any { e -> e.hasJournal }
            ReportPart.CYCLE -> state.profile?.menstruating == true && state.profile.cycle?.periodStarts?.isNotEmpty() == true
            else -> true
        }
    }

    fun build(state: AppState, now: LocalDateTime, days: Int, parts: Set<ReportPart>): DoctorReport {
        val today = now.toLocalDate()
        val start = today.minusDays(days - 1L)
        val name = state.profile?.name?.takeIf { it.isNotBlank() }
        val sections = buildList {
            if (ReportPart.PROFILE in parts) add(profile(state))
            if (ReportPart.TREATMENTS in parts) add(treatments(state, today))
            if (ReportPart.ADHERENCE in parts) adherence(state, now, days)?.let(::add)
            if (ReportPart.PRN in parts) prn(state, now, days)?.let(::add)
            if (ReportPart.MEASURES in parts) measures(state, today, days)?.let(::add)
            if (ReportPart.SLEEP in parts) sleep(state, today, days)?.let(::add)
            if (ReportPart.JOURNAL in parts) journal(state, start, today)?.let(::add)
            if (ReportPart.CYCLE in parts) cycle(state, today)?.let(::add)
        }
        return DoctorReport(
            title = "Suivi de santé" + (name?.let { " · $it" } ?: ""),
            period = "Du ${start.format(date)} au ${today.format(date)} ($days jours)",
            generated = "Généré le ${today.format(date)} avec MédiPote, à partir des données saisies par la personne.",
            sections = sections,
        )
    }

    private fun profile(state: AppState): ReportSection {
        val p = state.profile
        val facts = buildList {
            p?.age?.let { add("Âge" to "$it ans") }
            p?.sex?.let { add("Sexe" to it.label) }
            p?.weightKg?.let { add("Poids" to "${"%.1f".format(FR, it)} kg") }
            p?.heightCm?.let { add("Taille" to "$it cm") }
            add("Antécédents" to (p?.medicalHistory?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "aucun renseigné"))
            add("Allergies" to (p?.allergies?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "aucune renseignée"))
        }
        return ReportSection(ReportPart.PROFILE.label, listOf(ReportBlock.Facts(facts)))
    }

    private fun posology(t: Treatment, today: LocalDate): String = when {
        t.asNeeded -> buildString {
            append("Si besoin, ${t.unit.format(t.prnQuantity)} par prise")
            t.maxPerDay?.let { append(", max $it / 24 h") }
            t.minIntervalHours?.let { append(", au moins $it h entre deux prises") }
        }
        else -> buildString {
            append(t.doses.sortedBy { it.minuteOfDay }.joinToString(", ") { "${it.timeLabel} (${t.unit.format(t.quantityOn(today, it))})" })
            t.pill?.let { append(" · ${com.medipote.app.data.PillRegimen.of(it).label}") }
            when {
                t.everyDays > 1 -> append(" · tous les ${t.everyDays} jours")
                t.daysOfWeek.size < 7 -> append(" · ${t.daysOfWeek.sorted().joinToString(", ") { java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, FR) }}")
            }
            if (t.withFood) append(" · au repas")
        }
    }

    private fun treatments(state: AppState, today: LocalDate): ReportSection {
        val current = state.treatments.filter { it.endDate == null || !it.endDate.isBefore(today) }
        if (current.isEmpty()) return ReportSection(ReportPart.TREATMENTS.label, listOf(ReportBlock.Paragraph("Aucun traitement en cours.", muted = true)))
        val rows = current.sortedBy { it.asNeeded }.map { t ->
            listOf(
                t.displayName,
                posology(t, today),
                buildString {
                    append("depuis le ${t.startDate.format(shortDate)}")
                    t.endDate?.let { append(", jusqu'au ${it.format(shortDate)}") }
                },
            )
        }
        return ReportSection(
            ReportPart.TREATMENTS.label,
            listOf(ReportBlock.Table(listOf("Médicament", "Posologie", "Période"), rows, listOf(0.28f, 0.5f, 0.22f))),
        )
    }

    private fun adherence(state: AppState, now: LocalDateTime, days: Int): ReportSection? {
        if (state.treatments.none { !it.asNeeded }) return null
        val r = Reports.adherence(state, now, days)
        val blocks = mutableListOf<ReportBlock>()
        blocks += ReportBlock.Facts(
            listOf(
                "Prises faites" to "${pct(r.rate.value)} (${r.rate.taken} sur ${r.rate.decided})",
                "À l'heure (± 30 min)" to pct(r.punctuality),
                "Écart moyen avec l'heure prévue" to (r.averageDelayMinutes?.let { "${if (it > 0) "+" else ""}$it min" } ?: "—"),
                "Prises oubliées" to "${r.missed}",
                "Journées complètes" to "${r.completeDays} sur ${r.daysWithDoses}",
            ),
        )
        if (r.perTreatment.isNotEmpty()) {
            blocks += ReportBlock.Table(
                listOf("Traitement", "Prises faites", "Écart moyen"),
                r.perTreatment.map { listOf(it.name, if (it.rate.decided == 0) "—" else "${it.rate.taken}/${it.rate.decided} (${pct(it.rate.value)})", it.averageDelayMinutes?.let { d -> "$d min" } ?: "—") },
                listOf(0.5f, 0.3f, 0.2f),
            )
        }
        val slots = r.bySlot.filter { it.value.decided > 0 }
        if (slots.size > 1) blocks += ReportBlock.Paragraph("Selon le moment : " + slots.entries.joinToString(" · ") { "${it.key.label.lowercase()} ${pct(it.value.value)}" })
        r.insights.filter { it.tone == Tone.WARN }.forEach { blocks += ReportBlock.Paragraph("• ${it.text}") }
        return ReportSection(ReportPart.ADHERENCE.label, blocks)
    }

    private fun prn(state: AppState, now: LocalDateTime, days: Int): ReportSection? {
        val prn = state.treatments.filter { it.asNeeded }
        if (prn.isEmpty()) return null
        val blocks = mutableListOf<ReportBlock>()
        for (t in prn) {
            val r = Reports.prn(state, t.id, now, days)
            blocks += ReportBlock.Subtitle(t.displayName)
            if (r.count == 0) {
                blocks += ReportBlock.Paragraph("Aucune prise sur la période.", muted = true)
                continue
            }
            blocks += ReportBlock.Facts(
                listOfNotNull(
                    "Prises" to "${r.count} en ${r.days} jours (${r.daysWithIntake} jours concernés), ${dec(r.perWeek)} par semaine",
                    r.typicalMinuteOfDay?.let { "Heure habituelle" to "vers ${Reports.formatTime(it)}" },
                    r.reasons.takeIf { it.isNotEmpty() }?.let { "Motifs" to it.take(4).joinToString(", ") { (k, n) -> "$k ($n)" } },
                    r.contexts.takeIf { it.isNotEmpty() }?.let { "Contextes" to it.take(4).joinToString(", ") { (k, n) -> "$k ($n)" } },
                    r.averageIntensity?.let { "Intensité moyenne de la gêne" to "${dec(it)}/10" },
                    r.effectiveShare?.let { "Efficacité ressentie" to "${pct(it)} « bien » ou mieux (${r.reliefRated} notées)" },
                    r.cycleShare?.let { (d, p) -> "Pendant les règles" to "${pct(d)} des prises (${pct(p)} des jours)" },
                    ("Jours au-dessus du maximum" to "${r.daysOverMax}").takeIf { r.daysOverMax > 0 },
                ),
            )
        }
        return ReportSection(ReportPart.PRN.label, blocks)
    }

    private fun measures(state: AppState, today: LocalDate, days: Int): ReportSection? {
        val types = MeasureType.entries.filter { t -> state.measurements.any { it.type == t } }
        if (types.isEmpty()) return null
        val blocks = mutableListOf<ReportBlock>()
        for (type in types) {
            val r = Measures.report(state, type, today, days)
            if (r.count == 0) continue
            blocks += ReportBlock.Subtitle("${type.label} (${type.unit})")
            val avg = if (type == MeasureType.BLOOD_PRESSURE) "${r.average!!.roundToInt()}/${r.average2?.roundToInt() ?: "?"}" else type.format(r.average!!)
            blocks += ReportBlock.Facts(
                listOfNotNull(
                    "Mesures" to "${r.count}",
                    "Moyenne" to avg + (r.averagePulse?.let { " · pouls moyen $it" } ?: ""),
                    "Minimum / maximum" to "${r.min!!.display} / ${r.max!!.display}",
                    r.inRange?.takeIf { type != MeasureType.WEIGHT }?.let { "Dans les repères" to pct(it) },
                    r.change?.takeIf { type == MeasureType.WEIGHT }?.let { "Évolution" to "${if (it >= 0) "+" else "−"}${type.format(kotlin.math.abs(it))} kg" },
                ),
            )
            if (r.count >= 2) {
                val band = when (type) {
                    MeasureType.BLOOD_PRESSURE -> 60.0..135.0
                    MeasureType.GLUCOSE -> 0.70..1.10
                    MeasureType.HEART_RATE -> 50.0..100.0
                    MeasureType.TEMPERATURE -> 35.5..37.9
                    MeasureType.OXYGEN -> 95.0..100.0
                    MeasureType.WEIGHT -> null
                }
                blocks += ReportBlock.Chart(type.label, r.values.map { it.at to listOfNotNull(it.value, it.value2) }, { v -> type.format(v) }, band)
            }
            val last = r.values.takeLast(12).reversed()
            blocks += ReportBlock.Table(
                listOf("Date", "Valeur", "Remarque"),
                last.map { m ->
                    listOf(
                        "${m.at.format(shortDate)} ${"%02d:%02d".format(m.at.hour, m.at.minute)}",
                        m.display + (m.pulse?.let { " · pouls $it" } ?: ""),
                        listOf(m.context, m.notes).filter { it.isNotBlank() }.joinToString(" · "),
                    )
                },
                listOf(0.25f, 0.3f, 0.45f),
            )
        }
        return ReportSection(ReportPart.MEASURES.label, blocks)
    }

    private fun sleep(state: AppState, today: LocalDate, days: Int): ReportSection? {
        val r = Sleep.report(state, today, days)
        if (r.count == 0) return null
        val facts = listOfNotNull(
            "Nuits notées" to "${r.count}",
            r.averageAsleep?.let { "Sommeil moyen" to "${Sleep.format(it)} (au lit ${Sleep.format(r.averageInBed ?: 0)})" },
            r.typicalBedtime?.let { "Coucher / réveil habituels" to "${Reports.formatTime(it)} / ${r.typicalWake?.let(Reports::formatTime) ?: "—"}" },
            r.averageLatency?.let { "Endormissement" to Sleep.format(it) },
            r.averageAwakenings?.let { "Réveils nocturnes" to "${dec(it)} par nuit (${Sleep.format(r.averageAwakeMinutes ?: 0)} d'éveil), ${r.calmNights} nuits sans réveil" },
            r.reasons.takeIf { it.isNotEmpty() }?.let { "Causes des réveils" to it.take(4).joinToString(", ") { (k, n) -> "${k.lowercase()} ($n)" } },
            r.averageEfficiency?.let { "Efficacité du sommeil" to pct(it) },
            r.averageQuality?.let { "Qualité ressentie" to "${dec(it)}/5" },
            r.averageRested?.let { "Nuits reposantes" to "${dec(it)}/5" },
            r.averagePleasant?.let { "Nuits agréables" to "${dec(it)}/5" },
        )
        return ReportSection(ReportPart.SLEEP.label, listOf(ReportBlock.Facts(facts)))
    }

    private fun journal(state: AppState, start: LocalDate, today: LocalDate): ReportSection? {
        val entries = state.journal.filter { !it.date.isBefore(start) && !it.date.isAfter(today) && it.hasJournal }
        if (entries.isEmpty()) return null
        fun avg(v: List<Int>) = v.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        fun ranked(v: List<String>) = v.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).joinToString(", ") { "${it.key} (${it.value})" }
        val facts = listOfNotNull(
            "Jours renseignés" to "${entries.size}",
            avg(entries.mapNotNull { it.physical })?.let { "Forme physique moyenne" to "${dec(it)}/5" },
            avg(entries.mapNotNull { it.mental })?.let { "Moral moyen" to "${dec(it)}/5" },
            entries.flatMap { it.physicalSymptoms }.takeIf { it.isNotEmpty() }?.let { "Symptômes physiques" to ranked(it) },
            entries.flatMap { it.mentalSymptoms }.takeIf { it.isNotEmpty() }?.let { "Symptômes psychologiques" to ranked(it) },
            entries.filter { it.illness.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { list ->
                "Maladies notées" to list.sortedBy { it.date }.joinToString(", ") { "${it.illness} (${it.date.format(shortDate)})" }
            },
        )
        return ReportSection(ReportPart.JOURNAL.label, listOf(ReportBlock.Facts(facts)))
    }

    private fun cycle(state: AppState, today: LocalDate): ReportSection? {
        val info = state.profile?.cycle?.takeIf { state.profile.menstruating && it.periodStarts.isNotEmpty() } ?: return null
        val starts = info.periodStarts.sorted().takeLast(6)
        val status = Cycle.status(info, today)
        val facts = listOfNotNull(
            "Derniers débuts de règles" to starts.reversed().joinToString(", ") { it.format(shortDate) },
            "Durée moyenne du cycle" to "${Cycle.averageLength(info)} jours",
            "Durée des règles" to "${info.periodLength} jours",
            status?.let { "Prochaines règles estimées" to it.nextPeriod.format(date) },
            status?.takeIf { it.lateDays > 0 }?.let { "Retard" to "${it.lateDays} jours" },
            starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.takeIf { it.size >= 2 }?.let { "Cycles récents" to it.joinToString(", ") { d -> "$d j" } },
        )
        return ReportSection(ReportPart.CYCLE.label, listOf(ReportBlock.Facts(facts)))
    }
}
