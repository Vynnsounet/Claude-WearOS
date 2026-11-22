package com.medipote.app.domain

import com.medipote.app.data.Drug
import com.medipote.app.data.DrugGuide
import java.io.InputStream
import java.text.Normalizer
import java.util.zip.GZIPInputStream

/** What the scan of a French medicine box says: its CIP code, the 13 digits that name the box. */
object CipCode {
    private val GS1 = Regex("""01(0340\d{10})""")
    private val EAN = Regex("""^(340\d{10})$""")

    /** EAN-13 check digit. */
    fun valid(code: String): Boolean {
        if (code.length != 13 || !code.all(Char::isDigit)) return false
        val sum = code.take(12).mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10 == code[12] - '0'
    }

    /** The CIP13 in what a scanner read: the Data Matrix of the box (GS1), the bar code (EAN-13) or just the digits; null if there is none. */
    fun parse(raw: String): String? {
        val clean = raw.filter { it.isLetterOrDigit() }
        val gs1 = GS1.find(clean)?.groupValues?.get(1)?.drop(1)
        val candidate = gs1 ?: EAN.find(clean.filter(Char::isDigit))?.groupValues?.get(1)
        return candidate?.takeIf(::valid)
    }
}

/** The box a code stands for: its name in the public database, and the entry of the built-in guide it matches, if any. */
data class MedicineBox(val cip: String, val denomination: String, val drug: Drug?, val strength: String)

/** CIP13 → name of every box on sale in France (« Base de données publique des médicaments », Licence Ouverte). */
class MedicineDirectory(private val byCip: Map<String, String>) {
    val size: Int get() = byCip.size

    fun lookup(cip13: String): String? = byCip[cip13]

    /** The box of a scan, identified; null if the database does not know it. */
    fun identify(raw: String): MedicineBox? {
        val cip = CipCode.parse(raw) ?: return null
        val name = byCip[cip] ?: return null
        return Medicines.box(cip, name)
    }

    companion object {
        fun parse(input: InputStream): MedicineDirectory {
            val map = HashMap<String, String>(24_000)
            GZIPInputStream(input).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { !it.startsWith("#") }.forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab == 13) map[line.substring(0, 13)] = line.substring(14)
                }
            }
            return MedicineDirectory(map)
        }
    }
}

object Medicines {
    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private data class Key(val key: String, val drug: Drug)

    /** What each guide entry is called: its brand names and its generic name. The longest match wins (« paracétamol codéine » over « paracétamol »). */
    private val KEYS: List<Key> by lazy {
        DrugGuide.drugs.flatMap { d -> (d.name.split(',').map { it.trim() } + d.dci).map { Key(norm(it), d) } }.filter { it.key.length >= 4 }
    }

    private val STRENGTH = Regex("""(\d+(?:[.,]\d+)?)\s*(microgrammes?|µg|mcg|mg|g|ml|ui|%)(?![a-z])""", RegexOption.IGNORE_CASE)

    fun strength(denomination: String): String {
        val m = STRENGTH.find(denomination) ?: return ""
        val unit = when (val u = m.groupValues[2].lowercase()) { "microgramme", "microgrammes", "mcg" -> "µg"; "ui" -> "UI"; else -> u }
        return "${m.groupValues[1]} $unit"
    }

    fun drugFor(denomination: String): Drug? {
        val n = norm(denomination)
        return KEYS.filter { n == it.key || n.startsWith(it.key + " ") }.maxByOrNull { it.key.length }?.drug
    }

    fun box(cip: String, denomination: String): MedicineBox {
        val drug = drugFor(denomination)
        val raw = strength(denomination)
        // The guide's own wording of a strength when it has it ("75 µg"), else the one of the box.
        val strength = drug?.strengths?.firstOrNull { norm(it) == norm(raw) } ?: raw
        return MedicineBox(cip, denomination, drug, strength)
    }

    /** « LEVOTHYROX 75 microgrammes, comprimé sécable » → « Levothyrox » for a medication typed by hand. */
    fun displayName(denomination: String): String {
        val head = denomination.substringBefore(',').trim()
        val words = head.split(' ').takeWhile { w -> w.firstOrNull()?.isDigit() != true }
        return words.joinToString(" ").lowercase().replaceFirstChar(Char::uppercase).ifBlank { head }
    }
}

/** The directory of boxes, read once from the file shipped in the app (about 20 000 boxes, 230 kB). */
object MedicineDirectoryStore {
    @Volatile private var cached: MedicineDirectory? = null

    fun get(context: android.content.Context): MedicineDirectory = cached ?: synchronized(this) {
        cached ?: context.applicationContext.assets.open("cip.tsv.gz").use(MedicineDirectory::parse).also { cached = it }
    }
}
