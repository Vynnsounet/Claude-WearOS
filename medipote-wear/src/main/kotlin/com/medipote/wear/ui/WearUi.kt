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
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
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
import com.medipote.sync.Calm
import com.medipote.sync.HeartRate
import com.medipote.sync.NapTimer
import com.medipote.sync.Oxygen
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
    val onOxygen: (Int) -> Unit = {},
    val onNap: (Int) -> Unit = {},
    val onNapStart: () -> Unit = {},
    val onNapStop: () -> Unit = {},
    val onNapCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
    /** A symptom's strength for today (0 removes it). */
    val onSymptom: (String, Int) -> Unit = { _, _ -> },
    val onMission: (String) -> Unit = {},
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
        onOxygen = { percent -> send(WatchAction.OXYGEN, value = percent.toDouble()) },
        onNap = { minutes -> send(WatchAction.NAP, value = minutes.toDouble()) },
        onNapStart = { send(WatchAction.NAP_START) },
        onNapStop = { send(WatchAction.NAP_STOP) },
        onNapCancel = { send(WatchAction.NAP_CANCEL) },
        onSymptom = { id, level -> send(WatchAction.SYMPTOM, treatmentId = id, value = level.toDouble()) },
        onMission = { id -> send(WatchAction.MISSION, treatmentId = id) },
        onRetry = { WearLink.hello(context) },
    )
    MediPoteWearTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        state, now, pet, actions,
                        onOpenHeartRate = { nav.navigate("pulse") },
                        onOpenOxygen = { nav.navigate("oxygen") },
                        onOpenNap = { nav.navigate("nap") },
                        onOpenCalm = { nav.navigate("calm") },
                        onOpenSymptoms = { nav.navigate("symptoms") },
                        onOpenMissions = { nav.navigate("missions") },
                    )
                }
                composable("symptoms") {
                    SymptomsScreen(state.snapshot, onPick = { nav.navigate("symptom/${android.net.Uri.encode(it)}") })
                }
                composable("symptom/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    SymptomScreen(
                        state.snapshot?.symptoms?.firstOrNull { it.id == id },
                        onSave = { level -> actions.onSymptom(id, level); nav.popBackStack() },
                    )
                }
                composable("missions") {
                    MissionsScreen(state.snapshot, actions, onDone = { nav.popBackStack() })
                }
                composable("calm") {
                    CalmScreen(onDone = { nav.popBackStack() })
                }
                composable("nap") {
                    NapScreen(state.snapshot, now, actions, onDone = { nav.popBackStack() })
                }
                composable("oxygen") {
                    OxygenScreen(initial = state.snapshot?.lastOxygen, onSave = { percent -> actions.onOxygen(percent); nav.popBackStack() })
                }
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
fun HomeScreen(
    state: WearState,
    now: LocalDateTime,
    pet: Bitmap?,
    actions: WearActions,
    onOpenHeartRate: () -> Unit,
    onOpenOxygen: () -> Unit = {},
    onOpenNap: () -> Unit = {},
    onOpenCalm: () -> Unit = {},
    onOpenSymptoms: () -> Unit = {},
    onOpenMissions: () -> Unit = {},
) {
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

            item {
                val since = snapshot.napSince?.let(LocalDateTime::parse)
                FilledTonalButton(
                    onClick = onOpenNap,
                    label = { Text("😴 Sieste") },
                    secondaryLabel = {
                        Text(
                            when {
                                since != null -> "En cours · ${NapTimer.label(NapTimer.minutes(since, now))}"
                                snapshot.napMinutesToday > 0 -> "Aujourd'hui : ${NapTimer.label(snapshot.napMinutesToday)}"
                                else -> "Noter ou chronométrer"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (snapshot.symptoms.isNotEmpty()) {
                item {
                    val noted = snapshot.symptoms.filter { it.intensity > 0 }
                    FilledTonalButton(
                        onClick = onOpenSymptoms,
                        label = { Text("🌡️ Symptômes") },
                        secondaryLabel = {
                            Text(
                                if (noted.isEmpty()) "Noter comment je me sens" else noted.take(2).joinToString(" · ") { "${it.name.lowercase()} ${it.intensity}/5" },
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (snapshot.missions.isNotEmpty() || snapshot.winsToday > 0) {
                item {
                    FilledTonalButton(
                        onClick = onOpenMissions,
                        label = { Text("💛 Journée douce") },
                        secondaryLabel = {
                            Text(if (snapshot.winsToday > 0) "${snapshot.winsToday} petite${if (snapshot.winsToday > 1) "s" else ""} victoire${if (snapshot.winsToday > 1) "s" else ""}" else "De toutes petites missions")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                OutlinedButton(
                    onClick = onOpenOxygen,
                    label = { Text("💨 Noter ma saturation") },
                    secondaryLabel = {
                        val last = snapshot.lastOxygen
                        val at = snapshot.lastOxygenAt
                        Text(if (last != null && at != null) "$last % · ${WatchPlanner.ago(LocalDateTime.parse(at), now)}" else "Envoyée au téléphone")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedButton(
                    onClick = onOpenCalm,
                    label = { Text("🤗 Ça ne va pas") },
                    secondaryLabel = { Text("Respirer doucement") },
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

/** "Ça ne va pas": a slow circle, a soft tick at each breath, and the number to call if it is too heavy. */
@Composable
fun CalmScreen(onDone: () -> Unit, animate: Boolean = true) {
    val context = LocalContext.current
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameMillis { t ->
                if (last == 0L) last = t
                elapsed += t - last
                last = t
            }
        }
    }
    val state = Calm.at(elapsed)
    LaunchedEffect(state.inhale, animate) {
        if (!animate) return@LaunchedEffect
        runCatching { context.getSystemService(android.os.Vibrator::class.java)?.vibrate(android.os.VibrationEffect.createOneShot(40, 60)) }
    }
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(100.dp)
                            .scale(Calm.fill(state))
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    Text(if (state.inhale) "Inspire" else "Expire", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            item { Info("Suis le cercle, à ton rythme. Si c'est trop lourd : appelle le ${Calm.HELP_NUMBER}, jour et nuit, gratuit.") }
            item { Button(onClick = onDone, label = { Text("Ça va un peu mieux") }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

/** A nap: time it ("I'm falling asleep" / "I wake up") or note one that just ended. */
@Composable
fun NapScreen(snapshot: WatchSnapshot?, now: LocalDateTime, actions: WearActions, onDone: () -> Unit) {
    val since = snapshot?.napSince?.let(LocalDateTime::parse)
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("😴 Sieste") } }
            if (since != null) {
                item { Big("${NapTimer.minutes(since, now)}", "min · depuis ${since.format(hourMinute)}") }
                item { Button(onClick = { actions.onNapStop(); onDone() }, label = { Text("Je me réveille") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedButton(onClick = { actions.onNapCancel(); onDone() }, label = { Text("Annuler") }, modifier = Modifier.fillMaxWidth()) }
            } else {
                item { Button(onClick = { actions.onNapStart(); onDone() }, label = { Text("Je m'endors") }, secondaryLabel = { Text("Un minuteur démarre") }, modifier = Modifier.fillMaxWidth()) }
                item { Info("Ou je viens de me réveiller après :") }
                NapTimer.DURATIONS.forEach { minutes ->
                    item {
                        FilledTonalButton(
                            onClick = { actions.onNap(minutes); onDone() },
                            label = { Text(NapTimer.label(minutes)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Oxygen saturation, typed in: the watch's own sensor isn't open to apps, an oximeter is. */
@Composable
fun OxygenScreen(initial: Int?, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(Oxygen.clamp(initial ?: Oxygen.DEFAULT)) }
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("💨 Saturation") } }
            item { Big("$value", "%") }
            item {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    FilledTonalButton(onClick = { value = Oxygen.clamp(value - 1) }, label = { Text("−", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { value = Oxygen.clamp(value + 1) }, label = { Text("+", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f))
                }
            }
            Oxygen.advice(value)?.let { advice -> item { Info(advice) } }
            item { Button(onClick = { onSave(value) }, label = { Text("Enregistrer") }, secondaryLabel = { Text("Envoyée au téléphone") }, modifier = Modifier.fillMaxWidth()) }
            item { Info("Lis la valeur sur ton oxymètre ou dans l'appli santé de ta montre.") }
        }
    }
}

/** The symptoms followed; touch one to say how strong it is today. */
@Composable
fun SymptomsScreen(snapshot: WatchSnapshot?, onPick: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("🌡️ Symptômes") } }
            val list = snapshot?.symptoms.orEmpty()
            if (list.isEmpty()) item { Info("Choisis tes symptômes dans l'appli, sur le téléphone.") }
            list.forEach { sym ->
                item {
                    FilledTonalButton(
                        onClick = { onPick(sym.id) },
                        label = { Text("${sym.emoji} ${sym.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { Text(if (sym.intensity > 0) "${sym.intensity} / 5" else "Pas aujourd'hui") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** One symptom's strength for today, from 1 (a little) to 5 (very strongly); 0 removes it. */
@Composable
fun SymptomScreen(symptom: com.medipote.sync.WatchSymptom?, onSave: (Int) -> Unit) {
    var level by remember { mutableStateOf(symptom?.intensity ?: 0) }
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("${symptom?.emoji.orEmpty()} ${symptom?.name.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            item { Big(if (level == 0) "—" else "$level", if (level == 0) "pas aujourd'hui" else listOf("un peu", "léger", "modéré", "fort", "très fort")[level - 1]) }
            item {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    FilledTonalButton(onClick = { level = (level - 1).coerceAtLeast(0) }, label = { Text("−", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { level = (level + 1).coerceAtMost(5) }, label = { Text("+", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f))
                }
            }
            item { Button(onClick = { onSave(level) }, label = { Text("Enregistrer") }, secondaryLabel = { Text("Envoyé au téléphone") }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

/** A few tiny missions for a hard day; ticked ones stay ticked until the phone sends the next ones. */
@Composable
fun MissionsScreen(snapshot: WatchSnapshot?, actions: WearActions, onDone: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    var done by remember { mutableStateOf(setOf<String>()) }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("💛 Journée douce") } }
            val missions = snapshot?.missions.orEmpty()
            if (missions.isEmpty()) item { Info("Pas de mission pour le moment. Ouvre l'appli pour en voir d'autres.") }
            missions.forEach { m ->
                item {
                    val ticked = m.id in done
                    FilledTonalButton(
                        onClick = { if (!ticked) { done = done + m.id; actions.onMission(m.id) } },
                        label = { Text("${m.emoji} ${m.text}", maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { Text(if (ticked) "Fait ✓ bravo" else "Toucher quand c'est fait") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item { Info("Se reposer compte aussi. Rien n'est jamais en retard.") }
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
