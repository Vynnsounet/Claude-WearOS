package com.lingoquest.engine

/** A grammar point as shown to the player. */
data class RuleInfo(val key: String, val title: String, val text: String, val example: String)

/**
 * Grammar points of French and English. [fr] explains the French side, [en] the English side
 * (only shown in the English course).
 */
enum class Rule(val title: String, private val fr: String, private val en: String, val example: String) {
    ARTICLE(
        "Articles et genre",
        "En français, chaque nom est masculin ou féminin : le/un pour le masculin, la/une pour le féminin, les au pluriel.",
        "En anglais, « the » et « a/an » ne changent jamais.",
        "la maison (f.) · le chien (m.) · the house · the dog",
    ),
    SOME_DES(
        "Pluriel indéfini",
        "« des » est le pluriel de « un/une ».",
        "Il correspond à « some » en anglais ; au singulier : « a/an ».",
        "des chiens = some dogs · un chien = a dog",
    ),
    PLURAL(
        "Pluriel des noms",
        "Français : on ajoute en général un -s (-x pour -eau, -al → -aux).",
        "Anglais : -s aussi, mais certains pluriels sont irréguliers (child → children, man → men, mouse → mice).",
        "un oiseau → des oiseaux · a child → two children",
    ),
    POSSESSIVE(
        "Possessifs",
        "Le possessif français s'accorde avec le nom possédé, pas avec le possesseur : mon/ma/mes, ton/ta/tes " +
            "(tu) ou votre/vos (vous), notre/nos, leur/leurs. Devant une voyelle, « ma » devient « mon ».",
        "En anglais : my, your, our, their.",
        "ma maison · mon amie · leurs chiens · my house",
    ),
    DEMONSTRATIVE(
        "Démonstratifs",
        "ce (m.), cet (m. devant voyelle), cette (f.), ces (pluriel).",
        "En anglais : this / these.",
        "ce chien · cet homme · cette maison · ces chats",
    ),
    ADJ_AGREEMENT(
        "Accord de l'adjectif",
        "En français l'adjectif s'accorde en genre et en nombre avec le nom : féminin = souvent +e, pluriel = +s (ou -x).",
        "En anglais l'adjectif ne change jamais.",
        "un grand chien · une grande maison · des grandes maisons · a big dog",
    ),
    ADJ_PLACEMENT(
        "Place de l'adjectif",
        "En français l'adjectif est le plus souvent après le nom (couleur, forme, nationalité…), sauf quelques " +
            "adjectifs courts placés avant : beau, bon, grand, gros, jeune, joli, mauvais, nouveau, petit, vieux, " +
            "long, autre, même, premier, dernier.",
        "En anglais l'adjectif est toujours avant le nom.",
        "a red car → une voiture rouge · a small dog → un petit chien",
    ),
    ADJ_BEL(
        "Beau, nouveau, vieux devant une voyelle",
        "Au masculin singulier devant une voyelle ou un h muet : beau → bel, nouveau → nouvel, vieux → vieil.",
        "",
        "un bel arbre · un nouvel ami · un vieil homme",
    ),
    ADJ_AND(
        "Deux adjectifs",
        "Deux adjectifs placés après le nom sont reliés par « et » en français.",
        "En anglais ils précèdent le nom : « a black and white dog ».",
        "un chien noir et blanc",
    ),
    DE_ADJ(
        "« De » devant un adjectif pluriel",
        "Quand un adjectif pluriel est placé avant le nom, « des » devient « de » (d' devant une voyelle).",
        "",
        "de bons amis · d'autres chats",
    ),
    ELISION(
        "Élision et contractions",
        "Devant une voyelle ou un h muet, le/la/je/ne/de/que/si s'écrivent l'/j'/n'/d'/qu'/s'. À + le = au, à + les = aux, de + le = du.",
        "En anglais, « a » devient « an » devant une voyelle.",
        "l'amie · j'ai · je n'aime pas · au parc · an apple",
    ),
    PRONOUNS(
        "Pronoms sujets",
        "il, elle, ils (groupe masculin ou mixte), elles (groupe féminin) ; tu (une personne familière) ou vous " +
            "(politesse ou plusieurs personnes).",
        "En anglais : he, she, they, et « you » pour tu comme pour vous.",
        "tu manges / vous mangez = you eat",
    ),
    PRESENT(
        "Présent",
        "Le verbe français se conjugue à chaque personne (je mange, tu manges, il mange, nous mangeons, vous mangez, ils mangent).",
        "En anglais, seul « he/she/it » prend un -s au présent : he eats, they eat.",
        "il mange · elle aime → she likes · they eat",
    ),
    NEGATION(
        "Négation",
        "Français : ne … pas autour du verbe conjugué (n' devant une voyelle). Après la négation, un/une/des deviennent « de » (d') : je ne mange pas de pomme.",
        "Anglais : do/does + not + verbe sans « to » (did not au passé), ou « is/are not » avec be.",
        "je ne mange pas = I do not eat · il n'est pas grand = he is not big",
    ),
    PASSE_COMPOSE(
        "Passé composé avec avoir",
        "Le passé français se forme avec avoir au présent + participe passé : j'ai mangé, tu as vu.",
        "L'anglais utilise le prétérit (une seule forme : ate, saw, liked), souvent irrégulier.",
        "j'ai mangé = I ate · elle a vu = she saw",
    ),
    PASSE_COMPOSE_ETRE(
        "Passé composé avec être",
        "Aller, venir, arriver, partir, rester, tomber, entrer… se conjuguent avec être, et le participe s'accorde " +
            "avec le sujet : +e au féminin, +s au pluriel.",
        "",
        "il est allé · elle est allée · ils sont allés · elles sont allées",
    ),
    FUTURE(
        "Futur proche",
        "Aller au présent + infinitif : je vais manger, il va partir, ils vont voir. Négation : je ne vais pas manger.",
        "En anglais : be going to + verbe.",
        "I am going to eat = je vais manger",
    ),
    MODAL(
        "Verbes modaux",
        "pouvoir et devoir sont suivis de l'infinitif : il peut manger, je dois partir.",
        "En anglais (can, must) ils sont suivis du verbe sans « to » et ne prennent pas de -s. Négation : cannot / must not.",
        "il peut manger = he can eat · je ne peux pas = I cannot",
    ),
    INFINITIVE(
        "Verbe + infinitif",
        "Après certains verbes, le français met rien (vouloir, espérer, préférer), « de » (décider de, essayer de, " +
            "refuser de) ou « à » (commencer à, apprendre à, continuer à, réussir à).",
        "En anglais : « to » + infinitif.",
        "I want to eat = je veux manger · he decides to eat = il décide de manger",
    ),
    COPULA(
        "Le verbe être",
        "être : je suis, tu es, il est, nous sommes, vous êtes, ils sont ; description au passé : j'étais, il était, ils étaient.",
        "En anglais : I am, you/we/they are, he/she is (passé : was / were).",
        "she was tired = elle était fatiguée",
    ),
    PLACE_PREP(
        "Prépositions de lieu",
        "dans = à l'intérieur, à = vers / en (à + le = au). Les prépositions ne se traduisent pas toujours mot à mot.",
        "« enter the house » = entrer dans la maison.",
        "in the garden = dans le jardin · to the station = à la gare",
    ),
    PREP_NP(
        "Compléments du nom",
        "avec, dans : le complément se place après le nom.",
        "En anglais : with, in — même place.",
        "the woman with a book = la femme avec un livre",
    ),
    RELATIVE(
        "Proposition relative",
        "« qui » reprend le sujet : le verbe qui suit s'accorde avec le nom qualifié.",
        "En anglais : that / who.",
        "the man that eats = l'homme qui mange",
    ),
    CONNECTORS(
        "Mots de liaison",
        "et, mais, parce que (parce qu' devant une voyelle), quand, si (s'il / s'ils devant il / ils). Après « si », " +
            "on reste au présent.",
        "En anglais : and, but, because, when, if.",
        "if he eats = s'il mange · because she is tired = parce qu'elle est fatiguée",
    ),
    ADVERB(
        "Adverbes",
        "Les adverbes de temps et de lieu se placent en général à la fin de la phrase : aujourd'hui, ici, maintenant, hier, demain.",
        "",
        "I eat an apple today = je mange une pomme aujourd'hui",
    ),
    ;

    /** The explanation for a player of [course]; the English half only appears in the English course. */
    fun info(course: Lang) = RuleInfo(
        name, title,
        if (course == Lang.EN && en.isNotEmpty()) "$fr $en" else fr,
        example,
    )
}

/** Concrete facts about the words of a challenge (gender, plural, conjugation table…). */
internal object Notes {
    private val frPersons = listOf("je", "tu", "il/elle", "nous", "vous", "ils/elles")

    fun forEntry(entry: Entry, lang: Lang, lexicon: Lexicon? = null): String? = when (lang) {
        Lang.EN, Lang.FR -> basic(entry, lang)
        else -> lexicon?.fx(entry, lang)?.let { ForeignNotes.of(entry, lang, it) }
    }

    private fun basic(entry: Entry, lang: Lang): String? = when (entry) {
        is Noun -> if (lang == Lang.FR) {
            "${entry.fr[0]} : nom ${if (entry.gender == Gender.M) "masculin" else "féminin"}, pluriel « ${entry.frPlural} »"
        } else "${entry.en[0]} : pluriel « ${entry.enPlural} »"
        is Adj -> if (lang == Lang.FR) {
            "${entry.forms(Lang.FR)[0]} : ${entry.allFrForms.joinToString(", ")} — " +
                if (entry.before) "se place avant le nom" else "se place après le nom"
        } else null
        is Verb -> if (lang == Lang.FR) {
            val table = entry.present.mapIndexed { i, f -> "${frPersons[i]} $f" }.joinToString(", ")
            "${entry.fr[0]} : $table — passé : ${entry.participle} (${if (entry.aux == Aux.ETRE) "être" else "avoir"})"
        } else if (entry.modal) null
        else "${entry.en[0]} : he/she ${entry.en3}, passé « ${entry.enPast} »"
        else -> null
    }
}
