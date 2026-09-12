package com.example.stockflow.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.R
import com.example.stockflow.data.remote.ProfileDto
import com.example.stockflow.databinding.ActivityProfileBinding
import com.example.stockflow.ui.common.FullscreenImageActivity
import com.example.stockflow.ui.common.ProductImages
import com.example.stockflow.ui.common.SystemBars

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ProfileViewModel by viewModels()

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setPendingImage(uri)
            refreshPhoto()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyThemeAware(this, binding.profileRoot)

        binding.ivProfilePhoto.clipToOutline = true
        binding.ivProfilePhoto.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnChangePhoto.setOnClickListener {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.btnRemovePhoto.setOnClickListener {
            viewModel.clearImage()
            refreshPhoto()
        }
        binding.ivProfilePhoto.setOnClickListener { openFullscreenIfPossible() }
        binding.btnSave.setOnClickListener {
            viewModel.saveProfile(binding.etFullName.text.toString())
        }

        viewModel.profile.observe(this) { profile ->
            profile?.let {
                bindProfile(it)
                refreshPhoto()
            }
        }
        viewModel.pendingImageUri.observe(this) { refreshPhoto() }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is ProfileViewModel.UiState.Idle -> setLoading(false)
                is ProfileViewModel.UiState.Loading -> setLoading(true)
                is ProfileViewModel.UiState.Success -> {
                    setLoading(false)
                    Toast.makeText(this, R.string.profile_updated, Toast.LENGTH_SHORT).show()
                }
                is ProfileViewModel.UiState.Error -> {
                    setLoading(false)
                    binding.tvFormError.visibility = View.VISIBLE
                    binding.tvFormError.text = state.message
                }
            }
        }

        viewModel.loadProfile()
    }

    private fun bindProfile(profile: ProfileDto) {
        binding.etFullName.setText(profile.fullName)
        binding.etEmail.setText(profile.email)
    }

    private fun refreshPhoto() {
        ProductImages.loadUriOrRemote(
            imageView = binding.ivProfilePhoto,
            localUri = viewModel.pendingImageUri.value,
            remoteUrl = viewModel.currentDisplayImageUrl(),
            placeholderRes = R.drawable.ic_user
        )
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

    private fun setLoading(loading: Boolean) {
        binding.progressSaving.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
        binding.btnChangePhoto.isEnabled = !loading
        binding.btnRemovePhoto.isEnabled = !loading
        binding.etFullName.isEnabled = !loading
        if (loading) {
            binding.tvFormError.visibility = View.GONE
        }
    }
}
