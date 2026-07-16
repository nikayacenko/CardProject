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
}