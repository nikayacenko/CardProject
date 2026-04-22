// app/src/test/java/com/example/cardproject/algorithm/SpacedRepetitionCalculatorTest.kt
package com.example.cardproject.algorithm

import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpacedRepetitionCalculatorTest {

    private lateinit var calculator: SpacedRepetitionCalcul

    @Before
    fun setup() {
        calculator = SpacedRepetitionCalcul
    }

    // ============================================================
    // ТЕСТ 1: Интервалы должны расти при правильных ответах
    // ============================================================
    @Test
    fun intervalShouldGrow_LongTerm() {
        var card = createTestCard(interval = 1.0, reviewStage = 0)
        val intervals = mutableListOf<Double>()

        println("------ SM2 ТЕСТ 1: Рост интервалов (LONG_TERM) ------")

        repeat(6) { step ->
            val updated = calculator.calculateNextReview(
                card = card,
                learningMode = LearningMode.LONG_TERM,
                quality = 1
            )
            intervals.add(updated.interval)
            println("Шаг ${step + 1}: ${String.format("%.2f", updated.interval)} дней")
            card = updated
        }

        // Проверяем рост
        for (i in 1 until intervals.size) {
            assertTrue(
                "Интервал должен расти: ${intervals[i-1]} → ${intervals[i]}",
                intervals[i] > intervals[i-1]
            )
        }

        // Ожидаемые SM2 интервалы: 1, 3, 7, 14, 30, 60
        assertEquals(1.0, intervals[0], 0.1)
        assertEquals(3.0, intervals[1], 0.1)
        assertEquals(7.0, intervals[2], 0.1)
        assertEquals(14.0, intervals[3], 0.1)
        assertEquals(30.0, intervals[4], 0.1)
        assertEquals(60.0, intervals[5], 0.1)
    }

    // ============================================================
    // ТЕСТ 2: SHORT_TERM интервалы (в часах)
    // ============================================================
    @Test
    fun intervalShouldGrow_ShortTerm() {
        var card = createTestCard(interval = 1.0, reviewStage = 0)
        val intervalsInHours = mutableListOf<Double>()

        println("------ SM2 ТЕСТ 2: Рост интервалов (SHORT_TERM) ------")

        repeat(5) { step ->
            val updated = calculator.calculateNextReview(
                card = card,
                learningMode = LearningMode.SHORT_TERM,
                quality = 1
            )
            val hours = updated.interval
            intervalsInHours.add(hours)
            println("Шаг ${step + 1}: ${String.format("%.1f", hours)} часов")
            card = updated
        }

        // Проверяем рост
        for (i in 1 until intervalsInHours.size) {
            assertTrue(
                "Интервал должен расти: ${intervalsInHours[i-1]} → ${intervalsInHours[i]} часов",
                intervalsInHours[i] > intervalsInHours[i-1]
            )
        }

        // Ожидаемые SM2 интервалы для SHORT_TERM: 1, 3, 8, 24, 72 часов
        assertEquals(1.0, intervalsInHours[0], 0.1)
        assertEquals(3.0, intervalsInHours[1], 0.1)
        assertEquals(8.0, intervalsInHours[2], 0.1)
        assertEquals(24.0, intervalsInHours[3], 0.1)
        assertEquals(72.0, intervalsInHours[4], 0.1)
    }

    // ============================================================
    // ТЕСТ 3: Неправильный ответ должен сбрасывать прогресс
    // ============================================================
    @Test
    fun incorrectAnswerShouldResetProgress() {
        // Сначала несколько правильных ответов
        var card = createTestCard(interval = 7.0, reviewStage = 2, consecutiveCorrect = 3)

        println("------ SM2 ТЕСТ 3: Сброс при ошибке ------")
        println("До ошибки: stage=${card.reviewStage}, interval=${card.interval} дней")

        val updated = calculator.calculateNextReview(
            card = card,
            learningMode = LearningMode.LONG_TERM,
            quality = 0  // Неправильно
        )

        println("После ошибки: stage=${updated.reviewStage}, interval=${updated.interval} дней")

        assertTrue(updated.reviewStage < card.reviewStage)
        assertEquals(1.0, updated.interval, 0.1)
        assertEquals(0, updated.consecutiveCorrect)
    }

    // ============================================================
    // ТЕСТ 4: SHORT_TERM vs LONG_TERM
    // ============================================================
    @Test
    fun shortTermShouldBeShorterThanLongTerm() {
        val card = createTestCard(reviewStage = 2)

        val longTermResult = calculator.calculateNextReview(
            card = card,
            learningMode = LearningMode.LONG_TERM,
            quality = 1
        )

        val shortTermResult = calculator.calculateNextReview(
            card = card,
            learningMode = LearningMode.SHORT_TERM,
            quality = 1
        )

        println("------ SM2 ТЕСТ 4: Сравнение режимов ------")
        println("LONG_TERM:  ${longTermResult.interval} дней")
        println("SHORT_TERM: ${shortTermResult.interval} часов")

        // SHORT_TERM интервал в днях должен быть меньше LONG_TERM
        val shortTermInDays = shortTermResult.interval / 24
        assertTrue(shortTermInDays < longTermResult.interval)
    }

    // ============================================================
    // ТЕСТ 5: Максимальные интервалы не превышают лимиты
    // ============================================================
    @Test
    fun intervalsShouldNotExceedMaximum() {
        var card = createTestCard(reviewStage = 0)

        println("------ SM2 ТЕСТ 5: Максимальные интервалы ------")

        // LONG_TERM: после 7 правильных ответов должен быть 120 дней
        repeat(7) { step ->
            card = calculator.calculateNextReview(
                card = card,
                learningMode = LearningMode.LONG_TERM,
                quality = 1
            )
            println("Шаг ${step + 1}: ${card.interval} дней")
        }
        assertTrue(card.interval <= 120.0)

        // SHORT_TERM: после 5 правильных ответов должен быть 72 часа
        var shortCard = createTestCard(reviewStage = 0)
        repeat(5) { step ->
            shortCard = calculator.calculateNextReview(
                card = shortCard,
                learningMode = LearningMode.SHORT_TERM,
                quality = 1
            )
            println("SHORT_TERM шаг ${step + 1}: ${shortCard.interval} часов")
        }
        assertTrue(shortCard.interval <= 72.0)
    }

    // ============================================================
    // ТЕСТ 6: Новая карточка → минимальный интервал
    // ============================================================
    @Test
    fun newCardShouldHaveBaseInterval() {
        val newCard = createTestCard(reviewStage = 0, lastReviewed = null)

        val longTermResult = calculator.calculateNextReview(
            card = newCard,
            learningMode = LearningMode.LONG_TERM,
            quality = 1
        )

        val shortTermResult = calculator.calculateNextReview(
            card = newCard,
            learningMode = LearningMode.SHORT_TERM,
            quality = 1
        )

        println("------ SM2 ТЕСТ 6: Новая карточка ------")
        println("LONG_TERM:  ${longTermResult.interval} дней")
        println("SHORT_TERM: ${shortTermResult.interval} часов")

        assertEquals(1.0, longTermResult.interval, 0.1)
        assertEquals(1.0, shortTermResult.interval, 0.1)
    }

    // ============================================================
    // ТЕСТ 7: Последовательность с ошибками
    // ============================================================
    @Test
    fun sequenceWithErrors() {
        var card = createTestCard(reviewStage = 0)
        val results = mutableListOf<Pair<Int, Double>>()

        // Сценарий: ✅, ✅, ❌, ✅, ✅
        val answers = listOf(1, 1, 0, 1, 1)

        println("------ SM2 ТЕСТ 7: Последовательность с ошибками ------")

        answers.forEachIndexed { index, quality ->
            val updated = calculator.calculateNextReview(
                card = card,
                learningMode = LearningMode.LONG_TERM,
                quality = quality
            )
            results.add(index + 1 to updated.interval)
            println("Шаг ${index + 1}: ${if (quality == 1) "✅" else "❌"} интервал = ${updated.interval} дней")
            card = updated
        }

        // Проверки
        assertTrue(results[1].second > results[0].second)  // 2 > 1
        assertTrue(results[2].second > results[1].second)  // 3 > 2
        assertTrue(results[3].second < results[2].second)  // после ошибки меньше
        assertTrue(results[4].second > results[3].second)  // снова растет
    }

    private fun createTestCard(
        interval: Double = 1.0,
        reviewStage: Int = 0,
        consecutiveCorrect: Int = 0,
        lastReviewed: Long? = System.currentTimeMillis()
    ): Card {
        return Card(
            id = 1,
            deckId = 1,
            front = "Test",
            back = "Test",
            interval = interval,
            nextReview = System.currentTimeMillis(),
            lastReviewed = lastReviewed,
            reviewStage = reviewStage,
            consecutiveCorrect = consecutiveCorrect,
            easeFactor = 2.5,
            totalReviews = consecutiveCorrect,
            successRate = 0.5f,
            difficultyScore = 0.5f,
            questionType = "FACT",
            isFormula = false,
            wordCount = 10,
            averageResponseTimeMs = 3000,
            lastResponseTimeMs = 3000,
            linkedCardIds = "",
            lastFiveResults = "",
            algorithmType = "SM2",
            masteryLevel = 0f,
            lastPredictedProbability = 0f
        )
    }
}