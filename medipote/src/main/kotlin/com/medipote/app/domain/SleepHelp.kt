package com.medipote.app.domain

/** What an idea of the night help can open: a tool of the app. */
enum class SleepTool { BREATHE, RELAX, THOUGHTS }

/** One thing to try, short enough to read half asleep. */
data class SleepIdea(val id: String, val emoji: String, val title: String, val text: String, val tool: SleepTool? = null)

/** The two nights that are hard in different ways. */
enum class SleepTrouble(val id: String, val emoji: String, val label: String) {
    FALL("fall", "🌙", "Je n'arrive pas à m'endormir"),
    BACK("back", "🌃", "Je me suis réveillé·e et je n'arrive pas à me rendormir"),
}

/**
 * What the app knows by heart about hard nights: falling asleep, going back to sleep, and what helps
 * the days before. General wellbeing advice only: no medication, no supplement, no diagnosis, and it
 * says when to talk to a doctor.
 */
object SleepHelp {
    val FALL = listOf(
        SleepIdea("breathe", "🌬️", "Respirer lentement", "Inspire quatre secondes, expire six secondes. Quelques minutes suffisent.", SleepTool.BREATHE),
        SleepIdea("relax", "🧘", "Détendre le corps", "Une relaxation guidée, du front aux pieds, en trois minutes.", SleepTool.RELAX),
        SleepIdea("thoughts", "✍️", "Vider ma tête", "Écris ce qui tourne, pour le laisser sur le papier jusqu'à demain.", SleepTool.THOUGHTS),
        SleepIdea("soft", "🕯️", "Doux et frais", "Une lumière très douce, pas d'écran, des chaussettes chaudes aux pieds et une chambre plutôt fraîche."),
    )

    val BACK = listOf(
        SleepIdea("normal", "🤍", "Un réveil, c'est normal", "On se réveille plusieurs fois par nuit sans s'en souvenir d'habitude. Ça ne veut pas dire que la nuit est ratée."),
        SleepIdea("clock", "🙈", "Ne pas regarder l'heure", "Calculer le temps qui reste réveille encore plus. Tourne le réveil ou le téléphone."),
        SleepIdea("breathe", "🌬️", "Respirer lentement", "Inspire quatre secondes, expire six secondes, sans chercher à dormir.", SleepTool.BREATHE),
        SleepIdea("relax", "🧘", "Détendre le corps", "La relaxation guidée, du front aux pieds.", SleepTool.RELAX),
    )

    fun ideas(trouble: SleepTrouble): List<SleepIdea> = if (trouble == SleepTrouble.FALL) FALL else BACK

    val RULE_TITLE = "La règle des 20 minutes"
    val RULE_TEXT = "Si tu ne dors pas après environ 20 minutes, lève-toi. Va dans une autre pièce, avec une lumière douce, et fais " +
        "quelque chose de calme : lire quelques pages, une tisane sans caféine. Reviens te coucher quand le sommeil revient. " +
        "Évite les écrans. Ça paraît contraire à l'intuition, mais ça aide le lit à redevenir l'endroit où l'on dort."

    /** The guided relaxation, one step at a time. */
    val RELAX_STEPS = listOf(
        "Installe-toi, ferme les yeux. Laisse ton souffle ralentir.",
        "Ton front : détends-le, comme si on le lissait doucement.",
        "Ta mâchoire : écarte les dents, laisse la langue molle.",
        "Tes épaules : laisse-les descendre, loin des oreilles.",
        "Tes bras et tes mains : lourds, posés, sans effort.",
        "Ton ventre : laisse-le monter et descendre à chaque souffle.",
        "Tes jambes : lourdes, enfoncées dans le matelas.",
        "Tes pieds : chauds et lourds. Tu peux rester là, sans rien faire.",
    )

    /** For the days before: what makes nights easier. */
    val DAY_TIPS = listOf(
        SleepIdea("wake", "⏰", "Se lever à peu près à la même heure", "Même après une mauvaise nuit : c'est ce qui règle l'horloge du corps."),
        SleepIdea("light", "☀️", "La lumière du matin", "Dix minutes dehors, ou près d'une fenêtre, aident le sommeil du soir."),
        SleepIdea("caffeine", "☕", "La caféine plutôt le matin", "Évite le café et le thé fort après le début d'après-midi."),
        SleepIdea("nap", "😴", "Des siestes courtes", "20 à 30 minutes, avant 15 h : plus longues ou plus tard, elles gênent la nuit."),
        SleepIdea("evening", "🌙", "Un rituel du soir", "Lumières douces et écrans éteints environ une heure avant de dormir."),
        SleepIdea("bed", "🛏️", "Le lit sert à dormir", "Pas de travail ni d'écran au lit, pour que ton corps l'associe au sommeil."),
    )

    val CONSULT = listOf(
        "Une insomnie depuis plus de trois semaines, des ronflements forts avec des pauses de respiration, ou une grande fatigue en journée : parles-en à ton médecin.",
        "Certaines maladies (thyroïde, douleur, anxiété) et certains traitements perturbent le sommeil : en parler peut aider. Ne change jamais ton traitement de toi-même.",
    )

    const val NOTE = "Ce sont des repères généraux de bien-être : pas d'avis médical, ni de somnifère ou de complément conseillé ici."

    /** The key under which trying the help of a hard night is noted (once a day per trouble). */
    const val TRIED_KIND = "sleephelp"
}
