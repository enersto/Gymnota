package com.example.myfit.flutter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myfit.R
import com.example.myfit.model.AppTheme
import com.example.myfit.model.DayType
import com.example.myfit.ui.ImportDialog
import com.example.myfit.ui.KeyReferenceDialog
import com.example.myfit.ui.ManualRoutineDialog
import com.example.myfit.ui.ProfileEditDialog
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.viewmodel.MainViewModel
import java.time.LocalDate

@Composable
internal fun EmbeddedSettingsPage(
    viewModel: MainViewModel,
    aiRequest: String?,
    scheduleRequest: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context.activity()
    val language by viewModel.currentLanguage.collectAsState()
    val theme by viewModel.currentTheme.collectAsState()
    val schedules by viewModel.allSchedules.collectAsState(initial = emptyList())
    val profile by viewModel.userProfile.collectAsState()
    val connection by viewModel.connectionState.collectAsState()
    val colors = MaterialTheme.colorScheme
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var timerRevision by remember { mutableIntStateOf(0) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let { uri -> viewModel.exportHistoryToCsv(uri, context) }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) {
        it?.let { uri -> viewModel.backupDatabase(uri, context) }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> viewModel.restoreDatabase(uri, context) }
    }

    val timer = remember(timerRevision, viewModel) {
        mapOf("sound" to viewModel.getTimerSoundEnabled(),
            "prep" to viewModel.getTimerPrepEnabled(), "prepSeconds" to viewModel.getTimerPrepSeconds(),
            "final" to viewModel.getTimerFinalEnabled(), "finalSeconds" to viewModel.getTimerFinalSeconds())
    }
    val state = mapOf<String, Any?>(
        "embedded" to true, "page" to "/embedded-settings", "language" to language,
        "dark" to (colors.background.luminance() < 0.5f),
        "primary" to (colors.primary.toArgb().toLong() and 0xffffffffL),
        "onBackground" to (colors.onBackground.toArgb().toLong() and 0xffffffffL),
        "background" to (colors.background.toArgb().toLong() and 0xffffffffL),
        "secondary" to (colors.primaryContainer.toArgb().toLong() and 0xffffffffL),
        "requestedSection" to if (aiRequest != null) "ai-model" else if (scheduleRequest) "schedule-type" else null,
        "requestToken" to aiRequest,
        "selectedTheme" to theme.id,
        "themes" to AppTheme.entries.map { mapOf("id" to it.id, "color" to it.primary) },
        "schedule" to schedules.map {
            mapOf("day" to it.dayOfWeek, "type" to it.dayType.name, "color" to it.dayType.colorHex)
        },
        "timer" to timer,
        "provider" to profile.aiProvider,
        "model" to profile.aiModel,
        "modelConfigured" to profile.aiApiKey.isNotBlank(),
        "aiCache" to profile.useLocalAiCache,
        "aiHistoryLimit" to profile.maxHistoryLimit,
        "aiConnection" to when (connection) {
            MainViewModel.ConnectionState.Testing -> "testing"
            MainViewModel.ConnectionState.Success -> "success"
            is MainViewModel.ConnectionState.Error -> "error"
            MainViewModel.ConnectionState.Idle -> "idle"
        },
        "version" to com.example.myfit.BuildConfig.VERSION_NAME
    )

    val onAction by rememberUpdatedState<(String, Any?) -> Unit>({ action, value ->
        when (action) {
            "language" -> (value as? String)?.takeIf { it in setOf("zh", "en", "es", "ja", "de") && it != language }
                ?.let { viewModel.switchLanguage(it) }
            "theme" -> AppTheme.entries.firstOrNull { it.id == value }?.let { viewModel.switchTheme(it) }
            "schedule" -> schedules.firstOrNull { it.dayOfWeek == value }?.let {
                val next = DayType.entries[(it.dayType.ordinal + 1) % DayType.entries.size]
                viewModel.updateScheduleConfig(it.dayOfWeek, next)
            }
            "export" -> export.launch("history_${LocalDate.now()}.csv")
            "backup" -> backup.launch("backup.db")
            "restore" -> restore.launch(arrayOf("*/*"))
            "aiCache" -> (value as? Boolean)?.let(viewModel::toggleAiCache)
            "aiHistoryLimit" -> (value as? Int)?.takeIf { it in 1..20 }?.let(viewModel::updateMaxHistoryLimit)
            "import", "help", "profile", "weekly" -> dialog = action
            "sound", "prep", "final", "prepSeconds", "finalSeconds" -> {
                when (action) {
                    "sound" -> (value as? Boolean)?.let(viewModel::setTimerSoundEnabled)
                    "prep" -> (value as? Boolean)?.let(viewModel::setTimerPrepEnabled)
                    "final" -> (value as? Boolean)?.let(viewModel::setTimerFinalEnabled)
                    "prepSeconds" -> (value as? Int)?.takeIf { it in 3..30 }?.let(viewModel::setTimerPrepSeconds)
                    "finalSeconds" -> (value as? Int)?.takeIf { it in 3..15 }?.let(viewModel::setTimerFinalSeconds)
                }
                timerRevision++
            }
        }
    })
    var failed by remember(activity) { mutableStateOf(false) }
    var host by remember(activity) { mutableStateOf<EmbeddedCatalogHost?>(null) }
    LaunchedEffect(activity) {
        try {
            val prepared = try { HistoryEnginePool.acquire(activity) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { null }
            host = EmbeddedCatalogHost(activity, state, { _, _ -> }, {}, prepared,
                onSettingsAction = { action, value -> onAction(action, value) },
                onSettingsModel = { operation, fields -> modelSettings(viewModel, operation, fields) })
        } catch (_: IllegalStateException) {
            failed = true
        }
    }
    val active = host
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(active, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> active?.resume()
                Lifecycle.Event.ON_PAUSE -> active?.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); active?.dispose() }
    }
    SideEffect { active?.update(state) }

    if (failed) {
        fallback()
        return
    }

    GlassScaffoldContent { EmbeddedCatalogView(active, modifier) }

    BackHandler(enabled = active != null) {
        active?.backOr(onBack)
    }

    when (dialog) {
        "import" -> ImportDialog(stringResource(R.string.import_csv_template), { dialog = null }) {
            viewModel.importWeeklyRoutine(context, it)
            dialog = null
        }
        "help" -> KeyReferenceDialog { dialog = null }
        "profile" -> ProfileEditDialog(viewModel) { dialog = null }
        "weekly" -> ManualRoutineDialog(viewModel) { dialog = null }
    }
}

internal suspend fun modelSettings(
    viewModel: MainViewModel,
    operation: String,
    fields: Map<String, String>
): Map<String, Any?> {
    val settings = viewModel.userProfile.value
    val providers = (viewModel.availableProviders.map { it.name } + settings.aiProvider).distinct()
    val requested = fields.getValue("provider")
    val provider = requested.ifBlank { settings.aiProvider }
    check(provider in providers)
    return when (operation) {
        "load" -> {
            val (key, model, url) = if (provider == settings.aiProvider)
                Triple(settings.aiApiKey, settings.aiModel, settings.aiBaseUrl)
            else viewModel.getSavedProviderConfig(provider)
            mapOf("providers" to providers, "provider" to provider, "apiKey" to key, "model" to model, "baseUrl" to url)
        }
        "test", "save" -> {
            val key = fields.getValue("apiKey")
            val model = fields.getValue("model")
            val baseUrl = fields.getValue("baseUrl")
            if (operation == "test") viewModel.testAiConnection(provider, key, model, baseUrl)
            else viewModel.persistAiConfig(provider, key, model, baseUrl)
            emptyMap()
        }
        else -> error("Unknown model settings operation")
    }
}
