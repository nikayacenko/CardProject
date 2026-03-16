package com.example.cardproject.model

data class MLPrediction(
    val forgettingProbability: Float,  // 0-1, вероятность забывания
    val optimalIntervalDays: Float,    // Оптимальный интервал в днях
    val confidence: Float,             // Уверенность модели (0-1)
    val needsMoreData: Boolean         // Нужно больше данных для этой карточки
)