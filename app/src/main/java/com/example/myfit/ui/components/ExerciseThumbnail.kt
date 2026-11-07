package com.example.myfit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.ImageRequest

/** List thumbnails use a static frame; detail views keep the animated image loader. */
@Composable
fun ExerciseThumbnail(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val request = remember(context, model) {
        ImageRequest.Builder(context)
            .data(model)
            .memoryCacheKey("exercise-thumbnail:$model")
            .decoderFactory { result, options, _ -> BitmapFactoryDecoder(result.source, options) }
            .build()
    }
    AsyncImage(request, contentDescription, modifier, contentScale = contentScale)
}
