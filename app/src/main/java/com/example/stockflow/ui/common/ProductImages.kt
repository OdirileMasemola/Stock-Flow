package com.example.stockflow.ui.common

import android.widget.ImageView
import coil.load
import coil.request.CachePolicy
import com.example.stockflow.BuildConfig
import com.example.stockflow.R

/**
 * Resolves product image paths from the API into absolute URLs Coil can load.
 * Backend stores relative paths such as `/uploads/products/….jpg`.
 */
object ProductImages {

    fun resolveUrl(imageUrl: String?): String? {
        val raw = imageUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/${raw.trimStart('/')}"
    }

    fun loadInto(imageView: ImageView, imageUrl: String?) {
        val url = resolveUrl(imageUrl)
        imageView.load(url) {
            placeholder(R.drawable.bg_product_image_placeholder)
            error(R.drawable.bg_product_image_placeholder)
            fallback(R.drawable.bg_product_image_placeholder)
            crossfade(true)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
        }
    }
}
