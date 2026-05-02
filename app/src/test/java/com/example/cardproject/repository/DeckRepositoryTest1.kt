// apps/cardproject/src/test/java/com/example/cardproject/repository/DeckRepositoryTest.kt
package com.example.cardproject.repository

import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.cardproject.database.AppDatabase
import com.example.cardproject.database.dao.CardDao
import com.example.cardproject.database.dao.DeckDao
import com.example.cardproject.database.dao.NoteDao
import com.example.cardproject.database.dao.TagDao
import com.example.cardproject.database.repository.CardRepository
import com.example.cardproject.database.repository.DeckRepository
import com.example.cardproject.model.Deck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.runCatching
/**
 * Интеграционные тесты для DeckRepository.
 *
 * Что тестируется:
 * - Взаимодействие Repository с DeckDao (реальная БД)
 * - Бизнес-логика валидации данных
 * - Обработка ошибок (пустые имена, дубликаты)
 * - Корректность Flow-потоков данных
 */
@RunWith(RobolectricTestRunner::class)
class DeckRepositoryTest1 {

    private lateinit var database: AppDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao
    private lateinit var tagDao: TagDao
    private lateinit var noteDao: NoteDao

    private lateinit var cardRepository: CardRepository  // ← добавьте это
    private lateinit var repository: DeckRepository
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()

        deckDao = database.deckDao()
        cardDao = database.cardDao()
        tagDao = database.tagDao()
        noteDao = database.noteDao()
        cardRepository = CardRepository(cardDao)


        repository = DeckRepository(
            deckDao = deckDao,
            cardRepository = cardRepository,
            tagDao = tagDao
        )
    }

    @After
    fun teardown() {
        database.close()
    }



    // ============================================================
    // ТЕСТЫ СОЗДАНИЯ КОЛОДЫ (БИЗНЕС-ЛОГИКА)
    // ============================================================

    /**
     * Проверяет: создание колоды с корректными данными через Repository
     * Отличие от DAO: Repository добавляет валидацию и возвращает Result
     * Ожидает: Success с ID колоды
     */
    @Test
    fun createDeck_withValidData_shouldReturnSuccess(): Unit = runBlocking {
        val result = runCatching {
            repository.createDeck("Моя колода", "Описание")
        }

        result.onSuccess { deckId ->
            assertTrue(deckId > 0)
            println("Успех: id=$deckId")
        }.onFailure { error ->
            fail("Ошибка: ${error.message}")
        }
    }

    /**
     * Проверяет: создание колоды с пустым именем
     * Repository должен отклонить такой запрос без обращения к БД
     * Ожидает: Failure с сообщением об ошибке
     */
    @Test
    fun createDeck_withEmptyName_shouldReturnFailure(): Unit = runBlocking {
        val result = runCatching {
            repository.createDeck("", "Описание")
        }

        result.onSuccess { deckId ->
            fail("Создание с пустым именем не должно быть успешным")
        }.onFailure { error ->
            assertNotNull(error)
            assertTrue(
                error.message?.contains("пустым") == true ||
                        error.message?.contains("имя") == true
            )
            println("✅ createDeck_withEmptyName: Ошибка '${error.message}'")
        }
    }
    /**
     * Проверяет: создание колоды с именем только из пробелов
     * Ожидает: Failure (имя не может состоять только из пробелов)
     */
    @Test
    fun createDeck_withWhitespaceOnlyName_shouldReturnFailure(): Unit = runBlocking {
        val result = runCatching {
            repository.createDeck("   ", "Описание")
        }

        result.onSuccess { deckId ->
            fail("Имя из пробелов должно быть отклонено")
        }.onFailure { error ->
            assertNotNull(error)
            println("✅ createDeck_withWhitespaceOnlyName: Ошибка '${error.message}'")
        }
    }

    /**
     * Проверяет: создание колоды с именем, превышающим максимальную длину
     * Ожидает: Failure (имя слишком длинное)
     */
    @Test
    fun createDeck_withTooLongName_shouldReturnFailure(): Unit = runBlocking {
        val tooLongName = "A".repeat(1000)

        val result = runCatching {
            repository.createDeck(tooLongName, "Описание")
        }

        result.onSuccess { deckId ->
            fail("Слишком длинное имя должно быть отклонено")
        }.onFailure { error ->
            assertNotNull(error)
            assertTrue(
                error.message?.contains("длин") == true ||
                        error.message?.contains("превышает") == true
            )
            println("✅ createDeck_withTooLongName: Ошибка '${error.message}'")
        }
    }

    /**
     * Проверяет: создание колоды с очень длинным описанием (должно работать)
     */
    @Test
    fun createDeck_withVeryLongDescription_shouldWork(): Unit = runBlocking {
        val longDescription = "B".repeat(10000)

        val result = runCatching {
            repository.createDeck("Колода", longDescription)
        }

        result.onSuccess { deckId ->
            assertTrue(deckId > 0)
            val savedDeck = deckDao.getDeckById(deckId)
            assertEquals(longDescription, savedDeck?.description)
            println("✅ createDeck_withVeryLongDescription: Успех, id=$deckId, описание длиной ${longDescription.length}")
        }.onFailure { error ->
            fail("Длинное описание не должно вызывать ошибку: ${error.message}")
        }
    }


    // ============================================================
    // ТЕСТЫ ЧТЕНИЯ КОЛОД (БИЗНЕС-ЛОГИКА)
    // ============================================================

    /**
     * Проверяет: получение всех колод в виде Flow (реактивный поток)
     * Ожидает: Flow эмитит список колод при изменениях
     */
    @Test
    fun getAllDecks_shouldEmitListAsFlow() = runBlocking {
        // given: создаём две колоды
        deckDao.insertDeck(Deck(name = "Колода A"))
        deckDao.insertDeck(Deck(name = "Колода B"))

        // when: подписываемся на Flow
        val decks = repository.getAllDecks().first()

        // then
        assertEquals(2, decks.size)
        assertTrue(decks.any { it.name == "Колода A" })
        assertTrue(decks.any { it.name == "Колода B" })
    }

    /**
     * Проверяет: получение колоды по ID с бизнес-валидацией
     * Ожидает: Success с колодой или Failure с ошибкой "не найдена"
     */
    @Test
    fun getDeckById_withExistingId_shouldReturnSuccess(): Unit = runBlocking {
        // given
        val deckId = deckDao.insertDeck(Deck(name = "Тестовая колода"))

        val result = runCatching {
            repository.getDeckById(deckId)
        }

        result.onSuccess { deck ->
            assertNotNull(deck)
            assertEquals(deckId, deck?.id)
            assertEquals("Тестовая колода", deck?.name)
            println("✅ getDeckById_withExistingId: Найдена колода id=$deckId")
        }.onFailure { error ->
            fail("Существующая колода должна быть найдена: ${error.message}")
        }
    }

    /**
     * Проверяет: запрос несуществующей колоды
     * Ожидает: Failure с сообщением "Колода не найдена"
     */
    @Test
    fun getDeckById_withNonExistentId_shouldReturnFailure(): Unit = runBlocking {
        val result = runCatching {
            repository.getDeckById(999999L)
        }

        result.onSuccess { deck ->
            fail("Несуществующая колода не должна возвращаться")
        }.onFailure { error ->
            assertNotNull(error)
            assertTrue(
                error.message?.contains("не найдена") == true ||
                        error.message?.contains("не существует") == true
            )
            println("✅ getDeckById_withNonExistentId: Ошибка '${error.message}'")
        }
    }

    // ============================================================
    // ТЕСТЫ ОБНОВЛЕНИЯ КОЛОДЫ (БИЗНЕС-ЛОГИКА)
    // ============================================================

    /**
     * Проверяет: обновление существующей колоды
     * Ожидает: Success с true
     */
    @Test
    fun updateDeck_withValidData_shouldUpdateSuccessfully(): Unit = runBlocking {
        // given
        val deckId = deckDao.insertDeck(Deck(name = "Старое имя", description = "Старое описание"))

        // Получаем существующий Deck из БД
        val existingDeck = deckDao.getDeckById(deckId)
            ?: throw IllegalStateException("Колода не найдена после вставки")

        // Создаём обновлённую версию
        val updatedDeck = existingDeck.copy(
            name = "Новое имя",
            description = "Новое описание"
        )

        val result = runCatching {
            repository.updateDeck(updatedDeck, emptyList())
        }

        result.onSuccess { _ ->
            val deck = deckDao.getDeckById(deckId)
            assertEquals("Новое имя", deck?.name)
            assertEquals("Новое описание", deck?.description)
            println("✅ updateDeck_withValidData: Колода обновлена, id=$deckId")
        }.onFailure { error ->
            fail("Обновление не должно завершиться ошибкой: ${error.message}")
        }
    }

    /**
     * Проверяет: обновление несуществующей колоды
     * Ожидает: Failure с ошибкой
     */
    @Test
    fun updateDeck_withNonExistentId_shouldReturnFailure(): Unit = runBlocking {
        // Создаём несуществующий объект Deck с нереальным ID
        val nonExistentDeck = Deck(
            id = 999999L,
            name = "Несуществующая колода",
            description = "Описание"
        )

        val result = runCatching {
            repository.updateDeck(nonExistentDeck, emptyList())  // ← правильная сигнатура
        }

        result.onSuccess { _ ->
            fail("Обновление несуществующей колоды должно завершиться ошибкой")
        }.onFailure { error ->
            assertNotNull(error)
            println("✅ updateDeck_withNonExistentId: Ошибка '${error.message}'")
        }
    }

    // ============================================================
    // ТЕСТЫ УДАЛЕНИЯ КОЛОДЫ (БИЗНЕС-ЛОГИКА)
    // ============================================================

    /**
     * Проверяет: удаление существующей колоды
     * Ожидает: Success с true
     */
    @Test
    fun deleteDeck_withExistingId_shouldDeleteSuccessfully(): Unit = runBlocking {
        // given
        val deckId = deckDao.insertDeck(Deck(name = "Колода для удаления"))
        val deck = deckDao.getDeckById(deckId)!!

        // when
        val result = runCatching {
            repository.deleteDeck(deck)
        }

        // then
        result.onSuccess {
            val deletedDeck = deckDao.getDeckById(deckId)
            assertNull(deletedDeck)
            println("✅ deleteDeck_withExistingId: Колода удалена, id=$deckId")
        }.onFailure { error ->
            fail("Удаление не должно было завершиться ошибкой: ${error.message}")
        }
    }

    /**
     * Проверяет: удаление несуществующей колоды
     * Ожидает: Failure с ошибкой "не найдена"
     */
    @Test
    fun deleteDeck_withNonExistentId_shouldThrowException(): Unit = runBlocking {
        // given: создаём объект Deck с несуществующим ID
        val nonExistentDeck = Deck(id = 999999L, name = "Не существует")

        // when
        val result = runCatching {
            repository.deleteDeck(nonExistentDeck)
        }

        // then
        result.onSuccess {
            fail("Удаление несуществующей колоды должно выбросить исключение")
        }.onFailure { error ->
            assertNotNull(error)
            println("✅ deleteDeck_withNonExistentId: Ошибка '${error.message}'")
        }
    }

}
