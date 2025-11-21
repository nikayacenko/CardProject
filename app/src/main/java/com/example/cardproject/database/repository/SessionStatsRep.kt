package com.example.cardproject.database.repository

import com.example.cardproject.database.dao.SessionStatsDao
import com.example.cardproject.model.SessionStats
import com.example.cardproject.ui.stats.TotalStats
import kotlinx.coroutines.flow.Flow
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


}