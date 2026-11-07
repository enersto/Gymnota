package com.example.myfit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myfit.R
import com.example.myfit.data.CatalogExercise
import com.example.myfit.data.ExerciseCatalog
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.ui.components.*

/** Full-page browser also reused by the editor's animation picker. */
@Composable
fun ExerciseCatalogContent(
    language: String,
    onSelect: (ExerciseTemplate) -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String? = null
) {
    val context = LocalContext.current
    var entries by remember(language) { mutableStateOf<List<CatalogExercise>>(emptyList()) }
    var loading by remember(language) { mutableStateOf(true) }
    var failed by remember(language) { mutableStateOf(false) }
    var localQuery by rememberSaveable { mutableStateOf("") }
    var bodyPart by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ExerciseTemplate?>(null) }
    LaunchedEffect(language) {
        try {
            entries = ExerciseCatalog(context).load(language)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            failed = true
        } finally {
            loading = false
        }
    }
    val query = searchQuery ?: localQuery
    val filtered = remember(entries, query, bodyPart) {
        entries.filter {
            (bodyPart == null || it.bodyPart == bodyPart) &&
                (it.name.contains(query, true) || it.canonicalName.contains(query, true) ||
                    it.sourceId == query || it.instruction.contains(query, true))
        }
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.catalog_setup_hint), style = MaterialTheme.typography.bodySmall)
        if (searchQuery == null) {
            TextField(
                value = localQuery, onValueChange = { localQuery = it },
                label = { Text(stringResource(R.string.catalog_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().glassPanel(shape = RoundedCornerShape(16.dp), interactive = false),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlassChoiceChip(stringResource(R.string.catalog_all_parts), bodyPart == null, { bodyPart = null })
            BODY_PART_OPTIONS.forEach { key ->
                GlassChoiceChip(stringResource(getBodyPartResId(key)), bodyPart == key, { bodyPart = key })
            }
        }
        when {
            loading -> CircularProgressIndicator()
            failed -> Text(stringResource(R.string.catalog_load_failed))
            filtered.isEmpty() -> Text(stringResource(R.string.catalog_empty))
            else -> LazyColumn(
                Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(filtered, key = { it.sourceId }) { entry ->
                    GlassCard(Modifier.clickable { preview = entry.toTemplate() }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            ExerciseThumbnail(entry.gifUri, entry.name, Modifier.size(64.dp), contentScale = ContentScale.Fit)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(getBodyPartResId(entry.bodyPart)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    preview?.let { template ->
        ExerciseDetailDialog(template, onDismiss = { preview = null }, onEdit = {
            preview = null
            onSelect(template)
        }, actionLabel = stringResource(R.string.catalog_use_exercise))
    }
}

@Composable
fun ExerciseCatalogDialog(language: String, onDismiss: () -> Unit, onSelect: (ExerciseTemplate) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassDialogCard {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.titleLarge)
                ExerciseCatalogContent(language, onSelect, Modifier.weight(1f))
                GlassControlSurface(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
    }
}
