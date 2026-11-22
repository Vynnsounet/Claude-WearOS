package com.medipote.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Everything the app stores, saved as one JSON file in the app's private storage. */
@Serializable
data class AppState(
    val profile: Profile? = null,
    val treatments: List<Treatment> = emptyList(),
    val doseLogs: List<DoseLog> = emptyList(),
    val game: GameState = GameState(),
    val settings: Settings = Settings(),
    /** True once the first-run flow (profile, pet, treatments) is finished. */
    val onboarded: Boolean = false,
    /** Daily journal: wellbeing, symptoms, cycle notes and water, one entry per day. */
    val journal: List<DayEntry> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val documents: List<HealthDocument> = emptyList(),
    /** Intakes of "as needed" medications, newest first. */
    val prnIntakes: List<PrnIntake> = emptyList(),
    /** Logged nights, newest first, at most one per date. */
    val sleep: List<SleepNight> = emptyList(),
    /** What the user prepared for hard days (comfort box, safety plan, kept thoughts). */
    val support: SupportBox = SupportBox(),
    /** A gentle "how are you now?" notification the user asked for. */
    @Serializable(with = LocalDateTimeSerializer::class) val supportFollowUp: LocalDateTime? = null,
    /** Naps, newest first. */
    val naps: List<Nap> = emptyList(),
    /** A nap being timed: when "I'm falling asleep" was pressed. */
    @Serializable(with = LocalDateTimeSerializer::class) val napSince: LocalDateTime? = null,
    /** Health measurements (blood pressure, weight…), newest first. */
    val measurements: List<Measurement> = emptyList(),
    /** Period product worn right now, if any. */
    val productSession: ProductSession? = null,
    /** Period products worn before (latest first, a few months kept). */
    val productLog: List<ProductUse> = emptyList(),
    /** Dose reminders put off from their notification ("in 10 minutes"). */
    val snoozes: List<Snooze> = emptyList(),
    /** Symptoms felt, with their intensity, newest first. */
    val symptomLog: List<SymptomNote> = emptyList(),
    /** Small missions and steps done on hard days, newest first. */
    val wins: List<SmallWin> = emptyList(),
    /** Tasks cut into steps. */
    val tasks: List<SplitTask> = emptyList(),
    /** The energy announced today, if any. */
    val energy: EnergyNote? = null,
    /** What helped the person go back to sleep, in their own words: shown on the next hard night. */
    val sleepRecipe: List<String> = emptyList(),
    /** « Une idée, un souci ? »: what the person noted about the app, kept here and shared only if they choose. */
    val ideas: List<Idea> = emptyList(),
) {
    fun entryFor(date: LocalDate): DayEntry = journal.firstOrNull { it.date == date } ?: DayEntry(date)
}

@Serializable
enum class Sex(val label: String) { FEMALE("Femme"), MALE("Homme"), OTHER("Autre") }

@Serializable
data class Profile(
    val name: String,
    val age: Int? = null,
    val weightKg: Double? = null,
    val heightCm: Int? = null,
    val sex: Sex? = null,
    val medicalHistory: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val menstruating: Boolean = false,
    val cycle: CycleInfo? = null,
)

@Serializable
data class CycleInfo(
    /** Start dates of known periods, oldest first. The last one is the current cycle. */
    val periodStarts: List<@Serializable(with = LocalDateSerializer::class) LocalDate>,
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
)

@Serializable
data class Treatment(
    val id: String,
    /** Id in the built-in drug guide, or null for a medication typed in by hand. */
    val drugId: String? = null,
    val name: String,
    val strength: String = "",
    val unit: DoseUnit = DoseUnit.TABLET,
    val doses: List<DoseTime>,
    /** ISO days of week (1 = Monday … 7 = Sunday) on which the doses are due. */
    val daysOfWeek: Set<Int> = ALL_DAYS,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate? = null,
    val withFood: Boolean = false,
    val notes: String = "",
    /** When the treatment was added: doses planned before that moment don't count as missed. */
    @Serializable(with = LocalDateTimeSerializer::class) val createdAt: LocalDateTime? = null,
    /** Units left (tablets, sachets…), or null when the stock isn't tracked. */
    val stock: Double? = null,
    /** Units in one box, used by "I bought a box". */
    val packSize: Int? = null,
    /** Warn when fewer than this many days of treatment are left. */
    val lowStockDays: Int = 7,
    /** Set for a contraceptive pill: its pack rhythm decides which days are taken. */
    val pill: PillPack? = null,
    /** Taken "as needed" (si besoin): no schedule, no reminders, no points; each intake is logged. */
    val asNeeded: Boolean = false,
    /** Usual quantity of one as-needed intake. */
    val prnQuantity: Double = 1.0,
    /** Optional limits for as-needed use, from the prescription or the leaflet. */
    val maxPerDay: Int? = null,
    val minIntervalHours: Int? = null,
    /** As-needed stock warning, in units (days of treatment make no sense without a schedule). */
    val lowStockUnits: Int = 4,
    /** Taken every N days from the start date (1 = follow [daysOfWeek]). */
    val everyDays: Int = 1,
    /** Dose that changes over time (tapering): from each date, every dose time takes this quantity. */
    val steps: List<DoseStep> = emptyList(),
    /** Shown in the period medication list; null = automatic (common period painkillers only). */
    val forPeriod: Boolean? = null,
) {
    val displayName: String get() = if (strength.isBlank()) name else "$name $strength"

    fun isActiveOn(date: LocalDate): Boolean =
        !asNeeded && !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate)) &&
            (if (everyDays > 1) java.time.temporal.ChronoUnit.DAYS.between(startDate, date) % everyDays == 0L else date.dayOfWeek.value in daysOfWeek) &&
            (pill == null || pill.mustTake(date))

    /** Quantity of [dose] on [date], following the dose steps if there are any. */
    fun quantityOn(date: LocalDate, dose: DoseTime): Double =
        steps.filter { !it.from.isAfter(date) }.maxByOrNull { it.from }?.quantity ?: dose.quantity

    /** Average units used per day, taking the days of the week and pill breaks into account. */
    val dailyUse: Double
        get() {
            val today = LocalDate.now()
            val pillRatio = pill?.let { if (it.placebo) 1.0 else it.activeDays.toDouble() / it.cycleLength } ?: 1.0
            if (everyDays > 1) return doses.sumOf { quantityOn(today, it) } / everyDays * pillRatio
            // A week of doses, each on its own days, spread over seven days.
            val week = daysOfWeek.sumOf { d -> doses.filter { it.days == null || d in it.days }.sumOf { quantityOn(today, it) } }
            return week / 7.0 * pillRatio
        }

    /** Whole days of treatment left in stock, or null when not tracked. */
    val daysLeft: Int?
        get() {
            val s = stock ?: return null
            val use = dailyUse.takeIf { it > 0 } ?: return null
            return kotlin.math.floor(s / use).toInt()
        }

    val isLowStock: Boolean
        get() = if (asNeeded) stock?.let { it <= lowStockUnits } ?: false
        else daysLeft?.let { it <= lowStockDays } ?: false

    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
    }
}

/** From [from] on, each dose is [quantity] (e.g. a corticosteroid tapered week after week). */
@Serializable
data class DoseStep(
    @Serializable(with = LocalDateSerializer::class) val from: LocalDate,
    val quantity: Double,
)

@Serializable
data class DoseTime(
    /** Minutes after midnight, e.g. 8:30 → 510. */
    val minuteOfDay: Int,
    val quantity: Double = 1.0,
    /** When this dose time was added to an existing treatment: it isn't owed on earlier days. */
    @Serializable(with = LocalDateTimeSerializer::class) val since: LocalDateTime? = null,
    /**
     * The days of the week (1 = Monday … 7 = Sunday) this dose time is taken on; null = every day the
     * treatment is taken. It is what lets one treatment be taken at different times on different days
     * (10:00 on weekdays and 12:00 at weekends: two dose times with their own days).
     */
    val days: Set<Int>? = null,
    /** When [days] last changed, and what it was before: past days keep the days they had. */
    @Serializable(with = LocalDateSerializer::class) val daysFrom: LocalDate? = null,
    val daysBefore: Set<Int>? = null,
) {
    val timeLabel: String get() = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /** The days this dose time was taken on, on [date] (earlier days keep the days they had). */
    fun daysOn(date: LocalDate): Set<Int>? = if (daysFrom != null && date.isBefore(daysFrom)) daysBefore else days

    /** Whether this dose time is due on [date], treatment's own days aside. */
    fun appliesOn(date: LocalDate): Boolean = daysOn(date).let { it == null || date.dayOfWeek.value in it }

    /** "en semaine", "le week-end", "lun, mer, ven"; empty for every day. */
    val daysText: String get() = daysLabel(days)

    companion object {
        fun daysLabel(days: Set<Int>?): String = when {
            days == null || days.size == 7 -> ""
            days == setOf(1, 2, 3, 4, 5) -> "en semaine"
            days == setOf(6, 7) -> "le week-end"
            else -> days.sorted().joinToString(", ") {
                java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRANCE).trimEnd('.')
            }
        }
    }
}

@Serializable
enum class DoseUnit(val singular: String, val plural: String) {
    TABLET("comprimé", "comprimés"),
    CAPSULE("gélule", "gélules"),
    SACHET("sachet", "sachets"),
    ML("mL", "mL"),
    DROP("goutte", "gouttes"),
    PUFF("bouffée", "bouffées"),
    INJECTION("injection", "injections"),
    UNIT("unité", "unités"),
    PATCH("patch", "patchs"),
    SUPPOSITORY("suppositoire", "suppositoires"),
    APPLICATION("application", "applications"),
    VIAL("ampoule", "ampoules");

    fun format(quantity: Double): String {
        val q = formatQuantity(quantity)
        return "$q ${if (quantity > 1.0) plural else singular}"
    }

    companion object {
        fun formatQuantity(quantity: Double): String = when (quantity) {
            0.25 -> "¼"
            0.5 -> "½"
            0.75 -> "¾"
            else -> if (quantity % 1.0 == 0.0) quantity.toInt().toString()
            else quantity.toString().replace('.', ',')
        }
    }
}

/** How punctually a dose was given. */
@Serializable
enum class TakeStatus {
    ON_TIME,
    LATE,
    VERY_LATE,

    /** Marked as already taken more than an hour before the planned time. */
    EARLY,
}

@Serializable
data class DoseLog(
    val key: String,
    val treatmentId: String,
    @Serializable(with = LocalDateTimeSerializer::class) val scheduled: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class) val takenAt: LocalDateTime,
    val status: TakeStatus,
    val points: Int,
)

@Serializable
data class GameState(
    val points: Int = 0,
    val totalEarned: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    @Serializable(with = LocalDateSerializer::class) val lastCheckIn: LocalDate? = null,
    val petName: String = "Pilou",
    val owned: Set<String> = DEFAULT_OWNED,
    /** Slot name → item id. */
    val equipped: Map<String, String> = DEFAULT_EQUIPPED,
    val perfectDays: Set<@Serializable(with = LocalDateSerializer::class) LocalDate> = emptySet(),
    /** Days already rewarded for filling the journal / reaching the water goal. */
    val journalRewardDays: Set<@Serializable(with = LocalDateSerializer::class) LocalDate> = emptySet(),
    val waterRewardDays: Set<@Serializable(with = LocalDateSerializer::class) LocalDate> = emptySet(),
    /** Days the daily cuddle bonus was already given. */
    val cuddleDays: Set<@Serializable(with = LocalDateSerializer::class) LocalDate> = emptySet(),
    /** Doses given on time since the beginning: makes the companion grow (never decreases). */
    val growth: Int = 0,
    /** Badges earned: id → day. */
    val badges: Map<String, @Serializable(with = LocalDateSerializer::class) LocalDate> = emptyMap(),
    /** Weekly challenges already rewarded, "2026-11-09:sleep5". */
    val challengesDone: Set<String> = emptySet(),
    /** Rituals done (and rewarded), "2026-11-12:MORNING". */
    val ritualsDone: Set<String> = emptySet(),
    /** Day the weekly streak joker was last used. */
    @Serializable(with = LocalDateSerializer::class) val jokerUsedOn: LocalDate? = null,
    /** Monday of the last weekly recap opened. */
    @Serializable(with = LocalDateSerializer::class) val recapSeen: LocalDate? = null,
    /** Days the "night logged" bonus was already given. */
    val sleepRewardDays: Set<@Serializable(with = LocalDateSerializer::class) LocalDate> = emptySet(),
    /** Small rewards for taking care (a measure, a symptom noted, an appointment prepared…), each once: "2026-11-18:measure". */
    val careRewards: List<String> = emptyList(),
    /** What the companion wishes for this week (a shop item) and the Monday of that week. */
    val wishId: String? = null,
    @Serializable(with = LocalDateSerializer::class) val wishWeek: LocalDate? = null,
    /** The last day the surprise gift for gentle days was given. */
    @Serializable(with = LocalDateSerializer::class) val giftOn: LocalDate? = null,
    val history: List<PointEvent> = emptyList(),
) {
    companion object {
        val DEFAULT_OWNED = setOf("color_mint", "bg_room")
        val DEFAULT_EQUIPPED = mapOf("COLOR" to "color_mint", "BACKGROUND" to "bg_room")
    }
}

/** A dose reminder (doses planned at [doseTime]) to show again at [until]. */
@Serializable
data class Snooze(
    @Serializable(with = LocalDateTimeSerializer::class) val doseTime: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class) val until: LocalDateTime,
)

@Serializable
data class PointEvent(
    @Serializable(with = LocalDateTimeSerializer::class) val at: LocalDateTime,
    val amount: Int,
    val reason: String,
)

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

@Serializable
/** Accent colours; [seed] is the colour the tones are derived from (the first six are hand-tuned). */
enum class Accent(val label: String, val seed: Long) {
    MINT("Menthe", 0xFF1F7F69), OCEAN("Océan", 0xFF0B61A4), LAVENDER("Lavande", 0xFF6750A4),
    CORAL("Corail", 0xFF9C4234), AMBER("Ambre", 0xFF8B5000), ROSE("Rose", 0xFF984061),
    FOREST("Forêt", 0xFF2E7D32), SKY("Ciel", 0xFF0288D1), INDIGO("Indigo", 0xFF3949AB), PLUM("Prune", 0xFF8E24AA),
    CHERRY("Cerise", 0xFFC62828), TANGERINE("Mandarine", 0xFFEF6C00), SUNFLOWER("Tournesol", 0xFFF9A825),
    TEAL("Lagon", 0xFF00897B), SLATE("Ardoise", 0xFF546E7A), COCOA("Cacao", 0xFF6D4C41),
    BUBBLEGUM("Bonbon", 0xFFEC407A), OLIVE("Olive", 0xFF7C8B24),
}

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.MINT,
    val dynamicColor: Boolean = false,
    val water: WaterSettings = WaterSettings(),
    val sleep: SleepSettings = SleepSettings(),
    /** Measurement kinds shown in the app; none by default. */
    val measures: Set<MeasureType> = emptySet(),
    val backup: BackupSettings = BackupSettings(),
    val look: Look = Look(),
    val support: SupportSettings = SupportSettings(),
    val seasons: SeasonSettings = SeasonSettings(),
    val rituals: RitualSettings = RitualSettings(),
    val wellbeing: WellbeingSettings = WellbeingSettings(),
    val quiet: QuietHours = QuietHours(),
    /** The first-run tour has been shown (or skipped). */
    val tourSeen: Boolean = false,
    /** Cards of the hub pages the user chose to hide, "suivi:Cycle". */
    val hiddenCards: Set<String> = emptySet(),
    /** Cards pinned on the home screen (ids of hub cards, "soins:agenda"), four at most. */
    val pins: List<String> = emptyList(),
    /** What the person opens, counted on the phone only: it orders the cards and suggests tidying the unused ones. */
    val usage: UsageStats = UsageStats(),
    /** Order the cards of each page by use (once enough days have passed). */
    val cardsByUse: Boolean = true,
    /** Reminders put on pause for a while. */
    val pause: ReminderPause = ReminderPause(),
    /** The home screen says when the notes show something worth a professional's opinion. */
    val checkups: Boolean = true,
    /** Concerns set aside (key → ISO day): shown again after a week. */
    val dismissedChecks: Map<String, String> = emptyMap(),
    /** Sober mode: no points, streak, shop or badges on screen; the care stays the same. */
    val sober: Boolean = false,
    /** Reminder to change each period product, in minutes, when the user changed the default. */
    val productReminders: Map<PeriodProduct, Int> = emptyMap(),
    /** Ask for the phone's fingerprint, face or PIN to open the app. */
    val lockEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    /** Send a second notification when a dose is still not given 30 minutes after its time. */
    val followUpReminder: Boolean = true,
)

/** A thought about the app, with the screen the person was on. */
@Serializable
data class Idea(
    val id: String,
    val text: String,
    /** Where the person was (« Soins », « Réglages »…); empty when unknown. */
    val screen: String = "",
    @Serializable(with = LocalDateTimeSerializer::class) val at: LocalDateTime,
)

/**
 * Gentle reminders put to sleep until [until] (a trip, a bad week). The doses and the appointments keep
 * ringing unless the person explicitly adds [doses].
 */
@Serializable
data class ReminderPause(
    @Serializable(with = LocalDateTimeSerializer::class) val until: LocalDateTime? = null,
    /** Also silence the dose reminders: only on purpose, never the default. */
    val doses: Boolean = false,
)

/** How often each card of the hub pages was opened, on how many different days; nothing leaves the phone. */
@Serializable
data class UsageStats(
    val opens: Map<String, Int> = emptyMap(),
    /** Days on which at least one card was opened. */
    val days: Int = 0,
    @Serializable(with = LocalDateSerializer::class) val lastDay: LocalDate? = null,
    /** Cards the person chose to keep when the app offered to put them away. */
    val kept: Set<String> = emptySet(),
)

/**
 * Quiet hours: gentle reminders (water, stock, rituals, recap, "how did you sleep") wait for the end of
 * the night. Doses, appointments, snoozed reminders and the timers never wait.
 */
@Serializable
data class QuietHours(
    val enabled: Boolean = false,
    val startHour: Int = 22,
    val endHour: Int = 8,
) {
    /** Whether [time] falls in the quiet window (which can cross midnight). */
    fun contains(time: LocalDateTime): Boolean {
        if (!enabled) return false
        val m = time.hour * 60 + time.minute
        val start = startHour * 60
        val end = endHour * 60
        return if (start > end) m >= start || m < end else m in start until end
    }

    /** The moment the quiet window ends, for a [time] inside it. */
    fun endAfter(time: LocalDateTime): LocalDateTime {
        val end = time.toLocalDate().atTime(endHour, 0)
        return if (end.isAfter(time)) end else end.plusDays(1)
    }
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}
