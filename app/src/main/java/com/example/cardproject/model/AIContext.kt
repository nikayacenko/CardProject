// File: model/AIContext.kt
package com.example.cardproject.model

import java.util.Calendar

data class AIContext(
    val sessionStartTime: Long,
    val cardsReviewedInSession: Int,
    val currentHour: Int,
    val dayOfWeek: Int,
    val userFatigueLevel: Float,
    val averageResponseTimeMs: Long,
    val consecutiveCorrectInSession: Int,
    val consecutiveIncorrectInSession: Int,
    val learningMode: LearningMode
) {
    companion object {
        fun create(
            sessionStartTime: Long = System.currentTimeMillis(),
            cardsReviewedInSession: Int = 0,
            learningMode: LearningMode = LearningMode.LONG_TERM
        ): AIContext {
            val calendar = Calendar.getInstance()

            return AIContext(
                sessionStartTime = sessionStartTime,
                cardsReviewedInSession = cardsReviewedInSession,
                currentHour = calendar.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
                userFatigueLevel = 0.0f,
                averageResponseTimeMs = 0,
                consecutiveCorrectInSession = 0,
                consecutiveIncorrectInSession = 0,
                learningMode = learningMode
            )
        }
    }

    fun updateWithReview(
        wasCorrect: Boolean,
        responseTimeMs: Long
    ): AIContext {
        val newCardsReviewed = cardsReviewedInSession + 1
        val newConsecutiveCorrect = if (wasCorrect) consecutiveCorrectInSession + 1 else 0
        val newConsecutiveIncorrect = if (!wasCorrect) consecutiveIncorrectInSession + 1 else 0

        // Обновляем среднее время ответа
        val newAvgTime = if (cardsReviewedInSession > 0) {
            ((averageResponseTimeMs * cardsReviewedInSession) + responseTimeMs) / newCardsReviewed
        } else {
            responseTimeMs
        }

        // Рассчитываем усталость
        val fatigueLevel = calculateFatigueLevel(
            newCardsReviewed,
            responseTimeMs,
            newAvgTime,
            wasCorrect
        )

        return copy(
            cardsReviewedInSession = newCardsReviewed,
            averageResponseTimeMs = newAvgTime,
            userFatigueLevel = fatigueLevel,
            consecutiveCorrectInSession = newConsecutiveCorrect,
            consecutiveIncorrectInSession = newConsecutiveIncorrect
        )
    }

    private fun calculateFatigueLevel(
        cardsReviewed: Int,
        lastResponseTime: Long,
        avgTime: Long,
        wasCorrect: Boolean
    ): Float {
        // 1. Фактор времени суток (Циркадный ритм)
        // Усталость не начинается с 0, если ты сел учиться в 2 часа ночи
        val timeFactor = when (currentHour) {
            in 0..5 -> 0.4f    // Глубокая ночь — мозг уже уставший
            in 22..23 -> 0.2f  // Поздний вечер
            in 8..11 -> 0.0f   // Утренний пик бодрости
            else -> 0.1f       // День
        }

        // 2. Агрессивный фактор объема (1.5% за карту)
        // В мобильном приложении 30-50 карт — это уже серьезная нагрузка
        val volumeFatigue = cardsReviewed * 0.015f

        // 3. Когнитивный фактор (замедление реакции)
        // Если ты стал отвечать в 1.5 раза медленнее среднего — это +15% к усталости
        val speedFactor = if (avgTime > 0) {
            ((lastResponseTime.toFloat() / avgTime) - 1.1f).coerceIn(0f, 0.3f)
        } else 0f

        // 4. Эмоциональное выгорание (ошибки)
        // Каждая ошибка в сессии добавляет 4% к усталости (стресс)
        val errorFactor = if (!wasCorrect) 0.04f else 0f

        // Суммируем текущее состояние с накопленным
        // Используем плавное наслоение, чтобы шкала не прыгала слишком резко
        val targetFatigue = timeFactor + volumeFatigue + speedFactor + errorFactor

        return targetFatigue.coerceIn(0f, 1f)
    }
}