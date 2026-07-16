// app/src/main/java/com/example/cardproject/data/sync/ServerApi.kt
package com.example.cardproject.data.sync

import com.example.cardproject.model.ReviewLog
import com.example.cardproject.model.ServerModels.UploadResponse
import com.example.cardproject.model.ServerModels.ModelInfo
import com.example.cardproject.model.ServerModels.TrainResponse
import com.example.cardproject.model.ServerModels.TrainingStatus
import com.example.cardproject.model.ServerModels.LogsCountResponse
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ServerApi {

    // ============================================================
    // ЛОГИ
    // ============================================================

    /**
     * Отправить логи на сервер
     */
    @POST("api/logs/upload")
    suspend fun uploadLogs(
        @Body logs: List<ReviewLog>
    ): UploadResponse

    /**
     * Получить количество логов на сервере
     */
    @GET("api/logs/count")
    suspend fun getLogsCount(): LogsCountResponse

    // ============================================================
    // МОДЕЛЬ
    // ============================================================

    /**
     * Получить информацию о последней модели
     */
    @GET("api/model/latest")
    suspend fun getLatestModel(): ModelInfo

    /**
     * Скачать последнюю модель
     */
    @GET("api/model/download")
    suspend fun downloadModel(): Response<ResponseBody>

    /**
     * Запустить дообучение модели
     */
    @POST("api/model/fine-tune")
    suspend fun triggerFineTune(): TrainResponse

    /**
     * Получить статус дообучения
     */
    @GET("api/model/status")
    suspend fun getTrainingStatus(): TrainingStatus

    // ============================================================
    // ЗДОРОВЬЕ СЕРВЕРА
    // ============================================================

    /**
     * Проверка работоспособности
     */
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