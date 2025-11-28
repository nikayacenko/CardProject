package com.example.cardproject.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.database.repository.NoteRepository
import com.example.cardproject.model.Note
import com.example.cardproject.model.NoteWithTags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _note = MutableStateFlow<NoteWithTags?>(null)
    val note: StateFlow<NoteWithTags?> = _note

    private var currentNoteId: Long = -1

    fun loadNote(noteId: Long) {
        currentNoteId = noteId
        viewModelScope.launch {
            val loadedNote = noteRepository.getNoteById(noteId)
            _note.value = loadedNote
        }
    }

    fun saveNote(title: String, content: String, tags: List<String>) {
        viewModelScope.launch {
            try {
                val currentNote = _note.value
                if (currentNote != null) {
                    // Обновляем существующий конспект
                    val updatedNote = currentNote.note.copy(
                        title = title,
                        content = content,
                        updatedAt = System.currentTimeMillis()
                    )
                    noteRepository.updateNote(updatedNote, tags)
                    // Немедленно обновляем состояние
                    _note.value = NoteWithTags(updatedNote, tags)
                } else if (currentNoteId != -1L) {
                    // Запасной вариант: загружаем заново и обновляем
                    val existingNote = noteRepository.getNoteById(currentNoteId)
                    if (existingNote != null) {
                        val updatedNote = existingNote.note.copy(
                            title = title,
                            content = content,
                            updatedAt = System.currentTimeMillis()
                        )
                        noteRepository.updateNote(updatedNote, tags)
                        _note.value = NoteWithTags(updatedNote, tags)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Можно добавить обработку ошибок
            }
        }
    }

    fun deleteNote() {
        viewModelScope.launch {
            val currentNote = _note.value
            currentNote?.let {
                noteRepository.deleteNote(it.note)
            }
        }
    }
}