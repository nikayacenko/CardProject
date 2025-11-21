package com.example.cardproject.database.repository

import com.example.cardproject.database.dao.NoteDao
import com.example.cardproject.database.dao.TagDao
import com.example.cardproject.model.Note
import com.example.cardproject.model.NoteWithTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val tagDao: TagDao
) {

    fun getAllNotesWithTags(): Flow<List<NoteWithTags>> {
        return noteDao.getAllNotes().combine(tagDao.getAllTagsFlow()) { notes, allTags ->
            notes.map { note ->
                val tagsForNote = allTags
                    .filter { it.noteId == note.id }
                    .map { it.name }
                NoteWithTags(note, tagsForNote)
            }
        }
    }

    suspend fun createNote(title: String, content: String, tags: List<String>): Long {
        val note = Note(title = title, content = content)
        val noteId = noteDao.insertNote(note)

        if (tags.isNotEmpty()) {
            tagDao.insertTags(noteId, tags, isForNote = true)
        }

        return noteId
    }

    suspend fun updateNote(note: Note, tags: List<String>) {
        noteDao.updateNote(note)
        tagDao.deleteTagsByNoteId(note.id)

        if (tags.isNotEmpty()) {
            tagDao.insertTags(note.id, tags, isForNote = true)
        }
    }

    suspend fun deleteNote(note: Note) {
        tagDao.deleteTagsByNoteId(note.id)
        noteDao.deleteNote(note)
    }

    suspend fun getNoteById(noteId: Long): NoteWithTags? {
        val note = noteDao.getNoteById(noteId) ?: return null
        val tags = tagDao.getTagsByNoteId(noteId).map { it.name }
        return NoteWithTags(note, tags)
    }

    fun searchNotesWithTags(query: String): Flow<List<NoteWithTags>> {
        return noteDao.searchNotes(query).combine(tagDao.getAllTagsFlow()) { notes, allTags ->
            notes.map { note ->
                val tagsForNote = allTags
                    .filter { it.noteId == note.id }
                    .map { it.name }
                NoteWithTags(note, tagsForNote)
            }
        }
    }

    suspend fun getAllNoteTagNames(): List<String> {
        return tagDao.getAllNoteTagNames()
    }

    // Метод для получения всех тегов (и колод и конспектов)
    suspend fun getAllTagNames(): List<String> {
        return tagDao.getAllTagNames()
    }
}