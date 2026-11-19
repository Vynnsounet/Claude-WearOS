package com.medipote.app.domain

import com.medipote.app.data.Idea
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** « Une idée, un souci ? »: a list kept on the phone, handed over as plain text only if the person shares it. */
object Ideas {
    const val MAX = 200

    /** Where the person was, in the words of the app. */
    fun screenName(route: String?): String = when (route?.substringBefore('?')) {
        null, "" -> ""
        "home" -> "Accueil"
        "treatments" -> "Soins"
        "journal" -> "Suivi"
        "mieux" -> "Mieux-être"
        "moi" -> "Moi"
        "settings" -> "Paramètres"
        "find" -> "Recherche"
        "backup" -> "Sauvegarde"
        "home_layout" -> "Mon accueil"
        "cards_layout" -> "Mes cartes"
        "tour" -> "Petit tour"
        "support" -> "Ça ne va pas"
        "sleep_help" -> "Nuit difficile"
        "report" -> "Rapport pour le médecin"
        else -> route.substringBefore('?').substringBefore('/').replace('_', ' ').replaceFirstChar(Char::uppercase)
    }

    fun add(list: List<Idea>, text: String, screen: String, now: LocalDateTime): List<Idea> {
        val clean = text.trim().take(2000)
        if (clean.isEmpty()) return list
        return (listOf(Idea("idea-${now.toString().replace(Regex("[^0-9]"), "")}-${list.size}", clean, screen, now)) + list).take(MAX)
    }

    fun remove(list: List<Idea>, id: String) = list.filterNot { it.id == id }

    private val DAY = DateTimeFormatter.ofPattern("d/MM/yyyy")

    /** Plain text, one idea per paragraph; nothing else of the app's data is in it. */
    fun shareText(list: List<Idea>, version: String): String = buildString {
        append("Idées et soucis pour MédiPote ").append(version).append("\n\n")
        list.sortedBy { it.at }.forEach { i ->
            append("• ").append(i.text)
            append("\n  (").append(i.at.format(DAY))
            if (i.screen.isNotBlank()) append(" · ").append(i.screen)
            append(")\n\n")
        }
    }.trimEnd()
}
