package com.example.cardproject.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val createdAt: Long = System.currentTimeMillis(),
    var lastReviewed: Long? = null,
    var nextReview: Long? = null,
    var easeFactor: Double = 2.5,
    var interval: Int = 1,
    var reviewStage: Int = 0, // 0 - новая, 1-4 - этапы повторения
    var consecutiveCorrect: Int = 0 // последовательные правильные ответы
)