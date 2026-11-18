package com.medipote.app.domain

import com.medipote.app.data.AppState
import com.medipote.app.data.Season
import com.medipote.app.data.GameState
import com.medipote.app.data.TakeStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

// ─────────────────────────── Badges ───────────────────────────

data class Badge(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val target: Int,
    /** Current value towards [target]. */
    val progress: (AppState) -> Int,
)

object Badges {
    private fun onTime(s: AppState) = maxOf(s.game.growth, s.doseLogs.count { it.status == TakeStatus.ON_TIME })
    private fun journalDays(s: AppState) = s.journal.count { it.hasJournal }

    /** Longest run of consecutive nights logged. */
    fun sleepRun(s: AppState): Int {
        val dates = s.sleep.map { it.date }.toSortedSet()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (d in dates) {
            run = if (previous != null && d == previous.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            previous = d
        }
        return best
    }

    val all: List<Badge> = listOf(
        Badge("first_dose", "💊", "Premier pas", "Noter ta première prise", 1) { it.doseLogs.size.coerceAtLeast(if (it.game.growth > 0) 1 else 0) },
        Badge("ontime_10", "⏰", "Pile à l'heure", "10 prises à l'heure", 10, ::onTime),
        Badge("ontime_100", "🕰️", "Horloge suisse", "100 prises à l'heure", 100, ::onTime),
        Badge("ontime_500", "🏅", "Maître du temps", "500 prises à l'heure", 500, ::onTime),
        Badge("perfect_7", "🌟", "Sept étoiles", "7 journées parfaites", 7) { it.game.perfectDays.size },
        Badge("streak_7", "🔥", "Une semaine", "Une série de 7 jours", 7) { it.game.bestStreak },
        Badge("streak_30", "🌸", "Un mois", "Une série de 30 jours", 30) { it.game.bestStreak },
        Badge("streak_100", "👑", "Cent jours", "Une série de 100 jours", 100) { it.game.bestStreak },
        Badge("journal_1", "📖", "Cher journal", "Écrire dans ton journal", 1, ::journalDays),
        Badge("journal_30", "✍️", "Plume fidèle", "30 jours de journal", 30, ::journalDays),
        Badge("reflection_10", "💭", "Belles pensées", "Écrire une note dans ton journal 10 jours", 10) { s -> s.journal.count { it.notes.isNotBlank() || it.reflection.isNotBlank() } },
        Badge("sleep_1", "🌙", "Bonne nuit", "Noter ta première nuit", 1) { it.sleep.size },
        Badge("sleep_7", "✨", "Marchand de sable", "7 nuits notées d'affilée", 7, ::sleepRun),
        Badge("sleep_30", "🛌", "Gardien des rêves", "30 nuits notées", 30) { it.sleep.size },
        Badge("water_10", "💧", "Source vive", "Objectif d'eau atteint 10 jours", 10) { it.game.waterRewardDays.size },
        Badge("ritual_10", "🌅", "Douce routine", "10 rituels du matin ou du soir", 10) { it.game.ritualsDone.size },
        Badge("challenges_4", "🎯", "Défis relevés", "Réussir 4 défis de la semaine", 4) { it.game.challengesDone.size },
        Badge("measure_1", "🩺", "Bien mesuré", "Noter une première mesure de santé", 1) { it.measurements.size },
        Badge("document_1", "📂", "Bien rangé", "Ajouter un document au carnet de santé", 1) { it.documents.size },
        Badge("wardrobe_5", "🎀", "Garde-robe", "Avoir 5 objets pour ton compagnon", 5) { s -> s.game.owned.count { id -> Shop.item(id)?.let { it.price > 0 } == true } },
        Badge("bloom", "🌺", "En pleine floraison", "Faire grandir ton compagnon jusqu'à la floraison", 1) { s -> if (Evolution.growth(s).stage == Stage.BLOOM) 1 else 0 },
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Exclusive item won with a badge, if any. */
    fun reward(badgeId: String) = Shop.items.firstOrNull { it.badge == badgeId }

    /** Awards every badge newly reached (and its item). Returns the new state and the badges won. */
    fun award(state: AppState, today: LocalDate): Pair<AppState, List<Badge>> {
        val won = all.filter { it.id !in state.game.badges && it.progress(state) >= it.target }
        if (won.isEmpty()) return state to emptyList()
        val items = won.mapNotNull { reward(it.id)?.id }
        val game = state.game.copy(badges = state.game.badges + won.associate { it.id to today }, owned = state.game.owned + items)
        return state.copy(game = game) to won
    }
}

// ─────────────────────────── Weekly challenges ───────────────────────────

data class Challenge(val id: String, val emoji: String, val title: String, val target: Int, val points: Int)

data class ChallengeStatus(val challenge: Challenge, val progress: Int, val done: Boolean) {
    val complete: Boolean get() = progress >= challenge.target
}

object Challenges {
    private val pool = listOf(
        Challenge("sleep5", "🌙", "Noter 5 nuits", 5, 30),
        Challenge("journal3", "📖", "Écrire 3 fois dans ton journal", 3, 30),
        Challenge("water3", "💧", "Atteindre ton objectif d'eau 3 jours", 3, 30),
        Challenge("complete4", "💊", "4 journées où tout est pris", 4, 40),
        Challenge("mood5", "😊", "Noter ton humeur 5 jours", 5, 25),
        Challenge("cuddle4", "🤗", "Câliner ton compagnon 4 jours", 4, 20),
        Challenge("notes3", "💭", "Écrire une note dans ton journal 3 jours", 3, 25),
        Challenge("ritual4", "✨", "Faire 4 rituels", 4, 30),
        Challenge("journal5", "📖", "Remplir ton journal 5 jours", 5, 35),
        Challenge("sleep6", "🌙", "Noter 6 nuits", 6, 40),
        Challenge("wins3", "💛", "3 petites victoires dans la semaine", 3, 30),
    )

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Three challenges for the week of [date], picked from what makes sense for this person. */
    fun of(state: AppState, date: LocalDate): List<Challenge> {
        val usable = pool.filter { c ->
            when (c.id) {
                "complete4" -> state.treatments.any { !it.asNeeded }
                "water3" -> true
                "wins3" -> Wellbeing.enabled(state)
                else -> true
            }
        }
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + date.get(IsoFields.WEEK_BASED_YEAR) * 53
        // Steps through the list by a number with no common factor with its size, so three different ones always come.
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val step = listOf(5, 3, 7, 1).first { gcd(it, usable.size) == 1 }
        return (0 until usable.size).map { usable[(week * 3 + it * step) % usable.size] }.distinct().take(3)
    }

    private fun progress(state: AppState, c: Challenge, from: LocalDate, to: LocalDate): Int {
        fun inWeek(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        val entries = state.journal.filter { inWeek(it.date) }
        return when (c.id) {
            "sleep5" -> state.sleep.count { inWeek(it.date) }
            "journal3", "journal5" -> entries.count { it.hasJournal }
            "sleep6" -> state.sleep.count { inWeek(it.date) }
            "wins3" -> state.wins.count { inWeek(it.date) }
            "water3" -> state.game.waterRewardDays.count(::inWeek)
            "complete4" -> {
                val logs = state.doseLogs.associateBy { it.key }
                generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.count { d ->
                    val occ = Schedule.occurrencesOn(state.treatments, d)
                    occ.isNotEmpty() && occ.all { it.key in logs }
                }
            }
            "mood5" -> entries.count { it.mental != null }
            "cuddle4" -> state.game.cuddleDays.count(::inWeek)
            "notes3" -> entries.count { it.notes.isNotBlank() || it.reflection.isNotBlank() }
            "ritual4" -> state.game.ritualsDone.count { key -> LocalDate.parse(key.substringBefore(':')).let(::inWeek) }
            else -> 0
        }
    }

    fun status(state: AppState, date: LocalDate): List<ChallengeStatus> {
        val from = weekStart(date)
        val to = from.plusDays(6)
        return of(state, date).map { c ->
            ChallengeStatus(c, progress(state, c, from, to).coerceAtMost(c.target), "$from:${c.id}" in state.game.challengesDone)
        }
    }

    /** Rewards challenges just completed this week. Returns the new state and the challenges won. */
    fun reward(state: AppState, now: LocalDateTime): Pair<AppState, List<Challenge>> {
        val from = weekStart(now.toLocalDate())
        val won = status(state, now.toLocalDate()).filter { it.complete && !it.done }.map { it.challenge }
        if (won.isEmpty()) return state to emptyList()
        var game = state.game.copy(challengesDone = state.game.challengesDone + won.map { "$from:${it.id}" })
        won.forEach { game = Game.addPoints(game, it.points, "Défi réussi : ${it.title.lowercase()}", now) }
        return state.copy(game = game) to won
    }
}

// ─────────────────────────── Phrase of the day ───────────────────────────

/** A small motivating, cosy or pleasant sentence, a different one each day. */
object DailyPhrases {
    val all = listOf(
        "Un pas après l'autre, c'est déjà avancer.",
        "Prendre soin de soi, c'est un beau métier à plein temps.",
        "Aujourd'hui mérite un thé chaud et un peu de douceur.",
        "Tu fais de ton mieux, et c'est largement suffisant.",
        "Une petite victoire reste une victoire.",
        "Respire. Tout n'a pas besoin d'être fait aujourd'hui.",
        "Le soleil revient toujours, même après les jours gris.",
        "Offre-toi la même douceur qu'à quelqu'un que tu aimes.",
        "Ton rythme est le bon rythme.",
        "Un verre d'eau, une grande inspiration, et c'est reparti.",
        "Les petits gestes d'aujourd'hui font les beaux jours de demain.",
        "Tu as le droit de te reposer sans culpabiliser.",
        "Une couverture, une bonne musique : le confort, ça soigne aussi.",
        "Chaque jour est une nouvelle page, écris-la tranquillement.",
        "Prendre son traitement, c'est se faire un cadeau.",
        "Même les fleurs ont besoin de temps pour pousser.",
        "Aujourd'hui, accorde-toi une pause qui fait du bien.",
        "Tu as plus de force que tu ne le crois.",
        "Un sourire, même petit, illumine la journée.",
        "Le calme aussi, c'est avancer.",
        "Ce que tu fais pour ta santé compte vraiment.",
        "Une journée à la fois, c'est la bonne mesure.",
        "Il y a toujours une petite chose pour laquelle dire merci.",
        "Ralentir n'est pas reculer.",
        "Ton corps fait un travail incroyable chaque jour.",
        "Une sieste, un plaid, un chat : la recette du bonheur simple.",
        "La régularité, c'est déjà une superbe habitude.",
        "La douceur est une force.",
        "Aujourd'hui, choisis une chose qui te fait plaisir.",
        "Tu n'as rien à prouver, juste à prendre soin de toi.",
        "Les bonnes habitudes se construisent un jour après l'autre.",
        "Ouvre la fenêtre et laisse entrer un peu d'air frais.",
        "Tu avances, même quand ça ne se voit pas.",
        "Un moment pour soi, c'est jamais du temps perdu.",
        "La patience est une belle amie.",
        "Écouter son corps, c'est déjà le soigner.",
        "Un bon livre et une boisson chaude : le soir parfait.",
        "Bravo pour tous ces petits efforts.",
        "Il fait toujours beau quelque part.",
        "Prends le temps de savourer les petites choses.",
        "Ton bien-être passe avant le reste.",
        "Les jours difficiles finissent aussi par passer.",
        "Une marche, même courte, fait du bien au cœur.",
        "Tu es en train de devenir la meilleure version de toi.",
        "Le repos fait partie du chemin.",
        "Laisse-toi porter par la douceur du moment.",
        "Une chose à la fois, et tout ira bien.",
        "Chaque prise, c'est un petit « je tiens à moi ».",
        "Aujourd'hui, sois ton propre soutien.",
        "Même un petit progrès mérite d'être fêté.",
        "Un bain chaud, une bougie : tu le mérites bien.",
        "Rien ne presse, tu as le temps.",
        "Ta santé est un jardin : tu l'arroses chaque jour.",
        "Le bonheur se cache dans les détails.",
        "Tu es exactement là où tu dois être.",
        "Un câlin, un appel, un message : le lien fait du bien.",
        "Doucement mais sûrement, comme une tortue sereine.",
        "Ce soir, couche-toi avec une pensée gentille pour toi.",
        "Les nuages passent, toi tu restes.",
        "Un petit plaisir par jour éloigne les soucis.",
        "Tu prends soin de toi, et ça se voit.",
        "Le meilleur moment pour souffler, c'est maintenant.",
        "Chaque matin est une nouvelle chance.",
        "Aujourd'hui, un peu d'indulgence envers toi-même.",
        "Un chocolat chaud règle beaucoup de choses.",
        "Ta constance est ta super-force.",
        "Profite de la lumière du jour, elle te veut du bien.",
        "Tout petit pas compte, vraiment tout.",
    )

    private val seasonal = mapOf(
        Season.SPRING to listOf(
            "Les premières fleurs rappellent que tout recommence.", "Ouvre la fenêtre : le printemps a un air de renouveau.",
            "Comme les bourgeons, tu as le droit d'y aller doucement.", "Un rayon de soleil sur le visage, c'est déjà beaucoup.",
            "Les jours rallongent, et toi aussi tu respires mieux.", "Un petit tour dehors, pour voir ce qui pousse.",
        ),
        Season.SUMMER to listOf(
            "Un verre d'eau fraîche, un coin d'ombre : l'été se savoure.", "Ralentir quand il fait chaud, c'est de la sagesse.",
            "Une longue soirée douce, et rien d'autre à faire.", "Le soleil est là : pense à t'hydrater et à te protéger.",
            "Les journées sont longues, profite de chaque petit moment.", "Une glace, un livre, et le temps qui s'étire.",
        ),
        Season.AUTUMN to listOf(
            "Une boisson chaude et un plaid : l'automne a du charme.", "Les feuilles tombent doucement : toi aussi, tu peux lâcher prise.",
            "Le temps ralentit, c'est une belle saison pour se recentrer.", "Une soupe, un pull moelleux : le confort, ça soigne.",
            "Les jours raccourcissent, c'est le moment de se faire un cocon.", "Une balade dans les feuilles, ça réchauffe le cœur.",
        ),
        Season.WINTER to listOf(
            "Un plaid, un thé, et rien de plus à prouver.", "L'hiver est fait pour ralentir et se ménager.",
            "Même les jours courts ont leur lumière.", "Au chaud, tu prends soin de toi : c'est déjà beaucoup.",
            "La nature se repose, tu en as le droit aussi.", "Une bougie, une couverture, et le monde peut attendre.",
        ),
    )

    /** One phrase per day: every third day follows the season (when seasons are on), the others stay general. */
    fun of(date: LocalDate, season: Season? = null): String {
        val day = date.toEpochDay()
        if (season != null && Math.floorMod(day, 3L) == 0L) {
            val list = seasonal.getValue(season)
            return list[Math.floorMod(day / 3, list.size.toLong()).toInt()]
        }
        return all[Math.floorMod(day, all.size.toLong()).toInt()]
    }
}

// ─────────────────────────── Rituals ───────────────────────────

enum class RitualKind(val label: String, val emoji: String) { MORNING("Rituel du matin", "🌅"), EVENING("Rituel du soir", "🌙") }

object Rituals {
    const val BONUS = 5

    fun key(date: LocalDate, kind: RitualKind) = "$date:${kind.name}"

    /** Which ritual fits the time of day, if any. */
    fun current(now: LocalDateTime): RitualKind? = when (now.hour) {
        in 5..11 -> RitualKind.MORNING
        in 18..23, in 0..1 -> RitualKind.EVENING
        else -> null
    }

    /** The day a ritual belongs to (after midnight, the evening ritual is still yesterday's). */
    fun day(now: LocalDateTime, kind: RitualKind): LocalDate =
        if (kind == RitualKind.EVENING && now.hour < 5) now.toLocalDate().minusDays(1) else now.toLocalDate()

    fun done(game: GameState, now: LocalDateTime, kind: RitualKind) = key(day(now, kind), kind) in game.ritualsDone

    /** Marks a ritual done; the first time each day earns a small bonus. */
    fun complete(state: AppState, kind: RitualKind, now: LocalDateTime): Pair<AppState, Int> {
        val key = key(day(now, kind), kind)
        if (key in state.game.ritualsDone) return state to 0
        val game = Game.addPoints(state.game.copy(ritualsDone = state.game.ritualsDone + key), BONUS, kind.label, now)
        return state.copy(game = game) to BONUS
    }
}

// ─────────────────────────── Weekly recap ───────────────────────────

data class WeeklyRecap(
    val from: LocalDate,
    val to: LocalDate,
    val dosesTaken: Int,
    val dosesPlanned: Int,
    val onTime: Int,
    val perfectDays: Int,
    val nights: Int,
    val averageSleep: Int?,
    val bestNight: Int?,
    val moodAverage: Float?,
    val bestDay: LocalDate?,
    val journalDays: Int,
    val waterGoalDays: Int,
    val badges: List<Badge>,
    val challenges: Int,
    val pointsEarned: Int,
    val petMessage: String,
    /** Hard days: days with a symptom noted, those with a strong one, and the one that weighed most. */
    val symptomDays: Int = 0,
    val strongSymptomDays: Int = 0,
    val topSymptom: Pair<String, Double>? = null,
    /** Small victories of the week, the days they happened on, and the points they paid. */
    val wins: Int = 0,
    val winDays: Int = 0,
    val winPoints: Int = 0,
) {
    val rate: Float? get() = if (dosesPlanned == 0) null else dosesTaken.toFloat() / dosesPlanned
}

object Recaps {
    /** The week to recap on [today]: from Sunday 18:00, this week; otherwise the previous one. */
    fun weekToShow(now: LocalDateTime): LocalDate {
        val monday = Challenges.weekStart(now.toLocalDate())
        return if (now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18) monday else monday.minusWeeks(1)
    }

    fun build(state: AppState, from: LocalDate, now: LocalDateTime): WeeklyRecap {
        val to = from.plusDays(6)
        fun inWeek(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        val logs = state.doseLogs.associateBy { it.key }
        var planned = 0
        var taken = 0
        var onTime = 0
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) && !it.isAfter(now.toLocalDate()) }.forEach { d ->
            Schedule.occurrencesOn(state.treatments, d).filter { !it.scheduled.isAfter(now) }.forEach { o ->
                planned++
                logs[o.key]?.let { taken++; if (it.status == TakeStatus.ON_TIME) onTime++ }
            }
        }
        val nights = state.sleep.filter { inWeek(it.date) }
        val entries = state.journal.filter { inWeek(it.date) }
        val moods = entries.mapNotNull { e -> e.mental?.let { e.date to it } }
        val badges = state.game.badges.filter { inWeek(it.value) }.keys.mapNotNull(Badges::byId)
        val points = state.game.history.filter { inWeek(it.at.toLocalDate()) && it.amount > 0 }.sumOf { it.amount }
        val rate = if (planned == 0) null else taken.toFloat() / planned
        val pet = state.game.petName
        val symptoms = if (Wellbeing.enabled(state)) state.symptomLog.filter { inWeek(it.date) } else emptyList()
        val weekWins = if (Wellbeing.enabled(state)) state.wins.filter { inWeek(it.date) } else emptyList()
        val strongDays = symptoms.filter { it.intensity >= Wellbeing.HEAVY }.map { it.date }.distinct().size
        val message = when {
            weekWins.size >= 3 -> "Tes petites victoires m'ont fait chaud au cœur cette semaine 💛"
            strongDays >= 3 -> "Certains jours ont été lourds. Tu as pris soin de toi comme tu pouvais, et c'est déjà beaucoup 💛"
            rate != null && rate >= 0.95f -> "Quelle semaine ! Grâce à toi je me sens au top. Merci 💚"
            nights.size >= 5 -> "Tu as pris soin de tes nuits cette semaine, ça me fait plaisir 🌙"
            entries.count { it.hasJournal } >= 4 -> "J'ai adoré lire ton journal cette semaine 📖"
            rate != null && rate < 0.6f -> "Semaine un peu compliquée ? Pas grave, on recommence ensemble lundi 💛"
            else -> "Merci pour cette semaine passée ensemble. On continue ? ✨"
        }
        return WeeklyRecap(
            from = from, to = to,
            dosesTaken = taken, dosesPlanned = planned, onTime = onTime,
            perfectDays = state.game.perfectDays.count(::inWeek),
            nights = nights.size,
            averageSleep = nights.takeIf { it.isNotEmpty() }?.map { it.asleepMinutes }?.average()?.roundToInt(),
            bestNight = nights.maxOfOrNull { it.asleepMinutes },
            moodAverage = moods.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.toFloat(),
            bestDay = moods.maxByOrNull { it.second }?.first,
            journalDays = entries.count { it.hasJournal },
            waterGoalDays = state.game.waterRewardDays.count(::inWeek),
            badges = badges,
            challenges = state.game.challengesDone.count { LocalDate.parse(it.substringBefore(':')) == from },
            pointsEarned = points,
            petMessage = "$pet : « $message »",
            symptomDays = symptoms.map { it.date }.distinct().size,
            strongSymptomDays = strongDays,
            topSymptom = symptoms.groupBy { it.symptom }.maxByOrNull { (_, v) -> v.sumOf { it.intensity } }?.let { (id, v) -> id to v.map { it.intensity }.average() },
            wins = weekWins.size,
            winDays = weekWins.map { it.date }.distinct().size,
            winPoints = weekWins.sumOf { it.points },
        )
    }

    /** Whether the recap of the last finished week (or this one, on Sunday evening) hasn't been opened yet. */
    fun pending(state: AppState, now: LocalDateTime): LocalDate? {
        val week = weekToShow(now)
        if (state.game.recapSeen != null && !state.game.recapSeen.isBefore(week)) return null
        // Only for people who already used the app that week.
        val used = state.doseLogs.any { !it.scheduled.toLocalDate().isBefore(week) && !it.scheduled.toLocalDate().isAfter(week.plusDays(6)) } ||
            state.journal.any { !it.date.isBefore(week) && !it.date.isAfter(week.plusDays(6)) } ||
            state.sleep.any { !it.date.isBefore(week) && !it.date.isAfter(week.plusDays(6)) }
        return if (used) week else null
    }
}

