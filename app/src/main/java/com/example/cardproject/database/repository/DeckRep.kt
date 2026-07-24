package com.example.cardproject.database.repository

import com.example.cardproject.database.dao.DeckDao
import com.example.cardproject.database.dao.TagDao
import com.example.cardproject.model.Deck
import com.example.cardproject.model.DeckWithTags
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.Tag
import com.example.cardproject.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DeckRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val tagDao: TagDao,
    private val cardRepository: CardRepository,
    private val tokenManager: TokenManager
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
        return flow {
            while (true) {
                try {
                    // 🔥 ПОЛУЧАЕМ userId
                    val userId = tokenManager.getUserId()
                    println("🔑 [DeckRepository] Текущий userId: $userId")

                    if (userId == null) {
                        println("⚠️ [DeckRepository] userId = null, возвращаем пустой список")
                        emit(emptyList())
                        delay(1000)
                        continue
                    }

                    // 🔥 ЗАГРУЖАЕМ КОЛОДЫ ТОЛЬКО ДЛЯ ЭТОГО ПОЛЬЗОВАТЕЛЯ
                    val decks = deckDao.getDecksByUser(userId)
                    println("📚 [DeckRepository] Найдено ${decks.size} колод для userId=$userId")

                    // 🔥 ДЛЯ КАЖДОЙ КОЛОДЫ ЗАГРУЖАЕМ ТЕГИ И КОЛИЧЕСТВО КАРТОЧЕК
                    val result = decks.map { deck ->
                        val tags = getTagsByDeckId(deck.id)
                        val cardCount = cardRepository.getCardCount(deck.id)
                        println("📊 [DeckRepository] Колода '${deck.name}' (ID: ${deck.id}) имеет $cardCount карточек")

                        val deckWithCount = deck.copy(cardCount = cardCount)
                        DeckWithTags(deckWithCount, tags)
                    }

                    emit(result)

                    // Ждём перед следующим обновлением
                    delay(5000)

                } catch (e: Exception) {
                    println("❌ [DeckRepository] Ошибка загрузки колод: ${e.message}")
                    e.printStackTrace()
                    emit(emptyList())
                    delay(5000)
                }
            }
        }
    }


    fun getAllDecks(): Flow<List<Deck>> = deckDao.getAllDecks()

    suspend fun getDeckById(deckId: Long): Deck? = deckDao.getDeckById(deckId)

    suspend fun createDeck(name: String): Long {
        val userId = tokenManager.getUserId() ?: throw IllegalStateException("User not logged in")
        val deck = Deck(userId = userId, name = name)
        return deckDao.insertDeck(deck)
    }

    // Новый метод с тегами и обложкой
    suspend fun createDeck(
        userId: Long,
        name: String,
        description: String = "",
        tags: List<String> = emptyList(),
        coverColor: String? = null,
        learningMode: LearningMode = LearningMode.LONG_TERM
    ): Long {
        val deck = Deck(
            userId = userId,
            name = name,
            description = description,
            coverColor = coverColor ?: "#CCCCCC",
            learningMode = learningMode
        )
        val deckId = deckDao.insertDeck(deck)
        println("   ✅ Колода создана с ID: $deckId")

        if (tags.isNotEmpty()) {
            tagDao.insertTags(deckId, tags, isForNote = false)
            println("   ✅ Теги добавлены: $tags")
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


//    suspend fun deleteDeckById(deckId: Long) {
//        deckDao.deleteDeckById(deckId)
//    }

    // Методы для работы с тегами
    suspend fun getTagsByDeckId(deckId: Long): List<String> {
        return tagDao.getTagsByDeckId(deckId).map { it.name }
    }



}