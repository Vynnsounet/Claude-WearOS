package com.lingoquest.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanTest {
    private val mini = miniLexicon(
        Lang.DE,
        """
        n:apple|Apfel|g=m|pl=Äpfel
        n:man|Mann|g=m|pl=Männer
        n:woman|Frau|g=f|pl=Frauen
        n:dog|Hund|g=m|pl=Hunde
        n:house|Haus|g=n|pl=Häuser
        n:park|Park|g=m|pl=Parks
        n:water|Wasser|g=n|pl=Wasser
        a:big|groß
        a:tired|müde
        a:red|rot
        a:good|gut
        v:eat|essen|p=esse,isst,isst,essen,esst,essen|pp=gegessen
        v:see|sehen|p=sehe,siehst,sieht,sehen,seht,sehen|pp=gesehen
        v:go|gehen|pp=gegangen|aux=sein|loc=in:acc
        v:come|kommen|pp=gekommen|aux=sein|loc=in:acc
        v:can|können|p=kann,kannst,kann,können,könnt,können|pp=gekonnt
        v:want|wollen|p=will,willst,will,wollen,wollt,wollen|pp=gewollt|pt=wollte,wolltest,wollte,wollten,wolltet,wollten|inf=
        v:decide|beschließen|p=beschließe,beschließt,beschließt,beschließen,beschließt,beschließen|pp=beschlossen|inf=zu
        v:start|anfangen|p=fange,fängst,fängt,fangen,fangt,fangen|pp=angefangen|sep=an|inf=zu
        d:today|heute|t=1
        """,
    )

    @Test
    fun frenchSentencesBecomeGerman() = checkPairs(
        mini, Lang.DE,
        listOf(
            "^il mange " to "^er isst ",
            "^elle mange " to "^sie isst ",
            "^je mange " to "^ich esse ",
            "^tu manges " to "^du isst ",
            "^nous mangeons " to "^wir essen ",
            "^vous mangez " to "^ihr esst ",
            "^ils mangent " to "^sie essen ",
            "^il a mangé " to "^er hat .*gegessen",
            "^il ne mange pas " to "^er isst .*(nicht|kein\\w*)",
            "^elle a mangé " to "^sie hat .*gegessen",
            "^je vais manger " to "^ich werde .*essen",
            "^il ne va pas manger " to "^er wird .*(nicht|kein\\w*) .*essen",
            "^elle est grande" to "^sie ist groß",
            "^elle est fatiguée" to "^sie ist müde",
            "^ils sont fatigués" to "^sie sind müde",
            "^il veut manger " to "^er will .*essen",
            "^il voulait|^il a voulu manger " to "^er wollte .*essen",
            "^il peut manger " to "^er kann .*essen",
            "^il ne peut pas manger " to "^er kann .*(nicht|kein\\w*).* essen",
            "^il décide de manger " to "^er beschließt.* zu essen",
            "^il commence à manger " to "^er fängt .*an.* zu essen",
            "^il voit le chien\\b" to "^er sieht (heute |jetzt )?den (\\S+ )*hund",
            "^il voit \\w+ femme\\b" to "^er sieht (heute |jetzt )?\\S+ (\\S+ )*frau",
            "^il voit le père|^il voit l'homme" to "^er sieht (heute |jetzt )?den ",
            "^il est allé " to "^er ist .*gegangen",
            "^elle est allée" to "^sie ist .*gegangen",
            "^il va à la maison" to "^er geht (heute |jetzt )?(in das haus|ins haus)",
            "^il va au parc" to "^er geht (heute |jetzt )?in den park",
            "^le chien voit" to "^der hund sieht",
            "^la femme voit" to "^die frau sieht",
            "^les chiens voient" to "^die hunde sehen",
        ),
    )

    @Test
    fun subordinateClausesPutTheVerbLast() = checkPairs(
        mini, Lang.DE,
        listOf(
            "parce qu'il mange\\b|parce qu'elle mange\\b" to "weil (er|sie) .*isst",
            " et il mange| et elle mange" to " und (er|sie) isst",
            "^s'il |^s'ils |^si elle " to "^wenn",
        ),
        samples = 60000,
    )

    @Test
    fun fixedExamples() {
        // Spot checks of the declension tables.
        assertEquals("den", DeMorph.article('m', false, DeCase.ACC))
        assertEquals("dem", DeMorph.article('n', false, DeCase.DAT))
        assertEquals("den", DeMorph.article('m', true, DeCase.DAT))
        assertEquals("einen", DeMorph.einWord("ein", 'm', false, DeCase.ACC))
        assertEquals("eure", DeMorph.einWord("euer", 'f', false, DeCase.NOM))
        assertEquals("euer", DeMorph.einWord("euer", 'm', false, DeCase.NOM))
    }
}
