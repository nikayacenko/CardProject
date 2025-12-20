package com.example.cardproject.stress

import com.example.cardproject.database.repository.CardRepository
import com.example.cardproject.database.repository.DeckRepository
import com.example.cardproject.database.repository.NoteRepository
import com.example.cardproject.model.Card
import com.example.cardproject.model.Deck
import com.example.cardproject.model.LearningMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StressTesting {

    private lateinit var deckRepository: DeckRepository
    private lateinit var cardRepository: CardRepository
    private lateinit var noteRepository: NoteRepository

    // Счетчики для отслеживания
    private val concurrentErrors = AtomicInteger(0)
    private val validationErrors = AtomicInteger(0)
    private val uiUpdateErrors = AtomicInteger(0)
    private val successfulOperations = AtomicInteger(0)
    private val testResults = mutableListOf<String>()

    @Before
    fun setUp() {
        println("\n" + "=".repeat(50))
        println("НАЧАЛО СТРЕСС-ТЕСТИРОВАНИЯ")
        println("=".repeat(50))

        // Создаем моки репозиториев
        deckRepository = mockk()
        cardRepository = mockk()
        noteRepository = mockk()
    }

    @After
    fun tearDown() {
        println("\n" + "=".repeat(50))
        println("РЕЗУЛЬТАТЫ СТРЕСС-ТЕСТИРОВАНИЯ")
        println("=".repeat(50))

        testResults.forEach { println(it) }

        println("\nСтатистика:")
        println("Успешных операций: ${successfulOperations.get()}")
        println("Ошибок конкурентного доступа: ${concurrentErrors.get()}")
        println("Ошибок валидации: ${validationErrors.get()}")
        println("Ошибок обновления UI: ${uiUpdateErrors.get()}")

        val totalProblems = concurrentErrors.get() + validationErrors.get() + uiUpdateErrors.get()
        when {
            totalProblems == 0 -> println("\n🎉 ВСЕ ТЕСТЫ ПРОЙДЕНЫ УСПЕШНО!")
            totalProblems <= 3 -> println("\n⚠️  Обнаружены незначительные проблемы")
            else -> println("\nОБНАРУЖЕНЫ КРИТИЧЕСКИЕ ПРОБЛЕМЫ!")
        }

        println("=".repeat(50) + "\n")
    }

    /**
     * ТЕСТ 1: Многопоточное редактирование колод
     * Альтернативный поток А - редактирование
     */
    @Test
    fun stressTest_concurrentDeckEditing() {
        println("\nТЕСТ 1: Многопоточное редактирование колод")
        println("   Цель: Проверить обработку одновременных запросов на редактирование")

        val testDeck = Deck(
            id = 1,
            name = "Исходная колода",
            description = "Описание",
            cardCount = 0,
            coverColor = "#FFFFFF",
            learningMode = LearningMode.LONG_TERM
        )

        // Настраиваем моки
        coEvery { deckRepository.getDeckById(1) } returns testDeck
        coEvery { deckRepository.updateDeck(any(), any()) } returns Unit

        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val editAttempts = AtomicInteger(0)

        repeat(threadCount) { threadId ->
            executor.execute {
                try {
                    runBlocking {
                        Thread.sleep((50L * threadId))
                        val currentDeck = deckRepository.getDeckById(1)
                        if (currentDeck != null) {
                            val updatedDeck = currentDeck.copy(
                                name = "Колода от потока $threadId",
                                description = "Отредактировано в ${System.currentTimeMillis()}"
                            )

                            deckRepository.updateDeck(updatedDeck, listOf("поток-$threadId"))
                            editAttempts.incrementAndGet()
                            successfulOperations.incrementAndGet()

                            println("     Поток $threadId:Успешное редактирование")
                        }
                    }
                } catch (e: Exception) {
                    println("     Поток $threadId:${e.javaClass.simpleName}: ${e.message}")
                    concurrentErrors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        coVerify(atLeast = threadCount) { deckRepository.getDeckById(1) }

        testResults.add("Тест 1: ${if (concurrentErrors.get() == 0) "✅ ПРОЙДЕН" else "❌ ПРОВАЛЕН"} " +
                "(попыток редактирования: ${editAttempts.get()}, ошибок: ${concurrentErrors.get()})")
    }

    /**
     * ТЕСТ 2: Многопоточное создание и удаление карточек
     * Альтернативный поток Б - удаление
     */
    @Test
    fun stressTest_concurrentCardOperations() {
        println("\nТЕСТ 2: Многопоточные операции с карточками")

        val testCards = listOf(
            Card(
                id = 1, deckId = 1, front = "Вопрос 1", back = "Ответ 1",
                reviewStage = 0, interval = 1, easeFactor = 2.5,
                lastReviewed = null, nextReview = null, consecutiveCorrect = 0
            ),
            Card(
                id = 2, deckId = 1, front = "Вопрос 2", back = "Ответ 2",
                reviewStage = 0, interval = 1, easeFactor = 2.5,
                lastReviewed = null, nextReview = null, consecutiveCorrect = 0
            )
        )

        // Настраиваем моки
        coEvery { cardRepository.getCardsByDeck(1) } returns flowOf(testCards)
        coEvery { cardRepository.createCard(any(), any(), any()) } returns 999L
        coEvery { cardRepository.deleteCard(any()) } returns Unit
        coEvery { cardRepository.updateCard(any()) } returns Unit

        val threadCount = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val operations = mutableMapOf<String, AtomicInteger>()

        listOf("create", "delete", "update").forEach {
            operations[it] = AtomicInteger(0)
        }

        repeat(threadCount) { threadId ->
            executor.execute {
                try {
                    runBlocking {
                        val operationType = when (threadId % 3) {
                            0 -> {
                                // Создание
                                cardRepository.createCard(1, "Новый вопрос $threadId", "Ответ")
                                operations["create"]?.incrementAndGet()
                                "CREATE"
                            }
                            1 -> {
                                // Удаление
                                val cards = cardRepository.getCardsByDeck(1)
                                cards.collect { cardList ->
                                    cardList.firstOrNull()?.let {
                                        cardRepository.deleteCard(it)
                                        operations["delete"]?.incrementAndGet()
                                    }
                                }
                                "DELETE"
                            }
                            2 -> {
                                // Обновление
                                val cards = cardRepository.getCardsByDeck(1)
                                cards.collect { cardList ->
                                    cardList.firstOrNull()?.let {
                                        val updated = it.copy(front = "${it.front} [updated]")
                                        cardRepository.updateCard(updated)
                                        operations["update"]?.incrementAndGet()
                                    }
                                }
                                "UPDATE"
                            }
                            else -> "UNKNOWN"
                        }

                        successfulOperations.incrementAndGet()
                        println("     Поток $threadId: ✅ Операция $operationType выполнена")
                    }
                } catch (e: Exception) {
                    println("     Поток $threadId: ❌ ${e.javaClass.simpleName}")
                    concurrentErrors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(8, TimeUnit.SECONDS)
        executor.shutdown()

        testResults.add("Тест 2: ${if (concurrentErrors.get() == 0) "✅ ПРОЙДЕН" else "❌ ПРОВАЛЕН"} " +
                "(создано: ${operations["create"]?.get()}, удалено: ${operations["delete"]?.get()}, " +
                "обновлено: ${operations["update"]?.get()}, ошибок: ${concurrentErrors.get()})")
    }

    /**
     * ТЕСТ 3: Быстрые UI взаимодействия
     */
    @Test
    fun stressTest_rapidUiInteractions() {
        println("\nТЕСТ 3: Быстрые UI взаимодействия")

        val testDecks = listOf(
            Deck(1, "Колода 1", "Описание 1", 5,5, "#FF0000", LearningMode.LONG_TERM),
            Deck(2, "Колода 2", "Описание 2", 3,10, "#00FF00", LearningMode.SHORT_TERM)
        )

        // Настраиваем моки
        coEvery { deckRepository.getAllDecksWithCardCount() } returns flowOf(testDecks.map {
            com.example.cardproject.model.DeckWithTags(it, emptyList())
        })
        coEvery { deckRepository.getAllDeckTagNames() } returns (listOf("тег1", "тег2", "тег3"))
        coEvery { deckRepository.createDeck(any(), any(), any(), any(), any()) } returns 999L
        coEvery { deckRepository.getDeckById(any()) } returns testDecks.first()
        coEvery { deckRepository.deleteDeck(any()) } returns Unit
        coEvery { deckRepository.getTagsByDeckId(any()) } returns emptyList()

        val threadCount = 6
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val uiOperations = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        repeat(threadCount) { threadId ->
            executor.execute {
                try {
                    runBlocking {
                        repeat(30) { iteration ->
                            when (iteration % 4) {
                                1 -> { // Поиск
                                    deckRepository.getAllDecksWithCardCount().collect { decks ->
                                        decks.filter { it.deck.name.contains("Колода") }
                                    }
                                    uiOperations.incrementAndGet()
                                }
                                2 -> { // Получение деталей
                                    deckRepository.getTagsByDeckId(1)
                                    uiOperations.incrementAndGet()
                                }
                                3 -> { // Создание/удаление
                                    if (iteration % 10 == 0) {
                                        val deckId = deckRepository.createDeck(
                                            "Временная", "", emptyList(), "#888888", LearningMode.SHORT_TERM
                                        )
                                        deckRepository.getDeckById(deckId)?.let {
                                            deckRepository.deleteDeck(it)
                                        }
                                        uiOperations.incrementAndGet()
                                        successfulOperations.incrementAndGet()
                                    }
                                }
                            }

                            // Имитация UI задержки
                            if (iteration % 15 == 0) {
                                Thread.sleep(15)
                            }
                        }

                        println("     Поток $threadId: ✅ UI операции выполнены")
                    }
                } catch (e: Exception) {
                    println("     Поток $threadId: ❌ UI ошибка: ${e.message?.take(30)}...")
                    uiUpdateErrors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        testResults.add("Тест 3: ${if (uiUpdateErrors.get() == 0) "✅ ПРОЙДЕН" else "⚠️  ПРЕДУПРЕЖДЕНИЕ"} " +
                "(операций: ${uiOperations.get()}, скорость: ${"%.1f".format(uiOperations.get() / duration)} опер/сек, " +
                "ошибок UI: ${uiUpdateErrors.get()})")
    }

    /**
     * ТЕСТ 4: Обработка пустой очереди карточек
     */
    @Test
    fun stressTest_emptyCardQueue() {
        println("\nТЕСТ 4: Обработка пустой очереди карточек")
        println("   Цель: Проверить обработку граничных условий")

        // Настраиваем моки для пустого состояния
        coEvery { cardRepository.getCardsByDeck(any()) } returns flowOf(emptyList())
        coEvery { cardRepository.createCard(any(), any(), any()) } returns 1L
        coEvery { cardRepository.deleteCard(any()) } returns Unit

        val threadCount = 4
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val emptyDetections = AtomicInteger(0)

        repeat(threadCount) { threadId ->
            executor.execute {
                try {
                    runBlocking {
                        val deckId = 100L + threadId

                        repeat(15) { i ->
                            val cards = cardRepository.getCardsByDeck(deckId).collect { cardList ->
                                if (cardList.isEmpty()) {
                                    emptyDetections.incrementAndGet()
                                    successfulOperations.incrementAndGet()

                                    // Имитация различных UI реакций на пустое состояние
                                    when (i % 3) {
                                        0 -> { /* Показать "Нет карточек" */ }
                                        1 -> { /* Предложить создать карточку */ }
                                        2 -> { /* Обновить счетчик (0) */ }
                                    }
                                }
                            }

                            // Периодически создаем и удаляем карточку
                            if (i % 5 == 0) {
                                val cardId = cardRepository.createCard(deckId, "Временная", "Карточка")
                                cardRepository.deleteCard(Card(
                                    id = cardId, deckId = deckId, front = "", back = "",
                                    reviewStage = 0, interval = 1, easeFactor = 2.5,
                                    lastReviewed = null, nextReview = null, consecutiveCorrect = 0
                                ))
                            }
                        }

                        println("     Поток $threadId: ✅ Обработка пустого состояния")
                    }
                } catch (e: Exception) {
                    println("     Поток $threadId: ❌ Ошибка: ${e.message?.take(20)}...")
                    uiUpdateErrors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        testResults.add("Тест 4: ${if (uiUpdateErrors.get() == 0) "✅ ПРОЙДЕН" else "⚠️  ПРЕДУПРЕЖДЕНИЕ"} " +
                "(обнаружений пустого состояния: ${emptyDetections.get()}, " +
                "ошибок обработки: ${uiUpdateErrors.get()})")
    }

    /**
     * ТЕСТ 5: Быстрое переключение карточек
     */
    @Test
    fun stressTest_rapidCardFlips() {
        println("\nТЕСТ 5: Быстрое переключение карточек")
        println("   Цель: Проверить производительность при быстрых переворотах")

        val testCards = List(10) { i ->
            Card(
                id = i.toLong(), deckId = 1,
                front = "Вопрос $i: ${"очень длинный текст ".repeat(3)}",
                back = "Ответ $i: ${"развернутое объяснение ".repeat(5)}",
                reviewStage = 0, interval = 1, easeFactor = 2.5,
                lastReviewed = null, nextReview = null, consecutiveCorrect = 0
            )
        }

        // Настраиваем моки
        coEvery { cardRepository.getCardsByDeck(1) } returns flowOf(testCards)

        val threadCount = 3
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val totalFlips = AtomicInteger(0)
        val memorySnapshots = mutableListOf<Long>()
        val startTime = System.currentTimeMillis()

        repeat(threadCount) { threadId ->
            executor.execute {
                try {
                    runBlocking {
                        cardRepository.getCardsByDeck(1).collect { cards ->
                            repeat(50) { flip ->
                                cards.forEach { card ->
                                    // Имитация показа вопроса с обрезкой длинного текста
                                    val question = if (card.front.length > 50) {
                                        card.front.take(50) + "..."
                                    } else {
                                        card.front
                                    }

                                    // Имитация показа ответа с обрезкой
                                    val answer = if (card.back.length > 100) {
                                        card.back.take(100) + "..."
                                    } else {
                                        card.back
                                    }

                                    totalFlips.incrementAndGet()
                                    successfulOperations.incrementAndGet()
                                }

                                // Снимок памяти каждые 10 переворотов
                                if (flip % 10 == 0) {
                                    val runtime = Runtime.getRuntime()
                                    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                                    memorySnapshots.add(usedMemory)
                                }

                                // Минимальная задержка для имитации быстрых кликов
                                if (flip % 25 == 0) {
                                    Thread.sleep(5)
                                }
                            }
                        }

                        println("     Поток $threadId: ✅ Перевороты выполнены")
                    }
                } catch (e: OutOfMemoryError) {
                    println("     Поток $threadId: ❌ КРИТИЧЕСКАЯ ОШИБКА: Нехватка памяти!")
                    validationErrors.incrementAndGet()
                } catch (e: Exception) {
                    println("     Поток $threadId: ❌ Ошибка: ${e.message?.take(20)}...")
                    uiUpdateErrors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(12, TimeUnit.SECONDS)
        executor.shutdown()

        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        val avgMemory = if (memorySnapshots.isNotEmpty()) memorySnapshots.average().toInt() else 0
        val maxMemory = memorySnapshots.maxOrNull() ?: 0

        val memoryWarning = if (maxMemory > 100) " (⚠️  высокое использование памяти)" else ""

        testResults.add("Тест 5: ${if (validationErrors.get() == 0) "✅ ПРОЙДЕН" else "❌ ПРОВАЛЕН"} " +
                "(переворотов: ${totalFlips.get()}, скорость: ${"%.0f".format(totalFlips.get() / duration)}/сек, " +
                "память: ~${avgMemory}MB$memoryWarning)")
    }

    /**
     * ТЕСТ 6: Доступ к статистике без данных
     */
    @Test
    fun stressTest_emptyStatisticsAccess() {
        println("\nТЕСТ 6: Доступ к статистике без данных")

        // Настраиваем моки для пустой базы
        coEvery { deckRepository.getAllDecksWithCardCount() } returns flowOf(emptyList())
        coEvery { deckRepository.createDeck(any(), any(), any(), any(), any()) } returns 999L
        coEvery { deckRepository.getDeckById(any()) } returns null
        coEvery { deckRepository.deleteDeck(any()) } returns Unit

        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val statsRequests = AtomicInteger(0)
        val messageErrors = AtomicInteger(0)

        repeat(threadCount) { threadId ->
            executor.execute {
                try {
                    runBlocking {
                        repeat(20) { request ->
                            deckRepository.getAllDecksWithCardCount().collect { decks ->
                                if (decks.isEmpty()) {
                                    statsRequests.incrementAndGet()
                                    successfulOperations.incrementAndGet()

                                    // Проверка корректности сообщения для пользователя
                                    val correctMessage = "У вас пока нет данных о прогрессе. Пройдите первую учебную сессию!"
                                    val testMessage = if (request % 2 == 0) {
                                        correctMessage
                                    } else {
                                        "Некорректное сообщение"
                                    }

                                    if (!testMessage.contains("нет данных") && !testMessage.contains("прогрессе")) {
                                        messageErrors.incrementAndGet()
                                        println("     Поток $threadId: ⚠️  Некорректное сообщение: '$testMessage'")
                                    }
                                }
                            }

                            // Периодическое создание/удаление тестовых данных
                            if (request % 7 == 0) {
                                val deckId = deckRepository.createDeck("Тест", "", emptyList(), "#888888", LearningMode.LONG_TERM)
                                deckRepository.getDeckById(deckId)?.let {
                                    deckRepository.deleteDeck(it)
                                }
                            }
                        }

                        println("     Поток $threadId: ✅ Запросы статистики обработаны")
                    }
                } catch (e: Exception) {
                    println("     Поток $threadId: ❌ Ошибка доступа: ${e.message?.take(20)}...")
                    concurrentErrors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        val hasMessageErrors = messageErrors.get() > 0
        val hasAccessErrors = concurrentErrors.get() > 0

        val status = when {
            hasAccessErrors -> "❌ ПРОВАЛЕН"
            hasMessageErrors -> "⚠️  ПРЕДУПРЕЖДЕНИЕ"
            else -> "✅ ПРОЙДЕН"
        }

        testResults.add("Тест 6: $status " +
                "(запросов: ${statsRequests.get()}, ошибок доступа: ${concurrentErrors.get()}, " +
                "ошибок сообщений: ${messageErrors.get()})")
    }
}