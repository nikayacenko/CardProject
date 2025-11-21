package com.example.cardproject.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(), // Храним как Long
    val updatedAt: Long = System.currentTimeMillis()   // Храним как Long
)