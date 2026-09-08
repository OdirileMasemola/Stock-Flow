package com.example.stockflow.ui.signup

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.R
import com.example.stockflow.data.remote.RoleDto
import com.example.stockflow.databinding.ActivitySignupBinding
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.abs

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val viewModel: SignUpViewModel by viewModels()

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false
    private var roles: List<RoleDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupHeaderAnimation()
        observeViewModel()
    }

    private fun setupHeaderAnimation() {
        binding.appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            if (totalScrollRange == 0) return@OnOffsetChangedListener

            val percentage = abs(verticalOffset).toFloat() / totalScrollRange.toFloat()

            binding.tagline.alpha = 1f - (percentage * 2f).coerceIn(0f, 1f)

            val scale = 1f - (percentage * 0.4f).coerceIn(0f, 0.4f)
            binding.logoImage.scaleX = scale
            binding.logoImage.scaleY = scale
        })
    }

    private fun setupListeners() {
        binding.btnSignUp.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()
            val selectedRole = binding.spinnerRole.selectedItem as? RoleDto

            viewModel.signUp(name, phone, email, password, confirmPass, selectedRole?.id)
        }

        binding.ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }

        binding.ivConfirmPasswordToggle.setOnClickListener {
            toggleConfirmPasswordVisibility()
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }

        binding.btnGoogle.setOnClickListener {
            Toast.makeText(this, "Google Sign-In clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        binding.etPassword.inputType = if (isPasswordVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.etPassword.setSelection(binding.etPassword.text.length)
    }

    private fun toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible
        binding.etConfirmPassword.inputType = if (isConfirmPasswordVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.etConfirmPassword.setSelection(binding.etConfirmPassword.text.length)
    }

    private fun observeViewModel() {
        viewModel.rolesState.observe(this) { state ->
            when (state) {
                is SignUpViewModel.RolesState.Loading -> {
                    binding.spinnerRole.isEnabled = false
                    binding.btnSignUp.isEnabled = false
                }
                is SignUpViewModel.RolesState.Success -> {
                    roles = state.roles
                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        roles
                    )
                    binding.spinnerRole.adapter = adapter
                    binding.spinnerRole.isEnabled = true
                    binding.btnSignUp.isEnabled = true
                }
                is SignUpViewModel.RolesState.Error -> {
                    binding.spinnerRole.isEnabled = false
                    binding.btnSignUp.isEnabled = false
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.signUpState.observe(this) { state ->
            when (state) {
                is SignUpViewModel.SignUpState.Loading -> showLoading(true)
                is SignUpViewModel.SignUpState.Success -> {
                    showLoading(false)
                    Toast.makeText(this, "Account Created Successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is SignUpViewModel.SignUpState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSignUp.isEnabled = !isLoading && roles.isNotEmpty()
        binding.spinnerRole.isEnabled = !isLoading && roles.isNotEmpty()
    }
}
