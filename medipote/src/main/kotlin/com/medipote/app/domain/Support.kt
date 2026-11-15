package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.DayEntry
import com.medipote.app.data.TrustedContact
import java.time.LocalDateTime

/** A number to call when it is too heavy. */
data class HelpLine(val number: String, val name: String, val detail: String)

enum class BreathPhase(val label: String) { INHALE("Inspire"), HOLD_IN("Retiens"), EXHALE("Expire"), HOLD_OUT("Pause") }

/** Where the breathing is: which phase, how far along it (0..1), seconds left in it. */
data class BreathState(val phase: BreathPhase, val progress: Float, val secondsLeft: Int)

/** A way of breathing, in seconds per phase. The calming one breathes out longer than in. */
data class BreathPattern(val id: String, val label: String, val detail: String, val inhale: Int, val holdIn: Int, val exhale: Int, val holdOut: Int) {
    val cycleMillis: Long get() = (inhale + holdIn + exhale + holdOut) * 1000L

    fun at(elapsedMillis: Long): BreathState {
        var t = Math.floorMod(elapsedMillis, cycleMillis)
        for ((phase, seconds) in listOf(BreathPhase.INHALE to inhale, BreathPhase.HOLD_IN to holdIn, BreathPhase.EXHALE to exhale, BreathPhase.HOLD_OUT to holdOut)) {
            val length = seconds * 1000L
            if (length == 0L) continue
            if (t < length) return BreathState(phase, t.toFloat() / length, ((length - t + 999) / 1000).toInt())
            t -= length
        }
        return BreathState(BreathPhase.INHALE, 0f, inhale)
    }
}

data class GroundingStep(val count: Int, val sense: String, val hint: String)

/** "Ça ne va pas": tools for the hard moments. Support, not care: it never replaces a doctor or a therapist. */
object Support {
    /** France. Free, and open day and night for the first. */
    val HELP_LINES = listOf(
        HelpLine("3114", "3114 · prévention du suicide", "Écoute jour et nuit, gratuite. On peut appeler aussi pour parler d'une personne proche."),
        HelpLine("15", "15 · SAMU", "Urgence médicale."),
        HelpLine("112", "112 · urgences", "Danger immédiat."),
    )

    val PATTERNS = listOf(
        BreathPattern("calm", "Apaisante", "Inspire 4 s, expire 6 s", 4, 0, 6, 0),
        BreathPattern("coherence", "Cohérence cardiaque", "Inspire 5 s, expire 5 s", 5, 0, 5, 0),
        BreathPattern("box", "Carrée", "4 s · 4 s · 4 s · 4 s", 4, 4, 4, 4),
    )

    val GROUNDING = listOf(
        GroundingStep(5, "choses que tu vois", "Regarde autour de toi, sans te presser."),
        GroundingStep(4, "choses que tu touches", "Ton pull, la table, le sol sous tes pieds…"),
        GroundingStep(3, "choses que tu entends", "Même les plus petits bruits."),
        GroundingStep(2, "choses que tu sens", "Une odeur, ou celle que tu aimes."),
        GroundingStep(1, "chose que tu goûtes", "Une gorgée d'eau, ou le goût dans ta bouche."),
    )

    val THROAT_TIPS = listOf(
        "Laisse tomber ta mâchoire : dents écartées, langue molle.",
        "Baisse les épaules, puis soupire longuement par la bouche.",
        "Bois une petite gorgée d'eau, lentement.",
        "Pose une main sur ta gorge ou ta poitrine : sens-la bouger quand tu respires.",
    )

    const val ASK_FOR_HELP = "Ça ne va pas, tu peux m'appeler ? J'ai besoin de parler."

    /** Digits and a leading "+", for the dialer. */
    fun cleanPhone(raw: String): String = raw.trim().let { s -> (if (s.startsWith("+")) "+" else "") + s.filter(Char::isDigit) }

    fun validPhone(raw: String): Boolean = cleanPhone(raw).count(Char::isDigit) in 3..15

    fun contacts(state: AppState): List<TrustedContact> = state.settings.support.contacts.filter { validPhone(it.phone) }

    /** The very worst mood: the one for which support is gently offered. */
    fun lowMood(entry: DayEntry): Boolean = entry.mental == 1

    fun nudge(state: AppState, date: java.time.LocalDate): Boolean = state.settings.support.suggestOnLowMood && lowMood(state.entryFor(date))

    // ── "Ask me again in a while" ──

    const val FOLLOW_UP_HOURS = 2L

    fun askAgain(state: AppState, now: LocalDateTime): AppState = state.copy(supportFollowUp = now.plusHours(FOLLOW_UP_HOURS).withSecond(0).withNano(0))

    fun clearFollowUp(state: AppState): AppState = if (state.supportFollowUp == null) state else state.copy(supportFollowUp = null)
}
