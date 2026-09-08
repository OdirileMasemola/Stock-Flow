package com.example.stockflow.ui.signup

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stockflow.MainActivity
import com.example.stockflow.R
import com.example.stockflow.data.auth.GoogleAuthClient
import com.example.stockflow.data.remote.RoleDto
import com.example.stockflow.databinding.ActivitySignupBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
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
            lifecycleScope.launch {
                showLoading(true)
                val result = GoogleAuthClient(this@SignUpActivity).signInWithGoogle()
                result.fold(
                    onSuccess = { idToken -> viewModel.continueGoogleSignUp(idToken) },
                    onFailure = { error ->
                        showLoading(false)
                        Toast.makeText(
                            this@SignUpActivity,
                            error.message ?: "Unable to complete Google Sign-In. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
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

        viewModel.googleSignUpState.observe(this) { state ->
            when (state) {
                is SignUpViewModel.GoogleSignUpState.Loading -> showLoading(true)
                is SignUpViewModel.GoogleSignUpState.NeedsRole -> {
                    showLoading(false)
                    showRoleSelectionDialog()
                }
                is SignUpViewModel.GoogleSignUpState.Success -> {
                    showLoading(false)
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                is SignUpViewModel.GoogleSignUpState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showRoleSelectionDialog() {
        val currentRoles = roles
        if (currentRoles.isEmpty()) {
            Toast.makeText(this, "Unable to load roles. Please try again.", Toast.LENGTH_SHORT).show()
            viewModel.cancelPendingGoogleSignUp()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_choose_role, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.roleRadioGroup)
        val continueButton = dialogView.findViewById<MaterialButton>(R.id.btnContinueRole)

        currentRoles.forEach { role ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = role.name
                tag = role.id
                setTextColor(getColor(R.color.brand_text_dark))
                textSize = 16f
            }
            radioGroup.addView(button)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.cancelPendingGoogleSignUp()
            }
            .setCancelable(false)
            .create()

        continueButton.setOnClickListener {
            val selected = radioGroup.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
            val roleId = selected?.tag as? Int
            if (roleId == null) {
                return@setOnClickListener
            }
            dialog.dismiss()
            viewModel.completeGoogleSignUp(roleId)
        }

        radioGroup.setOnCheckedChangeListener { _, _ ->
            continueButton.isEnabled = radioGroup.checkedRadioButtonId != -1
        }

        dialog.show()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSignUp.isEnabled = !isLoading && roles.isNotEmpty()
        binding.spinnerRole.isEnabled = !isLoading && roles.isNotEmpty()
        binding.btnGoogle.isEnabled = !isLoading
    }
}
