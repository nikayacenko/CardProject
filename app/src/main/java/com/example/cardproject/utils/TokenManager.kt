package com.example.cardproject.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
        val userId = extractUserIdFromToken(token)
        if (userId != null) {
            saveUserId(userId)
        }
    }
    fun extractUserIdFromToken(token: String): Long? {
        return try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                val json = org.json.JSONObject(payload)
                json.getLong("sub")
            } else null
        } catch (e: Exception) {
            null
        }
    }
    fun saveUserId(userId: Long) {
        prefs.edit().putLong("user_id", userId).apply()
    }

    fun getUserId(): Long? {
        return prefs.getLong("user_id", -1).takeIf { it != -1L }
    }
    fun getToken(): String? {
        return prefs.getString("access_token", null)
    }

    fun saveUser(username: String) {
        prefs.edit().putString("username", username).apply()
    }

    fun getUsername(): String? {
        return prefs.getString("username", null)
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    fun clear() {
        prefs.edit().clear().apply()
    }


}