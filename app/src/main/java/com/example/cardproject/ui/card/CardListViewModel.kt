package com.example.cardproject.ui.card

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.database.AppDatabase
import com.example.cardproject.database.repository.CardRepository
import com.example.cardproject.database.repository.DeckRepository
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.utils.DataSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class CardListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CardRepository
    private val deckRepository: DeckRepository
    private var currentDeckId: Long = -1

    // ДОБАВЛЕНО: Flow для хранения режима обучения
    private val _learningMode = MutableStateFlow<LearningMode?>(null)
    val learningMode = _learningMode.asStateFlow()

    init {
        val cardDao = AppDatabase.getInstance(application).cardDao()
        val deckDao = AppDatabase.getInstance(application).deckDao()
        val tagDao = AppDatabase.getInstance(application).tagDao()
        repository = CardRepository(cardDao)
        deckRepository = DeckRepository(deckDao, tagDao, repository)
        println("🔄 CardListViewModel инициализирован")
    }

    fun setDeckId(deckId: Long) {
        currentDeckId = deckId
        println("🎯 Установлен deckId: $deckId")
        loadLearningMode() // ДОБАВЛЕНО: Загружаем режим обучения
    }

    // ДОБАВЛЕНО: Метод для загрузки режима обучения
    private fun loadLearningMode() {
        viewModelScope.launch {
            try {
                val deck = deckRepository.getDeckById(currentDeckId)
                _learningMode.value = deck?.learningMode ?: LearningMode.LONG_TERM
                println("🎯 Загружен режим обучения: ${_learningMode.value}")
            } catch (e: Exception) {
                println("❌ Ошибка загрузки режима обучения: ${e.message}")
                _learningMode.value = LearningMode.LONG_TERM
            }
        }
    }

    fun updateCard(card: Card) {
        viewModelScope.launch {
            repository.updateCard(card)
        }
    }

    fun deleteCard(card: Card) {
        viewModelScope.launch {
            repository.deleteCard(card)
        }
    }

    // ДОБАВЛЕНО: Метод для получения режима обучения
    fun getLearningMode(): LearningMode {
        return _learningMode.value ?: LearningMode.LONG_TERM
    }

    val cards get() = repository.getCardsByDeck(currentDeckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCard(front: String, back: String,questionType: String = "FACT") {
        viewModelScope.launch {
            println("🔄 Начало создания карточки: deckId=$currentDeckId, front='$front', back='$back'")

            if (currentDeckId != -1L) {
                try {
                    val cardId = repository.createCard(currentDeckId, front, back,questionType = questionType)
                    println("✅ Карточка создана с ID: $cardId")

                    val count = repository.getCardCount(currentDeckId)
                    println("📊 Теперь карточек в колоде: $count")

                    // Задержка для синхронизации БД
                    delay(100)

                    // Уведомляем об обновлении счетчика карточек
                    DataSyncManager.notifyDecksChanged()
                } catch (e: Exception) {
                    println("❌ Ошибка при создании карточки: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                println("❌ Неверный deckId: $currentDeckId")
            }
        }
    }

    private suspend fun notifyDecksChanged() {
        println("🔔 Уведомление: карточки изменились, нужно обновить счетчики колод")
    }
}