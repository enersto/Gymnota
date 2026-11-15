package com.example.myfit.flutter

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.compose.ui.window.DialogWindowProvider
import io.flutter.embedding.android.FlutterView
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlinx.coroutines.*

/** Legacy name, fixed host: no native Dialog, view reparenting or resize on detail open/close. */
internal class CatalogOverlayWindow(
    private val activity: Activity,
    private val view: FlutterView,
    private val onRegionChanged: () -> Unit = {},
    private val onBackdropReady: (ByteArray) -> Unit = {},
    private val onModalChanged: (Boolean) -> Unit = {},
    private val captureBackdrop: Boolean = true
) {
    val container = FrameLayout(activity) // Layout/touch marker in the original Compose pane.
    internal val inlineBounds = Rect()
    private val scope = MainScope()
    private val main = Handler(Looper.getMainLooper())
    private var disposed = false
    private var modal = false
    private var revealed = false
    private var gestureCaptured = false
    private var targetWindow: Window? = null
    private var targetRoot: ViewGroup? = null
    private var snapshotJob: Job? = null
    private var dirty = true
    private val hiddenNativeAccessibility = mutableListOf<Pair<View, Int>>()
    @Volatile private var cachedBackdrop: ByteArray? = null
    internal val isShowing get() = modal
    internal val isPresenting get() = modal
    internal val isRevealed get() = modal && revealed

    private val surface = object : FrameLayout(activity) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (disposed || targetRoot == null) return false
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                gestureCaptured = modal || (container.isShown && inlineBounds.contains(event.x.toInt(), event.y.toInt()))
            }
            if (!gestureCaptured) return false // Native title, tabs, buttons keep their original hit targets.
            super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) gestureCaptured = false
            return true
        }
    }.apply {
        setBackgroundColor(Color.TRANSPARENT)
        addView(view, FrameLayout.LayoutParams(-1, -1))
    }
    private val layoutObserver = ViewTreeObserver.OnGlobalLayoutListener { updateBounds() }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { main.post { if (!disposed && container.isAttachedToWindow) attach() } }
        override fun onViewDetachedFromWindow(v: View) { detach() }
    }
    init {
        container.addOnAttachStateChangeListener(attachment)
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds() }
    }

    private fun attach() {
        if (disposed) return
        var parent = container.parent
        var window = activity.window
        while (parent != null) {
            if (parent is DialogWindowProvider) { window = parent.window; break }
            parent = parent.parent
        }
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: window.decorView as ViewGroup
        if (targetRoot !== root) {
            detach()
            targetWindow = window; targetRoot = root
            // Compose DialogLayout carries elevation; draw and hit-test the fixed renderer above it.
            surface.elevation = (0 until root.childCount).maxOfOrNull { root.getChildAt(it).z }?.plus(activity.resources.displayMetrics.density) ?: 0f
            root.addView(surface, ViewGroup.LayoutParams(-1, -1))
            root.viewTreeObserver.addOnGlobalLayoutListener(layoutObserver)
            dirty = true
        }
        updateBounds()
    }

    private fun detach() {
        snapshotJob?.cancel(); snapshotJob = null
        targetRoot?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutObserver)
        val oldParent = surface.parent as? ViewGroup
        surface.visibility = View.GONE
        // A slot may detach while its ancestor is walking the child array. Do not mutate that array inline.
        main.post { if (surface.parent === oldParent) oldParent?.removeView(surface) }
        targetRoot = null; targetWindow = null
        inlineBounds.setEmpty()
        gestureCaptured = false
        onRegionChanged()
    }

    private fun updateBounds() {
        val root = targetRoot ?: return
        val slot = IntArray(2); val origin = IntArray(2)
        container.getLocationOnScreen(slot); root.getLocationOnScreen(origin)
        val next = Rect(slot[0] - origin[0], slot[1] - origin[1],
            slot[0] - origin[0] + container.width, slot[1] - origin[1] + container.height)
        val visible = container.isShown && next.intersect(0, 0, root.width, root.height)
        if (!visible) next.setEmpty()
        val changed = next != inlineBounds
        inlineBounds.set(next)
        surface.visibility = if (visible || modal) View.VISIBLE else View.GONE
        if (changed) { onRegionChanged(); refreshBackdrop() }
    }

    fun open(done: (Boolean) -> Unit) {
        if (modal) { done(true); return }
        if (disposed || targetRoot == null || view.width == 0 || view.height == 0) { done(false); return }
        modal = true; revealed = false
        targetRoot?.let { root ->
            for (index in 0 until root.childCount) {
                val child = root.getChildAt(index)
                if (child !== surface) {
                    hiddenNativeAccessibility.add(child to child.importantForAccessibility)
                    child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            }
        }
        view.requestFocus()
        onModalChanged(true)
        surface.visibility = View.VISIBLE
        done(true) // No new window, layout wait, readback, PNG encoding, or renderer move.
    }
    fun reveal() { revealed = modal }
    private fun restoreNativeAccessibility() {
        hiddenNativeAccessibility.forEach { (child, old) -> child.importantForAccessibility = old }
        hiddenNativeAccessibility.clear()
    }
    fun close(done: () -> Unit) {
        modal = false; revealed = false
        restoreNativeAccessibility()
        onModalChanged(false)
        updateBounds()
        if (dirty) refreshBackdrop()
        done()
    }
    fun inlineReady() = Unit // Coordinates never changed.
    suspend fun backdrop(): ByteArray? = cachedBackdrop // Compatibility only; not on the click path.

    /** Refresh native chrome during idle time; the live Flutter list is drawn over this image. */
    fun refreshBackdrop() {
        if (!captureBackdrop) return
        dirty = true
        snapshotJob?.cancel()
        if (disposed || modal || targetRoot == null || inlineBounds.isEmpty) return
        snapshotJob = scope.launch {
            delay(180)
            val window = targetWindow ?: return@launch
            // PixelCopy of one dialog cannot reconstruct intermediate editor windows.
            // Leave those native layers live instead of replacing them with an incomplete activity snapshot.
            if (window !== activity.window) return@launch
            val root = targetRoot ?: return@launch
            if (modal || !container.isAttachedToWindow || root.width <= 0 || root.height <= 0) return@launch
            val epoch = root
            val snapshotWidth = root.width; val snapshotHeight = root.height
            val location = IntArray(2); root.getLocationInWindow(location)
            val rect = Rect(location[0], location[1], location[0] + root.width, location[1] + root.height)
            val scale = min(1f, 480f / root.width)
            suspend fun copy(source: Window, bounds: Rect): Bitmap? {
                val result = CompletableDeferred<Bitmap?>()
                val bitmap = Bitmap.createBitmap((root.width * scale).toInt().coerceAtLeast(1),
                    (root.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(source, bounds, bitmap, { status ->
                        result.complete(if (status == PixelCopy.SUCCESS) bitmap else null)
                    }, Handler(Looper.getMainLooper()))
                } catch (_: IllegalArgumentException) { result.complete(null) }
                return withTimeoutOrNull(300) { result.await() }
            }
            val bitmap = copy(window, rect) ?: return@launch
            val bytes = withContext(Dispatchers.Default) {
                ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
            }
            if (!disposed && !modal && targetRoot === epoch && root.width == snapshotWidth && root.height == snapshotHeight) {
                cachedBackdrop = bytes; dirty = false; onBackdropReady(bytes)
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true; modal = false
        restoreNativeAccessibility()
        onModalChanged(false)
        container.removeOnAttachStateChangeListener(attachment)
        detach(); scope.cancel()
        main.post { surface.removeView(view) }
        cachedBackdrop = null
    }
}
