package com.example.cardproject.database.repository

import com.example.cardproject.database.dao.SessionStatsDao
import com.example.cardproject.model.SessionStats
import com.example.cardproject.ui.stats.TotalStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SessionStatsRepository @Inject constructor(
    private val sessionStatsDao: SessionStatsDao
) {
    suspend fun saveSessionStats(stats: SessionStats): Long {
        return sessionStatsDao.insertSessionStats(stats)
    }

    fun getSessionStatsByDeck(deckId: Long): Flow<List<SessionStats>> {
        return sessionStatsDao.getSessionStatsByDeck(deckId)
    }

    fun getRecentSessionStats(limit: Int = 50): Flow<List<SessionStats>> {
        return sessionStatsDao.getRecentSessionStats(limit)
    }

    suspend fun getSessionCount(deckId: Long): Int {
        return sessionStatsDao.getSessionCount(deckId)
    }

    suspend fun getAverageAccuracy(deckId: Long): Double? {
        return sessionStatsDao.getAverageAccuracy(deckId)
    }

    suspend fun getTotalCardsStudied(deckId: Long): Int? {
        return sessionStatsDao.getTotalCardsStudied(deckId)
    }

    suspend fun getTotalStudyTime(deckId: Long): Long? {
        return sessionStatsDao.getTotalStudyTime(deckId)
    }

    suspend fun getSpacedRepetitionSessionCount(deckId: Long): Int {
        return sessionStatsDao.getSpacedRepetitionSessionCount(deckId)
    }

    suspend fun getFullReviewSessionCount(deckId: Long): Int {
        return sessionStatsDao.getFullReviewSessionCount(deckId)
    }

    suspend fun getTotalStats(): TotalStats {
        val totalSessions = sessionStatsDao.getTotalSessions()
        val totalCardsStudied = sessionStatsDao.getTotalCardsStudied() ?: 0
        val averageAccuracy = sessionStatsDao.getAverageAccuracy() ?: 0.0
        val totalStudyTime = sessionStatsDao.getTotalStudyTime() ?: 0L

        return TotalStats(
            totalSessions = totalSessions,
            totalCardsStudied = totalCardsStudied,
            averageAccuracy = averageAccuracy,
            totalStudyTime = totalStudyTime
        )
    }

    suspend fun clearAllStats() {
        sessionStatsDao.clearAllStats()
    }

    fun getAllSessionStats(): Flow<List<SessionStats>> {
        return sessionStatsDao.getAllSessionStats()
    }


    fun getCalendarStats(startDate: Long, endDate: Long): Flow<Map<Long, CalendarStats>> {
        return sessionStatsDao.getSessionStatsByDateRange(startDate, endDate).map { sessions ->
            println(" Найдено сессий в диапазоне: ${sessions.size}")
            sessions.forEach { session ->
                println("    Сессия: ${java.util.Date(session.date)}, карточек: ${session.totalCards}")
            }

            sessions.groupBy { getStartOfDay(it.date) }
                .mapValues { (_, daySessions) ->
                    val totalCards = daySessions.sumOf { it.totalCards }
                    val correctCards = daySessions.sumOf { it.correctAnswers }
                    val successRate = if (totalCards > 0) (correctCards.toFloat() / totalCards) * 100f else 0f

                    CalendarStats(
                        cardCount = totalCards,
                        successRate = successRate
                    )
                }
        }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}

data class CalendarStats(
    val cardCount: Int,
    val successRate: Float
)