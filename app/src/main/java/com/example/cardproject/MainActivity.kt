package com.example.cardproject

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.example.cardproject.data.repository.UserRepository
import com.example.cardproject.databinding.ActivityMainBinding
import com.example.cardproject.ui.auth.LoginFragment
import com.example.cardproject.ui.deck.DeckListFragment
import com.example.cardproject.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🔥 Сначала применяем тему
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeColor = prefs.getString("theme_color", "purple")
        when (themeColor) {
            "purple" -> setTheme(R.style.Theme_CardProject_Purple)
            "blue" -> setTheme(R.style.Theme_CardProject_Blue)
            "green" -> setTheme(R.style.Theme_CardProject_Green)
            "orange" -> setTheme(R.style.Theme_CardProject_Orange)
            "pink" -> setTheme(R.style.Theme_CardProject_Pink)
            else -> setTheme(R.style.Theme_CardProject_Purple)
        }

        super.onCreate(savedInstanceState)
        val userId = tokenManager.getUserId()
        val token = tokenManager.getToken()

        Log.d("MainActivity", "🔑 Token: ${token?.take(20)}...")
        Log.d("MainActivity", "🔑 UserId: $userId")

        if (token != null && userId == null) {
            // Если есть токен, но нет userId - извлекаем из токена
            try {
                val extractedUserId = tokenManager.extractUserIdFromToken(token)
                if (extractedUserId != null) {
                    tokenManager.saveUserId(extractedUserId)
                    Log.d("MainActivity", "✅ userId извлечён из токена: $extractedUserId")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Ошибка извлечения userId: ${e.message}")
            }
        }
        setContentView(R.layout.activity_main)

        // 🔥 Проверяем авторизацию
        if (tokenManager.isLoggedIn()) {
            showDeckList()
        } else {
            showLogin()
        }
    }

    private fun showLogin() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    private fun showDeckList() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DeckListFragment())
            .commit()
    }

    fun logoutAndShowLogin() {
        tokenManager.clear()
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }
}