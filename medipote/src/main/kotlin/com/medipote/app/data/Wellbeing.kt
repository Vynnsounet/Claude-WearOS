package com.medipote.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * "Ce qui m'accompagne": what a person lives with and what they want to keep an eye on. Nothing of
 * this shows in the app until [enabled]; every list is the user's own and can be changed freely.
 */
@Serializable
data class WellbeingSettings(
    val enabled: Boolean = false,
    val conditions: List<MyCondition> = emptyList(),
    /** Symptoms followed: the id of a listed one ("fatigue") or a name typed by the user. */
    val symptoms: List<String> = emptyList(),
    /** Small missions written by the user, offered with the others. */
    val customMissions: List<String> = emptyList(),
)

/** A condition of the user: a listed one ([presetId]) or one named freely. */
@Serializable
data class MyCondition(val name: String, val presetId: String? = null)

/** How strongly a symptom was felt on a day: 1 (a little) to 5 (very strongly). */
@Serializable
data class SymptomNote(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val symptom: String,
    val intensity: Int,
    val note: String = "",
)

/** The energy the user said they had on a day: 1 (very little) to 3 (almost usual). */
@Serializable
data class EnergyNote(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val level: Int,
)

/** A small victory: a mission or a step done. Points are only ever added, never taken away. */
@Serializable
data class SmallWin(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    /** A listed mission's id, the text of a mission of the user, or "step:<task>:<n>". */
    val key: String,
    val label: String,
    /** Points won for it (0 once the day's points are reached). */
    val points: Int,
)

@Serializable
data class SplitStep(val text: String, val done: Boolean = false)

/** A task cut into small steps. */
@Serializable
data class SplitTask(
    val id: String,
    val title: String,
    val steps: List<SplitStep>,
    @Serializable(with = LocalDateTimeSerializer::class) val createdAt: LocalDateTime,
) {
    val doneCount: Int get() = steps.count { it.done }
    val finished: Boolean get() = steps.isNotEmpty() && steps.all { it.done }
}
