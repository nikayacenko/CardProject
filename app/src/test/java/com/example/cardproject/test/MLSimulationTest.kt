// File: app/src/test/java/com/example/cardproject/test/MLSimulationTest.kt
package com.example.cardproject.test

import com.example.cardproject.model.LearningMode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MLSimulationTest {

    @Test
    fun test30DaysSimulation() = runBlocking {
        // 1. Создаем тестовые данные
        val cards = TestDataFactory.createTestDeck(20)

        // 2. Создаем движок симуляции
        val engine = MLSimulationEngine()

        // 3. Запускаем симуляцию на 30 дней
        val report = engine.runSimulation(
            cards = cards,
            days = 30,
            mode = LearningMode.LONG_TERM
        )

        // 4. Проверяем базовые ожидания
        assert(report.masteredCards > 0) { "Должны быть выученные карточки" }
        assert(report.avgInterval > 5) { "Средний интервал должен расти" }

        // 5. Сохраняем результаты
        saveResults(report)
    }

    @Test
    fun test100DaysSimulation() = runBlocking {
        // 1. Создаем тестовые данные
        val cards = TestDataFactory.createTestDeck(20)

        // 2. Создаем движок симуляции
        val engine = MLSimulationEngine()

        // 3. Запускаем симуляцию на 100 дней
        val report = engine.runSimulation(
            cards = cards,
            days = 100,
            mode = LearningMode.LONG_TERM
        )

        // 4. Проверяем что после 100 дней большинство карточек выучены
        assert(report.masteredCards > report.totalCards * 0.7) {
            "После 100 дней должно быть выучено >70% карточек"
        }

        // 5. Сохраняем результаты
        saveResults(report)
    }

    @Test
    fun testCompareModes() = runBlocking {
        val cards = TestDataFactory.createTestDeck(20)
        val engine = MLSimulationEngine()

        // Тестируем LONG_TERM
        val longTermReport = engine.runSimulation(
            cards = cards.map { it.copy() },
            days = 60,
            mode = LearningMode.LONG_TERM
        )

        // Тестируем SHORT_TERM
        val shortTermReport = engine.runSimulation(
            cards = cards.map { it.copy() },
            days = 60,
            mode = LearningMode.SHORT_TERM
        )

        println("\n" + "=".repeat(60))
        println("СРАВНЕНИЕ РЕЖИМОВ")
        println("=".repeat(60))
        println("\n📚 ДОЛГОВРЕМЕННЫЙ:")
        println("• Выучено: ${longTermReport.masteredCards}")
        println("• Средний интервал: ${"%.1f".format(longTermReport.avgInterval)} дней")

        println("\n🎯 КРАТКОВРЕМЕННЫЙ:")
        println("• Выучено: ${shortTermReport.masteredCards}")
        println("• Средний интервал: ${"%.1f".format(shortTermReport.avgInterval)} дней")
    }

    private fun saveResults(report: SimulationReport) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File("build/reports/simulation_$timestamp.csv")
        file.parentFile.mkdirs()
        file.writeText(report.exportToCSV())
        println("\n💾 Результаты сохранены: ${file.absolutePath}")
    }
}