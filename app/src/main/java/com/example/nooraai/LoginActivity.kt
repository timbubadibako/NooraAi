package com.example.nooraai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nooraai.auth.AuthEvent
import com.example.nooraai.auth.AuthUiState
import com.example.nooraai.auth.AuthViewModel
import com.example.nooraai.databinding.ActivityLoginBinding
import com.example.nooraai.util.Prefs
import kotlinx.coroutines.flow.collectLatest

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    companion object { private const val TAG = "LoginActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etEmail.setText(Prefs.getLastEmail(this) ?: "")

        binding.btnLogin.setOnClickListener {
            viewModel.login(binding.etEmail.text?.toString() ?: "", binding.etPassword.text?.toString() ?: "")
        }
        binding.tvRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }

        binding.btnTogglePassword.setOnClickListener {
            val edit = binding.etPassword
            val isVisible = edit.transformationMethod is android.text.method.HideReturnsTransformationMethod
            if (isVisible) {
                edit.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                binding.btnTogglePassword.setImageResource(R.drawable.eye)
            } else {
                edit.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                binding.btnTogglePassword.setImageResource(R.drawable.eye_off)
            }
            edit.setSelection(edit.text?.length ?: 0)
        }

        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collectLatest { state: AuthUiState ->
                binding.btnLogin.isEnabled = !state.loading
                binding.etEmail.error = state.emailError
                binding.etPassword.error = state.passwordError
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.events.collectLatest { event: AuthEvent ->
                when (event) {
                    is AuthEvent.LoginSuccess -> {
                        Log.d(TAG, "Login success: ${event.message}")
                        Toast.makeText(this@LoginActivity, event.message, Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra(MainActivity.EXTRA_AUTH_RESULT, "login_success")
                        })
                        finish()
                    }
                    is AuthEvent.LoginFailed -> {
                        Log.d(TAG, "Login failed: ${event.message}")
                        binding.etPassword.error = event.message
                        Toast.makeText(this@LoginActivity, event.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }
}