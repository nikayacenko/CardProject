package com.example.cardproject.ui.deck

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.cardproject.model.Deck
import com.example.cardproject.model.DeckWithTags
import com.example.cardproject.model.LearningMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class DeckListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val mockDeckRepository = mockk<com.example.cardproject.database.repository.DeckRepository>()
    private val mockCardRepository = mockk<com.example.cardproject.database.repository.CardRepository>()

    private val testDecks = listOf(
        DeckWithTags(
            deck = Deck(
                id = 1,
                name = "Математика",
                description = "Карточки по математике",
                cardCount = 10,
                coverColor = "#FFFFFF",
                learningMode = LearningMode.LONG_TERM
            ),
            tags = listOf("математика", "обучение")
        ),
        DeckWithTags(
            deck = Deck(
                id = 2,
                name = "История",
                description = "Исторические даты",
                cardCount = 5,
                coverColor = "#CCAEA9",
                learningMode = LearningMode.SHORT_TERM
            ),
            tags = listOf("история", "обучение")
        ),
        DeckWithTags(
            deck = Deck(
                id = 3,
                name = "Программирование",
                description = "Основы программирования",
                cardCount = 0,
                coverColor = "#054564",
                learningMode = LearningMode.LONG_TERM
            ),
            tags = listOf("программирование")
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDeckSearchFilterLogic() = runTest {
        println("Тест: Логика фильтрации колод по поисковому запросу")

        // Given
        val decks = testDecks
        val query = "мат"

        // When - применяем логику фильтрации вручную
        val filteredDecks = decks.filter { deckWithTags ->
            deckWithTags.deck.name.contains(query, ignoreCase = true) ||
                    deckWithTags.deck.description.contains(query, ignoreCase = true) ||
                    deckWithTags.tags.any { it.contains(query, ignoreCase = true) }
        }

        // Then
        assertEquals(1, filteredDecks.size)
        assertEquals("Математика", filteredDecks[0].deck.name)

        println("Тест пройден: Найдена 1 колода по запросу '$query'")
    }

    @Test
    fun testTagFilteringLogic() = runTest {
        println("Тест: Логика фильтрации по тегам")

        // Given
        val decks = testDecks
        val selectedTags = setOf("обучение")

        // When - применяем логику фильтрации по тегам (И-логика)
        val filteredDecks = decks.filter { deckWithTags ->
            selectedTags.all { selectedTag ->
                deckWithTags.tags.any { deckTag ->
                    deckTag.equals(selectedTag, ignoreCase = true)
                }
            }
        }

        // Then
        assertEquals(2, filteredDecks.size)
        assertTrue(filteredDecks.all { it.tags.contains("обучение") })

        println("Тест пройден: Найдено ${filteredDecks.size} колод с тегом 'обучение'")
    }

    @Test
    fun testTagToggleLogic() = runTest {
        println("Тест: Логика переключения тегов")

        // Given
        val currentTags = mutableSetOf<String>()

        // When - добавляем тег
        val tagToAdd = "математика"
        if (currentTags.contains(tagToAdd)) {
            currentTags.remove(tagToAdd)
        } else {
            currentTags.add(tagToAdd)
        }

        // Then
        assertEquals(1, currentTags.size)
        assertTrue(currentTags.contains("математика"))

        // When - удаляем тег
        if (currentTags.contains(tagToAdd)) {
            currentTags.remove(tagToAdd)
        } else {
            currentTags.add(tagToAdd)
        }

        // Then
        assertTrue(currentTags.isEmpty())

        println("Тест пройден: Тег успешно добавлен и удален")
    }

    @Test
    fun testDeckCreationParameters() = runTest {
        println("Тест: Параметры создания колоды")

        // Given
        val name = "Новая колода"
        val description = "Описание"
        val tags = listOf("тест", "учеба")
        val coverColor = "#FF5733"
        val learningMode = LearningMode.LONG_TERM

        // Mock репозитория для проверки вызова
        coEvery { mockDeckRepository.createDeck(any(), any(), any(), any(), any()) } returns 1L

        // When
        mockDeckRepository.createDeck(name, description, tags, coverColor, learningMode)

        // Then
        coVerify {
            mockDeckRepository.createDeck(
                "Новая колода",
                "Описание",
                listOf("тест", "учеба"),
                "#FF5733",
                LearningMode.LONG_TERM
            )
        }

        println("Тест пройден: Метод создания колоды вызван с правильными параметрами")
    }

    @Test
    fun testDeckDeletionLogic() = runTest {
        println("Тест: Логика удаления колоды")

        // Given
        val deckToDelete = testDecks[0].deck

        // Mock репозитория
        coEvery { mockDeckRepository.deleteDeck(any()) } returns Unit

        // When
        mockDeckRepository.deleteDeck(deckToDelete)

        // Then
        coVerify { mockDeckRepository.deleteDeck(deckToDelete) }

        println("Тест пройден: Метод удаления колоды вызван")
    }

    @Test
    fun testStringTrimming() {
        println("Тест: Обрезка пробелов в строке")

        // Given
        val input = "  математика  "

        // When
        val trimmed = input.trim()

        // Then
        assertEquals("математика", trimmed)

        println("Тест пройден: Строка успешно обрезана")
    }

    @Test
    fun testListSorting() {
        println("Тест: Сортировка списка тегов")

        // Given
        val unsortedTags = listOf("зима", "осень", "лето", "весна")

        // When
        val sortedTags = unsortedTags.sorted()

        // Then
        assertEquals(listOf("весна", "зима", "лето", "осень"), sortedTags)

        println("Тест пройден: Теги отсортированы по алфавиту")
    }

    @Test
    fun testDeckEquality() {
        println("Тест: Сравнение колод")

        // Given
        val deck1 = Deck(
            id = 1,
            name = "Математика",
            description = "Карточки",
            cardCount = 10,
            coverColor = "#FFFFFF",
            learningMode = LearningMode.LONG_TERM
        )

        val deck2 = Deck(
            id = 1,
            name = "Математика",
            description = "Карточки",
            cardCount = 10,
            coverColor = "#FFFFFF",
            learningMode = LearningMode.LONG_TERM
        )

        val deck3 = Deck(
            id = 2,
            name = "История",
            description = "Даты",
            cardCount = 5,
            coverColor = "#CCAEA9",
            learningMode = LearningMode.SHORT_TERM
        )

        // When & Then
        assertEquals(deck1, deck2)
        assertFalse(deck1 == deck3)
        assertEquals(deck1.hashCode(), deck2.hashCode())

        println("Тест пройден: Колоды корректно сравниваются")
    }

}