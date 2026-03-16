package com.example.cardproject.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index
import java.util.Calendar

@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["timestamp"]),
        Index(value = ["deckId"]),
        Index(value = ["wasCorrect"])
    ]
)
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val deckId: Long,
    val timestamp: Long = System.currentTimeMillis(),

    // Результат ответа
    val quality: Int,                // 0 - забыл, 1 - вспомнил

    // Признаки карточки (из самой Card)
    val cardTextLength: Int,          // Длина текста вопроса
    val hasFormulas: Boolean,         // Есть формулы (isFormula)
    val questionType: String,         // Тип вопроса (FACT/PROOF)
    val difficultyScore: Float,       // Сложность карточки

    // Контекстные признаки
    val responseTimeMs: Long,         // Время раздумья (lastResponseTimeMs)
    val hourOfDay: Int,               // Час дня (0-23)
    val dayOfWeek: Int,                // День недели (1-7)
    val fatigueLevel: Float,           // Усталость до ответа
    val cardsReviewedInSession: Int,   // Карточек в сессии

    // История карточки
    val totalReviewsBefore: Int,       // Всего повторений до этого (totalReviews)
    val correctRateBefore: Float,      // Процент успеха до этого (successRate)
    val streakCorrectBefore: Int,      // Текущая серия успехов (consecutiveCorrect)
    val daysSinceLastReview: Int,      // Дней с прошлого повторения

    // Семантические связи
    val linkedCardsCount: Int,         // Количество связанных карточек (из linkedCardIds)
    val linkedCardsMastery: Float,     // Средняя выученность связанных (0-1)

    // Результат (для обучения)
    val wasCorrect: Boolean            // true - вспомнил, false - забыл
) {
    companion object {
        fun from(
            card: Card,
            context: AIContext,
            quality: Int,
            responseTimeMs: Long,
            linkedCardsMastery: Float = 0.5f,
            correctRateBefore: Float
        ): ReviewLog {
            val calendar = Calendar.getInstance()

            return ReviewLog(
                cardId = card.id,
                deckId = card.deckId,
                quality = quality,
                cardTextLength = card.front.length + card.back.length,
                hasFormulas = card.isFormula,
                questionType = card.questionType,
                difficultyScore = card.difficultyScore,
                responseTimeMs = responseTimeMs,
                hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
                fatigueLevel = context.userFatigueLevel,
                cardsReviewedInSession = context.cardsReviewedInSession,
                totalReviewsBefore = card.totalReviews,
                correctRateBefore = card.successRate,
                streakCorrectBefore = card.consecutiveCorrect,
                daysSinceLastReview = if (card.lastReviewed != null) {
                    ((System.currentTimeMillis() - card.lastReviewed!!) / (24 * 60 * 60 * 1000)).toInt()
                } else 0,
                linkedCardsCount = extractLinkedCardsCount(card.linkedCardIds),
                linkedCardsMastery = linkedCardsMastery,
                wasCorrect = quality == 1
            )
        }

        private fun extractLinkedCardsCount(linkedCardIds: String): Int {
            return if (linkedCardIds.isNotBlank()) linkedCardIds.split(",").size else 0
        }
    }
}