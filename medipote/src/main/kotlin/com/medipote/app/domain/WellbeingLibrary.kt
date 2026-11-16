package com.medipote.app.domain

/**
 * What the app knows by heart about symptoms and conditions. It lives in the app, written once, so
 * nothing about the user's health ever leaves the phone. It is general wellbeing information, never
 * a diagnosis or a treatment: it says to see a doctor when it matters and never touches a dose.
 */
data class SymptomInfo(
    val id: String,
    val emoji: String,
    val name: String,
    /** Gentle things that often help. */
    val tips: List<String>,
    /** When to talk to a doctor or call for help. */
    val consult: List<String>,
    /** Shown first when it is about the body image. */
    val kind: Boolean = false,
)

data class ConditionInfo(
    val id: String,
    val emoji: String,
    val name: String,
    val about: String,
    /** Ids of the symptoms usually followed with it. */
    val symptoms: List<String>,
    val tips: List<String>,
    val watch: List<String>,
)

/** A small thing to do on a hard day. [energy] is the least energy it takes (1 very little, 3 almost usual). */
data class Mission(
    val id: String,
    val emoji: String,
    val text: String,
    val energy: Int,
    val minutes: Int,
    /** Symptoms it suits; empty = any. */
    val symptoms: Set<String> = emptySet(),
)

/** How a task of a kind usually starts: tiny steps to pick from. */
data class TaskKind(val id: String, val emoji: String, val label: String, val steps: List<String>)

/** What gets in the way, and the step that eases it. */
data class Blocker(val id: String, val label: String, val step: String)

object WellbeingLibrary {
    val SYMPTOMS: List<SymptomInfo> = listOf(
        SymptomInfo(
            "fatigue", "😴", "Fatigue",
            tips = listOf(
                "Une journée à faible énergie n'est pas une journée ratée : choisis une seule chose importante, le reste peut attendre.",
                "Alterne de courts moments d'activité et de repos, avant d'être à bout.",
                "Un verre d'eau et une vraie pause, assis·e et sans écran, aident déjà parfois.",
                "Une sieste courte (20 à 30 minutes) fatigue moins qu'une longue et gêne moins la nuit.",
            ),
            consult = listOf(
                "Une fatigue qui s'installe, s'aggrave ou t'empêche de vivre comme d'habitude : parles-en à ton médecin.",
                "Fatigue avec essoufflement inhabituel, douleur dans la poitrine ou malaise : appelle le 15.",
            ),
        ),
        SymptomInfo(
            "anxiety", "😰", "Anxiété",
            tips = listOf(
                "Ralentis le souffle : quelques respirations lentes, l'expiration plus longue que l'inspiration.",
                "Nommer ce que tu ressens, à voix haute ou par écrit, apaise souvent.",
                "L'exercice 5-4-3-2-1 ramène l'attention sur ce qui t'entoure.",
                "Bouger doucement (marcher, s'étirer) aide à évacuer la tension. Un jour très anxieux, le café peut l'attiser.",
            ),
            consult = listOf(
                "Si l'anxiété revient souvent ou t'envahit, parles-en à ton médecin ou à un·e psychologue : des accompagnements existent.",
                "Idées noires : appelle le 3114, 24 h sur 24, gratuit.",
                "Palpitations avec douleur dans la poitrine ou malaise : 15.",
            ),
        ),
        SymptomInfo(
            "pain", "🤕", "Douleur",
            tips = listOf(
                "Note où et quand ça fait mal : c'est précieux pour ton médecin.",
                "La chaleur douce, un changement de position ou un mouvement lent soulagent parfois.",
                "Une respiration lente détend les muscles autour de la douleur.",
                "Respecte toujours les doses et les délais de tes médicaments « si besoin ».",
            ),
            consult = listOf(
                "Douleur forte, nouvelle ou qui ne passe pas : consulte.",
                "Douleur forte dans la poitrine, ou après un choc : appelle le 15.",
            ),
        ),
        SymptomInfo(
            "headache", "🤯", "Mal de tête",
            tips = listOf(
                "Bois un verre d'eau et repose-toi au calme, dans une lumière douce.",
                "Les repas sautés, le manque de sommeil et les écrans sont des déclencheurs fréquents : note ce qui précède.",
                "Une pause sans écran de quelques minutes peut aider.",
            ),
            consult = listOf(
                "Mal de tête soudain et très violent, avec nuque raide, fièvre, faiblesse, trouble de la parole ou de la vue : appelle le 15 tout de suite.",
                "Des maux de tête qui reviennent souvent : parles-en à ton médecin.",
            ),
        ),
        SymptomInfo(
            "nausea", "🤢", "Nausées",
            tips = listOf(
                "De petites gorgées de boisson fraîche, un peu d'air frais.",
                "Mange peu à la fois, lentement, quelque chose de simple.",
                "Évite les odeurs fortes et les repas gras.",
                "Si elles viennent après un nouveau médicament, demande conseil à ton médecin ou ton pharmacien, sans l'arrêter de toi-même.",
            ),
            consult = listOf(
                "Vomissements qui t'empêchent de boire depuis plus d'une journée, du sang, une forte douleur au ventre : consulte vite ou appelle le 15.",
            ),
        ),
        SymptomInfo(
            "fog", "🌫️", "Brouillard mental",
            tips = listOf(
                "Une seule chose à la fois, et écris ce que tu dois retenir.",
                "Une pause, un verre d'eau et un peu de lumière du jour aident parfois.",
                "Remets à plus tard les décisions importantes si tu le peux.",
                "Un sommeil et des repas réguliers soutiennent la concentration.",
            ),
            consult = listOf(
                "Confusion soudaine, difficulté à parler, faiblesse d'un côté du corps : appelle le 15 immédiatement.",
                "Un brouillard qui dure ou qui gêne ton quotidien : parles-en à ton médecin.",
            ),
        ),
        SymptomInfo(
            "lowmood", "😔", "Humeur basse",
            tips = listOf(
                "Tu n'as pas à aller bien. Une toute petite action suffit aujourd'hui.",
                "Parle à une personne de confiance, même pour dire seulement que ça ne va pas.",
                "Dix minutes à la lumière du jour, même derrière une fenêtre, comptent.",
                "Écrire ce qui pèse le rend un peu moins lourd.",
            ),
            consult = listOf(
                "Si ça dure depuis plus de deux semaines ou t'empêche de vivre : parles-en à ton médecin.",
                "Idées noires : appelle le 3114, 24 h sur 24, gratuit. L'écran « Ça ne va pas » est là aussi.",
            ),
        ),
        SymptomInfo(
            "sleep", "🌙", "Sommeil difficile",
            tips = listOf(
                "Garde une heure de lever régulière, même après une mauvaise nuit.",
                "Pose les écrans une heure avant de dormir, dans une chambre sombre et fraîche.",
                "Si tu ne dors pas, lève-toi un moment dans une lumière douce plutôt que d'attendre des heures au lit.",
            ),
            consult = listOf(
                "Un sommeil perturbé depuis plusieurs semaines : parles-en à ton médecin.",
                "Ronflements forts avec pauses de respiration pendant la nuit : parles-en aussi.",
            ),
        ),
        SymptomInfo(
            "breath", "💨", "Essoufflement",
            tips = listOf(
                "Assieds-toi, relâche les épaules, desserre ce qui serre.",
                "Respire lentement par le nez, souffle doucement par la bouche.",
                "Si tu as un plan d'action ou un traitement de secours, suis-les.",
            ),
            consult = listOf(
                "Essoufflement soudain, qui ne passe pas, avec douleur dans la poitrine, lèvres bleutées ou difficulté à parler : appelle le 15 ou le 112.",
            ),
        ),
        SymptomInfo(
            "dizzy", "😵", "Vertiges",
            tips = listOf(
                "Assieds-toi ou allonge-toi dès que ça tourne.",
                "Bois un verre d'eau et lève-toi lentement.",
                "Évite les escaliers et la conduite tant que ça tourne.",
            ),
            consult = listOf(
                "Vertige avec perte de connaissance, douleur dans la poitrine, trouble de la parole ou de la vue, faiblesse d'un côté : appelle le 15.",
                "Des vertiges qui reviennent : parles-en à ton médecin.",
            ),
        ),
        SymptomInfo(
            "joints", "🦴", "Douleurs articulaires",
            tips = listOf(
                "Des mouvements doux et réguliers valent mieux qu'un grand effort ou une immobilité longue.",
                "La chaleur détend, le froid apaise parfois une articulation gonflée.",
                "Alterne repos et activité, et fractionne les tâches.",
            ),
            consult = listOf(
                "Une articulation rouge, chaude, très gonflée, ou avec de la fièvre : consulte rapidement.",
            ),
        ),
        SymptomInfo(
            "digestion", "🍽️", "Digestion difficile",
            tips = listOf(
                "De petits repas, mangés lentement.",
                "Bois par petites gorgées et marche doucement après le repas.",
                "Note ce que tu as mangé quand ça va mal : ça aide à repérer ce qui gêne.",
            ),
            consult = listOf(
                "Du sang dans les selles, une perte de poids sans raison, une forte douleur au ventre ou de la fièvre : consulte.",
            ),
        ),
        SymptomInfo(
            "cold", "🥶", "Frilosité",
            tips = listOf(
                "Habille-toi en couches, garde les pieds et les mains au chaud.",
                "Une boisson chaude, une bouillotte, un petit mouvement pour te réchauffer.",
            ),
            consult = listOf(
                "Une frilosité nouvelle, avec fatigue, peau sèche ou prise de poids, peut être liée à la thyroïde : parles-en à ton médecin.",
            ),
        ),
        SymptomInfo(
            "irritability", "😤", "Irritabilité",
            tips = listOf(
                "Si tu peux, fais une pause avant de répondre : trois respirations lentes.",
                "Dis à tes proches que c'est un jour difficile, ce n'est pas contre eux.",
                "Bouger un peu aide à relâcher la tension.",
            ),
            consult = listOf(
                "Si c'est fréquent ou que ça abîme tes relations : parles-en à ton médecin.",
            ),
        ),
        SymptomInfo(
            "palpitations", "💓", "Palpitations",
            tips = listOf(
                "Assieds-toi, respire lentement et pose une main sur ta poitrine.",
                "Un verre d'eau, et moins de café ou d'excitants ce jour-là.",
                "Note quand ça arrive : c'est utile pour ton médecin.",
            ),
            consult = listOf(
                "Palpitations avec douleur dans la poitrine, malaise, essoufflement ou perte de connaissance : appelle le 15.",
                "Des palpitations répétées : parles-en à ton médecin.",
            ),
        ),
        SymptomInfo(
            "body", "🤍", "Mal avec mon corps",
            tips = listOf(
                "Ton corps fait de son mieux avec une maladie. Tu n'as pas à le mériter.",
                "Certaines maladies (thyroïde, SOPK…) et certains traitements rendent le poids plus difficile à maîtriser : ce n'est pas un manque de volonté.",
                "Évite de te peser tous les jours et de te comparer. Choisis un vêtement dans lequel tu es à l'aise aujourd'hui.",
                "Un geste gentil pour ton corps : une douche chaude, de la crème, un étirement, une couverture douce.",
                "Les régimes très restrictifs sont déconseillés sans avis médical. Un suivi médical ou diététique bienveillant peut aider.",
            ),
            consult = listOf(
                "Si tu te prives de manger, te fais vomir, ou si penser à ton corps t'envahit : parles-en à ton médecin.",
                "Si tu te sens très mal, appelle le 3114, 24 h sur 24, gratuit.",
            ),
            kind = true,
        ),
    )

    fun symptom(id: String): SymptomInfo? = SYMPTOMS.firstOrNull { it.id == id }

    val CONDITIONS: List<ConditionInfo> = listOf(
        ConditionInfo(
            "hashimoto", "🦋", "Hashimoto / hypothyroïdie",
            "Une maladie auto-immune où la thyroïde fonctionne au ralenti. Un traitement remplace l'hormone manquante, et l'équilibre se vérifie par des prises de sang.",
            listOf("fatigue", "cold", "fog", "lowmood", "anxiety", "body", "sleep"),
            listOf(
                "Prends ton traitement à heure régulière, et demande à ton médecin ou ton pharmacien comment le placer par rapport aux repas, au café, au calcium ou au fer.",
                "Fatigue, frilosité et grande envie de dormir peuvent revenir quand l'équilibre n'est pas bon : note-les, ça aide ton médecin.",
                "Le poids peut être plus difficile à maîtriser : ce n'est pas un manque de volonté.",
            ),
            listOf("Palpitations, tremblements, nervosité inhabituelle, ou au contraire grande lenteur et somnolence : consulte."),
        ),
        ConditionInfo(
            "diabetes", "🍬", "Diabète",
            "Le corps gère mal le sucre dans le sang. Le suivi (glycémie, traitement, alimentation, activité) aide à rester stable.",
            listOf("fatigue", "dizzy", "headache", "nausea", "fog"),
            listOf(
                "Suis les consignes de ton équipe soignante pour la glycémie et le traitement, et note tes mesures dans l'appli.",
                "Garde sur toi de quoi te resucrer si ton équipe te l'a conseillé, et préviens un proche de ce qu'il faut faire en cas de malaise.",
            ),
            listOf("Soif intense, envies très fréquentes d'uriner, vomissements, haleine fruitée ou grande somnolence : appelle le 15."),
        ),
        ConditionInfo(
            "asthma", "🌬️", "Asthme",
            "Les bronches se resserrent par moments et le souffle devient difficile. Un traitement de fond et un traitement de secours aident à le contrôler.",
            listOf("breath", "fatigue", "sleep"),
            listOf(
                "Garde ton traitement de secours à portée de main et suis ton plan d'action écrit.",
                "Repère tes déclencheurs : pollens, froid, effort, fumée, poussière.",
            ),
            listOf("Une crise qui ne cède pas avec le traitement de secours, des difficultés à parler ou des lèvres bleutées : appelle le 15 ou le 112."),
        ),
        ConditionInfo(
            "endometriosis", "🌸", "Endométriose",
            "Un tissu semblable à celui de l'utérus se développe ailleurs dans le corps et peut causer douleurs et fatigue, surtout autour des règles.",
            listOf("pain", "fatigue", "digestion", "nausea", "lowmood"),
            listOf(
                "Note tes douleurs et ton cycle : c'est précieux pour ton médecin.",
                "La chaleur, le repos et les mouvements doux soulagent souvent.",
            ),
            listOf("Une douleur très intense, une fièvre, ou des saignements anormalement abondants : consulte rapidement."),
        ),
        ConditionInfo(
            "pcos", "🌼", "SOPK",
            "Un déséquilibre hormonal qui peut perturber le cycle et rendre fatigue, humeur et poids plus difficiles à gérer.",
            listOf("fatigue", "body", "lowmood", "irritability", "sleep"),
            listOf(
                "Le poids peut être plus difficile à maîtriser à cause des hormones : ce n'est pas un manque de volonté.",
                "Un suivi médical ou diététique bienveillant peut aider, sans régime strict.",
            ),
            listOf("Des règles très irrégulières, ou une humeur très basse qui dure : parles-en à ton médecin."),
        ),
        ConditionInfo(
            "migraine", "⚡", "Migraine",
            "Des maux de tête intenses, souvent d'un seul côté, parfois avec nausées et sensibilité à la lumière ou au bruit.",
            listOf("headache", "nausea", "fog", "fatigue"),
            listOf(
                "Note ce qui précède une crise : sommeil, repas sautés, stress, écrans, hormones.",
                "Pendant une crise, un endroit calme et sombre aide souvent.",
            ),
            listOf("Un mal de tête soudain, le pire de ta vie, avec fièvre, nuque raide, faiblesse ou trouble de la parole : appelle le 15."),
        ),
        ConditionInfo(
            "anxiety_chronic", "🌿", "Anxiété chronique",
            "Une inquiétude intense et durable, avec des signes dans le corps : tension, souffle court, palpitations, sommeil perturbé.",
            listOf("anxiety", "sleep", "irritability", "fatigue", "palpitations", "fog"),
            listOf(
                "Des routines simples (sommeil, repas, mouvement) et la respiration lente aident au quotidien.",
                "Un accompagnement psychologique est efficace : n'attends pas d'aller très mal pour en parler.",
            ),
            listOf("Idées noires : appelle le 3114, 24 h sur 24. Palpitations avec douleur dans la poitrine : 15."),
        ),
        ConditionInfo(
            "depression", "🌧️", "Dépression",
            "Une tristesse profonde et durable, avec perte d'envie, fatigue et difficultés de concentration. Elle se soigne.",
            listOf("lowmood", "fatigue", "sleep", "fog", "body"),
            listOf(
                "Une toute petite action par jour compte déjà.",
                "Garde le lien avec une personne de confiance et avec ton médecin.",
            ),
            listOf("Idées noires : appelle le 3114, 24 h sur 24, gratuit."),
        ),
        ConditionInfo(
            "fibromyalgia", "🧩", "Fibromyalgie",
            "Des douleurs diffuses et durables, avec une grande fatigue, un sommeil peu réparateur et des difficultés de concentration.",
            listOf("pain", "fatigue", "fog", "sleep", "joints", "lowmood"),
            listOf(
                "Des mouvements doux et réguliers aident plus que le repos total.",
                "Fractionne tes efforts : alterne activité et pauses avant la fatigue.",
            ),
            listOf("Une douleur nouvelle et très différente de l'habituel, ou de la fièvre : consulte."),
        ),
        ConditionInfo(
            "hypertension", "❤️", "Hypertension",
            "La pression du sang dans les artères est trop haute. Elle se voit peu, d'où l'intérêt des mesures et du traitement régulier.",
            listOf("headache", "dizzy", "fatigue"),
            listOf(
                "Mesure ta tension comme ton médecin te l'a conseillé et note-la dans l'appli.",
                "Un traitement pris régulièrement protège le cœur, même quand on se sent bien.",
            ),
            listOf("Une tension très élevée avec mal de tête violent, douleur dans la poitrine, trouble de la vue ou de la parole : appelle le 15."),
        ),
        ConditionInfo(
            "ibd", "🍃", "Crohn / colite (MICI)",
            "Une inflammation chronique de l'intestin, avec des périodes calmes et des poussées.",
            listOf("digestion", "pain", "fatigue", "nausea", "joints"),
            listOf(
                "Note ce que tu manges et comment tu te sens pour repérer ce qui déclenche une poussée.",
                "Ne change pas ton traitement de toi-même, même quand ça va mieux.",
            ),
            listOf("Du sang dans les selles, de la fièvre, une forte douleur au ventre ou une déshydratation : consulte vite."),
        ),
        ConditionInfo(
            "arthritis", "🤲", "Polyarthrite / arthrose",
            "Une atteinte des articulations qui peut être douloureuse, raide et fatigante.",
            listOf("joints", "pain", "fatigue"),
            listOf(
                "Bouge un peu chaque jour : les articulations aiment le mouvement doux.",
                "La chaleur le matin aide à se dérouiller.",
            ),
            listOf("Une articulation rouge, chaude, très gonflée ou avec de la fièvre : consulte rapidement."),
        ),
        ConditionInfo(
            "chronic_fatigue", "🔋", "Fatigue chronique",
            "Une fatigue profonde qui ne passe pas avec le repos et s'aggrave parfois après un effort.",
            listOf("fatigue", "fog", "sleep", "pain"),
            listOf(
                "Fractionne tes efforts et garde de l'énergie en réserve : arrête-toi avant l'épuisement.",
                "Une journée de repos n'est pas une défaite.",
            ),
            listOf("Une aggravation brutale ou de nouveaux signes (fièvre, perte de poids, douleur forte) : consulte."),
        ),
        ConditionInfo(
            "ms", "🧠", "Sclérose en plaques",
            "Une maladie du système nerveux qui évolue par poussées ou de façon progressive, avec fatigue, troubles de l'équilibre ou de la sensibilité.",
            listOf("fatigue", "dizzy", "fog", "pain"),
            listOf(
                "La chaleur peut aggraver les symptômes : rafraîchis-toi, évite les efforts par forte chaleur.",
                "Respecte ton rythme et fractionne tes efforts.",
            ),
            listOf("De nouveaux signes qui durent plus de 24 heures (vue, force, sensibilité) : contacte rapidement ton équipe soignante."),
        ),
    )

    fun condition(id: String): ConditionInfo? = CONDITIONS.firstOrNull { it.id == id }

    val MISSIONS: List<Mission> = listOf(
        Mission("rest", "🛋️", "Me poser 5 minutes, sans rien faire", 1, 5),
        Mission("water", "🥤", "Boire un grand verre d'eau", 1, 1),
        Mission("tea", "🍵", "Me préparer une boisson chaude", 1, 5),
        Mission("breath", "🌬️", "Faire 5 respirations lentes", 1, 2, setOf("anxiety", "pain", "headache", "nausea", "lowmood", "dizzy", "breath", "palpitations", "irritability")),
        Mission("window", "🪟", "Ouvrir la fenêtre et respirer l'air frais", 1, 2, setOf("nausea", "fog", "headache", "lowmood", "dizzy")),
        Mission("music", "🎧", "Écouter une chanson qui me fait du bien", 1, 4, setOf("lowmood", "anxiety", "irritability", "pain")),
        Mission("plaid", "🧣", "Me mettre sous un plaid bien chaud", 1, 5, setOf("cold", "fatigue", "anxiety", "lowmood")),
        Mission("lie", "🛏️", "M'allonger 10 minutes, les yeux fermés", 1, 10, setOf("fatigue", "headache", "dizzy", "pain", "sleep")),
        Mission("meal", "🥣", "Manger quelque chose de simple", 1, 10, setOf("nausea", "fatigue", "fog", "dizzy")),
        Mission("heat", "♨️", "Poser une bouillotte là où ça fait mal", 1, 10, setOf("pain", "joints", "digestion")),
        Mission("dark", "🌑", "Rester au calme, lumière douce, sans écran", 1, 10, setOf("headache", "fog")),
        Mission("grounding", "🖐️", "Faire l'exercice 5-4-3-2-1", 1, 3, setOf("anxiety", "fog", "palpitations")),
        Mission("phone", "📵", "Poser mon téléphone 10 minutes", 1, 10, setOf("sleep", "anxiety", "irritability", "fog")),
        Mission("message", "💬", "Envoyer un petit message à quelqu'un de confiance", 1, 3, setOf("lowmood", "anxiety")),
        Mission("kind", "💛", "Me dire une phrase gentille, comme à un·e ami·e", 1, 1, setOf("body", "lowmood", "anxiety")),
        Mission("cloth", "👕", "Choisir un vêtement dans lequel je suis à l'aise", 1, 3, setOf("body")),
        Mission("bodycare", "🧴", "Un geste doux pour mon corps (crème, douche chaude)", 1, 5, setOf("body", "pain", "joints")),
        Mission("write", "✍️", "Écrire 3 phrases sur ce que je ressens", 2, 5, setOf("anxiety", "lowmood", "irritability", "body")),
        Mission("book", "📖", "Lire 5 pages d'un livre", 2, 10, setOf("anxiety", "lowmood", "sleep", "irritability")),
        Mission("shower", "🚿", "Prendre une douche ou me rafraîchir le visage", 2, 10, setOf("fatigue", "lowmood", "body", "fog")),
        Mission("stretch", "🤸", "Faire quelques étirements doux", 2, 5, setOf("pain", "joints", "anxiety", "fatigue")),
        Mission("light", "☀️", "Sortir 5 minutes à la lumière du jour", 2, 5, setOf("lowmood", "fatigue", "sleep", "fog")),
        Mission("corner", "🧺", "Ranger un seul petit coin", 2, 5),
        Mission("walk", "🚶", "Marcher 10 minutes dehors, à mon rythme", 3, 10, setOf("fatigue", "lowmood", "anxiety", "fog", "digestion", "body")),
        Mission("call", "📞", "Appeler quelqu'un que j'aime", 3, 10, setOf("lowmood", "anxiety")),
    )

    fun mission(id: String): Mission? = MISSIONS.firstOrNull { it.id == id }

    val TASK_KINDS: List<TaskKind> = listOf(
        TaskKind(
            "home", "🧺", "Ménage, rangement, lessive",
            listOf("Mettre un minuteur de 5 minutes", "Ramasser cinq objets qui traînent", "Prendre un sac et y mettre ce qui doit partir", "Ne faire qu'une seule pièce, ou un seul coin", "Remettre le minuteur si j'ai encore un peu d'élan"),
        ),
        TaskKind(
            "admin", "📄", "Papiers, démarches",
            listOf("Sortir le document ou ouvrir le site", "Rassembler les informations dont j'ai besoin", "Remplir seulement la première partie", "Noter ce qu'il reste à faire et quand", "Ranger le dossier à un endroit où je le retrouve"),
        ),
        TaskKind(
            "call", "📞", "Appel ou rendez-vous",
            listOf("Chercher le numéro ou le lien", "Noter en deux phrases ce que je veux dire", "Choisir un moment calme pour appeler", "Appeler ou prendre rendez-vous en ligne", "Noter la date et l'heure dans l'agenda"),
        ),
        TaskKind(
            "message", "✉️", "Mail ou message",
            listOf("Ouvrir le message", "Écrire une première phrase, même imparfaite", "Écrire le reste en une ou deux phrases", "Relire une fois, puis envoyer"),
        ),
        TaskKind(
            "meals", "🛒", "Courses, repas",
            listOf("Regarder ce qu'il me reste", "Noter trois choses dont j'ai besoin", "Choisir quelque chose de simple à manger", "Préparer seulement la première étape", "Passer par la livraison ou le drive si c'est plus doux"),
        ),
        TaskKind(
            "self", "🛁", "Prendre soin de moi",
            listOf("Préparer mes affaires", "Faire seulement le premier geste", "Mettre une musique douce", "Finir, puis me poser un instant"),
        ),
        TaskKind(
            "work", "💻", "Travail ou études",
            listOf("Ouvrir ce dont j'ai besoin", "Écrire en une phrase ce que je dois obtenir", "Travailler 10 minutes seulement", "Noter où j'en suis pour reprendre plus tard"),
        ),
        TaskKind(
            "other", "✨", "Autre chose",
            listOf("Dire en une phrase ce que je veux obtenir", "Imaginer la toute première étape, qui prend moins de 2 minutes", "Préparer ce dont j'ai besoin", "Faire cinq minutes seulement", "Décider de la suite : continuer ou reprendre demain"),
        ),
    )

    val BLOCKERS: List<Blocker> = listOf(
        Blocker("start", "Je ne sais pas par où commencer", "Écrire en une phrase ce que je veux obtenir à la fin"),
        Blocker("big", "Ça me semble trop gros", "Choisir un tout petit morceau et ne faire que lui aujourd'hui"),
        Blocker("energy", "Je manque d'énergie", "Faire cinq minutes seulement, puis m'arrêter sans culpabiliser"),
        Blocker("perfect", "J'ai peur de mal faire", "Faire une première version imparfaite, que je pourrai améliorer"),
        Blocker("focus", "Je n'arrive pas à me concentrer", "Poser mon téléphone et mettre un minuteur de dix minutes"),
    )
}
