package com.example.stockflow.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stockflow.databinding.ActivityLoginBinding
import com.example.stockflow.MainActivity
import com.example.stockflow.data.auth.GoogleAuthClient
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.ui.common.SystemBars
import com.example.stockflow.ui.signup.SignUpActivity
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.launch
import kotlin.math.abs

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    private var isPasswordVisible = false

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

        setupListeners()
        setupHeaderAnimation()
        observeViewModel()
    }

    private fun setupHeaderAnimation() {
        binding.appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            if (totalScrollRange == 0) return@OnOffsetChangedListener
            
            val percentage = abs(verticalOffset).toFloat() / totalScrollRange.toFloat()
            
            // Fade out tagline
            binding.tagline.alpha = 1f - (percentage * 2f).coerceIn(0f, 1f)
            
            // Scale down logo
            val scale = 1f - (percentage * 0.4f).coerceIn(0f, 0.4f)
            binding.logoImage.scaleX = scale
            binding.logoImage.scaleY = scale
            
            // Move logo/text up slightly if needed, but parallax handles most of it
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
                val result = GoogleAuthClient(this@LoginActivity).signInWithGoogle()
                result.fold(
                    onSuccess = { idToken -> viewModel.loginWithGoogle(idToken) },
                    onFailure = { error ->
                        showLoading(false)
                        Toast.makeText(
                            this@LoginActivity,
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
        if (isPasswordVisible) {
            binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.etPassword.setSelection(binding.etPassword.text.length)
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginViewModel.LoginState.Loading -> {
                    showLoading(true)
                }
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
