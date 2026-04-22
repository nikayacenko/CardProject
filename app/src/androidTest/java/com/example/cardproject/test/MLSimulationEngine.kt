// File: app/src/test/java/com/example/cardproject/test/MLSimulationEngine.kt
package com.example.cardproject.test

import android.util.Log
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.ml.MLSpacedRepetitionCalculator
import com.example.cardproject.model.AIContext
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.ReviewLog
import kotlinx.coroutines.delay

class MLSimulationEngine {

    private lateinit var mlCalculator: MLSpacedRepetitionCalculator
    private lateinit var reviewLogRepository: ReviewLogRepository

    private val simulationResults = mutableListOf<SimulationDay>()
    private var mlPredictions = 0
    private var mlErrors = 0
    private var mlSuccesses = 0

    /**
     * Инициализация с реальным ML калькулятором
     */
    fun initializeWithRealML(
        calculator: MLSpacedRepetitionCalculator
//        repository: ReviewLogRepository
    ) {
        this.mlCalculator = calculator
//        this.reviewLogRepository = repository
        println("✅ Реальный ML калькулятор инициализирован")
        android.util.Log.d("ML_TEST", "✅ Движок инициализирован. Модель готова: ${calculator != null}")
    }

    /**
     * Симуляция с реальным ML и виртуальным студентом
     */
    suspend fun runSimulationWithML(
        cards: List<Card>,
        student: VirtualStudent,
        days: Int = 60,
        mode: LearningMode = LearningMode.LONG_TERM,
        shouldSaveLogs: Boolean = false
    ): SimulationReport {

//        println("=".repeat(60))
//        println("🚀 СИМУЛЯЦИЯ С РЕАЛЬНЫМ ML И ВИРТУАЛЬНЫМ СТУДЕНТОМ")
//        println("=".repeat(60))
//        println("Карточек: ${cards.size}")
//        println("Режим: $mode")
//        println("Дней: $days")
//        println()

        val activeCards = cards.map { it.copy() }.toMutableList()
        var currentTime = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        simulationResults.clear()
        mlPredictions = 0
        mlErrors = 0
        mlSuccesses = 0

        // Очищаем логи перед симуляцией
        if (shouldSaveLogs) {
            clearLogs()
        }

        for (day in 1..days) {
            val dayResults = simulateDayWithML(
                day = day,
                cards = activeCards,
                student = student,
                mode = mode,
                currentTime = currentTime,
                shouldSaveLogs = shouldSaveLogs
            )
            simulationResults.add(dayResults)

            currentTime += oneDayMs

            if (day % 10 == 0 || day == 1) {
                println(dayResults.getSummary())
            }

            // Небольшая задержка для предотвращения перегрузки
            delay(20)
        }

        val report = generateReport(activeCards, student)

        println("\n" + report.getFullReport())
        println("\n🤖 ML СТАТИСТИКА:")
        println("   • Предсказаний: $mlPredictions")
        println("   • Ошибок: $mlErrors")
        println("   • Успешно: $mlSuccesses")
        println("   • Точность: ${"%.1f".format(mlAccuracy())}%")

        return report
    }

    /**
     * Симуляция без ML (для сравнения)
     */
    suspend fun runSimulationWithoutML(
        cards: List<Card>,
        student: VirtualStudent,
        days: Int = 60,
        mode: LearningMode = LearningMode.LONG_TERM,
        currentTime: Long,
        shouldSaveLogs: Boolean
    ): SimulationReport {

        println("=".repeat(60))
        println("🚀 СИМУЛЯЦИЯ БЕЗ ML (FALLBACK)")
        println("=".repeat(60))

        val activeCards = cards.map { it.copy() }.toMutableList()
        var currentTime = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        simulationResults.clear()

        for (day in 1..days) {
            val dayResults = simulateDayWithoutML(
                day = day,
                cards = activeCards,
                student = student,
                mode = mode,
                currentTime = currentTime
            )
            simulationResults.add(dayResults)

            currentTime += oneDayMs

            if (day % 10 == 0 || day == 1) {
                println(dayResults.getSummary())
            }
        }

        return generateReport(activeCards, student)
    }

    /**
     * Симуляция одного дня с ML
     */
    private suspend fun simulateDayWithML(
        day: Int,
        cards: MutableList<Card>,
        student: VirtualStudent,
        mode: LearningMode,
        currentTime: Long,
        shouldSaveLogs: Boolean
    ): SimulationDay {

        val reviewedCards = mutableListOf<Card>()
        val results = mutableListOf<Pair<Card, Boolean>>()

        // Определяем время дня
        val hourOfDay = when (day % 3) {
            0 -> 9   // Утро
            1 -> 15  // День
            else -> 20 // Вечер
        }

        // Усталость зависит от времени
        val fatigue = when {
            hourOfDay > 18 -> 0.7f + (day % 5) * 0.05f
            hourOfDay < 10 -> 0.2f
            else -> 0.4f
        }.coerceIn(0.1f, 0.9f)

        val context = AIContext(
            sessionStartTime = currentTime,
            cardsReviewedInSession = 0,
            currentHour = hourOfDay,
            dayOfWeek = (day % 7) + 1,
            userFatigueLevel = fatigue,
            averageResponseTimeMs = 3000 + (fatigue * 2000).toLong(),
            consecutiveCorrectInSession = 0,
            consecutiveIncorrectInSession = 0,
            learningMode = mode
        )
        val dayMs = 24 * 60 * 60 * 1000L
        for (card in cards) {
            // Проверяем, пришло ли время повторять
            val shouldReview = card.nextReview == null || card.nextReview!! <= currentTime

            if (shouldReview) {
                // Исправленный расчет интервала
                val lastReviewedTime = card.lastReviewed ?: (currentTime - dayMs)
                val diffMs = currentTime - lastReviewedTime

                // Важно: переводим мс в дни правильно
                val actualIntervalDays = (diffMs.toFloat() / dayMs.toFloat()).coerceAtLeast(0f)

                // Логируем для отладки (теперь тут будет 1.0, а не 8.64E7)
                Log.d("SIM_DEBUG", "День $day: Карточка ${card.id} | Интервал: %.2f дн.".format(actualIntervalDays))

                val questionType = com.example.cardproject.model.QuestionType.fromString(card.questionType)

                // Виртуальный студент пытается вспомнить
                val wasCorrect = student.tryToRecall(
                    intervalDays = actualIntervalDays,
                    hourOfDay = hourOfDay,
                    fatigue = fatigue,
                    questionType = questionType
                )

                val quality = if (wasCorrect) 1 else 0
                val responseTime = (2000f + fatigue * 3000f).toLong()

                try {
                    mlPredictions++

                    // ИСПОЛЬЗУЕМ РЕАЛЬНЫЙ ML КАЛЬКУЛЯТОР
                    val updatedCard = mlCalculator.calculateNextReviewWithoutSaving(
                        card = card,
                        context = context.copy(
                            sessionStartTime = currentTime,
                            cardsReviewedInSession = reviewedCards.size,
                            consecutiveCorrectInSession = results.count { it.second }
                        ),
                        learningMode = mode,
                        quality = quality,
                        responseTimeMs = responseTime
                    )

                    mlSuccesses++

                    val index = cards.indexOfFirst { it.id == card.id }
                    if (index >= 0) {
                        cards[index] = updatedCard
                        reviewedCards.add(updatedCard)
                        results.add(Pair(updatedCard, wasCorrect))
                    }

                } catch (e: Exception) {
                    android.util.Log.e("SIM_DEBUG", "❌ ОШИБКА ML на карточке ${card.id}: ${e.message}")
                    mlErrors++
                    println("   ❌ Ошибка ML: ${e.message}")

                    // Fallback при ошибке
                    val fallbackCard = fallbackSM2(card, quality, mode)
                    val index = cards.indexOfFirst { it.id == card.id }
                    if (index >= 0) {
                        cards[index] = fallbackCard
                        reviewedCards.add(fallbackCard)
                        results.add(fallbackCard to wasCorrect)
                    }
                }
            }
        }

        return SimulationDay(
            day = day,
            hourOfDay = hourOfDay,
            fatigue = fatigue,
            reviewedCount = reviewedCards.size,
            totalCards = cards.size,
            correctCount = results.count { it.second },
            results = results
        )
    }

    /**
     * Симуляция дня без ML
     */
    private suspend fun simulateDayWithoutML(
        day: Int,
        cards: MutableList<Card>,
        student: VirtualStudent,
        mode: LearningMode,
        currentTime: Long
    ): SimulationDay {

        val reviewedCards = mutableListOf<Card>()
        val results = mutableListOf<Pair<Card, Boolean>>()

        val hourOfDay = when (day % 3) {
            0 -> 9
            1 -> 15
            else -> 20
        }

        val fatigue = when {
            hourOfDay > 18 -> 0.7f
            hourOfDay < 10 -> 0.2f
            else -> 0.4f
        }

        for (card in cards) {
            val shouldReview = card.nextReview == null || card.nextReview!! <= currentTime

            if (shouldReview) {
                val intervalDays = if (card.lastReviewed != null) {
                    (currentTime - card.lastReviewed!!) / oneDayMs.toFloat()
                } else 0f

                val questionType = com.example.cardproject.model.QuestionType.fromString(card.questionType)

                val wasCorrect = student.tryToRecall(
                    intervalDays = intervalDays,
                    hourOfDay = hourOfDay,
                    fatigue = fatigue,
                    questionType = questionType
                )

                // Простой SM-2
                val updatedCard = if (wasCorrect) {
                    val newInterval = when {
                        card.interval <= 1 -> 3
                        card.interval <= 3 -> 7
                        card.interval <= 7 -> 14
                        card.interval <= 14 -> 30
                        else -> (card.interval * 1.5).toInt().coerceAtMost(365)
                    }

                    card.copy(
                        lastReviewed = System.currentTimeMillis(),
                        nextReview = System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L),
                        interval = newInterval.toDouble(),
                        consecutiveCorrect = card.consecutiveCorrect + 1,
                        totalReviews = card.totalReviews + 1
                    )
                } else {
                    card.copy(
                        lastReviewed = System.currentTimeMillis(),
                        nextReview = System.currentTimeMillis() + (24 * 60 * 60 * 1000L),
                        interval = 1.0,
                        consecutiveCorrect = 0,
                        totalReviews = card.totalReviews + 1
                    )
                }

                val index = cards.indexOfFirst { it.id == card.id }
                if (index >= 0) {
                    cards[index] = updatedCard
                    reviewedCards.add(updatedCard)
                    results.add(updatedCard to wasCorrect)
                }
            }
        }

        return SimulationDay(
            day = day,
            hourOfDay = hourOfDay,
            fatigue = fatigue,
            reviewedCount = reviewedCards.size,
            totalCards = cards.size,
            correctCount = results.count { it.second },
            results = results
        )
    }
    private suspend fun calculateWithML(
        card: Card,
        context: AIContext,
        quality: Int,
        responseTimeMs: Long,
        mode: LearningMode
    ): Card {
        // Вызываем новый метод без сохранения в БД
        return mlCalculator.calculateNextReviewWithoutSaving(
            card = card,
            context = context,
            learningMode = mode,
            quality = quality,
            responseTimeMs = responseTimeMs
        )
    }

    private fun fallbackSM2(card: Card, quality: Int, mode: LearningMode): Card {
        return if (quality == 1) {
            val newInterval = when {
                card.interval <= 1 -> 3
                card.interval <= 3 -> 7
                card.interval <= 7 -> 14
                card.interval <= 14 -> 30
                else -> (card.interval * 1.5).toInt().coerceAtMost(365)
            }

            card.copy(
                lastReviewed = System.currentTimeMillis(),
                nextReview = System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L),
                interval = newInterval.toDouble(),
                consecutiveCorrect = card.consecutiveCorrect + 1,
                totalReviews = card.totalReviews + 1
            )
        } else {
            card.copy(
                lastReviewed = System.currentTimeMillis(),
                nextReview = System.currentTimeMillis() + (24 * 60 * 60 * 1000L),
                interval = 1.0,
                consecutiveCorrect = 0,
                totalReviews = card.totalReviews + 1
            )
        }
    }

    private suspend fun clearLogs() {
        // Здесь можно добавить очистку логов перед симуляцией
        // reviewLogRepository.deleteAll()
    }

    private fun generateReport(cards: List<Card>, student: VirtualStudent): SimulationReport {
        val studentStats = student.getStats()

        val mastered = cards.count { it.interval > 30 }
        val learning = cards.count { it.interval in 1.0..30.0 }
        val new = cards.count { it.lastReviewed == null }

        val avgInterval = cards.filter { it.interval > 0 }
            .map { it.interval }
            .average()

        val maxInterval = cards.maxOfOrNull { it.interval } ?: 0.0

        val totalReviews = simulationResults.sumOf { it.reviewedCount }
        val totalCorrect = simulationResults.sumOf { it.correctCount }
        val overallAccuracy = if (totalReviews > 0) totalCorrect * 100 / totalReviews else 0

        return SimulationReport(
            totalDays = simulationResults.size,
            totalCards = cards.size,
            masteredCards = mastered,
            learningCards = learning,
            newCards = new,
            avgInterval = avgInterval,
            maxInterval = maxInterval,
            studentStats = studentStats,
            dailyReviews = simulationResults.map { it.reviewedCount },
            dailyCorrect = simulationResults.map { it.correctCount },
            simulationResults = simulationResults,
            mlPredictions = mlPredictions,
            mlErrors = mlErrors,
            mlSuccesses = mlSuccesses,
            overallAccuracy = overallAccuracy
        )
    }

    private fun mlAccuracy(): Double {
        return if (mlPredictions > 0) {
            (mlSuccesses.toDouble() / mlPredictions) * 100
        } else 0.0
    }

    companion object {
        private val oneDayMs = 24 * 60 * 60 * 1000L
    }
}
data class SimulationDay(
    val day: Int,
    val hourOfDay: Int,
    val fatigue: Float,
    val reviewedCount: Int,
    val totalCards: Int,
    val correctCount: Int,
    val results: List<Pair<Card, Boolean>>
) {
    fun getSummary(): String {
        val percent = if (reviewedCount > 0) correctCount * 100 / reviewedCount else 0
        return String.format(
            "📅 День %3d | %2d:00 | Усталость: %.0f%% | Повторений: %3d | Правильно: %3d (%d%%)",
            day, hourOfDay, fatigue * 100, reviewedCount, correctCount, percent
        )
    }
}
data class SimulationReport(
    val totalDays: Int,
    val totalCards: Int,
    val masteredCards: Int,
    val learningCards: Int,
    val newCards: Int,
    val avgInterval: Double,
    val maxInterval: Double,
    val studentStats: VirtualStudent.StudentStats,
    val dailyReviews: List<Int>,
    val dailyCorrect: List<Int>,
    val simulationResults: List<SimulationDay>,
    val mlPredictions: Int = 0,
    val mlErrors: Int = 0,
    val mlSuccesses: Int = 0,
    val overallAccuracy: Int = 0
) {
    val mlAccuracy: Double
        get() = if (mlPredictions > 0) {
            (mlSuccesses.toDouble() / mlPredictions) * 100
        } else 0.0
    fun getFullReport(): String {
        return """
            
            📊 ИТОГОВЫЙ ОТЧЕТ СИМУЛЯЦИИ
            ==============================
            
            🗓 Период: $totalDays дней
            🃏 Карточки: $totalCards
            
            📈 ПРОГРЕСС:
            • Выучено (интервал > 30): $masteredCards (${masteredCards * 100 / totalCards}%)
            • В процессе: $learningCards
            • Новых: $newCards
            
            ⏱ ИНТЕРВАЛЫ:
            • Средний: ${"%.1f".format(avgInterval)} дней
            • Максимальный: $maxInterval дней
            
            📊 ПОВТОРЕНИЯ:
            • Всего: ${dailyReviews.sum()}
            • В среднем в день: ${"%.1f".format(dailyReviews.average())}
            • Правильных: ${dailyCorrect.sum()}
            • Точность: $overallAccuracy%
            
            🤖 ML СТАТИСТИКА:
            • Предсказаний: $mlPredictions
            • Ошибок: $mlErrors
            • Успешно: $mlSuccesses
            • Точность ML: ${if (mlPredictions > 0) (mlSuccesses * 100 / mlPredictions) else 0}%
            
            ${studentStats}
        """.trimIndent()
    }

    fun exportToCSV(): String {
        val sb = StringBuilder()
        sb.appendln("Day,Hour,Fatigue,Reviewed,Correct,Accuracy")

        simulationResults.forEach { day ->
            val accuracy = if (day.reviewedCount > 0)
                day.correctCount * 100 / day.reviewedCount else 0
            sb.appendln("${day.day},${day.hourOfDay},${"%.2f".format(day.fatigue)},${day.reviewedCount},${day.correctCount},$accuracy")
        }

        return sb.toString()
    }
    fun getMasteredHistory(): List<Int> {
        return simulationResults.map { day ->
            day.results.count { it.first.interval > 30 }
        }
    }
}