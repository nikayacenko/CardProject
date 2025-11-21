package com.example.cardproject.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "session_stats") // ДЛЯ БАЗЫ ДАННЫХ
@Parcelize // ДЛЯ ПЕРЕДАЧИ МЕЖДУ FRAGMENT/ACTIVITY
data class SessionStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val deckName: String,
    val sessionType: SessionType,
    val totalCards: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val sessionDuration: Long,
    val date: Long = System.currentTimeMillis(),
    val learningMode: LearningMode? = null,
    val isCompleted: Boolean = true
) : Parcelable {

    val accuracy: Double
        get() = if (totalCards > 0) {
            (correctAnswers.toDouble() / totalCards.toDouble()) * 100
        } else {
            0.0
        }

    val formattedDuration: String
        get() = formatDuration(sessionDuration)


    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            "${hours}ч ${minutes % 60}м"
        } else if (minutes > 0) {
            "${minutes}м ${seconds % 60}с"
        } else {
            "${seconds}с"
        }
    }
}

@Parcelize
enum class SessionType : Parcelable {
    SPACED_REPETITION,
    FULL_REVIEW
}