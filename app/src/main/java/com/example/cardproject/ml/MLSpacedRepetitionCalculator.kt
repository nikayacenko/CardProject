// File: ml/MLSpacedRepetitionCalculator.kt
package com.example.cardproject.ml

import com.example.cardproject.model.AIContext
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.ReviewLog
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.model.MLPrediction
import com.example.cardproject.model.QuestionType
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
        val virtualNow = context.sessionStartTime
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
            return handleIncorrectAnswer(card, learningMode,virtualNow)
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

        // 1. Если модель не уверена, используем РОСТ по SM-2 (чтобы не стоять на месте)
        if (prediction.needsMoreData || prediction.confidence < 0.4f) {
            return calculateFallbackInterval(card, learningMode)
        }

        val currentInterval = maxOf(card.interval, baseInterval)

        // 2. АГРЕССИВНЫЙ МНОЖИТЕЛЬ ML
        // Поднимаем диапазон: от 1.8 (для сложных) до 3.5 (для легких)
        // Именно это позволит перегнать Fallback (у которого обычно 1.5-2.0)
        var mlMultiplier = (prediction.optimalIntervalDays.toDouble() * 1.2).coerceIn(1.8, 3.5)

        // 3. ФАКТОР СЛОЖНОСТИ
        val isHard = card.questionType == "PROOF" || card.difficultyScore > 0.7f
        val difficultyFactor = if (isHard) 0.75 else 1.2 // Легким карточкам даем ускорение

        // 4. БОНУС ЗА СТАБИЛЬНОСТЬ (STREAK)
        // Если 4 раза ответил верно — карточка должна "улетать"
        val streakBonus = when {
            card.consecutiveCorrect >= 4 -> 1.6
            card.consecutiveCorrect >= 2 -> 1.3
            else -> 1.0
        }

        // 5. ИТОГОВЫЙ РАСЧЕТ
        var interval = currentInterval * mlMultiplier * difficultyFactor * streakBonus

        // 6. КОРРЕКТИРОВКА РЕЖИМА
        if (learningMode == LearningMode.SHORT_TERM) {
            interval *= 0.5 // Интенсивный режим — повторяем чаще
        }

        // 7. ШТРАФ ЗА УСТАЛОСТЬ (только если она реально высокая)
        if (fatigue > 0.6f) {
            interval *= (1.0 - (fatigue - 0.6) * 0.5)
        }

        // 8. ЗАЩИТА ОТ ЗАСТОЯ
        // Если после всех расчетов интервал не вырос хотя бы в 1.4 раза —
        // принудительно ускоряем (кроме случаев ошибки)
        val minGrowth = 1.4
        if (interval < currentInterval * minGrowth) {
            interval = currentInterval * minGrowth
        }

        val result = interval.coerceIn(baseInterval, maxInterval)
        return result

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
                    0 -> 0.042   // 1 час
                    1 -> 0.125   // 3 часа
                    2 -> 0.25    // 6 часов
                    3 -> 0.5     // 12 часов
                    4 -> 1.0     // 1 день
                    else -> 3.0  // 3 дня
                }
            }
        }
    }

    /**
     * Обработка неправильного ответа
     */
    private fun handleIncorrectAnswer(card: Card, learningMode: LearningMode,virtualNow: Long): Card {
        val newInterval = when (learningMode) {
            LearningMode.LONG_TERM -> max(1.0, (card.interval * 0.5))
            LearningMode.SHORT_TERM -> max(0.042, (card.interval * 0.5))
        }

        val now = System.currentTimeMillis()
        val intervalMs = when (learningMode) {
            LearningMode.LONG_TERM -> (newInterval * 24 * 60 * 60 * 1000L).toLong()
            LearningMode.SHORT_TERM -> (newInterval * 24 * 60 * 60 * 1000L).toLong()
        }

        return card.copy(
            lastReviewed = virtualNow,
            nextReview = virtualNow + intervalMs,
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
        val baseEase = card.easeFactor

        // Бонус за правильный ответ (чем ниже вероятность забывания, тем больше бонус)
        val successBonus = (1.0 - prediction.forgettingProbability) * 0.3

        // Штраф за сложность карточки
        val complexityPenalty = prediction.forgettingProbability * 0.1

        val newEase = baseEase + successBonus - complexityPenalty

        return newEase.coerceIn(1.3, 2.5)
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
    suspend fun calculateNextReviewWithoutSaving(
        card: Card,
        context: AIContext,
        learningMode: LearningMode,
        quality: Int,
        responseTimeMs: Long
    ): Card {

        // 1. Получаем текущий процент успеха карточки
        val correctRate = card.successRate
        val virtualNow = context.sessionStartTime
        // 2. Создаем лог с признаками (без сохранения)
        val log = ReviewLog.from(
            card = card,
            context = context,
            quality = quality,
            responseTimeMs = responseTimeMs,
            correctRateBefore = correctRate,
            linkedCardsMastery = 0.5f
        )

        // 3. Если ответ неправильный - сбрасываем прогресс
        if (quality == 0) {
            return handleIncorrectAnswer(card, learningMode, virtualNow)
        }

        // 4. Получаем предсказание от ML модели
        val prediction = if (tfliteModel.isModelReady.value) {
            tfliteModel.predict(log)
        } else {
            // Модель не готова - используем fallback
            MLPrediction(0.5f, 1.0f, 0.0f, true)
        }

        // 5. Рассчитываем следующий интервал
        val nextInterval = calculateIntervalWithoutSaving(
            card = card,
            prediction = prediction,
            learningMode = learningMode,
            fatigue = context.userFatigueLevel,
            responseTimeMs
        )
        val safeInterval = nextInterval.coerceAtLeast(0.1)
        // 6. Обновляем карточку
        val intervalMs = when (learningMode) {
            LearningMode.LONG_TERM -> (safeInterval * 24 * 60 * 60 * 1000L).toLong()
            LearningMode.SHORT_TERM -> (safeInterval * 60 * 60 * 1000L).toLong()
        }

        return card.copy(
            lastReviewed = virtualNow,
            nextReview = virtualNow + intervalMs,
            interval = nextInterval,
            reviewStage = card.reviewStage + 1,
            consecutiveCorrect = card.consecutiveCorrect + 1,
            easeFactor = calculateEaseFactor(card, prediction),
            totalReviews = card.totalReviews + 1
        )
    }

    private fun calculateIntervalWithoutSaving1(
        card: Card,
        prediction: MLPrediction,
        learningMode: LearningMode,
        fatigue: Float
    ): Double {
        val baseInterval = BASE_INTERVALS[learningMode] ?: 1.0
        val maxInterval = MAX_INTERVALS[learningMode] ?: 365.0
// ДИАГНОСТИКА
//        println("🔍 DIAGNOSTICS:")
//        println("   needsMoreData: ${prediction.needsMoreData}")
//        println("   confidence: ${prediction.confidence}")
//        println("   raw optimalIntervalDays: ${prediction.optimalIntervalDays}")
//        println("   forgettingProbability: ${prediction.forgettingProbability}")

        // Если модель неуверена - используем SM-2 логику
        if (prediction.needsMoreData || prediction.confidence < 0.4f) {
            val fallback = calculateFallbackInterval(card, learningMode)
            println("   → USING FALLBACK: $fallback days")
            return fallback
        }

        var predictedInterval = prediction.optimalIntervalDays.toDouble()
        // Оптимальный интервал от модели
//        var interval = prediction.optimalIntervalDays.toDouble()
        //2. ДОБАВЬ ЭТО: Множитель роста (Bonus за стабильность)
        // Если модель "тормозит", мы сами разгоняем интервал на основе успешных серий
//        val growthFactor = when {
//            card.consecutiveCorrect >= 4 -> 2.2  // Прыжок для отличников
//            card.consecutiveCorrect >= 2 -> 1.5  // Уверенный рост
//            else -> 1.3                          // Минимальный рост при успехе
//        }

        val currentInterval = maxOf(card.interval, baseInterval)

        // Получаем множитель из предсказания модели
        var predictedMultiplier = prediction.optimalIntervalDays.toDouble()
        val mlMultiplier = (prediction.optimalIntervalDays / 1.5).coerceIn(1.3, 2.5)
        // Ограничиваем множитель разумными пределами
        predictedMultiplier = when (learningMode) {
            LearningMode.LONG_TERM -> predictedMultiplier.coerceIn(1.2, 2.5)
            LearningMode.SHORT_TERM -> predictedMultiplier.coerceIn(1.1, 1.8)
        }

        // Корректировка на сложность карточки
        val isHardTask = card.questionType.equals("PROOF") || card.difficultyScore > 0.7f
        val difficultyFactor = if (isHardTask) 0.9 else 1.1

        // Бонус за серию правильных ответов
        val streakBonus = when {
            card.consecutiveCorrect >= 4 -> 1.4
            card.consecutiveCorrect >= 2 -> 1.2
            else -> 1.0
        }

        // Для SHORT_TERM режима уменьшаем все множители
        val modeFactor = when (learningMode) {
            LearningMode.LONG_TERM -> 1.0
            LearningMode.SHORT_TERM -> 0.7
        }

        // Рассчитываем новый интервал (УМНОЖАЕМ, а не перезаписываем!)
        var interval = currentInterval * predictedMultiplier * difficultyFactor * streakBonus * modeFactor

        // Корректировка на усталость
        val fatigueReduction = (fatigue * 0.3).coerceIn(0.0, 0.5)
        interval *= (1.0 - fatigueReduction)

        // Корректировка на вероятность забывания
        if (prediction.forgettingProbability > 0.3f) {
            interval *= (1.0 - prediction.forgettingProbability * 0.3)
        }

        // Гарантируем минимальный рост для SHORT_TERM
        if (learningMode == LearningMode.SHORT_TERM && interval <= currentInterval * 1.05) {
            interval = currentInterval * 1.2
        }

        val result = interval.coerceIn(baseInterval, maxInterval)
        println("   FINAL INTERVAL: $result days")
        return result
    }
    private fun calculateIntervalWithoutSaving(
        card: Card,
        prediction: MLPrediction,
        learningMode: LearningMode,
        fatigue: Float,
        responseTimeMs: Long
    ): Double {
        val baseInterval = BASE_INTERVALS[learningMode] ?: 1.0
        val maxInterval = MAX_INTERVALS[learningMode] ?: 365.0

        // 1. Если модель не уверена, используем РОСТ по SM-2 (чтобы не стоять на месте)
        if (prediction.needsMoreData || prediction.confidence < 0.4f) {
            return calculateFallbackInterval(card, learningMode)
        }

        val currentInterval = maxOf(card.interval, baseInterval)

        // 2. АГРЕССИВНЫЙ МНОЖИТЕЛЬ ML
        // Поднимаем диапазон: от 1.8 (для сложных) до 3.5 (для легких)
        // Именно это позволит перегнать Fallback (у которого обычно 1.5-2.0)
        var mlMultiplier = (prediction.optimalIntervalDays.toDouble() * 1.2).coerceIn(1.8, 3.5)

        // 3. ФАКТОР СЛОЖНОСТИ
        val isHard = card.questionType == "PROOF" || card.difficultyScore > 0.7f
        val difficultyFactor = if (isHard) 0.75 else 1.2 // Легким карточкам даем ускорение

        // 4. БОНУС ЗА СТАБИЛЬНОСТЬ (STREAK)
        // Если 4 раза ответил верно — карточка должна "улетать"
        val streakBonus = when {
            card.consecutiveCorrect >= 4 -> 1.6
            card.consecutiveCorrect >= 2 -> 1.3
            else -> 1.0
        }

        // 5. ИТОГОВЫЙ РАСЧЕТ
        var interval = currentInterval * mlMultiplier * difficultyFactor * streakBonus
        val speedModifier = when {
            responseTimeMs <= 3000 -> 1.1      // Бонус за мгновенный ответ (автоматизм)
            responseTimeMs <= 8000 -> 1.0      // Нормальное время (уверенное знание)
            responseTimeMs <= 15000 -> 0.85    // Заминка (знание неустойчивое)
            else -> 0.7                        // Долгое раздумье (почти забыл)
        }
        interval *= speedModifier
        // 6. КОРРЕКТИРОВКА РЕЖИМА
        if (learningMode == LearningMode.SHORT_TERM) {
            interval *= 0.5 // Интенсивный режим — повторяем чаще
        }

        // 8. ЗАЩИТА ОТ ЗАСТОЯ
        // Если после всех расчетов интервал не вырос хотя бы в 1.4 раза —
        // принудительно ускоряем (кроме случаев ошибки)
        val minGrowth = 1.4
        if (interval < currentInterval * minGrowth) {
            interval = currentInterval * minGrowth
        }

        // 7. ШТРАФ ЗА УСТАЛОСТЬ (только если она реально высокая)
        if (fatigue > 0.3f) {
            interval *= (1.0 - (fatigue - 0.6) * 0.5)
        }



        val result = interval.coerceIn(baseInterval, maxInterval)
        return result
    }
}