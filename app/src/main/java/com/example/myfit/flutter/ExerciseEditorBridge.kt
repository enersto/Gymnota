package com.example.myfit.flutter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.decode.BitmapFactoryDecoder
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.myfit.data.CatalogMedia
import com.example.myfit.data.ExerciseCatalog
import com.example.myfit.model.ExerciseTemplate
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Android file/camera adapters only. Flutter owns the editor, Rust owns persistence. */
internal class ExerciseEditorBridge(
    private val activity: Activity,
    private val templates: () -> List<ExerciseTemplate>,
    private val save: suspend (ExerciseTemplate) -> Unit,
    private val extraPhotos: () -> Set<String> = { emptySet() },
) {
    private val draftPhotos = mutableSetOf<String>()
    private var photoResult: CompletableDeferred<String?>? = null
    private var cameraFile: File? = null
    private var closed = false

    suspend fun dispatch(operation: String, args: Map<*, *>): Any? = when (operation) {
        "save" -> {
            val id = (args["id"] as? Number)?.toLong() ?: 0L
            val previous = if (id == 0L) ExerciseTemplate(name = "", defaultTarget = "", category = "STRENGTH")
                else checkNotNull(templates().firstOrNull { it.id == id })
            fun text(key: String) = requireNotNull(args[key] as? String)
            require(text("name").isNotBlank())
            require(text("category") in setOf("STRENGTH", "CARDIO", "CORE"))
            val photo = args["imageUri"] as? String
            require(photo == null || photo in draftPhotos || photo == CatalogMedia.photoUri(previous))
            val animation = args["animationUri"] as? String
            require(animation == null || isBundled(animation) || animation == CatalogMedia.animationUri(activity, previous))
            val logType = (args["logType"] as? Number)?.toInt() ?: previous.logType
            require(logType in 0..2 || logType == previous.logType)
            val next = previous.copy(id = id, name = text("name"), defaultTarget = text("target"),
                category = text("category"), bodyPart = text("bodyPart"), equipment = text("equipmentKey"),
                isUnilateral = args["unilateral"] as? Boolean ?: false, logType = logType,
                instruction = text("instruction"), imageUri = photo, animationUri = animation)
            // Reserve the new photo before suspension: closing the host must not delete a committed photo.
            val reserved = photo != null && draftPhotos.remove(photo)
            try { withContext(NonCancellable) { save(next) } }
            catch (error: Throwable) {
                if (reserved && photo != null) { if (closed) File(photo).delete() else draftPhotos.add(photo) }
                throw error
            }
            true
        }
        "catalogDraft" -> {
            val id = args["sourceId"] as? String ?: error("Missing source id")
            val language = args["language"] as? String ?: "zh"
            val row = checkNotNull(ExerciseCatalog(activity).load(language).firstOrNull { it.sourceId == id })
            val known = templates().firstOrNull { CatalogMedia.animationUri(activity, it) == row.gifUri || it.name.equals(row.name, true) }
            if (known != null) mapOf("id" to known.id, "name" to known.name, "target" to known.defaultTarget,
                "category" to known.category, "bodyPart" to known.bodyPart, "equipmentKey" to known.equipment,
                "instruction" to known.instruction, "logType" to known.logType, "unilateral" to known.isUnilateral,
                "imageUri" to CatalogMedia.photoUri(known), "animationUri" to row.gifUri)
            else mapOf("id" to 0L, "name" to row.name, "target" to row.defaultTarget.orEmpty(),
                "category" to (row.category ?: "STRENGTH"), "bodyPart" to row.bodyPart,
                "equipmentKey" to row.equipment, "instruction" to row.instruction,
                "logType" to (row.logType ?: 0), "unilateral" to (row.isUnilateral ?: false),
                "animationUri" to row.gifUri, "imageUri" to null)
        }
        "photo" -> pickPhoto(args["source"] as? String ?: "gallery")
        "discardPhoto" -> { discard(args["uri"] as? String); true }
        "media" -> media(args["uri"] as? String ?: "")
        else -> error("Unknown editor operation")
    }

    private fun isBundled(uri: String): Boolean {
        val resolved = CatalogMedia.bundledUri(uri)
        return resolved.startsWith("file:///android_asset/catalog/videos/") &&
            resolved.removePrefix("file:///android_asset/catalog/videos/").matches(Regex("[A-Za-z0-9_-]+\\.webp"))
    }

    private suspend fun media(uri: String): ByteArray? {
        val resolved = CatalogMedia.bundledUri(uri)
        val bundled = isBundled(resolved)
        require(bundled || uri in draftPhotos || uri in extraPhotos() || templates().any {
            CatalogMedia.photoUri(it) == uri || CatalogMedia.animationUri(activity, it) == uri
        })
        return withContext(Dispatchers.IO) {
            if (bundled) activity.assets.open(resolved.removePrefix("file:///android_asset/")).use { it.readBytes() }
            else {
                val image = activity.imageLoader.execute(ImageRequest.Builder(activity)
                    .data(if (uri.startsWith("/")) File(uri) else uri).size(640).allowHardware(false)
                    .decoderFactory { source, options, _ -> BitmapFactoryDecoder(source.source, options) }.build()) as? SuccessResult
                image?.drawable?.toBitmap()?.let { bitmap -> ByteArrayOutputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray()
                } }
            }
        }
    }

    private suspend fun pickPhoto(source: String): String? {
        check(photoResult == null && !closed)
        val deferred = CompletableDeferred<String?>()
        photoResult = deferred
        try {
            if (source == "camera") {
                if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                    activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), 6223)
                else launchCamera()
            } else activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 6221)
            val uri = deferred.await() ?: return null
            val output = File(activity.filesDir, "img_${UUID.randomUUID()}.jpg")
            draftPhotos.add(output.path)
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(activity.contentResolver.openInputStream(Uri.parse(uri))).use { input ->
                        output.outputStream().use { input.copyTo(it) }
                    }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(output.path, bounds)
                    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid photo" }
                }
                return output.path
            } catch (error: Throwable) { discard(output.path); throw error }
        } finally { photoResult = null; cameraFile?.delete(); cameraFile = null }
    }

    private fun launchCamera() {
        val file = File.createTempFile("exercise-camera-", ".jpg", activity.cacheDir)
        cameraFile = file
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)
        activity.startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = android.content.ClipData.newRawUri("exercise-photo", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }, 6222)
    }

    fun permissionResult(code: Int, granted: Boolean): Boolean {
        if (code != 6223) return false
        if (granted && photoResult != null) try { launchCamera() } catch (e: Exception) { photoResult?.completeExceptionally(e) }
        else photoResult?.complete(null)
        return true
    }
    fun activityResult(code: Int, result: Int, data: Intent?): Boolean {
        if (code !in setOf(6221, 6222)) return false
        val uri = if (result != Activity.RESULT_OK) null else if (code == 6221) data?.data
            else cameraFile?.let { FileProvider.getUriForFile(activity, "${activity.packageName}.provider", it) }
        photoResult?.complete(uri?.toString())
        return true
    }
    private fun discard(uri: String?) { if (uri != null && draftPhotos.remove(uri)) File(uri).delete() }
    fun close() { closed = true; photoResult?.complete(null); draftPhotos.toList().forEach(::discard); cameraFile?.delete() }
}
