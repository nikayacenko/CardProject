package com.example.cardproject.database.dao

import androidx.room.*
import com.example.cardproject.model.Card
import com.example.cardproject.model.Deck
import com.example.cardproject.model.ReviewLog
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    fun getAllDecks(): Flow<List<Deck>>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun getDeckById(deckId: Long): Deck?

    @Insert
    suspend fun insertDeck(deck: Deck): Long

    @Update
    suspend fun updateDeck(deck: Deck)

    @Delete
    suspend fun deleteDeck(deck: Deck)

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeckById(deckId: Long){
        println("🚨🚨🚨 [DeckDao] УДАЛЕНИЕ ОДНОЙ КОЛОДЫ! deckId=$deckId")
        // выполнение
    }

    @Query("SELECT * FROM decks ORDER BY name ASC")
    suspend fun getAllDecksSync(): List<Deck>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun getDeckByIdSync(deckId: Long): Deck?

    @Query("DELETE FROM decks WHERE userId = :userId")
    suspend fun deleteAllDecks(userId: Long){
        println("🚨🚨🚨 [DeckDao] УДАЛЕНИЕ ОДНОЙ КОЛОДЫ! userId=$userId")
        // выполнение
    }

    // database/dao/DeckDao.kt
    @Query("SELECT * FROM decks WHERE userId = :userId ORDER BY name ASC")
    suspend fun getDecksByUser(userId: Long): List<Deck>

    // database/dao/CardDao.kt
    @Query("SELECT * FROM cards WHERE userId = :userId AND deckId = :deckId")
    suspend fun getCardsByDeck(userId: Long, deckId: Long): List<Card>

    // database/dao/ReviewLogDao.kt
    @Query("SELECT * FROM review_logs WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getLogsByUser(userId: Long): List<ReviewLog>

}

