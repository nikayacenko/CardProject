package com.example.cardproject.test

import com.example.cardproject.model.QuestionType
import kotlin.math.pow
import kotlin.random.Random

/**
 * Виртуальный студент для симуляции обучения
 */
class VirtualStudent(
    val baseMemoryStrength: Float = 0.95f,      // Базовая способность запоминания
    val morningEfficiency: Float = 1.1f,       // Эффективность утром
    val eveningEfficiency: Float = 0.9f,      // Эффективность вечером
    val fatigueSensitivity: Float = 0.03f,      // Чувствительность к усталости
    val randomSeed: Int = 42
) {

    private val random = Random(randomSeed)
    val memoryLog = mutableListOf<MemoryTest>()


    private val cardMemory = mutableMapOf<Long, MutableList<MemoryTest>>()

    fun tryToRecall(
        cardId: Long = 0,
        intervalDays: Float,
        hourOfDay: Int,
        fatigue: Float,
        questionType: QuestionType = QuestionType.FACT
    ): Boolean {
        //println("   Студент получил intervalDays = $intervalDays")  // ← добавить

        // Кривая забывания Эббингауза
//        val retention = 0.95f.pow(intervalDays)
//        val retention = exp(-intervalDays / 5.0).toFloat()
//        val retention = 0.9f.pow(kotlin.math.sqrt(intervalDays.toDouble()).toFloat())

//        val retention = 0.95f.pow(intervalDays / 7f)
        val retention = 0.95f.pow(intervalDays / 14f)

//        val retention = 1.0f / (1.0f + 0.5f * intervalDays)


        // моделирование циркадных ритмов
        val timeModifier = when (hourOfDay) {
            in 7..10 -> 1.05f      // Утро - пик
            in 11..14 -> 1f     // День - хорошо
            in 15..17 -> 0.98f    // После обеда - спад
            in 18..22 -> 0.97f    // Вечер - хуже
            else -> 0.95f         // Ночь - плохо
        }

        // Влияние сложности карточки
        val typeDifficulty = when (questionType) {
            QuestionType.FACT -> 0.0f
            QuestionType.DEFINITION -> 0.03f
            QuestionType.PROOF -> 0.07f
        }

        // Влияние усталости
        val fatiguePenalty = (fatigue * fatigueSensitivity).coerceIn(0f, 0.2f)

        // Базовая способность студента
        val base = baseMemoryStrength

        val tests = cardMemory[cardId] ?: mutableListOf()
        val successStreak = tests.takeLast(3).count { it.wasCorrect }
        val repetitionBonus = successStreak * 0.05f // +5% за каждую недавнюю победу

        // Финальная вероятность
        val finalProbability = (baseMemoryStrength * retention * timeModifier) -
                typeDifficulty - fatiguePenalty + repetitionBonus
            .coerceIn(0.01f, 0.99f)

        val clampedProbability = finalProbability.coerceIn(0.60f, 0.99f)

        val adjustedProbability = finalProbability.coerceIn(0.30f, 0.98f)


//        val result = random.nextFloat() < clampedProbability
        // Даже если вероятность 99%, есть 2-3% шанс, что студент просто затупит
        val brainFartChance = 0.01f
        val roll = random.nextFloat()

        // Итоговый результат:
        // Если выпал brainFart (roll < 0.03) -> ошибка.
        // В остальных случаях -> обычная проверка по вероятности.
        val result = if (roll < brainFartChance) {
            println("Студент внезапно забыл карточку.")
            false
        } else {
            roll < adjustedProbability
        }
        // Логируем тест
        memoryLog.add(MemoryTest(
            intervalDays = intervalDays as Float,
            hourOfDay = hourOfDay,
            fatigue = fatigue,
            questionType = questionType,
            probability = finalProbability,
            wasCorrect = result
        ))
        if (cardId > 0) {
            cardMemory.getOrPut(cardId) { mutableListOf() }.add(
                MemoryTest(intervalDays, hourOfDay, fatigue, questionType, finalProbability, result)
            )
        }

        return result
    }

    fun getCorrectRateForCard(cardId: Long): Float {
        val tests = cardMemory[cardId] ?: return 0.5f
        if (tests.isEmpty()) return 0.5f
        val correct = tests.count { it.wasCorrect }
        return correct.toFloat() / tests.size
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
            СТАТИСТИКА СТУДЕНТА:
            Всего тестов: $totalTests
            Правильных: ${(correctRate * 100).toInt()}%
            Средняя вероятность: ${(avgProbability * 100).toInt()}%
            Утром: ${(morningSuccess * 100).toInt()}%
            Вечером: ${(eveningSuccess * 100).toInt()}%
        """.trimIndent()
    }
}