package com.example.myfit.flutter

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import java.util.Locale
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.myfit.viewmodel.MainViewModel
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

internal class CoachBridge(
    private val activity: Activity,
    sharedViewModel: MainViewModel? = null,
    private val openSettings: (() -> Unit)? = null,
    private val pickImage: (() -> Unit)? = null,
) {
    private val store = ViewModelStore()
    private val vm = sharedViewModel ?: ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(activity.application))[MainViewModel::class.java]
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateCancel: MethodChannel? = null
    private var commandsChannel: MethodChannel? = null
    private var sink: EventChannel.EventSink? = null
    private var subscription: Job? = null
    private var picker: MethodChannel.Result? = null
    private var image: Uri? = null

    // FlutterActivity uses the device locale unless its resource context is explicitly localized.
    private fun promptContext(): android.content.Context {
        val language = vm.userProfile.value.languageCode
        val config = Configuration(activity.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return activity.createConfigurationContext(config)
    }

    fun attach(engine: FlutterEngine, stateChannel: String = "gymnota/coach/state") {
        val messenger = engine.dartExecutor.binaryMessenger
        val events = EventChannel(messenger, stateChannel)
        stateCancel = MethodChannel(messenger, stateChannel)
        events.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                sink = events
                subscription?.cancel()
                subscription = scope.launch {
                    combine(vm.aiResponse, vm.aiStreamState, vm.aiIsLoading, vm.aiStreamingEnabled, vm.aiCanImport,
                        vm.isGeneratingCsv, vm.userProfile, vm.connectionState) {
                        val profile = vm.userProfile.value
                        val stream = vm.aiStreamState.value
                        mapOf(
                            "configured" to profile.aiApiKey.isNotBlank(),
                            "model" to profile.aiModel,
                            "provider" to profile.aiProvider,
                            "answer" to (vm.aiResponse.value ?: ""),
                            "reasoning" to stream.reasoningSummary.ifBlank { stream.providerReasoning },
                            "status" to stream.status,
                            "connection" to when (vm.connectionState.value) {
                                MainViewModel.ConnectionState.Testing -> "testing"
                                MainViewModel.ConnectionState.Success -> "connected"
                                is MainViewModel.ConnectionState.Error -> "error"
                                MainViewModel.ConnectionState.Idle -> when (stream.status) {
                                    "answering", "reasoning", "completed" -> "connected"
                                    "connecting" -> "testing"
                                    "failed" -> "error"
                                    else -> "idle"
                                }
                            },
                            "busy" to vm.aiIsLoading.value,
                            "streaming" to vm.aiStreamingEnabled.value,
                            "canImport" to vm.aiCanImport.value,
                            "csvBusy" to vm.isGeneratingCsv.value,
                        )
                    }.collect { events.success(it) }
                }
            }
            override fun onCancel(arguments: Any?) { subscription?.cancel(); sink = null }
        })
        val commands = MethodChannel(messenger, "gymnota/coach")
        commandsChannel = commands
        commands.setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "settings" -> {
                        if (openSettings != null) openSettings.invoke()
                        else {
                            activity.setResult(Activity.RESULT_OK, Intent().putExtra("openAiSettings", true))
                            activity.finish()
                        }
                    }
                    "testConnection" -> {
                        val settings = vm.userProfile.value
                        if (!vm.aiIsLoading.value && !vm.isGeneratingCsv.value &&
                            vm.connectionState.value != MainViewModel.ConnectionState.Testing) {
                            vm.testAiConnection(settings.aiProvider, settings.aiApiKey, settings.aiModel, settings.aiBaseUrl)
                        }
                    }
                    "streaming" -> vm.setAiStreamingEnabled(call.arguments == true)
                    "stop" -> vm.cancelAiGeneration()
                    "send" -> {
                        if (vm.aiIsLoading.value || vm.isGeneratingCsv.value) {
                            result.error("busy", null, null)
                            return@setMethodCallHandler
                        }
                        val text = call.argument<String>("text").orEmpty()
                        if (call.argument<Boolean>("chat") == true) {
                            if (text.isNotBlank() || image != null) vm.sendFreeChat(promptContext(), text, image)
                            image = null
                        } else {
                            vm.currentTrainingGoal = text
                            vm.historyWeeks = (call.argument<Int>("weeks") ?: 3).coerceIn(1, 4)
                            vm.selectedFocus = call.argument<List<String>>("focus").orEmpty().toSet().ifEmpty { setOf("COMPREHENSIVE") }
                            vm.selectedScene = call.argument<List<String>>("scene").orEmpty().toSet().ifEmpty { setOf("GYM") }
                            vm.selectedInjuries = call.argument<List<String>>("injuries").orEmpty().toSet()
                            vm.generateWeeklyPlan(promptContext())
                        }
                    }
                    "history" -> {
                        scope.launch {
                            try {
                                val records = vm.aiChatHistory.first()
                                result.success(records.map { mapOf("id" to it.id, "goal" to it.userGoal, "answer" to it.content, "timestamp" to it.timestamp, "model" to it.modelUsed) })
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { result.error("history", null, null) }
                        }
                        return@setMethodCallHandler
                    }
                    "deleteHistory" -> {
                        val deletion = vm.requestAiHistoryDeletion((call.arguments as Number).toLong())
                        scope.launch {
                            try {
                                deletion.await()
                                result.success(true)
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { result.error("deleteHistory", null, null) }
                        }
                        return@setMethodCallHandler
                    }
                    "loadHistory" -> vm.loadAiResponseFromHistory(call.arguments as String)
                    "refine" -> vm.generateCsvFromAdvice(promptContext(), vm.aiResponse.value.orEmpty(), call.arguments as String) {
                        sink?.success(mapOf("csv" to it))
                    }
                    "import" -> vm.importWeeklyRoutine(activity, call.arguments as String)
                    "clearImage" -> { image = null }
                    "image" -> {
                        if (picker != null) { result.error("busy", null, null); return@setMethodCallHandler }
                        if (pickImage != null) pickImage.invoke()
                        else activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "image/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }, 6111)
                        picker = result
                        return@setMethodCallHandler
                    }
                    else -> { result.notImplemented(); return@setMethodCallHandler }
                }
                result.success(null)
            } catch (_: Exception) { result.error("coach", null, null) }
        }
    }

    fun activityResult(request: Int, result: Int, data: Intent?) {
        if (request != 6111) return
        selectedImage(data?.data?.takeIf { result == Activity.RESULT_OK })
    }

    fun selectedImage(selected: Uri?) {
        if (selected != null) image = selected
        picker?.success(selected != null)
        picker = null
    }

    fun close() {
        picker?.success(false)
        picker = null
        stateCancel?.let(::acknowledgeLateCancellation)
        commandsChannel?.setMethodCallHandler(null)
        stateCancel = null
        commandsChannel = null
        scope.cancel()
        store.clear()
    }
}

/** Dart disposes the old tab on its next frame, after the native host has parked. */
private fun acknowledgeLateCancellation(channel: MethodChannel) {
    val main = Handler(Looper.getMainLooper())
    val clear = Runnable { channel.setMethodCallHandler(null) }
    channel.setMethodCallHandler { call, result ->
        if (call.method == "cancel") {
            result.success(null)
            main.removeCallbacks(clear)
            clear.run()
        } else result.notImplemented()
    }
    main.postDelayed(clear, 2000)
}
