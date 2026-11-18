package com.example.myfit.flutter

import android.content.Intent
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.android.RenderMode
import io.flutter.embedding.engine.FlutterEngine

/** The history preview retains its activity; the catalogue is embedded in the original page. */
class FlutterLibraryActivity : FlutterFragmentActivity() {
    private val bridge by lazy { LibraryBridge(this) }
    private val coach by lazy { CoachBridge(this) }
    private val isRootPreview get() = intent.getStringExtra("page") in listOf(null, RootPreviewHost.PAGE)
    private val rootPreview by lazy { RootPreviewHost(this) }
    private val isCoach get() = intent.getStringExtra("page") == "/coach-preview"
    override fun getInitialRoute(): String = "/"
    override fun getRenderMode(): RenderMode = if (isRootPreview) RenderMode.texture else super.getRenderMode()
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        if (isRootPreview) {
            rootPreview.attach(flutterEngine)
            return
        }
        bridge.configure(flutterEngine)
        if (isCoach) coach.attach(flutterEngine)
    }
    @Deprecated("Legacy activity result used by FlutterActivity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (isRootPreview) {
            rootPreview.activityResult(requestCode, resultCode, data)
            rootPreview.coach.activityResult(requestCode, resultCode, data)
            return
        }
        bridge.onActivityResult(requestCode, resultCode, data)
        if (isCoach) coach.activityResult(requestCode, resultCode, data)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (isRootPreview) rootPreview.notificationPermissionResult(
            requestCode, grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    override fun onDestroy() {
        if (isRootPreview) rootPreview.close()
        else {
            if (isCoach) coach.close()
            bridge.close()
        }
        super.onDestroy()
    }
}
