package com.example.cardproject.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "review_logs")
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,            // К какой карте относится
    val timestamp: Long,         // Когда было повторение
    val quality: Int,            // 0 или 1 (знаю/не знаю)
    val responseTimeMs: Long,    // Как быстро ответил
    //контекст обучение
    val intervalBefore: Long,     // Какой интервал был ДО
    val intervalAfter: Long,       // Какой интервал стал ПОСЛЕ

    //ML фичи
    val hourOfDay: Int,            // Время суток

    // Для сравнения алгоритмов
    val algorithmUsed: String = "SM2",
    val modelVersion: String? = null // Какая версия нейросети работала
)