package com.medipote.app.wear

import com.medipote.app.data.AppState
import com.medipote.app.data.MeasureType
import com.medipote.app.data.Measurement
import com.medipote.app.data.PrnIntake
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Game
import com.medipote.app.domain.Measures
import com.medipote.app.domain.NapStop
import com.medipote.app.domain.Naps
import com.medipote.app.domain.PeriodCare
import com.medipote.app.domain.Prn
import com.medipote.app.domain.Schedule
import com.medipote.app.notifications.AlarmKind
import com.medipote.app.notifications.ReminderPlanner
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchDose
import com.medipote.sync.WatchPrn
import com.medipote.sync.WatchProduct
import com.medipote.sync.WatchReminder
import com.medipote.sync.WatchSnapshot
import java.time.LocalDateTime

/** What the watch is told, and what its actions change: pure, so it can be tested without a watch. */
object WatchBridge {
    const val FROM_WATCH = "Depuis la montre"

    /** Everything the watch needs for today and tomorrow. */
    fun snapshot(state: AppState, now: LocalDateTime): WatchSnapshot {
        val today = now.toLocalDate()
        val logs = state.doseLogs.associateBy { it.key }
        val items = Schedule.carriedOver(state.treatments, logs, now) +
            Schedule.itemsOn(state.treatments, logs, today, now) +
            Schedule.itemsOn(state.treatments, logs, today.plusDays(1), now)
        val doses = items.filter { it.state != DoseState.MISSED }.map { item ->
            val t = item.occurrence.treatment
            WatchDose(
                key = item.occurrence.key,
                name = t.displayName,
                quantity = t.unit.format(item.occurrence.quantity),
                time = item.occurrence.scheduled.toString(),
                taken = item.state == DoseState.TAKEN,
                withFood = t.withFood,
            )
        }
        val reminders = ReminderPlanner.plan(state, now).mapNotNull { spec ->
            when (spec.kind) {
                AlarmKind.DOSE -> WatchReminder(WatchReminder.DOSE, spec.time.toString(), spec.time.toString())
                AlarmKind.FOLLOW_UP -> WatchReminder(WatchReminder.FOLLOW_UP, spec.time.toString(), spec.ref)
                // A reminder put off on the phone rings on the watch too, as a follow-up.
                AlarmKind.SNOOZE -> WatchReminder(WatchReminder.FOLLOW_UP, spec.time.toString(), spec.ref)
                AlarmKind.WATER -> WatchReminder(WatchReminder.WATER, spec.time.toString())
                AlarmKind.PRODUCT -> state.productSession?.let { s ->
                    val p = s.product
                    val max = spec.ref == "max"
                    WatchReminder(
                        WatchReminder.PRODUCT, spec.time.toString(), spec.ref,
                        title = if (max) "⚠️ Plus de ${p.maxMinutes / 60} h" else "${p.emoji} Pense à changer",
                        text = if (max) "Change ${p.yours} maintenant." else "C'est le moment de changer ${p.yours}.",
                    )
                }
                else -> null
            }
        }
        val water = state.settings.water
        val prn = state.treatments
            .filter { it.asNeeded && (it.endDate == null || !it.endDate.isBefore(today)) }
            .map { t -> WatchPrn(t.id, t.displayName, t.unit.format(t.prnQuantity), Prn.last(state, t.id, now)?.takenAt?.toString()) }
        val pulse = Measures.latest(state, MeasureType.HEART_RATE)
        val oxygen = Measures.latest(state, MeasureType.OXYGEN)
        return WatchSnapshot(
            generatedAt = now.withNano(0).toString(),
            petName = state.game.petName,
            userName = state.profile?.name.orEmpty(),
            streak = state.game.streak,
            points = state.game.points,
            petLine = Game.petCondition(state, now).message,
            doses = doses,
            reminders = reminders,
            waterEnabled = water.enabled,
            waterDate = today.toString(),
            glasses = state.entryFor(today).waterGlasses,
            goal = water.goalGlasses,
            prn = prn,
            product = state.productSession?.let { s ->
                WatchProduct(s.product.label, s.product.emoji, s.since.toString(), PeriodCare.changeAt(s).toString(), PeriodCare.maxAt(s).toString())
            },
            lastHeartRate = pulse?.value?.toInt(),
            lastHeartRateAt = pulse?.at?.toString(),
            lastOxygen = oxygen?.value?.toInt(),
            lastOxygenAt = oxygen?.at?.toString(),
            napSince = Naps.running(state, now)?.toString(),
            napMinutesToday = Naps.minutesOn(state, today),
        )
    }

    /**
     * Records something done on the watch, at the time it was done there (the phone may hear of it
     * later). Safe to apply twice, except for water: the caller remembers the ids it has seen.
     */
    fun apply(state: AppState, action: WatchAction, now: LocalDateTime): AppState {
        val at = action.time.let { if (it.isAfter(now)) now else it }
        return when (action.type) {
            WatchAction.TAKE -> {
                var s = state
                for (key in action.doseKeys) {
                    val item = listOf(-1L, 0L, 1L).firstNotNullOfOrNull { d ->
                        Schedule.itemsOn(s.treatments, s.doseLogs.associateBy { it.key }, at.toLocalDate().plusDays(d), at)
                            .firstOrNull { it.occurrence.key == key }
                    } ?: continue
                    val result = Game.give(s, item, at) ?: Game.give(s, item, at, early = true) ?: continue
                    s = result.state.copy(snoozes = result.state.snoozes.filterNot { it.doseTime == item.occurrence.scheduled })
                }
                s
            }
            WatchAction.WATER -> Game.addWater(state, action.value.toInt().coerceIn(-1, 1), at).first
            WatchAction.PRN -> {
                val t = state.treatments.firstOrNull { it.id == action.treatmentId && it.asNeeded } ?: return state
                Prn.save(state, PrnIntake(id = "watch-${action.id}", treatmentId = t.id, takenAt = at, quantity = t.prnQuantity, notes = FROM_WATCH))
            }
            WatchAction.HEART_RATE -> {
                val bpm = action.value
                if (bpm < MeasureType.HEART_RATE.min || bpm > MeasureType.HEART_RATE.max) return state
                val saved = Measures.save(
                    state,
                    Measurement(id = "watch-${action.id}", type = MeasureType.HEART_RATE, at = at, value = Math.round(bpm).toDouble(), notes = FROM_WATCH),
                )
                // Measuring from the watch turns the pulse on in the app, or it would stay hidden.
                saved.copy(settings = saved.settings.copy(measures = saved.settings.measures + MeasureType.HEART_RATE))
            }
            WatchAction.OXYGEN -> {
                val percent = action.value
                if (percent < MeasureType.OXYGEN.min || percent > MeasureType.OXYGEN.max) return state
                val saved = Measures.save(
                    state,
                    Measurement(id = "watch-${action.id}", type = MeasureType.OXYGEN, at = at, value = Math.round(percent).toDouble(), notes = FROM_WATCH),
                )
                saved.copy(settings = saved.settings.copy(measures = saved.settings.measures + MeasureType.OXYGEN))
            }
            // Naps: the watch says when they ended or when the timer started and stopped.
            WatchAction.NAP -> {
                val minutes = action.value.toInt().coerceIn(Naps.MIN_MINUTES, Naps.MAX_MINUTES)
                Naps.save(state, Naps.endingAt("watch-${action.id}", at, minutes))
            }
            WatchAction.NAP_START -> Naps.start(state, at)
            WatchAction.NAP_CANCEL -> Naps.cancel(state)
            WatchAction.NAP_STOP -> when (val r = Naps.stop(state, at, "watch-${action.id}")) {
                is NapStop.Saved -> r.state
                is NapStop.TooShort -> r.state
                // Forgotten for hours: nothing is noted; the nap can be noted by hand.
                is NapStop.TooLong -> r.state
            }
            WatchAction.PRODUCT_CHANGED -> PeriodCare.change(state, at)
            else -> state
        }
    }
}
