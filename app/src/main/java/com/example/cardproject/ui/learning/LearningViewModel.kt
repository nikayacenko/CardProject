package com.example.cardproject.ui.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.algorithm.LearningProgress
import com.example.cardproject.algorithm.SpacedRepetitionCalcul
import com.example.cardproject.database.repository.CardRepository
import com.example.cardproject.database.repository.DeckRepository
import com.example.cardproject.database.repository.SessionStatsRepository
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
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
    private val sessionStatsRepository: SessionStatsRepository
) : ViewModel() {

    private var deckId: Long = -1
    private var learningMode: LearningMode = LearningMode.LONG_TERM
    private var dueCards: List<Card> = emptyList()
    private var currentCardIndex = 0
    private var totalCards: Int = 0
    private var startTime: Long = 0
    private var correctAnswers = 0
    private var wrongAnswers = 0
    private var deckName: String = ""

    // Храним временные ответы (не сохраняем в БД до завершения сессии)
    private val temporaryAnswers = mutableMapOf<Long, Int>() // cardId to quality

    private val _currentCard = MutableStateFlow<Card?>(null)
    val currentCard: StateFlow<Card?> = _currentCard.asStateFlow()

    private val _learningProgress = MutableStateFlow(LearningProgress(0, 0, 0, 0))
    val learningProgress: StateFlow<LearningProgress> = _learningProgress.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    fun setDeckId(deckId: Long, learningMode: LearningMode) {
        this.deckId = deckId
        this.learningMode = learningMode
        this.startTime = System.currentTimeMillis()
        this.correctAnswers = 0
        this.wrongAnswers = 0
        this.temporaryAnswers.clear()
        loadDeckName()
        loadDueCards()
    }

    private fun loadDeckName() {
        viewModelScope.launch {
            deckRepository.getDeckById(deckId)?.let { deck ->
                deckName = deck.name
            }
        }
    }

    private fun loadDueCards() {
        viewModelScope.launch {
            // Загружаем карточки для повторения (исключая временно отвеченные)
            val allDueCards = cardRepository.getCardsDueForReview(deckId)

            // Исключаем карточки, на которые уже дали временные ответы в этой сессии
            dueCards = allDueCards.filter { card ->
                !temporaryAnswers.containsKey(card.id)
            }

            totalCards = dueCards.size
            updateProgress()

            if (dueCards.isNotEmpty()) {
                currentCardIndex = 0
                _currentCard.value = dueCards[currentCardIndex]
            } else {
                _currentCard.value = null
            }
        }
    }

    fun getTotalCount(): Int {
        return totalCards
    }

    fun answerCard(quality: Int) {
        viewModelScope.launch {
            val currentCard = _currentCard.value ?: return@launch

            // Сохраняем временный ответ (НЕ обновляем в БД)
            temporaryAnswers[currentCard.id] = quality

            // Обновляем счетчики для отображения
            if (quality == 1) {
                correctAnswers++
            } else {
                wrongAnswers++
            }

            // Переходим к следующей карточке
            currentCardIndex++

            if (currentCardIndex < dueCards.size) {
                _currentCard.value = dueCards[currentCardIndex]
            } else {
                // Сессия завершена - сохраняем ВСЕ в БД
                saveAllToDatabase()
                _isFinished.value = true
            }

            updateProgress()
        }
    }

    private suspend fun saveAllToDatabase() {
        // 1. Обновляем все карточки в БД с временными ответами
        temporaryAnswers.forEach { (cardId, quality) ->
            val card = cardRepository.getCardById(cardId)
            card?.let {
                // Используем SpacedRepetitionCalcul напрямую
                val updatedCard = SpacedRepetitionCalcul.calculateNextReview(it, learningMode, quality)
                cardRepository.updateCard(updatedCard)
            }
        }

        // 2. Сохраняем статистику сессии
        val sessionDuration = System.currentTimeMillis() - startTime
        val totalAnswered = correctAnswers + wrongAnswers

        if (totalAnswered > 0) {
            val stats = SessionStats(
                deckId = deckId,
                deckName = deckName,
                sessionType = SessionType.SPACED_REPETITION,
                totalCards = totalAnswered,
                correctAnswers = correctAnswers,
                wrongAnswers = wrongAnswers,
                sessionDuration = sessionDuration,
                learningMode = learningMode
            )

            sessionStatsRepository.saveSessionStats(stats)
        }
    }

    // При досрочном выходе просто очищаем временные ответы
    fun cancelSession() {
        temporaryAnswers.clear()
        correctAnswers = 0
        wrongAnswers = 0
    }

    fun getAnsweredCardsCount(): Int {
        return correctAnswers + wrongAnswers
    }

    fun getRemainingCount(): Int {
        return dueCards.size - currentCardIndex
    }

    private suspend fun updateProgress() {
        // Используем оригинальный прогресс (без временных ответов)
        val progress = cardRepository.getLearningProgress(deckId)
        _learningProgress.value = progress
    }

    fun getCurrentSessionStats(): SessionStats {
        val sessionDuration = System.currentTimeMillis() - startTime
        val totalCards = correctAnswers + wrongAnswers

        return SessionStats(
            deckId = deckId,
            deckName = deckName,
            sessionType = SessionType.SPACED_REPETITION,
            totalCards = totalCards,
            correctAnswers = correctAnswers,
            wrongAnswers = wrongAnswers,
            sessionDuration = sessionDuration,
            learningMode = learningMode
        )
    }
}