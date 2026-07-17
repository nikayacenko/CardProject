// app/src/main/java/com/example/cardproject/data/sync/SyncManager.kt
package com.example.cardproject.data.sync

import android.content.Context
import android.util.Log
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.ml.TensorFlowLiteModel
import com.example.cardproject.model.ReviewLog
import com.example.cardproject.model.ServerModels
import com.example.cardproject.model.ServerModels.LogEntryRequest
import com.example.cardproject.utils.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ResponseBody

@Singleton
class SyncManager @Inject constructor(
    private val reviewLogRepository: ReviewLogRepository,
    @ApplicationContext private val context: Context,
    private val tfliteModel: TensorFlowLiteModel,
    private val tokenManager: TokenManager
) {

    private val api = RetrofitClient.instance

    // ============================================================
    // СОСТОЯНИЕ
    // ============================================================

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _isFineTuning = MutableStateFlow(false)
    val isFineTuning: StateFlow<Boolean> = _isFineTuning.asStateFlow()

    private val _fineTuneStatus = MutableStateFlow("Готово")
    val fineTuneStatus: StateFlow<String> = _fineTuneStatus.asStateFlow()

    private val _fineTuneProgress = MutableStateFlow(0f)
    val fineTuneProgress: StateFlow<Float> = _fineTuneProgress.asStateFlow()

    private val _hasNewModel = MutableStateFlow(false)
    val hasNewModel: StateFlow<Boolean> = _hasNewModel.asStateFlow()

    // ============================================================
    // МЕТОДЫ
    // ============================================================

    suspend fun isServerAvailable(): Boolean {
        return try {
            val response = api.healthCheck()
            Log.d("SyncManager", "healthCheck ответ: status=${response.status}")
            response.status == "healthy"
        } catch (e: Exception) {
            Log.e("SyncManager", "Ошибка healthCheck: ${e.message}")
            false
        }
    }

    suspend fun syncLogs(): Boolean {
        if (_isSyncing.value) return false

        val token = tokenManager.getToken()
        if (token == null) {
            Log.e("SyncManager", "Нет токена, пропускаем синхронизацию")
            _fineTuneStatus.value = "❌ Нет токена"
            return false
        }

        try {
            _isSyncing.value = true
            _syncProgress.value = 0f
            _fineTuneStatus.value = "Отправка логов..."

            val logs = reviewLogRepository.getUnsyncedLogs()
            if (logs.isEmpty()) {
                _fineTuneStatus.value = "Нет новых логов"
                _isSyncing.value = false
                return true
            }

            Log.d("SyncManager", "Отправка ${logs.size} логов на сервер")
            _syncProgress.value = 0.3f

            val requests = logs.map { log ->
                LogEntryRequest(
                    cardId = log.cardId,
                    questionType = log.questionType,
                    cardTextLength = log.cardTextLength,
                    hasFormulas = log.hasFormulas,
                    difficultyScore = log.difficultyScore,
                    responseTimeMs = log.responseTimeMs,
                    hourOfDay = log.hourOfDay,
                    dayOfWeek = log.dayOfWeek,
                    fatigueLevel = log.fatigueLevel,
                    cardsReviewedInSession = log.cardsReviewedInSession,
                    totalReviewsBefore = log.totalReviewsBefore,
                    correctRateBefore = log.correctRateBefore,
                    streakCorrectBefore = log.streakCorrectBefore,
                    daysSinceLastReview = log.daysSinceLastReview,
                    linkedCardsCount = log.linkedCardsCount,
                    linkedCardsMastery = log.linkedCardsMastery,
                    wasCorrect = log.wasCorrect,
                    timestamp = log.timestamp
                )
            }

            // 🔥 Вызов API
            val result: ServerModels.UploadResponse = api.uploadLogs("Bearer $token", requests)

            // 🔥 Проверяем поля result
            if (result.status == "ok" && result.saved > 0) {
                reviewLogRepository.markLogsSynced(logs)
                _syncProgress.value = 1f
                _fineTuneStatus.value = "Отправлено ${result.saved} логов"
                Log.d("SyncManager", "✅ Отправлено ${result.saved} логов")
                return true
            } else {
                _fineTuneStatus.value = "Ошибка: ${result.message ?: "неизвестная"}"
                return false
            }

        } catch (e: Exception) {
            Log.e("SyncManager", "Ошибка отправки логов: ${e.message}")
            _fineTuneStatus.value = "Ошибка: ${e.message}"
            return false
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun triggerFineTune(): Boolean {
        if (_isFineTuning.value) return false

        val token = tokenManager.getToken()
        if (token == null) {
            _fineTuneStatus.value = "❌ Нет токена"
            return false
        }

        if (!isServerAvailable()) {
            _fineTuneStatus.value = "❌ Сервер недоступен"
            return false
        }

        try {
            _isFineTuning.value = true
            _fineTuneProgress.value = 0f
            _fineTuneStatus.value = "📤 Отправка логов..."

            val syncSuccess = syncLogs()
            if (!syncSuccess) {
                _fineTuneStatus.value = "❌ Ошибка отправки логов"
                _isFineTuning.value = false
                return false
            }

            _fineTuneStatus.value = "🧠 Запуск дообучения..."
            _fineTuneProgress.value = 0.2f

            // 🔥 БЕЗ Response — просто вызываем
            val response = api.triggerFineTune("Bearer $token")

            if (response.status != "started") {
                _fineTuneStatus.value = "❌ ${response.message}"
                _isFineTuning.value = false
                return false
            }

            _fineTuneStatus.value = "⏳ Дообучение выполняется (1-3 минуты)..."
            _fineTuneProgress.value = 0.3f

            var attempts = 0
            var lastProgress = 0.3f

            while (attempts < 60) {
                delay(5000)
                attempts++

                try {
                    // 🔥 БЕЗ Response — просто вызываем
                    val status = api.getTrainingStatus("Bearer $token")

                    if (status.isRunning) {
                        val progress = 0.3f + (attempts / 60f) * 0.5f
                        if (progress > lastProgress) {
                            lastProgress = progress
                            _fineTuneProgress.value = progress
                        }
                        _fineTuneStatus.value = "⏳ Дообучение... ${status.message}"
                    } else {
                        _fineTuneStatus.value = "📥 Скачивание модели..."
                        _fineTuneProgress.value = 0.8f

                        val downloadSuccess = downloadAndInstallModel { progress ->
                            _fineTuneProgress.value = 0.8f + progress * 0.2f
                            _fineTuneStatus.value = "📥 Скачивание: ${(progress * 100).toInt()}%"
                        }

                        if (downloadSuccess) {
                            _fineTuneStatus.value = "✅ Модель обновлена!"
                            _fineTuneProgress.value = 1f
                            _hasNewModel.value = true
                            _isFineTuning.value = false
                            return true
                        } else {
                            _fineTuneStatus.value = "❌ Ошибка скачивания модели"
                            _isFineTuning.value = false
                            return false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SyncManager", "Ошибка проверки статуса: ${e.message}")
                }
            }

            _fineTuneStatus.value = "⏱️ Превышено время ожидания"
            _isFineTuning.value = false
            return false

        } catch (e: Exception) {
            Log.e("SyncManager", "Ошибка дообучения: ${e.message}")
            _fineTuneStatus.value = "❌ ${e.message}"
            _isFineTuning.value = false
            return false
        }
    }

    suspend fun downloadAndInstallModel(onProgress: (Float) -> Unit = {}): Boolean {
        val token = tokenManager.getToken()
        if (token == null) {
            Log.e("SyncManager", "Нет токена")
            return false
        }

        try {
            // 🔥 БЕЗ Response — просто получаем ModelInfo
            val modelInfo = api.getLatestModel("Bearer $token")
            Log.d("SyncManager", "Скачивание модели v${modelInfo.version}")

            // 🔥 БЕЗ Response — просто получаем ResponseBody
            val responseBody: ResponseBody = api.downloadModel("Bearer $token")

            val tempFile = File(context.cacheDir, "model_temp.tflite")
            tempFile.deleteOnExit()

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val contentLength = responseBody.contentLength()

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            onProgress(totalBytesRead.toFloat() / contentLength)
                        }
                    }
                }
            }

            if (tempFile.length() < 1000) {
                Log.e("SyncManager", "Файл слишком маленький: ${tempFile.length()}")
                tempFile.delete()
                return false
            }

            val modelsDir = File(context.filesDir, "models")
            modelsDir.mkdirs()

            val oldModelFile = File(modelsDir, "forgetting_model.tflite")
            if (oldModelFile.exists()) {
                val backupFile = File(modelsDir, "forgetting_model_backup.tflite")
                oldModelFile.copyTo(backupFile, overwrite = true)
                Log.d("SyncManager", "Создан бэкап старой модели")
            }

            val newModelFile = File(modelsDir, "forgetting_model.tflite")
            tempFile.copyTo(newModelFile, overwrite = true)
            tempFile.delete()

            Log.d("SyncManager", "Модель сохранена: ${newModelFile.absolutePath}")

            val prefs = context.getSharedPreferences("model", Context.MODE_PRIVATE)
            prefs.edit().putInt("model_version", modelInfo.version).apply()

            tfliteModel.updateModel(newModelFile)

            return true

        } catch (e: Exception) {
            Log.e("SyncManager", "Ошибка скачивания: ${e.message}")
            return false
        }
    }

    suspend fun checkForUpdates(): Boolean {
        val token = tokenManager.getToken()
        if (token == null) {
            Log.e("SyncManager", "Нет токена")
            return false
        }

        return try {
            if (!isServerAvailable()) return false

            // 🔥 БЕЗ Response — просто получаем ModelInfo
            val serverInfo = api.getLatestModel("Bearer $token")
            val prefs = context.getSharedPreferences("model", Context.MODE_PRIVATE)
            val localVersion = prefs.getInt("model_version", 0)

            val hasUpdate = serverInfo.version > localVersion
            _hasNewModel.value = hasUpdate

            if (hasUpdate) {
                _fineTuneStatus.value = "Доступна новая модель v${serverInfo.version}"
            }

            hasUpdate
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateModelIfAvailable(): Boolean {
        val hasUpdate = checkForUpdates()
        if (!hasUpdate) {
            _fineTuneStatus.value = "Модель актуальна"
            return false
        }

        _fineTuneStatus.value = "📥 Скачивание обновления..."
        val success = downloadAndInstallModel { progress ->
            _fineTuneStatus.value = "📥 Скачивание: ${(progress * 100).toInt()}%"
        }

        if (success) {
            _fineTuneStatus.value = "✅ Модель обновлена!"
            _hasNewModel.value = false
        }

        return success
    }

    suspend fun getServerLogsCount(): Int {
        val token = tokenManager.getToken()
        if (token == null) {
            Log.e("SyncManager", "Нет токена")
            return 0
        }

        return try {
            val response = api.getLogsCount("Bearer $token")
            response.count
        } catch (e: Exception) {
            Log.e("SyncManager", "Ошибка получения количества логов: ${e.message}")
            0
        }
    }

    suspend fun getTrainingStatus(): ServerModels.TrainingStatus {
        val token = tokenManager.getToken()
        if (token == null) {
            return ServerModels.TrainingStatus(
                isRunning = false,
                message = "Нет токена"
            )
        }

        return try {
            api.getTrainingStatus("Bearer $token")
        } catch (e: Exception) {
            Log.e("SyncManager", "Ошибка получения статуса: ${e.message}")
            ServerModels.TrainingStatus(
                isRunning = false,
                message = "Ошибка: ${e.message}"
            )
        }
    }
}