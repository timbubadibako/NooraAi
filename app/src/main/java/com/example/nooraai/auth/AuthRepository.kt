package com.example.nooraai.auth

import android.content.Context
import android.util.Log
import com.example.nooraai.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AuthRepository using Supabase Gotrue REST endpoints via Ktor.
 * Extended with PostgREST helpers for users_meta and small JSON parsing helpers.
 */
class AuthRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val http = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val restBase = "$baseUrl/rest/v1"

    // -------------------
    // Auth (Gotrue) calls
    // -------------------
    suspend fun login(email: String, password: String): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/auth/v1/token?grant_type=password"
                val resp: HttpResponse = http.post(url) {
                    contentType(ContentType.Application.Json)
                    header("apikey", anonKey)
                    header("Authorization", "Bearer $anonKey")
                    setBody(mapOf("email" to email, "password" to password))
                }
                val bodyText = resp.bodyAsText()
                val jsonElem = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
                val access = jsonElem?.get("access_token")?.jsonPrimitive?.contentOrNull
                val refresh = jsonElem?.get("refresh_token")?.jsonPrimitive?.contentOrNull
                val userJson = jsonElem?.get("user")?.toString()
                if (!access.isNullOrBlank()) {
                    AuthResult.Success(accessToken = access, refreshToken = refresh, userJson = userJson)
                } else {
                    val err = jsonElem?.get("error_description")?.jsonPrimitive?.contentOrNull
                        ?: jsonElem?.get("error")?.jsonPrimitive?.contentOrNull
                        ?: "Login gagal"
                    AuthResult.Failure(err)
                }
            } catch (t: Throwable) {
                Log.w("AuthRepository", "login exception", t)
                AuthResult.Failure(t.localizedMessage ?: "Login exception")
            }
        }
    }

    suspend fun register(email: String, password: String): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/auth/v1/signup"
                Log.d("AuthRepository", "REGISTER -> POST $url (email=${email}, anonPrefix=${anonKey.take(8)})")
                val resp: HttpResponse = http.post(url) {
                    contentType(ContentType.Application.Json)
                    header("apikey", anonKey)
                    header("Authorization", "Bearer $anonKey")
                    setBody(mapOf("email" to email, "password" to password))
                }

                val status = resp.status.value
                val bodyText = resp.bodyAsText()
                Log.d("AuthRepository", "REGISTER response status=$status body=$bodyText")

                val jsonElem = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
                val access = jsonElem?.get("access_token")?.jsonPrimitive?.contentOrNull
                val refresh = jsonElem?.get("refresh_token")?.jsonPrimitive?.contentOrNull
                val userJson = jsonElem?.get("user")?.toString()

                // If access token present -> success (session returned)
                if (!access.isNullOrBlank()) {
                    return@withContext AuthResult.Success(accessToken = access, refreshToken = refresh, userJson = userJson)
                }

                // If no access token, but user object present -> maybe email confirmation required (still success from Gotrue)
                if (!userJson.isNullOrBlank()) {
                    // return success but indicate no session
                    return@withContext AuthResult.Success(accessToken = null, refreshToken = null, userJson = userJson)
                }

                // Extract error message if present
                val err = jsonElem?.get("error_description")?.jsonPrimitive?.contentOrNull
                    ?: jsonElem?.get("error")?.jsonPrimitive?.contentOrNull
                    ?: "Register gagal (HTTP $status)"
                return@withContext AuthResult.Failure(err)
            } catch (t: Throwable) {
                Log.w("AuthRepository", "REGISTER exception", t)
                return@withContext AuthResult.Failure(t.localizedMessage ?: "Register exception")
            }
        }
    }

    suspend fun sendResetPasswordEmail(email: String): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/auth/v1/recover"
                val resp: HttpResponse = http.post(url) {
                    contentType(ContentType.Application.Json)
                    header("apikey", anonKey)
                    header("Authorization", "Bearer $anonKey")
                    setBody(mapOf("email" to email))
                }
                val bodyText = resp.bodyAsText()
                val jsonElem = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
                val err = jsonElem?.get("error_description")?.jsonPrimitive?.contentOrNull
                    ?: jsonElem?.get("error")?.jsonPrimitive?.contentOrNull
                if (err != null) AuthResult.Failure(err) else AuthResult.Success(null, null, null)
            } catch (t: Throwable) {
                Log.w("AuthRepository", "reset exception", t)
                AuthResult.Failure(t.localizedMessage ?: "Reset password gagal")
            }
        }
    }

    fun signOut() {
        // clear locally stored tokens via Prefs if you persist them
    }

    // -------------------
    // Gotrue helper
    // -------------------
    suspend fun getCurrentUserJson(accessToken: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/auth/v1/user"
                val resp: HttpResponse = http.get(url) {
                    header("Authorization", "Bearer $accessToken")
                    header("apikey", anonKey)
                    header("Accept", "application/json")
                }
                if (resp.status.value in 200..299) resp.bodyAsText() else null
            } catch (t: Throwable) {
                Log.w("AuthRepository", "getCurrentUserJson exception", t)
                null
            }
        }
    }

    // -------------------
    // PostgREST / users_meta helpers
    // -------------------
    @Serializable
    data class UserMeta(
        val id: String,
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("is_admin") val isAdmin: Boolean? = false,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        val locale: String? = null,
        val meta: JsonObject? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    suspend fun getUserMeta(userId: String, accessToken: String): UserMeta? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$restBase/users_meta?id=eq.$userId"
                val resp: HttpResponse = http.get(url) {
                    header("Authorization", "Bearer $accessToken")
                    header("apikey", anonKey)
                    header("Accept", "application/json")
                }
                val bodyText = resp.bodyAsText()
                if (resp.status.value in 200..299 && bodyText.isNotBlank() && bodyText.trim() != "[]") {
                    // decode to list and return first
                    val list: List<UserMeta> = json.decodeFromString(bodyText)
                    list.firstOrNull()
                } else null
            } catch (t: Throwable) {
                Log.w("AuthRepository", "getUserMeta exception", t)
                null
            }
        }
    }

    suspend fun createUserMeta(userId: String, payload: Map<String, Any?>, accessToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val bodyMap = payload.toMutableMap()
                bodyMap["id"] = userId
                val url = "$restBase/users_meta"
                val resp: HttpResponse = http.post(url) {
                    header("Authorization", "Bearer $accessToken")
                    header("apikey", anonKey)
                    contentType(ContentType.Application.Json)
                    setBody(listOf(bodyMap))
                }
                Log.d("AuthRepository", "createUserMeta status=${resp.status.value} body=${resp.bodyAsText()}")
                resp.status.value in 200..299
            } catch (t: Throwable) {
                Log.w("AuthRepository", "createUserMeta exception", t)
                false
            }
        }
    }

    suspend fun updateUserMeta(userId: String, payload: Map<String, Any?>, accessToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$restBase/users_meta?id=eq.$userId"
                val resp: HttpResponse = http.patch(url) {
                    header("Authorization", "Bearer $accessToken")
                    header("apikey", anonKey)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                Log.d("AuthRepository", "updateUserMeta status=${resp.status.value} body=${resp.bodyAsText()}")
                resp.status.value in 200..299
            } catch (t: Throwable) {
                Log.w("AuthRepository", "updateUserMeta exception", t)
                false
            }
        }
    }

    // -------------------
    // JSON parsing helpers
    // -------------------
    fun parseUserIdFromJson(userJson: String?): String? {
        if (userJson.isNullOrBlank()) return null
        return try {
            val obj = Json.parseToJsonElement(userJson).jsonObject
            obj["id"]?.jsonPrimitive?.contentOrNull
        } catch (t: Throwable) {
            Log.w("AuthRepository", "parseUserIdFromJson failed", t)
            null
        }
    }

    fun parseDisplayNameFromJson(userJson: String?): String? {
        if (userJson.isNullOrBlank()) return null
        return try {
            val obj = Json.parseToJsonElement(userJson).jsonObject
            val meta = when {
                obj["raw_user_meta_data"] != null -> obj["raw_user_meta_data"]?.jsonObject
                obj["user_metadata"] != null -> obj["user_metadata"]?.jsonObject
                else -> null
            }
            val fromMeta = meta?.get("display_name")?.jsonPrimitive?.contentOrNull
                ?: meta?.get("full_name")?.jsonPrimitive?.contentOrNull
            fromMeta ?: obj["email"]?.jsonPrimitive?.contentOrNull
        } catch (t: Throwable) {
            Log.w("AuthRepository", "parseDisplayNameFromJson failed", t)
            null
        }
    }
}