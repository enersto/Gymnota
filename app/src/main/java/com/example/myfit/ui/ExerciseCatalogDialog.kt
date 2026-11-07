package com.example.myfit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.example.myfit.R
import com.example.myfit.ui.components.GlassControlSurface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.ui.components.GlassDialogCard

/** Same Flutter catalogue inside the editor's existing animation picker. */
@Composable
fun ExerciseCatalogContent(language: String, onSelect: (ExerciseTemplate) -> Unit,
    modifier: Modifier = Modifier, searchQuery: String? = null) {
    val host = com.example.myfit.flutter.rememberEmbeddedCatalogHost(
        language, searchQuery.orEmpty(), onSelect, showSearch = searchQuery == null)
    com.example.myfit.flutter.EmbeddedCatalogView(host, modifier.fillMaxSize())
}

@Composable
fun ExerciseCatalogDialog(language: String, onDismiss: () -> Unit, onSelect: (ExerciseTemplate) -> Unit) {
    val host = com.example.myfit.flutter.rememberEmbeddedCatalogHost(language, "", onSelect, showSearch = true)
    Dialog(onDismissRequest = { host?.backOr(onDismiss) ?: onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }, contentAlignment = Alignment.Center) {
            GlassDialogCard(Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp)
                .pointerInput(Unit) { detectTapGestures { } }) {
                Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.titleLarge)
                    com.example.myfit.flutter.EmbeddedCatalogView(host, Modifier.weight(1f).fillMaxWidth())
                    GlassControlSurface(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            }
        }
    }
}
