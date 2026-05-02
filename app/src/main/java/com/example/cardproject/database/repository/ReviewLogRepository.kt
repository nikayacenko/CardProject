package com.example.cardproject.database.repository

import com.example.cardproject.database.AppDatabase
import com.example.cardproject.database.dao.ReviewLogDao
import com.example.cardproject.model.ReviewLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ReviewLogRepository @Inject constructor(
    private val reviewLogDao: ReviewLogDao
) {

    suspend fun insertLog(log: ReviewLog) = reviewLogDao.insertLog(log)

    suspend fun insertAll(logs: List<ReviewLog>) = reviewLogDao.insertAll(logs)

    fun getLogsForCard(cardId: Long): Flow<List<ReviewLog>> =
        reviewLogDao.getLogsForCard(cardId)

    suspend fun getRecentLogs(limit: Int): List<ReviewLog> =
        reviewLogDao.getRecentLogs(limit)

    suspend fun getLogsForDeck(deckId: Long): List<ReviewLog> =
        reviewLogDao.getLogsForDeck(deckId)

    suspend fun getAverageResponseTimeForDeck(deckId: Long): Long =
        reviewLogDao.getAverageResponseTimeForDeck(deckId) ?: 3000L

    suspend fun getCorrectRateForCard(cardId: Long): Float {
        val logs = reviewLogDao.getLogsForCardSync(cardId)
        if (logs.isEmpty()) return 0.5f
        val correct = logs.count { it.wasCorrect }
        return correct.toFloat() / logs.size
    }

    suspend fun getLogsCount(): Int = reviewLogDao.getLogsCount()

    suspend fun getLogsForTraining(limit: Int = 1000): List<ReviewLog> =
        reviewLogDao.getLogsForTraining(limit)



}