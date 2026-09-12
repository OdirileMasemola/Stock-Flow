package com.example.stockflow.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import coil.load
import coil.request.CachePolicy
import coil.size.Size
import com.example.stockflow.BuildConfig
import com.example.stockflow.R
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Resolves image paths from the API into absolute URLs Coil can load.
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

    fun loadInto(
        imageView: ImageView,
        imageUrl: String?,
        placeholderRes: Int = R.drawable.bg_product_image_placeholder,
        targetSizePx: Int? = null
    ) {
        val url = resolveUrl(imageUrl)
        imageView.load(url) {
            placeholder(placeholderRes)
            error(placeholderRes)
            fallback(placeholderRes)
            crossfade(true)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            if (targetSizePx != null && targetSizePx > 0) {
                size(Size(targetSizePx, targetSizePx))
            }
        }
    }

    fun loadUriOrRemote(
        imageView: ImageView,
        localUri: Uri?,
        remoteUrl: String?,
        placeholderRes: Int = R.drawable.bg_product_image_placeholder
    ) {
        when {
            localUri != null -> imageView.load(localUri) {
                placeholder(placeholderRes)
                error(placeholderRes)
                fallback(placeholderRes)
            }
            else -> loadInto(imageView, remoteUrl, placeholderRes)
        }
    }

    /**
     * Reads and compresses an image for upload (max edge 1280px, JPEG quality 85).
     */
    fun readCompressedImageBytes(context: Context, uri: Uri, maxBytes: Int = 5 * 1024 * 1024): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        val maxDim = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (maxDim / sample > 1280) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Unable to read the selected image")

        val stream = ByteArrayOutputStream()
        var quality = 85
        do {
            stream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            quality -= 10
        } while (stream.size() > maxBytes && quality >= 40)

        bitmap.recycle()
        val bytes = stream.toByteArray()
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("Selected image is empty")
        }
        if (bytes.size > maxBytes) {
            throw IllegalArgumentException("Image must be 5 MB or smaller")
        }
        return bytes
    }
}
