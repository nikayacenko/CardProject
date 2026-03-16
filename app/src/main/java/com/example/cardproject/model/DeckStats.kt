package com.example.cardproject.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeckStats(
    val deckId: Long,
    val deckName: String,
    val totalCards: Int,
    val learnedCards: Int, // Выученные карточки
    val inProgressCards: Int, // В процессе изучения
    val newCards: Int, // Новые/незнакомые
    val totalSessions: Int,
    val totalStudyTime: Long,
    val averageAccuracy: Double,
    val cardsByStatus: Map<CardStatus, Int>,
    val recentAccuracy: List<Double>, // Последние 10 сессий

    val averageDifficulty: Float,       // Средняя сложность колоды по версии ИИ
    val averageResponseTimeMs: Long,    // Базовая скорость ответа для этой колоды
    val conceptualMastery: Float,       // Процент усвоения связей (графа)
    val orphanCardsCount: Int,          // Сколько карт еще не связаны ни с чем
    val totalReviewsCount: Int,         // Общее кол-во подходов (для расчета опыта)
    val responseTimeTrend: Float   // История времени ответов для графика усталости
) : Parcelable {

    val learnedPercentage: Double
        get() = if (totalCards > 0) (learnedCards * 100.0) / totalCards else 0.0

    val inProgressPercentage: Double
        get() = if (totalCards > 0) (inProgressCards * 100.0) / totalCards else 0.0

    val newCardsPercentage: Double
        get() = if (totalCards > 0) (newCards * 100.0) / totalCards else 0.0

    val formattedTotalTime: String
        get() = formatDuration(totalStudyTime)

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            "${hours}ч ${minutes % 60}м"
        } else if (minutes > 0) {
            "${minutes}м"
        } else {
            "${seconds}с"
        }
    }

    // Оценка "силы" знаний в колоде (от 0 до 100)
    val knowledgeStrength: Int
        get() = ((learnedPercentage * 0.7) + (conceptualMastery * 100 * 0.3)).toInt()

    // Рекомендация ИИ по объему следующей сессии
    val recommendedSessionSize: Int
        get() = when {
            averageDifficulty > 0.7f -> 15 // Колода сложная, не перегружаем
            averageAccuracy < 60.0 -> 20   // Много ошибок, уменьшаем объем
            else -> 40                     // Всё хорошо, можно учить много
        }

}


enum class CardStatus {
    NEW, // Новые (reviewStage = 0)
    IN_PROGRESS, // В процессе (1 <= reviewStage < 3)
    LEARNED, // Выученные (reviewStage >= 3)
    LOCKED // Карточка заблокирована (граф), так как не выучены "предки"
}