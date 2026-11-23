package com.lingoquest.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RussianTest {
    private val mini = miniLexicon(
        Lang.RU,
        """
        n:apple|яблоко|g=n|pl=яблоки
        n:man|мужчина|g=m|an=1|pl=мужчины|gp=мужчин
        n:woman|женщина|g=f|an=1|pl=женщины|gp=женщин
        n:dog|собака|g=f|an=1|pl=собаки|gp=собак
        n:house|дом|g=m|pl=дома|pr=доме
        n:park|парк|g=m|pl=парки
        n:water|вода|g=f
        a:big|большой
        a:tired|усталый
        a:red|красный
        a:good|хороший
        v:eat|есть|p=ем,ешь,ест,едим,едите,едят|past=ел,ела,ело,ели
        v:see|видеть|p=вижу,видишь,видит,видим,видите,видят
        v:go|идти|p=иду,идёшь,идёт,идём,идёте,идут|past=шёл,шла,шло,шли|loc=в:acc
        v:come|приходить|p=прихожу,приходишь,приходит,приходим,приходите,приходят|loc=в:acc
        v:can|мочь|p=могу,можешь,может,можем,можете,могут|past=мог,могла,могло,могли
        v:want|хотеть|p=хочу,хочешь,хочет,хотим,хотите,хотят|inf=
        v:decide|решать|p=решаю,решаешь,решает,решаем,решаете,решают|inf=
        v:start|начинать|p=начинаю,начинаешь,начинает,начинаем,начинаете,начинают|inf=
        d:today|сегодня
        """,
    )

    @Test
    fun frenchSentencesBecomeRussian() = checkPairs(
        mini, Lang.RU,
        listOf(
            "^il mange " to "^он ест ",
            "^elle mange " to "^она ест ",
            "^je mange " to "^я ем ",
            "^tu manges " to "^ты ешь ",
            "^nous mangeons " to "^мы едим ",
            "^vous mangez " to "^вы едите ",
            "^ils mangent " to "^они едят ",
            "^il a mangé " to "^он ел ",
            "^elle a mangé " to "^она ела ",
            "^ils ont mangé " to "^они ели ",
            "^je vais manger " to "^я буду есть ",
            "^elle va manger " to "^она будет есть ",
            "^elle est grande" to "^она большая",
            "^il est grand" to "^он большой",
            "^ils sont fatigués" to "^они усталые",
            "^elle était fatiguée" to "^она была усталая",
            "^il voulait|^il a voulu manger " to "^он хотел ",
            "^il veut manger " to "^он хочет есть ",
            "^il peut manger " to "^он может есть ",
            "^il commence à manger " to "^он начинает есть ",
            "^il décide de manger " to "^он решает есть ",
            "^il voit \\w+ femme\\b" to "^он видит (\\S+ )*женщину",
            "^il voit le chien\\b" to "^он видит (\\S+ )*собаку",
            "^il voit l'homme" to "^он видит (\\S+ )*мужчину",
            "^il va au parc" to "^он идёт в парк",
            "^il est allé " to "^он шёл ",
            "^elle est allée" to "^она шла",
            "^il est venu" to "^он приходил",
        ),
    )

    @Test
    fun nounCases() {
        val dog = Fx(listOf("собака"), mapOf("g" to "f", "an" to "1", "pl" to "собаки", "gp" to "собак"))
        assertEquals("собаку", RuMorph.noun(dog, false, RuCase.ACC))
        assertEquals("собаке", RuMorph.noun(dog, false, RuCase.PREP))
        assertEquals("собакой", RuMorph.noun(dog, false, RuCase.INSTR))
        assertEquals("собак", RuMorph.noun(dog, true, RuCase.ACC))
        assertEquals("собаками", RuMorph.noun(dog, true, RuCase.INSTR))
        assertEquals("собаках", RuMorph.noun(dog, true, RuCase.PREP))
        val brother = Fx(listOf("брат"), mapOf("g" to "m", "an" to "1", "pl" to "братья", "gp" to "братьев"))
        assertEquals("брата", RuMorph.noun(brother, false, RuCase.ACC))
        assertEquals("братом", RuMorph.noun(brother, false, RuCase.INSTR))
        assertEquals("братьями", RuMorph.noun(brother, true, RuCase.INSTR))
        val table = Fx(listOf("стол"), mapOf("g" to "m", "pl" to "столы"))
        assertEquals("стол", RuMorph.noun(table, false, RuCase.ACC))
        assertEquals("столе", RuMorph.noun(table, false, RuCase.PREP))
        val teacher = Fx(listOf("учитель"), mapOf("g" to "m", "an" to "1", "pl" to "учителя", "gp" to "учителей"))
        assertEquals("учителя", RuMorph.noun(teacher, false, RuCase.ACC))
        assertEquals("учителем", RuMorph.noun(teacher, false, RuCase.INSTR))
        val night = Fx(listOf("ночь"), mapOf("g" to "f", "pl" to "ночи"))
        assertEquals("ночь", RuMorph.noun(night, false, RuCase.ACC))
        assertEquals("ночью", RuMorph.noun(night, false, RuCase.INSTR))
        val story = Fx(listOf("история"), mapOf("g" to "f", "pl" to "истории"))
        assertEquals("истории", RuMorph.noun(story, false, RuCase.PREP))
        assertEquals("историей", RuMorph.noun(story, false, RuCase.INSTR))
        val child = Fx(listOf("ребёнок"), mapOf("g" to "m", "an" to "1", "st" to "ребёнк", "pl" to "дети", "gp" to "детей", "ip" to "детьми"))
        assertEquals("ребёнка", RuMorph.noun(child, false, RuCase.ACC))
        assertEquals("детьми", RuMorph.noun(child, true, RuCase.INSTR))
    }

    @Test
    fun adjectiveCases() {
        assertEquals("большая", RuMorph.adjective("большой", 'f', RuCase.NOM, false))
        assertEquals("большие", RuMorph.adjective("большой", 'p', RuCase.NOM, false))
        assertEquals("большого", RuMorph.adjective("большой", 'm', RuCase.ACC, true))
        assertEquals("большой", RuMorph.adjective("большой", 'm', RuCase.ACC, false))
        assertEquals("большим", RuMorph.adjective("большой", 'm', RuCase.INSTR, false))
        assertEquals("хорошая", RuMorph.adjective("хороший", 'f', RuCase.NOM, false))
        assertEquals("хорошее", RuMorph.adjective("хороший", 'n', RuCase.NOM, false))
        assertEquals("хорошего", RuMorph.adjective("хороший", 'm', RuCase.ACC, true))
        assertEquals("хорошей", RuMorph.adjective("хороший", 'f', RuCase.PREP, false))
        assertEquals("синяя", RuMorph.adjective("синий", 'f', RuCase.NOM, false))
        assertEquals("синюю", RuMorph.adjective("синий", 'f', RuCase.ACC, false))
        assertEquals("маленькие", RuMorph.adjective("маленький", 'p', RuCase.NOM, false))
        assertEquals("новую", RuMorph.adjective("новый", 'f', RuCase.ACC, false))
        assertEquals("новых", RuMorph.adjective("новый", 'p', RuCase.PREP, false))
        assertEquals("красными", RuMorph.adjective("красный", 'p', RuCase.INSTR, false))
        assertEquals("моя", RuMorph.possessive("мо", 'f', RuCase.NOM, false))
        assertEquals("мою", RuMorph.possessive("мо", 'f', RuCase.ACC, false))
        assertEquals("нашем", RuMorph.possessive("наш", 'm', RuCase.PREP, false))
        assertEquals("этого", RuMorph.possessive("эт", 'm', RuCase.ACC, true))
        assertEquals("этот", RuMorph.possessive("эт", 'm', RuCase.NOM, false))
    }

    @Test
    fun pastTense() {
        val read = Fx(listOf("читать"), mapOf("p" to "читаю,читаешь,читает,читаем,читаете,читают"))
        assertEquals("читал", RuMorph.past(read, 'm'))
        assertEquals("читала", RuMorph.past(read, 'f'))
        assertEquals("читали", RuMorph.past(read, 'p'))
        val stay = Fx(listOf("оставаться"), mapOf("p" to "остаюсь,остаёшься,остаётся,остаёмся,остаётесь,остаются"))
        assertEquals("оставался", RuMorph.past(stay, 'm'))
        assertEquals("оставалась", RuMorph.past(stay, 'f'))
    }
}
