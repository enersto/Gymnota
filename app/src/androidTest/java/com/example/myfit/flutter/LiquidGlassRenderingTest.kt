package com.example.myfit.flutter

import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfit.MainActivity
import io.flutter.embedding.engine.renderer.FlutterUiDisplayListener
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LiquidGlassRenderingTest {
    @Test fun shaderAssetsLoadAndScrollingProducesBoundedFrameDiagnostics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var host: EmbeddedCatalogHost
            val firstFrame = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.lifecycleScope.launch {
                val prepared = CatalogEnginePool.acquire(activity)
                host = EmbeddedCatalogHost(activity, mapOf("embedded" to true, "page" to "/embedded-catalog",
                    "language" to "en", "query" to "", "showSearch" to false, "dark" to false,
                    "primary" to 0xff009688L, "background" to 0xffe0f2f1L), { _, _ -> }, {}, prepared)
                host.view.addOnFirstFrameRenderedListener(object : FlutterUiDisplayListener {
                    override fun onFlutterUiDisplayed() { firstFrame.countDown() }
                    override fun onFlutterUiNoLongerDisplayed() {}
                })
                activity.addContentView(host.container, FrameLayout.LayoutParams(-1, -1))
                }
            }
            assertTrue("Flutter must render a frame", firstFrame.await(30, TimeUnit.SECONDS))
            fun diagnostics(reset: Boolean = false): Map<*, *> {
                val done = CountDownLatch(1)
                val response = AtomicReference<Map<*, *>?>()
                scenario.onActivity { host.diagnostics(reset, object : MethodChannel.Result {
                    override fun success(result: Any?) { response.set(result as? Map<*, *>); done.countDown() }
                    override fun error(code: String, message: String?, details: Any?) { done.countDown() }
                    override fun notImplemented() { done.countDown() }
                }) }
                assertTrue("Dart diagnostics must respond", done.await(10, TimeUnit.SECONDS))
                return requireNotNull(response.get())
            }
            try {
                var ready: Map<*, *> = emptyMap<Any, Any>()
                for (attempt in 0 until 30) {
                    ready = diagnostics()
                    if (ready["ready"] == true) break
                    Thread.sleep(100)
                }
                assertEquals(true, ready["ready"])
                assertEquals(true, ready["catalogRasterPrimed"])
                assertEquals("All fixed shader assets must load", true, ready["shaderProgramsLoaded"])
                diagnostics(true)
                repeat(6) { index ->
                    val command = if (index % 2 == 0) "input swipe 540 1900 540 800 400" else "input swipe 540 800 540 1900 400"
                    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
                }
                Thread.sleep(1000) // The engine batches FrameTiming callbacks.
                val measured = diagnostics()
                assertTrue((measured["samples"] as Number).toInt() > 0)
                assertTrue((measured["samples"] as Number).toInt() <= 240)
                Log.i("GYMNOTA_GLASS", JSONObject(measured).toString())
                ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input tap 540 900")).use { it.readBytes() }
                var popup: Map<*, *> = emptyMap<Any, Any>()
                for (attempt in 0 until 50) {
                    popup = diagnostics()
                    if (popup["popupFullyVisibleMs"] != null) break
                    Thread.sleep(100)
                }
                assertEquals(true, popup["popupActive"])
                assertEquals("Buttons must retain true refraction", "premium", popup["dialogGlassQuality"])
                assertNotNull("First dialog paint must be reported", popup["popupFirstPaintMs"])
                assertNotNull("Dialog entrance must finish", popup["popupFullyVisibleMs"])
                Log.i("GYMNOTA_GLASS_POPUP", JSONObject(popup).toString())
                ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
                for (attempt in 0 until 50) {
                    if (diagnostics()["popupActive"] == false) break
                    Thread.sleep(100)
                }
                assertEquals(false, diagnostics()["popupActive"])

            } finally { scenario.onActivity { host.dispose(); (host.container.parent as? android.view.ViewGroup)?.removeView(host.container) } }
        }
    }
}
