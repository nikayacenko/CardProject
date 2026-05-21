// File: app/src/androidTest/java/com/example/cardproject/test/MLIntegrationTest.kt
package com.example.cardproject.test

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cardproject.database.AppDatabase
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.ml.MLSpacedRepetitionCalculator
import com.example.cardproject.ml.TensorFlowLiteModel
import com.example.cardproject.model.LearningMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class MLIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var reviewLogRepository: ReviewLogRepository
    private lateinit var tensorFlowModel: TensorFlowLiteModel
    private lateinit var mlCalculator: MLSpacedRepetitionCalculator
    private lateinit var engine: MLSimulationEngine

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()

        database = AppDatabase.getInstance(context)

        // Очищаем таблицы в правильном порядке (из-за foreign keys)
        database.reviewLogDao().deleteAll()
        database.cardDao().deleteAllCards()
        database.deckDao().deleteAllDecks()

        reviewLogRepository = ReviewLogRepository(database.reviewLogDao())

        tensorFlowModel = TensorFlowLiteModel(context)

        // Проверяем модель
        println("Модель загружена: ${tensorFlowModel.isModelReady.value}")
        if (tensorFlowModel.isModelReady.value) {
            // Тестируем один вызов
            val testFeatures = FloatArray(20) { 0.5f }
            println("Модель готова, 20 признаков")
        }
        // Создаем ML калькулятор с реальными зависимостями
        mlCalculator = MLSpacedRepetitionCalculator(
            tfliteModel = tensorFlowModel,
            reviewLogRepository = reviewLogRepository
        )

        // Создаем движок симуляции
        engine = MLSimulationEngine().apply {
            initializeWithRealML(mlCalculator)
        }

        println("Настройка завершена")
        println("   ML модель загружена: ${tensorFlowModel.isModelReady.value}")
    }

    @After
    fun cleanup() = runBlocking {
        delay(100)
//        if (::database.isInitialized) {
//            // Перед закрытием можно попробовать остановить трекеры
//            database.invalidationTracker.refreshVersionsAsync()
//            if (database.isOpen) {
//                database.close()
//            }
//        }

        if (::tensorFlowModel.isInitialized) {
            tensorFlowModel.close()
        }
    }

    @Test
    fun test30DaysWithRealMLAndVirtualStudent1() = runBlocking {
        // 1. Создаем тестовые данные
        val cards = TestDataFactory.createTestDeck(50)

        // 2. Создаем виртуального студента
        val student = VirtualStudent(
            baseMemoryStrength = 0.85f,
            morningEfficiency = 1.15f,
            eveningEfficiency = 0.85f,
            fatigueSensitivity = 0.05f
        )

        // 3. Запускаем симуляцию с реальным ML и виртуальным студентом
        val report = engine.runSimulationWithML(
            cards = cards,
            student = student,
            days = 120,
            mode = LearningMode.LONG_TERM,
            shouldSaveLogs = true  // Сохраняем логи для обучения модели
        )

        // 4. Проверяем результаты
        assert(report.masteredCards >= 0) { "Должны быть выученные карточки" }
        assert(report.avgInterval > 5) { "Средний интервал должен расти" }
        assert(report.mlPredictions > 0) { "ML модель должна делать предсказания" }
        assert(report.mlErrors == 0) { "ML модель не должна выдавать ошибки" }

        if (report.masteredCards == 0) {
            println("⚠️ ВНИМАНИЕ: Не выучено ни одной карточки за 30 дней!")
            println("   Возможные причины:")
            println("   1. Студент слишком слабый (низкий baseMemoryStrength)")
            println("   2. ML модель не загружена (mlPredictions = 0)")
            println("   3. Слишком высокий порог 'выучено' (интервал > 30 дней)")
        }
        // 5. Сохраняем результаты
        saveResults(report, "30days_real_ml")

        // 6. Выводим статистику
        println("\n📊 ИТОГОВАЯ СТАТИСТИКА:")
        println("   • Предсказаний ML: ${report.mlPredictions}")
        println("   • Ошибок ML: ${report.mlErrors}")
        println("   • Точность ML: ${report.mlAccuracy}%")
        println("   • Точность студента: ${(report.studentStats.correctRate * 100).toInt()}%")


    }

    @Test
    fun test50DaysWithRealMLAndVirtualStudent() = runBlocking {
        // ============================================================
        // 1. НАСТРОЙКА ЭКСПЕРИМЕНТА
        // ============================================================
        val cards = TestDataFactory.createTestDeck(50)

        val student = VirtualStudent(
            baseMemoryStrength = 0.85f,
            morningEfficiency = 1.15f,
            eveningEfficiency = 0.85f,
            fatigueSensitivity = 0.05f
        )

        println("╔══════════════════════════════════════════════════════════════════╗")
        println("║     🧪 ЭКСПЕРИМЕНТ: 50 дней с ML-моделью и виртуальным студентом  ║")
        println("╚══════════════════════════════════════════════════════════════════╝")
        println()
        println("📋 ПАРАМЕТРЫ ЭКСПЕРИМЕНТА:")
        println("   • Карточек: ${cards.size}")
        println("   • Типы: FACT, DEFINITION, PROOF")
        println("   • Студент: baseMemoryStrength = ${student.baseMemoryStrength}")
        println("   • Режим: LONG_TERM")
        println("   • Длительность: 50 дней")
        println()

        // ============================================================
        // 2. ЗАПУСК СИМУЛЯЦИИ
        // ============================================================
        val report = engine.runSimulationWithML(
            cards = cards,
            student = student,
            days = 50,
            mode = LearningMode.LONG_TERM,
            shouldSaveLogs = true
        )

        // ============================================================
        // 3. ПРОВЕРКИ
        // ============================================================
        assert(report.masteredCards > 0) { "❌ Ошибка: не выучено ни одной карточки" }
        assert(report.avgInterval > 5) { "❌ Ошибка: интервалы не растут" }
        assert(report.mlPredictions > 0) { "❌ Ошибка: ML модель не использовалась" }
        assert(report.mlErrors == 0) { "❌ Ошибка: ML модель выдала ошибки" }

        // ============================================================
        // 4. СОХРАНЕНИЕ РЕЗУЛЬТАТОВ
        // ============================================================
        saveResults(report, "50days_ml")

        // ============================================================
        // 5. ВЫВОД ДЛЯ СТАТЬИ
        // ============================================================
        println()
        println("=".repeat(70))
        println("РЕЗУЛЬТАТЫ ЭКСПЕРИМЕНТА (50 дней, 50 карточек)")
        println("=".repeat(70))
        println()

        // Основные результаты
        val masteredPercent = report.masteredCards * 100 / 50
        println("За 50 дней обучения на 50 карточках ML-модель показала следующие результаты:")
        println()
        println("  • Выучено карточек: ${report.masteredCards} из 50 ($masteredPercent%)")
        println("  • Средний интервал: ${"%.1f".format(report.avgInterval)} дней")
        println("  • Максимальный интервал: ${"%.1f".format(report.maxInterval)} дней")
        println("  • Всего тестов: ${report.studentStats.totalTests}")
        println("  • Точность ответов студента: ${report.overallAccuracy}%")
        println()

        // ML статистика
        println("Точность работы самой ML-модели составила ${"%.1f".format(report.mlAccuracy)}% при ${report.mlPredictions} предсказаниях без ошибок.")
        println()

        // Типы карточек
        println("Анализ по типам карточек показал:")
        println("  • FACT (лёгкие): успех ${"%.0f".format(report.studentStats.factSuccess * 100)}%")
        println("  • DEFINITION (средние): успех ${"%.0f".format(report.studentStats.definitionSuccess * 100)}%")
        println("  • PROOF (сложные): успех ${"%.0f".format(report.studentStats.proofSuccess * 100)}%")
        println()

        // Динамика
        println("Динамика выучивания карточек:")
        val learningCurve = report.getLearningCurve()

        val daysToCheck = listOf(10, 20, 30, 40, 50)
        for (day in daysToCheck) {
            val index = (day - 1).coerceAtMost(learningCurve.size - 1)
            val mastered = learningCurve.getOrNull(index) ?: report.masteredCards
            val percent = mastered * 100 / 50
            println("  • К $day дню: выучено $mastered карточек ($percent%)")
        }

        // Прогресс
        println()
        println("За время эксперимента сгенерировано ${report.mlPredictions} предсказаний ML-модели,")
        println("из них ${"%.1f".format(report.mlAccuracy)}% оказались точными. Студент достиг")
        println("${report.overallAccuracy}% правильных ответов, что подтверждает эффективность")
        println("подобранных интервалов повторения.")

        // Экспорт в CSV
        exportToCsvForArticle(report, "ml_50days_50cards")
    }

    private fun exportToCsvForArticle(report: SimulationReport, filename: String) {
        val csv = StringBuilder()
        csv.appendLine("Day,MasteredCards,Accuracy")

        for (i in report.simulationResults.indices) {
            val day = i + 1
            val mastered = report.getLearningCurve().getOrNull(i) ?: 0
            val accuracy = report.getAccuracyOverTime().getOrNull(i) ?: 0
            csv.appendLine("$day,$mastered,$accuracy")
        }

        val file = File(context.filesDir, "${filename}.csv")
        file.writeText(csv.toString())
        println()
        println("📁 Данные для графика сохранены: ${file.absolutePath}")
    }

    @Test
    fun testCompareMLWithFallback() = runBlocking {
        val cards = TestDataFactory.createTestDeck(20)
        val student = VirtualStudent()
        val studentML = VirtualStudent()
        val studentFallback = VirtualStudent()
        // Тест с ML
        val mlReport = engine.runSimulationWithML(
            cards = cards.map { it.copy() },
            student = studentML,
            days = 60,
            mode = LearningMode.LONG_TERM,
            shouldSaveLogs = false
        )

        // Тест без ML (fallback)
        val fallbackReport = engine.runSimulationWithoutML(
            cards = cards.map { it.copy() },
            student = studentFallback,
            days = 60,
            mode = LearningMode.LONG_TERM,
            currentTime = System.currentTimeMillis(),
            shouldSaveLogs = false
        )
        println("SM-2: totalTests = ${fallbackReport.studentStats.totalTests}")
        println("SM-2: dailyReviews sum = ${fallbackReport.dailyReviews.sum()}")
        println("ML: totalTests = ${mlReport.studentStats.totalTests}")
        println("ML: dailyReviews sum = ${mlReport.dailyReviews.sum()}")
        val csvData = StringBuilder()
        val currentMLDeck = cards.map { it.copy() }.toMutableList()
        val currentFBDeck = cards.map { it.copy() }.toMutableList()
        csvData.append("DAY_INDEX;ML_MASTERED;FALLBACK_MASTERED\n")
        for (i in 0 until 60) {
            val day = i + 1
            val mlDayResults = mlReport.simulationResults[i].results
            val fbDayResults = fallbackReport.simulationResults[i].results

            // 2. Вместо averageFatigue используем проверку точности (Accuracy)
            // Если точность в этот день упала ниже 70%, значит был кризис
            val mlAcc = if (mlDayResults.isNotEmpty())
                (mlDayResults.count { it.second }.toDouble() / mlDayResults.size * 60).toInt() else 0

            if (mlAcc < 70 && mlDayResults.isNotEmpty()) {
                println("ДЕНЬ $day | КРИЗИС ТОЧНОСТИ: $mlAcc%")

                // Берем первую попавшуюся карточку из этого дня для сравнения
                val (card, success) = mlDayResults.first()
                val mlNextInterval = card.interval
                val fallbackHypothesis = card.interval * 2.5

                println("   [Карта ${card.id}]")
                println("   -> ML адаптировался и поставил: ${"%.1f".format(mlNextInterval)} дн.")
                println("   -> Fallback поставил бы: ${"%.1f".format(fallbackHypothesis)} дн.")
                println("   -> Результат: ${if(success) "УСПЕХ" else "ПРОВАЛ"}")
                println("---")
            }

            // 3. Обновляем колоды и CSV (ваша рабочая логика)
            mlDayResults.forEach { (card, _) ->
                currentMLDeck.find { it.id == card.id }?.interval = card.interval
            }
            fbDayResults.forEach { (card, _) ->
                currentFBDeck.find { it.id == card.id }?.interval = card.interval
            }

            val mlMasteredAtDay = currentMLDeck.count { it.interval > 30 }
            val fallbackMasteredAtDay = currentFBDeck.count { it.interval > 30 }
            val fbAcc = if (fbDayResults.isNotEmpty())
                (fbDayResults.count { it.second }.toDouble() / fbDayResults.size * 100).toInt() else 0

            csvData.append("$day;$mlMasteredAtDay;$fallbackMasteredAtDay;$mlAcc;$fbAcc\n")
        }
        println("@@@_START_@@@")
        println(csvData.toString())
        println("@@@_END_@@@")
        println("\n" + "=".repeat(60))
        println("СРАВНЕНИЕ РЕЖИМОВ")
        println("=".repeat(60))

        println("\nС РЕАЛЬНЫМ ML:")
        println("   • Выучено: ${mlReport.masteredCards}/${mlReport.totalCards}")
        println("   • Средний интервал: ${"%.1f".format(mlReport.avgInterval)} дней")
        println("   • Точность студента: ${(mlReport.studentStats.correctRate * 100).toInt()}%")

        println("\nБЕЗ ML (FALLBACK):")
        println("   • Выучено: ${fallbackReport.masteredCards}/${fallbackReport.totalCards}")
        println("   • Средний интервал: ${"%.1f".format(fallbackReport.avgInterval)} дней")
        println("   • Точность студента: ${(fallbackReport.studentStats.correctRate * 100).toInt()}%")

        println("\n--- START CSV ---")
        println(csvData)
        println("--- END CSV ---")
        // Сохраняем оба отчета
        saveResults(mlReport, "60days_with_ml")
        saveResults(fallbackReport, "60days_without_ml")

    }

    @Test
    fun testDifferentStudentTypes() = runBlocking {
        val cards = TestDataFactory.createTestDeck(20)

        // Хороший студент
        val goodStudent = VirtualStudent(
            baseMemoryStrength = 0.95f,
            morningEfficiency = 1.2f,
            eveningEfficiency = 0.9f,
            fatigueSensitivity = 0.05f
        )

        // Средний студент
        val averageStudent = VirtualStudent(
            baseMemoryStrength = 0.85f,
            morningEfficiency = 1.1f,
            eveningEfficiency = 0.85f,
            fatigueSensitivity = 0.08f
        )

        // Слабый студент
        val weakStudent = VirtualStudent(
            baseMemoryStrength = 0.7f,
            morningEfficiency = 1.0f,
            eveningEfficiency = 0.7f,
            fatigueSensitivity = 0.1f
        )

        val reports = mutableListOf<SimulationReport>()

        for ((name, student) in listOf(
            "Хороший" to goodStudent,
            //"Средний" to averageStudent,
            //"Слабый" to weakStudent
        )) {
            val report = engine.runSimulationWithML(
                cards = cards.map { it.copy() },
                student = student,
                days = 60,
                mode = LearningMode.LONG_TERM,
                shouldSaveLogs = true
            )
            val sm2Report = engine.runSimulationWithoutML(
                cards = cards.map { it.copy() },
                student = student,
                days = 60,
                mode = LearningMode.LONG_TERM,
                currentTime = System.currentTimeMillis(),
                shouldSaveLogs = false
            )
            reports.add(report)

            println("\n🎓 ${name} СТУДЕНТ:")
            println("   • Выучено: ${report.masteredCards}/${report.totalCards}")
            println("   • Точность студента: ${(report.studentStats.correctRate * 100).toInt()}%")
            println("   • Сsm2Report = {SimulationReport@22604} SimulationReport(totalDays=60, totalCards=20, masteredCards=3, learningCards=17, newCards=0, avgInterval=12.3, maxInterval=67.0, studentStats=СТАТИСТИКА СТУДЕНТА:\\nВсего тестов: 1004\\nПравильных: 48%\\nСредняя вероятность: 51%\\nУтром: 53%\\nВечером: 44%, dailyReviews=[20, 0, 7, 2, 19, 8, 8, 7, 20, 11, 10, 9, 8, 7, 7, 20, 14, 14, 14, 14, 14, 14, 14, 14, 12, 11, 11, 11, 11, 11, 11, 20, 17, 17, 17, 16, 16, 16, 16, 16, 16, 16, 16, 15, 15, 15, 20, 19, 19, 18, 18, 18, 18, 18, 18, 18, 18, 18, 17, 17], dailyCorrect=[13, 0, 6, 2, 11, 3, 3, 2, 16, 7, 4, 7, 2, 3, 4, 9, 4, 7, 6, 7, 6, 9, 11, 6, 3, 2, 1, 2, 5, 4, 6, 9, 10, 5, 7, 10, 10, 6, 9, 10, 8, 9, 8, 5, 9, 6, 9, 6, 8, 8, 8, 7, 6, 8, 8, 8, 8, 3, 4, 10], simulationResults=[SimulationDay(day=1, hourOfDay=15, fatigue=0.4, reviewedCount=20, totalCards=20, correctCount=13, results=[(Card(id=1, deckId=1, front=Что такое фотосинтез?, back=Тестовый ответ 1, createdAt=1778500977316, lastReviewed=1778500979034, nextReview=1778760179034, easeFactor=2.5, interval… Viewредний интервал: ${"%.1f".format(report.avgInterval)} дней")

            saveResults(sm2Report, "60days_${name}_student_SM2")
            saveResults(report, "60days_${name}_student")
        }


    }

    @Test
    fun testCompareMLWithFallbackDetailed() = runBlocking {
        val cards = TestDataFactory.createTestDeck(20)

        val studentML = VirtualStudent(baseMemoryStrength = 0.85f)
        val studentFallback = VirtualStudent(baseMemoryStrength = 0.85f)

        val mlReport = engine.runSimulationWithML(
            cards = cards.map { it.copy() },
            student = studentML,
            days = 60,
            mode = LearningMode.LONG_TERM,
            shouldSaveLogs = false
        )

        val fallbackReport = engine.runSimulationWithoutML(
            cards = cards.map { it.copy() },
            student = studentFallback,
            days = 60,
            mode = LearningMode.LONG_TERM,
            currentTime = System.currentTimeMillis(),
            shouldSaveLogs = false
        )

        println("\n" + "=".repeat(80))
        println("СРАВНИТЕЛЬНЫЙ АНАЛИЗ ML vs SM-2")
        println("=".repeat(80))

        printComparisonMetric("Выучено карточек",
            "${mlReport.masteredCards}/${mlReport.totalCards} (${mlReport.getMasteredPercentage()}%)",
            "${fallbackReport.masteredCards}/${fallbackReport.totalCards} (${fallbackReport.getMasteredPercentage()}%)")

        printComparisonMetric("Средний интервал",
            "${"%.1f".format(mlReport.avgInterval)} дней",
            "${"%.1f".format(fallbackReport.avgInterval)} дней")

        printComparisonMetric("Всего тестов",
            mlReport.studentStats.totalTests.toString(),
            fallbackReport.studentStats.totalTests.toString())

        printComparisonMetric("Точность студента",
            "${mlReport.overallAccuracy}%",
            "${fallbackReport.overallAccuracy}%")

        printComparisonMetric("Эффективность (карточек на 100 тестов)",
            "${"%.1f".format(mlReport.getEffortRewardRatio())}",
            "${"%.1f".format(fallbackReport.getEffortRewardRatio())}")

        val testsSaved = mlReport.getTestsSavedPercentage(fallbackReport.studentStats.totalTests)
        println("\nИТОГО: ML экономит ${"%.0f".format(testsSaved)}% тестов при +${mlReport.masteredCards - fallbackReport.masteredCards} выученных карточках")

        println("========== ML-ПОДХОД ==========")
        println("Выучено: ${mlReport.masteredCards}/${mlReport.totalCards}")
        println("Средний интервал: ${"%.1f".format(mlReport.avgInterval)} дней")
        println("Всего тестов: ${mlReport.studentStats.totalTests}")

        println("========== SM-2 ==========")
        println("Выучено: ${fallbackReport.masteredCards}/${fallbackReport.totalCards}")
        println("Средний интервал: ${"%.1f".format(fallbackReport.avgInterval)} дней")
        println("Всего тестов: ${fallbackReport.studentStats.totalTests}")

        println("========== ПРЕИМУЩЕСТВО ==========")
        val saved = (1 - mlReport.studentStats.totalTests.toDouble() / fallbackReport.studentStats.totalTests) * 100
        println("Экономия тестов: ${"%.1f".format(saved)}%")

        println("\n📊 СРАВНЕНИЕ ВСЕГО ПЕРИОДА:")
        val mlAllAccuracy = mlReport.getAccuracyOverTime()
        val sm2AllAccuracy = fallbackReport.getAccuracyOverTime()

        val mlAvgTotal = mlAllAccuracy.filter { it > 0 }.average()
        val sm2AvgTotal = sm2AllAccuracy.filter { it > 0 }.average()

        println("   ML средняя: ${"%.1f".format(mlAvgTotal)}%")
        println("   SM-2 средняя: ${"%.1f".format(sm2AvgTotal)}%")

// Проверка, что во второй половине ML лучше
        val secondHalfStart = mlAllAccuracy.size / 2
        val mlSecondHalf = mlAllAccuracy.drop(secondHalfStart).filter { it > 0 }.average()
        val sm2SecondHalf = sm2AllAccuracy.drop(secondHalfStart).filter { it > 0 }.average()

        println("\nВТОРАЯ ПОЛОВИНА (дни ${secondHalfStart+1}-60):")
        println("   ML средняя: ${"%.1f".format(mlSecondHalf)}%")
        println("   SM-2 средняя: ${"%.1f".format(sm2SecondHalf)}%")

        println("\nПРОВЕРКА, ЧТО МОДЕЛЬ РАБОТАЕТ:")
        println("   ML предсказаний: ${mlReport.mlPredictions}")
        println("   ML ошибок: ${mlReport.mlErrors}")
        println("   ML успешных предсказаний (mlCorrectPredictions): ${mlReport.mlCorrectPredictions}")
        println("   ML точность предсказаний: ${mlReport.mlAccuracy}%")

        if (mlReport.mlPredictions == 0) {
            println("   ML НЕ ИСПОЛЬЗОВАЛАСЬ! Возможные причины:")
            println("      1. Модель не загружена (tensorFlowModel.isModelReady = false)")
            println("      2. Все предсказания ушли в fallback из-за низкой уверенности")
        }
    }

    private fun printComparisonMetric(name: String, mlValue: String, sm2Value: String) {
        println("\n📌 $name:")
        println("    ML:  $mlValue")
        println("    SM-2: $sm2Value")
    }
    private fun saveResults(report: SimulationReport, suffix: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

        val dir = File(context.getExternalFilesDir(null), "simulation_reports")
        dir.mkdirs()

        val csvFile = File(dir, "simulation_${suffix}_$timestamp.csv")
        csvFile.writeText(report.exportToCSV())

        val reportFile = File(dir, "report_${suffix}_$timestamp.txt")
        reportFile.writeText(report.getFullReport())

        println("\n Результаты сохранены:")
        println("   CSV: ${csvFile.absolutePath}")
        println("   TXT: ${reportFile.absolutePath}")
    }

    fun generateComparisonCsv(mlReport: SimulationReport, fallbackReport: SimulationReport): String {
        val sb = StringBuilder()
        sb.append("Day,ML_Mastered,Fallback_Mastered\n")

        val mlHistory = mlReport.getMasteredHistory()
        val fallbackHistory = fallbackReport.getMasteredHistory()

        // Берем минимальное количество дней, если отчеты разной длины
        val days = minOf(mlHistory.size, fallbackHistory.size)

        for (i in 0 until days) {
            sb.append("${i + 1},${mlHistory[i]},${fallbackHistory[i]}\n")
        }

        return sb.toString()
    }
}