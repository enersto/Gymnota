package com.example.myfit.flutter

import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfit.MainActivity
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogOverlayWindowTest {
    @Test fun fixedHostNeverMovesOrResizesTheRendererForDetailsAndPassesNativeTouches() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val laidOut = CountDownLatch(1)
            lateinit var host: CatalogOverlayWindow
            lateinit var view: FlutterView
            scenario.onActivity { activity ->
                view = FlutterView(activity, FlutterTextureView(activity).apply { isOpaque = false })
                view.doOnLayout { laidOut.countDown() }
                host = CatalogOverlayWindow(activity, view)
                host.container.elevation = 32f // Match elevated Compose DialogLayout siblings.
                activity.addContentView(host.container, FrameLayout.LayoutParams(-1, 240, Gravity.BOTTOM))
            }
            assertTrue("Fixed surface must be laid out", laidOut.await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val parent = view.parent as FrameLayout
                assertTrue("Fixed surface must draw above elevated dialog content", parent.z > host.container.z)
                val width = view.width; val height = view.height
                assertTrue(height > host.container.height * 3)
                assertEquals(240, host.inlineBounds.height())
                fun outsideTouch(): Boolean {
                    val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, width / 2f, 20f, 0)
                    val up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, width / 2f, 20f, 0)
                    val handled = parent.dispatchTouchEvent(down)
                    parent.dispatchTouchEvent(up); down.recycle(); up.recycle()
                    return handled
                }
                repeat(2) {
                    assertFalse("Native toolbar remains interactive without a modal", outsideTouch())
                    var opened = false; host.open { opened = it }; assertTrue(opened)
                    assertTrue(outsideTouch())
                    assertSame(parent, view.parent)
                    assertEquals(width, view.width); assertEquals(height, view.height)
                    assertEquals(1f, view.alpha)
                    host.close {}
                    assertFalse(host.isShowing)
                    assertSame(parent, view.parent)
                    assertEquals(width, view.width); assertEquals(height, view.height)
                    assertFalse(outsideTouch())
                    assertEquals(0, host.container.childCount)
                }
                host.dispose()

            }
        }
    }
}
