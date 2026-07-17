// app/src/main/java/com/example/cardproject/model/ServerModels.kt
package com.example.cardproject.model

import com.google.gson.annotations.SerializedName

/**
 * Модели для взаимодействия с сервером
 */
object ServerModels {

    // ============================================================
    // ЗАГРУЗКА ЛОГОВ
    // ============================================================

    data class UploadResponse(
        val status: String,
        val saved: Int,
        val message: String? = null
    )

    // ============================================================
    // УПРАВЛЕНИЕ МОДЕЛЬЮ
    // ============================================================

    data class ModelInfo(
        val version: Int,
        val url: String,
        @SerializedName("size_mb")
        val sizeMb: Float,
        @SerializedName("created_at")
        val createdAt: String,
        val accuracy: Float? = null,
        val mae: Float? = null,
        val samples: Int? = null
    )

    data class TrainResponse(
        val status: String,
        val message: String
    )

    data class TrainingStatus(
        @SerializedName("is_running")
        val isRunning: Boolean,
        val progress: Float? = null,
        val message: String
    )

    data class LogsCountResponse(
        val count: Int
    )

    /**
     * Модель для отправки логов на сервер (snake_case)
     */
    data class LogEntryRequest(
        @SerializedName("card_id")
        val cardId: Long,

        @SerializedName("question_type")
        val questionType: String,

        @SerializedName("card_text_length")
        val cardTextLength: Int,

        @SerializedName("has_formulas")
        val hasFormulas: Boolean,

        @SerializedName("difficulty_score")
        val difficultyScore: Float,

        @SerializedName("response_time_ms")
        val responseTimeMs: Long,

        @SerializedName("hour_of_day")
        val hourOfDay: Int,

        @SerializedName("day_of_week")
        val dayOfWeek: Int,

        @SerializedName("fatigue_level")
        val fatigueLevel: Float,

        @SerializedName("cards_reviewed_in_session")
        val cardsReviewedInSession: Int,

        @SerializedName("total_reviews_before")
        val totalReviewsBefore: Int,

        @SerializedName("correct_rate_before")
        val correctRateBefore: Float,

        @SerializedName("streak_correct_before")
        val streakCorrectBefore: Int,

        @SerializedName("days_since_last_review")
        val daysSinceLastReview: Int,

        @SerializedName("linked_cards_count")
        val linkedCardsCount: Int,

        @SerializedName("linked_cards_mastery")
        val linkedCardsMastery: Float,

        @SerializedName("was_correct")
        val wasCorrect: Boolean,

        val timestamp: Long
    )
}