package com.example.myfit.flutter

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfit.MainActivity
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HistoryRootEntryTest {
    @Test fun nativeHistoryTabEmbedsAndReusesFlutterWithoutOpeningAnotherActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val prepared = CountDownLatch(1); val error = AtomicReference<Throwable?>()
            scenario.onActivity { activity -> activity.lifecycleScope.launch {
                try { HistoryEnginePool.recycle(HistoryEnginePool.acquire(activity)) }
                catch (e: Throwable) { error.set(e) }
                finally { prepared.countDown() }
            } }
            assertTrue(prepared.await(30, TimeUnit.SECONDS)); error.get()?.let { throw AssertionError(it) }
            fun tap(fraction: Double) {
                var x = 0; var y = 0
                scenario.onActivity { activity -> x = (activity.window.decorView.width * fraction).toInt()
                    y = activity.window.decorView.height - (48 * activity.resources.displayMetrics.density).toInt() }
                ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input tap $x $y")).use { it.readBytes() }
            }
            fun active(view: View): Boolean {
                var node: View? = view
                while (node != null) { if (node.visibility != View.VISIBLE || node.alpha == 0f) return false; node = node.parent as? View }
                return view.isAttachedToWindow
            }
            fun find(view: View): FlutterView? {
                if (view is FlutterView && active(view)) return view
                if (view is ViewGroup) for (i in 0 until view.childCount) { find(view.getChildAt(i))?.let { return it } }
                return null
            }
            fun visible(): FlutterView? { var result: FlutterView? = null; scenario.onActivity { result = find(it.window.decorView) }; return result }
            fun awaitView(): FlutterView {
                repeat(150) { visible()?.let { return it }; Thread.sleep(40) }; error("History never rendered")
            }
            val start = SystemClock.elapsedRealtime(); tap(.39)
            val first = awaitView(); val engine = first.attachedFlutterEngine
            assertNotNull(engine)
            Log.i("GYMNOTA_HISTORY_ROOT", "firstVisibleFromTapMs=" + (SystemClock.elapsedRealtime()-start))
            tap(.61)
            repeat(150) { if (visible() == null) return@repeat; Thread.sleep(20) }
            assertNull("The native AI tab must not remain covered by history", visible())
            val reentry = SystemClock.elapsedRealtime(); tap(.39)
            assertSame(engine, awaitView().attachedFlutterEngine)
            Log.i("GYMNOTA_HISTORY_ROOT", "reentryVisibleFromTapMs=" + (SystemClock.elapsedRealtime()-reentry))
            scenario.onActivity { assertTrue(it.hasWindowFocus()) }
        }
    }
}
