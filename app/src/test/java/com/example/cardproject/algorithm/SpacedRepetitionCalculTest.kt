package com.example.cardproject.algorithm

import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpacedRepetitionCalculTest {

    private val now = System.currentTimeMillis()

    @Test
    fun testCalculateNextReviewCorrectAnswerIncreasesStage() {
        println("Тест: Правильный ответ увеличивает этап повторения")

        // Given
        val card = Card(
            id = 1,
            deckId = 1,
            front = "Вопрос",
            back = "Ответ",
            reviewStage = 1,
            interval = 1,
            easeFactor = 2.5,
            lastReviewed = now - TimeUnit.DAYS.toMillis(2),
            nextReview = now - TimeUnit.DAYS.toMillis(1),
            consecutiveCorrect = 1
        )

        val learningMode = LearningMode.LONG_TERM

        // When
        val updatedCard = SpacedRepetitionCalcul.calculateNextReview(card, learningMode, 1)

        // Then
        assertEquals(2, updatedCard.reviewStage)
        assertEquals(2, updatedCard.consecutiveCorrect)
        assertTrue(updatedCard.interval > 1)

        println("Тест пройден")
    }

    @Test
    fun testCalculateNextReviewWrongAnswerResetsProgress() {
        println("Тест: Неправильный ответ сбрасывает прогресс")

        // Given
        val card = Card(
            id = 2,
            deckId = 1,
            front = "Вопрос",
            back = "Ответ",
            reviewStage = 3,
            interval = 14,
            easeFactor = 2.2,
            lastReviewed = now - TimeUnit.DAYS.toMillis(5),
            nextReview = now + TimeUnit.DAYS.toMillis(9),
            consecutiveCorrect = 3
        )

        val learningMode = LearningMode.LONG_TERM

        // When
        val updatedCard = SpacedRepetitionCalcul.calculateNextReview(card, learningMode, 0)

        // Then
        assertEquals(2, updatedCard.reviewStage)
        assertEquals(0, updatedCard.consecutiveCorrect)
        assertEquals(1, updatedCard.interval)

        println("Тест пройден")
    }

    @Test
    fun testGetCardsDueForReview() {
        println("Тест: Получение карточек для повторения")

        // Given
        val cards = listOf(
            Card(
                id = 1,
                deckId = 1,
                front = "Новая карточка",
                back = "Ответ",
                reviewStage = 0,
                interval = 1,
                easeFactor = 2.5,
                lastReviewed = null,
                nextReview = null,
                consecutiveCorrect = 0
            ),
            Card(
                id = 2,
                deckId = 1,
                front = "Просроченная карточка",
                back = "Ответ",
                reviewStage = 1,
                interval = 3,
                easeFactor = 2.3,
                lastReviewed = now - TimeUnit.DAYS.toMillis(4),
                nextReview = now - TimeUnit.DAYS.toMillis(1),
                consecutiveCorrect = 1
            ),
            Card(
                id = 3,
                deckId = 1,
                front = "Не готовая карточка",
                back = "Ответ",
                reviewStage = 3,
                interval = 30,
                easeFactor = 2.0,
                lastReviewed = now - TimeUnit.DAYS.toMillis(10),
                nextReview = now + TimeUnit.DAYS.toMillis(20),
                consecutiveCorrect = 3
            )
        )

        // When
        val dueCards = SpacedRepetitionCalcul.getCardsDueForReview(cards)

        // Then
        assertEquals(2, dueCards.size)
        assertTrue(dueCards.any { it.id.toInt() == 1 })
        assertTrue(dueCards.any { it.id.toInt() == 2 })
        assertFalse(dueCards.any { it.id.toInt() == 3 })

        println("Тест пройден: Найдено ${dueCards.size} карточек для повторения")
    }

    @Test
    fun testGetLearningProgress() {
        println("Тест: Расчет прогресса обучения")

        // Given
        val cards = listOf(
            // Новая карточка
            Card(
                id = 1,
                deckId = 1,
                front = "Новая",
                back = "Ответ",
                reviewStage = 0,
                interval = 1,
                easeFactor = 2.5,
                lastReviewed = null,
                nextReview = null,
                consecutiveCorrect = 0
            ),
            // Изучаемая карточка
            Card(
                id = 2,
                deckId = 1,
                front = "Изучаемая",
                back = "Ответ",
                reviewStage = 2,
                interval = 7,
                easeFactor = 2.3,
                lastReviewed = now - TimeUnit.DAYS.toMillis(3),
                nextReview = now + TimeUnit.DAYS.toMillis(4),
                consecutiveCorrect = 2
            ),
            // Выученная карточка
            Card(
                id = 3,
                deckId = 1,
                front = "Выученная",
                back = "Ответ",
                reviewStage = 4,
                interval = 60,
                easeFactor = 2.0,
                lastReviewed = now - TimeUnit.DAYS.toMillis(10),
                nextReview = now + TimeUnit.DAYS.toMillis(50),
                consecutiveCorrect = 4
            )
        )

        // When
        val progress = SpacedRepetitionCalcul.getLearningProgress(cards)

        // Then
        assertEquals(3, progress.totalCards)
        assertEquals(1, progress.newCards)
        assertEquals(1, progress.learnedCards)
        assertEquals(33, progress.progressPercent)

        println("Тест пройден: Прогресс ${progress.progressPercent}%")
    }

    @Test
    fun testGetNextReviewTimeTextForNewCard() {
        println("Тест: Текст для новой карточки")

        // Given
        val card = Card(
            id = 1,
            deckId = 1,
            front = "Вопрос",
            back = "Ответ",
            reviewStage = 0,
            interval = 1,
            easeFactor = 2.5,
            lastReviewed = null,
            nextReview = null,
            consecutiveCorrect = 0
        )

        val learningMode = LearningMode.LONG_TERM

        // When
        val text = SpacedRepetitionCalcul.getNextReviewTimeText(card, learningMode)

        // Then
        assertEquals("Новое", text)

        println("Тест пройден: '$text'")
    }

    @Test
    fun testGetNextReviewTimeTextForDueCard() {
        println("Тест: Текст для готовой карточки")

        // Given
        val card = Card(
            id = 1,
            deckId = 1,
            front = "Вопрос",
            back = "Ответ",
            reviewStage = 1,
            interval = 1,
            easeFactor = 2.5,
            lastReviewed = now - TimeUnit.DAYS.toMillis(2),
            nextReview = now - TimeUnit.DAYS.toMillis(1),
            consecutiveCorrect = 1
        )

        val learningMode = LearningMode.LONG_TERM

        // When
        val text = SpacedRepetitionCalcul.getNextReviewTimeText(card, learningMode)

        // Then
        assertEquals("Готово к повторению", text)

        println("Тест пройден: '$text'")
    }

    @Test
    fun testGetNextReviewTimeTextForFutureCard() {
        println("Тест: Текст для карточки с будущей датой")

        // Given
        val card = Card(
            id = 1,
            deckId = 1,
            front = "Вопрос",
            back = "Ответ",
            reviewStage = 2,
            interval = 7,
            easeFactor = 2.5,
            lastReviewed = now - TimeUnit.DAYS.toMillis(5),
            nextReview = now + TimeUnit.DAYS.toMillis(2),
            consecutiveCorrect = 2
        )

        val learningMode = LearningMode.LONG_TERM

        // When
        val text = SpacedRepetitionCalcul.getNextReviewTimeText(card, learningMode)

        // Then
        assertTrue(text.startsWith("Через 2"))
        assertTrue(text.contains("дня") || text.contains("дня"))

        println("Тест пройден: '$text'")
    }

    @Test
    fun testGetNextReviewTimeTextShortTermMode() {
        println("Тест: Текст для краткосрочного режима")

        // Given
        val threeHoursFromNow = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3)

        val card = Card(
            id = 1,
            deckId = 1,
            front = "Вопрос",
            back = "Ответ",
            reviewStage = 1,
            interval = 3,
            easeFactor = 2.5,
            lastReviewed = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(5),
            nextReview = threeHoursFromNow,
            consecutiveCorrect = 1
        )

        val learningMode = LearningMode.SHORT_TERM

        // When
        val text = SpacedRepetitionCalcul.getNextReviewTimeText(card, learningMode)
        //Then
        assertTrue(text.contains("Через") || text.contains("час"))

        println("Тест пройден: '$text'")
    }
}