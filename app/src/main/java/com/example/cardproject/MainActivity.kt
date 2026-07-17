package com.example.cardproject

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.NavHostFragment
import com.example.cardproject.databinding.ActivityMainBinding
import com.example.cardproject.ui.auth.LoginFragment
import com.example.cardproject.ui.deck.DeckListFragment
import com.example.cardproject.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
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
        setContentView(R.layout.activity_main)

//        window.statusBarColor = ContextCompat.getColor(this, R.color.your_primary_dark_color)
//        window.navigationBarColor = ContextCompat.getColor(this, R.color.your_primary_color)
        // Простая загрузка только DeckListFragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DeckListFragment())
            .commit()

        tokenManager = TokenManager(applicationContext)

        // Проверяем авторизацию
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