// File: app/src/main/java/com/example/cardproject/ml/RealMLCalculator.kt
package com.example.cardproject.ml

import android.content.Context
import com.example.cardproject.model.AIContext
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.MLPrediction
import com.example.cardproject.model.ReviewLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealMLCalculator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tfliteModel: TensorFlowLiteModel
) {

    private val _isMLReady = MutableStateFlow(false)
    val isMLReady: StateFlow<Boolean> = _isMLReady

    init {
        _isMLReady.value = tfliteModel.isModelReady.value
    }

    /**
     * Главный метод - использует реальную ML модель
     */
    suspend fun calculateNextReview(
        card: Card,
        context: AIContext,
        learningMode: LearningMode,
        quality: Int,
        responseTimeMs: Long
    ): Card {
        // 1. Если модель не готова - используем заглушку
        if (!_isMLReady.value) {
            return fallbackCalculator(card, context, learningMode, quality, responseTimeMs)
        }

        // 2. Создаем признаки для ML модели
        val reviewLog = ReviewLog.from(
            card = card,
            context = context,
            quality = quality,
            responseTimeMs = responseTimeMs,
            linkedCardsMastery = 0.5f, // TODO: получить реальные данные
            correctRateBefore = calculateCorrectRate(card)
        )

        // 3. Получаем предсказание от TFLite модели
        val prediction = tfliteModel.predict(reviewLog)

        // 4. Рассчитываем следующий интервал
        val nextInterval = calculateIntervalFromPrediction(
            card = card,
            prediction = prediction,
            learningMode = learningMode,
            fatigue = context.userFatigueLevel
        )

        // 5. Обновляем карточку
        val now = System.currentTimeMillis()
        val intervalMs = when (learningMode) {
            LearningMode.LONG_TERM -> nextInterval * 24 * 60 * 60 * 1000L
            LearningMode.SHORT_TERM -> (nextInterval * 24 * 60 * 60 * 1000L).coerceAtMost(7 * 24 * 60 * 60 * 1000L)
        }

        return card.copy(
            lastReviewed = now,
            nextReview = (now + intervalMs).toLong(),
            interval = nextInterval.toDouble(),
            reviewStage = if (quality == 1) card.reviewStage + 1 else maxOf(0, card.reviewStage - 1),
            consecutiveCorrect = if (quality == 1) card.consecutiveCorrect + 1 else 0,
            easeFactor = calculateEaseFactor(card.easeFactor, prediction, quality),
            totalReviews = card.totalReviews + 1
        )
    }

    /**
     * Запасной калькулятор (когда модель не готова)
     */
    private fun fallbackCalculator(
        card: Card,
        context: AIContext,
        learningMode: LearningMode,
        quality: Int,
        responseTimeMs: Long
    ): Card {
        return if (quality == 1) {
            // Правильный ответ - SM-2 логика
            val newInterval = when {
                card.interval <= 1 -> 3
                card.interval <= 3 -> 7
                card.interval <= 7 -> 14
                card.interval <= 14 -> 30
                card.interval <= 30 -> 60
                else -> (card.interval * 1.5).toInt().coerceAtMost(365)
            }

            card.copy(
                lastReviewed = System.currentTimeMillis(),
                nextReview = System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L),
                interval = newInterval.toDouble(),
                consecutiveCorrect = card.consecutiveCorrect + 1,
                totalReviews = card.totalReviews + 1
            )
        } else {
            // Неправильный ответ
            val newInterval = 1
            card.copy(
                lastReviewed = System.currentTimeMillis(),
                nextReview = System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L),
                interval = newInterval.toDouble(),
                consecutiveCorrect = 0,
                totalReviews = card.totalReviews + 1
            )
        }
    }

    private fun calculateIntervalFromPrediction1(
        card: Card,
        prediction: MLPrediction,
        learningMode: LearningMode,
        fatigue: Float
    ): Int {
        // Если модель неуверена - используем fallback
        if (prediction.needsMoreData || prediction.confidence < 0.5f) {
            return when {
                card.interval <= 1 -> 3
                card.interval <= 3 -> 7
                card.interval <= 7 -> 14
                card.interval <= 14 -> 30
                else -> (card.interval * 1.5).toInt()
            }
        }

        // Интервал от ML модели (в днях)
        var interval = prediction.optimalIntervalDays.toDouble()

        // Корректировка на усталость
        interval *= (1.0 - fatigue * 0.2)

        // Ограничения в зависимости от режима
        return when (learningMode) {
            LearningMode.LONG_TERM -> interval.toInt().coerceIn(1, 365)
            LearningMode.SHORT_TERM -> interval.toInt().coerceIn(1, 30)
        }
    }
    private fun calculateIntervalFromPrediction(
        card: Card,
        prediction: MLPrediction,
        learningMode: LearningMode,
        fatigue: Float
    ): Int {
        // 1. ПРОВЕРКА УВЕРЕННОСТИ
        if (prediction.needsMoreData || prediction.confidence < 0.4f) {
            return getFallbackInterval(card.interval)
        }

        // 2. ЛОГИКА РОСТА (Ключевое изменение!)
        // Мы не заменяем интервал предсказанием, а используем предсказание как коэффициент
        val currentInterval = if (card.interval < 1.0) 1.0 else card.interval

        // Нейросеть предсказывает "идеальный" интервал для новых данных.
        // Мы интерпретируем её прогноз как множитель (Ease Factor).
        val mlGrowthMultiplier = (prediction.optimalIntervalDays / 1.5).coerceIn(1.3, 2.5)

        var calculatedInterval = currentInterval * mlGrowthMultiplier

        // 3. УЧЕТ РЕЖИМА ОБУЧЕНИЯ
        calculatedInterval = when (learningMode) {
            LearningMode.LONG_TERM -> {
                // Фундаментальный: даем интервалу расти максимально
                calculatedInterval
            }
            LearningMode.SHORT_TERM -> {
                // К событию: искусственно сжимаем интервал (в 2-3 раза чаще повторения)
                // Чтобы успеть "вдолбить" информацию перед дедлайном
                (calculatedInterval * 0.4).coerceAtLeast(0.1) // 0.1 дня ~ 2.4 часа
            }
        }

        // 4. КОРРЕКТИРОВКА НА УСТАЛОСТЬ (Штраф до 30%)
        calculatedInterval *= (1.0 - fatigue * 0.3)

        // 5. ГРАНИЦЫ
        return when (learningMode) {
            LearningMode.LONG_TERM -> calculatedInterval.toInt().coerceIn(1, 365)
            LearningMode.SHORT_TERM -> {
                // В интенсивном режиме интервал не может быть огромным (макс 7 дней)
                calculatedInterval.toInt().coerceIn(1, 7)
            }
        }
    }
    private fun getFallbackInterval(currentInterval: Double): Int {
        return when {
            currentInterval <= 1 -> 3
            currentInterval <= 3 -> 7
            currentInterval <= 7 -> 14
            else -> (currentInterval * 1.5).toInt()
        }
    }
    private fun calculateEaseFactor(
        currentEase: Double,
        prediction: MLPrediction,
        quality: Int
    ): Double {
        return if (quality == 1) {
            // Увеличиваем ease factor при правильном ответе
            (currentEase + 0.1 * (1 - prediction.forgettingProbability)).coerceIn(1.3, 2.5)
        } else {
            // Уменьшаем при неправильном
            (currentEase - 0.2).coerceAtLeast(1.3)
        }
    }

    private fun calculateCorrectRate(card: Card): Float {
        return if (card.totalReviews > 0) {
            card.consecutiveCorrect.toFloat() / card.totalReviews
        } else 0.5f
    }
}