package com.example.nooraai

import android.content.Context

/**
 * Merged Prefs helper:
 * - keeps the original API used elsewhere in the project (non-null getLastEmail, saveLastEmail, setLoggedIn, isLoggedIn, clearSession)
 * - adds optional user display name helpers (getUserDisplayName/saveUserDisplayName) used by ProfileActivity
 * - provides clearUserData for full cleanup on logout
 *
 * Replace the old Prefs.kt with this file and rebuild.
 */
object Prefs {
    private const val PREFS_NAME = "nooraai_prefs"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_LAST_EMAIL = "last_email"
    private const val KEY_USER_NAME = "user_display_name"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- login flag ---
    fun setLoggedIn(ctx: Context, loggedIn: Boolean) {
        sp(ctx).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
    }

    fun isLoggedIn(ctx: Context): Boolean {
        return sp(ctx).getBoolean(KEY_LOGGED_IN, false) // default false -> safe
    }

    // --- last email (non-null for compatibility with existing call sites) ---
    fun saveLastEmail(ctx: Context, email: String) {
        sp(ctx).edit().putString(KEY_LAST_EMAIL, email).apply()
    }

    fun getLastEmail(ctx: Context): String {
        return sp(ctx).getString(KEY_LAST_EMAIL, "") ?: ""
    }

    // --- user display name (nullable) ---
    fun saveUserDisplayName(ctx: Context, displayName: String) {
        sp(ctx).edit().putString(KEY_USER_NAME, displayName).apply()
    }

    fun getUserDisplayName(ctx: Context): String? {
        return sp(ctx).getString(KEY_USER_NAME, null)
    }

    // --- clear helpers ---
    // old clearSession behavior (keeps compatibility)
    fun clearSession(ctx: Context) {
        sp(ctx).edit().remove(KEY_LOGGED_IN).remove(KEY_LAST_EMAIL).apply()
    }

    // more thorough clear used on logout
    fun clearUserData(ctx: Context) {
        sp(ctx).edit()
            .remove(KEY_LAST_EMAIL)
            .remove(KEY_USER_NAME)
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
    }
}