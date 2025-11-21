package com.example.cardproject.algorithm

import com.example.cardproject.model.Card
import kotlin.math.max
import com.example.cardproject.model.LearningMode

object SpacedRepetitionCalcul {
    // Интервалы для долговременного обучения (в днях)
    private val LONG_TERM_INTERVALS = listOf(1, 3, 7, 14, 30, 60, 120)

    // Интервалы для кратковременного обучения (в часах)
    private val SHORT_TERM_INTERVALS = listOf(1, 3, 8, 24, 72) // 1 час, 3 часа, 8 часов, 1 день, 3 дня

    fun calculateNextReview(
        card: Card,
        learningMode: LearningMode, // learningMode передается как параметр
        quality: Int // 0 - не знаю, 1 - знаю
    ): Card {
        val now = System.currentTimeMillis()
        val updatedCard = card.copy(
            lastReviewed = now,
            consecutiveCorrect = if (quality == 1) card.consecutiveCorrect + 1 else 0
        )

        return when (quality) {
            0 -> resetCard(updatedCard, learningMode) // Не знаю - сбрасываем прогресс
            1 -> scheduleNextReview(updatedCard, learningMode) // Знаю - планируем следующее повторение
            else -> updatedCard
        }
    }

    private fun scheduleNextReview(card: Card, learningMode: LearningMode): Card {
        val intervals = when (learningMode) {
            LearningMode.LONG_TERM -> LONG_TERM_INTERVALS
            LearningMode.SHORT_TERM -> SHORT_TERM_INTERVALS
        }

        val currentStage = card.reviewStage
        val nextStage = minOf(currentStage + 1, intervals.size - 1)

        val interval = when (learningMode) {
            LearningMode.LONG_TERM -> intervals[nextStage] * 24 * 60 * 60 * 1000L // дни в миллисекунды
            LearningMode.SHORT_TERM -> intervals[nextStage] * 60 * 60 * 1000L // часы в миллисекунды
        }

        val nextReviewTime = System.currentTimeMillis() + interval

        return card.copy(
            reviewStage = nextStage,
            interval = intervals[nextStage],
            nextReview = nextReviewTime,
            easeFactor = max(1.3, card.easeFactor + (0.1 - (5 - 1) * (0.08 + (5 - 1) * 0.02)))
        )
    }

    private fun resetCard(card: Card, learningMode: LearningMode): Card {
        return card.copy(
            reviewStage = max(0, card.reviewStage - 1),
            interval = 1,
            nextReview = System.currentTimeMillis(), // Сразу доступна для повторения
            easeFactor = max(1.3, card.easeFactor - 0.1),
            consecutiveCorrect = 0
        )
    }

    // Получить карточки для повторения
    fun getCardsDueForReview(cards: List<Card>): List<Card> {
        val now = System.currentTimeMillis()
        return cards.filter { card ->
            card.nextReview == null || card.nextReview!! <= now
        }
    }

    // Получить прогресс изучения
    fun getLearningProgress(cards: List<Card>): LearningProgress {
        val total = cards.size
        val due = getCardsDueForReview(cards).size
        val learned = cards.count { it.reviewStage >= 3 && it.consecutiveCorrect >= 3 }
        val new = cards.count { it.reviewStage == 0 && it.lastReviewed == null }

        return LearningProgress(
            totalCards = total,
            dueCards = due,
            learnedCards = learned,
            newCards = new
        )
    }

    // Получить следующее время повторения в читаемом формате
    fun getNextReviewTimeText(card: Card, learningMode: LearningMode): String {
        return when {
            card.nextReview == null -> "Новое"
            card.nextReview!! > System.currentTimeMillis() -> {
                val diff = card.nextReview!! - System.currentTimeMillis()
                when (learningMode) {
                    LearningMode.LONG_TERM -> formatDays(diff)
                    LearningMode.SHORT_TERM -> formatHours(diff)
                }
            }
            else -> "Готово к повторению"
        }
    }

    private fun formatDays(millis: Long): String {
        val days = millis / (24 * 60 * 60 * 1000)
        return if (days > 0) {
            "Через $days ${getDayText(days.toInt())}"
        } else {
            formatHours(millis)
        }
    }

    private fun formatHours(millis: Long): String {
        val hours = millis / (60 * 60 * 1000)
        return if (hours > 0) {
            "Через $hours ${getHourText(hours.toInt())}"
        } else {
            "Сейчас"
        }
    }

    private fun getDayText(days: Int): String = when {
        days % 10 == 1 && days % 100 != 11 -> "день"
        days % 10 in 2..4 && (days % 100 < 10 || days % 100 >= 20) -> "дня"
        else -> "дней"
    }

    private fun getHourText(hours: Int): String = when {
        hours % 10 == 1 && hours % 100 != 11 -> "час"
        hours % 10 in 2..4 && (hours % 100 < 10 || hours % 100 >= 20) -> "часа"
        else -> "часов"
    }

    private fun rescheduleForLaterInSession(card: Card): Card {
        return card.copy(
            reviewStage = card.reviewStage, // Не сбрасываем этап
            nextReview = System.currentTimeMillis() + (30 * 1000L), // 30 секунд - появится позже в этой же сессии
            easeFactor = max(1.3, card.easeFactor - 0.15),
            consecutiveCorrect = 0
        )
    }
}


data class LearningProgress(
    val totalCards: Int,
    val dueCards: Int,
    val learnedCards: Int,
    val newCards: Int
) {
    val progressPercent: Int
        get() = if (totalCards > 0) {
            (learnedCards * 100 / totalCards)
        } else {
            0
        }
}