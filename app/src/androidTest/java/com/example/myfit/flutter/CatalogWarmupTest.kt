package com.example.myfit.flutter

import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myfit.MainActivity
import io.flutter.embedding.engine.renderer.FlutterUiDisplayListener
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CatalogWarmupTest {
    @Test fun warmedIsolateActivatesWithTheAttachedHostsBootstrapAndRenders() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var host: EmbeddedCatalogHost
            val rendered = CountDownLatch(1)
            val error = AtomicReference<Throwable?>()
            val borrowed = AtomicReference<io.flutter.embedding.engine.FlutterEngine>()
            scenario.onActivity { activity ->
                activity.lifecycleScope.launch {
                    try {
                        val start = SystemClock.elapsedRealtime()
                        val engine = CatalogEnginePool.acquire(activity)
                        borrowed.set(engine)
                        assertTrue("Dart isolate must already be executing", engine.dartExecutor.isExecutingDart)
                        val attachStart = SystemClock.elapsedRealtime()
                        host = EmbeddedCatalogHost(activity, mapOf("embedded" to true,
                            "language" to "es", "page" to "/embedded-catalog", "query" to "",
                            "showSearch" to false, "dark" to false), { _, _ -> }, {}, engine)
                        host.view.addOnFirstFrameRenderedListener(object : FlutterUiDisplayListener {
                            override fun onFlutterUiDisplayed() {
                                Log.i("GYMNOTA_WARMUP", "coldAwaitAndFirstFrameMs=" + (SystemClock.elapsedRealtime() - start) + ", preparedAttachAndFirstFrameMs=" + (SystemClock.elapsedRealtime() - attachStart))
                                rendered.countDown()
                            }
                            override fun onFlutterUiNoLongerDisplayed() {}
                        })
                        activity.addContentView(host.container, FrameLayout.LayoutParams(-1, -1))
                    } catch (e: Throwable) { error.set(e); rendered.countDown() }
                }
            }
            assertTrue("Prewarmed catalogue must render", rendered.await(30, TimeUnit.SECONDS))
            error.get()?.let { throw AssertionError("Warmup failed", it) }
            fun ready(): Boolean {
                val done = CountDownLatch(1)
                val value = AtomicReference<Any?>()
                scenario.onActivity { host.diagnostics(false, object : MethodChannel.Result {
                    override fun success(result: Any?) { value.set(result); done.countDown() }
                    override fun error(code: String, message: String?, details: Any?) { done.countDown() }
                    override fun notImplemented() { done.countDown() }
                }) }
                assertTrue(done.await(10, TimeUnit.SECONDS))
                val result = value.get() as? Map<*, *>
                assertEquals("Actual catalogue UI must be prepainted", true, result?.get("catalogRasterPrimed"))
                return result?.get("ready") == true
            }
            var loaded = false
            for (attempt in 0 until 50) {
                loaded = ready()
                if (loaded) break
                Thread.sleep(100)
            }
            assertTrue("Activation must use the real catalogue bridge", loaded)
            scenario.onActivity { host.dispose(); host.dispose(); (host.container.parent as? android.view.ViewGroup)?.removeView(host.container) }
            val reopened = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.lifecycleScope.launch {
                    try {
                        val start = SystemClock.elapsedRealtime()
                        val engine = CatalogEnginePool.acquire(activity)
                        assertSame("Page exit must retain the prepared engine", borrowed.get(), engine)
                        host = EmbeddedCatalogHost(activity, mapOf("embedded" to true,
                            "language" to "en", "query" to "", "showSearch" to false,
                            "dark" to false), { _, _ -> }, {}, engine)
                        host.view.addOnFirstFrameRenderedListener(object : FlutterUiDisplayListener {
                            override fun onFlutterUiDisplayed() {
                                Log.i("GYMNOTA_REENTRY", "recycledAttachAndFirstFrameMs=" + (SystemClock.elapsedRealtime() - start))
                                reopened.countDown()
                            }
                            override fun onFlutterUiNoLongerDisplayed() {}
                        })
                        activity.addContentView(host.container, FrameLayout.LayoutParams(-1, -1))
                    } catch (e: Throwable) { error.set(e); reopened.countDown() }
                }
            }
            assertTrue("Returned engine must render after rebinding", reopened.await(10, TimeUnit.SECONDS))
            error.get()?.let { throw AssertionError("Reentry failed", it) }
            assertTrue(ready())
            scenario.onActivity { host.dispose(); (host.container.parent as? android.view.ViewGroup)?.removeView(host.container) }
        }
    }
}
