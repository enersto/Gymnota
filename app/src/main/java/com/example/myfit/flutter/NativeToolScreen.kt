package com.example.myfit.flutter

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.rememberNavController
import com.example.myfit.R
import com.example.myfit.data.ExerciseCatalog
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.ui.*
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.viewmodel.MainViewModel

@Composable
internal fun NativeToolScreen(
    tool: String,
    templateId: Long,
    sourceId: String?,
    viewModel: MainViewModel,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    GlassScaffoldContent {
        when (tool) {
            "exercise-picker" -> ExerciseSelectionScreen(viewModel, rememberNavController(), onClose = onFinish)
            "weight" -> WeightDialog(viewModel, onFinish)
            "profile" -> ProfileEditDialog(viewModel, onFinish)
            "weekly" -> ManualRoutineDialog(viewModel, onFinish)
            "help" -> KeyReferenceDialog(onFinish)
            "import" -> ImportDialog(stringResource(R.string.import_csv_template), onFinish) {
                viewModel.importWeeklyRoutine(context, it)
                onFinish()
            }
            else -> LaunchedEffect(Unit) { onFinish() }
        }
    }
}
