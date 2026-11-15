package com.example.myfit.flutter

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import io.flutter.FlutterInjector
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Prepare data, widgets and real GPU frames, not just shader bytecode. */
internal object CatalogEnginePool {
    private val main = Handler(Looper.getMainLooper())
    private val claim = Mutex()
    private var pending: CompletableDeferred<FlutterEngine>? = null
    private var cancelWarmup: ((Activity) -> Unit)? = null

    fun warm(activity: Activity) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (pending != null || activity.isFinishing || activity.isDestroyed) return
        val ready = CompletableDeferred<FlutterEngine>()
        pending = ready
        val app = activity.applicationContext
        val loader = FlutterInjector.instance().flutterLoader()
        loader.startInitialization(app)
        loader.ensureInitializationCompleteAsync(app, null, main) {
            if (activity.isDestroyed || activity.isFinishing) {
                if (pending === ready) pending = null
                ready.cancel()
                return@ensureInitializationCompleteAsync
            }
            var engine: FlutterEngine? = null
            var bridge: LibraryBridge? = null
            var view: FlutterView? = null
            var slot: FrameLayout? = null
            var finished = false
            var primed = false
            var firstRaster = false
            var timeout: Runnable? = null
            var warmChannel: MethodChannel? = null
            fun finish(error: Throwable? = null) {
                if (finished) return
                finished = true
                timeout?.let(main::removeCallbacks)
                bridge?.close()
                view?.detachFromFlutterEngine()
                (slot?.parent as? ViewGroup)?.removeView(slot)
                warmChannel?.setMethodCallHandler(null)
                cancelWarmup = null
                if (error == null) {
                    engine!!.lifecycleChannel.appIsInactive()
                    engine!!.lifecycleChannel.appIsPaused()
                    ready.complete(engine!!)
                } else {
                    engine?.destroy()
                    if (pending === ready) pending = null
                    ready.completeExceptionally(error)
                }
            }
            try {
                engine = FlutterEngine(app)
                val active = engine!!
                val locale = activity.resources.configuration.locales[0].language
                val language = locale.takeIf { it in setOf("zh", "en", "es", "de", "ja") } ?: "en"
                bridge = LibraryBridge(activity, true, mapOf("embedded" to true,
                    "page" to "/embedded-catalog", "language" to language,
                    "query" to "", "showSearch" to false, "dark" to false))
                bridge!!.configure(active)
                warmChannel = MethodChannel(active.dartExecutor.binaryMessenger, "gymnota/catalogWarmup")
                warmChannel!!.setMethodCallHandler { call, result ->
                    if (call.method == "primed") { result.success(true); primed = true; if (firstRaster) finish() }
                    else result.notImplemented()
                }
                view = FlutterView(app, FlutterTextureView(app).apply { isOpaque = false })
                view!!.addOnFirstFrameRenderedListener(object : io.flutter.embedding.engine.renderer.FlutterUiDisplayListener {
                    override fun onFlutterUiDisplayed() { firstRaster = true; if (primed) finish() }
                    override fun onFlutterUiNoLongerDisplayed() {}
                })
                // Alpha affects composition only: Flutter still lays out and submits GPU frames.
                // This slot never receives touch and is removed when the first real list is ready.
                slot = object : FrameLayout(activity) {
                    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
                }.apply {
                    alpha = 0f
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    addView(view, FrameLayout.LayoutParams(-1, -1))
                }
                activity.findViewById<ViewGroup>(android.R.id.content).addView(slot, ViewGroup.LayoutParams(-1, -1))
                view!!.attachToFlutterEngine(active)
                active.navigationChannel.setInitialRoute("/embedded-catalog")
                active.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint(loader.findAppBundlePath(), "warmCatalog"))
                active.lifecycleChannel.appIsResumed()
                cancelWarmup = { owner -> if (owner === activity) finish(java.util.concurrent.CancellationException("Activity destroyed")) }
                timeout = Runnable { if (!finished) finish(IllegalStateException("Catalogue render warmup timed out")) }
                main.postDelayed(timeout!!, 15000)
            } catch (e: Exception) { finish(e) }
        }
    }

    fun releaseWarmup(activity: Activity) { cancelWarmup?.invoke(activity) }

    /** At most one idle renderer survives page exit; it owns no host Activity/view. */
    fun recycle(engine: FlutterEngine) {
        check(Looper.myLooper() == Looper.getMainLooper())
        engine.lifecycleChannel.appIsInactive()
        engine.lifecycleChannel.appIsPaused()
        if (pending == null) pending = CompletableDeferred<FlutterEngine>().apply { complete(engine) }
        else engine.destroy()
    }

    suspend fun acquire(activity: Activity): FlutterEngine = claim.withLock {
        warm(activity)
        val ready = requireNotNull(pending)
        val engine = ready.await()
        if (pending === ready) pending = null
        engine
    }
}
