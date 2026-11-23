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
        // On a festival (never on a very hard day, see Seasons.ambiance) the companion says so.
        Seasons.ambiance(state, now)?.festival?.let { return Seasons.greeting(it, state.profile?.name.orEmpty()) }
        val user = state.profile?.name?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val hello = when (now.hour) {
            in 5..11 -> "Bonjour$user ! ☀️"
            in 12..17 -> "Coucou$user !"
            in 18..22 -> "Bonsoir$user !"
            else -> "Tu ne dors pas$user ? 🌙"
        }
        return dueLine(state, now)?.let { "$hello $it" } ?: reactionTo(state, now)?.let { "$hello $it" } ?: hello
    }

    /** What the companion says about the night or the journal just noted. */
    fun reactionTo(state: AppState, now: LocalDateTime): String? {
        val today = now.toLocalDate()
        val night = state.sleep.firstOrNull { it.date == today }
        val entry = state.entryFor(today)
        return when {
            Support.lowMood(entry) -> "Je suis là, tout près de toi 💚"
            now.hour in 5..13 && night != null && ((night.rested ?: 3) <= 2 || night.asleepMinutes < state.settings.sleep.goalMinutes - 90) ->
                "Nuit difficile… on y va doucement aujourd'hui 💛"
            now.hour in 5..13 && night != null && ((night.rested ?: 0) >= 4 || (night.quality ?: 0) >= 4) ->
                "Tu as bien dormi, ça se voit ! Je déborde d'énergie ✨"
            now.hour >= 17 && entry.reflection.isNotBlank() -> "Merci de m'avoir raconté ta journée 📖"
            now.hour >= 17 && (entry.mental ?: 3) <= 2 -> "Journée pas facile ? Je reste là, tout près 🤗"
            else -> null
        }
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
        // On a very hard day: no series, no points, no jokes. Just company.
        if (Support.lowMood(state.entryFor(now.toLocalDate()))) {
            return listOf("Je suis là avec toi.", "Pas besoin de faire quoi que ce soit. Je reste tout près.", "Respire doucement. Je suis là 💚").random(random)
        }
        dueLine(state, now)?.let { return it }
        val tips = buildList {
            val logs = state.doseLogs.associateBy { it.key }
            Schedule.itemsOn(state.treatments, logs, now.toLocalDate(), now)
                .firstOrNull { it.state == DoseState.UPCOMING }
                ?.let { add("Prochaine prise ${it.occurrence.dose.whenText} : ${it.occurrence.treatment.name}.") }
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
            reactionTo(state, now)?.let { add(it) }
            add(DailyPhrases.of(now.toLocalDate()))
            val night = state.sleep.firstOrNull { it.date == now.toLocalDate() }
            if (night == null && now.hour in 5..13) add("Bien dormi ? Raconte-moi ta nuit dans Suivi › Sommeil 🌙")
            val napToday = Naps.on(state, now.toLocalDate()).isNotEmpty()
            if (night != null && night.asleepMinutes < state.settings.sleep.goalMinutes - 60 && now.hour in 12..17 && !napToday) {
                add("Courte nuit… une petite sieste, ça te dit ? 😴")
            }
            if (napToday) add("Une petite sieste, ça fait du bien 😴")
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
