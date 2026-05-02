package com.example.cardproject.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId"), Index("nextReview")]
)
data class Card(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val createdAt: Long = System.currentTimeMillis(),
    var lastReviewed: Long? = null,
    var nextReview: Long? = null,
    var easeFactor: Double = 2.5,
    var interval: Double = 1.0,
    var reviewStage: Int = 0, // 0 - новая, 1-4 - этапы повторения
    var consecutiveCorrect: Int = 0, // последовательные правильные ответы

    //метрики сложности
    val difficultyScore: Float = 0.5f,
    val isFormula: Boolean = false,
    val wordCount: Int = 0,
    val questionType: String = "FACT", // FACT, PROOF

    //поведенческие факторы
    val averageResponseTimeMs: Long = 0,
    val totalReviews: Int = 0,
    val lastResponseTimeMs: Long = 0, // Время последнего ответа (усталость)
    var successRate: Float = 0f,

    //связь
    val linkedCardIds: String = "", // "12,45,67"

    var lastFiveResults: String = "",

    //управление алгоритмом
    val algorithmType: String = "ML", // Для сравнения в курсовой (SM2 vs ML)
    val masteryLevel: Float = 0f,
    var lastPredictedProbability: Float = 0f // Уверенность ИИ в вашем ответе
){
    fun calculateMetadata(): Card {
        val fullText = "$front $back"

        // 1. Подсчет слов
        val words = fullText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val calculatedWordCount = words.size

        // 2. Определение формул
        val hasFormula = detectFormula(fullText)

        // 3. РАСЧЕТ СЛОЖНОСТИ (0-1)
        val calculatedDifficulty = calculateDifficultyScore(
            questionType = this.questionType,
            wordCount = calculatedWordCount,
            hasFormula = hasFormula,
            frontLength = front.length,
            backLength = back.length
        )

        println("📊 РАСЧЕТ СЛОЖНОСТИ для карточки:")
        println("   - Тип: $questionType")
        println("   - Слов: $calculatedWordCount")
        println("   - Формулы: $hasFormula")
        println("   - Сложность: $calculatedDifficulty")

        return this.copy(
            wordCount = calculatedWordCount,
            isFormula = hasFormula,
            difficultyScore = calculatedDifficulty
        )
    }

    /**
     * ДЕТАЛЬНЫЙ РАСЧЕТ СЛОЖНОСТИ
     * Учитывает 5 факторов:
     * 1. Тип вопроса (30%)
     * 2. Длина текста (25%)
     * 3. Наличие формул (20%)
     * 4. Структура ответа (15%)
     * 5. Специфические маркеры (10%)
     */
    private fun calculateDifficultyScore(
        questionType: String,
        wordCount: Int,
        hasFormula: Boolean,
        frontLength: Int,
        backLength: Int
    ): Float {

        // ФАКТОР 1: ТИП ВОПРОСА (вес 40%) - увеличил вес
        val typeFactor = when (QuestionType.fromString(questionType)) {
            QuestionType.FACT -> 0.25f
            QuestionType.DEFINITION -> 0.55f
            QuestionType.PROOF -> 0.85f
        }

        // ФАКТОР 2: ДЛИНА ТЕКСТА (вес 25%)
        val lengthFactor = when {
            wordCount <= 10 -> 0.1f
            wordCount <= 25 -> 0.3f
            wordCount <= 50 -> 0.5f
            else -> 0.7f
        }

        // ФАКТОР 3: НАЛИЧИЕ ФОРМУЛ (вес 20%)
        val formulaFactor = if (hasFormula) 0.8f else 0.2f

        // ИТОГОВЫЙ РАСЧЕТ
        val finalDifficulty =
            typeFactor * 0.45f +
                    lengthFactor * 0.30f +
                    formulaFactor * 0.25f

        // Убираем шум, просто округляем до 2 знаков
        return (finalDifficulty * 100).toInt() / 100.0f
    }

    /**
     * Определение наличия формул
     */
    private fun detectFormula(text: String): Boolean {
        val formulaPatterns = listOf(
            Regex("""\$.*?\$"""),           // $math$
            Regex("""\$\$.*?\$\$"""),       // $$math$$
            Regex("""\\[a-zA-Z]+"""),       // \frac, \sqrt
            Regex("""[a-z]_\d+"""),         // x_1, y_2
            Regex("""[a-z]\^\d+"""),        // x^2, y^3
            Regex("""\d+[\+\-\*\/]\d+"""),  // 2+2, 3*4
            Regex("""[=≠≤≥<>]"""),          // операторы сравнения
            Regex("""\b(sin|cos|tan|log|ln|sqrt|sum|int)\b""", RegexOption.IGNORE_CASE),
            Regex("""[αβγδεζηθικλμνξοπρστυφχψω]""") // греческие буквы
        )
        return formulaPatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Получить тип вопроса как enum
     */
    fun getQuestionTypeEnum(): QuestionType {
        return QuestionType.fromString(questionType)
    }

    /**
     * Получить закодированное значение для ML
     */
    fun getEncodedQuestionType(): Float {
        return QuestionType.encode(getQuestionTypeEnum())
    }
}
