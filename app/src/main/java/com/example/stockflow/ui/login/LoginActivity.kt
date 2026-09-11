package com.example.stockflow.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stockflow.MainActivity
import com.example.stockflow.data.auth.GoogleAccountResult
import com.example.stockflow.data.auth.GoogleAuthClient
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.databinding.ActivityLoginBinding
import com.example.stockflow.ui.common.SystemBars
import com.example.stockflow.ui.signup.SignUpActivity
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.launch
import kotlin.math.abs

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleAuthClient: GoogleAuthClient
    private val viewModel: LoginViewModel by viewModels()
    private var isPasswordVisible = false

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If a session already exists, skip login and open the dashboard.
        if (SessionStore(this).hasValidSession()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
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
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.login(email, password)
        }

        binding.ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }

        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Forgot Password clicked", Toast.LENGTH_SHORT).show()
        }

        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        binding.btnGoogle.setOnClickListener {
            lifecycleScope.launch {
                showLoading(true)
                // Force account picker, then use the classic Google Sign-In intent
                // (Credential Manager was reporting cancel after account selection on device).
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
                        onSuccess = { idToken -> viewModel.loginWithGoogle(idToken) },
                        onFailure = { error ->
                            showLoading(false)
                            Toast.makeText(
                                this@LoginActivity,
                                error.message ?: "Unable to complete Google Sign-In. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
                is GoogleAccountResult.Cancelled -> {
                    showLoading(false)
                    Toast.makeText(
                        this@LoginActivity,
                        "Google Sign-In was cancelled.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is GoogleAccountResult.Error -> {
                    showLoading(false)
                    Toast.makeText(
                        this@LoginActivity,
                        resolved.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        if (isPasswordVisible) {
            binding.etPassword.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            binding.etPassword.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.etPassword.setSelection(binding.etPassword.text.length)
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginViewModel.LoginState.Loading -> showLoading(true)
                is LoginViewModel.LoginState.Success -> {
                    showLoading(false)
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                is LoginViewModel.LoginState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.btnGoogle.isEnabled = !isLoading
    }
}
