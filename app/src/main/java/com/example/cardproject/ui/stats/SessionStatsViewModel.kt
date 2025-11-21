package com.example.cardproject.ui.stats

import androidx.lifecycle.ViewModel
import com.example.cardproject.database.repository.SessionStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionStatsViewModel @Inject constructor(
    private val sessionStatsRepository: SessionStatsRepository
) : ViewModel() {
    // Можно добавить методы для получения истории сессий
}