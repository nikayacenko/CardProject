//// apps/cardproject/src/test/java/com/example/cardproject/viewmodel/SessionViewModelTest.kt
//package com.example.cardproject.viewmodel
//
//import androidx.arch.core.executor.testing.InstantTaskExecutorRule
//import com.example.cardproject.database.repository.SessionStatsRepository
//import com.example.cardproject.model.Card
//import com.example.cardproject.ui.stats.SessionStatsViewModel
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.flow.flowOf
//import kotlinx.coroutines.test.StandardTestDispatcher
//import kotlinx.coroutines.test.resetMain
//import kotlinx.coroutines.test.runTest
//import kotlinx.coroutines.test.setMain
//import org.junit.After
//import org.junit.Assert.*
//import org.junit.Before
//import org.junit.Rule
//import org.junit.Test
//import org.mockito.Mock
//import org.mockito.Mockito.*
//import org.mockito.MockitoAnnotations
//import org.mockito.kotlin.any
//import org.mockito.kotlin.eq
//import org.mockito.kotlin.whenever
//
///**
// * Интеграционные тесты для SessionViewModel.
// *
// * Что тестируется:
// * - Инициализация сессии и загрузка карточек
// * - Показ вопроса и ответа
// * - Оценка ответа (правильный/неправильный)
// * - Переход к следующей карточке
// * - Завершение сессии и подсчёт статистики
// * - Повтор неправильных карточек
// */
//@OptIn(ExperimentalCoroutinesApi::class)
//class SessionViewModelTest {
//
//    @get:Rule
//    val instantExecutorRule = InstantTaskExecutorRule()
//
//    @Mock
//    private lateinit var repository: SessionStatsRepository
//
//    private lateinit var viewModel: SessionStatsViewModel
//    private val testDispatcher = StandardTestDispatcher()
//
//    @Before
//    fun setup() {
//        MockitoAnnotations.openMocks(this)
//        Dispatchers.setMain(testDispatcher)
//        viewModel = SessionStatsViewModel(repository)
//    }
//
//    @After
//    fun tearDown() {
//        Dispatchers.resetMain()
//    }
//
//    // ============================================================
//    // ТЕСТЫ ИНИЦИАЛИЗАЦИИ СЕССИИ
//    // ============================================================
//
//    /**
//     * Проверяет: успешную инициализацию сессии с карточками.
//     * Ожидает: сессия создана, первая карточка загружена, состояние корректно.
//     */
//    @Test
//    fun initSession_withCards_shouldInitializeSuccessfully() = runTest {
//        // given
//        val mockCards = listOf(
//            Card(id = 1, question = "Вопрос 1", answer = "Ответ 1"),
//            Card(id = 2, question = "Вопрос 2", answer = "Ответ 2")
//        )
//        whenever(repository.getCardsToReview(eq(1L), any()))
//            .thenReturn(flowOf(mockCards))
//
//        // when
//        viewModel.initSession(1L)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // then
//        val state = viewModel.sessionState.value
//        assertNotNull(state)
//        assertEquals(2, state?.totalCards)
//        assertEquals(0, state?.currentCardIndex)
//        assertEquals(2, state?.remainingCards)
//
//        val currentCard = viewModel.getCurrentCard()
//        assertEquals("Вопрос 1", currentCard?.question)
//        assertFalse(viewModel.isSessionComplete.value)
//    }
//
//    /**
//     * Проверяет: инициализацию сессии без карточек.
//     * Ожидает: сообщение об ошибке, сессия не активна.
//     */
//    @Test
//    fun initSession_withNoCards_shouldShowError() = runTest {
//        // given
//        whenever(repository.getCardsToReview(eq(1L), any()))
//            .thenReturn(flowOf(emptyList()))
//
//        // when
//        viewModel.initSession(1L)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // then
//        val errorMessage = viewModel.errorMessage.value
//        assertNotNull(errorMessage)
//        assertTrue(errorMessage?.contains("Нет карточек") == true)
//
//        val isSessionActive = viewModel.isSessionActive.value
//        assertFalse(isSessionActive)
//    }
//
//    // ============================================================
//    // ТЕСТЫ ПОКАЗА ОТВЕТА
//    // ============================================================
//
//    /**
//     * Проверяет: показ ответа на карточку.
//     * Ожидает: поле ответа становится видимым, отображается правильный ответ.
//     */
//    @Test
//    fun showAnswer_shouldRevealAnswerAndMakeVisible() = runTest {
//        // given
//        val mockCards = listOf(Card(id = 1, question = "Что такое Kotlin?", answer = "Язык программирования"))
//        whenever(repository.getCardsToReview(any(), any()))
//            .thenReturn(flowOf(mockCards))
//
//        viewModel.initSession(1L)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // when
//        viewModel.showAnswer()
//
//        // then
//        val isAnswerVisible = viewModel.isAnswerVisible.value
//        assertTrue(isAnswerVisible)
//
//        val currentAnswer = viewModel.currentAnswer.value
//        assertEquals("Язык программирования", currentAnswer)
//    }
//
//    // ============================================================
//    // ТЕСТЫ ОЦЕНКИ ОТВЕТА
//    // ============================================================
//
//    /**
//     * Проверяет: оценку правильного ответа (качество 5).
//     * Ожидает: счётчик правильных ответов растёт, интервал увеличивается.
//     */
//    @Test
//    fun evaluateAnswer_withCorrectAnswer_shouldIncreaseCorrectCounter() = runTest {
//        // given
//        val mockCards = listOf(Card(id = 1, question = "Вопрос", answer = "Ответ", interval = 1.0))
//        whenever(repository.getCardsToReview(any(), any()))
//            .thenReturn(flowOf(mockCards))
//        whenever(repository.saveReviewResult(eq(1L), eq(5), any(), any()))
//            .thenReturn(Result.success(3.0))
//
//        viewModel.initSession(1L)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // when
//        viewModel.evaluateAnswer(5, 3000)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // then
//        val state = viewModel.sessionState.value
//        assertEquals(1, state?.correctAnswers)
//        assertEquals(0, state?.wrongAnswers)
//
//        verify(repository).saveReviewResult(eq(1L), eq(5), any(), any())
//    }
//
//    /**
//     * Проверяет: оценку неправильного ответа (качество 0).
//     * Ожидает: счётчик неправильных ответов растёт, вопрос добавляется в конец очереди.
//     */
//    @Test
//    fun evaluateAnswer_withWrongAnswer_shouldIncreaseWrongCounterAndKeepCard() = runTest {
//        // given
//        val mockCards = listOf(
//            Card(id = 1, question = "Вопрос 1"),
//            Card(id = 2, question = "Вопрос 2")
//        )
//        whenever(repository.getCardsToReview(any(), any()))
//            .thenReturn(flowOf(mockCards))
//        whenever(repository.saveReviewResult(eq(1L), eq(0), any(), any()))
//            .thenReturn(Result.success(0.5))
//
//        viewModel.initSession(1L)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // when
//        viewModel.evaluateAnswer(0,