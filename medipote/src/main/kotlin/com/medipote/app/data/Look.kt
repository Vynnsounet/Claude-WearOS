package com.medipote.app.data

import kotlinx.serialization.Serializable

/** Blocks of the home screen, which the user can reorder or hide. */
@Serializable
enum class HomeSection(val label: String, val emoji: String, val hint: String = "") {
    STATUS("Série, points et défis", "🔥"),
    PHRASE("Phrase du jour", "🌼", "Une petite phrase douce, différente chaque jour"),
    COMPANION("Compagnon", "🐾"),
    NEXT_DOSE("Prochaine prise, en grand", "⏰"),
    MOOD("Humeur du jour", "😊", "Se replie en une ligne une fois notée"),
    RITUAL("Rituel du matin / du soir", "✨", "Le matin et le soir seulement"),
    PERIOD("Protection pendant les règles", "🩸", "Pendant les règles ou quand un minuteur tourne"),
    DOSES("Prises du jour", "💊"),
    AS_NEEDED("Si besoin", "🩹", "Après une prise des dernières 24 h"),
    MEASURES("Mes mesures", "🩺"),
    WATER("Hydratation", "💧", "Si les rappels d'eau sont activés ou un verre noté"),
    PROMPTS("Invitations du jour (nuit, journal)", "🌙", "Nuit le matin, journal le soir"),
    APPOINTMENT("Prochain rendez-vous", "📅", "Dans les 3 jours"),
    CHALLENGES("Défis de la semaine", "🎯"),
    GARDEN("Mon jardin", "🌷"),
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
    /** No longer shown (replaced by the phrase of the day); kept so old saves still load. */
    val motto: String = "",
    val homeOrder: List<HomeSection> = HomeSection.entries,
    /** 1 = the companion comes right under the phrase of the day, the next dose after the alerts. */
    val layoutVersion: Int = 0,
    val hiddenSections: Set<HomeSection> = emptySet(),
    /** Celebration animations (daily streak…). */
    val celebrations: Boolean = true,
) {
    /** Every section in the user's order; one added by an update goes where it is by default. */
    val fullOrder: List<HomeSection>
        get() {
            val order = homeOrder.distinct().toMutableList()
            HomeSection.entries.forEachIndexed { i, section ->
                if (section in order) return@forEachIndexed
                val before = HomeSection.entries.take(i).lastOrNull { it in order }
                order.add(before?.let { order.indexOf(it) + 1 } ?: 0, section)
            }
            return order
        }

    /**
     * One-time update of a saved order (the same result on a fresh one): the companion goes right
     * under the status line and the phrase, then the next dose; everything else keeps its place.
     */
    fun migrated(): Look {
        if (layoutVersion >= LAYOUT_VERSION) return this
        val rest = fullOrder.filter { it != HomeSection.COMPANION && it != HomeSection.NEXT_DOSE }
        val top = rest.takeWhile { it == HomeSection.STATUS || it == HomeSection.PHRASE }
        val order = top + listOf(HomeSection.COMPANION, HomeSection.NEXT_DOSE) + rest.drop(top.size)
        return copy(homeOrder = order, layoutVersion = LAYOUT_VERSION)
    }

    val visibleSections: List<HomeSection>
        get() = fullOrder.filter { it !in hiddenSections }

    companion object {
        const val LAYOUT_VERSION = 1
        val AVATARS = listOf(
            "👋", "😊", "🌸", "🌻", "🌿", "🍀", "🌈", "⭐", "🌙", "☀️", "🔥", "💜",
            "🐱", "🐶", "🦊", "🐼", "🐸", "🦄", "🐝", "🦋", "🍓", "🍵", "🎧", "🎨",
        )
    }
}
