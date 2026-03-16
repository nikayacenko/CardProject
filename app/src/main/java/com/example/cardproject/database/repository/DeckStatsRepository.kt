package com.example.cardproject.database.repository

import com.example.cardproject.database.dao.CardDao
import com.example.cardproject.database.dao.DeckDao
import com.example.cardproject.database.dao.SessionStatsDao
import com.example.cardproject.database.dao.ReviewLogDao
import com.example.cardproject.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckStatsRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val sessionStatsDao: SessionStatsDao,
    private val reviewLogDao: ReviewLogDao  // Добавили зависимость
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
        val reviewLogs = reviewLogDao.getLogsForDeck(deckId) // Получаем логи для дополнительной статистики

        return calculateDeckStats(
            deckId = deckId,
            deckName = deck?.name ?: "Неизвестная колода",
            cards = cards,
            sessions = sessions,
            reviewLogs = reviewLogs
        )
    }

    fun getAllDecksStats(): Flow<List<DeckStats>> {
        return deckDao.getAllDecks().map { decks ->
            decks.map { deck ->
                // Для Flow используем suspend функцию внутри map
                runBlockingOrUseFlowOperator(deck.id)
            }
        }
    }

    // Вспомогательная функция для Flow
    private suspend fun runBlockingOrUseFlowOperator(deckId: Long): DeckStats {
        val cards = cardDao.getCardsByDeckSync(deckId)
        val sessions = sessionStatsDao.getSessionStatsByDeckSync(deckId)
        val deck = deckDao.getDeckByIdSync(deckId)
        val reviewLogs = reviewLogDao.getLogsForDeck(deckId)

        return calculateDeckStats(
            deckId = deckId,
            deckName = deck?.name ?: "Неизвестная колода",
            cards = cards,
            sessions = sessions,
            reviewLogs = reviewLogs
        )
    }

    private suspend fun calculateDeckStats(
        deckId: Long,
        deckName: String,
        cards: List<Card>,
        sessions: List<SessionStats>,
        reviewLogs: List<ReviewLog> // Добавляем параметр
    ): DeckStats {
        val totalCards = cards.size

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

        // ===== НОВЫЕ ПАРАМЕТРЫ =====

        // Средняя сложность карточек в колоде
        val averageDifficulty = calculateAverageDifficulty(cards)

        // Среднее время ответа по колоде
        val averageResponseTimeMs = if (reviewLogs.isNotEmpty()) {
            reviewLogs.map { it.responseTimeMs }.average().toLong()
        } else {
            0L
        }

        // Концептуальная освоенность (процент связанных карточек)
        val conceptualMastery = calculateConceptualMastery(cards)

        // Количество карточек без связей
        val orphanCardsCount = cards.count { card ->
            card.linkedCardIds.isNullOrBlank() || card.linkedCardIds.split(",").size < 2
        }

        // Общее количество повторений
        val totalReviewsCount = reviewLogs.size

        // Тренд времени ответа (положительный = замедляется, отрицательный = ускоряется)
        val responseTimeTrend = calculateResponseTimeTrend(reviewLogs)

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
            recentAccuracy = recentAccuracy,
            // Добавляем новые параметры:
            averageDifficulty = averageDifficulty,
            averageResponseTimeMs = averageResponseTimeMs,
            conceptualMastery = conceptualMastery,
            orphanCardsCount = orphanCardsCount,
            totalReviewsCount = totalReviewsCount,
            responseTimeTrend = responseTimeTrend
        )
    }

    private fun calculateAverageDifficulty(cards: List<Card>): Float {
        if (cards.isEmpty()) return 0.5f

        // Примерная оценка сложности на основе длины текста
        var totalDifficulty = 0.0f
        for (card in cards) {
            val frontLength = card.front.length
            val backLength = card.back.length
            // Простая эвристика: чем длиннее текст, тем сложнее
            val difficulty = (frontLength + backLength) / 1000f
            totalDifficulty += difficulty.coerceIn(0.1f, 1.0f)
        }

        return totalDifficulty / cards.size
    }

    private fun calculateConceptualMastery(cards: List<Card>): Float {
        if (cards.isEmpty()) return 0.0f

        // Процент карточек, у которых есть связи
        val cardsWithLinks = cards.count {
            !it.linkedCardIds.isNullOrBlank() && it.linkedCardIds.split(",").size >= 2
        }

        return cardsWithLinks.toFloat() / cards.size
    }

    private fun calculateResponseTimeTrend(logs: List<ReviewLog>): Float {
        if (logs.size < 5) return 0.0f

        // Берем последние 10 логов (или меньше)
        val recentLogs = logs.takeLast(10)
        if (recentLogs.size < 2) return 0.0f

        // Простая линейная регрессия для определения тренда
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0

        recentLogs.forEachIndexed { index, log ->
            val x = index.toDouble()
            val y = log.responseTimeMs.toDouble()
            sumX += x
            sumY += y
            sumXY += x * y
            sumX2 += x * x
        }

        val n = recentLogs.size.toDouble()
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)

        // Нормализуем до диапазона -1..1
        return (slope / 1000.0).toFloat().coerceIn(-1f, 1f)
    }
}