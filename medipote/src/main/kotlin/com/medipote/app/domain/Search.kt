package com.medipote.app.domain

import com.medipote.app.data.AppState
import java.text.Normalizer

/** Where a search result leads: a screen, or an action done on the spot. */
sealed interface SearchTarget {
    /** A route of the app; a card of a tab is "treatments?card=soins:agenda". */
    data class Route(val route: String) : SearchTarget

    /** Something done at once, without leaving the screen ("water"). */
    data class Action(val id: String) : SearchTarget
}

data class SearchEntry(
    val id: String,
    val emoji: String,
    val title: String,
    /** Where it lives, "Suivi › Sommeil": every thing has one place, and the search says which. */
    val path: String,
    val keywords: List<String>,
    val target: SearchTarget,
)

/**
 * The app's search: what the person types, in their own words, leads to the one place where it lives.
 * Everything runs on the phone, from a list written once plus the person's own treatments, appointments
 * and documents; nothing typed is kept or sent.
 */
object AppSearch {
    private fun card(tab: String, key: String) = SearchTarget.Route("$tab?card=$key")
    private fun go(route: String) = SearchTarget.Route(route)

    /** Every screen and card, with the words people use for it. */
    val CATALOG: List<SearchEntry> = listOf(
        // ── Soins ──
        SearchEntry("soins:treatments", "💊", "Traitements", "Soins", listOf("médicament", "médicaments", "comprimé", "gélule", "prise", "prises", "posologie", "pilulier", "ajouter un traitement", "stock"), card("treatments", "soins:treatments")),
        SearchEntry("soins:pill", "🔁", "Pilule", "Soins", listOf("contraception", "contraceptif", "plaquette", "pause", "oubli de pilule"), card("treatments", "soins:pill")),
        SearchEntry("soins:agenda", "📅", "Agenda", "Soins", listOf("rendez-vous", "rdv", "médecin", "docteur", "consultation", "dentiste", "préparer un rendez-vous"), card("treatments", "soins:agenda")),
        SearchEntry("soins:docs", "📂", "Carnet de santé", "Soins", listOf("ordonnance", "document", "documents", "analyse", "résultats", "compte rendu", "pdf", "photo", "scan"), card("treatments", "soins:docs")),
        // ── Suivi ──
        SearchEntry("suivi:journal", "📖", "Journal", "Suivi", listOf("humeur", "note", "notes", "écrire", "journée", "aujourd'hui", "moral"), card("suivi", "suivi:journal")),
        SearchEntry("suivi:sleep", "🌙", "Sommeil", "Suivi", listOf("dormir", "nuit", "nuits", "sieste", "siestes", "réveil", "réveils", "coucher", "insomnie"), card("suivi", "suivi:sleep")),
        SearchEntry("suivi:measures", "🩺", "Mesures", "Suivi", listOf("tension", "poids", "pouls", "glycémie", "température", "saturation", "oxygène", "fréquence cardiaque"), card("suivi", "suivi:measures")),
        SearchEntry("suivi:cycle", "🩸", "Cycle", "Suivi", listOf("règles", "menstruations", "ovulation", "protection", "tampon", "serviette", "cycle menstruel", "période"), card("suivi", "suivi:cycle")),
        SearchEntry("suivi:symptoms", "🌡️", "Symptômes", "Suivi", listOf("symptôme", "douleur", "fatigue", "mal", "ressenti", "intensité", "noter un symptôme"), card("suivi", "suivi:symptoms")),
        // ── Mieux-être ──
        SearchEntry("mieux:softday", "💛", "Journée douce", "Mieux-être", listOf("mission", "missions", "énergie", "petite victoire", "journée lourde", "tâche"), card("mieux", "mieux:softday")),
        SearchEntry("mieux:feel", "💭", "Ce que je ressens", "Mieux-être", listOf("anxiété", "angoisse", "panique", "tristesse", "moral bas", "déprime", "dépression", "poitrine serrée", "fatigue extrême", "envie de rien"), go("support_feel")),
        SearchEntry("mieux:rituals", "✨", "Rituels du matin et du soir", "Mieux-être", listOf("rituel", "matin", "soir", "routine"), go("ritual/MORNING")),
        SearchEntry("mieux:me", "🌿", "Ce qui m'accompagne", "Mieux-être", listOf("maladie", "condition", "hashimoto", "diabète", "asthme", "thyroïde", "mes symptômes à suivre", "mes missions"), go("wellbeing_me")),
        // ── Moi ──
        SearchEntry("moi:stats", "📊", "Statistiques", "Moi", listOf("stats", "courbe", "graphique", "observance", "bilan", "moyenne", "évolution"), card("moi", "moi:stats")),
        SearchEntry("moi:year", "📆", "Mon année", "Moi", listOf("année en pixels", "pixels", "calendrier"), card("moi", "moi:year")),
        SearchEntry("moi:shop", "🛍️", "Boutique", "Moi", listOf("objets", "habits", "compagnon", "points", "acheter", "souhait", "chapeau"), card("moi", "moi:shop")),
        SearchEntry("moi:badges", "🏅", "Badges et défis", "Moi", listOf("badge", "défi", "défis", "récompense", "série", "trophée"), card("moi", "moi:badges")),
        // ── Aide quand ça ne va pas ──
        SearchEntry("support", "❤️", "Ça ne va pas", "Le cœur en haut de l'écran", listOf("aide", "soutien", "urgence", "détresse", "3114", "suicide", "idées noires", "mal", "à l'aide", "personnes de confiance"), go("support")),
        SearchEntry("breathe", "🌬️", "Respirer", "Ça ne va pas", listOf("respiration", "calmer", "cohérence cardiaque", "souffle", "stress"), go("support_breathe")),
        SearchEntry("ground", "🖐️", "Me poser (5-4-3-2-1)", "Ça ne va pas", listOf("ancrage", "5 4 3 2 1", "panique", "calmer"), go("support_ground")),
        SearchEntry("thoughts", "✍️", "Vider ma tête", "Ça ne va pas", listOf("pensées", "écrire", "ruminer", "inquiétudes"), go("support_thoughts")),
        SearchEntry("comfort", "🧸", "Ma boîte à réconfort", "Ça ne va pas", listOf("réconfort", "ce qui me fait du bien"), go("support_comfort")),
        SearchEntry("plan", "📝", "Mon plan", "Ça ne va pas", listOf("plan de crise", "signes", "qui appeler"), go("support_plan")),
        // ── Sommeil ──
        SearchEntry("sleep_help", "🌙", "Nuit difficile", "Suivi › Sommeil", listOf("insomnie", "dormir", "endormir", "rendormir", "réveillé", "3 h du matin", "nuit blanche", "pas sommeil"), go("sleep_help")),
        SearchEntry("sleep_relax", "🧘", "Relaxation guidée", "Suivi › Sommeil", listOf("détendre", "relaxer", "corps", "dormir"), go("sleep_relax")),
        SearchEntry("sleep_tips", "🌤️", "Pour mieux dormir", "Suivi › Sommeil", listOf("conseils sommeil", "hygiène du sommeil", "rituel du soir", "mot du soir"), go("sleep_tips")),
        // ── Noter, tout de suite ──
        SearchEntry("water", "💧", "Un verre d'eau", "Noter", listOf("eau", "boire", "hydratation", "verre"), SearchTarget.Action("water")),
        SearchEntry("log_sleep", "🌙", "Noter ma nuit", "Noter", listOf("nuit", "dormi", "sommeil", "réveils"), go("sleep_log")),
        SearchEntry("log_nap", "😴", "Noter une sieste", "Noter", listOf("sieste", "chronomètre", "je m'endors"), go("nap_log")),
        SearchEntry("log_journal", "📖", "Écrire dans mon journal", "Noter", listOf("journal", "humeur", "note", "aujourd'hui"), go("journal_today")),
        SearchEntry("log_symptom", "🌡️", "Noter un symptôme", "Noter", listOf("symptôme", "douleur", "fatigue", "ressenti"), go("symptoms")),
        SearchEntry("task", "🧩", "Découper une tâche en petites étapes", "Mieux-être › Journée douce", listOf("tâche", "procrastination", "trop gros", "ménage", "papiers"), go("task_new")),
        // ── Réglages et le reste ──
        SearchEntry("report", "📄", "Rapport pour le médecin (PDF)", "Moi › Statistiques", listOf("pdf", "médecin", "rapport", "imprimer", "partager", "consultation"), go("report")),
        SearchEntry("settings", "⚙️", "Paramètres", "En haut à droite", listOf("réglages", "options", "configuration"), go("settings")),
        SearchEntry("profile", "👤", "Profil médical", "En haut de l'écran", listOf("antécédents", "allergies", "âge", "taille", "poids", "informations"), go("profile")),
        SearchEntry("home_layout", "🏠", "Mon accueil", "Paramètres", listOf("accueil", "blocs", "ordre", "simple", "complet", "afficher", "masquer"), go("home_layout")),
        SearchEntry("cards_layout", "🗂️", "Mes cartes", "Paramètres", listOf("cartes", "onglets", "masquer", "afficher", "ranger"), go("cards_layout")),
        SearchEntry("appearance", "🎨", "Couleurs et police", "Paramètres", listOf("couleur", "thème", "sombre", "clair", "police", "taille du texte", "accent", "personnaliser"), go("appearance")),
        SearchEntry("seasons", "🍂", "Saisons et fêtes", "Paramètres", listOf("saison", "fête", "noël", "anniversaire", "halloween"), go("seasons")),
        SearchEntry("backup", "💾", "Sauvegarde", "Paramètres", listOf("sauvegarder", "restaurer", "exporter", "changer de téléphone", "perdre mes données", "copie"), go("backup")),
        SearchEntry("quiet", "🔕", "Heures calmes", "Paramètres", listOf("silence", "nuit", "ne pas déranger", "notifications", "rappels"), go("settings?section=reminders")),
        SearchEntry("reminders", "🔔", "Rappels et notifications", "Paramètres", listOf("rappel", "notification", "alarme", "son", "vibration", "pause"), go("settings?section=reminders")),
        SearchEntry("sober", "🍃", "Mode sobre", "Paramètres › Jeu", listOf("sans points", "sans jeu", "série", "points", "badges", "boutique", "discret", "calme"), go("settings?section=game")),
        SearchEntry("moment", "🕐", "Accueil du moment", "Paramètres › Mon accueil", listOf("matin", "soir", "voir plus", "accueil simple", "moins de choses"), go("home_layout")),
        SearchEntry("tour", "👋", "Revoir le petit tour", "Aide", listOf("tuto", "tutoriel", "aide", "comment ça marche", "découvrir", "explications", "guide"), go("tour")),
    )

    /** Lowercase, no accents, simple separators: what is typed and what is searched match whatever the spelling. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[’'\\-_/·›]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** The person's own treatments, appointments and documents as entries. */
    fun personal(state: AppState): List<SearchEntry> = buildList {
        state.treatments.forEach { t ->
            add(SearchEntry("t:${t.id}", "💊", t.displayName, "Soins › Traitements", listOfNotNull(t.name, t.drugId), go("editor?treatment=${t.id}")))
        }
        state.appointments.forEach { a ->
            add(SearchEntry("a:${a.id}", "📅", a.title + if (a.doctor.isNotBlank()) " · ${a.doctor}" else "", "Soins › Agenda", listOf(a.doctor, a.location), go("appointment?id=${a.id}")))
        }
        state.documents.forEach { d ->
            add(SearchEntry("d:${d.id}", "📄", d.title, "Soins › Carnet de santé", listOf(d.category.label), go("document/${d.id}")))
        }
    }

    private fun score(entry: SearchEntry, tokens: List<String>): Int {
        val title = normalize(entry.title)
        val titleWords = title.split(' ')
        val keywords = entry.keywords.map(::normalize)
        val keywordWords = keywords.flatMap { it.split(' ') }
        val path = normalize(entry.path)
        var total = 0
        for (token in tokens) {
            total += when {
                title.startsWith(token) -> 100
                titleWords.any { it.startsWith(token) } -> 70
                title.contains(token) && token.length >= 3 -> 50
                keywords.any { it == token || it.startsWith(token) } -> 45
                keywordWords.any { it.startsWith(token) } -> 35
                keywords.any { it.contains(token) } && token.length >= 3 -> 20
                path.contains(token) && token.length >= 3 -> 8
                else -> return 0 // every word typed must be found somewhere
            }
        }
        return total
    }

    /** Entries matching what was typed, best first; empty text gives nothing (suggestions come from [SUGGESTIONS]). */
    fun query(state: AppState, text: String, limit: Int = 12): List<SearchEntry> {
        val tokens = normalize(text).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val all = CATALOG + personal(state)
        return all.mapIndexed { i, e -> Triple(e, score(e, tokens), i) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<SearchEntry, Int, Int>> { it.second }.thenBy { it.third })
            .take(limit).map { it.first }
    }

    /** What to try typing, shown before anything is typed. */
    val SUGGESTIONS = listOf("sommeil", "eau", "symptôme", "règles", "anxiété", "rendez-vous", "ordonnance", "couleurs")
}
