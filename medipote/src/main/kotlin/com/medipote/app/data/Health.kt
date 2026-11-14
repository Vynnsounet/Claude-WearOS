package com.medipote.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

// ─────────────────────────── Contraceptive pill ───────────────────────────

/** How a pill pack is organised: active pills, then a break or placebo pills. */
enum class PillRegimen(val label: String, val activeDays: Int, val breakDays: Int, val placebo: Boolean) {
    R21_7("21 jours + 7 jours de pause", 21, 7, false),
    R24_4("24 jours + 4 jours de pause", 24, 4, false),
    R21_7_PLACEBO("21 actifs + 7 placebos", 21, 7, true),
    R24_4_PLACEBO("24 actifs + 4 placebos", 24, 4, true),
    CONTINUOUS("28 jours en continu", 28, 0, false);

    companion object {
        fun of(pack: PillPack): PillRegimen =
            entries.firstOrNull { it.activeDays == pack.activeDays && it.breakDays == pack.breakDays && it.placebo == pack.placebo }
                ?: R21_7
    }
}

@Serializable
data class PillPack(
    val activeDays: Int = 21,
    val breakDays: Int = 7,
    /** True when the break is made of placebo pills that are still taken every day. */
    val placebo: Boolean = false,
    /** Day the first pill of a pack was taken; later packs follow on without gaps. */
    @Serializable(with = LocalDateSerializer::class) val packStart: LocalDate,
) {
    val cycleLength: Int get() = activeDays + breakDays

    /** 0-based position in the current pack, or null before the first pack. */
    fun position(date: LocalDate): Int? {
        val days = ChronoUnit.DAYS.between(packStart, date)
        return if (days < 0) null else Math.floorMod(days, cycleLength.toLong()).toInt()
    }

    fun currentPackStart(date: LocalDate): LocalDate {
        val days = ChronoUnit.DAYS.between(packStart, date).coerceAtLeast(0)
        return packStart.plusDays(days / cycleLength * cycleLength)
    }

    fun isBreak(date: LocalDate): Boolean = position(date)?.let { it >= activeDays } ?: false

    /** A pill is due: active day, or placebo day. */
    fun mustTake(date: LocalDate): Boolean = position(date)?.let { it < activeDays || placebo } ?: false
}

// ─────────────────────────── Water ───────────────────────────

@Serializable
data class WaterSettings(
    val enabled: Boolean = false,
    val goalGlasses: Int = 8,
    val glassMl: Int = 250,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 21 * 60,
    val intervalMinutes: Int = 120,
) {
    /** Minutes of the day at which to remind. */
    val reminderMinutes: List<Int>
        get() = if (intervalMinutes <= 0 || endMinute < startMinute) emptyList()
        else (startMinute..endMinute step intervalMinutes).toList()
}

// ─────────────────────────── Journal ───────────────────────────

@Serializable
enum class Flow(val label: String) { SPOTTING("Spotting"), LIGHT("Léger"), MEDIUM("Moyen"), HEAVY("Abondant") }

@Serializable
enum class Intercourse(val label: String) { PROTECTED("Protégé"), UNPROTECTED("Non protégé") }

/** Everything noted about one day. Scores go from 1 (very bad) to 5 (very good). */
@Serializable
data class DayEntry(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val physical: Int? = null,
    val mental: Int? = null,
    val physicalSymptoms: List<String> = emptyList(),
    val mentalSymptoms: List<String> = emptyList(),
    /** An illness that day, e.g. "Grippe". */
    val illness: String = "",
    val notes: String = "",
    val flow: Flow? = null,
    val cycleSymptoms: List<String> = emptyList(),
    val intercourse: Intercourse? = null,
    val waterGlasses: Int = 0,
    /** Glasses already rewarded today, so removing and re-adding a glass doesn't pay twice. */
    val waterRewarded: Int = 0,
) {
    val hasJournal: Boolean
        get() = physical != null || mental != null || physicalSymptoms.isNotEmpty() || mentalSymptoms.isNotEmpty() ||
            illness.isNotBlank() || notes.isNotBlank()

    val hasCycleData: Boolean get() = flow != null || cycleSymptoms.isNotEmpty() || intercourse != null

    val isEmpty: Boolean get() = !hasJournal && !hasCycleData && waterGlasses == 0 && waterRewarded == 0
}

// ─────────────────────────── Appointments ───────────────────────────

@Serializable
data class Appointment(
    val id: String,
    /** Specialty or reason, e.g. "Dentiste". */
    val title: String,
    val doctor: String = "",
    val location: String = "",
    @Serializable(with = LocalDateTimeSerializer::class) val dateTime: LocalDateTime,
    val notes: String = "",
    /** Reminders, in minutes before the appointment. */
    val reminderMinutes: List<Int> = listOf(24 * 60, 60),
)

// ─────────────────────────── Health documents ───────────────────────────

@Serializable
enum class DocCategory(val label: String) {
    PRESCRIPTION("Ordonnance"),
    LAB("Analyses"),
    IMAGING("Imagerie"),
    REPORT("Compte-rendu"),
    VACCINE("Vaccination"),
    OTHER("Autre"),
}

@Serializable
data class HealthDocument(
    val id: String,
    val title: String,
    val category: DocCategory,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    /** File name inside the app's private "documents" folder. */
    val fileName: String,
    val mimeType: String,
    val notes: String = "",
) {
    val isPdf: Boolean get() = mimeType == "application/pdf"
}

// ─────────────────────────── As-needed intakes ───────────────────────────

/** One intake of an "as needed" medication, with the context the user chose to note. */
@Serializable
data class PrnIntake(
    val id: String,
    val treatmentId: String,
    @Serializable(with = LocalDateTimeSerializer::class) val takenAt: LocalDateTime,
    val quantity: Double = 1.0,
    /** Why it was taken, e.g. "Mal de tête". */
    val reason: String = "",
    /** How bad it was before taking it, 1 (light) to 10 (unbearable). */
    val intensity: Int? = null,
    /** Where / in what situation, e.g. "Au travail", "Stress". */
    val contexts: List<String> = emptyList(),
    /** Did it help: 1 (no effect) to 5 (very well). */
    val relief: Int? = null,
    /** Ask an hour later whether it helped. */
    val askRelief: Boolean = false,
    val notes: String = "",
)
