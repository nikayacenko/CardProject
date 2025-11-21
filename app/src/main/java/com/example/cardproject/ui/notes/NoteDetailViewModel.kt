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
            val currentNote = _note.value
            if (currentNote != null) {
                // Обновляем существующий конспект
                val updatedNote = currentNote.note.copy(
                    title = title,
                    content = content,
                    updatedAt = System.currentTimeMillis()
                )
                noteRepository.updateNote(updatedNote, tags)
                _note.value = NoteWithTags(updatedNote, tags)
            } else {
                // Создаем новый конспект (на случай если что-то пошло не так)
                noteRepository.createNote(title, content, tags)
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