package com.example.cardproject.ui.deck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.database.AppDatabase
import com.example.cardproject.database.repository.CardRepository
import com.example.cardproject.database.repository.DeckRepository
import com.example.cardproject.model.Deck
import com.example.cardproject.model.DeckWithTags
import com.example.cardproject.model.LearningMode
import com.example.cardproject.utils.DataSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class DeckListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DeckRepository

    // Потоки для поиска и фильтрации
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val _availableTags = MutableStateFlow<List<String>>(emptyList())

    // ОСНОВНОЙ ПОТОК ДАННЫХ - без фильтрации
    private val _allDecks = MutableStateFlow<List<DeckWithTags>>(emptyList())

    val searchQuery: StateFlow<String> = _searchQuery
    val selectedTags: StateFlow<Set<String>> = _selectedTags
    val availableTags: StateFlow<List<String>> = _availableTags

    init {
        val deckDao = AppDatabase.getInstance(application).deckDao()
        val tagDao = AppDatabase.getInstance(application).tagDao()
        val cardRepository = CardRepository(AppDatabase.getInstance(application).cardDao())
        repository = DeckRepository(deckDao, tagDao, cardRepository)

        loadAllDecks()
        loadAvailableTags()
        viewModelScope.launch {
            DataSyncManager.refreshDecks.collect {
                println("🔄 DeckListViewModel: получено уведомление об изменении карточек")
                loadAllDecks() // Перезагружаем колоды с актуальными счетчиками
            }
        }
    }

    // КОМБИНИРУЕМ ВСЕ ФИЛЬТРЫ ВМЕСТЕ - используем _allDecks как источник
    val decks = combine(
        _allDecks,
        _searchQuery,
        _selectedTags
    ) { allDecks, query, selectedTags ->
        println("🔄 Применение фильтров: всего колод=${allDecks.size}, query='$query', tags=$selectedTags")

        var filteredDecks = allDecks

        // Применяем текстовый поиск
        if (query.isNotBlank()) {
            filteredDecks = filteredDecks.filter { deckWithTags ->
                deckWithTags.deck.name.contains(query, ignoreCase = true) ||
                        deckWithTags.deck.description.contains(query, ignoreCase = true) ||
                        deckWithTags.tags.any { it.contains(query, ignoreCase = true) }
            }
            println("🔍 После поиска: ${filteredDecks.size} колод")
        }

        // Применяем фильтрацию по тегам
        if (selectedTags.isNotEmpty()) {
            filteredDecks = filterDecksByTags(filteredDecks, selectedTags)
            println("🏷️ После фильтрации по тегам: ${filteredDecks.size} колод")
        }

        // Логируем результат
        filteredDecks.forEach { deck ->
            println("   ✅ '${deck.deck.name}' - теги: ${deck.tags}, описание: '${deck.deck.description}'")
        }

        filteredDecks
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Загрузка всех колод
    private fun loadAllDecks() {
        viewModelScope.launch {
            try {
                // ПОДПИСЫВАЕМСЯ НА FLOW И ОБНОВЛЯЕМ _allDecks ПРИ КАЖДОМ ИЗМЕНЕНИИ
                repository.getAllDecksWithCardCount().collect { decks ->
                    _allDecks.value = decks
                    println("📚 Обновлены колоды: ${decks.size}")
                }
            } catch (e: Exception) {
                println("❌ Ошибка загрузки колод: ${e.message}")
            }
        }
    }

    // Метод для установки поискового запроса
    fun setSearchQuery(query: String) {
        _searchQuery.value = query.trim()
    }

    // Метод для очистки поиска
    fun clearSearch() {
        _searchQuery.value = ""
    }

    // Методы для фильтрации по тегам
    fun toggleTag(tag: String) {
        val currentTags = _selectedTags.value.toMutableSet()
        if (currentTags.contains(tag)) {
            currentTags.remove(tag)
            println("❌ Тег удален: $tag")
        } else {
            currentTags.add(tag)
            println("✅ Тег добавлен: $tag")
        }
        _selectedTags.value = currentTags
        println("🏷️ Текущие выбранные теги: $currentTags")
    }

    // Загрузка доступных тегов
    private fun loadAvailableTags() {
        viewModelScope.launch {
            try {
                // Используем метод только для тегов колод
                val tags = repository.getAllDeckTagNames() // Этот метод нужно добавить в DeckRepository
                _availableTags.value = tags.sorted()
                println("🏷️ Загружены доступные теги колод: $tags")
            } catch (e: Exception) {
                println("❌ Ошибка загрузки тегов: ${e.message}")
            }
        }
    }

    // Фильтрация колод по тегам (И логика - все выбранные теги должны присутствовать)
    private fun filterDecksByTags(decks: List<DeckWithTags>, selectedTags: Set<String>): List<DeckWithTags> {
        return decks.filter { deckWithTags ->
            val deckTags = deckWithTags.tags.map { it.lowercase() }
            val searchTags = selectedTags.map { it.lowercase() }

            val matches = searchTags.all { selectedTag ->
                deckTags.any { deckTag ->
                    deckTag == selectedTag
                }
            }

            if (matches) {
                println("   ✅ Колода '${deckWithTags.deck.name}' подходит под теги: $selectedTags")
            } else {
                println("   ❌ Колода '${deckWithTags.deck.name}' НЕ подходит под теги: $selectedTags (ее теги: $deckTags)")
            }

            matches
        }
    }

    fun createDeck(
        name: String,
        description: String = "",
        tags: List<String> = emptyList(),
        coverColor: String? = null,
        learningMode: LearningMode = LearningMode.LONG_TERM // ДОБАВЛЕНО
    ) {
        viewModelScope.launch {
            println("🔄 Создание колоды: '$name' с тегами: $tags, режим: $learningMode")
            repository.createDeck(name, description, tags, coverColor, learningMode)

            // ПЕРЕЗАГРУЖАЕМ ВСЕ ДАННЫЕ
            loadAllDecks()
            loadAvailableTags()
            println("✅ Колода создана, данные обновлены")
        }
    }

    fun deleteDeck(deck: Deck) {
        viewModelScope.launch {
            try {
                println("🗑️ Удаление колоды в ViewModel: ${deck.name}")
                repository.deleteDeck(deck)
                println("✅ Колода удалена успешно")

                // ПЕРЕЗАГРУЖАЕМ ВСЕ ДАННЫЕ
                loadAllDecks()
                loadAvailableTags()
            } catch (e: Exception) {
                println("❌ Ошибка при удалении колоды: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    suspend fun getTagsByDeckId(deckId: Long): List<String> {
        return repository.getTagsByDeckId(deckId)
    }

    fun updateDeck(deck: Deck, tags: List<String>) {
        viewModelScope.launch {
            println("✏️ Обновление колоды: '${deck.name}' с тегами: $tags")
            repository.updateDeck(deck, tags)

            // ПЕРЕЗАГРУЖАЕМ ВСЕ ДАННЫЕ
            loadAllDecks()
            loadAvailableTags()
            println("✅ Колода обновлена, данные перезагружены")
        }
    }

    // ПУБЛИЧНЫЙ МЕТОД ДЛЯ ПРИНУДИТЕЛЬНОГО ОБНОВЛЕНИЯ
    fun refreshData() {
        viewModelScope.launch {
            println("🔄 Принудительное обновление данных")
            loadAllDecks()
            loadAvailableTags()
        }
    }
}