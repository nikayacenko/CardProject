// utils/SessionManager.kt
package com.example.cardproject.utils

import com.example.cardproject.data.repository.UserRepository
import com.example.cardproject.model.LocalUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val tokenManager: TokenManager,
    private val userRepository: UserRepository
) {

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    fun getToken(): String? = tokenManager.getToken()

    fun getUserId(): Long? = tokenManager.getUserId()

    fun getUsername(): String? = tokenManager.getUsername()

    suspend fun getCurrentUser(): LocalUser? = userRepository.getCurrentUser()

    fun clear() {
        tokenManager.clear()
    }
}