package com.medipote.app.data

import kotlinx.serialization.Serializable

/** Blocks of the home screen, which the user can reorder or hide. */
@Serializable
enum class HomeSection(val label: String, val emoji: String) {
    COMPANION("Compagnon", "🐾"),
    DOSES("Prises du jour", "💊"),
    AS_NEEDED("Si besoin", "🩹"),
    MEASURES("Mes mesures", "🩺"),
    WATER("Hydratation", "💧"),
    PROMPTS("Invitations du jour (nuit, journal)", "🌙"),
    APPOINTMENT("Prochain rendez-vous", "📅"),
}

/** Font of titles and big numbers (body text keeps the readable system font). */
@Serializable
enum class FontChoice(val label: String) { MODERN("Moderne"), ELEGANT("Élégante"), HANDWRITTEN("Manuscrite"), TYPEWRITER("Machine à écrire") }

@Serializable
enum class Corners(val label: String) { ROUNDED("Arrondis"), SOFT("Très ronds"), STRAIGHT("Plus droits") }

@Serializable
enum class Backdrop(val label: String) { PLAIN("Uni"), TINTED("Teinté de ta couleur") }

/** Everything that makes the app look like its user's. */
@Serializable
data class Look(
    /** Any colour picked by the user (ARGB); overrides the accent when set. */
    val customColor: Long? = null,
    val headingFont: FontChoice = FontChoice.MODERN,
    /** 0.85 to 1.3: text bigger or smaller than the phone's setting. */
    val textScale: Float = 1f,
    val corners: Corners = Corners.ROUNDED,
    val backdrop: Backdrop = Backdrop.PLAIN,
    /** Dark theme with a pure black background (OLED screens). */
    val pureBlack: Boolean = false,
    /** Shown next to the greeting. */
    val avatar: String = "👋",
    /** A sentence of one's own, shown on the home screen. */
    val motto: String = "",
    val homeOrder: List<HomeSection> = HomeSection.entries,
    val hiddenSections: Set<HomeSection> = emptySet(),
    /** Celebration animations (daily streak…). */
    val celebrations: Boolean = true,
) {
    /** Sections in the user's order, new ones (added by an update) at the end. */
    val visibleSections: List<HomeSection>
        get() = (homeOrder + HomeSection.entries.filter { it !in homeOrder }).distinct().filter { it !in hiddenSections }

    companion object {
        val AVATARS = listOf(
            "👋", "😊", "🌸", "🌻", "🌿", "🍀", "🌈", "⭐", "🌙", "☀️", "🔥", "💜",
            "🐱", "🐶", "🦊", "🐼", "🐸", "🦄", "🐝", "🦋", "🍓", "🍵", "🎧", "🎨",
        )
    }
}
