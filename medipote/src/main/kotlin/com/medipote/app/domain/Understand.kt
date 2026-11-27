package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.MeasureType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** How soon something is worth a professional's eyes. Never a diagnosis. */
enum class Urgency(val label: String) {
    /** Worth saying at the next appointment. */
    MENTION("À évoquer à ton prochain rendez-vous"),

    /** Worth an appointment in the days to come. */
    SOON("Prends rendez-vous dans les jours qui viennent"),

    /** Worth calling for help now. */
    URGENT("Appelle le 15 ou le 112"),
}

/** What can explain a symptom in the person's own context, with the data it comes from. */
data class Factor(val emoji: String, val title: String, val evidence: String, val tip: String? = null)

/** Something in the notes that deserves a professional's opinion. [key] is stable, so it can be set aside for a week. */
data class Concern(
    val key: String,
    val urgency: Urgency,
    val title: String,
    val reasons: List<String>,
    /** The symptom it is about, when it is about one. */
    val symptom: String? = null,
)

data class AdviceItem(val emoji: String, val text: String)

/**
 * « Comprendre »: explanations, advice and « should I see someone? » drawn from the person's own notes with
 * rules written by hand. It is **not** a generative AI: every sentence comes from a rule that can be read, tested
 * and corrected, nothing is invented, and nothing leaves the phone. Thresholds are cautious, general and indicative:
 * they are for a health professional to review.
 */
object Understand {
    // ── Thresholds ──
    const val STRONG = 4
    const val RECENT_DAYS = 3
    const val FREQUENT_NOTES = 5       // notes in 7 days, each at least MODERATE
    const val MODERATE = 3
    const val LONG_NOTES = 10          // notes in 14 days
    const val DISMISS_DAYS = 7L
    const val PERIOD_MENTION_DAYS = 45L
    const val PERIOD_SOON_DAYS = 90L
    const val LOW_MOOD = 2
    const val LOW_MOOD_DAYS = 7        // of the last 14

    private val SLEEP_SENSITIVE = setOf("fatigue", "headache", "anxiety", "lowmood", "fog", "irritability", "dizzy", "pain", "palpitations")
    private val CYCLE_SENSITIVE = setOf("fatigue", "headache", "anxiety", "lowmood", "irritability", "pain", "digestion", "nausea", "joints", "sleep", "fog")
    private val DEHYDRATION = setOf("headache", "fatigue", "dizzy", "fog")
    private val NEW_TREATMENT = setOf("nausea", "dizzy", "headache", "fatigue", "digestion", "fog", "palpitations", "sleep")
    private val FEVER = setOf("fatigue", "headache", "joints", "pain", "cold", "nausea")
    private val BLOOD_PRESSURE = setOf("headache", "dizzy", "palpitations")
    private val ILLNESS = setOf("fatigue", "headache", "joints", "pain", "nausea", "digestion")

    private fun f1(x: Double) = "%.1f".format(java.util.Locale.FRANCE, x)

    // ─────────────────────────── What can explain it ───────────────────────────

    /** What, in the last days and in the past notes, can explain [symptom]. Each line shows its source. */
    fun factors(state: AppState, symptom: String, today: LocalDate): List<Factor> = buildList {
        val from = today.minusDays(RECENT_DAYS - 1L)
        val goal = state.settings.sleep.goalMinutes

        // Sleep, the last nights.
        if (symptom in SLEEP_SENSITIVE) {
            val nights = state.sleep.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
            val blank = nights.count { it.noSleep }
            val avg = nights.takeIf { it.isNotEmpty() }?.map { it.asleepMinutes }?.average()?.toInt()
            if (blank > 0) {
                add(Factor("🌙", "Une nuit blanche", "Tu as noté ${if (blank > 1) "$blank nuits blanches" else "une nuit blanche"} ces derniers jours.", "Garde ton heure de lever, sors à la lumière du jour, et si tu fais une sieste, courte (20 à 30 min) et avant 15 h."))
            } else if (avg != null && avg < goal - 90) {
                add(Factor("🌙", "Peu de sommeil", "Tu as dormi ${Sleep.format(avg)} en moyenne ces ${nights.size} dernières nuits, pour un objectif de ${Sleep.format(goal)}.", "Ce soir, couche-toi un peu plus tôt, écrans éteints une heure avant."))
            }
        }

        // The cycle.
        val info = state.profile?.cycle
        if (symptom in CYCLE_SENSITIVE && state.profile?.menstruating == true && info != null) {
            val phase = CycleStats.phaseOn(info, today)
            val status = Cycle.status(info, today)
            when {
                phase == CyclePhase.MENSTRUAL -> add(Factor("🩸", "Tes règles", "Tu es dans la phase de tes règles : les hormones sont au plus bas.", "De la chaleur sur le ventre et une marche douce soulagent souvent."))
                phase == CyclePhase.LUTEAL && status != null && status.daysUntilNextPeriod in 0..7 ->
                    add(Factor("🌗", "Les jours avant tes règles", "Tes règles sont attendues dans ${status.daysUntilNextPeriod} jour${if (status.daysUntilNextPeriod > 1) "s" else ""} : la progestérone domine.", "Moins de caféine et d'alcool, des repas réguliers, un coucher plus tôt."))
            }
        }

        // Water.
        val water = state.settings.water
        val last2 = (0..1).map { Water.ml(state, today.minusDays(it.toLong())) }
        val waterGoal = Water.goalMl(water)
        if (symptom in DEHYDRATION && (water.enabled || last2.any { it > 0 }) && last2.average() < waterGoal / 2.0) {
            add(Factor("💧", "Peu d'eau", "Tu as noté ${Water.format(last2.average().toInt())} par jour ces 2 derniers jours, pour un objectif de ${Water.format(waterGoal)}.", "Un grand verre d'eau maintenant, puis un petit à chaque heure."))
        }

        // A treatment begun recently.
        val recent = state.treatments.filter { !it.asNeeded && !it.startDate.isAfter(today) && !it.startDate.isBefore(today.minusDays(14)) }
        if (symptom in NEW_TREATMENT && recent.isNotEmpty()) {
            val t = recent.first()
            val days = ChronoUnit.DAYS.between(t.startDate, today)
            add(Factor("💊", "Un traitement commencé il y a peu", "« ${t.displayName} » a commencé il y a ${if (days == 0L) "moins d'un jour" else "$days jour${if (days > 1) "s" else ""}"}, d'après tes dates.", "Relis la notice ou demande à ton pharmacien si ce symptôme y figure. N'arrête rien sans avis."))
        }

        // Doses forgotten.
        val missed = missedDoses(state, today, 3)
        if (missed >= 2) {
            add(Factor("⏰", "Des prises manquées", "$missed prises n'ont pas été notées ces 3 derniers jours.", "Si tu les as oubliées, ça vaut la peine d'en parler à ton médecin ou à ton pharmacien : il y a souvent des astuces."))
        }

        // Fever, blood pressure.
        val temp = state.measurements.filter { it.type == MeasureType.TEMPERATURE && !it.at.toLocalDate().isBefore(from) }.maxByOrNull { it.value }
        if (symptom in FEVER && temp != null && temp.value >= 38.0) {
            add(Factor("🌡️", "De la fièvre", "Tu as mesuré ${temp.type.format(temp.value)} °C le ${temp.at.toLocalDate().dayOfMonth}/${"%02d".format(temp.at.toLocalDate().monthValue)}.", "Repose-toi et bois. Si elle dure plus de 3 jours ou monte à 39 °C, vois un médecin."))
        }
        val bp = Measures.latest(state, MeasureType.BLOOD_PRESSURE)?.takeIf { !it.at.toLocalDate().isBefore(from) }
        if (symptom in BLOOD_PRESSURE && bp != null) {
            val a = Measures.assess(bp, state.profile)
            if (a != null && a.level != Level.NORMAL) add(Factor("🩺", if (a.level == Level.HIGH) "Une tension élevée" else "Une tension basse", "Ta dernière tension : ${bp.value.toInt()}/${bp.value2?.toInt() ?: "—"}. ${a.text}", null))
        }

        // Being ill.
        val ill = (0 until RECENT_DAYS).map { state.entryFor(today.minusDays(it.toLong())).illness }.firstOrNull { it.isNotBlank() }
        if (symptom in ILLNESS && ill != null) add(Factor("🤒", "Une maladie en cours", "Tu as noté « $ill » dans ton journal.", "Le repos aide plus qu'on ne le croit."))

        // The conditions the person follows.
        state.settings.wellbeing.conditions.mapNotNull { c -> c.presetId?.let(WellbeingLibrary::condition) }
            .filter { symptom in it.symptoms }
            .forEach { add(Factor(it.emoji, "Ce que tu suis avec ${it.name}", "${Wellbeing.name(symptom)} fait partie de ce qu'on suit souvent avec cette condition.", null)) }

        // Patterns in the notes.
        patterns(state, symptom, today).forEach(::add)
    }

    /** What the person's own past notes show about this symptom. */
    private fun patterns(state: AppState, symptom: String, today: LocalDate): List<Factor> = buildList {
        val notes = state.symptomLog.filter { it.symptom == symptom && !it.date.isBefore(today.minusDays(120)) && it.intensity >= 2 }
        if (notes.size < 4) return@buildList
        val name = Wellbeing.name(symptom).lowercase()

        // The same days as another symptom.
        val byDate = state.symptomLog.filter { it.intensity >= 2 && !it.date.isBefore(today.minusDays(120)) }.groupBy { it.date }
        val noteDays = notes.map { it.date }.toSet()
        val together = byDate.filterKeys { it in noteDays }.values.flatten().filter { it.symptom != symptom }.groupingBy { it.symptom }.eachCount()
            .filter { it.value >= 3 }.maxByOrNull { it.value }
        if (together != null) {
            add(Factor("🔗", "Souvent avec ${Wellbeing.name(together.key).lowercase()}", "Tu as noté les deux les mêmes jours ${together.value} fois sur ${noteDays.size}.", null))
        }

        // After a short night.
        val goal = state.settings.sleep.goalMinutes
        val short = notes.filter { n -> state.sleep.firstOrNull { it.date == n.date }?.let { it.asleepMinutes < goal - 60 } == true }
        val rested = notes.filter { n -> state.sleep.firstOrNull { it.date == n.date }?.let { it.asleepMinutes >= goal - 60 } == true }
        if (short.size >= 3 && rested.size >= 3) {
            val a = short.map { it.intensity }.average()
            val b = rested.map { it.intensity }.average()
            if (a - b >= 0.7) add(Factor("😴", "Plus fort après une courte nuit", "Ton ${name} est à ${f1(a)}/5 après une courte nuit, contre ${f1(b)}/5 après une bonne nuit.", "Protéger ton sommeil peut aider ce symptôme."))
        }

        // A phase of the cycle.
        val info = state.profile?.cycle
        if (info != null && state.profile?.menstruating == true) {
            val phases = notes.mapNotNull { CycleStats.phaseOn(info, it.date) }
            if (phases.size >= 4) {
                val (phase, n) = phases.groupingBy { it }.eachCount().maxByOrNull { it.value }!!
                if (n.toDouble() / phases.size >= 0.6 && phase != CyclePhase.FOLLICULAR) {
                    add(Factor("🔁", "Surtout en ${phase.label.lowercase()}", "$n de tes ${phases.size} notes tombent dans cette phase de ton cycle.", "Tu peux t'y préparer : plus de repos et moins de choses à faire ces jours-là."))
                }
            }
        }
    }

    // ─────────────────────────── Should I see someone? ───────────────────────────

    /** Doses planned and not given, among the [days] days before [today]. */
    fun missedDoses(state: AppState, today: LocalDate, days: Int): Int {
        val logs = state.doseLogs.associateBy { it.key }
        return (1..days).sumOf { back ->
            val date = today.minusDays(back.toLong())
            // Looked at the next day, once the time to give it has passed.
            Schedule.itemsOn(state.treatments.filter { !it.asNeeded }, logs, date, date.plusDays(1).atTime(12, 0)).count { it.state == DoseState.MISSED }
        }
    }

    /** Things in the notes of one symptom that deserve an opinion. */
    fun symptomConcerns(state: AppState, symptom: String, today: LocalDate): List<Concern> = buildList {
        val name = Wellbeing.name(symptom)
        val notes = state.symptomLog.filter { it.symptom == symptom && !it.date.isAfter(today) }
        fun between(days: Int) = notes.filter { !it.date.isBefore(today.minusDays(days - 1L)) }
        val recent = between(RECENT_DAYS)

        // A top mark on something that can be serious.
        if (symptom == "breath" && notes.any { it.intensity == 5 && !it.date.isBefore(today.minusDays(1)) }) {
            add(Concern("sym:$symptom:max", Urgency.URGENT, "$name au plus fort", listOf("Tu as noté le maximum pour « ${name.lowercase()} » hier ou aujourd'hui. Si c'est brutal, inhabituel, ou avec une douleur dans la poitrine ou un malaise : appelle le 15."), symptom))
        }
        // Strong for days.
        if (recent.count { it.intensity >= STRONG } >= 2) {
            add(Concern("sym:$symptom:strong", Urgency.SOON, "$name fort plusieurs jours de suite", listOf("Tu l'as noté à ${STRONG}/5 ou plus sur ${recent.count { it.intensity >= STRONG }} des $RECENT_DAYS derniers jours."), symptom))
        }
        // Almost every day.
        val week = between(7)
        if (week.count { it.intensity >= MODERATE } >= FREQUENT_NOTES) {
            add(Concern("sym:$symptom:frequent", Urgency.SOON, "$name presque tous les jours", listOf("Noté à ${MODERATE}/5 ou plus ${week.count { it.intensity >= MODERATE }} jours sur les 7 derniers."), symptom))
        }
        // Long-lasting.
        val fortnight = between(14)
        if (fortnight.count { it.intensity >= 2 } >= LONG_NOTES && week.count { it.intensity >= MODERATE } < FREQUENT_NOTES) {
            add(Concern("sym:$symptom:long", Urgency.MENTION, "$name depuis plus de deux semaines", listOf("Tu l'as noté ${fortnight.size} jours sur les 14 derniers."), symptom))
        }
        // Getting worse.
        val last = notes.sortedByDescending { it.date }.take(3)
        val before = notes.sortedByDescending { it.date }.drop(3).take(7)
        if (last.size == 3 && before.size >= 3 && last.map { it.intensity }.average() - before.map { it.intensity }.average() >= 1.0 && last.none { it.date.isBefore(today.minusDays(7)) }) {
            add(Concern("sym:$symptom:worse", Urgency.MENTION, "$name qui s'aggrave", listOf("Tes 3 dernières notes (${f1(last.map { it.intensity }.average())}/5) sont plus fortes que les précédentes (${f1(before.map { it.intensity }.average())}/5)."), symptom))
        }
        // With a measure out of range, the same day.
        if (symptom in setOf("headache", "dizzy", "palpitations", "breath") && recent.any { it.intensity >= MODERATE }) {
            val bp = Measures.latest(state, MeasureType.BLOOD_PRESSURE)?.takeIf { !it.at.toLocalDate().isBefore(today.minusDays(1)) }
            if (bp != null && (bp.value >= 180 || (bp.value2 ?: 0.0) >= 110)) {
                add(Concern("sym:$symptom:bp", Urgency.URGENT, "$name avec une tension très élevée", listOf("Tension ${bp.value.toInt()}/${bp.value2?.toInt() ?: "—"} et $name noté : appelle le 15, surtout avec une vue trouble ou une douleur dans la poitrine."), symptom))
            }
        }
    }

    /** Several symptoms at once, measures, sleep, cycle, mood, forgotten doses: everything that deserves an opinion. */
    fun scan(state: AppState, today: LocalDate): List<Concern> = buildList {
        val tracked = (state.symptomLog.map { it.symptom }.distinct() + state.settings.wellbeing.symptoms).distinct()
        tracked.forEach { addAll(symptomConcerns(state, it, today)) }

        // Many symptoms on the same days.
        val strongDays = (0 until RECENT_DAYS).count { back ->
            Wellbeing.notesOn(state, today.minusDays(back.toLong())).filter { it.intensity >= MODERATE }.map { it.symptom }.distinct().size >= 3
        }
        if (strongDays >= 2) add(Concern("many", Urgency.SOON, "Plusieurs symptômes à la fois", listOf("Au moins trois symptômes à ${MODERATE}/5 ou plus, sur $strongDays des $RECENT_DAYS derniers jours.")))

        addAll(measureConcerns(state, today))
        addAll(sleepConcerns(state, today))
        addAll(cycleConcerns(state, today))

        // Mood.
        val mood = (0 until 14).mapNotNull { state.entryFor(today.minusDays(it.toLong())).mental }
        if (mood.count { it <= LOW_MOOD } >= LOW_MOOD_DAYS) {
            add(Concern("mood", Urgency.SOON, "Un moral bas depuis un moment", listOf("Ton humeur est basse ${mood.count { it <= LOW_MOOD }} jours sur les 14 derniers. Tu n'as pas à traverser ça seul·e : parles-en à ton médecin ou à une personne de confiance.")))
        }
        // Forgotten doses.
        val missed = missedDoses(state, today, 7)
        if (missed >= 3) add(Concern("missed", Urgency.MENTION, "Des prises souvent manquées", listOf("$missed prises sans note cette semaine. Ton médecin ou ton pharmacien peut adapter les horaires ou proposer des astuces.")))
    }.sortedBy { it.urgency.ordinal.unaryMinus() }.distinctBy { it.key }

    fun measureConcerns(state: AppState, today: LocalDate): List<Concern> = buildList {
        fun recent(type: MeasureType, days: Long) = state.measurements.filter { it.type == type && !it.at.toLocalDate().isBefore(today.minusDays(days)) }.sortedByDescending { it.at }

        recent(MeasureType.BLOOD_PRESSURE, 2).firstOrNull()?.let { m ->
            val sys = m.value
            val dia = m.value2 ?: 0.0
            if (sys >= 180 || dia >= 110) add(Concern("bp:high", Urgency.SOON, "Une tension très élevée", listOf("Dernière mesure : ${sys.toInt()}/${dia.toInt()}. Refais-la au calme après 5 minutes assis·e ; si elle reste à ce niveau, appelle ton médecin. Avec un mal de tête fort, une vue trouble ou une douleur dans la poitrine : appelle le 15.")))
            else if (sys < 90 || (m.value2 != null && dia < 60)) {
                val dizzy = Wellbeing.notesOn(state, today).any { it.symptom == "dizzy" && it.intensity >= MODERATE } || Wellbeing.notesOn(state, today.minusDays(1)).any { it.symptom == "dizzy" && it.intensity >= MODERATE }
                if (dizzy) add(Concern("bp:low", Urgency.SOON, "Une tension basse avec des vertiges", listOf("Dernière mesure : ${sys.toInt()}/${dia.toInt()}, et des vertiges notés. Lève-toi doucement, bois, et parles-en à ton médecin.")))
            }
        }
        recent(MeasureType.HEART_RATE, 2).firstOrNull()?.let { m ->
            if (m.value > 120 || m.value < 40) add(Concern("pulse", Urgency.SOON, "Un pouls inhabituel", listOf("Dernière mesure : ${m.value.toInt()} bpm au repos. Refais-la assis·e et au calme ; si elle persiste ou avec un malaise, parles-en à un médecin (15 en cas de malaise).")))
        }
        val temps = recent(MeasureType.TEMPERATURE, 3)
        val hot = temps.map { it.at.toLocalDate() }.distinct().filter { d -> temps.any { it.at.toLocalDate() == d && it.value >= 38.0 } }
        when {
            temps.any { it.value >= 39.0 } -> add(Concern("fever:high", Urgency.SOON, "Une forte fièvre", listOf("Tu as mesuré ${temps.maxOf { it.value }.let { Measures.dec(it) }} °C. À partir de 39 °C, ou si elle s'accompagne de raideur de la nuque, de confusion ou d'éruption, vois un médecin sans attendre (15 si ça s'aggrave vite).")))
            hot.size >= 3 -> add(Concern("fever:long", Urgency.SOON, "De la fièvre depuis 3 jours", listOf("Une température de 38 °C ou plus sur ${hot.size} jours : un avis médical est utile.")))
        }
        recent(MeasureType.OXYGEN, 2).firstOrNull()?.let { m ->
            when {
                m.value < 92 -> add(Concern("spo2:low", Urgency.URGENT, "Une saturation basse", listOf("${m.value.toInt()} % : sous 92 %, appelle le 15, surtout avec un essoufflement. Vérifie que le capteur est bien placé et que tes doigts ne sont pas froids.")))
                m.value < 95 -> add(Concern("spo2:borderline", Urgency.SOON, "Une saturation un peu basse", listOf("${m.value.toInt()} % : refais la mesure au calme, mains chaudes. Si elle reste sous 95 %, parles-en à un médecin.")))
            }
        }
        // Weight.
        val weights = state.measurements.filter { it.type == MeasureType.WEIGHT && !it.at.toLocalDate().isBefore(today.minusDays(30)) }.sortedBy { it.at }
        if (weights.size >= 2) {
            val change = (weights.last().value - weights.first().value) / weights.first().value
            if (abs(change) >= 0.05) add(Concern("weight", Urgency.MENTION, "Un poids qui change vite", listOf("${if (change > 0) "+" else "−"}${f1(abs(change) * 100)} % en 30 jours (${Measures.dec(weights.first().value)} → ${Measures.dec(weights.last().value)} kg) : à évoquer à ton prochain rendez-vous.")))
        }
    }

    fun sleepConcerns(state: AppState, today: LocalDate): List<Concern> = buildList {
        val week = state.sleep.filter { !it.date.isBefore(today.minusDays(6)) && !it.date.isAfter(today) }
        val blank = week.count { it.noSleep }
        if (blank >= 2) add(Concern("sleep:blank", Urgency.SOON, "Plusieurs nuits blanches", listOf("$blank nuits blanches sur les 7 dernières. Un avis médical peut aider, surtout si ça dure.")))
        val short = week.count { !it.noSleep && it.asleepMinutes < 5 * 60 }
        if (week.size >= 5 && short + blank >= 5) add(Concern("sleep:short", Urgency.MENTION, "Moins de 5 h de sommeil presque chaque nuit", listOf("${short + blank} nuits sur ${week.size} sous 5 h. À évoquer à ton prochain rendez-vous.")))
        val slept = week.filter { !it.noSleep }
        if (slept.size >= 5 && slept.map { it.latencyMinutes }.average() >= 45) add(Concern("sleep:latency", Urgency.MENTION, "Un endormissement long", listOf("Tu mets en moyenne ${Sleep.format(slept.map { it.latencyMinutes }.average().toInt())} à t'endormir sur les 7 dernières nuits.")))
    }

    fun cycleConcerns(state: AppState, today: LocalDate): List<Concern> = buildList {
        if (state.profile?.menstruating != true) return@buildList
        val info = state.profile.cycle ?: return@buildList
        val starts = info.periodStarts.sorted().filter { !it.isAfter(today) }
        // A pill or an implant can stop periods: only without any pill in the treatments.
        val onPill = state.treatments.any { it.pill != null }
        if (starts.size >= 2 && !onPill) {
            val since = ChronoUnit.DAYS.between(starts.last(), today)
            when {
                since > PERIOD_SOON_DAYS -> add(Concern("cycle:absent", Urgency.SOON, "Pas de règles depuis plus de 3 mois", listOf("Dernières règles notées il y a $since jours. Si c'est bien le cas, parles-en à ton médecin (et pense à un test de grossesse si c'est possible).")))
                since > PERIOD_MENTION_DAYS -> add(Concern("cycle:late", Urgency.MENTION, "Des règles très en retard", listOf("Dernières règles notées il y a $since jours. Si tu as bien noté tes règles, à évoquer à ton prochain rendez-vous.")))
            }
            val lengths = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.takeLast(3)
            if (lengths.size == 3 && lengths.all { it < 21 || it > 38 }) {
                add(Concern("cycle:irregular", Urgency.MENTION, "Des cycles hors des repères", listOf("Tes 3 derniers cycles : ${lengths.joinToString(", ") { "$it j" }}. Les repères courants vont de ${CycleStats.USUAL_MIN} à 38 jours : à évoquer à ton prochain rendez-vous.")))
            }
        }
        val long = CycleStats.report(state, today)?.records?.mapNotNull { it.periodDays }?.takeLast(3)
        if (long != null && long.size >= 2 && long.count { it > 8 } >= 2) {
            add(Concern("cycle:long", Urgency.MENTION, "Des règles de plus de 8 jours", listOf("Durées notées : ${long.joinToString(", ") { "$it j" }}. À évoquer à ton prochain rendez-vous.")))
        }
    }

    /** What is left to show: an urgent matter always, the rest unless set aside less than a week ago. */
    fun visible(concerns: List<Concern>, dismissed: Map<String, String>, today: LocalDate): List<Concern> = concerns.filter { c ->
        c.urgency == Urgency.URGENT || dismissed[c.key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { ChronoUnit.DAYS.between(it, today) >= DISMISS_DAYS } != false
    }

    fun dismiss(dismissed: Map<String, String>, key: String, today: LocalDate): Map<String, String> =
        (dismissed.filterValues { v -> runCatching { ChronoUnit.DAYS.between(LocalDate.parse(v), today) < 60 }.getOrDefault(false) } + (key to today.toString()))

    // ─────────────────────────── Advice for the cycle and the sleep ───────────────────────────

    fun cycleAdvice(state: AppState, today: LocalDate): List<AdviceItem> = buildList {
        val info = state.profile?.cycle?.takeIf { state.profile.menstruating } ?: return@buildList
        val phase = CycleStats.phaseOn(info, today) ?: return@buildList
        val status = Cycle.status(info, today)
        when (phase) {
            CyclePhase.MENSTRUAL -> {
                add(AdviceItem("🔥", "De la chaleur sur le ventre (bouillotte) soulage souvent les crampes."))
                add(AdviceItem("🚶", "Une marche ou des étirements doux valent mieux que rester immobile."))
                add(AdviceItem("🥬", "Des aliments riches en fer (lentilles, légumes verts, viande) avec un fruit ou un légume riche en vitamine C aident à les absorber."))
                add(AdviceItem("💤", "Ménage ton énergie : c'est normal d'être plus fatigué·e, avance à ton rythme."))
            }
            CyclePhase.FOLLICULAR -> {
                add(AdviceItem("⚡", "L'énergie remonte souvent : c'est un bon moment pour une activité plus soutenue ou pour ce qui te demande de l'élan."))
                add(AdviceItem("🌙", "Profites-en pour installer un rythme de sommeil régulier, qui t'aidera pour la suite du cycle."))
            }
            CyclePhase.OVULATION -> {
                add(AdviceItem("💧", "Bois régulièrement. Une gêne brève d'un côté du ventre peut arriver ; si elle est forte ou dure, parles-en à ton médecin."))
                add(AdviceItem("🌸", "C'est la fenêtre de fertilité estimée. Ce n'est qu'une estimation : elle ne remplace pas une méthode de contraception."))
            }
            CyclePhase.LUTEAL -> {
                add(AdviceItem("🌗", "Avant les règles, dormir moins bien, être plus irritable ou plus fatigué·e est fréquent. Ce n'est pas dans ta tête."))
                add(AdviceItem("☕", "Moins de caféine et d'alcool, surtout le soir, aident le sommeil et l'humeur."))
                add(AdviceItem("🍝", "Des repas réguliers, avec des féculents complets, évitent les coups de fatigue."))
                add(AdviceItem("🛏️", "Couche-toi un peu plus tôt ces jours-là."))
                if (status != null && status.daysUntilNextPeriod in 0..3) add(AdviceItem("📅", "Tes règles sont attendues dans ${status.daysUntilNextPeriod} jour${if (status.daysUntilNextPeriod > 1) "s" else ""} : prévois des protections, une bouillotte et un jour plus calme."))
            }
        }
        // What the person's own journal shows.
        val report = CycleStats.report(state, today)
        report?.byPhase?.let { phases ->
            val luteal = phases.firstOrNull { it.phase == CyclePhase.LUTEAL }?.averageMood
            val others = phases.filter { it.phase != CyclePhase.LUTEAL }.mapNotNull { it.averageMood }
            if (luteal != null && others.isNotEmpty() && others.average() - luteal >= 0.7) {
                add(AdviceItem("📓", "D'après ton journal, ton moral baisse avant tes règles (${f1(luteal)}/5 contre ${f1(others.average())}/5 le reste du cycle) : tu peux t'y préparer."))
            }
        }
        val lutealNights = state.sleep.filter { !it.noSleep && CycleStats.phaseOn(info, it.date) == CyclePhase.LUTEAL }
        val otherNights = state.sleep.filter { !it.noSleep && CycleStats.phaseOn(info, it.date).let { p -> p != null && p != CyclePhase.LUTEAL } }
        if (lutealNights.size >= 5 && otherNights.size >= 5) {
            val diff = otherNights.map { it.asleepMinutes }.average() - lutealNights.map { it.asleepMinutes }.average()
            if (diff >= 30) add(AdviceItem("😴", "Tu dors en moyenne ${Sleep.format(diff.toInt())} de moins avant tes règles : garde ton heure de lever et avance le coucher."))
        }
    }

    fun sleepAdvice(state: AppState, today: LocalDate): List<AdviceItem> = buildList {
        val r = Sleep.report(state, today, 14)
        if (r.count == 0) return@buildList
        val goal = state.settings.sleep.goalMinutes
        if (r.sleeplessNights > 0) {
            add(AdviceItem("🌙", "Après une nuit blanche : garde ton heure de lever, sors à la lumière du jour, évite la caféine l'après-midi et, si tu fais une sieste, courte et avant 15 h."))
        }
        r.averageAsleep?.takeIf { it < goal - 60 }?.let {
            add(AdviceItem("🛏️", "Tu dors ${Sleep.format(goal - it)} de moins que ton objectif en moyenne. Avance ton coucher de 15 minutes chaque soir plutôt que d'un seul coup."))
        }
        r.averageLatency?.takeIf { it >= 30 }?.let {
            add(AdviceItem("🧘", "Tu mets environ ${Sleep.format(it)} à t'endormir. Une routine calme le soir, une lumière douce et pas d'écran dans l'heure qui précède aident ; au bout de 20 minutes sans sommeil, lève-toi et fais quelque chose de calme."))
        }
        r.bedtimeSpread?.takeIf { it >= 60 }?.let {
            add(AdviceItem("⏰", "Tes heures de coucher varient d'environ ${Sleep.format(it)}. Une heure de lever régulière, même le week-end, stabilise le reste."))
        }
        r.typicalAwakening?.let { m ->
            if (r.awakeningCount >= 3) add(AdviceItem("🌌", "Tes réveils reviennent surtout vers ${Reports.formatTime(m)}. Ne regarde pas l'heure, et si tu ne te rendors pas en 20 minutes, lève-toi un moment dans une lumière douce."))
        }
        r.reasons.firstOrNull()?.let { (reason, n) ->
            val tip = when (reason) {
                "Toilettes" -> "Réduis les boissons deux heures avant le coucher."
                "Chaleur" -> "Une chambre fraîche (autour de 18 °C) et une literie légère aident."
                "Froid" -> "Une couche de plus, des chaussettes ou une bouillotte aux pieds."
                "Bruit" -> "Des bouchons d'oreille ou un bruit blanc peuvent aider."
                "Anxiété" -> "Écris tes soucis avant de te coucher (« Vider ma tête ») et respire lentement."
                "Cauchemar" -> "Un rituel apaisant avant de dormir ; si ça se répète, parles-en à ton médecin."
                "Douleur" -> "Cherche une position qui soulage, et parles-en à ton médecin si elle te réveille souvent."
                "Soif" -> "Garde un verre d'eau près du lit, sans trop boire avant."
                else -> null
            }
            if (tip != null && n >= 2) add(AdviceItem("💡", "Ce qui te réveille le plus : ${reason.lowercase()} ($n fois). $tip"))
        }
        r.tags.firstOrNull { it.first == "Caféine" }?.let { add(AdviceItem("☕", "Tu as noté de la caféine ${it.second} fois : pas de café ni de thé fort après 14 h peut changer tes nuits.")) }
        r.tags.firstOrNull { it.first == "Écrans avant de dormir" }?.let { add(AdviceItem("📱", "Tu as noté des écrans avant de dormir ${it.second} fois : essaie de les couper une heure avant.")) }
    }.take(5)
}
