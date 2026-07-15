package com.example.cardproject.ui.info


import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        }
    }
}