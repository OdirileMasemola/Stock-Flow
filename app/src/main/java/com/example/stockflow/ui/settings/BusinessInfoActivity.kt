package com.example.stockflow.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.stockflow.R
import com.example.stockflow.data.remote.BusinessDto
import com.example.stockflow.databinding.ActivityBusinessInfoBinding
import com.example.stockflow.ui.common.FullscreenImageActivity
import com.example.stockflow.ui.common.ProductImages
import com.example.stockflow.ui.common.SystemBars
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import java.util.Locale

class BusinessInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessInfoBinding
    private val viewModel: BusinessInfoViewModel by viewModels()

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var locationCallback: LocationCallback? = null

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setPendingImage(uri)
            refreshStoreImage()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentLocation()
        } else {
            showError(getString(R.string.location_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyThemeAware(this, binding.businessRoot)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnChangeStoreImage.setOnClickListener {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.btnRemoveStoreImage.setOnClickListener {
            viewModel.clearImage()
            refreshStoreImage()
        }
        binding.ivStoreImage.setOnClickListener { openFullscreenIfPossible() }
        binding.btnUseCurrentLocation.setOnClickListener { requestLocationOrFetch() }
        binding.btnSave.setOnClickListener {
            viewModel.saveBusiness(
                storeName = binding.etStoreName.text.toString(),
                ownerName = binding.etOwnerName.text.toString(),
                phone = binding.etPhone.text.toString(),
                email = binding.etEmail.text.toString(),
                address = binding.etAddress.text.toString()
            )
        }

        viewModel.business.observe(this) { business ->
            business?.let {
                bindBusiness(it)
                refreshStoreImage()
                refreshLocationLabel()
            }
        }
        viewModel.pendingImageUri.observe(this) { refreshStoreImage() }
        viewModel.pendingLatitude.observe(this) { refreshLocationLabel() }
        viewModel.pendingLongitude.observe(this) { refreshLocationLabel() }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is BusinessInfoViewModel.UiState.Idle -> setLoading(false)
                is BusinessInfoViewModel.UiState.Loading -> setLoading(true)
                is BusinessInfoViewModel.UiState.Success -> {
                    setLoading(false)
                    Toast.makeText(this, R.string.business_updated, Toast.LENGTH_SHORT).show()
                }
                is BusinessInfoViewModel.UiState.Error -> {
                    setLoading(false)
                    showError(state.message)
                }
            }
        }

        viewModel.loadBusiness()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    private fun requestLocationOrFetch() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchCurrentLocation() {
        setLoading(true)
        binding.tvFormError.visibility = View.GONE

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setWaitForAccurateLocation(false)
            .setMaxUpdates(1)
            .setDurationMillis(15000L)
            .build()

        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .build()

        LocationServices.getSettingsClient(this)
            .checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                startLocationUpdates(request)
            }
            .addOnFailureListener { error ->
                setLoading(false)
                if (error is ResolvableApiException) {
                    try {
                        error.startResolutionForResult(this, REQ_LOCATION_SETTINGS)
                    } catch (_: Exception) {
                        showError(getString(R.string.location_services_disabled))
                    }
                } else {
                    showError(getString(R.string.location_services_disabled))
                }
            }
    }

    private var awaitingLocation = false

    private fun startLocationUpdates(request: LocationRequest) {
        stopLocationUpdates()
        awaitingLocation = true
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!awaitingLocation) return
                awaitingLocation = false
                stopLocationUpdates()
                val location = result.lastLocation
                setLoading(false)
                if (location == null) {
                    showError(getString(R.string.location_unavailable))
                    return
                }
                viewModel.setCurrentLocation(location.latitude, location.longitude)
                refreshLocationLabel()
                Toast.makeText(this@BusinessInfoActivity, R.string.location_captured, Toast.LENGTH_SHORT).show()
            }
        }
        locationCallback = callback
        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            awaitingLocation = false
            setLoading(false)
            showError(getString(R.string.location_permission_denied))
            return
        }

        binding.root.postDelayed({
            if (awaitingLocation && locationCallback === callback) {
                awaitingLocation = false
                stopLocationUpdates()
                setLoading(false)
                showError(getString(R.string.location_timeout))
            }
        }, 16000L)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun bindBusiness(business: BusinessDto) {
        binding.etStoreName.setText(business.storeName.orEmpty())
        binding.etOwnerName.setText(business.ownerName.orEmpty())
        binding.etPhone.setText(business.phone.orEmpty())
        binding.etEmail.setText(business.email.orEmpty())
        binding.etAddress.setText(business.address.orEmpty())
    }

    private fun refreshStoreImage() {
        ProductImages.loadUriOrRemote(
            imageView = binding.ivStoreImage,
            localUri = viewModel.pendingImageUri.value,
            remoteUrl = viewModel.currentDisplayImageUrl()
        )
    }

    private fun refreshLocationLabel() {
        val lat = viewModel.pendingLatitude.value
        val lng = viewModel.pendingLongitude.value
        binding.tvLocationValue.text = if (lat != null && lng != null) {
            String.format(Locale.US, "%.6f, %.6f", lat, lng)
        } else {
            getString(R.string.location_not_set)
        }
    }

    private fun openFullscreenIfPossible() {
        val local = viewModel.pendingImageUri.value
        val remote = viewModel.currentDisplayImageUrl()
        when {
            local != null -> {
                startActivity(
                    Intent(this, FullscreenImageActivity::class.java).putExtra(
                        FullscreenImageActivity.EXTRA_IMAGE_URI,
                        local.toString()
                    )
                )
            }
            !remote.isNullOrBlank() -> {
                startActivity(
                    Intent(this, FullscreenImageActivity::class.java).putExtra(
                        FullscreenImageActivity.EXTRA_IMAGE_URL,
                        remote
                    )
                )
            }
            else -> Toast.makeText(this, R.string.no_image_to_view, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        binding.tvFormError.visibility = View.VISIBLE
        binding.tvFormError.text = message
    }

    private fun setLoading(loading: Boolean) {
        binding.progressSaving.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
        binding.btnUseCurrentLocation.isEnabled = !loading
        binding.btnChangeStoreImage.isEnabled = !loading
        binding.btnRemoveStoreImage.isEnabled = !loading
        binding.etStoreName.isEnabled = !loading
        binding.etOwnerName.isEnabled = !loading
        binding.etPhone.isEnabled = !loading
        binding.etEmail.isEnabled = !loading
        binding.etAddress.isEnabled = !loading
        if (loading) {
            binding.tvFormError.visibility = View.GONE
        }
    }

    companion object {
        private const val REQ_LOCATION_SETTINGS = 4401
    }
}
