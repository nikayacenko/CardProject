package com.example.cardproject.ui.info


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.ml.TensorFlowLiteModel
import com.example.cardproject.model.LearningMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val reviewLogRepository: ReviewLogRepository,
    private val tfliteModel: TensorFlowLiteModel
) : ViewModel() {

    private val _isMLReady = MutableStateFlow(false)
    val isMLReady: StateFlow<Boolean> = _isMLReady.asStateFlow()

    private val _logsCount = MutableStateFlow(0)
    val logsCount: StateFlow<Int> = _logsCount.asStateFlow()

    private val _defaultLearningMode = MutableStateFlow(LearningMode.LONG_TERM)
    val defaultLearningMode: StateFlow<LearningMode> = _defaultLearningMode.asStateFlow()

    private val _newCardsPerDay = MutableStateFlow(10)
    val newCardsPerDay: StateFlow<Int> = _newCardsPerDay.asStateFlow()

    init {
        loadData()
        loadPreferences()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isMLReady.value = tfliteModel.isModelReady.value
            _logsCount.value = reviewLogRepository.getLogsCount()
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            // Здесь можно загружать из SharedPreferences
            // Пока оставляем значения по умолчанию
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
        }
    }
}