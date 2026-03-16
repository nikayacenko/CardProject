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
        // Базовая усталость от количества карточек (0.01 за каждые 10 карт)
        val volumeFatigue = cardsReviewed / 1000f

        // Усталость от замедления
        val slowdownFatigue = if (avgTime > 0) {
            ((lastResponseTime.toFloat() / avgTime) - 1f).coerceIn(0f, 0.5f)
        } else 0f

        // Штраф за ошибку
        val errorPenalty = if (!wasCorrect) 0.05f else 0f

        return (volumeFatigue + slowdownFatigue + errorPenalty).coerceIn(0f, 1f)
    }
}