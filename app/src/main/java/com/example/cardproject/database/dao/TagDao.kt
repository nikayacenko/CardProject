package com.example.cardproject.database.dao

import androidx.room.*
import com.example.cardproject.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    // Основные CRUD операции
    @Insert
    suspend fun insertTag(tag: Tag): Long

    @Delete
    suspend fun deleteTag(tag: Tag)

    // Flow методы для реактивного программирования
    @Query("SELECT * FROM tag")
    fun getAllTagsFlow(): Flow<List<Tag>>

    @Query("SELECT * FROM tag WHERE deckId = :deckId")
    fun getTagsByDeckIdFlow(deckId: Long): Flow<List<Tag>>

    @Query("SELECT * FROM tag WHERE noteId = :noteId")
    fun getTagsByNoteIdFlow(noteId: Long): Flow<List<Tag>>

    // Обычные suspend методы для однократного получения
    @Query("SELECT * FROM tag")
    suspend fun getAllTags(): List<Tag>

    @Query("SELECT * FROM tag WHERE deckId = :deckId")
    suspend fun getTagsByDeckId(deckId: Long): List<Tag>

    @Query("SELECT * FROM tag WHERE noteId = :noteId")
    suspend fun getTagsByNoteId(noteId: Long): List<Tag>

    // Остальные методы остаются без изменений
    @Query("DELETE FROM tag WHERE deckId = :deckId")
    suspend fun deleteTagsByDeckId(deckId: Long)

    @Query("DELETE FROM tag WHERE noteId = :noteId")
    suspend fun deleteTagsByNoteId(noteId: Long)

    @Query("SELECT DISTINCT name FROM tag WHERE deckId IS NOT NULL ORDER BY name")
    suspend fun getAllDeckTagNames(): List<String>

    @Query("SELECT DISTINCT name FROM tag WHERE noteId IS NOT NULL ORDER BY name")
    suspend fun getAllNoteTagNames(): List<String>

    @Query("SELECT DISTINCT name FROM tag ORDER BY name")
    suspend fun getAllTagNames(): List<String>

    @Transaction
    suspend fun insertTags(itemId: Long, tags: List<String>, isForNote: Boolean = false) {
        tags.forEach { tagName ->
            val tag = if (isForNote) {
                Tag(name = tagName, noteId = itemId)
            } else {
                Tag(name = tagName, deckId = itemId)
            }
            insertTag(tag)
        }
    }
}