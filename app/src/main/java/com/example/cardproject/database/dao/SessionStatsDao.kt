package com.example.cardproject.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.cardproject.model.SessionStats

@Dao
interface SessionStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionStats(stats: SessionStats): Long

    @Query("SELECT * FROM session_stats WHERE deckId = :deckId ORDER BY date DESC")
    fun getSessionStatsByDeck(deckId: Long): Flow<List<SessionStats>>

    @Query("SELECT * FROM session_stats ORDER BY date DESC LIMIT :limit")
    fun getRecentSessionStats(limit: Int = 50): Flow<List<SessionStats>>

    @Query("DELETE FROM session_stats WHERE deckId = :deckId")
    suspend fun deleteSessionStatsByDeck(deckId: Long)

    @Query("SELECT COUNT(*) FROM session_stats WHERE deckId = :deckId")
    suspend fun getSessionCount(deckId: Long): Int

    @Query("SELECT AVG((correctAnswers * 100.0) / totalCards) FROM session_stats WHERE deckId = :deckId AND totalCards > 0")
    suspend fun getAverageAccuracy(deckId: Long): Double?

    @Query("SELECT SUM(totalCards) FROM session_stats WHERE deckId = :deckId")
    suspend fun getTotalCardsStudied(deckId: Long): Int?

    @Query("SELECT SUM(sessionDuration) FROM session_stats WHERE deckId = :deckId")
    suspend fun getTotalStudyTime(deckId: Long): Long?

    @Query("SELECT COUNT(*) FROM session_stats WHERE deckId = :deckId AND sessionType = 'SPACED_REPETITION'")
    suspend fun getSpacedRepetitionSessionCount(deckId: Long): Int

    @Query("SELECT COUNT(*) FROM session_stats WHERE deckId = :deckId AND sessionType = 'FULL_REVIEW'")
    suspend fun getFullReviewSessionCount(deckId: Long): Int

    @Query("SELECT COUNT(*) FROM session_stats")
    suspend fun getTotalSessions(): Int

    @Query("SELECT SUM(totalCards) FROM session_stats")
    suspend fun getTotalCardsStudied(): Int?

    @Query("SELECT AVG((correctAnswers * 100.0) / totalCards) FROM session_stats WHERE totalCards > 0")
    suspend fun getAverageAccuracy(): Double?

    @Query("SELECT SUM(sessionDuration) FROM session_stats")
    suspend fun getTotalStudyTime(): Long?

    @Query("DELETE FROM session_stats")
    suspend fun clearAllStats()

    @Query("SELECT * FROM session_stats ORDER BY date DESC")
    fun getAllSessionStats(): Flow<List<SessionStats>>

    @Query("SELECT * FROM session_stats WHERE deckId = :deckId ORDER BY date DESC")
    suspend fun getSessionStatsByDeckSync(deckId: Long): List<SessionStats>

    @Query("SELECT * FROM session_stats WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getSessionStatsByDateRange(startDate: Long, endDate: Long): Flow<List<SessionStats>>

}