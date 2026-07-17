// app/src/main/java/com/example/cardproject/data/sync/ServerApi.kt
package com.example.cardproject.data.sync

import com.example.cardproject.model.AuthRequest
import com.example.cardproject.model.AuthResponse
import com.example.cardproject.model.ServerModels
import com.example.cardproject.model.ServerModels.UploadResponse
import com.example.cardproject.model.ServerModels.ModelInfo
import com.example.cardproject.model.ServerModels.TrainResponse
import com.example.cardproject.model.ServerModels.TrainingStatus
import com.example.cardproject.model.ServerModels.LogsCountResponse
import com.example.cardproject.model.User
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ServerApi {

    // ============================================================
    // АУТЕНТИФИКАЦИЯ
    // ============================================================

    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(@Header("Authorization") token: String): Response<User>

    // ============================================================
    // ЛОГИ
    // ============================================================

    @POST("api/logs/upload")
    suspend fun uploadLogs(
        @Header("Authorization") token: String,
        @Body logs: List<ServerModels.LogEntryRequest>
    ): UploadResponse

    @GET("api/logs/count")
    suspend fun getLogsCount(
        @Header("Authorization") token: String
    ): LogsCountResponse

    // ============================================================
    // МОДЕЛЬ
    // ============================================================

    @GET("api/model/latest")
    suspend fun getLatestModel(
        @Header("Authorization") token: String
    ): ModelInfo

    @GET("api/model/download")
    suspend fun downloadModel(
        @Header("Authorization") token: String
    ): ResponseBody

    @POST("api/model/fine-tune")
    suspend fun triggerFineTune(
        @Header("Authorization") token: String
    ): TrainResponse

    @GET("api/model/status")
    suspend fun getTrainingStatus(
        @Header("Authorization") token: String
    ): TrainingStatus

    // ============================================================
    // ЗДОРОВЬЕ
    // ============================================================

    @GET("api/health")
    suspend fun healthCheck(): HealthResponse

    data class HealthResponse(
        val status: String,
        @SerializedName("logs_count")
        val logsCount: Int,
        @SerializedName("model_version")
        val modelVersion: ModelInfo?
    )
}