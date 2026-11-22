package com.medipote.app.domain

import com.medipote.app.data.Treatment
import kotlin.math.abs

/** A thing to know to take a medication well: when, with what, how far from another. */
data class Precaution(val emoji: String, val text: String, val warn: Boolean = false)

/**
 * « À savoir pour bien la prendre »: the well-established habits of use of common medications, and the gap to keep from the
 * ones that get in each other's way. Deliberately short, general and from the usual leaflets; the leaflet and the pharmacist
 * always prevail, and the list is for a pharmacist to review.
 */
object Precautions {
    private val BINDERS = setOf("calcium_vitd", "fer", "magnesium", "alginate", "diosmectite")
    private val QUINOLONES = setOf("ciprofloxacine", "ofloxacine", "levofloxacine")
    private val PPI = setOf("omeprazole", "esomeprazole", "pantoprazole", "lansoprazole")
    private val NSAID = setOf("ibuprofene", "ketoprofene", "naproxene", "diclofenac")
    private val SEDATING = setOf("alprazolam", "bromazepam", "oxazepam", "zolpidem", "zopiclone", "hydroxyzine", "tramadol", "paracetamol_codeine", "pregabaline", "gabapentine")

    private val NOTES: Map<String, List<String>> = buildMap {
        put("levothyroxine", listOf("⏰|Le matin à jeun, avec un verre d'eau, 30 à 60 minutes avant le petit-déjeuner.", "☕|Le café et le soja gênent son absorption : attends un peu avant d'en boire."))
        put("fer", listOf("☕|À distance du thé, du café et des produits laitiers, qui gênent son absorption."))
        put("calcium_vitd", listOf("🍽️|À prendre au cours d'un repas."))
        put("alendronate", listOf("⏰|À jeun, avec un grand verre d'eau seule, au moins 30 minutes avant de manger ou de prendre autre chose.", "🧍|Reste assis·e ou debout pendant 30 minutes : ne te recouche pas."))
        put("metformine", listOf("🍽️|Pendant ou juste après le repas.", "🍷|Limite l'alcool."))
        PPI.forEach { put(it, listOf("⏰|Le matin, environ 30 minutes avant un repas.")) }
        put("atorvastatine", listOf("🍊|Évite le jus de pamplemousse."))
        put("simvastatine", listOf("🍊|Évite le jus de pamplemousse."))
        listOf("prednisone", "prednisolone").forEach { put(it, listOf("⏰|Le matin, pendant le repas.")) }
        NSAID.forEach { put(it, listOf("🍽️|Pendant un repas.", "⚠️|Pas d'autre anti-inflammatoire en même temps, ni d'aspirine sans avis.")) }
        put("paracetamol", listOf("🔎|Vérifie que tes autres médicaments ne contiennent pas déjà du paracétamol."))
        put("doxycycline", listOf("💧|Avec un grand verre d'eau, assis·e ou debout, jamais juste avant de t'allonger.", "☀️|Protège-toi du soleil : la peau devient plus sensible."))
        QUINOLONES.forEach { put(it, listOf("☀️|Protège-toi du soleil.", "🦵|Une douleur dans un tendon : arrête de forcer et appelle ton médecin.")) }
        put("warfarine", listOf("🥬|Garde une alimentation régulière en légumes verts (vitamine K), sans en changer d'un coup.", "⚠️|Pas d'aspirine ni d'anti-inflammatoire sans l'avis de ton médecin."))
        put("amiodarone", listOf("☀️|Protège-toi du soleil."))
        put("lithium", listOf("💧|Bois régulièrement.", "⚠️|Pas d'anti-inflammatoire sans l'avis de ton médecin."))
        SEDATING.forEach { put(it, listOf("🍷|L'alcool renforce la somnolence : évite-le.")) }
        listOf("zolpidem", "zopiclone").forEach { put(it, listOf("🌙|Juste avant de te coucher, quand tu peux dormir toute la nuit.")) }
        listOf("levonorgestrel_ee", "desogestrel").forEach { put(it, listOf("🤢|Un vomissement ou une forte diarrhée peut gêner l'efficacité : demande conseil à ton pharmacien.", "🌿|Le millepertuis (tisanes, compléments) réduit son efficacité.")) }
    }

    /** What to know about [drugId] alone. */
    fun notes(drugId: String?): List<Precaution> = NOTES[drugId].orEmpty().map { line ->
        val (emoji, text) = line.split('|', limit = 2)
        Precaution(emoji, text)
    }

    private data class Pair(val a: Set<String>, val b: Set<String>, val minutes: Int, val why: String)

    private val PAIRS = listOf(
        Pair(setOf("levothyroxine"), BINDERS, 240, "gênent l'absorption de la lévothyroxine"),
        Pair(setOf("doxycycline") + QUINOLONES, BINDERS, 120, "se lient à l'antibiotique et l'empêchent d'agir"),
        Pair(setOf("alendronate"), BINDERS + setOf("levothyroxine"), 30, "doivent attendre l'alendronate"),
        Pair(setOf("fer"), setOf("calcium_vitd"), 120, "se gênent l'un l'autre"),
    )

    private fun closest(a: List<Int>, b: List<Int>): Int? =
        a.flatMap { x -> b.map { y -> abs(x - y).let { d -> minOf(d, 24 * 60 - d) } } }.minOrNull()

    private fun label(drugId: String, others: List<Treatment>): String = others.firstOrNull { it.drugId == drugId }?.displayName ?: drugId

    /** The gap to keep between the medication [drugId] taken at [times] and the other treatments, with what it really is. */
    fun spacing(drugId: String?, times: List<Int>, others: List<Treatment>): List<Precaution> {
        if (drugId == null || times.isEmpty()) return emptyList()
        val active = others.filter { !it.asNeeded && it.drugId != null && it.doses.isNotEmpty() }
        return buildList {
            PAIRS.forEach { p ->
                val partners = when {
                    drugId in p.a -> active.filter { it.drugId in p.b }
                    drugId in p.b -> active.filter { it.drugId in p.a }
                    else -> emptyList()
                }
                partners.forEach { other ->
                    val gap = closest(times, other.doses.map { it.minuteOfDay }) ?: return@forEach
                    val name = other.displayName
                    val hours = if (p.minutes % 60 == 0) "${p.minutes / 60} h" else "${p.minutes} min"
                    if (gap < p.minutes) add(Precaution("⏱️", "$name est pris à ${if (gap < 60) "$gap min" else "${gap / 60} h"} d'écart : laisse au moins $hours entre les deux (${if (drugId in p.a) "ils" else "ceux-ci"} ${p.why}).", warn = true))
                    else add(Precaution("✅", "$name : tes horaires laissent assez d'écart (au moins $hours)."))
                }
            }
        }.distinctBy { it.text }
    }

    /** The pairs of treatments of the person taken too close to each other (each pair once): for the top of the list of treatments. */
    fun problems(treatments: List<Treatment>): List<kotlin.Pair<Treatment, Precaution>> {
        val active = treatments.filter { !it.asNeeded && it.drugId != null && it.doses.isNotEmpty() }
        return buildList {
            active.forEachIndexed { i, t ->
                active.drop(i + 1).forEach { u ->
                    val hit = spacing(t.drugId, t.doses.map { it.minuteOfDay }, listOf(u)).firstOrNull { it.warn }
                    if (hit != null) add(t to hit)
                }
            }
        }
    }

    /** Every medication of the guide this list speaks of (a check that each one exists there). */
    val knownIds: Set<String> get() = NOTES.keys + PAIRS.flatMap { it.a + it.b }

    const val DISCLAIMER = "Repères courants : la notice et ton pharmacien ont toujours le dernier mot."
}
