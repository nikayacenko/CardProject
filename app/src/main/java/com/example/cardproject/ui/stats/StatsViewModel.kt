package com.example.cardproject.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.database.repository.DeckStatsRepository
import com.example.cardproject.database.repository.SessionStatsRepository
import com.example.cardproject.model.DeckStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val sessionStatsRepository: SessionStatsRepository,
    private val deckStatsRepository: DeckStatsRepository
) : ViewModel() {

    val allSessions = sessionStatsRepository.getAllSessionStats()
    val allDecksStats = deckStatsRepository.getAllDecksStats()

    val totalStats = allSessions.map { sessions ->
        calculateTotalStats(sessions)
    }

    // ДОБАВЬТЕ: Flow для отладки
    private val _debugInfo = MutableStateFlow("")
    val debugInfo: StateFlow<String> = _debugInfo.asStateFlow()

    private val _decksStatsSync = MutableStateFlow<List<DeckStats>>(emptyList())
    val decksStatsSync: StateFlow<List<DeckStats>> = _decksStatsSync.asStateFlow()

    init {
        loadAllDecksStats()
    }

    private fun loadAllDecksStats() {
        viewModelScope.launch {
            try {
                val stats = deckStatsRepository.getAllDecksStatsSync()
                _decksStatsSync.value = stats
                _debugInfo.value = "Загружено статистик колод: ${stats.size}"
            } catch (e: Exception) {
                _debugInfo.value = "Ошибка загрузки статистик: ${e.message}"
            }
        }
    }

    private fun calculateTotalStats(sessions: List<com.example.cardproject.model.SessionStats>): TotalStats {
        _debugInfo.value = "Сессий в БД: ${sessions.size}"

        if (sessions.isEmpty()) {
            return TotalStats()
        }

        val totalSessions = sessions.size
        val totalCardsStudied = sessions.sumOf { it.totalCards }
        val totalStudyTime = sessions.sumOf { it.sessionDuration }
        val averageAccuracy = sessions.map { it.accuracy }.average()

        _debugInfo.value = "Рассчитано: sessions=$totalSessions, cards=$totalCardsStudied, accuracy=$averageAccuracy"

        return TotalStats(
            totalSessions = totalSessions,
            totalCardsStudied = totalCardsStudied,
            averageAccuracy = averageAccuracy,
            totalStudyTime = totalStudyTime
        )
    }

    fun clearAllStats() {
        viewModelScope.launch {
            sessionStatsRepository.clearAllStats()
        }
    }

    // ДОБАВЬТЕ: метод для отладки
    fun getDebugInfo(): String {
        return _debugInfo.value
    }
}

data class TotalStats(
    val totalSessions: Int = 0,
    val totalCardsStudied: Int = 0,
    val averageAccuracy: Double = 0.0,
    val totalStudyTime: Long = 0
) {
    val formattedTotalTime: String
        get() = formatDuration(totalStudyTime)

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            "${hours}ч ${minutes % 60}м"
        } else if (minutes > 0) {
            "${minutes}м"
        } else {
            "${seconds}с"
        }
    }
}