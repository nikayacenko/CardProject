package com.example.cardproject.model

data class NoteWithTags(
    val note: Note,
    val tags: List<String>
)