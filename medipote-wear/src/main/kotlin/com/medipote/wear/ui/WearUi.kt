package com.medipote.wear.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.medipote.sync.HeartRate
import com.medipote.sync.WatchAction
import com.medipote.sync.WatchDose
import com.medipote.sync.WatchDoseState
import com.medipote.sync.WatchPlanner
import com.medipote.sync.WatchPrn
import com.medipote.sync.WatchSnapshot
import com.medipote.wear.HeartRateSensor
import com.medipote.wear.Reminders
import com.medipote.wear.WearLink
import com.medipote.wear.WearState
import com.medipote.wear.store
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** What the screens can do; wired to the phone link by the app, to nothing in tests. */
data class WearActions(
    val onTake: (List<WatchDose>) -> Unit = {},
    val onWater: () -> Unit = {},
    val onPrn: (WatchPrn) -> Unit = {},
    val onProductChanged: () -> Unit = {},
    val onHeartRate: (Int) -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun MediPoteWearApp() {
    val context = LocalContext.current
    val store = context.store
    val state by store.state.collectAsStateWithLifecycle()
    // The clock moves the doses from "à venir" to "c'est l'heure".
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now()
        }
    }
    val pet = remember(state.petVersion) { store.pet() }
    val nav = rememberSwipeDismissableNavController()
    fun send(type: String, keys: List<String> = emptyList(), treatmentId: String = "", value: Double = 0.0) {
        WearLink.send(context, WearLink.action(type, LocalDateTime.now(), keys, treatmentId, value))
        Reminders.tick(context)
    }
    val actions = WearActions(
        onTake = { doses -> send(WatchAction.TAKE, keys = doses.map { it.key }) },
        onWater = { send(WatchAction.WATER, value = 1.0) },
        onPrn = { send(WatchAction.PRN, treatmentId = it.id) },
        onProductChanged = { send(WatchAction.PRODUCT_CHANGED) },
        onHeartRate = { bpm -> send(WatchAction.HEART_RATE, value = bpm.toDouble()) },
        onRetry = { WearLink.hello(context) },
    )
    MediPoteWearTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                composable("home") { HomeScreen(state, now, pet, actions, onOpenHeartRate = { nav.navigate("pulse") }) }
                composable("pulse") {
                    PulseScreen(onSave = { bpm -> actions.onHeartRate(bpm); nav.popBackStack() })
                }
            }
        }
    }
}

private sealed interface Confirm {
    data class Take(val doses: List<WatchDose>) : Confirm
    data class Prn(val prn: WatchPrn) : Confirm
    data object Product : Confirm
}

/** Today at a glance: the doses to take, water, as-needed medications, pulse. */
@Composable
fun HomeScreen(state: WearState, now: LocalDateTime, pet: Bitmap?, actions: WearActions, onOpenHeartRate: () -> Unit) {
    val snapshot = state.snapshot
    val listState = rememberTransformingLazyColumnState()
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            if (snapshot == null) {
                item { ListHeader { Text("MédiPote") } }
                item {
                    Text(
                        "Ouvre MédiPote sur ton téléphone pour relier la montre.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item { FilledTonalButton(onClick = actions.onRetry, label = { Text("Réessayer") }, modifier = Modifier.fillMaxWidth()) }
                return@TransformingLazyColumn
            }

            item { Header(snapshot, pet) }

            // Doses of the day, grouped by time: one tap gives everything due at that time.
            val today = WatchPlanner.dosesOn(snapshot, now.toLocalDate())
            val groups = today.filter { !it.taken }.groupBy { it.time }.toSortedMap()
            if (today.isNotEmpty()) {
                item {
                    Text(
                        if (groups.isEmpty()) "Tout est pris aujourd'hui 🎉" else "${today.count { it.taken }} / ${today.size} prises faites",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            groups.entries.forEachIndexed { index, (_, doses) ->
                item {
                    val first = doses.first()
                    val doseState = WatchPlanner.state(first, now)
                    val label = "${first.at.format(hourMinute)} · " + doses.joinToString(", ") { it.name }
                    val detail = when (doseState) {
                        WatchDoseState.DUE -> "C'est l'heure"
                        WatchDoseState.LATE -> "En retard"
                        else -> "À venir"
                    } + " · " + doses.joinToString(", ") { it.quantity }
                    // Only what is due now stands out; the rest stays quiet.
                    if (doseState != WatchDoseState.UPCOMING && index == 0) {
                        Button(
                            onClick = { confirm = Confirm.Take(doses) },
                            label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { confirm = Confirm.Take(doses) },
                            label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                val glasses = WatchPlanner.glasses(snapshot, now.toLocalDate())
                FilledTonalButton(
                    onClick = actions.onWater,
                    label = { Text("💧 $glasses / ${snapshot.goal} verres") },
                    secondaryLabel = { Text("Toucher : +1 verre") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            snapshot.product?.let { p ->
                item {
                    FilledTonalButton(
                        onClick = { confirm = Confirm.Product },
                        label = { Text("${p.emoji} ${p.label}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { Text("À changer vers ${LocalDateTime.parse(p.changeAt).format(hourMinute)}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (snapshot.prn.isNotEmpty()) {
                item { ListHeader { Text("Si besoin") } }
                snapshot.prn.forEach { prn ->
                    item {
                        OutlinedButton(
                            onClick = { confirm = Confirm.Prn(prn) },
                            label = { Text(prn.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = {
                                Text(
                                    prn.lastTakenAt?.let { "Dernière prise ${WatchPlanner.ago(LocalDateTime.parse(it), now)}" } ?: prn.quantity,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenHeartRate,
                    label = { Text("❤️ Mesurer mon pouls") },
                    secondaryLabel = {
                        val last = snapshot.lastHeartRate
                        val at = snapshot.lastHeartRateAt
                        Text(if (last != null && at != null) "$last bpm · ${WatchPlanner.ago(LocalDateTime.parse(at), now)}" else "Envoyé au téléphone")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                val waiting = state.pending.size
                Text(
                    if (waiting > 0) "$waiting action${if (waiting > 1) "s" else ""} en attente du téléphone"
                    else "À jour · ${WatchPlanner.ago(snapshot.generated, now)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }

    val pending = confirm
    AlertDialog(
        visible = pending != null,
        onDismissRequest = { confirm = null },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                when (pending) {
                    is Confirm.Take -> actions.onTake(pending.doses)
                    is Confirm.Prn -> actions.onPrn(pending.prn)
                    Confirm.Product -> actions.onProductChanged()
                    null -> Unit
                }
                confirm = null
            })
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = { confirm = null }) },
        title = {
            Text(
                when (pending) {
                    is Confirm.Take -> if (pending.doses.size > 1) "Tout est pris ?" else "C'est pris ?"
                    is Confirm.Prn -> "Noter une prise ?"
                    Confirm.Product -> "Protection changée ?"
                    null -> ""
                },
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                when (pending) {
                    is Confirm.Take -> pending.doses.joinToString("\n") { "${it.name} · ${it.quantity}" }
                    is Confirm.Prn -> "${pending.prn.name} · ${pending.prn.quantity}"
                    Confirm.Product -> "Le minuteur repart de zéro."
                    null -> ""
                },
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Composable
private fun Header(snapshot: WatchSnapshot, pet: Bitmap?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (pet != null) {
            Image(pet.asImageBitmap(), contentDescription = snapshot.petName, modifier = Modifier.size(64.dp).clip(CircleShape))
        }
        Text(snapshot.petName.ifBlank { "MédiPote" }, style = MaterialTheme.typography.titleMedium)
        Text("🔥 ${snapshot.streak} j · ⭐ ${snapshot.points}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (snapshot.petLine.isNotBlank()) {
            Text(
                snapshot.petLine,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

/** Measures the pulse with the watch's sensor, then sends it to the phone's measurements. */
@Composable
fun PulseScreen(onSave: (Int) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(HeartRateSensor.allowed(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val readings = remember { mutableStateListOf<Float>() }
    var seconds by remember { mutableStateOf(0) }
    var run by remember { mutableStateOf(0) }
    val available = remember { HeartRateSensor.available(context) }

    LaunchedEffect(allowed, run) {
        if (!allowed || !available) return@LaunchedEffect
        readings.clear()
        HeartRateSensor.readings(context).collect { readings.add(it) }
    }
    LaunchedEffect(allowed, run) {
        seconds = 0
        while (allowed && available && seconds < PULSE_TIMEOUT_S) {
            delay(1000)
            seconds++
        }
    }
    PulseContent(
        available = available,
        allowed = allowed,
        live = readings.lastOrNull { HeartRate.valid(it) }?.let { Math.round(it) },
        steady = HeartRate.steady(readings),
        timedOut = seconds >= PULSE_TIMEOUT_S,
        onAllow = { ask.launch(HeartRateSensor.permission) },
        onRetry = { run++ },
        onSave = onSave,
    )
}

private const val PULSE_TIMEOUT_S = 45

@Composable
fun PulseContent(
    available: Boolean,
    allowed: Boolean,
    live: Int?,
    steady: Int?,
    timedOut: Boolean,
    onAllow: () -> Unit,
    onRetry: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("❤️ Pouls") } }
            when {
                !available -> item { Info("Cette montre n'a pas de capteur de pouls utilisable par l'appli.") }
                !allowed -> {
                    item { Info("Autorise l'accès au capteur de pouls pour mesurer.") }
                    item { Button(onClick = onAllow, label = { Text("Autoriser") }, modifier = Modifier.fillMaxWidth()) }
                }
                steady != null -> {
                    item { Big("$steady", "bpm") }
                    item { Button(onClick = { onSave(steady) }, label = { Text("Enregistrer") }, secondaryLabel = { Text("Envoyé au téléphone") }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedButton(onClick = onRetry, label = { Text("Refaire la mesure") }, modifier = Modifier.fillMaxWidth()) }
                }
                timedOut -> {
                    item { Info("Mesure instable. Serre un peu le bracelet, pose le bras et réessaie.") }
                    item { Button(onClick = onRetry, label = { Text("Réessayer") }, modifier = Modifier.fillMaxWidth()) }
                }
                else -> {
                    item { Big(live?.toString() ?: "…", "bpm") }
                    item { Info("Reste immobile, le bras posé, quelques secondes.") }
                }
            }
        }
    }
}

@Composable
private fun Info(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
}

@Composable
private fun Big(value: String, unit: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
