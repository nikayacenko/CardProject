package com.example.cardproject.database.dao

import androidx.room.*
import com.example.cardproject.model.Card
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun getCardsByDeck(deckId: Long): Flow<List<Card>>

    @Insert
    suspend fun insertCard(card: Card): Long

    @Update
    suspend fun updateCard(card: Card)

    @Delete
    suspend fun deleteCard(card: Card)

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId")
    suspend fun getCardCount(deckId: Long): Int

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun getCardsByDeckSync(deckId: Long): List<Card>

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCardById(cardId: Long): Card?


}