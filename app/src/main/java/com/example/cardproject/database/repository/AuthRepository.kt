package com.example.cardproject.data.repository

import com.example.cardproject.data.sync.RetrofitClient
import com.example.cardproject.data.sync.ServerApi
import com.example.cardproject.model.AuthRequest
import com.example.cardproject.model.AuthResponse
import com.example.cardproject.model.User
import com.example.cardproject.utils.TokenManager
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ServerApi,
    private val tokenManager: TokenManager
) {

    suspend fun register(username: String, password: String, email: String? = null): AuthResponse? {
        return try {
            val request = AuthRequest(username, password, email)
            println("🔴🔴🔴 [REGISTER] ===== НАЧАЛО =====")
            println("🔴 [REGISTER] username: $username")
            println("🔴 [REGISTER] email: $email")
            println("🔴 [REGISTER] URL: http://10.0.2.2:8000/auth/register")

            val response: Response<AuthResponse> = api.register(request)

            println("🔴 [REGISTER] Код ответа: ${response.code()}")
            println("🔴 [REGISTER] Успешно: ${response.isSuccessful}")
            println("🔴 [REGISTER] Тело: ${response.body()}")
            println("🔴 [REGISTER] Ошибка: ${response.errorBody()?.string()}")
            println("🔴🔴🔴 [REGISTER] ===== КОНЕЦ =====")

            if (response.isSuccessful && response.body()?.status == "success") {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            println("🔴🔴🔴 [REGISTER] ОШИБКА: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun login(username: String, password: String): AuthResponse? {
        return try {
            val request = AuthRequest(username, password)
            val response: Response<AuthResponse> = api.login(request)
            if (response.isSuccessful && response.body()?.status == "success") {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getUser(userId: Long): User? {
        return try {
            val token = tokenManager.getToken() ?: return null
            val response = api.getCurrentUser("Bearer $token")
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
}