package com.example.stockflow.ui.login

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.R
import com.example.stockflow.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
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
            Toast.makeText(this, "Create Account clicked", Toast.LENGTH_SHORT).show()
        }

        binding.btnGoogle.setOnClickListener {
            Toast.makeText(this, "Google Sign-In clicked", Toast.LENGTH_SHORT).show()
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
                    // Navigate to Dashboard (TBD)
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
    }
}
