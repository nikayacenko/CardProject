package com.example.cardproject.database.dao//package com.example.cardproject.database.dao
//
//import androidx.room.Room
//import androidx.test.core.app.ApplicationProvider
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import com.example.cardproject.database.AppDatabase
//import com.example.cardproject.model.Card
//import com.example.cardproject.model.Deck
//import com.example.cardproject.model.LearningMode
//import com.example.cardproject.model.Note
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.flow.first
//import kotlinx.coroutines.test.runTest
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//import org.junit.runner.RunWith
//import kotlin.math.log10
//import kotlin.test.assertTrue
//
//@ExperimentalCoroutinesApi
//@RunWith(AndroidJUnit4::class)
//class DatabasePerformanceTest {
//
//    private lateinit var database: AppDatabase
//    private lateinit var cardDao: CardDao
//    private lateinit var deckDao: DeckDao
//    private lateinit var noteDao: NoteDao
//
//    @Before
//    fun setup() {
//        database = Room.inMemoryDatabaseBuilder(
//            ApplicationProvider.getApplicationContext(),
//            AppDatabase::class.java
//        ).allowMainThreadQueries().build()
//
//        cardDao = database.cardDao()
//        deckDao = database.deckDao()
//        noteDao = database.noteDao()
//    }
//
//    @After
//    fun tearDown() {
//        database.close()
//    }
//
//    @Test
//    fun performanceTest_getCardsByDeck() = runTest {
//        println("\nТЕСТ 1: Получение карточек по колоде")
//        println("=".repeat(50))
//
//        val dataSizes = listOf(10, 100, 1000, 10000)
//        val results = mutableListOf<Pair<Int, Long>>()
//
//        dataSizes.forEach { size ->
//            println("\nТест с $size записями")
//
//            clearDatabase()
//
//            val deck = Deck(
//                id = 1,
//                name = "Test Deck",
//                description = "Performance test deck",
//                cardCount = 0,
//                coverColor = "#FFFFFF",
//                learningMode = LearningMode.LONG_TERM
//            )
//            deckDao.insertDeck(deck)
//
//            val insertTime = kotlin.system.measureTimeMillis {
//                for (index in 1..size) {
//                    val card = Card(
//                        id = index.toLong(),
//                        deckId = 1,
//                        front = "Вопрос $index",
//                        back = "Ответ $index",
//                        reviewStage = 0,
//                        interval = 1,
//                        easeFactor = 2.5,
//                        lastReviewed = null,
//                        nextReview = null,
//                        consecutiveCorrect = 0
//                    )
//                    cardDao.insertCard(card)
//                }
//            }
//            println("Вставка данных: $insertTime мс")
//
//            val executionTime = kotlin.system.measureTimeMillis {
//                val result = cardDao.getCardsByDeck(1).first()
//                println("Найдено карточек: ${result.size}")
//                assertTrue(result.size == size, "Ожидалось $size карточек, найдено ${result.size}")
//            }
//
//            results.add(size to executionTime)
//            println("Время выполнения запроса: $executionTime мс")
//
//            val maxTime = calculateMaxTime(size)
//            assertTrue(
//                executionTime < maxTime,
//                "Запрос слишком медленный для $size записей: $executionTime мс при допустимых $maxTime мс"
//            )
//        }
//
//        printPerformanceTable("getCardsByDeck", results)
//    }
//
//    @Test
//    fun performanceTest_getAllDecks() = runTest {
//        println("\nТЕСТ 2: Получение всех колод")
//        println("=".repeat(50))
//
//        val dataSizes = listOf(10, 100, 1000, 10000)
//        val results = mutableListOf<Pair<Int, Long>>()
//
//        dataSizes.forEach { size ->
//            println("\nТест с $size колодами")
//
//            clearDatabase()
//
//            val insertTime = kotlin.system.measureTimeMillis {
//                for (index in 1..size) {
//                    val deck = Deck(
//                        id = index.toLong(),
//                        name = "Колода $index",
//                        description = "Описание колоды $index",
//                        cardCount = index % 10, // Разное количество карточек
//                        coverColor = "#FFFFFF",
//                        learningMode = LearningMode.LONG_TERM
//                    )
//                    deckDao.insertDeck(deck)
//                }
//            }
//            println("Вставка колод: $insertTime мс")
//
//            val executionTime = kotlin.system.measureTimeMillis {
//                val result = deckDao.getAllDecks().first()
//                println("Найдено колод: ${result.size}")
//                assertTrue(result.size == size, "Ожидалось $size колод, найдено ${result.size}")
//            }
//
//            results.add(size to executionTime)
//            println("Время выполнения запроса: $executionTime мс")
//
//            val maxTime = calculateMaxTime(size)
//            assertTrue(
//                executionTime < maxTime,
//                "Запрос слишком медленный для $size записей: $executionTime мс при допустимых $maxTime мс"
//            )
//        }
//
//        printPerformanceTable("getAllDecks", results)
//    }
//
//    @Test
//    fun performanceTest_searchNotes() = runTest {
//        println("\nТЕСТ 3: Поиск конспектов по тексту")
//        println("=".repeat(50))
//
//        val dataSizes = listOf(10, 100, 1000, 10000)
//        val results = mutableListOf<Pair<Int, Long>>()
//
//        dataSizes.forEach { size ->
//            println("\nТест с $size конспектами")
//
//            clearDatabase()
//
//            val insertTime = kotlin.system.measureTimeMillis {
//                for (index in 1..size) {
//                    val note = Note(
//                        id = index.toLong(),
//                        title = "Конспект по теме $index",
//                        content = "Содержание конспекта $index по важной теме обучения",
//                        createdAt = System.currentTimeMillis(),
//                        updatedAt = System.currentTimeMillis()
//                    )
//                    noteDao.insertNote(note)
//                }
//            }
//            println("Вставка конспектов: $insertTime мс")
//
//            val executionTime = kotlin.system.measureTimeMillis {
//                val result = noteDao.searchNotes("теме").first()
//                println("Найдено конспектов: ${result.size}")
//                val expectedCount = size
//                assertTrue(
//                    result.size == expectedCount,
//                    "Ожидалось $expectedCount конспектов, найдено ${result.size}"
//                )
//            }
//
//            results.add(size to executionTime)
//            println("Время выполнения поиска: $executionTime мс")
//
//            val maxTime = calculateMaxTime(size) * 2 // Поиск может быть медленнее
//            assertTrue(
//                executionTime < maxTime,
//                "Поиск слишком медленный для $size записей: $executionTime мс при допустимых $maxTime мс"
//            )
//        }
//
//        printPerformanceTable("searchNotes", results)
//    }
//
//    private suspend fun clearDatabase() {
//        cardDao.deleteAllCards()
//        deckDao.deleteAllDecks()
//        noteDao.deleteAllNotes()
//    }
//
//    private fun calculateMaxTime(records: Int): Long {
//        return when (records) {
//            10 -> 150
//            100 -> 200
//            1000 -> 1000
//            10000 -> 5000
//            else -> (records * 0.2).toLong()
//        }
//    }
//
//    private fun printPerformanceTable(testName: String, results: List<Pair<Int, Long>>) {
//        println("\nРЕЗУЛЬТАТЫ ТЕСТА: $testName")
//        println("=".repeat(50))
//        println("Кол-во записей | Время (мс) | O(n) оценка")
//        println("-".repeat(50))
//
//        results.forEach { (size, time) ->
//            val complexity = when {
//                time < 20 -> "O(1)"
//                time < size / 10 -> "O(log n)"
//                time < size * 2 -> "O(n)"
//                time < size * log10(size.toDouble()) -> "O(n log n)"
//                else -> "O(n²)"
//            }
//            println("%13d | %10d | %10s".format(size, time, complexity))
//        }
//    }
//
//
//}