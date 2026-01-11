package com.example.nooraai.auth

/**
 * One-shot events emitted by AuthViewModel for the UI to react (Login success/failure etc).
 */
sealed class AuthEvent {
    data class LoginSuccess(val message: String = "Login berhasil") : AuthEvent()
    data class LoginFailed(val message: String) : AuthEvent()
    data class RegisterSuccess(val message: String = "Register berhasil") : AuthEvent()
    data class RegisterFailed(val message: String) : AuthEvent()
    data class ForgotPasswordSent(val message: String = "Email reset dikirim") : AuthEvent()
}