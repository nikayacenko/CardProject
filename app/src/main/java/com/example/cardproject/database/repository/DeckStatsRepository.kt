package com.example.cardproject.database.repository

import com.example.cardproject.database.dao.CardDao
import com.example.cardproject.database.dao.DeckDao
import com.example.cardproject.database.dao.SessionStatsDao
import com.example.cardproject.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DeckStatsRepository @Inject constructor(
    private val cardDao: CardDao,
    private val sessionStatsDao: SessionStatsDao,
    private val deckDao: DeckDao
) {

    suspend fun getAllDecksStatsSync(): List<DeckStats> {
        val decks = deckDao.getAllDecksSync()
        return decks.map { deck ->
            getDeckStatsSync(deck.id)
        }
    }

    suspend fun getDeckStatsSync(deckId: Long): DeckStats {
        val cards = cardDao.getCardsByDeckSync(deckId)
        val sessions = sessionStatsDao.getSessionStatsByDeckSync(deckId)
        val deck = deckDao.getDeckByIdSync(deckId)

        return calculateDeckStats(deckId, deck?.name ?: "Неизвестная колода", cards, sessions)
    }

    fun getAllDecksStats(): Flow<List<DeckStats>> {
        return deckDao.getAllDecks().map { decks ->
            decks.map { deck ->
                getDeckStatsSync(deck.id)
            }
        }
    }

    private fun calculateDeckStats(
        deckId: Long,
        deckName: String,
        cards: List<Card>,
        sessions: List<SessionStats>
    ): DeckStats {
        val totalCards = cards.size

        // ИСПРАВЛЕННАЯ ЛОГИКА СТАТИСТИКИ:

        // Выученные карточки: 3+ правильных ответа подряд И stage >= 3
        val learnedCards = cards.count {
            it.consecutiveCorrect >= 3 && it.reviewStage >= 3
        }

        // Новые карточки: никогда не изучались (нет lastReviewed)
        val newCards = cards.count {
            it.lastReviewed == null
        }

        // Карточки в процессе: ВСЕ карточки, которые изучались хоть раз (есть lastReviewed),
        // но еще не выучены (не соответствуют критериям learnedCards)
        val inProgressCards = cards.count { card ->
            card.lastReviewed != null && // изучалась хоть раз
                    !(card.consecutiveCorrect >= 3 && card.reviewStage >= 3) // но еще не выучена
        }

        // Статистика по сессиям
        val totalSessions = sessions.size
        val totalStudyTime = sessions.sumOf { it.sessionDuration }
        val averageAccuracy = if (sessions.isNotEmpty()) {
            sessions.map { it.accuracy }.average()
        } else {
            0.0
        }

        // Карточки по статусам
        val cardsByStatus = mapOf(
            CardStatus.NEW to newCards,
            CardStatus.IN_PROGRESS to inProgressCards,
            CardStatus.LEARNED to learnedCards
        )

        // Точность последних сессий (максимум 10)
        val recentAccuracy = sessions.takeLast(10).map { it.accuracy }

        return DeckStats(
            deckId = deckId,
            deckName = deckName,
            totalCards = totalCards,
            learnedCards = learnedCards,
            inProgressCards = inProgressCards,
            newCards = newCards,
            totalSessions = totalSessions,
            totalStudyTime = totalStudyTime,
            averageAccuracy = averageAccuracy,
            cardsByStatus = cardsByStatus,
            recentAccuracy = recentAccuracy
        )
    }
}