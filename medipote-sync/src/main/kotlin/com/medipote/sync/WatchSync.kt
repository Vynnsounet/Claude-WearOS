package com.medipote.sync

import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One dose of today or tomorrow, as the watch shows it. */
@Serializable
data class WatchDose(
    val key: String,
    val name: String,
    /** "1 comprimé", "2 gélules"… */
    val quantity: String,
    /** Planned time, ISO local date-time. */
    val time: String,
    val taken: Boolean = false,
    val withFood: Boolean = false,
    /** For a time window: when it closes, ISO local date-time. Null for a dose at a fixed time. */
    val until: String? = null,
) {
    val at: LocalDateTime get() = LocalDateTime.parse(time)

    /** When the dose stops being on time: the end of its window, else its time. */
    val endsAt: LocalDateTime get() = until?.let(LocalDateTime::parse) ?: at

    /** "08:00", or "10:00–12:00" for a window. */
    val label: String get() = "%02d:%02d".format(at.hour, at.minute) + (until?.let { "–%02d:%02d".format(endsAt.hour, endsAt.minute) } ?: "")
}

/** A reminder the watch rings by itself, even when the phone is far away. */
@Serializable
data class WatchReminder(
    val kind: String,
    /** When to ring, ISO local date-time. */
    val time: String,
    /** For doses: the planned time of the doses it is about. */
    val ref: String = "",
    val title: String = "",
    val text: String = "",
) {
    val at: LocalDateTime get() = LocalDateTime.parse(time)
    val key: String get() = "$kind|$time|$ref"

    companion object {
        const val DOSE = "DOSE"
        const val FOLLOW_UP = "FOLLOW_UP"
        const val WINDOW_END = "WINDOW_END"
        const val WATER = "WATER"
        const val PRODUCT = "PRODUCT"
    }
}

/** An as-needed medication that can be logged from the watch. */
@Serializable
data class WatchPrn(val id: String, val name: String, val quantity: String, val lastTakenAt: String? = null)

/** The period product being worn, with its timer. */
@Serializable
data class WatchProduct(val label: String, val emoji: String, val since: String, val changeAt: String, val maxAt: String)

/** A symptom the person follows, with today's strength (0 = not noted). */
@Serializable
data class WatchSymptom(val id: String, val emoji: String, val name: String, val intensity: Int = 0)

/** A small mission for a hard day, to tick on the watch. */
@Serializable
data class WatchMission(val id: String, val emoji: String, val text: String)

/** Everything the watch needs, sent by the phone each time something changes. */
@Serializable
data class WatchSnapshot(
    val version: Int = 1,
    val generatedAt: String,
    val petName: String = "",
    val userName: String = "",
    val streak: Int = 0,
    val points: Int = 0,
    val petLine: String = "",
    val doses: List<WatchDose> = emptyList(),
    val reminders: List<WatchReminder> = emptyList(),
    val waterEnabled: Boolean = false,
    /** The day [glasses] is about: the count starts again at midnight. */
    val waterDate: String = "",
    val glasses: Int = 0,
    val goal: Int = 8,
    val prn: List<WatchPrn> = emptyList(),
    val product: WatchProduct? = null,
    val lastHeartRate: Int? = null,
    val lastHeartRateAt: String? = null,
    val lastOxygen: Int? = null,
    val lastOxygenAt: String? = null,
    /** A nap being timed: when "I'm falling asleep" was pressed (phone or watch). */
    val napSince: String? = null,
    /** Minutes of nap noted today, to show at a glance. */
    val napMinutesToday: Int = 0,
    /** The symptoms followed (empty until the person turns them on). */
    val symptoms: List<WatchSymptom> = emptyList(),
    /** A few gentle missions for today (empty when the gentle day is off). */
    val missions: List<WatchMission> = emptyList(),
    /** Small victories noted today. */
    val winsToday: Int = 0,
) {
    val generated: LocalDateTime get() = LocalDateTime.parse(generatedAt)
}

/** Something done on the watch, for the phone to record. [id] makes it safe to receive twice. */
@Serializable
data class WatchAction(
    val id: String,
    val type: String,
    /** When it was done on the watch, ISO local date-time. */
    val at: String,
    val doseKeys: List<String> = emptyList(),
    val treatmentId: String = "",
    val value: Double = 0.0,
) {
    val time: LocalDateTime get() = LocalDateTime.parse(at)

    companion object {
        const val TAKE = "TAKE"
        const val WATER = "WATER"
        const val PRN = "PRN"
        const val HEART_RATE = "HEART_RATE"

        /** Oxygen saturation (%) typed on the watch: no public API gives apps the watch's own sensor. */
        const val OXYGEN = "OXYGEN"

        /** A nap that just ended: [value] minutes, ending at the time of the gesture. */
        const val NAP = "NAP"
        const val NAP_START = "NAP_START"
        const val NAP_STOP = "NAP_STOP"
        const val NAP_CANCEL = "NAP_CANCEL"
        const val PRODUCT_CHANGED = "PRODUCT_CHANGED"

        /** A symptom's strength for today: [treatmentId] is the symptom, [value] 1 to 5 (0 removes it). */
        const val SYMPTOM = "SYMPTOM"

        /** A mission done: [treatmentId] is the mission. */
        const val MISSION = "MISSION"
    }
}

/** Names and encoding shared by both apps. */
object WatchSync {
    /** Data item: the latest snapshot, phone to watch. */
    const val PATH_SNAPSHOT = "/medipote/snapshot"

    /** Data items: one per action, watch to phone; deleted by the phone once recorded. */
    const val PATH_ACTIONS = "/medipote/action"

    /** Message, watch to phone: "I'm here"; payload "1" when the watch can show reminders itself. */
    const val PATH_HELLO = "/medipote/hello"
    const val KEY_JSON = "json"
    const val KEY_PET = "pet"

    /** Declared by the watch app (res/values/wear.xml), looked up by the phone. */
    const val CAPABILITY_WATCH = "medipote_watch"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(snapshot: WatchSnapshot): String = json.encodeToString(WatchSnapshot.serializer(), snapshot)
    fun decodeSnapshot(text: String): WatchSnapshot? = runCatching { json.decodeFromString(WatchSnapshot.serializer(), text) }.getOrNull()
    fun encode(action: WatchAction): String = json.encodeToString(WatchAction.serializer(), action)
    fun decodeAction(text: String): WatchAction? = runCatching { json.decodeFromString(WatchAction.serializer(), text) }.getOrNull()
}

enum class WatchDoseState { TAKEN, UPCOMING, DUE, LATE }

/** The watch's own rules: what to ring, what to show, and how an action changes the screen at once. */
object WatchPlanner {
    /** A dose is "due" from 30 minutes before its time, like on the phone. */
    const val EARLY_MINUTES = 30L
    const val LATE_MINUTES = 30L

    fun state(dose: WatchDose, now: LocalDateTime): WatchDoseState = when {
        dose.taken -> WatchDoseState.TAKEN
        now.isBefore(dose.at.minusMinutes(EARLY_MINUTES)) -> WatchDoseState.UPCOMING
        now.isAfter(dose.endsAt.plusMinutes(LATE_MINUTES)) -> WatchDoseState.LATE
        else -> WatchDoseState.DUE
    }

    /** Doses to show for [date]: that day's, plus older ones still waiting. */
    fun dosesOn(snapshot: WatchSnapshot, date: LocalDate): List<WatchDose> =
        snapshot.doses.filter { it.at.toLocalDate() == date || (it.at.toLocalDate().isBefore(date) && !it.taken) }.sortedBy { it.time }

    /** The dose to put forward: the first one not taken that is due or late, else the next one today. */
    fun next(snapshot: WatchSnapshot, now: LocalDateTime): WatchDose? {
        val today = dosesOn(snapshot, now.toLocalDate()).filter { !it.taken }
        return today.firstOrNull { state(it, now) != WatchDoseState.UPCOMING } ?: today.firstOrNull()
    }

    fun glasses(snapshot: WatchSnapshot, date: LocalDate): Int = if (snapshot.waterDate == date.toString()) snapshot.glasses else 0

    /** The doses a dose reminder is about, still to take. */
    fun dosesOf(snapshot: WatchSnapshot, reminder: WatchReminder): List<WatchDose> =
        snapshot.doses.filter { it.time == reminder.ref && !it.taken }

    /** Whether a reminder still has something to say when it rings. */
    fun relevant(snapshot: WatchSnapshot, reminder: WatchReminder, now: LocalDateTime): Boolean = when (reminder.kind) {
        WatchReminder.DOSE, WatchReminder.FOLLOW_UP, WatchReminder.WINDOW_END -> dosesOf(snapshot, reminder).isNotEmpty()
        WatchReminder.WATER -> snapshot.waterEnabled && glasses(snapshot, now.toLocalDate()) < snapshot.goal
        WatchReminder.PRODUCT -> snapshot.product != null
        else -> false
    }

    /** Reminders to schedule on the watch: still ahead and still useful. */
    fun pending(snapshot: WatchSnapshot, now: LocalDateTime): List<WatchReminder> =
        snapshot.reminders.filter { it.at.isAfter(now) && (it.kind == WatchReminder.WATER || relevant(snapshot, it, now)) }
            .distinctBy { it.key }.sortedBy { it.time }

    /** Title and text of a reminder, from what is still to do. */
    fun message(snapshot: WatchSnapshot, reminder: WatchReminder, now: LocalDateTime): Pair<String, String> = when (reminder.kind) {
        WatchReminder.DOSE, WatchReminder.FOLLOW_UP, WatchReminder.WINDOW_END -> {
            val doses = dosesOf(snapshot, reminder)
            val list = doses.joinToString(", ") { "${it.name} (${it.quantity})" }
            val window = doses.firstOrNull { it.until != null }
            when {
                reminder.kind == WatchReminder.WINDOW_END -> "Il reste un peu de temps ⏳" to "$list, jusqu'à ${"%02d:%02d".format(window?.endsAt?.hour ?: 0, window?.endsAt?.minute ?: 0)}"
                reminder.kind == WatchReminder.FOLLOW_UP -> "Pas encore pris ?" to list
                window != null -> "Tu peux prendre 💊" to "$list, jusqu'à ${"%02d:%02d".format(window.endsAt.hour, window.endsAt.minute)}"
                else -> "C'est l'heure 💊" to list
            }
        }
        WatchReminder.WATER -> "Un verre d'eau ? 💧" to "${glasses(snapshot, now.toLocalDate())} / ${snapshot.goal} verres aujourd'hui"
        else -> reminder.title to reminder.text
    }

    /** Shows an action's effect on the watch right away, before the phone answers. */
    fun apply(snapshot: WatchSnapshot, action: WatchAction): WatchSnapshot = when (action.type) {
        WatchAction.TAKE -> snapshot.copy(doses = snapshot.doses.map { if (it.key in action.doseKeys) it.copy(taken = true) else it })
        WatchAction.WATER -> {
            val date = action.time.toLocalDate()
            snapshot.copy(waterDate = date.toString(), glasses = (glasses(snapshot, date) + action.value.toInt()).coerceAtLeast(0))
        }
        WatchAction.PRN -> snapshot.copy(prn = snapshot.prn.map { if (it.id == action.treatmentId) it.copy(lastTakenAt = action.at) else it })
        WatchAction.HEART_RATE -> snapshot.copy(lastHeartRate = action.value.toInt(), lastHeartRateAt = action.at)
        WatchAction.OXYGEN -> snapshot.copy(lastOxygen = action.value.toInt(), lastOxygenAt = action.at)
        WatchAction.NAP -> snapshot.copy(napMinutesToday = snapshot.napMinutesToday + action.value.toInt())
        WatchAction.NAP_START -> if (snapshot.napSince == null) snapshot.copy(napSince = action.at) else snapshot
        WatchAction.NAP_CANCEL -> snapshot.copy(napSince = null)
        WatchAction.NAP_STOP -> {
            val since = snapshot.napSince?.let(LocalDateTime::parse)
            val minutes = since?.let { java.time.Duration.between(it, action.time).toMinutes().toInt() } ?: 0
            // Counted here only when it is a real nap (the phone applies the same limits).
            snapshot.copy(napSince = null, napMinutesToday = snapshot.napMinutesToday + if (minutes in 1..240) minutes else 0)
        }
        WatchAction.PRODUCT_CHANGED -> snapshot.copy(reminders = snapshot.reminders.filterNot { it.kind == WatchReminder.PRODUCT })
        else -> snapshot
    }

    /** "il y a 5 min", "il y a 2 h", "hier"… */
    fun ago(from: LocalDateTime, now: LocalDateTime): String {
        val minutes = java.time.Duration.between(from, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 24 * 60 -> "il y a ${minutes / 60} h"
            else -> "il y a ${minutes / (24 * 60)} j"
        }
    }
}

/** Heart rate from the watch's sensor: a steady value out of noisy readings. */
object HeartRate {
    const val MIN_READINGS = 8
    const val MIN_BPM = 30
    const val MAX_BPM = 220

    fun valid(bpm: Float): Boolean = bpm >= MIN_BPM && bpm <= MAX_BPM

    /** The median of the last readings once there are enough of them and they agree; null while still settling. */
    fun steady(readings: List<Float>): Int? {
        val good = readings.filter(::valid).takeLast(MIN_READINGS)
        if (good.size < MIN_READINGS) return null
        val sorted = good.sorted()
        if (sorted.last() - sorted.first() > 12f) return null
        return Math.round((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f)
    }
}

/** Oxygen saturation typed on the watch (from a pulse oximeter or the watch's own health app). */
object Oxygen {
    const val MIN = 70
    const val MAX = 100
    const val DEFAULT = 96

    fun clamp(value: Int): Int = value.coerceIn(MIN, MAX)

    /** The same gentle wording as the phone's measurements; null when there is nothing to say. */
    fun advice(value: Int): String? = when {
        value < 92 -> "Basse (sous 92 %) : refais la mesure et contacte un médecin si elle reste basse."
        value < 95 -> "Un peu basse (sous 95 %)."
        else -> null
    }
}

/** Naps timed or noted on the watch. */
object NapTimer {
    val DURATIONS = listOf(20, 30, 45, 60)
    const val MAX_MINUTES = 240

    fun minutes(since: LocalDateTime, now: LocalDateTime): Int = java.time.Duration.between(since, now).toMinutes().toInt().coerceAtLeast(0)

    fun label(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${"%02d".format(minutes % 60)}"
    }
}

/** Calm breathing on the watch: in for 4 seconds, out for 6 (the longer breath out is what soothes). */
object Calm {
    const val INHALE_MS = 4_000L
    const val EXHALE_MS = 6_000L
    const val CYCLE_MS = INHALE_MS + EXHALE_MS
    const val HELP_NUMBER = "3114"

    data class State(val inhale: Boolean, val progress: Float, val secondsLeft: Int)

    fun at(elapsedMillis: Long): State {
        val t = Math.floorMod(elapsedMillis, CYCLE_MS)
        return if (t < INHALE_MS) State(true, t.toFloat() / INHALE_MS, ((INHALE_MS - t + 999) / 1000).toInt())
        else (t - INHALE_MS).let { State(false, it.toFloat() / EXHALE_MS, ((EXHALE_MS - it + 999) / 1000).toInt()) }
    }

    /** 0.5 when empty, 1 when full. */
    fun fill(state: State): Float = if (state.inhale) 0.5f + 0.5f * state.progress else 1f - 0.5f * state.progress
}
