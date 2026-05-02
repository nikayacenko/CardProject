package com.example.cardproject.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Матрица ошибок для оценки ML модели
 */
data class MLConfusionMatrix(
    val truePositive: Int = 0,    // Предсказал "вспомнит" → вспомнил
    val falsePositive: Int = 0,   // Предсказал "вспомнит" → забыл
    val falseNegative: Int = 0,   // Предсказал "забудет" → вспомнил
    val trueNegative: Int = 0     // Предсказал "забудет" → забыл
) {
    // Всего предсказаний
    val total: Int get() = truePositive + falsePositive + falseNegative + trueNegative

    // Точность (Accuracy) = (TP + TN) / Total
    val accuracy: Float get() = if (total > 0) (truePositive + trueNegative).toFloat() / total else 0f

    // Полнота (Recall/TPR) = TP / (TP + FN) - насколько хорошо находит положительные случаи
    val recall: Float get() = if (truePositive + falseNegative > 0) truePositive.toFloat() / (truePositive + falseNegative) else 0f

    // Точность (Precision) = TP / (TP + FP) - насколько точны положительные предсказания
    val precision: Float get() = if (truePositive + falsePositive > 0) truePositive.toFloat() / (truePositive + falsePositive) else 0f

    // F1-score = 2 * (Precision * Recall) / (Precision + Recall)
    val f1Score: Float get() = if (precision + recall > 0) 2 * (precision * recall) / (precision + recall) else 0f

    // Специфичность (TNR) = TN / (TN + FP) - насколько хорошо находит отрицательные случаи
    val specificity: Float get() = if (trueNegative + falsePositive > 0) trueNegative.toFloat() / (trueNegative + falsePositive) else 0f

    fun toJson(): String {
        return """{"tp":$truePositive,"fp":$falsePositive,"fn":$falseNegative,"tn":$trueNegative}"""
    }

    companion object {
        fun fromJson(json: String): MLConfusionMatrix {
            val pattern = Regex("""tp":(\d+),"fp":(\d+),"fn":(\d+),"tn":(\d+)""")
            val match = pattern.find(json)
            return if (match != null) {
                MLConfusionMatrix(
                    truePositive = match.groupValues[1].toInt(),
                    falsePositive = match.groupValues[2].toInt(),
                    falseNegative = match.groupValues[3].toInt(),
                    trueNegative = match.groupValues[4].toInt()
                )
            } else MLConfusionMatrix()
        }
    }
}

@Entity(tableName = "daily_ml_stats")
data class DailyMLStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val deckId: Long,
    val matrixJson: String,
    val predictionsCount: Int
) {
    fun getMatrix(): MLConfusionMatrix = MLConfusionMatrix.fromJson(matrixJson)
}