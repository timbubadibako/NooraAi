package com.example.nooraai.auth

/**
 * Generic result for auth operations.
 */
sealed class AuthResult {
    data class Success(val accessToken: String?, val refreshToken: String?, val userJson: String? = null) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}