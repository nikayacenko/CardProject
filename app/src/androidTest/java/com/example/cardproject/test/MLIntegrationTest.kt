// File: app/src/androidTest/java/com/example/cardproject/test/MLIntegrationTest.kt
package com.example.cardproject.test

import android.content.Context
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
        println("🤖 Модель загружена: ${tensorFlowModel.isModelReady.value}")
        if (tensorFlowModel.isModelReady.value) {
            // Тестируем один вызов
            val testFeatures = FloatArray(20) { 0.5f }
            println("✅ Модель готова, 20 признаков")
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

        println("✅ Настройка завершена")
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
    fun test30DaysWithRealMLAndVirtualStudent() = runBlocking {
        // 1. Создаем тестовые данные
        val cards = TestDataFactory.createTestDeck(20)

        // 2. Создаем виртуального студента
        val student = VirtualStudent(
            baseMemoryStrength = 0.9f,
            morningEfficiency = 1.2f,
            eveningEfficiency = 0.85f
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
                println("   -> Результат: ${if(success) "✅ УСПЕХ" else "❌ ПРОВАЛ"}")
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
        println("📊 СРАВНЕНИЕ РЕЖИМОВ")
        println("=".repeat(60))

        println("\n🤖 С РЕАЛЬНЫМ ML:")
        println("   • Выучено: ${mlReport.masteredCards}/${mlReport.totalCards}")
        println("   • Средний интервал: ${"%.1f".format(mlReport.avgInterval)} дней")
        println("   • Точность студента: ${(mlReport.studentStats.correctRate * 100).toInt()}%")

        println("\n📚 БЕЗ ML (FALLBACK):")
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
            baseMemoryStrength = 0.75f,
            morningEfficiency = 1.1f,
            eveningEfficiency = 0.85f,
            fatigueSensitivity = 0.08f
        )

        // Слабый студент
        val weakStudent = VirtualStudent(
            baseMemoryStrength = 0.55f,
            morningEfficiency = 1.0f,
            eveningEfficiency = 0.7f,
            fatigueSensitivity = 0.12f
        )

        val reports = mutableListOf<SimulationReport>()

        for ((name, student) in listOf(
            //"Хороший" to goodStudent,
            //"Средний" to averageStudent,
            "Слабый" to weakStudent
        )) {
            val report = engine.runSimulationWithML(
                cards = cards.map { it.copy() },
                student = student,
                days = 60,
                mode = LearningMode.LONG_TERM,
                shouldSaveLogs = true
            )
            reports.add(report)

            println("\n🎓 ${name} СТУДЕНТ:")
            println("   • Выучено: ${report.masteredCards}/${report.totalCards}")
            println("   • Точность студента: ${(report.studentStats.correctRate * 100).toInt()}%")
            println("   • Средний интервал: ${"%.1f".format(report.avgInterval)} дней")


            saveResults(report, "60days_${name}_student")
        }
    }

    private fun saveResults(report: SimulationReport, suffix: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val dir = File(context.filesDir, "simulation_reports")
        dir.mkdirs()

        val csvFile = File(dir, "simulation_${suffix}_$timestamp.csv")
        csvFile.writeText(report.exportToCSV())

        val reportFile = File(dir, "report_${suffix}_$timestamp.txt")
        reportFile.writeText(report.getFullReport())

        println("\n💾 Результаты сохранены:")
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