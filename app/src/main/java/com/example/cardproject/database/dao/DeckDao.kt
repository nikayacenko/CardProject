package com.example.cardproject.database.dao

import androidx.room.*
import com.example.cardproject.model.Deck
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
    suspend fun deleteDeckById(deckId: Long)

    @Query("SELECT * FROM decks ORDER BY name ASC")
    suspend fun getAllDecksSync(): List<Deck>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun getDeckByIdSync(deckId: Long): Deck?

}

