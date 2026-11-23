package com.lingoquest.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingoquest.app.ui.theme.LingoTheme
import com.lingoquest.engine.Lang
import com.lingoquest.engine.Pos
import com.lingoquest.engine.Stage

@Composable
fun LingoApp(vm: GameViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    LingoTheme {
        BackHandler(enabled = state.screen != Screen.HOME) { vm.back() }
        when (state.screen) {
            Screen.HOME -> HomeScreen(state, vm)
            Screen.PLAY -> PlayScreen(state, vm)
            Screen.WORDS -> WordsScreen(state, vm.wordRows(state.profile.course), onBack = vm::back)
        }
    }
}

fun Stage.label(): String = when (this) {
    Stage.WORD -> "Mot"
    Stage.NOUN_PHRASE -> "Groupe nominal"
    Stage.NOUN_PHRASE_ADJ -> "Groupe nominal + adjectif"
    Stage.NOUN_PHRASE_COMPLEX -> "Groupe nominal complexe"
    Stage.SENTENCE_SIMPLE -> "Phrase simple"
    Stage.SENTENCE_COMPLEX -> "Phrase complexe"
}

fun Pos.label(): String = when (this) {
    Pos.NOUN -> "nom"
    Pos.VERB -> "verbe"
    Pos.ADJ -> "adjectif"
    Pos.ADV -> "adverbe"
    Pos.WORD -> "mot"
}

fun Lang.label(): String = nameFr
