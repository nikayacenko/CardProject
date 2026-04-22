package com.example.cardproject.database.repository

import com.example.cardproject.algorithm.LearningProgress
import com.example.cardproject.algorithm.SpacedRepetitionCalcul
import com.example.cardproject.database.dao.CardDao
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CardRepository @Inject constructor(
    private val cardDao: CardDao
) {

    init {
        println("CardRepository создан с cardDao: ${cardDao != null}")
    }
    fun getCardsByDeck(deckId: Long): Flow<List<Card>> {
        return cardDao.getCardsByDeck(deckId)
    }

    suspend fun createCard(deckId: Long, front: String, back: String): Long {
        val card = Card(deckId = deckId, front = front, back = back)
        val id = cardDao.insertCard(card)
        return id
    }

//    suspend fun updateCard(card: Card) = cardDao.updateCard(card)

    suspend fun deleteCard(card: Card) = cardDao.deleteCard(card)

    suspend fun getCardCount(deckId: Long): Int {
        val count = cardDao.getCardCount(deckId)
        println("📊 Количество карточек в колоде $deckId: $count")
        return count
    }

    suspend fun getCardsDueForReview(deckId: Long): List<Card> {
        val cards = cardDao.getCardsByDeckSync(deckId)
        return SpacedRepetitionCalcul.getCardsDueForReview(cards)
    }

    suspend fun updateCardReview(card: Card, learningMode: LearningMode, quality: Int) {
        val updatedCard = SpacedRepetitionCalcul.calculateNextReview(card, learningMode, quality)
        cardDao.updateCard(updatedCard)
    }

    suspend fun getLearningProgress(deckId: Long): LearningProgress {
        val cards = cardDao.getCardsByDeckSync(deckId)
        return SpacedRepetitionCalcul.getLearningProgress(cards)
    }

    // Получить информацию о следующем повторении
    fun getNextReviewInfo(card: Card, learningMode: LearningMode): String {
        return SpacedRepetitionCalcul.getNextReviewTimeText(card, learningMode)
    }

    // Для получения всех карточек синхронно (для вычислений)
    suspend fun getCardsByDeckSync(deckId: Long): List<Card> {
        return cardDao.getCardsByDeckSync(deckId)
    }

    suspend fun getCardById(cardId: Long): Card? {
        return cardDao.getCardById(cardId)
    }

    suspend fun updateCard(card: Card) {
        cardDao.updateCard(card)
    }

    suspend fun blockCard(cardId: Long, blockSeconds: Int = 30) {
        val now = System.currentTimeMillis()
        val card = getCardById(cardId) ?: return

        val updatedCard = card.copy(
            nextReview = now + (blockSeconds * 1000L)
        )
        updateCard(updatedCard)
    }
}