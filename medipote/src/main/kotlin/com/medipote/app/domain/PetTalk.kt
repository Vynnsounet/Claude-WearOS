package com.medipote.app.domain

import com.medipote.app.data.AppState
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** What the companion does when touched. Durations drive the animations. */
enum class PetReaction(val durationMs: Int) {
    NONE(0),

    /** Single tap: a happy hop. */
    JUMP(700),

    /** Several quick taps: it laughs and wiggles. */
    GIGGLE(1200),

    /** Too many taps: dizzy eyes and orbiting stars. */
    DIZZY(1800),

    /** Stroked sideways: eyes closed, hearts. */
    PETTED(1100),

    /** Long press: a big squishy hug. */
    HUG(1300),

    /** Tapped while unwell. */
    SNEEZE(1200),

    /** Tapped while asleep. */
    YAWN(1600),
}

/** The companion's lines: contextual, short, first person, addressing the user with "tu". */
object PetTalk {

    fun greeting(state: AppState, now: LocalDateTime): String {
        val user = state.profile?.name?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val hello = when (now.hour) {
            in 5..11 -> "Bonjour$user ! ☀️"
            in 12..17 -> "Coucou$user !"
            in 18..22 -> "Bonsoir$user !"
            else -> "Tu ne dors pas$user ? 🌙"
        }
        return dueLine(state, now)?.let { "$hello $it" } ?: hello
    }

    fun line(reaction: PetReaction, state: AppState, now: LocalDateTime, random: Random = Random.Default): String = when (reaction) {
        PetReaction.GIGGLE -> listOf("Hihi ! Ça chatouille !", "Arrête, je vais exploser de rire ! 😆", "Hihihi… encore !").random(random)
        PetReaction.DIZZY -> listOf("Wouah… tout tourne… 😵", "Doucement, la tête me tourne !").random(random)
        PetReaction.PETTED -> listOf("Mmmh… encore !", "J'adore les câlins ❤️", "Ronron…", "C'est tout doux…").random(random)
        PetReaction.HUG -> listOf("Un gros câlin ! 🤗", "Moi aussi je tiens à toi 💚").random(random)
        PetReaction.YAWN -> listOf("Zzz… encore cinq minutes…", "Hmm ? Il fait nuit, on dort… 🌙").random(random)
        PetReaction.SNEEZE -> dueName(state, now)?.let { "Atchoum ! $it m'aiderait à aller mieux…" }
            ?: "Atchoum ! Je me remets doucement…"
        PetReaction.JUMP, PetReaction.NONE -> chatter(state, now, random)
    }

    /** Something useful when there is something to say, otherwise a friendly word. */
    fun chatter(state: AppState, now: LocalDateTime, random: Random = Random.Default): String {
        dueLine(state, now)?.let { return it }
        val tips = buildList {
            val logs = state.doseLogs.associateBy { it.key }
            Schedule.itemsOn(state.treatments, logs, now.toLocalDate(), now)
                .firstOrNull { it.state == DoseState.UPCOMING }
                ?.let { add("Prochaine prise à ${it.occurrence.dose.timeLabel} : ${it.occurrence.treatment.name}.") }
            if (state.game.streak >= 2) add("Déjà ${state.game.streak} jours d'affilée, on fait une super équipe !")
            val water = state.settings.water
            val glasses = state.entryFor(now.toLocalDate()).waterGlasses
            if (glasses < water.goalGlasses && now.hour in 8..21) add("Un petit verre d'eau ? 💧 On en est à $glasses / ${water.goalGlasses}.")
            state.appointments
                .filter { it.dateTime.isAfter(now) && ChronoUnit.DAYS.between(now.toLocalDate(), it.dateTime.toLocalDate()) <= 2 }
                .minByOrNull { it.dateTime }
                ?.let {
                    val day = when (ChronoUnit.DAYS.between(now.toLocalDate(), it.dateTime.toLocalDate())) {
                        0L -> "aujourd'hui"
                        1L -> "demain"
                        else -> "après-demain"
                    }
                    add("N'oublie pas : ${it.title} $day !")
                }
            if (!state.entryFor(now.toLocalDate()).hasJournal) add("Tu me racontes ta journée dans le journal ?")
            val night = state.sleep.firstOrNull { it.date == now.toLocalDate() }
            if (night == null && now.hour in 5..13) add("Bien dormi ? Raconte-moi ta nuit dans Suivi › Sommeil 🌙")
            if (night != null && night.asleepMinutes < state.settings.sleep.goalMinutes - 60 && now.hour in 12..17) {
                add("Courte nuit… une petite sieste, ça te dit ? 😴")
            }
            Shop.items.filter { it.id !in state.game.owned && it.price in 1..state.game.points }.maxByOrNull { it.price }
                ?.let { add("Avec tes ${state.game.points} points… « ${it.name} », ça m'irait bien, non ? 👀") }
        }
        val friendly = listOf(
            "Coucou ! 👋", "Boop !", "Merci de prendre soin de toi… et de moi 💚",
            "Je grandis grâce à tes bonnes habitudes 🌱", "Tu me fais un câlin ? Glisse ton doigt sur moi !",
        )
        // Mostly useful tips, sometimes just a friendly word.
        return if (tips.isNotEmpty() && random.nextInt(3) != 0) tips.random(random) else friendly.random(random)
    }

    private fun dueName(state: AppState, now: LocalDateTime): String? {
        val logs = state.doseLogs.associateBy { it.key }
        return (Schedule.carriedOver(state.treatments, logs, now) + Schedule.itemsOn(state.treatments, logs, now.toLocalDate(), now))
            .firstOrNull { it.canGive }?.occurrence?.treatment?.name
    }

    private fun dueLine(state: AppState, now: LocalDateTime): String? =
        dueName(state, now)?.let { "C'est l'heure de $it ! Tu me le donnes ? 💊" }
}
