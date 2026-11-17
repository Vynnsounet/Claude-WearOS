package com.medipote.app.domain

/** The tools of "Ça ne va pas" a sign can point to. */
enum class SupportTool(val emoji: String, val label: String) {
    BREATHE("🌬️", "Respirer"),
    GROUND("🖐️", "Me poser (5-4-3-2-1)"),
    THOUGHTS("✍️", "Vider ma tête"),
    COMFORT("🧸", "Ma boîte à réconfort"),
    SOFT_DAY("💛", "Journée douce"),
}

/** A way of feeling bad, as the person would name it. */
data class Feeling(val id: String, val emoji: String, val label: String, val intro: String, val consult: List<String>)

/**
 * One thing felt, in the body or in the mind: what can be tried right now, a word on why it happens,
 * and which symptom it can be noted as. [chest] signs always remind that a pain in the chest is a
 * reason to call; [urgent] ones show the help line first instead of tips.
 */
data class Sign(
    val id: String,
    val feeling: String,
    val emoji: String,
    val label: String,
    val why: String,
    val tryNow: List<String>,
    val tools: Set<SupportTool> = emptySet(),
    /** The id of a symptom of the tracking it can be noted as, if any. */
    val symptom: String? = null,
    val chest: Boolean = false,
    val urgent: Boolean = false,
)

/**
 * What the app knows by heart about anxiety, panic and low mood as they are lived in the body and in
 * the head. General wellbeing information: it never diagnoses, never names an illness for the person,
 * never touches a treatment, and always says when to talk to a doctor or to call.
 */
object FeelingGuide {
    const val ANXIETY = "anxiety"
    const val PANIC = "panic"
    const val LOW = "low"

    val CHEST_ALERT = "Une douleur forte dans la poitrine, qui irradie vers le bras ou la mâchoire, avec des sueurs, un malaise ou un essoufflement important : appelle le 15 tout de suite. En cas de doute, appelle aussi."

    val FEELINGS = listOf(
        Feeling(
            ANXIETY, "😰", "Anxiété",
            "Une tension qui dure, des inquiétudes qui tournent, un corps en alerte.",
            listOf(
                "Si l'anxiété revient souvent ou t'envahit, parles-en à ton médecin ou à un·e psychologue : des accompagnements efficaces existent.",
                "Certaines maladies (la thyroïde par exemple) et certains traitements peuvent aussi donner de l'anxiété, des palpitations ou de la fatigue : un point avec ton médecin vaut la peine.",
            ),
        ),
        Feeling(
            PANIC, "😨", "Angoisse",
            "Une vague de peur qui monte vite, parfois sans raison. Elle est très pénible, mais elle redescend.",
            listOf(
                "Une première crise, ou des crises qui se répètent : parles-en à ton médecin, c'est très courant et ça se soigne.",
                "Si tu as un doute sur ce qui se passe dans ta poitrine, appelle le 15.",
            ),
        ),
        Feeling(
            LOW, "😔", "Moral bas",
            "Tristesse, poids sur le cœur, plus d'énergie ni d'envie. Ce n'est pas de la paresse ni un défaut.",
            listOf(
                "Un moral bas depuis plus de deux semaines, ou qui t'empêche de vivre comme d'habitude : parles-en à ton médecin.",
                "Une fatigue extrême ou un moral bas peuvent aussi avoir une cause physique (thyroïde, carence, anémie…) qu'un bilan peut repérer : ça vaut la peine de la chercher.",
                "Si tu penses à te faire du mal : appelle le 3114, jour et nuit, gratuit.",
            ),
        ),
    )

    val SIGNS = listOf(
        // ── Anxiété ──
        Sign(
            "chest_tight", ANXIETY, "🧱", "Poitrine serrée",
            "Quand on est tendu·e, les muscles autour des côtes se contractent : on a l'impression que ça serre.",
            listOf(
                "Pose une main sur ta poitrine et respire lentement : l'expiration plus longue que l'inspiration.",
                "Relâche les épaules et desserre ce qui serre (col, ceinture).",
                "Étire les bras vers le haut, puis laisse-les retomber.",
            ),
            setOf(SupportTool.BREATHE), "anxiety", chest = true,
        ),
        Sign(
            "heart_fast", ANXIETY, "💓", "Cœur qui bat vite",
            "Le corps se prépare à agir : le cœur accélère. C'est une réaction d'alerte, pas un danger en soi.",
            listOf(
                "Assieds-toi et respire lentement pendant deux minutes.",
                "De l'eau fraîche sur le visage ou les poignets aide à calmer.",
                "Évite le café et les excitants aujourd'hui.",
            ),
            setOf(SupportTool.BREATHE), "palpitations", chest = true,
        ),
        Sign(
            "short_breath", ANXIETY, "💨", "Souffle court",
            "L'angoisse fait respirer plus vite et plus haut, ce qui donne l'impression de manquer d'air.",
            listOf(
                "Assieds-toi, relâche les épaules.",
                "Inspire par le nez, puis souffle doucement par la bouche, comme à travers une paille.",
                "Compte quatre en inspirant, six en expirant.",
            ),
            setOf(SupportTool.BREATHE), "breath", chest = true,
        ),
        Sign(
            "shaky", ANXIETY, "🤲", "Tremblements, mains moites",
            "L'adrénaline mobilise les muscles : ça tremble ou ça transpire, même sans effort.",
            listOf(
                "Serre fort les poings cinq secondes, puis relâche : répète trois fois.",
                "Bois de l'eau tiède à petites gorgées.",
                "Marche doucement pour laisser l'énergie sortir.",
            ),
            setOf(SupportTool.BREATHE), "anxiety",
        ),
        Sign(
            "stomach", ANXIETY, "🤢", "Boule au ventre, nausées",
            "Le ventre réagit beaucoup aux émotions : il se noue.",
            listOf(
                "Pose une main chaude sur le ventre et respire lentement vers elle.",
                "Une boisson tiède, une toute petite portion de quelque chose de simple.",
                "Évite les repas lourds et les odeurs fortes.",
            ),
            setOf(SupportTool.BREATHE), "nausea",
        ),
        Sign(
            "tense", ANXIETY, "😬", "Nuque, épaules, mâchoire serrées",
            "Sous tension, on se crispe sans s'en rendre compte, surtout à la mâchoire et aux épaules.",
            listOf(
                "Relâche la mâchoire : lèvres fermées, dents écartées, langue molle.",
                "Roule les épaules, puis penche doucement la tête d'un côté et de l'autre.",
                "De la chaleur sur la nuque (douche, bouillotte) détend.",
            ),
            emptySet(), "pain",
        ),
        Sign(
            "dizzy_a", ANXIETY, "😵", "Vertiges, tête qui tourne",
            "Respirer vite ou être très tendu·e peut donner la tête qui tourne.",
            listOf(
                "Assieds-toi ou allonge-toi dès que ça tourne.",
                "Bois un verre d'eau, respire lentement.",
                "Relève-toi doucement, sans te presser.",
            ),
            setOf(SupportTool.BREATHE), "dizzy",
        ),
        Sign(
            "loop", ANXIETY, "🌀", "Pensées qui tournent en boucle",
            "Quand on est anxieux·se, l'esprit cherche des dangers : les mêmes pensées reviennent.",
            listOf(
                "Écris-les sans filtre, pour les sortir de ta tête.",
                "Demande-toi : est-ce un fait, ou une peur ? Que dirais-tu à un·e ami·e ?",
                "Ramène ton attention sur ce qui est autour de toi (5-4-3-2-1).",
            ),
            setOf(SupportTool.THOUGHTS, SupportTool.GROUND), "anxiety",
        ),
        Sign(
            "sleep_a", ANXIETY, "🌙", "Difficulté à s'endormir",
            "Le corps en alerte a du mal à se mettre au repos.",
            listOf(
                "Pose ton téléphone, mets une lumière douce.",
                "Écris ce qui tourne dans ta tête pour le laisser sur le papier.",
                "Respiration lente. Si tu ne dors pas, lève-toi un moment plutôt que de rester des heures au lit.",
            ),
            setOf(SupportTool.THOUGHTS, SupportTool.BREATHE), "sleep",
        ),
        Sign(
            "restless", ANXIETY, "🏃", "Besoin de bouger, impossible de se poser",
            "L'énergie de l'alerte cherche une sortie.",
            listOf(
                "Marche dix minutes, dehors si tu peux.",
                "Secoue les bras et les jambes, étire-toi.",
                "Puis respire lentement quelques minutes pour redescendre.",
            ),
            setOf(SupportTool.BREATHE), "irritability",
        ),

        // ── Angoisse ──
        Sign(
            "wave", PANIC, "🌊", "Une vague de peur soudaine",
            "Une crise d'angoisse monte vite et redescend en général en dix à vingt minutes. C'est très désagréable, mais pas dangereux en soi.",
            listOf(
                "Dis-toi : « c'est une vague, elle va passer ».",
                "Respire lentement, l'expiration plus longue que l'inspiration.",
                "Pose les pieds au sol et sens-les. Puis 5 choses que tu vois, 4 que tu touches…",
                "Ne lutte pas : laisse la vague monter et redescendre.",
            ),
            setOf(SupportTool.BREATHE, SupportTool.GROUND), "anxiety", chest = true,
        ),
        Sign(
            "choke", PANIC, "😮", "Impression d'étouffer",
            "La gorge et la poitrine se contractent, et le souffle devient court : c'est l'alerte du corps.",
            listOf(
                "Desserre ce qui serre, ouvre une fenêtre.",
                "Souffle doucement par la bouche, comme sur une bougie, plusieurs fois.",
                "Assieds-toi, pose les mains sur les cuisses.",
            ),
            setOf(SupportTool.BREATHE), "breath", chest = true,
        ),
        Sign(
            "tingle", PANIC, "✨", "Fourmillements, engourdissements",
            "Respirer trop vite change l'équilibre du sang et donne des fourmillements aux mains, aux lèvres.",
            listOf(
                "Ralentis ton souffle : trois secondes pour inspirer, cinq pour expirer.",
                "Ouvre et ferme doucement les mains.",
                "Ça s'apaise quand la respiration redevient lente.",
            ),
            setOf(SupportTool.BREATHE), "anxiety",
        ),
        Sign(
            "unreal", PANIC, "🌫️", "Impression d'irréalité, d'être à côté",
            "Le cerveau se met à distance pour se protéger : c'est impressionnant mais temporaire.",
            listOf(
                "Touche un objet froid ou rugueux, sens-le bien.",
                "Décris à voix haute cinq choses que tu vois.",
                "Appuie fort tes pieds au sol.",
            ),
            setOf(SupportTool.GROUND), "anxiety",
        ),
        Sign(
            "lose_control", PANIC, "😱", "Peur de mourir ou de perdre le contrôle",
            "C'est une pensée typique des crises d'angoisse : très convaincante, mais elle ne se réalise pas.",
            listOf(
                "Répète-toi : « ça va passer, je ne risque rien ».",
                "Concentre-toi sur ton souffle, en le ralentissant.",
                "Si tu peux, appelle quelqu'un de confiance et reste en ligne.",
            ),
            setOf(SupportTool.BREATHE, SupportTool.GROUND), "anxiety", chest = true,
        ),

        // ── Moral bas ──
        Sign(
            "heavy_chest", LOW, "💔", "Poids dans la poitrine",
            "La tristesse se ressent souvent dans le corps : poitrine lourde, gorge serrée, comme un poids posé dessus.",
            listOf(
                "Pose une main sur ta poitrine, respire doucement et dis-toi une phrase gentille.",
                "Une boisson chaude, un plaid, une bouillotte sur la poitrine ou le ventre.",
                "Si les larmes viennent, laisse-les venir : pleurer soulage.",
                "Dis à quelqu'un que ça pèse, même par un simple message.",
            ),
            setOf(SupportTool.BREATHE, SupportTool.COMFORT), "lowmood", chest = true,
        ),
        Sign(
            "exhausted", LOW, "🔋", "Fatigue extrême",
            "Un moral bas épuise : tout demande un effort énorme, même ce qui est simple.",
            listOf(
                "Se reposer est une vraie action : ne te juge pas.",
                "Réduis la journée à l'essentiel : une seule chose, le reste peut attendre.",
                "Un verre d'eau, quelque chose à manger, un peu de lumière du jour.",
                "Une sieste courte (20 à 30 minutes) plutôt qu'une longue.",
            ),
            setOf(SupportTool.SOFT_DAY), "fatigue",
        ),
        Sign(
            "cant_get_up", LOW, "🛏️", "Du mal à se lever du lit",
            "Quand le moral est très bas, démarrer devient le plus dur. Ce n'est pas un manque de volonté.",
            listOf(
                "Ne vise pas « me lever » : vise une toute petite étape, comme t'asseoir au bord du lit.",
                "Un verre d'eau, ouvrir les rideaux, poser les pieds au sol.",
                "Choisis une seule mission de deux minutes (journée douce).",
                "Si ça dure, dis-le à une personne de confiance ou à ton médecin.",
            ),
            setOf(SupportTool.SOFT_DAY), "lowmood",
        ),
        Sign(
            "no_desire", LOW, "😶", "Envie de rien faire",
            "La perte d'envie est un signe courant d'un moral bas. Ce n'est ni de la paresse ni un défaut de caractère.",
            listOf(
                "Choisis la plus petite action possible, cinq minutes, sans objectif de résultat.",
                "Fais quelque chose qui demande peu : une chanson, un thé, regarder par la fenêtre.",
                "Pas de culpabilité : commencer, même un peu, compte déjà.",
                "Envoie un petit message à quelqu'un, sans rien attendre.",
            ),
            setOf(SupportTool.SOFT_DAY), "lowmood",
        ),
        Sign(
            "lump", LOW, "🥺", "Gorge nouée, envie de pleurer",
            "Les émotions serrent la gorge : c'est une réaction normale du corps.",
            listOf(
                "Laisse tomber la mâchoire : dents écartées, langue molle. Baisse les épaules, soupire longuement.",
                "Bois de petites gorgées d'eau, lentement.",
                "Pose une main sur ta gorge ou ta poitrine : sens-la bouger quand tu respires.",
                "Pleure si ça vient : ça relâche la tension.",
            ),
            setOf(SupportTool.BREATHE, SupportTool.COMFORT), "lowmood",
        ),
        Sign(
            "foggy_l", LOW, "🌫️", "Du mal à se concentrer",
            "Un moral bas occupe l'esprit et laisse peu de place pour le reste.",
            listOf(
                "Une seule chose à la fois, et écris ce que tu dois retenir.",
                "Fais des pauses courtes, avec un verre d'eau et de la lumière.",
                "Remets à plus tard les décisions importantes si tu le peux.",
            ),
            emptySet(), "fog",
        ),
        Sign(
            "appetite", LOW, "🍽️", "Appétit qui change",
            "Le moral touche l'appétit : on n'a plus faim, ou on mange pour se consoler.",
            listOf(
                "Ne te force pas et ne te le reproche pas.",
                "Quelque chose de simple en petite portion, à heures à peu près régulières.",
                "Bois assez d'eau dans la journée.",
            ),
            emptySet(), null,
        ),
        Sign(
            "sleep_l", LOW, "😴", "Sommeil trop ou trop peu",
            "Le moral bas dérègle le sommeil : on dort mal, ou énormément sans être reposé·e.",
            listOf(
                "Garde une heure de lever à peu près régulière.",
                "Un peu de lumière du jour le matin aide l'horloge du corps.",
                "Pose les écrans avant de dormir.",
            ),
            emptySet(), "sleep",
        ),
        Sign(
            "empty", LOW, "🕳️", "Sentiment de vide, coupé·e des autres",
            "Un moral bas donne l'impression que rien ne touche et que les autres sont loin.",
            listOf(
                "Envoie un petit message ou appelle quelqu'un, même pour dire que ça ne va pas fort.",
                "Va dans un endroit où il y a un peu de monde, sans rien avoir à dire.",
                "Écris ce qui manque, juste pour toi.",
            ),
            setOf(SupportTool.COMFORT, SupportTool.THOUGHTS), "lowmood",
        ),
        Sign(
            "guilt", LOW, "⛓️", "Culpabilité, je ne vaux rien",
            "Un moral bas met des lunettes très noires : les pensées sur soi deviennent dures et injustes.",
            listOf(
                "Parle-toi comme tu parlerais à un·e ami·e.",
                "Écris la pensée, puis ce que dirait quelqu'un qui t'aime.",
                "Ce ne sont pas des faits, ce sont des pensées du moment.",
            ),
            setOf(SupportTool.THOUGHTS), "lowmood",
        ),
        Sign(
            "dark", LOW, "🌑", "Des idées noires",
            "Tu n'es pas seul·e, et ce que tu ressens est à prendre au sérieux. Parler à quelqu'un aide, tout de suite.",
            listOf(
                "Appelle le 3114 : on te répond jour et nuit, gratuitement.",
                "Éloigne ce qui pourrait te faire du mal et ne reste pas seul·e.",
                "Préviens une personne de confiance, ou rends-toi aux urgences.",
            ),
            setOf(SupportTool.COMFORT), "lowmood", urgent = true,
        ),
    )

    fun feeling(id: String): Feeling? = FEELINGS.firstOrNull { it.id == id }

    fun sign(id: String): Sign? = SIGNS.firstOrNull { it.id == id }

    fun signsOf(feeling: String): List<Sign> = SIGNS.filter { it.feeling == feeling }

    /** The feelings the chosen signs belong to, in the guide's order. */
    fun feelingsOf(chosen: Collection<String>): List<Feeling> =
        FEELINGS.filter { f -> chosen.any { sign(it)?.feeling == f.id } }

    /** The symptoms the chosen signs can be noted as, without doubles. */
    fun symptomsOf(chosen: Collection<String>): List<String> = chosen.mapNotNull { sign(it)?.symptom }.distinct()
}
