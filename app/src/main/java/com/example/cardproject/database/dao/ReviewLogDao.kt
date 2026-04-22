// File: database/dao/ReviewLogDao.kt
package com.example.cardproject.database.dao

import androidx.room.*
import com.example.cardproject.model.ReviewLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {

    @Insert
    suspend fun insertLog(log: ReviewLog)

    @Insert
    suspend fun insertAll(logs: List<ReviewLog>)

    @Query("SELECT * FROM review_logs WHERE cardId = :cardId ORDER BY timestamp DESC")
    fun getLogsForCard(cardId: Long): Flow<List<ReviewLog>>

    @Query("SELECT * FROM review_logs WHERE cardId = :cardId ORDER BY timestamp DESC")
    suspend fun getLogsForCardSync(cardId: Long): List<ReviewLog>

    @Query("SELECT * FROM review_logs WHERE deckId = :deckId ORDER BY timestamp DESC LIMIT 500")
    suspend fun getLogsForDeck(deckId: Long): List<ReviewLog>

    @Query("SELECT * FROM review_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<ReviewLog>

    @Query("SELECT AVG(responseTimeMs) FROM review_logs WHERE deckId = :deckId")
    suspend fun getAverageResponseTimeForDeck(deckId: Long): Long?

    @Query("SELECT COUNT(*) FROM review_logs")
    suspend fun getLogsCount(): Int

    @Query("SELECT * FROM review_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsForTraining(limit: Int): List<ReviewLog>

    @Query("DELETE FROM review_logs WHERE timestamp < :cutoffDate")
    suspend fun deleteOldLogs(cutoffDate: Long)

    @Query("DELETE FROM review_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM review_logs WHERE cardId = :cardId")
    suspend fun deleteByCardId(cardId: Long)

}