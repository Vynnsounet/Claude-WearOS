package com.lingoquest.engine

import org.junit.Test

class SpanishTest {
    private val mini = miniLexicon(
        Lang.ES,
        """
        n:apple|manzana|g=f
        n:man|hombre|g=m
        n:woman|mujer|g=f
        n:dog|perro|g=m
        n:house|casa|g=f
        n:park|parque|g=m
        n:water|agua|g=f|st=1
        a:big|grande
        a:tired|cansado|e=1
        a:red|rojo
        a:good|bueno|b=1|ap=buen
        v:eat|comer
        v:see|ver|p=veo,ves,ve,vemos,veis,ven|pp=visto
        v:go|ir|p=voy,vas,va,vamos,vais,van|pp=ido|loc=a
        v:come|venir|p=vengo,vienes,viene,venimos,venís,vienen|loc=a
        v:can|poder|p=puedo,puedes,puede,podemos,podéis,pueden
        v:want|querer|p=quiero,quieres,quiere,queremos,queréis,quieren
        v:decide|decidir
        v:start|empezar|p=empiezo,empiezas,empieza,empezamos,empezáis,empiezan|inf=a
        d:today|hoy
        """,
    )

    @Test
    fun frenchSentencesBecomeSpanish() = checkPairs(
        mini, Lang.ES,
        listOf(
            "^il mange " to "^él come ",
            "^elle mange " to "^ella come ",
            "^je mange " to "^yo como ",
            "^nous mangeons " to "^nosotros comemos ",
            "^ils mangent " to "^ellos comen ",
            "^elles mangent " to "^ellas comen ",
            "^tu manges " to "^tú comes ",
            "^vous mangez " to "^vosotros coméis ",
            "^il a mangé " to "^él ha comido ",
            "^il ne mange pas " to "^él no come ",
            "^elle n'a pas mangé " to "^ella no ha comido ",
            "^je vais manger " to "^yo voy a comer ",
            "^il ne va pas manger " to "^él no va a comer ",
            "^elle est grande" to "^ella es grande",
            "^il est grand" to "^él es grande",
            "^elle est fatiguée" to "^ella está cansada",
            "^ils sont fatigués" to "^ellos están cansados",
            "^elles sont fatiguées" to "^ellas están cansadas",
            "^il veut manger " to "^él quiere comer ",
            "^il peut manger " to "^él puede comer ",
            "^il ne peut pas manger " to "^él no puede comer ",
            "^il commence à manger " to "^él empieza a comer ",
            "^il décide de manger " to "^él decide comer ",
            "^il voit \\w+ femme" to "^él ve a \\w+ mujer",
            "^il voit le chien" to "^él ve el perro",
            "^je vais à la maison" to "^yo voy a la casa",
            "^il va au parc" to "^él va al parque",
            "^elle est venue" to "^ella ha venido",
        ),
    )

    @Test
    fun spanishToFrenchWorksToo() = checkPairs(
        mini, Lang.ES,
        listOf(
            "^il mange " to "^él come ",
        ),
        samples = 3000, toFrench = true,
    )
}
