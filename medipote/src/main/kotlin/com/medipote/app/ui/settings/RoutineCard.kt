package com.medipote.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Anchor
import com.medipote.app.data.Routine
import com.medipote.app.ui.components.TimePickerModal

/** « Mes repas et mon réveil »: the times that doses tied to them (« 30 minutes avant le petit-déjeuner ») follow. */
@Composable
fun RoutineCard(routine: Routine, usualWake: Int?, onChange: (Routine) -> Unit) {
    var editing by remember { mutableStateOf<Anchor?>(null) }
    fun clock(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Anchor.entries.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = a }) { Text(clock(routine.minute(a))) }
            }
        }
        if (usualWake != null && usualWake != routine.wake) {
            TextButton(onClick = { onChange(routine.copy(wake = usualWake)) }) { Text("Prendre mon réveil habituel d'après mes nuits (${clock(usualWake)})") }
        }
        Text(
            "Une prise « 30 minutes avant le petit-déjeuner » ou « au coucher » suit ces heures : si elles changent, la prise aussi.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    editing?.let { a ->
        TimePickerModal(routine.minute(a), title = "Heure de ton ${a.label.lowercase()}", onDismiss = { editing = null }) { m -> onChange(routine.with(a, m)); editing = null }
    }
}
