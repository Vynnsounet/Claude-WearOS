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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiEvent {
    data class Fed(val treatmentName: String, val status: TakeStatus, val points: Int, val perfectDayBonus: Int) : UiEvent
    data class CheckedIn(val reward: CheckInReward) : UiEvent
    data class Bought(val item: ShopItem) : UiEvent
    data class Message(val text: String) : UiEvent

    /** Points won outside a dose (water…), shown floating above the pet. */
    data class Points(val amount: Int) : UiEvent
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = app.repository
    val state: StateFlow<AppState> = repo.state

    private val _now = MutableStateFlow(repo.now())
    /** Current time, refreshed every 20 seconds so dose states and the pet's mood stay live. */
    val now: StateFlow<LocalDateTime> = _now.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

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
        reward?.let { _events.tryEmit(UiEvent.CheckedIn(it)) }
        reschedule()
    }

    fun give(item: DoseItem, early: Boolean = false) {
        val now = repo.now()
        var fed: UiEvent.Fed? = null
        var lowStock: Treatment? = null
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
            _events.tryEmit(it)
            val ctx = getApplication<Application>()
            val time = item.occurrence.scheduled
            val logs = state.value.doseLogs.mapTo(HashSet()) { l -> l.key }
            val sameTime = com.medipote.app.domain.Schedule.occurrencesOn(state.value.treatments, time.toLocalDate())
                .filter { o -> o.scheduled == time }
            if (sameTime.all { o -> o.key in logs }) Notifications.cancel(ctx, time)
        }
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
        if (bought) _events.tryEmit(UiEvent.Bought(item))
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
        _events.tryEmit(UiEvent.Message(if (bonus > 0) "Journal enregistré : +$bonus pts" else "Journal enregistré"))
    }

    fun addWater(delta: Int) {
        var reward: com.medipote.app.domain.WaterReward? = null
        repo.update { s ->
            val (updated, r) = Game.addWater(s, delta, repo.now())
            reward = r
            updated
        }
        val r = reward ?: return
        if (r.total > 0) _events.tryEmit(UiEvent.Points(r.total))
        if (r.goalBonus > 0) {
            Notifications.cancelWater(getApplication())
            _events.tryEmit(UiEvent.Message("Objectif d'hydratation atteint : +${r.goalBonus} pts bonus 💧"))
        }
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

    // ── Sleep ──

    fun saveSleep(night: com.medipote.app.data.SleepNight) {
        var bonus = 0
        repo.update { s -> com.medipote.app.domain.Sleep.save(s, night, repo.now()).let { (u, b) -> bonus = b; u } }
        if (night.date == repo.now().toLocalDate()) Notifications.cancelSleep(getApplication())
        _events.tryEmit(UiEvent.Message(if (bonus > 0) "Nuit enregistrée : +$bonus pts" else "Nuit enregistrée"))
        if (bonus > 0) _events.tryEmit(UiEvent.Points(bonus))
        reschedule()
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
        if (bonus > 0) _events.tryEmit(UiEvent.Points(bonus))
    }

    // ── Appointments ──

    fun saveAppointment(a: Appointment) {
        repo.update { s -> s.copy(appointments = (s.appointments.filterNot { it.id == a.id } + a).sortedBy { it.dateTime }) }
        reschedule()
    }

    fun deleteAppointment(id: String) {
        repo.update { s -> s.copy(appointments = s.appointments.filterNot { it.id == id }) }
        reschedule()
    }

    // ── Health record ──

    fun saveDocument(doc: HealthDocument) {
        repo.update { s -> s.copy(documents = (s.documents.filterNot { it.id == doc.id } + doc).sortedByDescending { it.date }) }
    }

    fun deleteDocument(doc: HealthDocument) {
        DocumentStore.delete(getApplication(), doc)
        repo.update { s -> s.copy(documents = s.documents.filterNot { it.id == doc.id }) }
    }

    // ── Stock and pill ──

    fun refill(treatmentId: String) {
        repo.update { s ->
            s.copy(treatments = s.treatments.map { t ->
                if (t.id == treatmentId && t.packSize != null) t.copy(stock = (t.stock ?: 0.0) + t.packSize) else t
            })
        }
        _events.tryEmit(UiEvent.Message("Stock mis à jour"))
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

    fun message(text: String) {
        _events.tryEmit(UiEvent.Message(text))
    }

    fun reschedule() {
        ReminderScheduler.reschedule(getApplication(), state.value, repo.now())
    }
}
