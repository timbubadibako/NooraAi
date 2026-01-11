package com.example.nooraai.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nooraai.util.Prefs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AuthUiState(val loading: Boolean = false, val emailError: String? = null, val passwordError: String? = null)

/**
 * AuthViewModel: uses AuthRepository to perform auth actions.
 * AuthEvent is defined separately in auth/AuthEvent.kt
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AuthRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Local JSON parsing helpers (kept inside ViewModel so we don't depend on repo helpers)
    private fun parseUserIdFromJson(userJson: String?): String? {
        if (userJson.isNullOrBlank()) return null
        return try {
            val obj = Json.parseToJsonElement(userJson).jsonObject
            obj["id"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDisplayNameFromJson(userJson: String?): String? {
        if (userJson.isNullOrBlank()) return null
        return try {
            val obj = Json.parseToJsonElement(userJson).jsonObject

            // prefer raw_user_meta_data (Supabase default), fallback to user_metadata, then email
            val meta = when {
                obj["raw_user_meta_data"] != null -> obj["raw_user_meta_data"]?.jsonObject
                obj["user_metadata"] != null -> obj["user_metadata"]?.jsonObject
                else -> null
            }

            val fromMeta = meta?.get("display_name")?.jsonPrimitive?.contentOrNull
                ?: meta?.get("full_name")?.jsonPrimitive?.contentOrNull

            fromMeta ?: obj["email"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }

    fun login(email: String, password: String) {
        var hasError = false
        val emailErr = if (email.isBlank()) { hasError = true; "Email harus diisi" } else null
        val passErr = if (password.length < 6) { hasError = true; "Password minimal 6 karakter" } else null
        _uiState.value = _uiState.value.copy(emailError = emailErr, passwordError = passErr)
        if (hasError) return

        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val res = repo.login(email.trim(), password)
            _uiState.value = _uiState.value.copy(loading = false)
            when (res) {
                is AuthResult.Success -> {
                    // persist tokens & state
                    Prefs.saveLastEmail(getApplication(), email.trim())
                    Prefs.setLoggedIn(getApplication(), true)
                    Prefs.saveAccessToken(getApplication(), res.accessToken)
                    Prefs.saveRefreshToken(getApplication(), res.refreshToken)

                    // Sync users_meta (best-effort)
                    val access = res.accessToken
                    var userJson = res.userJson
                    var userId = parseUserIdFromJson(userJson)
                    if (userId.isNullOrBlank() && !access.isNullOrBlank()) {
                        userJson = repo.getCurrentUserJson(access)
                        userId = parseUserIdFromJson(userJson)
                    }

                    if (!userId.isNullOrBlank() && !access.isNullOrBlank()) {
                        val meta = repo.getUserMeta(userId, access)
                        if (meta == null) {
                            val displayName = parseDisplayNameFromJson(userJson) ?: email.trim()
                            val payload = mapOf("display_name" to displayName, "locale" to "id")
                            repo.createUserMeta(userId, payload, access)
                        }
                    }

                    _events.send(AuthEvent.LoginSuccess("Login berhasil"))
                }
                is AuthResult.Failure -> {
                    _events.send(AuthEvent.LoginFailed(res.message))
                }
            }
        }
    }

    /**
     * Register with optional fullName provided by UI.
     * If Supabase returns session/access token, ViewModel will attempt to create/update users_meta (best-effort).
     */
    fun register(email: String, password: String, fullName: String? = null) {
        var hasError = false
        val emailErr = if (email.isBlank()) { hasError = true; "Email harus diisi" } else null
        val passErr = if (password.length < 6) { hasError = true; "Password minimal 6 karakter" } else null
        _uiState.value = _uiState.value.copy(emailError = emailErr, passwordError = passErr)
        if (hasError) return

        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val res = repo.register(email.trim(), password)
            _uiState.value = _uiState.value.copy(loading = false)
            when (res) {
                is AuthResult.Success -> {
                    val access = res.accessToken
                    val refresh = res.refreshToken

                    // persist last email & tokens if present
                    Prefs.saveLastEmail(getApplication(), email.trim())
                    Prefs.saveAccessToken(getApplication(), access)
                    Prefs.saveRefreshToken(getApplication(), refresh)

                    // Try to obtain userId from response or /auth/v1/user
                    var userJson = res.userJson
                    var userId = parseUserIdFromJson(userJson)
                    if (userId.isNullOrBlank() && !access.isNullOrBlank()) {
                        userJson = repo.getCurrentUserJson(access)
                        userId = parseUserIdFromJson(userJson)
                    }

                    // Best-effort create/update users_meta using fullName if provided
                    if (!userId.isNullOrBlank() && !access.isNullOrBlank()) {
                        val existing = repo.getUserMeta(userId, access)
                        val displayName = fullName?.takeIf { it.isNotBlank() }
                            ?: parseDisplayNameFromJson(userJson)
                            ?: email.trim()

                        if (existing == null) {
                            val payload = mapOf("display_name" to displayName, "locale" to "id")
                            repo.createUserMeta(userId, payload, access)
                        } else {
                            // update if missing display name (best-effort)
                            if (existing.displayName.isNullOrBlank() && displayName.isNotBlank()) {
                                repo.updateUserMeta(userId, mapOf("display_name" to displayName), access)
                            }
                        }
                    } else {
                        // userId or access token not available (likely email confirmation required)
                        // registration still considered successful; user must confirm email before full profile actions
                    }

                    _events.send(AuthEvent.RegisterSuccess("Pendaftaran berhasil. Periksa email jika perlu konfirmasi."))
                }
                is AuthResult.Failure -> {
                    _events.send(AuthEvent.RegisterFailed(res.message))
                }
            }
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(emailError = "Email harus diisi")
            return
        }
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val res = repo.sendResetPasswordEmail(email.trim())
            _uiState.value = _uiState.value.copy(loading = false)
            when (res) {
                is AuthResult.Success -> _events.send(AuthEvent.ForgotPasswordSent("Email reset dikirim"))
                is AuthResult.Failure -> _events.send(AuthEvent.RegisterFailed(res.message))
            }
        }
    }
}