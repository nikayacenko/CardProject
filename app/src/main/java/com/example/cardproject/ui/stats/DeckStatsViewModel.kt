package com.example.cardproject.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.database.repository.DeckStatsRepository
import com.example.cardproject.model.DeckStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckStatsViewModel @Inject constructor(
    private val deckStatsRepository: DeckStatsRepository
) : ViewModel() {

    private val _deckStats = MutableStateFlow<DeckStats?>(null)
    val deckStats: StateFlow<DeckStats?> = _deckStats.asStateFlow()

    fun loadDeckStats(deckId: Long) {
        viewModelScope.launch {
            val stats = deckStatsRepository.getDeckStatsSync(deckId)
            _deckStats.value = stats
        }
    }
}