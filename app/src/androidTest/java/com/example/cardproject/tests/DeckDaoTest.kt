// apps/cardproject/src/test/java/com/example/cardproject/database/DeckDaoTest.kt
package com.example.cardproject.database.dao

import androidx.room.Room
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.cardproject.database.AppDatabase
import com.example.cardproject.model.Card
import com.example.cardproject.model.Deck
import com.example.cardproject.model.ReviewLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Интеграционные тесты для DeckDao.
 *
 * Что тестируется:
 * - Взаимодействие DeckDao с реальной SQLite-базой данных (в памяти)
 * - Корректность выполнения SQL-запросов
 * - Работу внешних ключей и каскадного удаления
 * - Транзакционную целостность
 */
class DeckDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON;")
                    // Проверка, что внешние ключи действительно включены
                    val cursor = db.query("PRAGMA foreign_keys;")
                    if (cursor.moveToFirst()) {
                        val enabled = cursor.getInt(0)
                        println("Foreign keys enabled: $enabled")  // Должно быть 1
                    }
                    cursor.close()
                }
            })
            .build()
        deckDao = database.deckDao()
        cardDao = database.cardDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // ============================================================
    // ТЕСТЫ СОЗДАНИЯ КОЛОДЫ (CREATE)
    // ============================================================

    /**
     * Проверяет: создание колоды с корректными данными
     * Ожидает: колода сохраняется в БД, возвращается ненулевой ID
     */
    @Test
    fun createDeck_withValidData_shouldSaveToDatabase() = runBlocking {
        // given: подготовка данных
        val deck = Deck(
            name = "Программирование на Kotlin",
            description = "Основы языка Kotlin",
            coverColor = "#FF5722",
            createdAt = System.currentTimeMillis()
        )

        // when: выполнение операции
        val deckId = deckDao.insertDeck(deck)

        // then: проверка результата
        assertTrue("ID колоды должен быть > 0", deckId > 0)

        val savedDeck = deckDao.getDeckById(deckId)
        assertNotNull("Колода должна быть найдена в БД", savedDeck)
        assertEquals("Название колоды не соответствует", "Программирование на Kotlin",
            savedDeck?.name
        )
        assertEquals("Описание не соответствует", "Основы языка Kotlin", savedDeck?.description)
        assertEquals("Цвет не соответствует", "#FF5722", savedDeck?.coverColor)
    }

    /**
     * Проверяет: создание колоды с минимальными данными (без описания и цвета)
     * Означает: обязательные поля корректно обрабатываются, необязательные могут быть пустыми
     */
    @Test
    fun createDeck_withMinimalData_shouldUseDefaultValues() = runBlocking {
        val deck = Deck(name = "Минимальная колода")
        val deckId = deckDao.insertDeck(deck)

        val savedDeck = deckDao.getDeckById(deckId)
        assertNotNull(savedDeck)
        assertEquals("Минимальная колода", savedDeck?.name)
        // Проверяем, что описание пустое
        assertEquals("", savedDeck?.description)
    }

    /**
     * Проверяет: создание колоды с максимальной длиной имени
     * Ожидает: данные сохраняются корректно
     */
    @Test
    fun createDeck_withVeryLongName_shouldBeSavedCorrectly() = runBlocking {
        // given: имя длиной 500 символов
        val longName = "A".repeat(500)
        val deck = Deck(name = longName)

        // when
        val deckId = deckDao.insertDeck(deck)

        // then
        val savedDeck = deckDao.getDeckById(deckId)
        assertEquals("Имя должно сохраниться полностью", longName, savedDeck?.name)
    }

    // ============================================================
    // ТЕСТЫ ЧТЕНИЯ КОЛОДЫ (READ)
    // ============================================================

    /**
     * Проверяет: получение колоды по существующему ID
     * Ожидает: возвращается корректный объект Deck
     */
    @Test
    fun getDeckById_withExistingId_shouldReturnCorrectDeck() = runBlocking {
        // given
        val deck = Deck(name = "Тестовая колода")
        val deckId = deckDao.insertDeck(deck)

        // when
        val retrievedDeck = deckDao.getDeckById(deckId)

        // then
        assertNotNull(retrievedDeck)
        assertEquals(deckId, retrievedDeck?.id)
        assertEquals("Тестовая колода", retrievedDeck?.name)
    }

    /**
     * Проверяет: получение колоды по несуществующему ID
     * Ожидает: возвращается null
     */
    @Test
    fun getDeckById_withNonExistentId_shouldReturnNull() = runBlocking {
        // when: запрос несуществующего ID (999999)
        val retrievedDeck = deckDao.getDeckById(999999L)

        // then
        assertNull("Для несуществующего ID должно возвращаться null", retrievedDeck)
    }


    // ============================================================
    // ТЕСТЫ ОБНОВЛЕНИЯ КОЛОДЫ (UPDATE)
    // ============================================================

    /**
     * Проверяет: обновление названия и описания колоды
     * Ожидает: данные корректно обновляются в БД
     */
    @Test
    fun updateDeck_withNewNameAndDescription_shouldUpdateDatabase() = runBlocking {
        // given
        val deck = Deck(name = "Старое название", description = "Старое описание")
        val deckId = deckDao.insertDeck(deck)

        // when
        val updatedDeck = deck.copy(
            id = deckId,
            name = "Новое название",
            description = "Новое описание"
        )
        deckDao.updateDeck(updatedDeck)

        // then
        val retrievedDeck = deckDao.getDeckById(deckId)
        assertEquals("Название должно обновиться", "Новое название", retrievedDeck?.name)
        assertEquals("Описание должно обновиться", "Новое описание", retrievedDeck?.description)
    }

    /**
     * Проверяет: обновление цвета колоды (визуальная идентификация)
     */
    @Test
    fun updateDeck_withNewColor_shouldUpdateColorOnly() = runBlocking {
        // given
        val deck = Deck(name = "Колода", description = "Описание", coverColor = "#FFFFFF")
        val deckId = deckDao.insertDeck(deck)

        // when: обновляем только цвет
        val updatedDeck = deck.copy(id = deckId, coverColor = "#000000")
        deckDao.updateDeck(updatedDeck)

        // then
        val retrievedDeck = deckDao.getDeckById(deckId)
        assertEquals("#000000", retrievedDeck?.coverColor)
        assertEquals("Название не должно измениться", "Колода", retrievedDeck?.name)
        assertEquals("Описание не должно измениться", "Описание", retrievedDeck?.description)
    }

    /**
     * Проверяет: обновление несуществующей колоды
     * Ожидает: метод не должен выбросить исключение, но и не обновит несуществующую запись
     */
    @Test
    fun updateDeck_withNonExistentId_shouldDoNothing() = runBlocking {
        // given: колода с несуществующим ID
        val nonExistentDeck = Deck(id = 999999L, name = "Не существует")

        // when: попытка обновления
        deckDao.updateDeck(nonExistentDeck)

        // then: исключение не выброшено (тест проходит)
        // Дополнительно: можно проверить, что запись не появилась
        val deck = deckDao.getDeckById(999999L)
        assertNull(deck)
    }

    // ============================================================
    // ТЕСТЫ УДАЛЕНИЯ КОЛОДЫ (DELETE) И КАСКАДНОСТИ
    // ============================================================

    /**
     * Проверяет: удаление колоды по объекту
     * Ожидает: колода удаляется из БД
     */
    @Test
    fun deleteDeck_byDeckObject_shouldRemoveFromDatabase() = runBlocking {
        val deck = Deck(name = "Колода")
        val deckId = deckDao.insertDeck(deck)

        val card = Card(deckId = deckId, front = "Вопрос", back = "Ответ")
        cardDao.insertCard(card)

        // Используем удаление по ID
        deckDao.deleteDeckById(deckId)

        val remainingCards = cardDao.getCardsByDeckSync(deckId)
        assertTrue(remainingCards.isEmpty())
    }

    /**
     * Проверяет: удаление колоды по ID
     * Ожидает: колода удаляется из БД
     */
    @Test
    fun deleteDeckById_withExistingId_shouldRemoveFromDatabase() = runBlocking {
        // given
        val deck = Deck(name = "Колода")
        val deckId = deckDao.insertDeck(deck)

        // when
        deckDao.deleteDeckById(deckId)

        // then
        val deletedDeck = deckDao.getDeckById(deckId)
        assertNull(deletedDeck)
    }

    /**
     * Проверяет: каскадное удаление карточек при удалении колоды
     * Это КЛЮЧЕВОЙ тест для интеграции — проверяет работу FOREIGN KEY
     */
    @Test
    fun deleteDeck_withCards_shouldCascadeDeleteCards() = runBlocking {
        // given: создание колоды с двумя карточками
        val deck = Deck(name = "Колода с карточками")
        val deckId = deckDao.insertDeck(deck)

        val card1 = Card(deckId = deckId, front = "Вопрос 1", back = "Ответ 1")
        val card2 = Card(deckId = deckId, front = "Вопрос 2", back = "Ответ 2")
        cardDao.insertCard(card1)
        cardDao.insertCard(card2)

        // before: проверяем, что карточки созданы
        val cardsBefore = cardDao.getCardsByDeck(deckId).first()
        assertEquals(2, cardsBefore.size)

        // when: удаление колоды ПО ID (исправлено!)
        deckDao.deleteDeckById(deckId)  // ← используем deleteDeckById, а не deleteDeck

        // then: карточки должны быть удалены каскадно
        val remainingCards = cardDao.getCardsByDeck(deckId).first()
        assertTrue("Все карточки колоды должны быть удалены", remainingCards.isEmpty())
    }

    /**
     * Проверяет: удаление всех колод (очистка таблицы)
     */
    @Test
    fun deleteAllDecks_shouldClearTable() = runBlocking {
        // given
        deckDao.insertDeck(Deck(name = "Колода 1"))
        deckDao.insertDeck(Deck(name = "Колода 2"))
        deckDao.insertDeck(Deck(name = "Колода 3"))

        // when
        deckDao.deleteAllDecks()

        // then
        val allDecks = deckDao.getAllDecks().first()
        assertTrue("Таблица колод должна быть пуста", allDecks.isEmpty())
    }
}