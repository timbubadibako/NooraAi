package com.example.nooraai

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nooraai.auth.AuthEvent
import com.example.nooraai.auth.AuthUiState
import com.example.nooraai.auth.AuthViewModel
import com.example.nooraai.databinding.ActivityRegisterBinding
import kotlinx.coroutines.flow.collectLatest

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text?.toString()?.trim()
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            viewModel.register(email, password, fullName)
        }

        binding.tvSignIn.setOnClickListener {
            // go back to login
            finish()
        }

        binding.btnTogglePassword.setOnClickListener {
            val edit = binding.etPassword
            val isVisible = edit.transformationMethod is HideReturnsTransformationMethod
            if (isVisible) {
                edit.transformationMethod = PasswordTransformationMethod.getInstance()
                binding.btnTogglePassword.setImageResource(R.drawable.eye)
            } else {
                edit.transformationMethod = HideReturnsTransformationMethod.getInstance()
                binding.btnTogglePassword.setImageResource(R.drawable.eye_off)
            }
            edit.setSelection(edit.text?.length ?: 0)
        }

        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collectLatest { state: AuthUiState ->
                binding.btnRegister.isEnabled = !state.loading
                binding.progressBar.visibility = if (state.loading) android.view.View.VISIBLE else android.view.View.GONE
                binding.etEmail.error = state.emailError
                binding.etPassword.error = state.passwordError
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.events.collectLatest { event: AuthEvent ->
                when (event) {
                    is AuthEvent.RegisterSuccess -> {
                        Toast.makeText(this@RegisterActivity, event.message, Toast.LENGTH_SHORT).show()
                        // Option: go to LoginActivity or main flow depending on your signup policy
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    }
                    is AuthEvent.RegisterFailed -> {
                        Toast.makeText(this@RegisterActivity, event.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }
}