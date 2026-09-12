package com.example.stockflow.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.stockflow.R
import com.example.stockflow.data.ProductSkuCodes
import com.example.stockflow.databinding.ActivityBarcodeScannerBinding
import com.example.stockflow.ui.common.SystemBars
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live camera barcode/QR scanner used by Add Product, Inventory, and POS.
 * Returns [EXTRA_SCAN_VALUE] on success.
 */
class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBarcodeScannerBinding
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val handled = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            onPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarcodeScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        binding.btnClose.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        ensureCameraPermission()
    }

    private fun ensureCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> startCamera()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.scan_camera_permission_title)
                    .setMessage(R.string.scan_camera_permission_rationale)
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                    .setPositiveButton(R.string.scan_allow_camera) { _, _ ->
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .show()
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun onPermissionDenied() {
        if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.scan_camera_permission_title)
                .setMessage(R.string.scan_camera_permission_settings)
                .setNegativeButton(R.string.cancel) { _, _ ->
                    setResult(RESULT_CANCELED)
                    finish()
                }
                .setPositiveButton(R.string.scan_open_settings) { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                    setResult(RESULT_CANCELED)
                    finish()
                }
                .show()
        } else {
            Toast.makeText(this, R.string.scan_camera_permission_denied, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                bindCamera(provider)
                Log.d(TAG, "scanner opened")
            } catch (e: Exception) {
                Log.w(TAG, "scanner initialization failure", e)
                showError(getString(R.string.scan_camera_unavailable))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun bindCamera(provider: ProcessCameraProvider) {
        provider.unbindAll()

        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = binding.previewView.surfaceProvider }

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE
            )
            .build()
        val scanner = BarcodeScanning.getClient(options)

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            if (handled.get()) {
                imageProxy.close()
                return@setAnalyzer
            }
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return@setAnalyzer
            }
            val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(input)
                .addOnSuccessListener { barcodes ->
                    val chosen = barcodes
                        .mapNotNull { barcode ->
                            val raw = barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
                                ?: return@mapNotNull null
                            barcode to raw
                        }
                        .minByOrNull { (barcode, _) -> formatPriority(barcode.format) }
                        ?: return@addOnSuccessListener

                    val (barcode, raw) = chosen
                    val sku = ProductSkuCodes.toStockFlowSku(raw)
                    if (sku == null) {
                        // Keep scanning; URL/QR marketing codes are not StockFlow SKUs.
                        Log.d(TAG, "scan ignored format=${barcode.format} len=${raw.length}")
                        return@addOnSuccessListener
                    }
                    if (handled.compareAndSet(false, true)) {
                        Log.d(
                            TAG,
                            "scan accepted format=${barcode.format} rawLen=${raw.length} skuLen=${sku.length} " +
                                "upcCanonicalized=${raw.length == 12 && sku.length == 13}"
                        )
                        runOnUiThread { finishWithValue(sku) }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "scan process failure", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }

        try {
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (e: Exception) {
            Log.w(TAG, "camera bind failure", e)
            showError(getString(R.string.scan_camera_unavailable))
        }
    }

    private fun finishWithValue(value: String) {
        cameraProvider?.unbindAll()
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_SCAN_VALUE, value)
        )
        finish()
    }

    private fun showError(message: String) {
        binding.tvScanError.visibility = View.VISIBLE
        binding.tvScanError.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        analysisExecutor.shutdown()
        cameraProvider?.unbindAll()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SCAN_VALUE = "extra_scan_value"
        private const val TAG = "BarcodeScanner"

        private fun formatPriority(format: Int): Int = when (format) {
            Barcode.FORMAT_EAN_13 -> 0
            Barcode.FORMAT_EAN_8 -> 1
            Barcode.FORMAT_UPC_A -> 2
            Barcode.FORMAT_UPC_E -> 3
            Barcode.FORMAT_CODE_128 -> 4
            Barcode.FORMAT_CODE_39 -> 5
            Barcode.FORMAT_QR_CODE -> 6
            else -> 100
        }
    }
}
