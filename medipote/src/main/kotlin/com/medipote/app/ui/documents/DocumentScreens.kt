package com.medipote.app.ui.documents

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medipote.app.data.DocCategory
import com.medipote.app.data.DocumentStore
import com.medipote.app.data.HealthDocument
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.dayMonthYear
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun rememberThumbnail(doc: HealthDocument, maxPx: Int): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, doc.fileName) {
        value = withContext(Dispatchers.IO) { DocumentStore.thumbnail(context, doc, maxPx) }
    }
    return bitmap
}

@Composable
private fun Thumbnail(doc: HealthDocument, modifier: Modifier) {
    val bmp = rememberThumbnail(doc, 400)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), doc.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                if (doc.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp),
            )
        }
        if (doc.isPdf) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                Text("PDF", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

/** Embedded content of the "Documents" tab. */
@Composable
fun DocumentsScreen(documents: List<HealthDocument>, onTakePhoto: () -> Unit, onImport: () -> Unit, onOpen: (HealthDocument) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<DocCategory?>(null) }
    var menu by remember { mutableStateOf(false) }
    val shown = documents.filter { filter == null || it.category == filter }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(onClick = { menu = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Ajouter") })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Prendre une photo") }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                        onClick = { menu = false; onTakePhoto() },
                    )
                    DropdownMenuItem(
                        text = { Text("Importer un fichier (PDF, image)") }, leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                        onClick = { menu = false; onImport() },
                    )
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tous (${documents.size})") }) }
                    items(DocCategory.entries) { c ->
                        val n = documents.count { it.category == c }
                        FilterChip(selected = filter == c, onClick = { filter = if (filter == c) null else c }, label = { Text(if (n > 0) "${c.label} ($n)" else c.label) })
                    }
                }
            }
            if (documents.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Description, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Ton carnet de santé numérique", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Prends en photo tes ordonnances, résultats d'analyses ou comptes-rendus, ou importe un PDF.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { doc ->
                Card(onClick = { onOpen(doc) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Thumbnail(doc, Modifier.fillMaxWidth().aspectRatio(0.85f).clip(MaterialTheme.shapes.medium))
                    Column(Modifier.padding(10.dp)) {
                        Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${doc.category.label} · ${doc.date.format(com.medipote.app.ui.components.shortDate)}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Disclaimer("Tes documents restent dans le stockage privé de l'app, sur ce téléphone uniquement, et ne sont pas sauvegardés dans le cloud.")
            }
        }
    }
}

/** Title, category, date and notes of a new or existing document. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentInfoScreen(doc: HealthDocument, isNew: Boolean, onSave: (HealthDocument) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf(doc.title) }
    var category by remember { mutableStateOf(doc.category) }
    var date by remember { mutableStateOf(doc.date) }
    var notes by remember { mutableStateOf(doc.notes) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nouveau document" else "Modifier le document") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Annuler") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onSave(doc.copy(title = title.trim(), category = category, date = date, notes = notes.trim())) },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(doc, Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large))
            Text("Catégorie", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocCategory.entries.forEach { c ->
                    FilterChip(selected = category == c, onClick = {
                        if (title.isBlank() || DocCategory.entries.any { it.label == title }) title = c.label
                        category = c
                    }, label = { Text(c.label) })
                }
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(80) }, label = { Text("Titre *") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
            )
            DateField("Date du document", date, { date = it }, maxDate = LocalDate.now().plusDays(1))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notes (médecin, laboratoire…)") }, minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Full view: zoomable photo or every page of a PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(doc: HealthDocument, onBack: () -> Unit, onEdit: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val pages by produceState<List<Bitmap>?>(null, doc.fileName) {
        value = withContext(Dispatchers.IO) { DocumentStore.pages(context, doc, 1200) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${doc.category.label} · ${doc.date.format(dayMonthYear)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, "Partager") }
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Modifier") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val zoom = rememberTransformableState { z, pan, _ ->
            scale = (scale * z).coerceIn(1f, 5f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).transformable(zoom)
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (doc.notes.isNotBlank()) item { Text(doc.notes, style = MaterialTheme.typography.bodyMedium) }
            val list = pages
            when {
                list == null -> item { Text("Chargement…", style = MaterialTheme.typography.bodyMedium) }
                list.isEmpty() -> item { Text("Impossible d'afficher ce fichier. Utilise « Partager » pour l'ouvrir avec une autre app.") }
                else -> items(list) { bmp ->
                    Image(
                        bmp.asImageBitmap(), null, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(4.dp))
                    Text("Pince pour zoomer.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce document ?") },
            text = { Text("Le fichier sera effacé de ton téléphone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}
