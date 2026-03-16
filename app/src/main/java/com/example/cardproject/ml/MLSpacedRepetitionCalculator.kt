// File: ml/MLSpacedRepetitionCalculator.kt
package com.example.cardproject.ml

import com.example.cardproject.model.AIContext
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.ReviewLog
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.model.MLPrediction
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MLSpacedRepetitionCalculator @Inject constructor(
    private val tfliteModel: TensorFlowLiteModel,
    private val reviewLogRepository: ReviewLogRepository
) {

    // Базовые интервалы (в днях) для разных режимов
    private val BASE_INTERVALS = mapOf(
        LearningMode.LONG_TERM to 1.0,
        LearningMode.SHORT_TERM to 0.042 // 1 час
    )

    // Максимальные интервалы
    private val MAX_INTERVALS = mapOf(
        LearningMode.LONG_TERM to 365.0, // 1 год
        LearningMode.SHORT_TERM to 7.0    // 1 неделя
    )

    /**
     * Главный метод расчета следующего повторения
     */
    suspend fun calculateNextReview(
        card: Card,
        context: AIContext,
        learningMode: LearningMode,
        quality: Int,
        responseTimeMs: Long
    ): Card {
        // 1. Получаем текущий процент успеха карточки
        val correctRate = reviewLogRepository.getCorrectRateForCard(card.id)

        // 2. Получаем среднюю выученность связанных карточек
        val linkedMastery = calculateLinkedCardsMastery(card)

        // 3. Создаем лог с признаками
        val log = ReviewLog.from(
            card = card,
            context = context,
            quality = quality,
            responseTimeMs = responseTimeMs,
            correctRateBefore = correctRate,
            linkedCardsMastery = linkedMastery
        )

        // 4. Сохраняем лог в БД
//        reviewLogRepository.insertLog(log)

        // 5. Если ответ неправильный - сбрасываем прогресс
        if (quality == 0) {
            return handleIncorrectAnswer(card, learningMode)
        }

        // 6. Получаем предсказание от ML модели
        val prediction = tfliteModel.predict(log)

        // 7. Рассчитываем следующий интервал
        val nextInterval = calculateInterval(
            card = card,
            prediction = prediction,
            learningMode = learningMode,
            fatigue = context.userFatigueLevel
        )

        // 8. Обновляем карточку
        val now = System.currentTimeMillis()
        // Используем coerceAtLeast, чтобы интервал никогда не был нулевым
        val safeInterval = nextInterval.coerceAtLeast(BASE_INTERVALS[learningMode] ?: 0.042)
        val intervalMs = (safeInterval * 24 * 60 * 60 * 1000L).toLong() // Добавлен L для Long

        return card.copy(
            lastReviewed = now,
            nextReview = now + intervalMs,
            interval = safeInterval,
            reviewStage = card.reviewStage + 1,
            consecutiveCorrect = card.consecutiveCorrect + 1,
            easeFactor = calculateEaseFactor(card, prediction)
        )
    }

    /**
     * Расчет интервала на основе ML предсказания
     */
    private fun calculateInterval(
        card: Card,
        prediction: MLPrediction,
        learningMode: LearningMode,
        fatigue: Float
    ): Double {
        val baseInterval = BASE_INTERVALS[learningMode] ?: 1.0
        val maxInterval = MAX_INTERVALS[learningMode] ?: 365.0

        // Если модель неуверена - используем SM-2 логику
        if (prediction.needsMoreData || prediction.confidence < 0.5f) {
            return calculateFallbackInterval(card, learningMode)
        }

        // Оптимальный интервал от модели (уже нормализован)
        var interval = prediction.optimalIntervalDays.toDouble()

        // Корректировка на усталость
        if (fatigue > 0.7f) {
            interval *= (1.0 - fatigue * 0.3) // Уменьшаем интервал при усталости
        }

        // Корректировка на основе вероятности забывания
        val retentionTarget = when (learningMode) {
            LearningMode.LONG_TERM -> 0.9   // Хотим 90% запоминания
            LearningMode.SHORT_TERM -> 0.7  // 70% достаточно для краткосрочного
        }

        // Если вероятность забывания выше цели - уменьшаем интервал
        if (prediction.forgettingProbability > retentionTarget) {
            interval *= (retentionTarget / prediction.forgettingProbability)
        }

        // Ограничиваем интервал
        return interval.coerceIn(baseInterval, maxInterval)
    }

    /**
     * Запасной вариант (SM-2 подобный)
     */
    private fun calculateFallbackInterval(card: Card, learningMode: LearningMode): Double {
        return when (learningMode) {
            LearningMode.LONG_TERM -> {
                when (card.reviewStage) {
                    0 -> 1.0
                    1 -> 3.0
                    2 -> 7.0
                    3 -> 14.0
                    4 -> 30.0
                    else -> 60.0
                }
            }
            LearningMode.SHORT_TERM -> {
                when (card.reviewStage) {
                    0 -> 0.042 // 1 час
                    1 -> 0.125 // 3 часа
                    2 -> 0.333 // 8 часов
                    3 -> 1.0   // 1 день
                    4 -> 3.0   // 3 дня
                    else -> 7.0
                }
            }
        }
    }

    /**
     * Обработка неправильного ответа
     */
    private fun handleIncorrectAnswer(card: Card, learningMode: LearningMode): Card {
        val newInterval = when (learningMode) {
            LearningMode.LONG_TERM -> max(1.0, (card.interval * 0.5))
            LearningMode.SHORT_TERM -> max(1.0, (card.interval * 0.5))
        }

        val now = System.currentTimeMillis()
        val intervalMs = when (learningMode) {
            LearningMode.LONG_TERM -> newInterval * 24 * 60 * 60 * 1000L
            LearningMode.SHORT_TERM -> newInterval * 60 * 60 * 1000L
        }
            .toLong()

        return card.copy(
            lastReviewed = now,
            nextReview = now + intervalMs,
            interval = newInterval,
            reviewStage = max(0, card.reviewStage - 1),
            consecutiveCorrect = 0,
            easeFactor = max(1.3, card.easeFactor - 0.2)
        )
    }

    /**
     * Расчет фактора легкости
     */
    private fun calculateEaseFactor(card: Card, prediction: MLPrediction): Double {
        val baseEase = card.easeFactor + (0.1 - (5 - 1) * (0.08 + (5 - 1) * 0.02))

        // Корректировка на основе сложности карточки
        val complexityPenalty = prediction.forgettingProbability * 0.2

        return max(1.3, baseEase - complexityPenalty)
    }

    /**
     * Расчет средней выученности связанных карточек
     */
    private suspend fun calculateLinkedCardsMastery(card: Card): Float {
        val linkedIds = extractLinkedIds(card.linkedCardIds)
        if (linkedIds.isEmpty()) return 0.5f

        var totalMastery = 0.0f
        for (linkedId in linkedIds) {
            val correctRate = reviewLogRepository.getCorrectRateForCard(linkedId)
            totalMastery += correctRate
        }

        return totalMastery / linkedIds.size
    }

    private fun extractLinkedIds(linkData: String): List<Long> {
        return if (linkData.isNotBlank()) {
            linkData.split(",")
                .mapNotNull { it.toLongOrNull() }
        } else emptyList()
    }
}