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
    var consecutiveCorrect: Int = 0, // последовательные правильные ответы

    //метрики сложности
    val difficultyScore: Float = 0.5f,
    val isFormula: Boolean = false,
    val wordCount: Int = 0,
    val questionType: String = "FACT", // FACT, PROOF

    //поведенческие факторы
    val averageResponseTimeMs: Long = 0,
    val totalReviews: Int = 0,
    val lastResponseTimeMs: Long = 0, // Время последнего ответа (усталость)
    var successRate: Float = 0f,

    //связь
    val linkedCardIds: String = "", // "12,45,67"

    var lastFiveResults: String = "",

    //управление алгоритмом
    val algorithmType: String = "ML", // Для сравнения в курсовой (SM2 vs ML)
    val masteryLevel: Float = 0f,
    var lastPredictedProbability: Float = 0f // Уверенность ИИ в вашем ответе
)