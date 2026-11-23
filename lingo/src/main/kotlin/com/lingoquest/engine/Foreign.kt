package com.lingoquest.engine

internal object ForeignNotes {
    fun of(entry: Entry, lang: Lang, fx: Fx): String? = null
}

internal class SpanishRealizer(lexicon: Lexicon) : Realizer { override val lang = Lang.ES
    override fun realize(node: Node, sink: Sink): List<T> = TODO() }
internal class GermanRealizer(lexicon: Lexicon) : Realizer { override val lang = Lang.DE
    override fun realize(node: Node, sink: Sink): List<T> = TODO() }
internal class RussianRealizer(lexicon: Lexicon) : Realizer { override val lang = Lang.RU
    override fun realize(node: Node, sink: Sink): List<T> = TODO() }
internal class JapaneseRealizer(lexicon: Lexicon) : Realizer { override val lang = Lang.JA
    override fun realize(node: Node, sink: Sink): List<T> = TODO() }
