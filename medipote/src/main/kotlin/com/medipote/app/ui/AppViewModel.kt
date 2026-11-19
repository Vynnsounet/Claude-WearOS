package com.medipote.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medipote.app.data.AppState
import com.medipote.app.data.Appointment
import com.medipote.app.data.CycleInfo
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DocumentStore
import com.medipote.app.data.Flow
import com.medipote.app.data.HealthDocument
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.TakeStatus
import com.medipote.app.data.Treatment
import com.medipote.app.domain.CheckInReward
import com.medipote.app.domain.Cycle
import com.medipote.app.domain.DoseItem
import com.medipote.app.domain.Game
import com.medipote.app.domain.Shop
import com.medipote.app.domain.ShopItem
import com.medipote.app.notifications.Notifications
import com.medipote.app.notifications.ReminderScheduler
import com.medipote.app.repository
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiEvent {
    data class Fed(val treatmentName: String, val status: TakeStatus, val points: Int, val perfectDayBonus: Int) : UiEvent
    data class CheckedIn(val reward: CheckInReward) : UiEvent
    data class Bought(val item: ShopItem) : UiEvent
    data class Message(val text: String) : UiEvent

    /** Points won outside a dose (water…), shown floating above the pet. */
    data class Points(val amount: Int) : UiEvent

    data class BadgeEarned(val badge: com.medipote.app.domain.Badge) : UiEvent
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = app.repository
    val state: StateFlow<AppState> = repo.state

    private val _now = MutableStateFlow(repo.now())
    /** Current time, refreshed every 20 seconds so dose states and the pet's mood stay live. */
    val now: StateFlow<LocalDateTime> = _now.asStateFlow()

    // A channel, not a shared flow: an event sent before the screen starts listening (the app's
    // first frame, when the daily check-in happens) waits for it instead of being lost.
    private val _events = kotlinx.coroutines.channels.Channel<UiEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val events: kotlinx.coroutines.flow.Flow<UiEvent> = _events.receiveAsFlow()

    /** The daily streak screen waiting to be shown; kept here so a rotation doesn't lose it. */
    private val _checkIn = MutableStateFlow<UiEvent.CheckedIn?>(null)
    val checkIn: StateFlow<UiEvent.CheckedIn?> = _checkIn.asStateFlow()
    fun checkInShown() {
        _checkIn.value = null
    }

    /** Shows the streak screen again, with the current streak: nothing is counted or won. */
    fun previewStreak() {
        val g = state.value.game
        val streak = g.streak.coerceAtLeast(1)
        _checkIn.value = UiEvent.CheckedIn(CheckInReward(streak, com.medipote.app.domain.Rules.streakReward(streak), 0, 0, maxOf(g.bestStreak, streak)))
    }

    init {
        viewModelScope.launch {
            while (true) {
                delay(20_000)
                _now.value = repo.now()
            }
        }
    }

    /** Called when the app comes to the foreground: daily streak and alarm refresh. */
    fun onResume() {
        _now.value = repo.now()
        if (!state.value.onboarded) return
        var reward: CheckInReward? = null
        repo.update { s ->
            val result = Game.checkIn(s.game, repo.now()) ?: return@update s
            reward = result.second
            s.copy(game = result.first)
        }
        reward?.let { _checkIn.value = UiEvent.CheckedIn(it) }
        // The companion's wish of the week is chosen once, on the first opening of the week.
        repo.update { com.medipote.app.domain.Wishes.refresh(it, repo.now().toLocalDate()) }
        // The companion's birthday is the first day of use: noted once, so it never moves.
        if (state.value.settings.seasons.petBirthday == null) {
            repo.update { s ->
                s.copy(settings = s.settings.copy(seasons = s.settings.seasons.copy(petBirthday = com.medipote.app.domain.Seasons.petBirthday(s, repo.now().toLocalDate()))))
            }
        }
        reschedule()
        checkAchievements()
    }

    fun give(item: DoseItem, early: Boolean = false) {
        val now = repo.now()
        var fed: UiEvent.Fed? = null
        var lowStock: Treatment? = null
        val stageBefore = com.medipote.app.domain.Evolution.growth(state.value).stage
        repo.update { s ->
            val result = Game.give(s, item, now, early) ?: return@update s
            fed = UiEvent.Fed(item.occurrence.treatment.name, result.log.status, result.log.points, result.perfectDayBonus)
            lowStock = result.lowStock
            result.state
        }
        lowStock?.let {
            Notifications.showStock(getApplication(), state.value)
            reschedule()
        }
        fed?.let {
            _now.value = now
            _events.trySend(it)
            val ctx = getApplication<Application>()
            val time = item.occurrence.scheduled
            val logs = state.value.doseLogs.mapTo(HashSet()) { l -> l.key }
            val sameTime = com.medipote.app.domain.Schedule.occurrencesOn(state.value.treatments, time.toLocalDate())
                .filter { o -> o.scheduled == time }
            if (sameTime.all { o -> o.key in logs }) Notifications.cancel(ctx, time)
        }
        // Emitted after the "fed" message so it shows next instead of being replaced by it.
        val stageAfter = com.medipote.app.domain.Evolution.growth(state.value).stage
        if (stageAfter.ordinal > stageBefore.ordinal) {
            _events.trySend(UiEvent.Message("✨ ${state.value.game.petName} a grandi : ${stageAfter.label.lowercase()} ! (${stageAfter.description})"))
        }
        if (fed != null) checkAchievements()
    }

    fun saveProfile(profile: Profile) {
        repo.update { it.copy(profile = profile) }
    }

    fun namePet(name: String) {
        repo.update { it.copy(game = it.game.copy(petName = name.trim().ifBlank { "Pilou" })) }
    }

    fun finishOnboarding() {
        repo.update { it.copy(onboarded = true) }
        onResume()
    }

    fun saveTreatment(edited: Treatment) {
        val treatment = if (edited.createdAt == null) edited.copy(createdAt = repo.now()) else edited
        repo.update { s -> com.medipote.app.domain.Schedule.applyEdit(s, treatment, repo.now()) }
        reschedule()
        com.medipote.app.widget.Widgets.requestUpdate(getApplication())
    }

    fun deleteTreatment(id: String) {
        repo.update { s -> s.copy(treatments = s.treatments.filterNot { it.id == id }) }
        reschedule()
    }

    fun buy(item: ShopItem) {
        var bought = false
        repo.update { s ->
            val game = Shop.buy(s.game, item) ?: return@update s
            bought = true
            s.copy(game = game)
        }
        if (bought) {
            _events.trySend(UiEvent.Bought(item))
            if (item.id == state.value.game.wishId) _events.trySend(UiEvent.Message("🎁 ${state.value.game.petName} est ravi·e : son souhait de la semaine est exaucé !"))
        }
        checkAchievements()
    }

    fun equip(item: ShopItem) {
        repo.update { s -> s.copy(game = Shop.equip(s.game, item)) }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        repo.update { it.copy(settings = transform(it.settings)) }
        reschedule()
    }

    fun logPeriod(date: LocalDate) {
        repo.update { s ->
            val p = s.profile ?: return@update s
            val info = p.cycle ?: CycleInfo(emptyList())
            s.copy(profile = p.copy(menstruating = true, cycle = Cycle.logPeriod(info, date)))
        }
    }

    fun updateCycle(transform: (CycleInfo) -> CycleInfo) {
        repo.update { s ->
            val p = s.profile ?: return@update s
            s.copy(profile = p.copy(cycle = transform(p.cycle ?: CycleInfo(emptyList()))))
        }
    }

    fun resetAll() {
        DocumentStore.dir(getApplication()).deleteRecursively()
        repo.reset()
        reschedule()
    }

    // ── Journal and water ──

    fun saveEntry(entry: DayEntry) {
        var bonus = 0
        repo.update { s ->
            val (updated, b) = Game.saveEntry(s, entry, repo.now())
            bonus = b
            // Logging a period flow with no period start in the previous 10 days starts a new cycle.
            val p = updated.profile
            if (entry.flow != null && entry.flow != Flow.SPOTTING && p != null && p.menstruating) {
                val info = p.cycle ?: CycleInfo(emptyList())
                val recent = info.periodStarts.any { !it.isAfter(entry.date) && !it.isBefore(entry.date.minusDays(10)) }
                if (!recent) return@update updated.copy(profile = p.copy(cycle = Cycle.logPeriod(info, entry.date)))
            }
            updated
        }
        _events.trySend(UiEvent.Message(if (bonus > 0) "Journal enregistré : +$bonus pts" else "Journal enregistré"))
        checkAchievements()
    }

    fun addWater(delta: Int) {
        var reward: com.medipote.app.domain.WaterReward? = null
        repo.update { s ->
            val (updated, r) = Game.addWater(s, delta, repo.now())
            reward = r
            updated
        }
        val r = reward ?: return
        if (r.total > 0) _events.trySend(UiEvent.Points(r.total))
        if (r.goalBonus > 0) {
            Notifications.cancelWater(getApplication())
            _events.trySend(UiEvent.Message("Objectif d'hydratation atteint : +${r.goalBonus} pts bonus 💧"))
        }
        checkAchievements()
    }

    /** Petting or hugging the companion: a little bonus the first time each day. */
    /** "Already taken": a dose planned later today, taken in advance. */
    fun giveEarly(item: DoseItem) = give(item, early = true)

    // ── As-needed medications ──

    fun savePrnIntake(intake: com.medipote.app.data.PrnIntake) {
        var low = false
        repo.update { s ->
            val before = s.treatments.firstOrNull { it.id == intake.treatmentId }?.isLowStock ?: false
            val updated = com.medipote.app.domain.Prn.save(s, intake)
            low = !before && (updated.treatments.firstOrNull { it.id == intake.treatmentId }?.isLowStock ?: false)
            updated
        }
        if (low) Notifications.showStock(getApplication(), state.value)
        reschedule()
    }

    fun deletePrnIntake(id: String) {
        repo.update { com.medipote.app.domain.Prn.delete(it, id) }
        reschedule()
    }

    // ── Doses noted afterwards ──

    fun catchUp(item: DoseItem, takenAt: LocalDateTime) {
        var points = 0
        var lowStock: Treatment? = null
        repo.update { s ->
            val r = Game.catchUp(s, item, takenAt, repo.now()) ?: return@update s
            points = r.log.points + r.perfectDayBonus
            lowStock = r.lowStock
            r.state
        }
        if (lowStock != null) Notifications.showStock(getApplication(), state.value)
        _now.value = repo.now()
        _events.trySend(UiEvent.Message("Prise notée : +$points pts · ${state.value.game.petName} va mieux 💚"))
        reschedule()
        checkAchievements()
    }

    fun undoDose(key: String) {
        repo.update { Game.undo(it, key, repo.now()) }
        _now.value = repo.now()
        _events.trySend(UiEvent.Message("Prise annulée"))
        reschedule()
    }

    // ── App lock ──

    private val _locked = MutableStateFlow(state.value.settings.lockEnabled && state.value.onboarded)
    /** True while the app waits for the phone's fingerprint, face or PIN. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var stoppedAt: Long? = null

    fun onAppStop() {
        stoppedAt = android.os.SystemClock.elapsedRealtime()
    }

    fun onAppStart() {
        val since = stoppedAt?.let { android.os.SystemClock.elapsedRealtime() - it } ?: return
        if (state.value.settings.lockEnabled && since > AppLock.TIMEOUT_MS) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    // ── Health measurements ──

    fun saveMeasurement(m: com.medipote.app.data.Measurement) {
        repo.update { com.medipote.app.domain.Measures.save(it, m) }
        _events.trySend(UiEvent.Message("Mesure enregistrée"))
        // The first one of the day earns a little; a measure noted for an earlier day does not.
        if (m.at.toLocalDate() == repo.now().toLocalDate()) careReward { com.medipote.app.domain.Care.measureNoted(it, repo.now()) }
        checkAchievements()
    }

    fun deleteMeasurement(id: String) {
        repo.update { com.medipote.app.domain.Measures.delete(it, id) }
    }

    // ── Sleep ──

    fun saveSleep(night: com.medipote.app.data.SleepNight) {
        var bonus = 0
        repo.update { s -> com.medipote.app.domain.Sleep.save(s, night, repo.now()).let { (u, b) -> bonus = b; u } }
        if (night.date == repo.now().toLocalDate()) Notifications.cancelSleep(getApplication())
        _events.trySend(UiEvent.Message(if (bonus > 0) "Nuit enregistrée : +$bonus pts" else "Nuit enregistrée"))
        if (bonus > 0) _events.trySend(UiEvent.Points(bonus))
        reschedule()
        checkAchievements()
    }

    fun updateSeasons(transform: (com.medipote.app.data.SeasonSettings) -> com.medipote.app.data.SeasonSettings) {
        repo.update { it.copy(settings = it.settings.copy(seasons = transform(it.settings.seasons))) }
    }

    // ── Hard days: symptoms, small missions, tasks cut into steps ──

    fun updateWellbeing(transform: (com.medipote.app.data.WellbeingSettings) -> com.medipote.app.data.WellbeingSettings) {
        repo.update { it.copy(settings = it.settings.copy(wellbeing = transform(it.settings.wellbeing))) }
    }

    fun setSymptom(symptom: String, intensity: Int?) {
        val today = repo.now().toLocalDate()
        repo.update { com.medipote.app.domain.Wellbeing.setIntensity(it, today, symptom, intensity) }
        if (intensity != null) careReward { com.medipote.app.domain.Care.symptomNoted(it, repo.now()) }
        checkAchievements()
    }

    /** "Ce que je ressens": notes what was felt as symptoms for today (and turns the tracking on). */
    fun noteFelt(symptoms: List<String>, intensity: Int) {
        val today = repo.now().toLocalDate()
        repo.update { com.medipote.app.domain.Wellbeing.noteFelt(it, today, symptoms, intensity) }
        _events.trySend(UiEvent.Message("Noté dans tes symptômes 📝"))
        careReward { com.medipote.app.domain.Care.symptomNoted(it, repo.now()) }
    }

    /** A symptom's strength on any day (from the journal of that day). */
    fun setSymptomOn(date: java.time.LocalDate, symptom: String, intensity: Int?) {
        repo.update { com.medipote.app.domain.Wellbeing.setIntensity(it, date, symptom, intensity) }
        if (intensity != null && date == repo.now().toLocalDate()) careReward { com.medipote.app.domain.Care.symptomNoted(it, repo.now()) }
    }

    fun setSymptomNote(symptom: String, note: String) {
        val today = repo.now().toLocalDate()
        repo.update { com.medipote.app.domain.Wellbeing.setNote(it, today, symptom, note) }
    }

    fun setEnergy(level: Int) {
        val today = repo.now().toLocalDate()
        repo.update { com.medipote.app.domain.Wellbeing.setEnergy(it, today, level) }
    }

    /** A small reward for taking care: a discreet "+N pts" bubble, nothing when it was already given. */
    private fun careReward(block: (AppState) -> com.medipote.app.domain.CareReward) {
        var points = 0
        repo.update { s -> block(s).also { points = it.points }.state }
        if (points > 0) {
            _events.trySend(UiEvent.Points(points))
            _events.trySend(UiEvent.Message("+$points pts 💛"))
        }
    }

    private fun celebrate(result: com.medipote.app.domain.WinResult) {
        if (result.already) return
        if (result.points > 0) {
            _events.trySend(UiEvent.Points(result.points))
            _events.trySend(UiEvent.Message("Une petite victoire : +${result.points} pts 💛"))
        } else {
            _events.trySend(UiEvent.Message("Bravo, c'est noté 💛"))
        }
    }

    /** A mission done: points are only ever added. */
    fun doMission(mission: com.medipote.app.domain.Mission) {
        var result: com.medipote.app.domain.WinResult? = null
        repo.update { s ->
            com.medipote.app.domain.Wellbeing.missionWin(s, mission, repo.now()).also { result = it }.state
        }
        result?.let(::celebrate)
        checkAchievements()
    }

    /** "I tried it": a gesture for a sign ("feel") or a tip for a symptom ("advice"), noted as a small victory. */
    fun tried(kind: String, id: String, label: String) {
        var result: com.medipote.app.domain.WinResult? = null
        repo.update { s -> com.medipote.app.domain.Wellbeing.tried(s, kind, id, label, repo.now()).also { result = it }.state }
        result?.let(::celebrate)
    }

    /** Saves a task cut into steps; returns its id. */
    fun saveTask(id: String?, title: String, steps: List<String>): String {
        val taskId = id ?: java.util.UUID.randomUUID().toString()
        repo.update { com.medipote.app.domain.Wellbeing.saveTask(it, taskId, title, steps, repo.now()) }
        return taskId
    }

    fun deleteTask(id: String) {
        repo.update { com.medipote.app.domain.Wellbeing.deleteTask(it, id) }
    }

    fun completeStep(taskId: String, index: Int) {
        var result: com.medipote.app.domain.WinResult? = null
        repo.update { s ->
            com.medipote.app.domain.Wellbeing.completeStep(s, taskId, index, repo.now()).also { result = it }.state
        }
        val r = result ?: return
        celebrate(r)
        if (!r.already && state.value.tasks.firstOrNull { it.id == taskId }?.finished == true) {
            _events.trySend(UiEvent.Message("Tout est fait, bravo ! 🎉"))
        }
        checkAchievements()
    }

    fun showAllCards() {
        repo.update { it.copy(settings = it.settings.copy(hiddenCards = emptySet())) }
    }

    /** The end of the first-run tour: what matters to the person decides which cards start visible. */
    fun applyInterests(chosen: Set<com.medipote.app.ui.Interest>) {
        repo.update { s ->
            val hidden = com.medipote.app.ui.Interest.hiddenFor(chosen)
            val mental = com.medipote.app.ui.Interest.MENTAL in chosen
            s.copy(
                settings = s.settings.copy(
                    tourSeen = true, hiddenCards = hidden,
                    wellbeing = if (mental) s.settings.wellbeing.copy(enabled = true) else s.settings.wellbeing,
                ),
            )
        }
    }

    /** A card of a hub page was opened: counted on the phone, to order the cards and suggest tidying. */
    fun cardOpened(key: String) {
        val today = repo.now().toLocalDate()
        repo.update { s -> s.copy(settings = s.settings.copy(usage = com.medipote.app.domain.CardUse.record(s.settings.usage, key, today))) }
    }

    /** « Garder » : the card stays, and the app does not offer to put it away again. */
    fun keepCard(key: String) {
        repo.update { s -> s.copy(settings = s.settings.copy(usage = com.medipote.app.domain.CardUse.keep(s.settings.usage, key))) }
    }

    /** Pins a card on the home screen, or removes the pin. */
    fun togglePin(key: String) {
        val pins = state.value.settings.pins
        if (key !in pins && pins.size >= com.medipote.app.domain.CardUse.MAX_PINS) {
            _events.trySend(UiEvent.Message("Déjà ${com.medipote.app.domain.CardUse.MAX_PINS} raccourcis : retires-en un d'abord"))
            return
        }
        repo.update { s ->
            val next = if (key in pins) com.medipote.app.domain.CardUse.unpin(s.settings.pins, key) else com.medipote.app.domain.CardUse.pin(s.settings.pins, key)
            s.copy(settings = s.settings.copy(pins = next))
        }
        _events.trySend(UiEvent.Message(if (key in pins) "Raccourci retiré de l'accueil" else "📌 Épinglé sur l'accueil"))
    }

    fun tourSeen() {
        repo.update { it.copy(settings = it.settings.copy(tourSeen = true)) }
    }

    fun setSleepRecipe(items: List<String>) {
        repo.update { it.copy(sleepRecipe = items) }
    }

    /** Shows or hides a card of a hub page. */
    fun setCardHidden(key: String, hidden: Boolean) {
        repo.update { s ->
            val cards = if (hidden) s.settings.hiddenCards + key else s.settings.hiddenCards - key
            s.copy(settings = s.settings.copy(hiddenCards = cards))
        }
    }

    // ── Support ("Ça ne va pas") ──

    fun updateSupport(transform: (com.medipote.app.data.SupportBox) -> com.medipote.app.data.SupportBox) {
        repo.update { it.copy(support = transform(it.support)) }
    }

    fun updateSupportSettings(transform: (com.medipote.app.data.SupportSettings) -> com.medipote.app.data.SupportSettings) {
        repo.update { it.copy(settings = it.settings.copy(support = transform(it.settings.support))) }
    }

    /** Written down and kept, privately (a thought not kept is simply never saved). */
    fun keepThought(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        updateSupport { box ->
            box.copy(thoughts = (listOf(com.medipote.app.data.ThoughtNote(java.util.UUID.randomUUID().toString(), repo.now(), clean)) + box.thoughts).take(200))
        }
        _events.trySend(UiEvent.Message("Gardé, rien que pour toi"))
    }

    fun deleteThought(id: String) = updateSupport { box -> box.copy(thoughts = box.thoughts.filterNot { it.id == id }) }

    /** "Ask me again in a while": one gentle notification, in two hours. */
    fun askAgainLater() {
        repo.update { com.medipote.app.domain.Support.askAgain(it, repo.now()) }
        reschedule()
        _events.trySend(UiEvent.Message("D'accord, je te redemande dans 2 h 🤗"))
    }

    /** Opening the support screen answers the follow-up. */
    fun supportOpened() {
        if (state.value.supportFollowUp == null) return
        repo.update { com.medipote.app.domain.Support.clearFollowUp(it) }
        reschedule()
    }

    // ── Naps ──

    fun startNap() {
        repo.update { com.medipote.app.domain.Naps.start(it, repo.now()) }
        _events.trySend(UiEvent.Message("Bonne sieste ! 😴"))
    }

    fun cancelNap() {
        repo.update { com.medipote.app.domain.Naps.cancel(it) }
    }

    /** "I wake up": notes the nap; the caller fixes it by hand when the timer was forgotten. */
    fun stopNap(): com.medipote.app.domain.NapStop {
        var result: com.medipote.app.domain.NapStop? = null
        repo.update { s ->
            val r = com.medipote.app.domain.Naps.stop(s, repo.now(), java.util.UUID.randomUUID().toString())
            result = r
            when (r) {
                is com.medipote.app.domain.NapStop.Saved -> r.state
                is com.medipote.app.domain.NapStop.TooShort -> r.state
                is com.medipote.app.domain.NapStop.TooLong -> r.state
            }
        }
        val r = result!!
        _events.trySend(
            UiEvent.Message(
                when (r) {
                    is com.medipote.app.domain.NapStop.Saved -> "Sieste de ${com.medipote.app.domain.Sleep.format(r.nap.minutes)} enregistrée"
                    is com.medipote.app.domain.NapStop.TooShort -> "Sieste trop courte pour être notée"
                    is com.medipote.app.domain.NapStop.TooLong -> "Le minuteur tournait depuis longtemps : corrige la durée"
                },
            ),
        )
        return r
    }

    fun saveNap(nap: com.medipote.app.data.Nap) {
        repo.update { com.medipote.app.domain.Naps.save(it, nap) }
        _events.trySend(UiEvent.Message("Sieste enregistrée"))
    }

    fun deleteNap(id: String) {
        repo.update { com.medipote.app.domain.Naps.delete(it, id) }
    }

    fun deleteSleep(date: LocalDate) {
        repo.update { com.medipote.app.domain.Sleep.delete(it, date) }
        reschedule()
    }

    private val _routes = MutableStateFlow<String?>(null)
    /** A screen to open, requested from outside the UI (a tapped notification). */
    val routes: StateFlow<String?> = _routes.asStateFlow()

    fun open(route: String) {
        _routes.value = route
    }

    fun routeHandled() {
        _routes.value = null
    }

    fun cuddle() {
        var bonus = 0
        repo.update { s -> Game.cuddle(s, repo.now()).let { (u, b) -> bonus = b; u } }
        if (bonus > 0) _events.trySend(UiEvent.Points(bonus))
        if (bonus > 0) checkAchievements()
    }

    // ── Appointments ──

    fun saveAppointment(a: Appointment) {
        repo.update { s -> s.copy(appointments = (s.appointments.filterNot { it.id == a.id } + a).sortedBy { it.dateTime }) }
        reschedule()
    }

    /** The questions written for an appointment: the first time, they earn a little. */
    fun savePrepQuestions(a: com.medipote.app.data.Appointment, questions: List<String>) {
        saveAppointment(a.copy(questions = questions))
        if (questions.isNotEmpty()) careReward { com.medipote.app.domain.Care.appointmentPrepared(it, a.id, repo.now()) }
    }

    /** "I went": noted once, and it earns a little. */
    fun markAttended(a: com.medipote.app.data.Appointment) {
        saveAppointment(a.copy(attended = true))
        careReward { com.medipote.app.domain.Care.appointmentAttended(it, a.id, repo.now()) }
    }

    fun deleteAppointment(id: String) {
        repo.update { s -> s.copy(appointments = s.appointments.filterNot { it.id == id }) }
        reschedule()
    }

    // ── Health record ──

    fun saveDocument(doc: HealthDocument) {
        repo.update { s -> s.copy(documents = (s.documents.filterNot { it.id == doc.id } + doc).sortedByDescending { it.date }) }
        checkAchievements()
    }

    fun deleteDocument(doc: HealthDocument) {
        DocumentStore.delete(getApplication(), doc)
        repo.update { s -> s.copy(documents = s.documents.filterNot { it.id == doc.id }) }
    }

    // ── Stock and pill ──

    fun refill(treatmentId: String) {
        val wasLow = state.value.treatments.firstOrNull { it.id == treatmentId }?.isLowStock == true
        repo.update { s ->
            s.copy(treatments = s.treatments.map { t ->
                if (t.id == treatmentId && t.packSize != null) t.copy(stock = (t.stock ?: 0.0) + t.packSize) else t
            })
        }
        _events.trySend(UiEvent.Message("Stock mis à jour"))
        // Buying back what was running low is a way of taking care.
        if (wasLow) careReward { com.medipote.app.domain.Care.restocked(it, treatmentId, repo.now()) }
        reschedule()
    }

    fun startNewPillPack(treatmentId: String, date: LocalDate) {
        repo.update { s ->
            s.copy(treatments = s.treatments.map { t ->
                val pack = t.pill
                if (t.id == treatmentId && pack != null) t.copy(pill = pack.copy(packStart = date), startDate = minOf(t.startDate, date)) else t
            })
        }
        reschedule()
    }

    /** Badges newly earned and weekly challenges just completed, after any change. */
    fun checkAchievements() {
        var badges = emptyList<com.medipote.app.domain.Badge>()
        var challenges = emptyList<com.medipote.app.domain.Challenge>()
        repo.update { s ->
            val (afterChallenges, won) = com.medipote.app.domain.Challenges.reward(s, repo.now())
            challenges = won
            val (afterBadges, earned) = com.medipote.app.domain.Badges.award(afterChallenges, repo.now().toLocalDate())
            badges = earned
            afterBadges
        }
        // A week of gentle days earns a surprise.
        var gift = 0
        repo.update { s -> com.medipote.app.domain.Care.gift(s, repo.now()).also { gift = it.points }.state }
        if (gift > 0) {
            _events.trySend(UiEvent.Points(gift))
            _events.trySend(UiEvent.Message("🎁 Un petit cadeau : 7 jours à prendre soin de toi · +$gift pts"))
        }
        challenges.forEach { _events.trySend(UiEvent.Message("🎯 Défi réussi : ${it.title.lowercase()} · +${it.points} pts")) }
        badges.forEach { _events.trySend(UiEvent.BadgeEarned(it)) }
    }

    /** Home: today's mood in one tap. */
    fun setMood(value: Int?) {
        val today = repo.now().toLocalDate()
        saveEntry(state.value.entryFor(today).copy(mental = value))
    }


    // ── Period products and medication during periods ──

    fun startProduct(product: com.medipote.app.data.PeriodProduct) {
        repo.update { com.medipote.app.domain.PeriodCare.start(it, product, repo.now()) }
        Notifications.cancelProduct(getApplication())
        reschedule()
    }

    fun productChanged() {
        repo.update { com.medipote.app.domain.PeriodCare.change(it, repo.now()) }
        Notifications.cancelProduct(getApplication())
        _events.trySend(UiEvent.Message("Protection changée, minuteur relancé"))
        reschedule()
    }

    fun productRemoved() {
        repo.update { com.medipote.app.domain.PeriodCare.stop(it, repo.now()) }
        Notifications.cancelProduct(getApplication())
        reschedule()
    }

    fun setProductReminder(product: com.medipote.app.data.PeriodProduct, minutes: Int) {
        repo.update { com.medipote.app.domain.PeriodCare.setReminder(it, product, minutes) }
        reschedule()
    }

    /** Adds a common period medication (from the guide) as "as needed"; returns its id. */
    fun addPeriodMedication(drugId: String): String? {
        val drug = com.medipote.app.data.DrugGuide.byId(drugId) ?: return null
        state.value.treatments.firstOrNull { it.drugId == drugId && it.asNeeded }?.let { existing ->
            setPeriodMed(existing.id, true)
            return existing.id
        }
        return addCustomPeriodMedication(drug.dci, drug.strengths.firstOrNull().orEmpty(), drug.id)
    }

    /** A medication of one's own for periods (typed in, or picked from the guide). */
    fun addCustomPeriodMedication(name: String, strength: String, drugId: String? = null): String {
        val drug = drugId?.let(com.medipote.app.data.DrugGuide::byId)
        val t = Treatment(
            id = java.util.UUID.randomUUID().toString(), drugId = drug?.id, name = name.trim(),
            strength = strength.trim(), unit = drug?.unit ?: com.medipote.app.data.DoseUnit.TABLET, doses = emptyList(),
            startDate = repo.now().toLocalDate(), asNeeded = true, createdAt = repo.now(), forPeriod = true,
        )
        repo.update { it.copy(treatments = it.treatments + t) }
        _events.trySend(UiEvent.Message("${t.displayName} ajouté à tes médicaments pour les règles"))
        return t.id
    }

    fun setPeriodMed(treatmentId: String, listed: Boolean) {
        repo.update { com.medipote.app.domain.PeriodCare.setPeriodMed(it, treatmentId, listed) }
    }

    // ── Rituals ──

    fun completeRitual(kind: com.medipote.app.domain.RitualKind) {
        var bonus = 0
        repo.update { s -> com.medipote.app.domain.Rituals.complete(s, kind, repo.now()).let { (u, b) -> bonus = b; u } }
        if (bonus > 0) _events.trySend(UiEvent.Points(bonus))
        checkAchievements()
    }

    fun markRecapSeen(week: LocalDate) {
        repo.update { s -> s.copy(game = s.game.copy(recapSeen = week)) }
    }

    fun message(text: String) {
        _events.trySend(UiEvent.Message(text))
    }

    fun reschedule() {
        ReminderScheduler.reschedule(getApplication(), state.value, repo.now())
    }
}
