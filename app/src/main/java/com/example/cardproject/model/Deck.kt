package com.example.cardproject.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize
import java.util.Date

@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val cardCount: Int = 0,
    val coverColor: String? = null,
    val learningMode: LearningMode = LearningMode.LONG_TERM
)

@Parcelize
enum class LearningMode: Parcelable {
    LONG_TERM, // Долговременный
    SHORT_TERM // Кратковременный
}