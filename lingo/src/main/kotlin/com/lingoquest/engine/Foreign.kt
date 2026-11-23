package com.lingoquest.engine

internal object ForeignNotes {
    fun of(entry: Entry, lang: Lang, fx: Fx): String? = when (lang) {
        Lang.ES -> EsMorph.note(entry, fx)
        Lang.DE -> DeMorph.note(entry, fx)
        Lang.RU -> RuMorph.note(entry, fx)
        else -> null
    }
}

internal class JapaneseRealizer(lexicon: Lexicon) : Realizer { override val lang = Lang.JA
    override fun realize(node: Node, sink: Sink): List<T> = TODO() }
