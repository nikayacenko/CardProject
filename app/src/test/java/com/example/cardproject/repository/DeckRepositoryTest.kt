//// apps/cardproject/src/test/java/com/example/cardproject/repository/DeckRepositoryTest.kt
//package com.example.cardproject.repository
//
//import com.example.cardproject.database.dao.CardDao
//import com.example.cardproject.database.dao.DeckDao
//import com.example.cardproject.database.dao.NoteDao
//import com.example.cardproject.database.dao.TagDao
//import com.example.cardproject.database.repository.CardRepository
//import com.example.cardproject.database.repository.DeckRepository
//import kotlinx.coroutines.flow.first
//import kotlinx.coroutines.flow.flowOf
//import kotlinx.coroutines.runBlocking
//import org.junit.Assert.*
//import org.junit.Before
//import org.junit.Test
//import org.mockito.Mock
//import org.mockito.Mockito.*
//import org.mockito.MockitoAnnotations
//
///**
// * JVM-тесты для DeckRepository.
// *
// * Все зависимости (DAO) заменены моками, поэтому тесты работают без Android.
// * Тестируется только бизнес-логика Repository, а не реальная БД.
// */
//class DeckRepositoryTest {
//
//    @Mock
//    private lateinit var mockDeckDao: DeckDao
//
//    @Mock
//    private lateinit var mockCardDao: CardDao
//
//    @Mock
//    private lateinit var mockTagDao: TagDao
//
//    @Mock
//    private lateinit var mockNoteDao: NoteDao
//
//    @Mock
//    private lateinit var mockCardRepository: CardRepository
//
//    private lateinit var repository: DeckRepository
//
//    @Before
//    fun setup() {
//        MockitoAnnotations.openMocks(this)
//        repository = DeckRepository(
//            deckDao = mockDeckDao,
//            cardRepository = mockCardRepository,
//            tagDao = mockTagDao,
//            noteDao = mockNoteDao
//        )
//    }
//
//    // ============================================================
//    // ТЕСТЫ СОЗДАНИЯ КОЛОДЫ
//    // ============================================================
//
//    @Test
//    fun createDeck_withValidData_shouldReturnSuccess(): Unit = runBlocking {
//        // given: мокаем успешную вставку
//        val expectedDeckId = 1L
//        whenever(mockDeckDao.insertDeck(any())).thenReturn(expectedDeckId)
//
//        // when
//        val result = runCatching {
//            repository.createDeck("Моя колода", "Описание")
//        }
//
//        // then
//        result.onSuccess { deckId ->
//            assertEquals(expectedDeckId, deckId)
//            verify(mockDeckDao).insertDeck(any())
//            println("✅ createDeck_withValidData: Успех, id=$deckId")
//        }.onFailure { error ->
//            fail("Создание не должно было завершиться ошибкой: ${error.message}")
//        }
//    }
//
//    @Test
//    fun createDeck_withEmptyName_shouldReturnFailure(): Unit = runBlocking {
//        // when
//        val result = runCatching {
//            repository.createDeck("", "Описание")
//        }
//
//        // then
//        result.onSuccess { deckId ->
//            fail("Создание с пустым именем не должно быть успешным")
//        }.onFailure { error ->
//            assertNotNull(error)
//            assertTrue(
//                error.message?.contains("пустым") == true ||
//                        error.message?.contains("имя") == true
//            )
//            verify(mockDeckDao, never()).insertDeck(any())
//            println("✅ createDeck_withEmptyName: Ошибка '${error.message}'")
//        }
//    }
//
//    @Test
//    fun createDeck_withWhitespaceOnlyName_shouldReturnFailure(): Unit = runBlocking {
//        // when
//        val result = runCatching {
//            repository.createDeck("   ", "Описание")
//        }
//
//        // then
//        result.onSuccess { deckId ->
//            fail("Имя из пробелов должно быть отклонено")
//        }.onFailure { error ->
//            assertNotNull(error)
//            verify(mockDeckDao, never()).insertDeck(any())
//            println("✅ createDeck_withWhitespaceOnlyName: Ошибка '${error.message}'")
//        }
//    }
//
//    @Test
//    fun createDeck_withTooLongName_shouldReturnFailure(): Unit = runBlocking {
//        // given: имя длиной 1000 символов
//        val tooLongName = "A".repeat(1000)
//
//        // when
//        val result = runCatching {
//            repository.createDeck(tooLongName, "Описание")
//        }
//
//        // then
//        result.onSuccess { deckId ->
//            fail("Слишком длинное имя должно быть отклонено")
//        }.onFailure { error ->
//            assertNotNull(error)
//            assertTrue(
//                error.message?.contains("длин") == true ||
//                        error.message?.contains("превышает") == true
//            )
//            verify(mockDeckDao, never()).insertDeck(any())
//            println("✅ createDeck_withTooLongName: Ошибка '${error.message}'")
//        }
//    }
//
//    @Test
//    fun createDeck_whenDaoFails_shouldPropagateError(): Unit = runBlocking {
//        // given: мокаем ошибку DAO
//        val expectedError = RuntimeException("Ошибка базы данных")
//        whenever(mockDeckDao.insertDeck(any())).thenThrow(expectedError)
//
//        // when
//        val result = runCatching {
//            repository.createDeck("Моя колода", "Описание")
//        }
//
//        // then
//        result.onSuccess { deckId ->
//            fail("Создание должно было завершиться ошибкой")
//        }.onFailure { error ->
//            assertEquals(expectedError, error)
//            println("✅ createDeck_whenDaoFails: Ошибка '${error.message}'")
//        }
//    }
//
//    // ============================================================
//    // ТЕСТЫ ЧТЕНИЯ КОЛОД
//    // ============================================================
//
//    @Test
//    fun getAllDecks_shouldReturnAllDecks(): Unit = runBlocking {
//        // given: мокаем Flow с списком колод
//        val mockDecks = listOf(
//            Deck(id = 1, name = "Колода A"),
//            Deck(id = 2, name = "Колода B"),
//            Deck(id = 3, name = "Колода C")
//        )
//        whenever(mockDeckDao.getAllDecks()).thenReturn(flowOf(mockDecks))
//
//        // when
//        val result = runCatching {
//            repository.getAllDecks().first()
//        }
//
//        // then
//        result.onSuccess { decks ->
//            assertEquals(3, decks.size)
//            assertEquals("Колода A", decks[0].name)
//            assertEquals("Колода B", decks[1].name)
//            assertEquals("Колода C", decks[2].name)
//            println("✅ getAllDecks: Найдено ${decks.size} колод")
//        }.onFailure { error ->
//            fail("Получение не должно было завершиться ошибкой: ${error.message}")
//        }
//    }
//
//    @Test
//    fun getAllDecks_whenDaoReturnsEmptyList_shouldReturnEmptyList(): Unit = runBlocking {
//        // given: мокаем пустой список
//        whenever(mockDeckDao.getAllDecks()).thenReturn(flowOf(emptyList()))
//
//        // when
//        val result = runCatching {
//            repository.getAllDecks().first()
//        }
//
//        // then
//        result.onSuccess { decks ->
//            assertTrue(decks.isEmpty())
//            println("✅ getAllDecks_whenDaoReturnsEmptyList: Пустой список")
//        }.onFailure { error ->
//            fail("Получение не должно было завершиться ошибкой: ${error.message}")
//        }
//    }
//
//    @Test
//    fun getDeckById_withExistingId_shouldReturnDeck(): Unit = runBlocking {
//        // given
//        val expectedDeck = Deck(id = 1L, name = "Тестовая колода")
//        whenever(mockDeckDao.getDeckById(1L)).thenReturn(expectedDeck)
//
//        // when
//        val result = runCatching {
//            repository.getDeckById(1L)
//        }
//
//        // then
//        result.onSuccess { deck ->
//            assertNotNull(deck)
//            assertEquals(1L, deck.id)
//            assertEquals("Тестовая колода", deck.name)
//            println("✅ getDeckById: Найдена колода id=${deck.id}")
//        }.onFailure { error ->
//            fail("Получение не должно было завершиться ошибкой: ${error.message}")
//        }
//    }
//
//    @Test
//    fun getDeckById_withNonExistentId_shouldReturnNull(): Unit = runBlocking {
//        // given
//        whenever(mockDeckDao.getDeckById(999L)).thenReturn(null)
//
//        // when
//        val result = runCatching {
//            repository.getDeckById(999L)
//        }
//
//        // then
//        result.onSuccess { deck ->
//            assertNull(deck)
//            println("✅ getDeckById_withNonExistentId: null (колода не найдена)")
//        }.onFailure { error ->
//            fail("Получение не должно было завершиться ошибкой: ${error.message}")
//        }
//    }
//
//    // ============================================================
//    // ТЕСТЫ ОБНОВЛЕНИЯ КОЛОДЫ
//    // ============================================================
//
//    @Test
//    fun updateDeck_withValidData_shouldUpdateSuccessfully(): Unit = runBlocking {
//        // given
//        val deckId = 1L
//        val existingDeck = Deck(id = deckId, name = "Старое имя")
//        whenever(mockDeckDao.getDeckById(deckId)).thenReturn(existingDeck)
//        whenever(mockDeckDao.updateDeck(any())).thenReturn(1)
//
//        // when
//        val result = runCatching {
//            repository.updateDeck(deckId, "Новое имя", "Новое описание")
//        }
//
//        // then
//        result.onSuccess { updated ->
//            assertTrue(updated)
//            verify(mockDeckDao).updateDeck(any())
//            println("✅ updateDeck_withValidData: Колода обновлена")
//        }.onFailure { error ->
//            fail("Обновление не должно было завершиться ошибкой: ${error.message}")
//        }
//    }
//
//    @Test
//    fun updateDeck_withNonExistentId_shouldReturnFailure(): Unit = runBlocking {
//        // given
//        whenever(mockDeckDao.getDeckById(999L)).thenReturn(null)
//
//        // when
//        val result = runCatching {
//            repository.updateDeck(999L, "Новое имя", "Описание")
//        }
//
//        // then
//        result.onSuccess { updated ->
//            fail("Обновление несуществующей колоды должно завершиться ошибкой")
//        }.onFailure { error ->
//            assertNotNull(error)
//            assertTrue(error.message?.contains("не найдена") == true)
//            verify(mockDeckDao, never()).updateDeck(any())
//            println("✅ updateDeck_withNonExistentId: Ошибка '${error.message}'")
//        }
//    }
//
//    @Test
//    fun updateDeck_withEmptyName_shouldReturnFailure(): Unit = runBlocking {
//        // given
//        val deckId = 1L
//        val existingDeck = Deck(id = deckId, name = "Колода")
//        whenever(mockDeckDao.getDeckById(deckId)).thenReturn(existingDeck)
//
//        // when
//        val result = runCatching {
//            repository.updateDeck(deckId, "", "Описание")
//        }
//
//        // then
//        result.onSuccess { updated ->
//            fail("Пустое имя должно быть отклонено")
//        }.onFailure { error ->
//            assertNotNull(error)
//            assertTrue(error.message?.contains("имя") == true)
//            verify(mockDeckDao, never()).updateDeck(any())
//            println("✅ updateDeck_withEmptyName: Ошибка '${error.message}'")
//        }
//    }
//
//    // ============================================================
//    // ТЕСТЫ УДАЛЕНИЯ КОЛОДЫ
//    // ============================================================
//
//    @Test
//    fun deleteDeck_withExistingId_shouldDeleteSuccessfully(): Unit = runBlocking {
//        // given
//        val deckId = 1L
//        val deck = Deck(id = deckId, name = "Колода для удаления")
//        whenever(mockDeckDao.getDeckById(deckId)).thenReturn(deck)
//        whenever(mockDeckDao.deleteDeck(deck)).thenReturn(1)
//
//        // when
//        val result = runCatching {
//            repository.deleteDeck(deck)
//        }
//
//        // then
//        result.onSuccess {
//            verify(mockDeckDao).deleteDeck(deck)
//            println("✅ deleteDeck_withExistingId: Колода удалена")
//        }.onFailure { error ->
//            fail("Удаление не должно было завершиться ошибкой: ${error.message}")
//        }
//    }
//
//    @Test
//    fun deleteDeck_withNonExistentId_shouldReturnFailure(): Unit = runBlocking {
//        // given: создаём колоду с ID, которого нет в БД
//        val nonExistentDeck = Deck(id = 999L, name = "Не существует")
//        whenever(mockDeckDao.getDeckById(999L)).thenReturn(null)
//
//        // when
//        val result = runCatching {
//            repository.deleteDeck(nonExistentDeck)
//        }
//
//        // then
//        result.onSuccess {
//            fail("Удаление несуществующей колоды должно завершиться ошибкой")
//        }.onFailure { error ->
//            assertNotNull(error)
//            assertTrue(error.message?.contains("не найдена") == true)
//            verify(mockDeckDao, never()).deleteDeck(any())
//            println("✅ deleteDeck_withNonExistentId: Ошибка '${error.message}'")
//        }
//    }
//
//    // ============================================================
//    // ТЕСТЫ ПОИСКА
//    // ============================================================
//
//    @Test
//    fun searchDecks_withKeyword_shouldReturnMatchingDecks(): Unit = runBlocking {
//        // given
//        val mockDecks = listOf(
//            Deck(name = "Python для начинающих"),
//            Deck(name = "Python продвинутый")
//        )
//        whenever(mockDeckDao.getDecksByName("Python")).thenReturn(mockDecks)
//
//        // when
//        val result = runCatching {
//            repository.searchDecks("Python")
//        }
//
//        // then
//        result.onSuccess { decks ->
//            assertEquals(2, decks.size)
//            assertTrue(decks.all { it.name.contains("Python") })
//            println("✅ searchDecks: Найдено ${decks.size} колод")
//        }.onFailure { error ->
//            fail("Поиск не должен был завершиться ошибкой: ${error.message}")
//        }
//    }
//
//    @Test
//    fun searchDecks_withEmptyQuery_shouldReturnAllDecks(): Unit = runBlocking {
//        // given
//        val mockDecks = listOf(
//            Deck(name = "Колода 1"),
//            Deck(name = "Колода 2")
//        )
//        whenever(mockDeckDao.getAllDecks()).thenReturn(mockDecks)
//
//        // when
//        val result = runCatching {
//            repository.searchDecks("")
//        }
//
//        // then
//        result.onSuccess { decks ->
//            assertEquals(2, decks.size)
//            println("✅ searchDecks_withEmptyQuery: Найдено ${decks.size} колод")
//        }.onFailure { error ->
//            fail("Поиск не должен был завершиться ошибкой: ${error.message}")
//        }
//    }
//
//    @Test
//    fun searchDecks_withNonMatchingKeyword_shouldReturnEmptyList(): Unit = runBlocking {
//        // given
//        whenever(mockDeckDao.getDecksByName("Kotlin")).thenReturn(emptyList())
//
//        // when
//        val result = runCatching {
//            repository.searchDecks("Kotlin")
//        }
//
//        // then
//        result.onSuccess { decks ->
//            assertTrue(decks.isEmpty())
//            println("✅ searchDecks_withNonMatchingKeyword: Пустой результат")
//        }.onFailure { error ->
//            fail("Поиск не должен был завершиться ошибкой: ${error.message}")
//        }
//    }
//}
