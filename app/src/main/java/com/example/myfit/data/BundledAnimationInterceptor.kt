package com.example.myfit.data

import android.net.Uri
import coil.intercept.Interceptor
import coil.request.ImageResult

/** Also handles historical snapshot images that bypass CatalogMedia helpers. */
class BundledAnimationInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is String && data !is Uri) return chain.proceed(chain.request)
        val original = data.toString()
        val mapped = CatalogMedia.bundledUri(original)
        return chain.proceed(if (mapped == original) chain.request else chain.request.newBuilder().data(mapped).build())
    }
}
