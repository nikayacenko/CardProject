// app/src/test/java/com/example/cardproject/ml/IntervalCalculatorTest.kt
package com.example.cardproject.ml

import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.MLPrediction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntervalCalculatorTest {

    private lateinit var calculator: TestableIntervalCalculator

    @Before
    fun setup() {
        calculator = TestableIntervalCalculator()
    }

    // ============================================================
    // ТЕСТ 1: Проверка роста интервалов при правильных ответах
    // ============================================================
    @Test
    fun testIntervalGrowsWithCorrectAnswers_LongTerm() {
        var currentCard = createTestCard(
            interval = 1.0,
            consecutiveCorrect = 0,
            reviewStage = 0
        )

        val prediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 1.8f,  // Множитель 1.8x
            confidence = 0.85f,
            needsMoreData = false
        )

        val intervals = mutableListOf<Double>()

        // Симулируем 5 правильных ответов подряд
        for (i in 1..5) {
            val newInterval = calculator.calculateInterval(
                card = currentCard,
                prediction = prediction,
                learningMode = LearningMode.LONG_TERM,
                fatigue = 0.3f
            )
            intervals.add(newInterval)
            currentCard = currentCard.copy(
                interval = newInterval,
                consecutiveCorrect = i,
                reviewStage = i
            )

            println("Шаг $i: ${String.format("%.2f", newInterval)} дней")
        }

        // Проверки
        for (i in 1 until intervals.size) {
            assertTrue(
                "Интервал должен увеличиваться: ${intervals[i-1]} → ${intervals[i]}",
                intervals[i] > intervals[i-1]
            )
        }

        // Проверяем экспоненциальный рост
        assertTrue(
            "5-й интервал (${intervals[4]}) должен быть > 3x первого (${intervals[0] * 3})",
            intervals[4] > intervals[0] * 3
        )
    }

    // ============================================================
    // ТЕСТ 2: LONG_TERM vs SHORT_TERM
    // ============================================================
    @Test
    fun testShortTermIntervalsAreShorterThanLongTerm() {
        val card = createTestCard(
            interval = 1.0,
            consecutiveCorrect = 2,
            reviewStage = 2
        )

        val prediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 1.8f,
            confidence = 0.85f,
            needsMoreData = false
        )

        val longTermInterval = calculator.calculateInterval(
            card, prediction, LearningMode.LONG_TERM, 0.3f
        )

        val shortTermInterval = calculator.calculateInterval(
            card, prediction, LearningMode.SHORT_TERM, 0.3f
        )

        println("LONG_TERM: ${String.format("%.2f", longTermInterval)} дней")
        println("SHORT_TERM: ${String.format("%.2f", shortTermInterval)} дней")

        assertTrue(
            "SHORT_TERM интервал ($shortTermInterval) должен быть МЕНЬШЕ LONG_TERM ($longTermInterval)",
            shortTermInterval < longTermInterval
        )
    }

    // ============================================================
    // ТЕСТ 3: Сброс интервала при неправильном ответе
    // ============================================================
    @Test
    fun testIntervalResetsOnIncorrectAnswer() {
        val card = createTestCard(
            interval = 7.0,
            consecutiveCorrect = 5,
            reviewStage = 5
        )

        val prediction = MLPrediction(
            forgettingProbability = 0.8f,
            optimalIntervalDays = 0.5f,
            confidence = 0.9f,
            needsMoreData = false
        )

        val newInterval = calculator.calculateIntervalForIncorrect(
            card, LearningMode.LONG_TERM
        )

        println("Старый интервал: ${card.interval} дней")
        println("Новый интервал (после ошибки): ${String.format("%.2f", newInterval)} дней")

        assertTrue(
            "Интервал должен уменьшиться: ${card.interval} → $newInterval",
            newInterval < card.interval
        )

        assertTrue(
            "Интервал не должен быть меньше 1 дня: $newInterval",
            newInterval >= 1.0
        )
    }

    // ============================================================
    // ТЕСТ 4: Влияние усталости на интервал
    // ============================================================
    @Test
    fun testFatigueReducesInterval() {
        val card = createTestCard(
            interval = 3.0,
            consecutiveCorrect = 2,
            reviewStage = 2
        )

        val prediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 1.8f,
            confidence = 0.85f,
            needsMoreData = false
        )

        val normalInterval = calculator.calculateInterval(
            card, prediction, LearningMode.LONG_TERM, 0.3f
        )

        val highFatigueInterval = calculator.calculateInterval(
            card, prediction, LearningMode.LONG_TERM, 0.85f
        )

        println("Нормальная усталость (0.3): ${String.format("%.2f", normalInterval)} дней")
        println("Высокая усталость (0.85): ${String.format("%.2f", highFatigueInterval)} дней")

        assertTrue(
            "При высокой усталости интервал должен быть МЕНЬШЕ",
            highFatigueInterval < normalInterval
        )
    }

    // ============================================================
    // ТЕСТ 5: Влияние сложности карточки (difficultyScore)
    // ============================================================
    @Test
    fun testDifficultyScoreAffectsInterval() {
        val easyCard = createTestCard(
            interval = 2.0,
            consecutiveCorrect = 2,
            reviewStage = 2,
            difficultyScore = 0.2f  // Легкая
        )

        val hardCard = createTestCard(
            interval = 2.0,
            consecutiveCorrect = 2,
            reviewStage = 2,
            difficultyScore = 0.9f  // Сложная
        )

        val prediction = MLPrediction(
            forgettingProbability = 0.5f,  // Средняя
            optimalIntervalDays = 1.5f,
            confidence = 0.85f,
            needsMoreData = false
        )

        val easyInterval = calculator.calculateInterval(
            easyCard, prediction, LearningMode.LONG_TERM, 0.3f
        )

        val hardInterval = calculator.calculateInterval(
            hardCard, prediction, LearningMode.LONG_TERM, 0.3f
        )

        println("Легкая карточка (difficulty=0.2): ${String.format("%.2f", easyInterval)} дней")
        println("Сложная карточка (difficulty=0.9): ${String.format("%.2f", hardInterval)} дней")

        // Сложные карточки должны иметь МЕНЬШИЕ интервалы
        assertTrue(
            "Сложная карточка должна иметь меньший интервал",
            hardInterval <= easyInterval
        )
    }

    // ============================================================
    // ТЕСТ 6: Влияние типа вопроса (questionType)
    // ============================================================
    @Test
    fun testQuestionTypeAffectsInterval() {
        val factCard = createTestCard(
            interval = 2.0,
            consecutiveCorrect = 2,
            reviewStage = 2,
            questionType = "FACT"
        )

        val proofCard = createTestCard(
            interval = 2.0,
            consecutiveCorrect = 2,
            reviewStage = 2,
            questionType = "PROOF"
        )

        val prediction = MLPrediction(
            forgettingProbability = 0.5f,
            optimalIntervalDays = 1.5f,
            confidence = 0.85f,
            needsMoreData = false
        )

        val factInterval = calculator.calculateInterval(
            factCard, prediction, LearningMode.LONG_TERM, 0.3f
        )

        val proofInterval = calculator.calculateInterval(
            proofCard, prediction, LearningMode.LONG_TERM, 0.3f
        )

        println("FACT карточка: ${String.format("%.2f", factInterval)} дней")
        println("PROOF карточка: ${String.format("%.2f", proofInterval)} дней")

        // PROOF (сложнее) должен иметь меньший интервал
        assertTrue(
            "PROOF карточка должна иметь меньший интервал, чем FACT",
            proofInterval <= factInterval
        )
    }

    // ============================================================
    // ТЕСТ 7: Последовательность реальных сценариев
    // ============================================================
    @Test
    fun testRealWorldScenario() {
        var card = createTestCard(
            interval = 1.0,
            consecutiveCorrect = 0,
            reviewStage = 0,
            difficultyScore = 0.7f  // Средне-сложная
        )

        val results = mutableListOf<Pair<Int, Double>>()

        // Сценарий: 3 правильных, потом 1 неправильный, потом 2 правильных
        val answers = listOf(
            true to 0.2f,   // Правильно, низкая усталость
            true to 0.3f,   // Правильно, средняя усталость
            true to 0.5f,   // Правильно, повышенная усталость
            false to 0.4f,  // Неправильно
            true to 0.3f,   // Правильно
            true to 0.2f    // Правильно
        )

        answers.forEachIndexed { index, (isCorrect, fatigue) ->
            val prediction = MLPrediction(
                forgettingProbability = if (isCorrect) 0.2f else 0.8f,
                optimalIntervalDays = if (isCorrect) 1.5f else 0.6f,
                confidence = 0.8f,
                needsMoreData = false
            )

            val newInterval = if (isCorrect) {
                calculator.calculateInterval(card, prediction, LearningMode.LONG_TERM, fatigue)
            } else {
                calculator.calculateIntervalForIncorrect(card, LearningMode.LONG_TERM)
            }

            results.add(index + 1 to newInterval)

            card = card.copy(
                interval = newInterval,
                consecutiveCorrect = if (isCorrect) card.consecutiveCorrect + 1 else 0,
                reviewStage = if (isCorrect) card.reviewStage + 1 else maxOf(0, card.reviewStage - 1)
            )

            println("Шаг ${index + 1}: ${if (isCorrect) "✅" else "❌"} интервал = ${String.format("%.2f", newInterval)} дней")
        }

        // Проверки
        assertTrue(results[1].second > results[0].second)  // 2 > 1
        assertTrue(results[2].second > results[1].second)  // 3 > 2
        assertTrue(results[3].second < results[2].second)  // после ошибки интервал уменьшился
        assertTrue(results[5].second > results[4].second)  // снова растет
    }

    // ============================================================
    // ТЕСТ 8: SHORT_TERM режим (интервалы в днях, но должны быть короче)
    // ============================================================
    @Test
    fun testShortTermIntervalsAreShort() {
        var card = createTestCard(
            interval = 0.042,  // 1 час в днях
            consecutiveCorrect = 0,
            reviewStage = 0
        )

        val prediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 1.5f,
            confidence = 0.85f,
            needsMoreData = false
        )

        val intervalsInHours = mutableListOf<Double>()

        for (i in 1..5) {
            val newIntervalDays = calculator.calculateInterval(
                card, prediction, LearningMode.SHORT_TERM, 0.3f
            )
            val hours = newIntervalDays * 24
            intervalsInHours.add(hours)

            println("SHORT_TERM шаг $i: ${String.format("%.1f", hours)} часов")

            card = card.copy(
                interval = newIntervalDays,
                consecutiveCorrect = i
            )
        }

        // Проверяем, что интервалы растут
        for (i in 1 until intervalsInHours.size) {
            assertTrue(
                "Интервалы должны расти: ${intervalsInHours[i-1]} → ${intervalsInHours[i]} часов",
                intervalsInHours[i] > intervalsInHours[i-1]
            )
        }

        // Максимальный интервал не более 7 дней (168 часов)
        assertTrue(
            "Максимальный интервал не должен превышать 168 часов, но он = ${intervalsInHours.last()} часов",
            intervalsInHours.last() <= 168.0
        )
    }

    // ============================================================
    // ТЕСТ 9: Уверенность модели (confidence)
    // ============================================================
    @Test
    fun testLowConfidenceUsesConservativeMultiplier() {
        val card = createTestCard(
            interval = 2.0,
            consecutiveCorrect = 1,
            reviewStage = 1
        )

        val lowConfidencePrediction = MLPrediction(
            forgettingProbability = 0.5f,
            optimalIntervalDays = 2.0f,  // Высокий множитель, но низкая уверенность
            confidence = 0.3f,
            needsMoreData = true
        )

        val highConfidencePrediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 2.0f,  // Такой же множитель
            confidence = 0.85f,
            needsMoreData = false
        )

        val lowConfInterval = calculator.calculateInterval(
            card, lowConfidencePrediction, LearningMode.LONG_TERM, 0.3f
        )

        val highConfInterval = calculator.calculateInterval(
            card, highConfidencePrediction, LearningMode.LONG_TERM, 0.3f
        )

        println("Низкая уверенность (0.3): ${String.format("%.2f", lowConfInterval)} дней")
        println("Высокая уверенность (0.85): ${String.format("%.2f", highConfInterval)} дней")

        // При низкой уверенности используем консервативный множитель (меньше рост)
        assertTrue(
            "При низкой уверенности интервал должен быть меньше или равен",
            lowConfInterval <= highConfInterval
        )
    }

    // ============================================================
    // ТЕСТ 10: Граничные значения
    // ============================================================
    @Test
    fun testBoundaryValues() {
        // Новая карточка
        val newCard = createTestCard(
            interval = 0.0,
            consecutiveCorrect = 0,
            reviewStage = 0
        )

        val prediction = MLPrediction(
            forgettingProbability = 0.5f,
            optimalIntervalDays = 1.0f,
            confidence = 0.5f,
            needsMoreData = true
        )

        val minInterval = calculator.calculateInterval(
            newCard, prediction, LearningMode.LONG_TERM, 0.3f
        )

        println("Минимальный интервал для новой карточки: $minInterval дней")
        assertTrue(minInterval >= 1.0)

        // Экспертная карточка (много повторений)
        val expertCard = createTestCard(
            interval = 300.0,
            consecutiveCorrect = 50,
            reviewStage = 20
        )

        val maxInterval = calculator.calculateInterval(
            expertCard, prediction, LearningMode.LONG_TERM, 0.3f
        )

        println("Максимальный интервал: $maxInterval дней")
        assertTrue(maxInterval <= 365.0)
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private fun createTestCard(
        id: Long = 1,
        deckId: Long = 1,
        interval: Double = 1.0,
        consecutiveCorrect: Int = 0,
        reviewStage: Int = 0,
        difficultyScore: Float = 0.5f,
        questionType: String = "FACT",
        easeFactor: Double = 2.5,
        totalReviews: Int = 0,
        successRate: Float = 0.5f
    ): Card {
        return Card(
            id = id,
            deckId = deckId,
            front = "Test Front",
            back = "Test Back",
            interval = interval,
            nextReview = System.currentTimeMillis(),
            lastReviewed = System.currentTimeMillis(),
            reviewStage = reviewStage,
            consecutiveCorrect = consecutiveCorrect,
            easeFactor = easeFactor,
            totalReviews = totalReviews,
            successRate = successRate,
            difficultyScore = difficultyScore,
            questionType = questionType,
            isFormula = questionType == "FORMULA",
            wordCount = 100,
            averageResponseTimeMs = 5000,
            lastResponseTimeMs = 5000,
            linkedCardIds = "",
            lastFiveResults = "",
            algorithmType = "ML",
            masteryLevel = 0f,
            lastPredictedProbability = 0f
        )
    }
}

// ============================================================
// ТЕСТИРУЕМЫЙ КАЛЬКУЛЯТОР (адаптирован под вашу логику)
// ============================================================
class TestableIntervalCalculator {

    private val BASE_INTERVALS = mapOf(
        LearningMode.LONG_TERM to 1.0,
        LearningMode.SHORT_TERM to 0.042  // 1 час в днях
    )

    private val MAX_INTERVALS = mapOf(
        LearningMode.LONG_TERM to 365.0,
        LearningMode.SHORT_TERM to 7.0
    )

    fun calculateInterval(
        card: Card,
        prediction: MLPrediction,
        learningMode: LearningMode,
        fatigue: Float
    ): Double {
        val baseInterval = BASE_INTERVALS[learningMode] ?: 1.0
        val maxInterval = MAX_INTERVALS[learningMode] ?: 365.0

        // Используем текущий интервал карточки
        val currentInterval = maxOf(card.interval, baseInterval)

        // Получаем множитель из модели
        val rawMultiplier = if (prediction.needsMoreData || prediction.confidence < 0.5f) {
            1.3  // Консервативный множитель при неуверенности
        } else {
            // Нормализуем предсказание в диапазон 1.1-2.5
            prediction.optimalIntervalDays.coerceIn(1.1f, 2.5f).toDouble()
        }

        // Корректировка множителя на сложность карточки
        val difficultyMultiplier = 1.0 - (card.difficultyScore * 0.3)

        // Корректировка на тип вопроса
        val typeMultiplier = when (card.questionType) {
            "PROOF" -> 0.7
            "DEFINITION" -> 0.85
            else -> 1.0
        }

        // Для SHORT_TERM режима уменьшаем множитель
        val modeMultiplier = if (learningMode == LearningMode.SHORT_TERM) 0.6 else 1.0

        val finalMultiplier = rawMultiplier * difficultyMultiplier * typeMultiplier * modeMultiplier

        // Рассчитываем новый интервал (УМНОЖАЕМ, а не перезаписываем!)
        var newInterval = currentInterval * finalMultiplier

        // Корректировка на усталость
        if (fatigue > 0.7f) {
            newInterval *= (1.0 - fatigue * 0.3)
        }

        // Корректировка на вероятность забывания
        if (prediction.forgettingProbability > 0.3f) {
            newInterval *= (1.0 - prediction.forgettingProbability * 0.4)
        }

        // Ограничиваем
        return newInterval.coerceIn(baseInterval, maxInterval)
    }

    fun calculateIntervalForIncorrect(
        card: Card,
        learningMode: LearningMode
    ): Double {
        val baseInterval = BASE_INTERVALS[learningMode] ?: 1.0
        val newInterval = maxOf(card.interval * 0.5, baseInterval)
        return newInterval.coerceAtMost(MAX_INTERVALS[learningMode] ?: 365.0)
    }
}