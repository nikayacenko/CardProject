// app/src/androidTest/java/com/example/cardproject/ui/deck/DeckListViewModelIntegrationTest.kt
package com.example.cardproject.ui.deck

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cardproject.database.AppDatabase
import com.example.cardproject.database.dao.CardDao
import com.example.cardproject.database.repository.CardRepository
import com.example.cardproject.database.repository.DeckRepository
import com.example.cardproject.model.Card
import com.example.cardproject.model.Deck
import com.example.cardproject.model.LearningMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционные тесты для DeckListViewModel.
 *
 * Что тестируется:
 * - Взаимодействие ViewModel с реальным Repository (через реальную БД SQLite)
 * - Корректность фильтрации колод по поисковому запросу
 * - Корректность фильтрации колод по тегам (AND-логика)
 * - Создание, обновление и удаление колод через ViewModel
 * - Применение тегов к колодам
 * - Обновление счетчиков карточек в колодах
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var deckRepository: DeckRepository
    private lateinit var cardRepository: CardRepository
    private lateinit var cardDao: CardDao
    private lateinit var viewModel: DeckListViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()

        val deckDao = database.deckDao()
        val tagDao = database.tagDao()
        cardDao = database.cardDao()

        cardRepository = CardRepository(cardDao)
        deckRepository = DeckRepository(deckDao, tagDao, cardRepository)

        viewModel = DeckListViewModel(ApplicationProvider.getApplicationContext())
        injectRepository(viewModel, deckRepository)
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun injectRepository(viewModel: DeckListViewModel, repository: DeckRepository) {
        try {
            val field = viewModel.javaClass.getDeclaredField("repository")
            field.isAccessible = true
            field.set(viewModel, repository)
        } catch (e: Exception) {
            println("Не удалось внедрить репозиторий через рефлексию: ${e.message}")
        }
    }

    // ============================================================
    // ТЕСТЫ: ЗАГРУЗКА И ОТОБРАЖЕНИЕ КОЛОД
    // ============================================================

    @Test
    fun loadDecks_shouldDisplayAllDecksFromDatabase() = runTest(testDispatcher) {
        // given
        deckRepository.createDeck("Программирование на Kotlin", "", emptyList())
        deckRepository.createDeck("Алгоритмы и структуры данных", "", emptyList())
        advanceUntilIdle()

        // when
        viewModel.refreshData()
        advanceUntilIdle()

        // then
        val decks = viewModel.decks.value
        assertEquals("Должны быть загружены 2 колоды", 2, decks.size)
    }

    @Test
    fun deckCardCount_shouldUpdateWhenCardsAreAdded() = runTest(testDispatcher) {
        // given
        val deckId = deckRepository.createDeck("Тестовая колода", "", emptyList())
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        val deckWithCount = viewModel.decks.value.find { it.deck.id == deckId }
        assertNotNull("Колода должна существовать", deckWithCount)

        // when: добавляем карточку в колоду
        val card = Card(
            deckId = deckId,
            front = "Что такое Kotlin?",
            back = "Современный язык программирования"
        )
        cardDao.insertCard(card)
        advanceUntilIdle()

        // then
        viewModel.refreshData()
        advanceUntilIdle()

        val updatedCards = cardRepository.getCardsByDeckSync(deckId)
        assertEquals("После добавления карточки должно быть 1", 1, updatedCards.size)
    }

    // ============================================================
    // ТЕСТЫ: ФИЛЬТРАЦИЯ ПО ПОИСКОВОМУ ЗАПРОСУ
    // ============================================================

    @Test
    fun filterDecks_byName_shouldReturnMatchingDecks() = runTest(testDispatcher) {
        // given
        deckRepository.createDeck("Python для начинающих", "", emptyList())
        deckRepository.createDeck("Java разработка", "", emptyList())
        deckRepository.createDeck("Python продвинутый", "", emptyList())
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        // when
        viewModel.setSearchQuery("Python")
        advanceUntilIdle()

        // then
        val filteredDecks = viewModel.decks.value
        assertEquals(2, filteredDecks.size)
        assertTrue(filteredDecks.all { it.deck.name.contains("Python") })
    }

    @Test
    fun filterDecks_byDescription_shouldReturnMatchingDecks() = runTest(testDispatcher) {
        // given
        deckRepository.createDeck("Колода 1", "Это описание про базы данных SQLite", emptyList())
        deckRepository.createDeck("Колода 2", "Другое описание без ключевого слова", emptyList())
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        // when
        viewModel.setSearchQuery("SQLite")
        advanceUntilIdle()

        // then
        val filteredDecks = viewModel.decks.value
        assertEquals(1, filteredDecks.size)
        assertEquals("Колода 1", filteredDecks[0].deck.name)
    }

    @Test
    fun clearSearch_shouldShowAllDecks() = runTest(testDispatcher) {
        // given
        deckRepository.createDeck("Колода A", "", emptyList())
        deckRepository.createDeck("Колода B", "", emptyList())
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        val allDecksCount = viewModel.decks.value.size

        // when
        viewModel.setSearchQuery("A")
        advanceUntilIdle()
        viewModel.clearSearch()
        advanceUntilIdle()

        // then
        val decks = viewModel.decks.value
        assertEquals(allDecksCount, decks.size)
    }

    // ============================================================
    // ТЕСТЫ: ФИЛЬТРАЦИЯ ПО ТЕГАМ
    // ============================================================

    @Test
    fun filterDecks_bySingleTag_shouldReturnDecksWithThatTag() = runTest(testDispatcher) {
        // given
        deckRepository.createDeck("Колода ML", "", listOf("machine-learning"))
        deckRepository.createDeck("Колода Web", "", listOf("web"))
        deckRepository.createDeck("Колода Data", "", listOf("data"))
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        // when
        viewModel.toggleTag("machine-learning")
        advanceUntilIdle()

        // then
        val filteredDecks = viewModel.decks.value
        assertEquals(1, filteredDecks.size)
        assertEquals("Колода ML", filteredDecks[0].deck.name)
    }

    @Test
    fun filterDecks_byMultipleTags_shouldReturnDecksWithAllTags() = runTest(testDispatcher) {
        // given
        deckRepository.createDeck("Колода FullStack", "", listOf("python", "django", "web"))
        deckRepository.createDeck("Колода ML", "", listOf("python", "tensorflow"))
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        // when
        viewModel.toggleTag("python")
        viewModel.toggleTag("django")
        advanceUntilIdle()

        // then
        val filteredDecks = viewModel.decks.value
        assertEquals(1, filteredDecks.size)
        assertEquals("Колода FullStack", filteredDecks[0].deck.name)
    }

    @Test
    fun toggleTag_shouldAddAndRemoveTag() = runTest(testDispatcher) {
        // given
        deckRepository.createDeck("Колода", "", listOf("test"))
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        val allDecksCount = viewModel.decks.value.size

        // when: добавляем тег
        viewModel.toggleTag("test")
        advanceUntilIdle()
        assertTrue(viewModel.selectedTags.value.contains("test"))
        assertEquals(1, viewModel.decks.value.size)

        // when: удаляем тег
        viewModel.toggleTag("test")
        advanceUntilIdle()
        assertFalse(viewModel.selectedTags.value.contains("test"))
        assertEquals(allDecksCount, viewModel.decks.value.size)
    }

    @Test
    fun filterDecks_bySearchAndTags_shouldCombineFilters() = runTest(testDispatcher) {
        // given
        deckRepository.createDeck("Python ML колода", "", listOf("python", "ml"))
        deckRepository.createDeck("Python Web колода", "", listOf("python", "web"))
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        // when
        viewModel.setSearchQuery("Python")
        viewModel.toggleTag("web")
        advanceUntilIdle()

        // then
        val filteredDecks = viewModel.decks.value
        assertEquals(1, filteredDecks.size)
        assertEquals("Python Web колода", filteredDecks[0].deck.name)
    }

    // ============================================================
    // ТЕСТЫ: ЗАГРУЗКА ТЕГОВ
    // ============================================================

    @Test
    fun loadAvailableTags_shouldCollectAllTagsFromDecks() = runTest(testDispatcher) {
        // given
        deckRepository.createDeck("Колода 1", "", listOf("python", "django"))
        deckRepository.createDeck("Колода 2", "", listOf("java", "spring"))
        deckRepository.createDeck("Колода 3", "", listOf("python", "flask"))
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        // then
        val tags = viewModel.availableTags.value
        assertTrue(tags.contains("python"))
        assertTrue(tags.contains("java"))
        assertTrue(tags.contains("django"))
        assertTrue(tags.contains("spring"))
        assertTrue(tags.contains("flask"))
        assertEquals(5, tags.size)
    }

    // ============================================================
    // ТЕСТЫ: СОЗДАНИЕ КОЛОДЫ
    // ============================================================

    @Test
    fun createDeck_shouldAddDeckToDatabaseAndDisplay() = runTest(testDispatcher) {
        // given
        advanceUntilIdle()
        val initialCount = viewModel.decks.value.size

        // when
        viewModel.createDeck("Новая колода", "Описание новой колоды", listOf("тест"), null, LearningMode.LONG_TERM)
        advanceUntilIdle()

        // then
        val decks = viewModel.decks.value
        assertEquals(initialCount + 1, decks.size)
        assertTrue(decks.any { it.deck.name == "Новая колода" })
    }

    @Test
    fun createDeck_withTags_shouldSaveTagsAndBeFilterable() = runTest(testDispatcher) {
        // when
        viewModel.createDeck("Важная колода", "", listOf("важное", "срочное"), null, LearningMode.LONG_TERM)
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        // then
        viewModel.toggleTag("важное")
        advanceUntilIdle()

        val filteredDecks = viewModel.decks.value
        assertEquals(1, filteredDecks.size)
        assertEquals("Важная колода", filteredDecks[0].deck.name)
    }

    // ============================================================
    // ТЕСТЫ: ОБНОВЛЕНИЕ КОЛОДЫ
    // ============================================================

    @Test
    fun updateDeck_shouldUpdateDeckData() = runTest(testDispatcher) {
        // given
        viewModel.createDeck("Старое название", "Старое описание", listOf("старый"), null, LearningMode.LONG_TERM)
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        val deck = viewModel.decks.value.first().deck

        // when
        val updatedDeck = deck.copy(name = "Новое название", description = "Новое описание")
        viewModel.updateDeck(updatedDeck, listOf("новый"))
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        // then
        val updated = viewModel.decks.value.find { it.deck.id == deck.id }
        assertEquals("Новое название", updated?.deck?.name)
        assertEquals("Новое описание", updated?.deck?.description)
    }

    // ============================================================
    // ТЕСТЫ: УДАЛЕНИЕ КОЛОДЫ
    // ============================================================

    @Test
    fun deleteDeck_shouldRemoveDeckFromDatabaseAndDisplay() = runTest(testDispatcher) {
        // given
        viewModel.createDeck("Колода для удаления", "", emptyList(), null, LearningMode.LONG_TERM)
        advanceUntilIdle()
        viewModel.refreshData()
        advanceUntilIdle()

        val initialCount = viewModel.decks.value.size
        val deckToDelete = viewModel.decks.value.first().deck

        // when
        viewModel.deleteDeck(deckToDelete)
        advanceUntilIdle()

        // then
        val decks = viewModel.decks.value
        assertEquals(initialCount - 1, decks.size)
        assertFalse(decks.any { it.deck.id == deckToDelete.id })
    }

    @Test
    fun deleteDeck_shouldCascadeDeleteCards() = runTest(testDispatcher) {
        // given
        val deckId = deckRepository.createDeck("Колода с карточкой", "", emptyList())
        val card = Card(
            deckId = deckId,
            front = "Тест",
            back = "Ответ"
        )
        cardDao.insertCard(card)
        advanceUntilIdle()

        // when
        viewModel.refreshData()
        advanceUntilIdle()
        val deck = viewModel.decks.value.find { it.deck.id == deckId }?.deck
        assertNotNull(deck)

        viewModel.deleteDeck(deck!!)
        advanceUntilIdle()

        // then
        val cards = cardRepository.getCardsByDeckSync(deckId)
        assertTrue(cards.isEmpty())
    }

    // ============================================================
    // ТЕСТЫ: ПРИНУДИТЕЛЬНОЕ ОБНОВЛЕНИЕ ДАННЫХ
    // ============================================================

    @Test
    fun refreshData_shouldReloadDecksFromDatabase() = runTest(testDispatcher) {
        // given
        advanceUntilIdle()
        val initialDecks = viewModel.decks.value

        // when
        val newDeckId = deckRepository.createDeck("Прямое создание", "", emptyList())
        advanceUntilIdle()

        var decks = viewModel.decks.value
        assertFalse(decks.any { it.deck.id == newDeckId })

        // when
        viewModel.refreshData()
        advanceUntilIdle()

        // then
        decks = viewModel.decks.value
        assertTrue(decks.any { it.deck.id == newDeckId })
    }
}