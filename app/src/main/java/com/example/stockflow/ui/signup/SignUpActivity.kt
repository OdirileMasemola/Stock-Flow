package com.example.stockflow.ui.signup

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stockflow.MainActivity
import com.example.stockflow.R
import com.example.stockflow.data.auth.GoogleAccountResult
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
    private var roleDialog: AlertDialog? = null
    private var pendingRoleDialog = false
    private var navigatedToMain = false

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

    override fun onDestroy() {
        roleDialog?.dismiss()
        roleDialog = null
        super.onDestroy()
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
        lifecycleScope.launch {
            when (val resolved = googleAuthClient.resolveSignInResult(resultCode, data)) {
                is GoogleAccountResult.Success -> {
                    googleAuthClient.exchangeGoogleAccount(resolved.account).fold(
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
                }
                is GoogleAccountResult.Cancelled -> {
                    showLoading(false)
                    Toast.makeText(
                        this@SignUpActivity,
                        "Google Sign-In was cancelled.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is GoogleAccountResult.Error -> {
                    showLoading(false)
                    Toast.makeText(
                        this@SignUpActivity,
                        resolved.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
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
                    if (pendingRoleDialog) {
                        pendingRoleDialog = false
                        showRoleSelectionDialog()
                    }
                }
                is SignUpViewModel.RolesState.Error -> {
                    binding.dropdownRole.isEnabled = false
                    binding.btnSignUp.isEnabled = false
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    if (pendingRoleDialog) {
                        pendingRoleDialog = false
                        Toast.makeText(
                            this,
                            "Unable to load roles. Please try Google Sign-In again.",
                            Toast.LENGTH_SHORT
                        ).show()
                        viewModel.cancelPendingGoogleSignUp()
                    }
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
                is SignUpViewModel.GoogleSignUpState.Idle -> Unit
                is SignUpViewModel.GoogleSignUpState.Loading -> showLoading(true)
                is SignUpViewModel.GoogleSignUpState.NeedsRole -> {
                    showLoading(false)
                    viewModel.acknowledgeRolePrompt()
                    requestRoleSelection()
                }
                is SignUpViewModel.GoogleSignUpState.Success -> {
                    showLoading(false)
                    goToMainAfterGoogleSignUp()
                }
                is SignUpViewModel.GoogleSignUpState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestRoleSelection() {
        val availableRoles = roles.ifEmpty { viewModel.currentRoles() }
        if (availableRoles.isEmpty()) {
            pendingRoleDialog = true
            viewModel.loadRoles()
            Toast.makeText(this, "Loading roles…", Toast.LENGTH_SHORT).show()
            return
        }
        roles = availableRoles
        showRoleSelectionDialog()
    }

    private fun showRoleSelectionDialog() {
        if (isFinishing || isDestroyed) return
        if (roleDialog?.isShowing == true) return

        val currentRoles = roles.ifEmpty { viewModel.currentRoles() }
        if (currentRoles.isEmpty()) {
            pendingRoleDialog = true
            viewModel.loadRoles()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_choose_role, null)
        val optionsContainer = dialogView.findViewById<LinearLayout>(R.id.roleOptionsContainer)
        val continueButton = dialogView.findViewById<MaterialButton>(R.id.btnContinueRole)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.btnCancelRole)

        var selectedRoleId: Int? = null
        val radioButtons = mutableListOf<RadioButton>()

        currentRoles.forEach { role ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_role_choice, optionsContainer, false)
            val radio = row.findViewById<RadioButton>(R.id.roleRadio)
            val nameView = row.findViewById<TextView>(R.id.roleName)
            val descriptionView = row.findViewById<TextView>(R.id.roleDescription)

            nameView.text = role.name
            descriptionView.text = shortRoleDescription(role)
            radio.isClickable = false
            radio.isFocusable = false
            radioButtons.add(radio)

            row.setOnClickListener {
                selectedRoleId = role.id
                radioButtons.forEach { it.isChecked = false }
                radio.isChecked = true
                continueButton.isEnabled = true
            }

            optionsContainer.addView(row)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.setOnCancelListener {
            viewModel.cancelPendingGoogleSignUp()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
            viewModel.cancelPendingGoogleSignUp()
        }

        continueButton.setOnClickListener {
            val roleId = selectedRoleId
            if (roleId == null) return@setOnClickListener
            dialog.dismiss()
            viewModel.completeGoogleSignUp(roleId)
        }

        roleDialog = dialog
        try {
            dialog.show()
        } catch (_: Exception) {
            roleDialog = null
            viewModel.cancelPendingGoogleSignUp()
            Toast.makeText(this, "Unable to show role selection. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shortRoleDescription(role: RoleDto): String {
        return when (role.name.trim().lowercase()) {
            "owner" -> getString(R.string.role_desc_owner)
            "staff" -> getString(R.string.role_desc_staff)
            "supplier" -> getString(R.string.role_desc_supplier)
            else -> getString(R.string.role_desc_default)
        }
    }

    private fun goToMainAfterGoogleSignUp() {
        if (navigatedToMain || isFinishing || isDestroyed) return
        navigatedToMain = true
        roleDialog?.dismiss()
        roleDialog = null
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSignUp.isEnabled = !isLoading && roles.isNotEmpty()
        binding.dropdownRole.isEnabled = !isLoading && roles.isNotEmpty()
        binding.btnGoogle.isEnabled = !isLoading
    }
}
