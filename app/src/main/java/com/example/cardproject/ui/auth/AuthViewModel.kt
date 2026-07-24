package com.example.cardproject.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.data.repository.AuthRepository
import com.example.cardproject.data.repository.UserRepository
import com.example.cardproject.model.User
import com.example.cardproject.utils.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun register(username: String, password: String, email: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = authRepository.register(username, password, email)
            if (result != null) {
                // 🔥 ИСПРАВЛЕНО: accessToken вместо access_token
                tokenManager.saveToken(result.accessToken)  // ← было result.access_token
                tokenManager.saveUserId(result.user.id.toLong())
                tokenManager.saveUser(result.user.username)
                userRepository.saveUserFromServer(result.user)
                userRepository.updateLastLogin()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = result.user,
                    username = result.user.username
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка регистрации. Возможно, пользователь уже существует."
                )
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            println("🔐 [AuthVM] ===== НАЧАЛО ВХОДА =====")
            println("🔐 [AuthVM] username: $username")

            val result = authRepository.login(username, password)
            if (result != null) {
                // 🔥 ЛОГИРУЕМ
                Log.d("AuthVM", "🔐 Вход успешен")
                Log.d("AuthVM", "   Token: ${result.accessToken.take(20)}...")
                Log.d("AuthVM", "   User ID: ${result.user.id}")

                // 🔥 СОХРАНЯЕМ
                tokenManager.saveToken(result.accessToken)
                tokenManager.saveUserId(result.user.id.toLong())
                tokenManager.saveUser(result.user.username)

                // 🔥 ПРОВЕРЯЕМ, ЧТО СОХРАНИЛОСЬ
                val savedUserId = tokenManager.getUserId()
                Log.d("AuthVM", "   Saved User ID: $savedUserId")

                userRepository.saveUserFromServer(result.user)
                userRepository.updateLastLogin()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = result.user,
                    username = result.user.username
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Неверный логин или пароль"
                )
            }
        }
    }

    fun logout() {
        // 🔥 Очищаем токен
        tokenManager.clear()

        // 🔥 Очищаем локальную БД (опционально - можно оставить данные для офлайн-режима)
        viewModelScope.launch {
            try {
                // Очищаем все данные пользователя
//                userRepository.clearAll()
                // deckRepository.clearAll()
                // cardRepository.clearAll()
                // reviewLogRepository.clearAll()
                Log.d("AuthVM", "✅ Данные очищены")
            } catch (e: Exception) {
                Log.e("AuthVM", "❌ Ошибка очистки данных: ${e.message}")
            }
        }

        _uiState.value = AuthUiState()
        Log.d("AuthVM", "✅ Выход выполнен")
    }



    fun checkAuth() {
        if (tokenManager.isLoggedIn()) {
            viewModelScope.launch {
                val localUser = userRepository.getCurrentUser()
                if (localUser != null) {
                    _uiState.value = _uiState.value.copy(
                        isAuthenticated = true,
                        username = localUser.username,
                        user = localUser.toUser()
                    )
                    Log.d("AuthVM", "✅ Пользователь авторизован: ${localUser.username}")
                } else {
                    // Если пользователь не найден локально, но токен есть
                    // Пробуем загрузить с сервера
                    loadUserFromServer()
                }
            }
        } else {
            Log.d("AuthVM", "❌ Пользователь не авторизован")
        }
    }
    private suspend fun loadUserFromServer() {
        try {
            val userId = tokenManager.getUserId() ?: return
            // Здесь запрос к серверу для получения данных пользователя
            // val user = authRepository.getUser(userId)
            // userRepository.saveUserFromServer(user)
        } catch (e: Exception) {
            Log.e("AuthVM", "❌ Ошибка загрузки пользователя: ${e.message}")
        }
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: User? = null,
    val username: String? = null,
    val error: String? = null
)