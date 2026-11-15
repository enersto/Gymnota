package com.example.myfit.flutter

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myfit.data.ExerciseCatalog
import com.example.myfit.model.ExerciseTemplate
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import kotlinx.coroutines.launch

internal tailrec fun Context.activity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> error("Catalogue requires an activity")
}

/** One renderer for the original tab, warmed while its sibling page is visible. */
private val catalogSessionIds = java.util.concurrent.atomic.AtomicLong()

class EmbeddedCatalogHost internal constructor(
    private val activity: Activity,
    initialState: Map<String, Any?>,
    onSelect: (String, String) -> Unit,
    onPersonal: () -> Unit,
    preparedEngine: FlutterEngine? = null,
    private val coachBridge: CoachBridge? = null,
    personalAction: ((String, Long?) -> Unit)? = null,
    personalMedia: ((Long) -> String?)? = null,
    onSettingsAction: ((String, Any?) -> Unit)? = null,
    onSettingsModel: (suspend (String, Map<String, String>) -> Map<String, Any?>)? = null
) {
    private var disposed = false
    private val history = initialState["page"] == "/embedded-history"
    private val sharedPage = initialState["page"] in setOf("/embedded-history", "/embedded-coach", "/embedded-settings")
    private val engine = preparedEngine ?: FlutterEngine(activity.applicationContext)
    private val texture = FlutterTextureView(activity).apply { isOpaque = false }
    val view = FlutterView(activity, texture)
    private val modalBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { backOr { overlay.close {} } }
    }
    private val overlay: CatalogOverlayWindow = CatalogOverlayWindow(activity, view, ::updateScene, ::sendBackdrop,
        { active -> modalBack.isEnabled = active }, captureBackdrop = !history && initialState["page"] != "/embedded-settings")
    internal val container get() = overlay.container
    private val sessionId = catalogSessionIds.incrementAndGet()
    private val coachEventChannel = coachBridge?.let { "gymnota/coach/state/$sessionId" }
    private val sessionFields = mapOf("catalogSession" to sessionId, "fixedViewport" to true,
        "coachEventChannel" to coachEventChannel)
    private val sessionState = initialState + sessionFields
    private val bridge: LibraryBridge = LibraryBridge(activity, true, sessionState, onSelect, onPersonal,
        onOverlayOpen = overlay::open, onOverlayClose = overlay::close, onOverlayBackdrop = overlay::backdrop, onOverlayReady = overlay::reveal, onInlineReady = overlay::inlineReady,
        onPersonalAction = personalAction, personalMedia = personalMedia, onSettingsAction = onSettingsAction, onSettingsModel = onSettingsModel)
    init {
        (activity as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(activity, modalBack)
        bridge.configure(engine)
        coachBridge?.attach(engine, requireNotNull(coachEventChannel))
        bridge.update(sessionState + mapOf("hostActive" to true))
        view.alpha = 0f
        view.addOnFirstFrameRenderedListener(object : io.flutter.embedding.engine.renderer.FlutterUiDisplayListener {
            override fun onFlutterUiDisplayed() { view.alpha = 1f }
            override fun onFlutterUiNoLongerDisplayed() {}
        })
        if (!engine.dartExecutor.isExecutingDart) engine.navigationChannel.setInitialRoute(if (history) "/embedded-history" else "/embedded-catalog")
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        view.attachToFlutterEngine(engine)
        if (engine.dartExecutor.isExecutingDart) {
            io.flutter.plugin.common.MethodChannel(engine.dartExecutor.binaryMessenger, if (sharedPage) "gymnota/historyWarmup" else "gymnota/catalogWarmup")
                .invokeMethod("activate", null)
        } else engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        engine.lifecycleChannel.appIsResumed()
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateScene() }
    }
    private var baseState: Map<String, Any?> = sessionState
    internal fun update(state: Map<String, Any?>) {
        val next = state + sessionFields
        if (next != baseState) { baseState = next; overlay.refreshBackdrop() }
        updateScene()
    }
    private fun sendBackdrop(bytes: ByteArray) {
        val density = activity.resources.displayMetrics.density
        bridge.updateBackdrop(sessionState["catalogSession"], bytes, view.width / density, view.height / density)
    }
    private fun updateScene() {
        if (disposed) return
        val decor = activity.window.decorView
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val density = activity.resources.displayMetrics.density
        val bounds = overlay.inlineBounds
        bridge.update(baseState + mapOf("inlineLeft" to bounds.left / density, "inlineTop" to bounds.top / density,
            "inlineWidth" to bounds.width() / density, "inlineHeight" to bounds.height() / density,
            "sceneX" to location[0] / density, "sceneY" to location[1] / density,
            "sceneWidth" to decor.width / density, "sceneHeight" to decor.height / density))
    }
    fun backOr(close: () -> Unit): Unit = bridge.popOrClose(close)
    internal fun diagnostics(reset: Boolean, result: io.flutter.plugin.common.MethodChannel.Result) {
        io.flutter.plugin.common.MethodChannel(engine.dartExecutor.binaryMessenger, "gymnota/library")
            .invokeMethod("glassDiagnostics", reset, result)
    }
    internal fun resume() = engine.lifecycleChannel.appIsResumed()
    internal fun pause() {
        engine.lifecycleChannel.appIsInactive()
        engine.lifecycleChannel.appIsPaused()
    }
    internal fun dispose() {
        if (disposed) return
        disposed = true
        modalBack.remove()
        val reusable = !overlay.isPresenting
        overlay.dispose()
        if (reusable && sharedPage) bridge.park()
        bridge.close()
        coachBridge?.close()
        view.detachFromFlutterEngine()
        if (reusable) {
            if (sharedPage) HistoryEnginePool.recycle(engine) else CatalogEnginePool.recycle(engine)
        } else {
            if (sharedPage) HistoryEnginePool.relinquish(engine)
            engine.lifecycleChannel.appIsDetached()
            engine.destroy()
        }
    }
}

@Composable
fun rememberEmbeddedCatalogHost(
    language: String,
    query: String,
    onSelect: (ExerciseTemplate) -> Unit,
    onPersonal: (() -> Unit)? = null,
    showSearch: Boolean = false,
    activate: Boolean = true,
    personalRows: List<Map<String, Any?>>? = null,
    personalSelected: Boolean = true,
    onPersonalAction: ((String, Long?) -> Unit)? = null,
    personalMedia: ((Long) -> String?)? = null
): EmbeddedCatalogHost? {
    val context = LocalContext.current
    val activity = context.activity()
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    val personal by rememberUpdatedState(onPersonal)
    val personalAction by rememberUpdatedState(onPersonalAction)
    val media by rememberUpdatedState(personalMedia)
    val colors = MaterialTheme.colorScheme
    val state = mapOf<String, Any?>("embedded" to true, "page" to "/embedded-catalog", "language" to language,
        "query" to query, "showSearch" to showSearch, "canSwipe" to false,
        "dark" to (colors.background.luminance() < 0.5f),
        "primary" to (colors.primary.toArgb().toLong() and 0xffffffffL),
        "background" to (colors.background.toArgb().toLong() and 0xffffffffL),
        "secondary" to (colors.primaryContainer.toArgb().toLong() and 0xffffffffL),
        "surface" to (colors.surface.toArgb().toLong() and 0xffffffffL),
        "onSurface" to (colors.onSurface.toArgb().toLong() and 0xffffffffL),
        "onBackground" to (colors.onBackground.toArgb().toLong() and 0xffffffffL),
        "exerciseManager" to (personalRows != null), "personalRows" to personalRows,
        "personalSelected" to personalSelected, "personalSearchRevision" to personalRows?.hashCode())
    var host by remember(activity) { mutableStateOf<EmbeddedCatalogHost?>(null) }
    LaunchedEffect(activity, activate) {
        if (!activate || host != null) return@LaunchedEffect
        val prepared = try { CatalogEnginePool.acquire(activity) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
        host = EmbeddedCatalogHost(activity, state, { id, locale ->
            scope.launch {
                ExerciseCatalog(context).load(locale).firstOrNull { it.sourceId == id }?.toTemplate()?.let(select)
            }
        }, { personal?.invoke() }, prepared,
            personalAction = { action, id -> personalAction?.invoke(action, id) },
            personalMedia = { id -> media?.invoke(id) })
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = host
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
    return host
}

@Composable
fun EmbeddedCatalogView(host: EmbeddedCatalogHost?, modifier: Modifier = Modifier) {
    if (host == null) {
        androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    } else AndroidView(factory = { host.container }, modifier = modifier)
}

/** The normal history tab, using a pre-rendered engine instead of a new Activity. */
@Composable
fun rememberEmbeddedHistoryHost(language: String): EmbeddedCatalogHost? {
    val context = LocalContext.current
    val activity = context.activity()
    val colors = MaterialTheme.colorScheme
    val state = mapOf<String, Any?>("embedded" to true, "page" to "/embedded-history", "language" to language,
        "onBackground" to (colors.onBackground.toArgb().toLong() and 0xffffffffL),
        "dark" to (colors.background.luminance() < 0.5f),
        "primary" to (colors.primary.toArgb().toLong() and 0xffffffffL),
        "background" to (colors.background.toArgb().toLong() and 0xffffffffL),
        "secondary" to (colors.primaryContainer.toArgb().toLong() and 0xffffffffL))
    var host by remember(activity) { mutableStateOf<EmbeddedCatalogHost?>(null) }
    LaunchedEffect(activity) {
        val prepared = try { HistoryEnginePool.acquire(activity) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
        host = EmbeddedCatalogHost(activity, state, { _, _ -> }, {}, prepared)
    }
    val active = host
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(active, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_RESUME -> active?.resume()
            Lifecycle.Event.ON_PAUSE -> active?.pause()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); active?.dispose() }
    }
    SideEffect { active?.update(state) }
    return host
}

/** Embeds the coach in the root tab and borrows the Activity's existing ViewModel. */
@Composable
fun EmbeddedCoachPage(
    viewModel: com.example.myfit.viewmodel.MainViewModel,
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.activity()
    val language by viewModel.currentLanguage.collectAsState()
    val colors = MaterialTheme.colorScheme
    val settingsAction by rememberUpdatedState(openSettings)
    var coach by remember { mutableStateOf<CoachBridge?>(null) }
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> coach?.selectedImage(uri) }

    val state = mapOf<String, Any?>(
        "embedded" to true, "page" to "/embedded-coach", "language" to language,
        "dark" to (colors.background.luminance() < 0.5f),
        "primary" to (colors.primary.toArgb().toLong() and 0xffffffffL),
        "onBackground" to (colors.onBackground.toArgb().toLong() and 0xffffffffL),
        "background" to (colors.background.toArgb().toLong() and 0xffffffffL),
        "secondary" to (colors.primaryContainer.toArgb().toLong() and 0xffffffffL),
    )
    var host by remember { mutableStateOf<EmbeddedCatalogHost?>(null) }
    LaunchedEffect(activity, viewModel) {
        val prepared = try { HistoryEnginePool.acquire(activity) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
        val bridge = CoachBridge(activity, viewModel, { settingsAction() }) {
            gallery.launch(arrayOf("image/*"))
        }
        coach = bridge
        host = EmbeddedCatalogHost(activity, state, { _, _ -> }, {}, prepared, coachBridge = bridge)
    }
    val active = host
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(active, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_RESUME -> active?.resume()
            Lifecycle.Event.ON_PAUSE -> active?.pause()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); active?.dispose() }
    }
    SideEffect { active?.update(state) }
    EmbeddedCatalogView(active, modifier)
}
