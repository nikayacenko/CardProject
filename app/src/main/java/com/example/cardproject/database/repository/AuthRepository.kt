package com.example.cardproject.data.repository

import com.example.cardproject.data.sync.RetrofitClient
import com.example.cardproject.model.AuthRequest
import com.example.cardproject.model.AuthResponse
import com.example.cardproject.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {

    private val api = RetrofitClient.instance

    suspend fun register(username: String, password: String, email: String? = null): AuthResponse? {
        return try {
            val request = AuthRequest(username, password, email)
            val response = api.register(request)
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun login(username: String, password: String): AuthResponse? {
        return try {
            val request = AuthRequest(username, password)
            val response = api.login(request)
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getCurrentUser(token: String): User? {
        return try {
            val response = api.getCurrentUser("Bearer $token")
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}