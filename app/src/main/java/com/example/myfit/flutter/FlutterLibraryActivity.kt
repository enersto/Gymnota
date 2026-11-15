package com.example.myfit.flutter

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/** The history preview retains its activity; the catalogue is embedded in the original page. */
class FlutterLibraryActivity : FlutterActivity() {
    private val bridge by lazy { LibraryBridge(this) }
    private val coach by lazy { CoachBridge(this) }
    private val isCoach get() = intent.getStringExtra("page") == "/coach-preview"
    override fun getInitialRoute(): String = "/"
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        bridge.configure(flutterEngine)
        if (isCoach) coach.attach(flutterEngine)
    }
    @Deprecated("Legacy activity result used by FlutterActivity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        bridge.onActivityResult(requestCode, resultCode, data)
        if (isCoach) coach.activityResult(requestCode, resultCode, data)
    }
    override fun onDestroy() { if (isCoach) coach.close(); bridge.close(); super.onDestroy() }
}
