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
    val recentAccuracy: List<Double> // Последние 10 сессий
) : Parcelable {

    val learnedPercentage: Double
        get() = if (totalCards > 0) (learnedCards.toDouble() / totalCards * 100) else 0.0

    val inProgressPercentage: Double
        get() = if (totalCards > 0) (inProgressCards.toDouble() / totalCards * 100) else 0.0

    val newCardsPercentage: Double
        get() = if (totalCards > 0) (newCards.toDouble() / totalCards * 100) else 0.0

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
}

enum class CardStatus {
    NEW, // Новые (reviewStage = 0)
    IN_PROGRESS, // В процессе (1 <= reviewStage < 3)
    LEARNED // Выученные (reviewStage >= 3)
}