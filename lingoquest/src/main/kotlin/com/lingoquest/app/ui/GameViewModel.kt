package com.lingoquest.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lingoquest.app.profileStore
import com.lingoquest.engine.Cefr
import com.lingoquest.engine.Challenge
import com.lingoquest.engine.Direction
import com.lingoquest.engine.Entry
import com.lingoquest.engine.GameSession
import com.lingoquest.engine.Judge
import com.lingoquest.engine.Lexicon
import com.lingoquest.engine.Outcome
import com.lingoquest.engine.Profile
import com.lingoquest.engine.Progression
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Screen { HOME, PLAY, WORDS }

/** What the player just answered, shown until they tap "Continuer". */
data class Feedback(val outcome: Outcome, val challenge: Challenge, val given: String)

/** End-of-series recap, shown after the third mistake. */
data class RunSummary(val reached: Int, val xp: Int, val best: Int, val newBest: Boolean)

/** A lexicon entry with the player level at which it unlocks. */
data class WordRow(val entry: Entry, val unlockLevel: Int)

data class UiState(
    val screen: Screen,
    val profile: Profile,
    val level: Int,
    val cefr: Cefr,
    val levelProgress: Float,
    val xpInLevel: Long,
    /** XP needed for this level; 0 at the maximum level. */
    val xpForNext: Int,
    val unlockedWords: Int,
    val totalWords: Int,
    /** Words the next level will unlock. */
    val nextLevelWords: Int,
    val challenge: Challenge?,
    val step: Int,
    val lives: Int,
    val runXp: Int,
    val typed: String = "",
    /** Indices into the tile bank, in the order the player tapped them. */
    val picked: List<Int> = emptyList(),
    val feedback: Feedback? = null,
    val runOver: RunSummary? = null,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = app.profileStore
    private val lexicon = Lexicon.load()
    private val session = GameSession(lexicon, store.load())
    private var pendingSummary: RunSummary? = null

    /** Every word with the level that unlocks it, for the vocabulary screen. */
    val wordRows: List<WordRow> by lazy {
        val unlockLevel = HashMap<String, Int>()
        for (level in 1..Progression.MAX_LEVEL) {
            Progression.newWordsAt(lexicon, level).forEach { unlockLevel.putIfAbsent(it.id, level) }
        }
        lexicon.entries.map { WordRow(it, unlockLevel[it.id] ?: Progression.MAX_LEVEL) }
    }

    private val _ui = MutableStateFlow(snapshot(Screen.HOME))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private fun snapshot(screen: Screen): UiState {
        val profile = session.profile
        val level = session.level
        return UiState(
            screen = screen,
            profile = profile,
            level = level,
            cefr = session.cefr,
            levelProgress = Progression.levelProgress(profile.xp),
            xpInLevel = profile.xp - Progression.xpForLevel(level),
            xpForNext = if (level >= Progression.MAX_LEVEL) 0 else Progression.xpToNext(level),
            unlockedWords = Progression.unlockedCount(lexicon, level),
            totalWords = lexicon.entries.size,
            nextLevelWords = if (level >= Progression.MAX_LEVEL) 0 else Progression.newWordsAt(lexicon, level + 1).size,
            challenge = session.challenge,
            step = session.step,
            lives = session.lives,
            runXp = session.runXp,
        )
    }

    private fun refresh(update: (UiState) -> UiState = { it }) {
        val current = _ui.value
        _ui.value = update(
            snapshot(current.screen).copy(
                typed = current.typed, picked = current.picked,
                feedback = current.feedback, runOver = current.runOver,
            ),
        )
    }

    fun startRun() {
        pendingSummary = null
        session.startRun()
        _ui.value = snapshot(Screen.PLAY)
    }

    fun openWords() {
        _ui.value = snapshot(Screen.WORDS)
    }

    /** Back button / close: leaves the series (an abandoned series is simply not counted). */
    fun back() {
        pendingSummary = null
        _ui.value = snapshot(Screen.HOME)
    }

    fun setDirection(direction: Direction) {
        if (direction == session.profile.direction) return
        session.setDirection(direction)
        store.save(session.profile)
        refresh()
    }

    fun onTyped(text: String) {
        if (_ui.value.feedback != null) return
        _ui.value = _ui.value.copy(typed = text)
    }

    fun pickTile(index: Int) {
        val state = _ui.value
        if (state.feedback != null || index in state.picked) return
        _ui.value = state.copy(picked = state.picked + index)
    }

    fun unpickTile(position: Int) {
        val state = _ui.value
        if (state.feedback != null || position !in state.picked.indices) return
        _ui.value = state.copy(picked = state.picked.filterIndexed { i, _ -> i != position })
    }

    fun clearTiles() {
        _ui.value = _ui.value.copy(picked = emptyList())
    }

    fun submit() {
        val state = _ui.value
        val challenge = state.challenge ?: return
        if (state.feedback != null) return
        val answer = if (challenge.typed) state.typed else Judge.fromTiles(state.picked.map { challenge.bank[it] })
        if (answer.isBlank()) return

        val bestBefore = session.profile.bestStep
        val outcome = session.submit(answer)
        store.save(session.profile)
        pendingSummary = if (outcome.runOver) {
            RunSummary(outcome.step, session.runXp, session.profile.bestStep, outcome.step > bestBefore)
        } else null
        _ui.value = snapshot(Screen.PLAY).copy(feedback = Feedback(outcome, challenge, answer))
    }

    /** "Continuer": moves on to the next challenge, or to the recap when the series is over. */
    fun next() {
        _ui.value = snapshot(Screen.PLAY).copy(feedback = null, runOver = pendingSummary)
    }
}
