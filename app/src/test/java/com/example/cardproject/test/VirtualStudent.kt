// File: app/src/test/java/com/example/cardproject/test/VirtualStudent.kt
package com.example.cardproject.test

import com.example.cardproject.model.QuestionType
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

/**
 * Виртуальный студент для симуляции обучения
 */
class VirtualStudent(
    val baseMemoryStrength: Float = 0.95f,      // Базовая способность запоминания
    val morningEfficiency: Float = 1.1f,       // Эффективность утром
    val eveningEfficiency: Float = 0.9f,      // Эффективность вечером
    val fatigueSensitivity: Float = 0.08f,      // Чувствительность к усталости
    val randomSeed: Int = 42
) {

    private val random = Random(randomSeed)
    val memoryLog = mutableListOf<MemoryTest>()


    fun tryToRecall(
        intervalDays: Float,
        hourOfDay: Int,
        fatigue: Float,
        questionType: QuestionType = QuestionType.FACT
    ): Boolean {

        // 1. Кривая забывания Эббингауза
//        val retention = 0.95f.pow(intervalDays)
//        val retention = exp(-intervalDays / 5.0).toFloat()
//        val retention = 0.9f.pow(kotlin.math.sqrt(intervalDays.toDouble()).toFloat())
        val retention = 0.95f.pow(intervalDays / 7f)
//        val retention = 1.0f / (1.0f + 0.5f * intervalDays)


        // 2. Влияние времени суток (циркадные ритмы)
        val timeModifier = when (hourOfDay) {
            in 7..10 -> 1.2f      // Утро - пик
            in 11..14 -> 1.1f     // День - хорошо
            in 15..17 -> 0.95f    // После обеда - спад
            in 18..22 -> 0.85f    // Вечер - хуже
            else -> 0.75f         // Ночь - плохо
        }

        // 3. Влияние сложности карточки
        val typeDifficulty = when (questionType) {
            QuestionType.FACT -> 0.0f
            QuestionType.DEFINITION -> 0.03f
            QuestionType.PROOF -> 0.07f
        }

        // 4. Влияние усталости
        val fatiguePenalty = (fatigue * fatigueSensitivity).coerceIn(0f, 0.2f)

        // 5. Базовая способность студента
        val base = baseMemoryStrength

        val repetitionBonus = 0.0f // будет добавляться из истории


        // Финальная вероятность
        val finalProbability = (baseMemoryStrength * retention * timeModifier) -
                typeDifficulty - fatiguePenalty + repetitionBonus
            .coerceIn(0.01f, 0.99f)

        val clampedProbability = finalProbability.coerceIn(0.25f, 0.98f)

        println("   📊 Интервал=${"%.1f".format(intervalDays)}д, " +
                "удержание=${"%.0f".format(retention*100)}%, " +
                "вероятность=${"%.0f".format(clampedProbability*100)}%")

        val result = random.nextFloat() < clampedProbability
        // Логируем тест
        memoryLog.add(MemoryTest(
            intervalDays = intervalDays,
            hourOfDay = hourOfDay,
            fatigue = fatigue,
            questionType = questionType,
            probability = finalProbability,
            wasCorrect = result
        ))

        return result
    }

    /**
     * Получить статистику студента
     */
    fun getStats(): StudentStats {
        if (memoryLog.isEmpty()) return StudentStats(0, 0f, 0f, 0f, 0f)

        val total = memoryLog.size
        val correct = memoryLog.count { it.wasCorrect }
        val avgProbability = memoryLog.map { it.probability }.average().toFloat()

        // Анализ по времени суток
        val morningTests = memoryLog.filter { it.hourOfDay in 8..12 }
        val eveningTests = memoryLog.filter { it.hourOfDay in 18..22 }

        val morningCorrect = morningTests.count { it.wasCorrect }.toFloat() / maxOf(1, morningTests.size)
        val eveningCorrect = eveningTests.count { it.wasCorrect }.toFloat() / maxOf(1, eveningTests.size)

        val factTests = memoryLog.filter { it.questionType == QuestionType.FACT }
        val defTests = memoryLog.filter { it.questionType == QuestionType.DEFINITION }
        val proofTests = memoryLog.filter { it.questionType == QuestionType.PROOF }

        return StudentStats(
            totalTests = total,
            correctRate = correct.toFloat() / total,
            avgProbability = avgProbability,
            morningSuccess = morningCorrect,
            eveningSuccess = eveningCorrect,
            factSuccess = factTests.count { it.wasCorrect }.toFloat() / maxOf(1, factTests.size),
            definitionSuccess = defTests.count { it.wasCorrect }.toFloat() / maxOf(1, defTests.size),
            proofSuccess = proofTests.count { it.wasCorrect }.toFloat() / maxOf(1, proofTests.size)
        )
    }

    data class MemoryTest(
        val intervalDays: Float,
        val hourOfDay: Int,
        val fatigue: Float,
        val questionType: QuestionType,
        val probability: Float,
        val wasCorrect: Boolean
    )

    data class StudentStats(
        val totalTests: Int,
        val correctRate: Float,
        val avgProbability: Float,
        val morningSuccess: Float,
        val eveningSuccess: Float,
        val factSuccess: Float = 0f,
        val definitionSuccess: Float = 0f,
        val proofSuccess: Float = 0f
    ) {
        override fun toString(): String = """
            📊 СТАТИСТИКА СТУДЕНТА:
            • Всего тестов: $totalTests
            • Правильных: ${(correctRate * 100).toInt()}%
            • Средняя вероятность: ${(avgProbability * 100).toInt()}%
            • Утром: ${(morningSuccess * 100).toInt()}%
            • Вечером: ${(eveningSuccess * 100).toInt()}%
        """.trimIndent()
    }
}