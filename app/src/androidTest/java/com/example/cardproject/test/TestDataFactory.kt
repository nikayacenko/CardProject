package com.example.cardproject.test

// File: app/src/test/java/com/example/cardproject/test/TestDataFactory.kt

import com.example.cardproject.model.Card
import com.example.cardproject.model.QuestionType
import java.util.*

object TestDataFactory {

    /**
     * Создает тестовую карточку с заданными параметрами
     */
    fun createTestCard(
        id: Long = 1,
        deckId: Long = 1,
        difficulty: Float = 0.5f,
        questionType: QuestionType = QuestionType.FACT,
        hasFormulas: Boolean = false
    ): Card {
        return Card(
            id = id,
            deckId = deckId,
            front = when (questionType) {
                QuestionType.FACT -> "Столица Франции?"
                QuestionType.DEFINITION -> "Что такое фотосинтез?"
                QuestionType.PROOF -> "Докажите теорему Пифагора"
            },
            back = "Тестовый ответ $id",
            createdAt = System.currentTimeMillis(),
            reviewStage = 0,
            interval = 1.0,
            lastReviewed = null,
            nextReview = null,
            easeFactor = 2.5,
            consecutiveCorrect = 0,
            totalReviews = 0,
            isFormula = hasFormulas,
            questionType = questionType.name,
            difficultyScore = when (questionType) {
                QuestionType.FACT -> 0.3f
                QuestionType.DEFINITION -> 0.6f
                QuestionType.PROOF -> 0.9f
            },
            linkedCardIds = "",
            successRate = 0.5f
        )
    }

    /**
     * Создает набор карточек разной сложности
     */
    fun createTestDeck(size: Int = 10): List<Card> {
        val cards = mutableListOf<Card>()
        val random = Random(42)

        for (i in 1..size) {
            val type = when (i % 3) {
                0 -> QuestionType.FACT
                1 -> QuestionType.DEFINITION
                else -> QuestionType.PROOF
            }

            cards.add(createTestCard(
                id = i.toLong(),
                questionType = type,
                hasFormulas = type == QuestionType.PROOF
            ))
        }

        return cards
    }
}