package com.example.stockflow.ui.common

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.stockflow.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * Reusable full-screen image viewer for profile and business images.
 * Accepts a remote path/URL and/or a local content [Uri] for unsaved previews.
 */
class FullscreenImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_image)

        val root = findViewById<View>(R.id.fullscreenRoot)
        SystemBars.applyThemeAware(this, root)

        val imageView = findViewById<ImageView>(R.id.ivFullscreen)
        val progress = findViewById<ProgressBar>(R.id.progressFullscreen)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)

        toolbar.setNavigationOnClickListener { finish() }

        val localUri = intent.getStringExtra(EXTRA_IMAGE_URI)?.let { Uri.parse(it) }
        val remote = ProductImages.resolveUrl(intent.getStringExtra(EXTRA_IMAGE_URL))

        when {
            localUri != null -> {
                progress.visibility = View.GONE
                imageView.load(localUri) {
                    placeholder(R.drawable.bg_product_image_placeholder)
                    error(R.drawable.bg_product_image_placeholder)
                }
            }
            !remote.isNullOrBlank() -> {
                progress.visibility = View.VISIBLE
                imageView.load(remote) {
                    placeholder(R.drawable.bg_product_image_placeholder)
                    error(R.drawable.bg_product_image_placeholder)
                    listener(
                        onSuccess = { _, _ -> progress.visibility = View.GONE },
                        onError = { _, _ -> progress.visibility = View.GONE }
                    )
                }
            }
            else -> {
                progress.visibility = View.GONE
                imageView.setImageResource(R.drawable.bg_product_image_placeholder)
            }
        }
    }

    companion object {
        const val EXTRA_IMAGE_URL = "extra_image_url"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }
}
