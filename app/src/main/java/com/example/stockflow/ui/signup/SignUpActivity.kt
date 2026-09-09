package com.example.stockflow.ui.signup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stockflow.MainActivity
import com.example.stockflow.R
import com.example.stockflow.data.auth.GoogleAuthClient
import com.example.stockflow.data.remote.RoleDto
import com.example.stockflow.databinding.ActivitySignupBinding
import com.example.stockflow.ui.common.SystemBars
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlin.math.abs

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var googleAuthClient: GoogleAuthClient
    private val viewModel: SignUpViewModel by viewModels()

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false
    private var roles: List<RoleDto> = emptyList()
    private var selectedRole: RoleDto? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this, binding.root)
        googleAuthClient = GoogleAuthClient(this)

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
        binding.dropdownRole.setOnClickListener {
            binding.dropdownRole.showDropDown()
        }

        binding.dropdownRole.setOnItemClickListener { _, _, position, _ ->
            selectedRole = roles.getOrNull(position)
        }

        binding.btnSignUp.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()

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
                googleAuthClient.clearLastGoogleAccount()
                googleSignInLauncher.launch(googleAuthClient.getSignInIntent())
            }
        }
    }

    private fun handleGoogleSignInResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            showLoading(false)
            if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Google Sign-In was cancelled.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        lifecycleScope.launch {
            val accountResult = googleAuthClient.parseSignInIntent(data)
            accountResult.fold(
                onSuccess = { account ->
                    googleAuthClient.exchangeGoogleAccount(account).fold(
                        onSuccess = { idToken -> viewModel.continueGoogleSignUp(idToken) },
                        onFailure = { error ->
                            showLoading(false)
                            Toast.makeText(
                                this@SignUpActivity,
                                error.message ?: "Unable to complete Google Sign-In. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                },
                onFailure = { error ->
                    showLoading(false)
                    Toast.makeText(
                        this@SignUpActivity,
                        error.message ?: "Unable to complete Google Sign-In. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
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
                    binding.dropdownRole.isEnabled = false
                    binding.btnSignUp.isEnabled = false
                }
                is SignUpViewModel.RolesState.Success -> {
                    roles = state.roles
                    selectedRole = null
                    binding.dropdownRole.setText("", false)
                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        roles.map { it.name }
                    )
                    binding.dropdownRole.setAdapter(adapter)
                    binding.dropdownRole.isEnabled = true
                    binding.btnSignUp.isEnabled = true
                }
                is SignUpViewModel.RolesState.Error -> {
                    binding.dropdownRole.isEnabled = false
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
        binding.dropdownRole.isEnabled = !isLoading && roles.isNotEmpty()
        binding.btnGoogle.isEnabled = !isLoading
    }
}
