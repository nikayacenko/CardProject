package com.example.cardproject.ui.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.database.repository.DeckStatsRepository
import com.example.cardproject.model.DeckPrediction
import com.example.cardproject.model.DeckStats
import com.example.cardproject.model.toPrediction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DeckStatsViewModel @Inject constructor(
    private val deckStatsRepository: DeckStatsRepository
) : ViewModel() {
    private val _prediction = MutableStateFlow<DeckPrediction?>(null)
    val prediction: StateFlow<DeckPrediction?> = _prediction.asStateFlow()
    private val _deckStats = MutableStateFlow<DeckStats?>(null)
    val deckStats: StateFlow<DeckStats?> = _deckStats.asStateFlow()

    fun loadDeckStats(deckId: Long) {
        viewModelScope.launch {
            val stats = deckStatsRepository.getDeckStatsSync(deckId)
            _deckStats.value = stats

            // Загружаем прогноз
            loadPrediction(deckId, stats)
        }
    }

    private suspend fun loadPrediction(deckId: Long, stats: DeckStats) {
        try {
            Log.d("DeckStatsVM", "🔮 Загрузка прогноза для колоды $deckId")
            Log.d("DeckStatsVM", "📊 Статистика: totalCards=${stats.totalCards}, learnedCards=${stats.learnedCards}")

            val firstSessionDate = deckStatsRepository.getFirstSessionDate(deckId)
            Log.d("DeckStatsVM", "📅 Первая сессия: $firstSessionDate")

            val sessionHistory = deckStatsRepository.getSessionHistory(deckId)
            Log.d("DeckStatsVM", "📊 История сессий: ${sessionHistory.size} записей")

            // Проверяем, что сессии не пустые
            if (sessionHistory.isNotEmpty()) {
                Log.d("DeckStatsVM", "   Первая: ${Date(sessionHistory.first().date)}")
                Log.d("DeckStatsVM", "   Последняя: ${Date(sessionHistory.last().date)}")
            }

            val prediction = stats.toPrediction(firstSessionDate, sessionHistory)
            Log.d("DeckStatsVM", "✅ Прогноз создан: daysToComplete=${prediction.predictedDaysToComplete}")
            Log.d("DeckStatsVM", "   milestones: ${prediction.milestones.size}")

            _prediction.value = prediction
        } catch (e: Exception) {
            Log.e("DeckStatsVM", "❌ Ошибка загрузки прогноза: ${e.message}")
            e.printStackTrace()
        }
    }
}