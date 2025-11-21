package com.example.cardproject.database.repository

import com.example.cardproject.database.dao.DeckDao
import com.example.cardproject.database.dao.TagDao
import com.example.cardproject.model.Deck
import com.example.cardproject.model.DeckWithTags
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.Tag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DeckRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val tagDao: TagDao,
    private val cardRepository: CardRepository
) {

    init {
        println("✅ DeckRepository создан:")
        println("   - deckDao: ${deckDao != null}")
        println("   - tagDao: ${tagDao != null}")
        println("   - cardRepository: ${cardRepository != null}")
    }

    // Получаем колоды с тегами
    fun getAllDecksWithTags(): Flow<List<DeckWithTags>> {
        return deckDao.getAllDecks().map { decks ->
            decks.map { deck ->
                // Загружаем теги для каждой колоды
                val tags = if (tagDao != null) {
                    getTagsByDeckId(deck.id)
                } else {
                    emptyList()
                }
                DeckWithTags(deck, tags)
            }
        }
    }
    fun getAllDecksWithCardCount(): Flow<List<DeckWithTags>> {
        return deckDao.getAllDecks().map { decks ->
            decks.map { deck ->
                // Загружаем теги для каждой колоды
                val tags = getTagsByDeckId(deck.id)

                // Получаем количество карточек для этой колоды
                val cardCount = cardRepository.getCardCount(deck.id)
                println("📊 DeckRepository: Колода '${deck.name}' имеет $cardCount карточек")

                // Создаем колоду с актуальным счетчиком
                val deckWithCount = deck.copy(cardCount = cardCount)
                DeckWithTags(deckWithCount, tags)
            }
        }
    }


    fun getAllDecks(): Flow<List<Deck>> = deckDao.getAllDecks()

    suspend fun getDeckById(deckId: Long): Deck? = deckDao.getDeckById(deckId)

    suspend fun createDeck(name: String, description: String = ""): Long {
        val deck = Deck(name = name, description = description)
        return deckDao.insertDeck(deck)
    }

    // Новый метод с тегами и обложкой
    suspend fun createDeck(
        name: String,
        description: String = "",
        tags: List<String> = emptyList(),
        coverColor: String? = null,
        learningMode: LearningMode = LearningMode.LONG_TERM // ДОБАВЛЕНО
    ): Long {
        val deck = Deck(
            name = name,
            description = description,
            coverColor = coverColor ?: "#CCCCCC",
            learningMode = learningMode // ДОБАВЛЕНО
        )
        val deckId = deckDao.insertDeck(deck)

        // Добавляем теги
        if (tags.isNotEmpty()) {
            tagDao.insertTags(deckId, tags, isForNote = false)
        }

        return deckId
    }

    suspend fun updateDeck(deck: Deck, tags: List<String>) {
        deckDao.updateDeck(deck)
        tagDao.deleteTagsByDeckId(deck.id)
        if (tags.isNotEmpty()) {
            tagDao.insertTags(deck.id, tags, isForNote = false)
        }

        // ДОБАВЬТЕ НЕБОЛЬШУЮ ЗАДЕРЖКУ ДЛЯ СИНХРОНИЗАЦИИ
        delay(50)
    }

    suspend fun deleteDeck(deck: Deck) {
        println("🗑️ Удаление колоды из репозитория: ${deck.name}")
        deckDao.deleteDeck(deck)
    }

    suspend fun getAllDeckTagNames(): List<String> {
        return tagDao.getAllDeckTagNames()
    }


    suspend fun deleteDeckById(deckId: Long) {
        deckDao.deleteDeckById(deckId)
    }

    // Методы для работы с тегами
    suspend fun getTagsByDeckId(deckId: Long): List<String> {
        return tagDao.getTagsByDeckId(deckId).map { it.name }
    }

    suspend fun getAllTagNames(): List<String> {
        return tagDao.getAllTagNames()
    }

//    suspend fun searchDecksByTag(tagName: String): List<DeckWithTags> {
//        val deckIds = tagDao.getDeckIdsByTag(tagName)
//        val decks = deckIds.mapNotNull { deckId -> deckDao.getDeckById(deckId) }
//        return decks.map { deck ->
//            val tags = getTagsByDeckId(deck.id)
//            DeckWithTags(deck, tags)
//        }
//    }

    // Вспомогательная функция для комбинирования Flow
    suspend fun getDeckWithTagsById(deckId: Long): DeckWithTags? {
        val deck = deckDao.getDeckById(deckId) ?: return null
        val tags = getTagsByDeckId(deckId)
        return DeckWithTags(deck, tags)
    }

}