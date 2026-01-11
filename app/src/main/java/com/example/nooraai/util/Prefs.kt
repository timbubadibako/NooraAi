package com.example.nooraai.util

import android.content.Context

object Prefs {
    private const val PREFS = "noora_prefs"
    private const val KEY_LAST_EMAIL = "last_email"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    fun saveLastEmail(context: Context, email: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_EMAIL, email).apply()
    }

    fun getLastEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_EMAIL, "")
    }

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOGGED_IN, false)
    }

    fun saveAccessToken(context: Context, token: String?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (token == null) editor.remove(KEY_ACCESS_TOKEN) else editor.putString(KEY_ACCESS_TOKEN, token)
        editor.apply()
    }

    fun getAccessToken(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCESS_TOKEN, null)
    }

    fun saveRefreshToken(context: Context, token: String?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (token == null) editor.remove(KEY_REFRESH_TOKEN) else editor.putString(KEY_REFRESH_TOKEN, token)
        editor.apply()
    }

    fun getRefreshToken(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_REFRESH_TOKEN, null)
    }

    fun clearTokens(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }
}