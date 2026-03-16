package com.example.cardproject.ui.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.algorithm.LearningProgress
import com.example.cardproject.database.repository.CardRepository
import com.example.cardproject.database.repository.DeckRepository
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.database.repository.SessionStatsRepository
import com.example.cardproject.ml.MLSpacedRepetitionCalculator
import com.example.cardproject.ml.TensorFlowLiteModel
import com.example.cardproject.model.AIContext
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.ReviewLog
import com.example.cardproject.model.SessionStats
import com.example.cardproject.model.SessionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LearningViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val sessionStatsRepository: SessionStatsRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val mlCalculator: MLSpacedRepetitionCalculator,
    private val tfliteModel: TensorFlowLiteModel
) : ViewModel() {

    private var deckId: Long = -1
    private var deckName: String = ""
    private var dueCards: List<Card> = emptyList()
    private var currentCardIndex = 0
    private var startTime: Long = 0

    // Результаты только для статистики текущей сессии
    private val sessionResults = mutableListOf<Pair<Boolean, Long>>()

    private val _currentCard = MutableStateFlow<Card?>(null)
    val currentCard: StateFlow<Card?> = _currentCard.asStateFlow()

    private val _learningProgress = MutableStateFlow(LearningProgress(0, 0, 0, 0))
    val learningProgress: StateFlow<LearningProgress> = _learningProgress.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    private val _sessionContext = MutableStateFlow(AIContext.create())
    val sessionContext: StateFlow<AIContext> = _sessionContext.asStateFlow()

    private val _isMLReady = MutableStateFlow(false)
    val isMLReady: StateFlow<Boolean> = _isMLReady.asStateFlow()

    init {
        viewModelScope.launch {
            _isMLReady.value = tfliteModel.isModelReady.value
        }
    }

    fun setDeckId(deckId: Long, mode: LearningMode) {
        this.deckId = deckId
        this.startTime = System.currentTimeMillis()
        _sessionContext.value = AIContext.create(learningMode = mode)
        loadDeckData()
    }

    private fun loadDeckData() {
        viewModelScope.launch {
            deckRepository.getDeckById(deckId)?.let { deckName = it.name }
            dueCards = cardRepository.getCardsDueForReview(deckId)
            updateProgress()
            if (dueCards.isNotEmpty()) {
                _currentCard.value = dueCards[currentCardIndex]
            }
        }
    }

    fun answerCard(card: Card, quality: Int, responseTimeMs: Long) {
        viewModelScope.launch {
            val context = _sessionContext.value

            // 1. МГНОВЕННОЕ СОХРАНЕНИЕ ПРОГРЕССА КАРТОЧКИ
            val updatedCard = mlCalculator.calculateNextReview(
                card, context, context.learningMode, quality, responseTimeMs
            )
            cardRepository.updateCard(updatedCard)

            // 2. СОХРАНЕНИЕ ЛОГА (ДЛЯ ОБУЧЕНИЯ МОДЕЛИ)
            val log = ReviewLog.from(card, context, quality, responseTimeMs, 0.5f, card.successRate)
            reviewLogRepository.insertLog(log)

            // 3. ДОБАВЛЕНИЕ В СТАТИСТИКУ СЕССИИ
            sessionResults.add((quality == 1) to responseTimeMs)

            // 4. ОБНОВЛЕНИЕ КОНТЕКСТА И ПЕРЕХОД
            _sessionContext.value = context.updateWithReview(quality == 1, responseTimeMs)

            currentCardIndex++
            if (currentCardIndex < dueCards.size) {
                _currentCard.value = dueCards[currentCardIndex]
            } else {
                finishSession() // Автоматическое завершение
            }
            updateProgress()
        }
    }

    fun finishSession() {
        viewModelScope.launch {
            saveSessionStats()
            _isFinished.value = true
        }
    }

    fun abandonSession() {
        // Прогресс карточек уже в БД, просто не создаем запись SessionStats
        sessionResults.clear()
        _isFinished.value = true
    }

    private suspend fun saveSessionStats() {
        if (sessionResults.isEmpty()) return

        val correct = sessionResults.count { it.first }
        val stats = SessionStats(
            deckId = deckId,
            deckName = deckName,
            sessionType = SessionType.SPACED_REPETITION,
            totalCards = sessionResults.size,
            correctAnswers = correct,
            wrongAnswers = sessionResults.size - correct,
            sessionDuration = System.currentTimeMillis() - startTime,
            learningMode = _sessionContext.value.learningMode
        )
        sessionStatsRepository.saveSessionStats(stats)
    }

    private suspend fun updateProgress() {
        _learningProgress.value = cardRepository.getLearningProgress(deckId)
    }

    fun getAnsweredCount() = sessionResults.size
    fun getTotalCount() = dueCards.size
    fun getCurrentSessionStats(): SessionStats {
        val correct = sessionResults.count { it.first }
        return SessionStats(
            deckId = deckId,
            deckName = deckName,
            sessionType = SessionType.SPACED_REPETITION,
            totalCards = sessionResults.size,
            correctAnswers = correct,
            wrongAnswers = sessionResults.size - correct,
            sessionDuration = System.currentTimeMillis() - startTime,
            learningMode = _sessionContext.value.learningMode
        )
    }
}