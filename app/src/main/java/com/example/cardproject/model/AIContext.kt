package com.example.cardproject.model

data class AIContext(
    val cardsReviewedInSession: Int = 0,
    val userFatigueLevel: Float = 0f,    // Результат расчета
    val currentHour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
    val breakDurationMinutes: Int = 0
)