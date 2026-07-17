package com.example.cardproject.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.data.repository.AuthRepository
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
    private val tokenManager: TokenManager
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
                tokenManager.saveUser(result.user.username)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = result.user
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

            val result = authRepository.login(username, password)
            if (result != null) {
                // 🔥 ИСПРАВЛЕНО: accessToken вместо access_token
                tokenManager.saveToken(result.accessToken)  // ← было result.access_token
                tokenManager.saveUser(result.user.username)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = result.user
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
        tokenManager.clear()
        _uiState.value = AuthUiState()
    }



    fun checkAuth() {
        if (tokenManager.isLoggedIn()) {
            _uiState.value = _uiState.value.copy(
                isAuthenticated = true,
                username = tokenManager.getUsername()
            )
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