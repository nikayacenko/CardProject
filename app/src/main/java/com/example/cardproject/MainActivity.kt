package com.example.cardproject

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import com.example.cardproject.databinding.ActivityMainBinding
import com.example.cardproject.ui.deck.DeckListFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = ContextCompat.getColor(this, R.color.your_primary_dark_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.your_primary_color)
        // Простая загрузка только DeckListFragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DeckListFragment())
            .commit()
    }
}