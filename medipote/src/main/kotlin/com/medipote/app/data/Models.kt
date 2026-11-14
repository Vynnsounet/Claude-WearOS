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
) {
    /** Body-mass index, or null when weight or height is missing. */
    val bmi: Double?
        get() {
            val w = weightKg ?: return null
            val h = heightCm?.takeIf { it > 0 } ?: return null
            val m = h / 100.0
            return w / (m * m)
        }
}

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
) {
    val displayName: String get() = if (strength.isBlank()) name else "$name $strength"

    fun isActiveOn(date: LocalDate): Boolean =
        !asNeeded && !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate)) &&
            date.dayOfWeek.value in daysOfWeek && (pill == null || pill.mustTake(date))

    /** Average units used per day, taking the days of the week and pill breaks into account. */
    val dailyUse: Double
        get() {
            val perDay = doses.sumOf { it.quantity }
            val pillRatio = pill?.let { if (it.placebo) 1.0 else it.activeDays.toDouble() / it.cycleLength } ?: 1.0
            return perDay * daysOfWeek.size / 7.0 * pillRatio
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

@Serializable
data class DoseTime(
    /** Minutes after midnight, e.g. 8:30 → 510. */
    val minuteOfDay: Int,
    val quantity: Double = 1.0,
    /** When this dose time was added to an existing treatment: it isn't owed on earlier days. */
    @Serializable(with = LocalDateTimeSerializer::class) val since: LocalDateTime? = null,
) {
    val timeLabel: String get() = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
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
    /** Days the "night logged" bonus was already given. */
    val sleepRewardDays: Set<@Serializable(with = LocalDateSerializer::class) LocalDate> = emptySet(),
    val history: List<PointEvent> = emptyList(),
) {
    companion object {
        val DEFAULT_OWNED = setOf("color_mint", "bg_room")
        val DEFAULT_EQUIPPED = mapOf("COLOR" to "color_mint", "BACKGROUND" to "bg_room")
    }
}

@Serializable
data class PointEvent(
    @Serializable(with = LocalDateTimeSerializer::class) val at: LocalDateTime,
    val amount: Int,
    val reason: String,
)

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

@Serializable
enum class Accent(val label: String) {
    MINT("Menthe"), OCEAN("Océan"), LAVENDER("Lavande"), CORAL("Corail"), AMBER("Ambre"), ROSE("Rose")
}

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.MINT,
    val dynamicColor: Boolean = false,
    val water: WaterSettings = WaterSettings(),
    val sleep: SleepSettings = SleepSettings(),
    val notificationsEnabled: Boolean = true,
    /** Send a second notification when a dose is still not given 30 minutes after its time. */
    val followUpReminder: Boolean = true,
)

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
