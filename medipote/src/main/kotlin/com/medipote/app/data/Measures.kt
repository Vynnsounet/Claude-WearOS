package com.medipote.app.data

import java.time.LocalDateTime
import kotlinx.serialization.Serializable

/** Kinds of health measurements; each one can be shown or hidden in the settings. */
@Serializable
enum class MeasureType(
    val label: String,
    val unit: String,
    /** Decimals shown and typed. */
    val decimals: Int,
    /** Plausible input range, to catch typos. */
    val min: Double,
    val max: Double,
    val emoji: String,
) {
    BLOOD_PRESSURE("Tension", "mmHg", 0, 40.0, 260.0, "🩺"),
    HEART_RATE("Pouls", "bpm", 0, 25.0, 250.0, "❤️"),
    GLUCOSE("Glycémie", "g/L", 2, 0.2, 6.0, "🩸"),
    WEIGHT("Poids", "kg", 1, 2.0, 400.0, "⚖️"),
    TEMPERATURE("Température", "°C", 1, 33.0, 43.0, "🌡️"),
    OXYGEN("Saturation en oxygène", "%", 0, 50.0, 100.0, "🫁");

    fun format(v: Double): String = "%.${decimals}f".format(java.util.Locale.FRANCE, v)
}

/**
 * One measurement. For blood pressure, [value] is the systolic pressure, [value2] the diastolic one
 * and [pulse] the heart rate if the monitor gave it.
 */
@Serializable
data class Measurement(
    val id: String,
    val type: MeasureType,
    @Serializable(with = LocalDateTimeSerializer::class) val at: LocalDateTime,
    val value: Double,
    val value2: Double? = null,
    val pulse: Int? = null,
    /** For glucose: à jeun, après un repas… */
    val context: String = "",
    val notes: String = "",
) {
    val display: String
        get() = when (type) {
            MeasureType.BLOOD_PRESSURE -> "${value.toInt()}/${value2?.toInt() ?: "?"}"
            else -> type.format(value)
        }
}
