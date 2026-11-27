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

/** One drink: when, how much, and whether it was a hot one (counted only if the person chose so). */
@Serializable
data class Drink(
    @Serializable(with = LocalDateTimeSerializer::class) val at: LocalDateTime,
    val ml: Int,
    val hot: Boolean = false,
)

@Serializable
data class WaterSettings(
    val enabled: Boolean = false,
    val goalGlasses: Int = 8,
    val glassMl: Int = 250,
    /** The goal in millilitres; 0 = not set yet, so it follows [goalGlasses] × [glassMl]. */
    val goalMl: Int = 0,
    /** Unsweetened hot drinks (tea, herbal tea) can be noted too and count in the total. */
    val countHotDrinks: Boolean = false,
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
    /** Glasses noted before 1.34, when water was counted in glasses: read as [WaterSettings.glassMl] each, then folded into [drinks]. */
    val waterGlasses: Int = 0,
    /** What was drunk, one line each: the total is their sum. */
    val drinks: List<Drink> = emptyList(),
    /** Glasses already rewarded today, so removing and re-adding a glass doesn't pay twice. */
    val waterRewarded: Int = 0,
    /** Answer to the question of the day. */
    val reflection: String = "",
) {
    val hasJournal: Boolean
        get() = physical != null || mental != null || physicalSymptoms.isNotEmpty() || mentalSymptoms.isNotEmpty() ||
            illness.isNotBlank() || notes.isNotBlank() || reflection.isNotBlank()

    val hasCycleData: Boolean get() = flow != null || cycleSymptoms.isNotEmpty() || intercourse != null

    val isEmpty: Boolean get() = !hasJournal && !hasCycleData && waterGlasses == 0 && waterRewarded == 0 && drinks.isEmpty()
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
    /** What the person wants to ask or say at this appointment. */
    val questions: List<String> = emptyList(),
    /** "I went": noted once the appointment has taken place. */
    val attended: Boolean = false,
    /** What was said and decided, written after the appointment. */
    val afterNotes: String = "",
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
    /** Prescriptions: last day it can be used at the pharmacy. */
    @Serializable(with = LocalDateSerializer::class) val validUntil: LocalDate? = null,
    /** Remind this many days before [validUntil] to get it renewed (null: no reminder). */
    val remindDays: Int? = 10,
    /** Prescriptions: the day the person renewed it (the reminders stop). */
    @Serializable(with = LocalDateSerializer::class) val renewedOn: LocalDate? = null,
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

// ─────────────────────────── Sleep ───────────────────────────

@Serializable
data class SleepSettings(
    /** Hours of sleep aimed for each night, in minutes. */
    val goalMinutes: Int = 8 * 60,
    /** A morning notification to log the night, until it is logged. */
    val reminder: Boolean = false,
    val reminderMinute: Int = 9 * 60,
    /** Naps count towards the daily sleep goal (never in the night's own figures). */
    val napsInGoal: Boolean = true,
    /** A soft reminder in the evening to get ready for sleep, with an idea waiting for a hard night. */
    val windDown: Boolean = false,
    val windDownMinute: Int = 21 * 60 + 30,
    /** « Ma soirée »: the factors the person put away (ids), so the list stays short. */
    val hiddenFactors: List<String> = emptyList(),
    /** Factors the person wrote themselves. */
    val customFactors: List<String> = emptyList(),
    /** Substances and sexual activity stay out of sight until the person turns them on. */
    val sensitiveFactors: Boolean = false,
)

/** Waking up during the night: when, for how long, and why if known. */
@Serializable
data class Awakening(
    @Serializable(with = LocalDateTimeSerializer::class) val at: LocalDateTime,
    val minutes: Int = 10,
    val reason: String = "",
)

/** One night, identified by the date of the morning it ends on. */
@Serializable
data class SleepNight(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    /** Lights off. */
    @Serializable(with = LocalDateTimeSerializer::class) val bedtime: LocalDateTime,
    /** Time it took to fall asleep. */
    val latencyMinutes: Int = 15,
    /** Final wake-up. */
    @Serializable(with = LocalDateTimeSerializer::class) val wakeTime: LocalDateTime,
    val awakenings: List<Awakening> = emptyList(),
    /** 1 (very bad) to 5 (excellent). */
    val quality: Int? = null,
    /** How rested on waking up, 1 (not at all) to 5 (very). */
    val rested: Int? = null,
    /** How pleasant the night felt (dreams, comfort), 1 to 5. */
    val pleasant: Int? = null,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    /** « Nuit blanche »: no sleep at all. Can be edited later if sleep finally came in the morning. */
    val noSleep: Boolean = false,
) {
    val inBedMinutes: Int get() = java.time.Duration.between(bedtime, wakeTime).toMinutes().toInt().coerceAtLeast(0)
    val awakeMinutes: Int get() = awakenings.sumOf { it.minutes }
    val asleepMinutes: Int get() = if (noSleep) 0 else (inBedMinutes - latencyMinutes - awakeMinutes).coerceAtLeast(0)

    /** Share of the time in bed spent asleep. */
    val efficiency: Float get() = if (inBedMinutes == 0 || noSleep) 0f else asleepMinutes.toFloat() / inBedMinutes
}

// ─────────────────────────── Seasons and festivals ───────────────────────────

@Serializable
enum class Season(val label: String, val emoji: String) {
    SPRING("Printemps", "🌸"), SUMMER("Été", "☀️"), AUTUMN("Automne", "🍂"), WINTER("Hiver", "❄️"),
}

/** A day the companion marks, quietly. Mother's and father's days are left out on purpose. */
@Serializable
enum class Festival(val label: String, val emoji: String) {
    NEW_YEAR("Nouvel An", "🎆"),
    VALENTINE("Saint-Valentin", "💝"),
    EASTER("Pâques", "🥚"),
    HALLOWEEN("Halloween", "🎃"),
    CHRISTMAS("Noël", "🎄"),
    PET_BIRTHDAY("Anniversaire du compagnon", "🎂"),
    USER_BIRTHDAY("Mon anniversaire", "🎈"),
}

@Serializable
data class SeasonSettings(
    /** The master switch: off means no seasonal decor, tint, phrases or festivals at all. */
    val enabled: Boolean = true,
    /** A very light tint of the app's backgrounds (the colour accent never changes). */
    val tint: Boolean = true,
    val festivals: Set<Festival> = Festival.entries.toSet(),
    /** The seasons of the southern hemisphere are the other way round. */
    val southern: Boolean = false,
    /** Day and month only matter; the year is ignored. */
    @Serializable(with = LocalDateSerializer::class) val birthday: LocalDate? = null,
    /** When the companion "was born": the first day of use, kept once known. */
    @Serializable(with = LocalDateSerializer::class) val petBirthday: LocalDate? = null,
)

// ─────────────────────────── Support ("Ça ne va pas") ───────────────────────────

/** Someone to reach when things are hard, chosen in a calm moment. */
@Serializable
data class TrustedContact(val name: String, val phone: String)

@Serializable
data class SupportSettings(
    val contacts: List<TrustedContact> = emptyList(),
    /** The user's doctor or therapist, if they want their number at hand. */
    val professionalName: String = "",
    val professionalPhone: String = "",
    /** A small heart in the top bar of the home screen. */
    val homeButton: Boolean = true,
    /** After a "very bad" mood, offer support, gently. */
    val suggestOnLowMood: Boolean = true,
    /** Slow vibrations guiding the breathing. */
    val vibrate: Boolean = true,
)

@Serializable
data class ThoughtNote(
    val id: String,
    @Serializable(with = LocalDateTimeSerializer::class) val at: LocalDateTime,
    val text: String,
)

/** What the user prepared for the hard days: all private, on the device. */
@Serializable
data class SupportBox(
    /** A message to oneself, written on a good day. */
    val messageToSelf: String = "",
    /** What helps: a song, a place, an activity. */
    val comfort: List<String> = emptyList(),
    // The safety plan.
    val signs: List<String> = emptyList(),
    val helps: List<String> = emptyList(),
    /** What matters to me. */
    val matters: String = "",
    /** Thoughts written down and kept (the others are erased). */
    val thoughts: List<ThoughtNote> = emptyList(),
)

/** How a nap left one feeling. */
@Serializable
enum class NapFeeling(val emoji: String, val label: String) {
    FRESH("😊", "En forme"), SAME("😐", "Pareil"), FOGGY("😵", "Dans le brouillard"),
}

/** A nap: its own record, apart from the nights, so the night's figures stay about the night. */
@Serializable
data class Nap(
    val id: String,
    @Serializable(with = LocalDateTimeSerializer::class) val start: LocalDateTime,
    val minutes: Int,
    val feeling: NapFeeling? = null,
    val notes: String = "",
) {
    val date: LocalDate get() = start.toLocalDate()
    val end: LocalDateTime get() = start.plusMinutes(minutes.toLong())
}

// ─────────────────────────── Backup ───────────────────────────

@Serializable
data class BackupSettings(
    /** A copy of everything, every week, in a folder the user picked. */
    val auto: Boolean = false,
    /** Storage Access Framework tree URI of that folder. */
    val folderUri: String? = null,
    val folderName: String = "",
    /** Automatic backups are protected by a password (kept on this device only). */
    val encrypted: Boolean = false,
    @Serializable(with = LocalDateTimeSerializer::class) val lastAuto: LocalDateTime? = null,
    @Serializable(with = LocalDateTimeSerializer::class) val lastManual: LocalDateTime? = null,
    val lastError: String? = null,
)

// ─────────────────────────── Rituals ───────────────────────────

/** Morning and evening routines: everything to note, in one short flow. */
@Serializable
data class RitualSettings(
    val morningReminder: Boolean = false,
    val morningMinute: Int = 8 * 60,
    val eveningReminder: Boolean = false,
    val eveningMinute: Int = 21 * 60 + 30,
    /** A notification on Sunday evening when the week's recap is ready. */
    val weeklyRecap: Boolean = true,
)

// ─────────────────────────── Period products ───────────────────────────

/**
 * Period products, with a default reminder to change them and the longest time they should be worn
 * (internal ones: toxic shock syndrome risk; always follow the product's own leaflet).
 */
@Serializable
enum class PeriodProduct(
    val label: String,
    val emoji: String,
    val defaultMinutes: Int,
    val maxMinutes: Int,
    val internal: Boolean,
    /** "ta serviette", "ton tampon"… */
    val yours: String,
) {
    PAD("Serviette", "🌸", 6 * 60, 8 * 60, false, "ta serviette"),
    WASHABLE_PAD("Serviette lavable", "🌿", 6 * 60, 8 * 60, false, "ta serviette lavable"),
    TAMPON("Tampon", "🔸", 4 * 60, 8 * 60, true, "ton tampon"),
    CUP("Coupe menstruelle", "🌷", 8 * 60, 12 * 60, true, "ta coupe"),
    DISC("Disque menstruel", "⭕", 8 * 60, 12 * 60, true, "ton disque"),
    UNDERWEAR("Culotte de règles", "🩲", 8 * 60, 12 * 60, false, "ta culotte de règles"),
}

/** The product worn right now, since when, and when to be reminded. */
@Serializable
data class ProductSession(
    val product: PeriodProduct,
    @Serializable(with = LocalDateTimeSerializer::class) val since: LocalDateTime,
    val remindMinutes: Int,
)

/** A product worn and changed (history). */
@Serializable
data class ProductUse(
    val product: PeriodProduct,
    @Serializable(with = LocalDateTimeSerializer::class) val start: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class) val end: LocalDateTime,
)


// ─────────────────────────── Lab results ───────────────────────────

/** One value from a blood test (or another analysis). [marker] is the id of a known one ("tsh") or a name typed by the person. */
@Serializable
data class LabResult(
    val id: String,
    val marker: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val value: Double,
    val unit: String,
    /** The reference range printed on the person's report, when they typed it; else the usual one is used. */
    val low: Double? = null,
    val high: Double? = null,
    val note: String = "",
)

/** A change of a treatment (strength, times), kept so that analyses can be read against it. */
@Serializable
data class DoseChange(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val text: String,
)

// ─────────────────────────── Regular check-ups ───────────────────────────

/** A vaccine received: [vaccine] is the id of one of the calendar (« dtp », « flu »…) or the name the person wrote. */
@Serializable
data class Vaccination(
    val id: String,
    val vaccine: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val note: String = "",
)

/** Something to do from time to time: a dentist visit, a yearly blood test. The person sets the rhythm. */
@Serializable
data class RegularCheckup(
    val id: String,
    val title: String,
    val everyMonths: Int,
    @Serializable(with = LocalDateSerializer::class) val lastDone: LocalDate? = null,
    val remind: Boolean = true,
)

// ─────────────────────────── Travel ───────────────────────────

/**
 * A trip across time zones: the phone changes clock on arrival, but the body does not. From [arrival], the doses stay at
 * home time (shifted by [offsetHours], destination minus home) and come back to the local clock [stepHours] a day; from
 * [returnDate], the same way back.
 */
@Serializable
data class TravelPlan(
    @Serializable(with = LocalDateSerializer::class) val arrival: LocalDate,
    val offsetHours: Int,
    val stepHours: Int = 1,
    @Serializable(with = LocalDateSerializer::class) val returnDate: LocalDate? = null,
)
