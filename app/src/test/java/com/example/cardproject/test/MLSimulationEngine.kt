// File: app/src/test/java/com/example/cardproject/test/MLSimulationEngine.kt
package com.example.cardproject.test

import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.ml.MLSpacedRepetitionCalculator
import com.example.cardproject.ml.TensorFlowLiteModel
import com.example.cardproject.model.AIContext
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.QuestionType
import kotlin.math.max

/**
 * Движок для симуляции обучения
 */
class MLSimulationEngine {

    private lateinit var mlCalculator: MLSpacedRepetitionCalculator
    private lateinit var tensorFlowModel: TensorFlowLiteModel
    private lateinit var reviewLogRepository: ReviewLogRepository

    private val student = VirtualStudent()

    // Результаты симуляции
    private val simulationResults = mutableListOf<SimulationDay>()

    // Для замера производительности ML
    private var mlPredictions = 0
    private var mlErrors = 0
    /**
     * Инициализация с реальными зависимостями
     */
    fun initializeWithRealML(
        context: android.content.Context,
        database: com.example.cardproject.database.AppDatabase
    ) {
        // Создаем реальные репозитории
        reviewLogRepository = com.example.cardproject.database.repository.ReviewLogRepository(
            database.reviewLogDao()
        )

        // Создаем реальную ML модель
        tensorFlowModel = TensorFlowLiteModel(context)

        // Создаем реальный ML калькулятор
        mlCalculator = MLSpacedRepetitionCalculator(
            tfliteModel = tensorFlowModel,
            reviewLogRepository = reviewLogRepository
        )

        println("🤖 Реальный ML калькулятор инициализирован")
        println("   Модель загружена: ${tensorFlowModel.isModelReady.value}")
    }
    /**
     * Запуск симуляции на N дней
     */
    suspend fun runSimulation(
        cards: List<Card>,
        days: Int = 100,
        mode: LearningMode = LearningMode.LONG_TERM
    ): SimulationReport {

        println("🚀 ЗАПУСК СИМУЛЯЦИИ НА $days ДНЕЙ")
        println("Карточек: ${cards.size}")
        println("Режим: $mode")
        println()

        // Копируем карточки для симуляции
        val activeCards = cards.map { it.copy() }.toMutableList()
        var currentTime = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        simulationResults.clear()

        for (day in 1..days) {
            val dayResults = simulateDay(day, activeCards, mode, currentTime)
            simulationResults.add(dayResults)

            // Проматываем время
            currentTime += oneDayMs

            // Выводим прогресс каждые 10 дней
            if (day % 10 == 0 || day == 1) {
                println(dayResults.getSummary())
            }
        }

        val report = generateReport(activeCards)
        println("\n" + report.getFullReport())

        return report
    }

    /**
     * Симуляция одного дня
     */
    private suspend fun simulateDay(
        day: Int,
        cards: MutableList<Card>,
        mode: LearningMode,
        currentTime: Long
    ): SimulationDay {

        val reviewedCards = mutableListOf<Card>()
        val results = mutableListOf<Pair<Card, Boolean>>()

        // Определяем время дня (утро/день/вечер)
        val hourOfDay = when (day % 3) {
            0 -> 9   // Утро
            1 -> 15  // День
            else -> 20 // Вечер
        }

        // Усталость зависит от времени и дня недели
        val fatigue = when {
            hourOfDay > 18 -> 0.7f + (day % 5) * 0.05f  // Вечером устал
            hourOfDay < 10 -> 0.2f                       // Утром бодр
            else -> 0.4f                                  // Днем средне
        }.coerceIn(0.1f, 0.9f)

        // Контекст для ИИ
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

        // Проверяем каждую карточку
        for (card in cards) {
            // Нужно ли повторять сегодня?
            val shouldReview = card.nextReview == null || card.nextReview!! <= currentTime

            if (shouldReview) {
                // Интервал с прошлого раза
                val intervalDays = if (card.lastReviewed != null) {
                    (currentTime - card.lastReviewed!!) / oneDayMs.toFloat()
                } else 0f

                // Получаем QuestionType из строки
                val questionType = QuestionType.fromString(card.questionType)

                // Студент пытается вспомнить
                val wasCorrect = student.tryToRecall(
                    intervalDays = intervalDays,
                    hourOfDay = hourOfDay,
                    fatigue = fatigue,
                    questionType = questionType
                )
                println("   Карточка ${card.id}: интервал=${"%.1f".format(intervalDays)}д, " +
                        "тип=${questionType}, усталость=${"%.0f".format(fatigue*100)}%, " +
                        "результат=${if(wasCorrect) "✅" else "❌"}, " +
                        "вероятность=${"%.0f".format(student.memoryLog.last().probability*100)}%")

                val quality = if (wasCorrect) 1 else 0

                // ИИ рассчитывает следующий интервал
                val responseTime = (2000 + fatigue * 3000).toLong()

                // Здесь используем ваш ML калькулятор
                val updatedCard = calculateNextReview(
                    card = card,
                    context = context,
                    quality = quality,
                    responseTimeMs = responseTime,
                    mode = mode
                )

                // Обновляем карточку
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

    /**
     * Временная заглушка для расчета интервала
     * ЗАМЕНИТЕ ЭТО на вызов вашего ML калькулятора!
     */
//    private fun calculateNextReview(
//        card: Card,
//        context: AIContext,
//        quality: Int,
//        responseTimeMs: Long,
//        mode: LearningMode
//    ): Card {
//        return if (quality == 1) {
//            // Правильный ответ - интервал растет экспоненциально
//            val newInterval = when {
//                card.interval <= 1 -> 3
//                card.interval <= 3 -> 7
//                card.interval <= 7 -> 14
//                card.interval <= 14 -> 30
//                card.interval <= 30 -> 60
//                else -> (card.interval * 1.3).toInt().coerceAtMost(365)
//            }
//
//            // Учитываем тип вопроса (сложные карточки растут медленнее)
//            val questionType = QuestionType.fromString(card.questionType)
//            val typeMultiplier = when (questionType) {
//                QuestionType.FACT -> 1.2f      // Факты растут быстрее
//                QuestionType.DEFINITION -> 1.0f // Определения нормально
//                QuestionType.PROOF -> 0.8f      // Доказательства растут медленнее
//            }
//
//            val timeBonus = when (context.currentHour) {
//                in 7..10 -> 1.1f    // Утро - бонус
//                in 18..22 -> 0.9f    // Вечер - штраф
//                else -> 1.0f
//            }
//
//            val adjustedInterval = (newInterval * typeMultiplier * timeBonus).toDouble()
//
//            card.copy(
//                lastReviewed = System.currentTimeMillis(),
//                nextReview = (System.currentTimeMillis() + (adjustedInterval * 24 * 60 * 60 * 1000L)).toLong(),
//                interval = adjustedInterval,
//                consecutiveCorrect = card.consecutiveCorrect + 1,
//                totalReviews = card.totalReviews + 1,
//                successRate = (card.successRate * card.totalReviews + 1) / (card.totalReviews + 1)
//            )
//        } else {
//            // НЕПРАВИЛЬНЫЙ ОТВЕТ - интервал сбрасывается, но не до нуля
//            val penalty = when {
//                card.interval > 30 -> 7   // Если был большой интервал - сбрасываем до недели
//                card.interval > 7 -> 3     // Если был средний - до 3 дней
//                else -> 1                   // Иначе до 1 дня
//            }
//                .toDouble()
//
//            card.copy(
//                lastReviewed = System.currentTimeMillis(),
//                nextReview = (System.currentTimeMillis() + (penalty * 24 * 60 * 60 * 1000L)).toLong(),
//                interval = penalty,
//                consecutiveCorrect = 0,
//                totalReviews = card.totalReviews + 1,
//                successRate = (card.successRate * card.totalReviews) / (card.totalReviews + 1)
//            )
//        }
//    }
    // В MLSimulationEngine.kt замените calculateNextReview на:

    private fun calculateNextReview(
        card: Card,
        context: AIContext,
        quality: Int,
        responseTimeMs: Long,
        mode: LearningMode
    ): Card {
        val currentTime = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        return if (quality == 1) {
            // Правильный ответ - экспоненциальный рост с учетом истории
            val baseMultiplier = when (mode) {
                LearningMode.LONG_TERM -> 2.4f
                LearningMode.SHORT_TERM -> 1.8f
            }

            // Учитываем количество правильных ответов подряд
            val streakBonus = 1.0f + (card.consecutiveCorrect * 0.15f).coerceAtMost(0.5f)

            // Учитываем время суток
            val timeBonus = when (context.currentHour) {
                in 7..10 -> 1.3f
                in 11..14 -> 1.2f
                in 15..17 -> 0.95f
                in 18..22 -> 0.85f
                else -> 0.75f
            }

            // Учитываем усталость
            val fatiguePenalty = 1.0f - (context.userFatigueLevel * 0.2f).coerceIn(0f, 0.3f)

            // Новый интервал
            val newInterval = if (card.interval <= 1) {
                // Первые повторения - фиксированные шаги
                when (card.reviewStage) {
                    0 -> 1
                    1 -> 3
                    2 -> 7
                    3 -> 14
                    4 -> 30
                    else -> 60
                }
                    .toDouble()
            } else {
                // Дальше - умножение с факторами
                (card.interval * baseMultiplier * streakBonus * timeBonus * fatiguePenalty).toInt()
                    .coerceIn((card.interval + 1).toInt(), 365)
                    .toDouble()
            }

            println("   ✅ Правильно! Интервал: ${card.interval} -> $newInterval")

            card.copy(
                lastReviewed = System.currentTimeMillis(),
                nextReview = (System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L)).toLong(),
                interval = newInterval,
                reviewStage = card.reviewStage + 1,
                consecutiveCorrect = card.consecutiveCorrect + 1,
                totalReviews = card.totalReviews + 1
            )
        } else {
            // Неправильный ответ - сильное уменьшение
            val newInterval = when {
                card.interval <= 3 -> 1
                card.interval <= 7 -> 2
                card.interval <= 14 -> 4
                card.interval <= 30 -> 7
                card.interval <= 60 -> 14
                else -> (card.interval * 0.3).toInt().coerceAtLeast(7)
            }
                .toDouble()

            println("   ❌ Неправильно! Интервал: ${card.interval} -> $newInterval")

            card.copy(
                lastReviewed = System.currentTimeMillis(),
                nextReview = (System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L)).toLong(),
                interval = newInterval,
                reviewStage = max(0.0, card.reviewStage - 2.0).toInt(),
                consecutiveCorrect = 0,
                totalReviews = card.totalReviews + 1
            )
        }
    }

    /**
     * Генерация отчета
     */
    private fun generateReport(cards: List<Card>): SimulationReport {
        val studentStats = student.getStats()

        // Анализ карточек
        val mastered = cards.count { it.interval > 30 }
        val learning = cards.count { it.interval in 1.0..30.0 }
        val new = cards.count { it.lastReviewed == null }

        // Средние интервалы
        val avgInterval = cards.filter { it.interval > 0 }.map { it.interval }.average()
        val maxInterval = cards.maxOfOrNull { it.interval } ?: 0

        // Прогресс по дням
        val dailyReviews = simulationResults.map { it.reviewedCount }
        val dailyCorrect = simulationResults.map { it.correctCount }

        return SimulationReport(
            totalDays = simulationResults.size,
            totalCards = cards.size,
            masteredCards = mastered,
            learningCards = learning,
            newCards = new,
            avgInterval = avgInterval,
            maxInterval = maxInterval,
            studentStats = studentStats,
            dailyReviews = dailyReviews,
            dailyCorrect = dailyCorrect,
            simulationResults = simulationResults
        )
    }

    companion object {
        private val oneDayMs = 24 * 60 * 60 * 1000L
    }


}
/**
 * Движок для симуляции обучения с реальным ML калькулятором
 */
//class MLSimulationEngine {
//
//    // Реальный ML калькулятор
//    private lateinit var mlCalculator: MLSpacedRepetitionCalculator
//    fun initMocks(calculator: MLSpacedRepetitionCalculator) {
//        this.mlCalculator = calculator
//    }
//    private lateinit var tensorFlowModel: TensorFlowLiteModel
//    private lateinit var reviewLogRepository: ReviewLogRepository
//
//    // Виртуальный студент для имитации ответов
//    private val student = VirtualStudent()
//
//    // Результаты симуляции
//    private val simulationResults = mutableListOf<SimulationDay>()
//
//    // Для замера производительности ML
//    private var mlPredictions = 0
//    private var mlErrors = 0
//
//    /**
//     * Инициализация с реальными зависимостями
//     */
//    fun initializeWithRealML(
//        context: android.content.Context,
//        database: com.example.cardproject.database.AppDatabase
//    ) {
//        // Создаем реальные репозитории
//        reviewLogRepository = com.example.cardproject.database.repository.ReviewLogRepository(
//            database.reviewLogDao()
//        )
//
//        // Создаем реальную ML модель
//        tensorFlowModel = TensorFlowLiteModel(context)
//
//        // Создаем реальный ML калькулятор
//        mlCalculator = MLSpacedRepetitionCalculator(
//            tfliteModel = tensorFlowModel,
//            reviewLogRepository = reviewLogRepository
//        )
//
//        println("🤖 Реальный ML калькулятор инициализирован")
//        println("   Модель загружена: ${tensorFlowModel.isModelReady.value}")
//    }
//
//    /**
//     * Запуск симуляции с реальным ML
//     */
//    suspend fun runSimulationWithML(
//        cards: List<Card>,
//        days: Int = 60,
//        mode: LearningMode = LearningMode.LONG_TERM
//    ): SimulationReport {
//
//        println("=".repeat(60))
//        println("🚀 ЗАПУСК СИМУЛЯЦИИ С РЕАЛЬНЫМ ML НА $days ДНЕЙ")
//        println("=".repeat(60))
//        println("Карточек: ${cards.size}")
//        println("Режим: $mode")
//        println("ML модель: ${if (tensorFlowModel.isModelReady.value) "✅" else "❌"}")
//        println()
//
//        // Копируем карточки для симуляции
//        val activeCards = cards.map { it.copy() }.toMutableList()
//        var currentTime = System.currentTimeMillis()
//        val oneDayMs = 24 * 60 * 60 * 1000L
//
//        simulationResults.clear()
//        mlPredictions = 0
//        mlErrors = 0
//
//        for (day in 1..days) {
//            val dayResults = simulateDayWithML(day, activeCards, mode, currentTime)
//            simulationResults.add(dayResults)
//
//            currentTime += oneDayMs
//
//            if (day % 10 == 0 || day == 1) {
//                println(dayResults.getSummary())
//            }
//        }
//
//        val report = generateReport(activeCards)
//        println("\n" + report.getFullReport())
//        println("\n🤖 ML Статистика:")
//        println("   • Предсказаний: $mlPredictions")
//        println("   • Ошибок: $mlErrors")
//        println("   • Успешность: ${(mlPredictions - mlErrors) * 100 / mlPredictions}%")
//
//        return report
//    }
//
//    private suspend fun simulateDay(
//        day: Int,
//        cards: MutableList<Card>,
//        mode: LearningMode,
//        currentTime: Long
//    ): SimulationDay {
//
//        val reviewedCards = mutableListOf<Card>()
//        val results = mutableListOf<Pair<Card, Boolean>>()
//
//        // Определяем время дня (утро/день/вечер)
//        val hourOfDay = when (day % 3) {
//            0 -> 9   // Утро
//            1 -> 15  // День
//            else -> 20 // Вечер
//        }
//
//        // Усталость зависит от времени и дня недели
//        val fatigue = when {
//            hourOfDay > 18 -> 0.7f + (day % 5) * 0.05f  // Вечером устал
//            hourOfDay < 10 -> 0.2f                       // Утром бодр
//            else -> 0.4f                                  // Днем средне
//        }.coerceIn(0.1f, 0.9f)
//
//        // Контекст для ИИ
//        val context = AIContext(
//            sessionStartTime = currentTime,
//            cardsReviewedInSession = 0,
//            currentHour = hourOfDay,
//            dayOfWeek = (day % 7) + 1,
//            userFatigueLevel = fatigue,
//            averageResponseTimeMs = 3000 + (fatigue * 2000).toLong(),
//            consecutiveCorrectInSession = 0,
//            consecutiveIncorrectInSession = 0,
//            learningMode = mode
//        )
//
//        // Проверяем каждую карточку
//        for (card in cards) {
//            // Нужно ли повторять сегодня?
//            val shouldReview = card.nextReview == null || card.nextReview!! <= currentTime
//
//            if (shouldReview) {
//                // Интервал с прошлого раза
//                val intervalDays = if (card.lastReviewed != null) {
//                    (currentTime - card.lastReviewed!!) / oneDayMs.toFloat()
//                } else 0f
//
//                // Получаем QuestionType из строки
//                val questionType = QuestionType.fromString(card.questionType)
//
//                // Студент пытается вспомнить
//                val wasCorrect = student.tryToRecall(
//                    intervalDays = intervalDays,
//                    hourOfDay = hourOfDay,
//                    fatigue = fatigue,
//                    questionType = questionType
//                )
//                println("   Карточка ${card.id}: интервал=${"%.1f".format(intervalDays)}д, " +
//                        "тип=${questionType}, усталость=${"%.0f".format(fatigue*100)}%, " +
//                        "результат=${if(wasCorrect) "✅" else "❌"}, " +
//                        "вероятность=${"%.0f".format(student.memoryLog.last().probability*100)}%")
//
//                val quality = if (wasCorrect) 1 else 0
//
//                // ИИ рассчитывает следующий интервал
//                val responseTime = (2000 + fatigue * 3000).toLong()
//
//                // Здесь используем ваш ML калькулятор
//                val updatedCard = calculateNextReview(
//                    card = card,
//                    context = context,
//                    quality = quality,
//                    responseTimeMs = responseTime,
//                    mode = mode
//                )
//
//                // Обновляем карточку
//                val index = cards.indexOfFirst { it.id == card.id }
//                if (index >= 0) {
//                    cards[index] = updatedCard
//                    reviewedCards.add(updatedCard)
//                    results.add(updatedCard to wasCorrect)
//                }
//            }
//        }
//
//        return SimulationDay(
//            day = day,
//            hourOfDay = hourOfDay,
//            fatigue = fatigue,
//            reviewedCount = reviewedCards.size,
//            totalCards = cards.size,
//            correctCount = results.count { it.second },
//            results = results
//        )
//    }
//    /**
//     * Симуляция одного дня с использованием ML калькулятора
//     */
//    private suspend fun simulateDayWithML(
//        day: Int,
//        cards: MutableList<Card>,
//        mode: LearningMode,
//        currentTime: Long
//    ): SimulationDay {
//
//        val reviewedCards = mutableListOf<Card>()
//        val results = mutableListOf<Pair<Card, Boolean>>()
//
//        // Определяем время дня
//        val hourOfDay = when (day % 3) {
//            0 -> 9   // Утро
//            1 -> 15  // День
//            else -> 20 // Вечер
//        }
//
//        // Усталость зависит от времени
//        val fatigue = when {
//            hourOfDay > 18 -> 0.7f + (day % 5) * 0.05f
//            hourOfDay < 10 -> 0.2f
//            else -> 0.4f
//        }.coerceIn(0.1f, 0.9f)
//
//        // Контекст для ML
//        val context = AIContext(
//            sessionStartTime = currentTime,
//            cardsReviewedInSession = 0,
//            currentHour = hourOfDay,
//            dayOfWeek = (day % 7) + 1,
//            userFatigueLevel = fatigue,
//            averageResponseTimeMs = 3000 + (fatigue * 2000).toLong(),
//            consecutiveCorrectInSession = 0,
//            consecutiveIncorrectInSession = 0,
//            learningMode = mode
//        )
//
//        // Проверяем каждую карточку
//        for (card in cards) {
//            val shouldReview = card.nextReview == null || card.nextReview!! <= currentTime
//
//            if (shouldReview) {
//                val intervalDays = if (card.lastReviewed != null) {
//                    (currentTime - card.lastReviewed!!) / oneDayMs.toFloat()
//                } else 0f
//
//                // Получаем тип вопроса
//                val questionType = QuestionType.fromString(card.questionType)
//
//                // Виртуальный студент пытается вспомнить
//                val wasCorrect = student.tryToRecall(
//                    intervalDays = intervalDays,
//                    hourOfDay = hourOfDay,
//                    fatigue = fatigue,
//                    questionType = questionType
//                )
//
//                val quality = if (wasCorrect) 1 else 0
//                val responseTime = (2000 + fatigue * 3000).toLong()
//
//                try {
//                    // ИСПОЛЬЗУЕМ РЕАЛЬНЫЙ ML КАЛЬКУЛЯТОР!
//                    mlPredictions++
//
//                    val updatedCard = mlCalculator.calculateNextReview(
//                        card = card,
//                        context = context,
//                        learningMode = mode,
//                        quality = quality,
//                        responseTimeMs = responseTime
//                    )
//
//                    // Обновляем карточку в списке
//                    val index = cards.indexOfFirst { it.id == card.id }
//                    if (index >= 0) {
//                        cards[index] = updatedCard
//                        reviewedCards.add(updatedCard)
//                        results.add(updatedCard to wasCorrect)
//                    }
//
//                    // Логируем результат (каждые 10 раз для отладки)
//                    if (mlPredictions % 10 == 0) {
//                        println("   🤖 ML предсказание #$mlPredictions: интервал ${updatedCard.interval} дней")
//                    }
//
//                } catch (e: Exception) {
//                    mlErrors++
//                    println("   ❌ Ошибка ML: ${e.message}")
//                    e.printStackTrace()
//
//                    // Fallback на SM-2 в случае ошибки
//                    val fallbackCard = fallbackSM2(card, quality, mode)
//                    val index = cards.indexOfFirst { it.id == card.id }
//                    if (index >= 0) {
//                        cards[index] = fallbackCard
//                        reviewedCards.add(fallbackCard)
//                        results.add(fallbackCard to wasCorrect)
//                    }
//                }
//            }
//        }
//
//        return SimulationDay(
//            day = day,
//            hourOfDay = hourOfDay,
//            fatigue = fatigue,
//            reviewedCount = reviewedCards.size,
//            totalCards = cards.size,
//            correctCount = results.count { it.second },
//            results = results
//        )
//    }
//
//    /**
//     * Запасной вариант (SM-2) на случай ошибок ML
//     */
//    private fun fallbackSM2(card: Card, quality: Int, mode: LearningMode): Card {
//        return if (quality == 1) {
//            val newInterval = when (card.consecutiveCorrect) {
//                0 -> 1
//                1 -> 3
//                2 -> 7
//                3 -> 14
//                else -> (card.interval * 2).coerceAtMost(365.0)
//            }
//                .toDouble()
//
//            card.copy(
//                lastReviewed = System.currentTimeMillis(),
//                nextReview = (System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L)).toLong(),
//                interval = newInterval,
//                consecutiveCorrect = card.consecutiveCorrect + 1,
//                totalReviews = card.totalReviews + 1
//            )
//        } else {
//            card.copy(
//                lastReviewed = System.currentTimeMillis(),
//                nextReview = System.currentTimeMillis() + (24 * 60 * 60 * 1000L),
//                interval = 1.0,
//                consecutiveCorrect = 0,
//                totalReviews = card.totalReviews + 1
//            )
//        }
//    }
//    private fun calculateNextReview(
//        card: Card,
//        context: AIContext,
//        quality: Int,
//        responseTimeMs: Long,
//        mode: LearningMode
//    ): Card {
//        val currentTime = System.currentTimeMillis()
//        val oneDayMs = 24 * 60 * 60 * 1000L
//        return if (quality == 1) {
//            // Правильный ответ - экспоненциальный рост с учетом истории
//            val baseMultiplier = when (mode) {
//                LearningMode.LONG_TERM -> 2.4f
//                LearningMode.SHORT_TERM -> 1.8f
//            }
//
//            // Учитываем количество правильных ответов подряд
//            val streakBonus = 1.0f + (card.consecutiveCorrect * 0.15f).coerceAtMost(0.5f)
//
//            // Учитываем время суток
//            val timeBonus = when (context.currentHour) {
//                in 7..10 -> 1.3f
//                in 11..14 -> 1.2f
//                in 15..17 -> 0.95f
//                in 18..22 -> 0.85f
//                else -> 0.75f
//            }
//
//            // Учитываем усталость
//            val fatiguePenalty = 1.0f - (context.userFatigueLevel * 0.2f).coerceIn(0f, 0.3f)
//
//            // Новый интервал
//            val newInterval = if (card.interval <= 1) {
//                // Первые повторения - фиксированные шаги
//                when (card.reviewStage) {
//                    0 -> 1
//                    1 -> 3
//                    2 -> 7
//                    3 -> 14
//                    4 -> 30
//                    else -> 60
//                }
//                    .toDouble()
//            } else {
//                // Дальше - умножение с факторами
//                (card.interval * baseMultiplier * streakBonus * timeBonus * fatiguePenalty).toInt()
//                    .coerceIn((card.interval + 1).toInt(), 365)
//                    .toDouble()
//            }
//
//            println("   ✅ Правильно! Интервал: ${card.interval} -> $newInterval")
//
//            card.copy(
//                lastReviewed = System.currentTimeMillis(),
//                nextReview = (System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L)).toLong(),
//                interval = newInterval,
//                reviewStage = card.reviewStage + 1,
//                consecutiveCorrect = card.consecutiveCorrect + 1,
//                totalReviews = card.totalReviews + 1
//            )
//        } else {
//            // Неправильный ответ - сильное уменьшение
//            val newInterval = when {
//                card.interval <= 3 -> 1
//                card.interval <= 7 -> 2
//                card.interval <= 14 -> 4
//                card.interval <= 30 -> 7
//                card.interval <= 60 -> 14
//                else -> (card.interval * 0.3).toInt().coerceAtLeast(7)
//            }
//                .toDouble()
//
//            println("   ❌ Неправильно! Интервал: ${card.interval} -> $newInterval")
//
//            card.copy(
//                lastReviewed = System.currentTimeMillis(),
//                nextReview = (System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L)).toLong(),
//                interval = newInterval,
//                reviewStage = max(0.0, card.reviewStage - 2.0).toInt(),
//                consecutiveCorrect = 0,
//                totalReviews = card.totalReviews + 1
//            )
//        }
//    }
//
//    /**
//     * Генерация отчета
//     */
//    private fun generateReport(cards: List<Card>): SimulationReport {
//        val studentStats = student.getStats()
//
//        val mastered = cards.count { it.interval > 30 }
//        val learning = cards.count { it.interval in 1.0..30.0 }
//        val new = cards.count { it.lastReviewed == null }
//
//        val avgInterval = cards.filter { it.interval > 0 }.map { it.interval }.average()
//        val maxInterval = cards.maxOfOrNull { it.interval } ?: 0
//            .toDouble()
//
//        return SimulationReport(
//            totalDays = simulationResults.size,
//            totalCards = cards.size,
//            masteredCards = mastered,
//            learningCards = learning,
//            newCards = new,
//            avgInterval = avgInterval,
//            maxInterval = maxInterval,
//            studentStats = studentStats,
//            simulationResults = simulationResults,
//            mlPredictions = mlPredictions,
//            mlErrors = mlErrors
//        )
//    }
//
//    suspend fun runSimulation(
//        cards: List<Card>,
//        days: Int = 60,
//        mode: LearningMode = LearningMode.LONG_TERM
//    ): SimulationReport {
//
//        println("=".repeat(60))
//        println("🚀 ЗАПУСК СИМУЛЯЦИИ (FALLBACK) НА $days ДНЕЙ")
//        println("=".repeat(60))
//        println("Карточек: ${cards.size}")
//        println("Режим: $mode")
//        println()
//
//        val activeCards = cards.map { it.copy() }.toMutableList()
//        var currentTime = System.currentTimeMillis()
//        val oneDayMs = 24 * 60 * 60 * 1000L
//
//        simulationResults.clear()
//
//        for (day in 1..days) {
//            val dayResults = simulateDay(day, activeCards, mode, currentTime)
//            simulationResults.add(dayResults)
//
//            currentTime += oneDayMs
//
//            if (day % 10 == 0 || day == 1) {
//                println(dayResults.getSummary())
//            }
//        }
//
//        val report = generateReport(activeCards)
//        println("\n" + report.getFullReport())
//
//        return report
//    }
//
//    companion object {
//        private val oneDayMs = 24 * 60 * 60 * 1000L
//    }
//}
///**
// * Результат одного дня симуляции
// */
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

///**
// * Итоговый отчет симуляции
// */
data class SimulationReport(
    val totalDays: Int,
    val totalCards: Int,
    val masteredCards: Int,
    val learningCards: Int,
    val newCards: Int,
    val avgInterval: Double,
    val maxInterval: Any,
    val studentStats: VirtualStudent.StudentStats,
    val dailyReviews: List<Int>,
    val dailyCorrect: List<Int>,
    val simulationResults: List<SimulationDay>
) {
    fun getFullReport(): String {
        val totalReviews = dailyReviews.sum()
        val avgDaily = dailyReviews.average()
        val totalCorrect = dailyCorrect.sum()
        val overallAccuracy = if (totalReviews > 0) totalCorrect * 100 / totalReviews else 0

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
            • Всего: $totalReviews
            • В среднем в день: ${"%.1f".format(avgDaily)}
            • Правильных: $totalCorrect
            • Точность: $overallAccuracy%

            ${studentStats}
        """.trimIndent()
    }

    /**
     * Экспорт в CSV для анализа
     */
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
}
///**
// * Обновленный класс отчета с ML статистикой
// */
//data class SimulationReport(
//    val totalDays: Int,
//    val totalCards: Int,
//    val masteredCards: Int,
//    val learningCards: Int,
//    val newCards: Int,
//    val avgInterval: Double,
//    val maxInterval: Double,
//    val studentStats: VirtualStudent.StudentStats,
//    val simulationResults: List<SimulationDay>,
//    val mlPredictions: Int = 0,
//    val mlErrors: Int = 0
//) {
//    fun getFullReport(): String {
//        val totalReviews = simulationResults.sumOf { it.reviewedCount }
//        val avgDaily = totalReviews.toDouble() / totalDays
//        val totalCorrect = simulationResults.sumOf { it.correctCount }
//        val overallAccuracy = if (totalReviews > 0) totalCorrect * 100 / totalReviews else 0
//
//        return """
//
//            📊 ИТОГОВЫЙ ОТЧЕТ СИМУЛЯЦИИ
//            ==============================
//
//            🗓 Период: $totalDays дней
//            🃏 Карточки: $totalCards
//
//            📈 ПРОГРЕСС:
//            • Выучено (интервал > 30): $masteredCards (${masteredCards * 100 / totalCards}%)
//            • В процессе: $learningCards
//            • Новых: $newCards
//
//            ⏱ ИНТЕРВАЛЫ:
//            • Средний: ${"%.1f".format(avgInterval)} дней
//            • Максимальный: $maxInterval дней
//
//            📊 ПОВТОРЕНИЯ:
//            • Всего: $totalReviews
//            • В среднем в день: ${"%.1f".format(avgDaily)}
//            • Правильных: $totalCorrect
//            • Точность: $overallAccuracy%
//
//            ${studentStats}
//
//            🤖 ML СТАТИСТИКА:
//            • Предсказаний: $mlPredictions
//            • Ошибок: $mlErrors
//            • Успешность: ${if (mlPredictions > 0) (mlPredictions - mlErrors) * 100 / mlPredictions else 0}%
//        """.trimIndent()
//    }
//
//    fun exportToCSV(): String {
//        val sb = StringBuilder()
//        sb.appendln("Day,Hour,Fatigue,Reviewed,Correct,Accuracy")
//
//        simulationResults.forEach { day ->
//            val accuracy = if (day.reviewedCount > 0)
//                day.correctCount * 100 / day.reviewedCount else 0
//            sb.appendln("${day.day},${day.hourOfDay},${"%.2f".format(day.fatigue)},${day.reviewedCount},${day.correctCount},$accuracy")
//        }
//
//        return sb.toString()
//    }
//}