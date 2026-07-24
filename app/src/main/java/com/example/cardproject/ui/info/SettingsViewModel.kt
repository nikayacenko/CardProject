package com.example.cardproject.ui.info


import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.data.sync.SyncManager
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.ml.TensorFlowLiteModel
import com.example.cardproject.model.LearningMode
import dagger.hilt.android.internal.Contexts.getApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val reviewLogRepository: ReviewLogRepository,
    private val tfliteModel: TensorFlowLiteModel,
    private val syncManager: SyncManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isMLReady = MutableStateFlow(false)
    val isMLReady: StateFlow<Boolean> = _isMLReady.asStateFlow()

    private val _logsCount = MutableStateFlow(0)
    val logsCount: StateFlow<Int> = _logsCount.asStateFlow()

    private val _defaultLearningMode = MutableStateFlow(LearningMode.LONG_TERM)
    val defaultLearningMode: StateFlow<LearningMode> = _defaultLearningMode.asStateFlow()

    private val _newCardsPerDay = MutableStateFlow(10)
    val newCardsPerDay: StateFlow<Int> = _newCardsPerDay.asStateFlow()

    private val _minCardsForSession = MutableStateFlow(10)
    val minCardsForSession: StateFlow<Int> = _minCardsForSession.asStateFlow()

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val _isFineTuning = MutableStateFlow(false)
    val isFineTuning: StateFlow<Boolean> = _isFineTuning.asStateFlow()

    private val _fineTuneStatus = MutableStateFlow("Готово")
    val fineTuneStatus: StateFlow<String> = _fineTuneStatus.asStateFlow()

    private val _fineTuneProgress = MutableStateFlow(0f)
    val fineTuneProgress: StateFlow<Float> = _fineTuneProgress.asStateFlow()

    private val _serverLogsCount = MutableStateFlow(0)
    val serverLogsCount: StateFlow<Int> = _serverLogsCount.asStateFlow()

    private val _hasNewModel = MutableStateFlow(false)
    val hasNewModel: StateFlow<Boolean> = _hasNewModel.asStateFlow()

    init {
        loadData()
        loadPreferences()
        loadServerData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isMLReady.value = tfliteModel.isModelReady.value
            _logsCount.value = reviewLogRepository.getLogsCount()
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            _minCardsForSession.value = prefs.getInt("min_cards_for_session", 10)
        }
    }
    fun setMinCardsForSession(count: Int) {
        if (count in 1..30) {
            _minCardsForSession.value = count
            viewModelScope.launch {
                prefs.edit().putInt("min_cards_for_session", count).apply()
            }
        }
    }
    fun setDefaultLearningMode(mode: LearningMode) {
        _defaultLearningMode.value = mode
        // Сохранить в SharedPreferences
    }

    fun setNewCardsPerDay(count: Int) {
        if (count in 1..50) {
            _newCardsPerDay.value = count
            // Сохранить в SharedPreferences
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isMLReady.value = tfliteModel.isModelReady.value
            _logsCount.value = reviewLogRepository.getLogsCount()
            refreshServerData()
        }
    }

    /**
     * Загрузка данных с сервера
     */
    private fun loadServerData() {
        viewModelScope.launch {
            try {
                val isAvailable = syncManager.isServerAvailable()
                Log.d("SettingsVM", "Сервер доступен: $isAvailable")

                if (isAvailable) {
                    _fineTuneStatus.value = "Сервер доступен"
                    updateServerLogsCount()
                    checkForUpdates()
                } else {
                    _fineTuneStatus.value = "Сервер недоступен"
                }
            } catch (e: Exception) {
                Log.e("SettingsVM", "Ошибка: ${e.message}")
                _fineTuneStatus.value = "Ошибка подключения"
            }
        }
    }
    /**
     * Обновление количества логов на сервере
     */
    private suspend fun updateServerLogsCount() {
        try {
            val count = syncManager.getServerLogsCount()
            _serverLogsCount.value = count
        } catch (e: Exception) {
            // Игнорируем
        }
    }

    /**
     * Проверка обновлений модели
     */
    suspend fun checkForUpdates(): Boolean {
        val hasUpdate = syncManager.checkForUpdates()
        _hasNewModel.value = hasUpdate
        if (hasUpdate) {
            _fineTuneStatus.value = "Доступна новая модель!"
        }
        return hasUpdate
    }

    /**
     * Запуск дообучения
     */
    fun startFineTune() {
        viewModelScope.launch {
            if (_isFineTuning.value) {
                _fineTuneStatus.value = "Дообучение уже запущено"
                return@launch
            }

            _isFineTuning.value = true
            _fineTuneProgress.value = 0f
            _fineTuneStatus.value = "Запуск дообучения..."

            try {
                // 1. Проверяем соединение
                if (!syncManager.isServerAvailable()) {
                    _fineTuneStatus.value = "Сервер недоступен"
                    _isFineTuning.value = false
                    return@launch
                }

                // 2. Синхронизируем логи
                _fineTuneStatus.value = "Отправка логов..."
                _fineTuneProgress.value = 0.1f
                syncManager.syncLogs()

                // 3. Запускаем дообучение
                _fineTuneStatus.value = "Дообучение..."
                _fineTuneProgress.value = 0.2f

                val success = syncManager.triggerFineTune()
                _fineTuneProgress.value = 0.8f

                if (!success) {
                    _fineTuneStatus.value = "Ошибка дообучения"
                    _isFineTuning.value = false
                    return@launch
                }

                // 4. Ждём завершения (мониторинг)
                _fineTuneStatus.value = "Ожидание завершения..."
                _fineTuneProgress.value = 0.8f

                var completed = false
                var attempts = 0
                while (attempts < 60 && !completed) {
                    attempts++
                    val status = syncManager.getTrainingStatus()
                    if (!status.isRunning) {
                        completed = true
                    }
                }

                if (!completed) {
                    _fineTuneStatus.value = "Превышено время ожидания"
                    _isFineTuning.value = false
                    return@launch
                }

                // 5. Скачиваем модель
                _fineTuneStatus.value = "Скачивание модели..."
                _fineTuneProgress.value = 0.85f

                val downloadSuccess = syncManager.downloadAndInstallModel { progress ->
                    _fineTuneProgress.value = 0.85f + progress * 0.15f
                }

                if (downloadSuccess) {
                    _fineTuneStatus.value = "Модель обновлена!"
                    _fineTuneProgress.value = 1f
                    _hasNewModel.value = false
                    _isMLReady.value = tfliteModel.isModelReady.value

                    _isMLReady.value = tfliteModel.isModelReady.value
                    Log.d("SettingsVM", "✅ Модель загружена, isMLReady=${_isMLReady.value}")
                    // Обновляем количество логов на сервере
                    updateServerLogsCount()
                } else {
                    _fineTuneStatus.value = "Ошибка скачивания модели"
                }

            } catch (e: Exception) {
                _fineTuneStatus.value = "❌ ${e.message}"
                e.printStackTrace()
            } finally {
                _isFineTuning.value = false
            }
        }
    }

    /**
     * Принудительная проверка обновлений (для кнопки)
     */
    fun refreshServerData() {
        viewModelScope.launch {
            _fineTuneStatus.value = "Проверка..."
            try {
                if (syncManager.isServerAvailable()) {
                    updateServerLogsCount()
                    val hasUpdate = checkForUpdates()
                    _fineTuneStatus.value = if (hasUpdate) {
                        "Доступна новая модель"
                    } else {
                        "Модель актуальна"
                    }
                } else {
                    _fineTuneStatus.value = "❌ Сервер недоступен"
                }
            } catch (e: Exception) {
                _fineTuneStatus.value = "❌ ${e.message}"
            }
        }
    }
}