package com.example.cardproject.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.database.repository.NoteRepository
import com.example.cardproject.model.NoteWithTags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val _availableTags = MutableStateFlow<List<String>>(emptyList())

    val searchQuery: StateFlow<String> = _searchQuery
    val selectedTags: StateFlow<Set<String>> = _selectedTags
    val availableTags: StateFlow<List<String>> = _availableTags

    val notes = combine(
        noteRepository.getAllNotesWithTags(),
        _searchQuery,
        _selectedTags
    ) { allNotes, query, selectedTags ->
        var filteredNotes = allNotes

        // Применяем текстовый поиск
        if (query.isNotBlank()) {
            filteredNotes = filteredNotes.filter { noteWithTags ->
                noteWithTags.note.title.contains(query, ignoreCase = true) ||
                        noteWithTags.note.content.contains(query, ignoreCase = true) ||
                        noteWithTags.tags.any { it.contains(query, ignoreCase = true) }
            }
        }

        // Применяем фильтрацию по тегам
        if (selectedTags.isNotEmpty()) {
            filteredNotes = filteredNotes.filter { noteWithTags ->
                selectedTags.all { selectedTag ->
                    noteWithTags.tags.any { it.equals(selectedTag, ignoreCase = true) }
                }
            }
        }

        filteredNotes
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadAvailableTags()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query.trim()
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun toggleTag(tag: String) {
        val currentTags = _selectedTags.value.toMutableSet()
        if (currentTags.contains(tag)) {
            currentTags.remove(tag)
        } else {
            currentTags.add(tag)
        }
        _selectedTags.value = currentTags
    }

    private fun loadAvailableTags() {
        viewModelScope.launch {
            val tags = noteRepository.getAllNoteTagNames()
            _availableTags.value = tags.sorted()
        }
    }

    fun createNote(title: String, content: String, tags: List<String>) {
        viewModelScope.launch {
            noteRepository.createNote(title, content, tags)
            loadAvailableTags() // Перезагружаем теги после создания
        }
    }

    fun updateNote(noteWithTags: NoteWithTags) {
        viewModelScope.launch {
            noteRepository.updateNote(noteWithTags.note, noteWithTags.tags)
        }
    }

    fun deleteNote(noteWithTags: NoteWithTags) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteWithTags.note)
            loadAvailableTags() // Перезагружаем теги после удаления
        }
    }
}