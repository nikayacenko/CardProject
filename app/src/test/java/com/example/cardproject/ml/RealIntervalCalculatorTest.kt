package com.example.cardproject.ml

import com.example.cardproject.model.AIContext
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.MLPrediction
import com.example.cardproject.model.ReviewLog
import com.example.cardproject.database.repository.ReviewLogRepository
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.*
import org.mockito.MockitoAnnotations

class MLSpacedRepetitionCalculatorTest {

    @Mock
    private lateinit var mockTfliteModel: TensorFlowLiteModel

    @Mock
    private lateinit var mockReviewLogRepository: ReviewLogRepository

    private lateinit var calculator: MLSpacedRepetitionCalculator

    @Before
    fun setup() = runBlocking {
        MockitoAnnotations.openMocks(this@MLSpacedRepetitionCalculatorTest)

        whenever(mockTfliteModel.isModelReady).thenReturn(
            MutableStateFlow(true)
        )

        doReturn(0.5f)
            .`when`(mockReviewLogRepository)
            .getCorrectRateForCard(anyLong())

        calculator = MLSpacedRepetitionCalculator(
            tfliteModel = mockTfliteModel,
            reviewLogRepository = mockReviewLogRepository
        )
    }

    @Test
    fun intervalShouldGrow() = runBlocking {
        var card = createTestCard(interval = 1.0)
        println("------ТЕСТ 1------")
        repeat(5) { step ->
            val prediction = MLPrediction(
                forgettingProbability = 0.2f,
                optimalIntervalDays = 1.3f + step,
                confidence = 0.9f,
                needsMoreData = false
            )

            `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)

            val updated = calculator.calculateNextReviewWithoutSaving(
                card,
                AIContext.create(learningMode=LearningMode.LONG_TERM),
                LearningMode.LONG_TERM,
                1,
                3000
            )
            println(
                "Step ${step + 1}: old=${String.format("%.3f", card.interval)} → " +
                        "new=${String.format("%.3f", updated.interval)}  " +
                        "(predicted=${prediction.optimalIntervalDays})"
            )
            assertTrue(updated.interval > card.interval)
            card = updated
        }
    }

    @Test
    fun shortTermShouldBeSmallerThanLongTerm() = runBlocking {
        val prediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 2.0f,
            confidence = 0.9f,
            needsMoreData = false
        )

        `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)
        val longTermCard = createTestCard(interval = 1.0)
        val shortTermCard = createTestCard(interval = 0.042)
        val longTerm = calculator.calculateNextReviewWithoutSaving(
            longTermCard,
            AIContext.create(learningMode=LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            1,
            3000
        )

        val shortTerm = calculator.calculateNextReviewWithoutSaving(
            shortTermCard,
            AIContext.create(learningMode=LearningMode.LONG_TERM),
            LearningMode.SHORT_TERM,
            1,
            3000
        )
        println("------ТЕСТ 2------")

        println("Режим LONG_TERM:  исходный интервал = ${String.format("%.4f", longTermCard.interval)} дн.," +
                " новый = ${String.format("%.4f", longTerm.interval)} дн.")
        println("Режим SHORT_TERM: исходный интервал = ${String.format("%.4f", shortTermCard.interval)} дн.," +
                " новый = ${String.format("%.4f", shortTerm.interval)} дн.")

        println("Проверка: интервал SHORT_TERM < LONG_TERM ? " +
                "${shortTerm.interval} < ${longTerm.interval}")
        assertTrue(shortTerm.interval < longTerm.interval)
    }

    @Test
    fun incorrectAnswerShouldReduceInterval() = runBlocking {
        val card = createTestCard(interval = 5.0)

        val updated = calculator.calculateNextReviewWithoutSaving(
            card,
            AIContext.create(learningMode=LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            0,
            3000
        )
        println("------ТЕСТ 3------")

        println("Исходный интервал: ${String.format("%.4f", card.interval)} дней")
        println("Интервал после ошибки: ${String.format("%.4f", updated.interval)} дней")
        assertTrue(updated.interval < card.interval)
    }

    @Test
    fun modelShouldBeCalled() {
        runBlocking {
            val prediction = MLPrediction(0.3f, 1.5f, 0.8f, false)
            whenever(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)
            println("------ТЕСТ 4------")
            println("Вызов расчёта интервала...")
            calculator.calculateNextReviewWithoutSaving(
                createTestCard(),
                AIContext.create(),
                LearningMode.LONG_TERM,
                1,
                3000
            )
            println("Проверяем, что метод predict() был вызван...")
            verify(mockTfliteModel, atLeastOnce()).predict(any())
            println("✓ РЕЗУЛЬТАТ: modelShouldBeCalled — УСПЕШНО ПРОЙДЁН")
        }
    }

    // 5. Падение интервала при ошибке в SHORT_TERM режиме
    @Test
    fun shortTermIncorrectAnswerShouldResetToBaseInterval() = runBlocking {
        val card = createTestCard(
            interval = 2.0,  // 2 дня (но для SHORT_TERM это много)
            consecutiveCorrect = 5,
            reviewStage = 5
        )
        println("------ТЕСТ 5------")
        println("Исходный интервал карточки: ${card.interval} дней")
        println("Режим: SHORT_TERM, ответ: неправильный")
        val updated = calculator.calculateNextReviewWithoutSaving(
            card,
            AIContext.create(learningMode = LearningMode.SHORT_TERM),
            LearningMode.SHORT_TERM,
            0,  // Неправильный ответ
            5000
        )
        println("Интервал после ошибки: ${updated.interval} дней")
        println("В часах: ${updated.interval * 24} ч")
        // SHORT_TERM база 0.042 дня (1 час)
        assertTrue(updated.interval <= 1.0)  // Должен быть <= 1 дня
        assertTrue(updated.interval >= 0.042) // Но не меньше 1 часа
        println("SHORT_TERM после ошибки: ${updated.interval * 24} часов")
    }

    // 6. Новая карточка (reviewStage=0) должна иметь минимальный интервал
    @Test
    fun newCardShouldHaveBaseInterval() = runBlocking {
        val newCard = createTestCard(
            interval = 0.0,
            consecutiveCorrect = 0,
            reviewStage = 0
        )
        println("------ТЕСТ 6------")
        println("Создана новая карточка (reviewStage = 0).")
        println("Ожидаем базовые интервалы: LONG_TERM = 1 день, SHORT_TERM = 1 час.")
        val prediction = MLPrediction(
            forgettingProbability = 0.5f,
            optimalIntervalDays = 1.0f,
            confidence = 0.5f,
            needsMoreData = true
        )
        `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)

        val updatedLong = calculator.calculateNextReviewWithoutSaving(
            newCard,
            AIContext.create(learningMode = LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            1,
            3000
        )

        val updatedShort = calculator.calculateNextReviewWithoutSaving(
            newCard,
            AIContext.create(learningMode = LearningMode.SHORT_TERM),
            LearningMode.SHORT_TERM,
            1,
            3000
        )
        println("LONG_TERM интервал:  ${updatedLong.interval} дней")
        println("SHORT_TERM интервал: ${updatedShort.interval} дней")
        println("SHORT_TERM в часах:  ${updatedShort.interval * 24} ч")
        assertEquals(1.0, updatedLong.interval, 0.1)  // LONG_TERM: 1 день
        assertEquals(0.042, updatedShort.interval, 0.01)  // SHORT_TERM: 1 час
    }

    // 7. Высокая уверенность модели → больший рост интервала
    @Test
    fun highConfidenceShouldLeadToLargerIntervalGrowth() = runBlocking {
        val card = createTestCard(interval = 2.0, consecutiveCorrect = 2)

        val lowConfidencePrediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 1.8f,
            confidence = 0.3f,
            needsMoreData = true
        )

        val highConfidencePrediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 1.8f,
            confidence = 0.9f,
            needsMoreData = false
        )

        `when`(mockTfliteModel.predict(argThat { true }))
            .thenReturn(lowConfidencePrediction)
            .thenReturn(highConfidencePrediction)
        println("------ТЕСТ 7------")
        println("Шаг 1: низкая уверенность модели (${lowConfidencePrediction.confidence})")
        val lowConfCard = calculator.calculateNextReviewWithoutSaving(
            card,
            AIContext.create(learningMode = LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            1,
            3000
        )
        println("Интервал при низкой уверенности: ${lowConfCard.interval} дней")
        println("Шаг 2: высокая уверенность модели (${highConfidencePrediction.confidence})")
        val highConfCard = calculator.calculateNextReviewWithoutSaving(
            card,
            AIContext.create(learningMode = LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            1,
            3000
        )
        println("Интервал при высокой уверенности: ${highConfCard.interval} дней")
        println("Низкая уверенность: ${lowConfCard.interval} дней")
        println("Высокая уверенность: ${highConfCard.interval} дней")

        assertTrue(highConfCard.interval >= lowConfCard.interval)
    }

   // 8. Сложные карточки (PROOF) должны иметь меньшие интервалы
    @Test
    fun proofCardsShouldHaveSmallerIntervalsThanFactCards() = runBlocking {
        val factCard = createTestCard(
            interval = 2.0,
            questionType = "FACT",
            difficultyScore = 0.3f
        )

        val proofCard = createTestCard(
            interval = 2.0,
            questionType = "PROOF",
            difficultyScore = 0.9f
        )
        println("------ТЕСТ 8------")
        println("FACT карточка: сложность = ${factCard.difficultyScore}, тип = FACT")
        println("PROOF карточка: сложность = ${proofCard.difficultyScore}, тип = PROOF")
        println("Ожидаем: PROOF → меньший интервал")
        val prediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 1.8f,
            confidence = 0.9f,
            needsMoreData = false
        )
        `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)

        val factResult = calculator.calculateNextReviewWithoutSaving(
            factCard,
            AIContext.create(learningMode = LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            1,
            3000
        )

        val proofResult = calculator.calculateNextReviewWithoutSaving(
            proofCard,
            AIContext.create(learningMode = LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            1,
            3000
        )

        println("FACT карточка: ${factResult.interval} дней")
        println("PROOF карточка: ${proofResult.interval} дней")

        println("Результат FACT:  ${factResult.interval} дней")
        println("Результат PROOF: ${ proofResult.interval} дней")
        println("Проверка: PROOF ≤ FACT ?  ${proofResult.interval} ≤ ${factResult.interval}")

        assertTrue(proofResult.interval <= factResult.interval)
    }

    // 9. Ограничение максимального интервала
    @Test
    fun intervalShouldNotExceedMaximum() = runBlocking {
        val expertCard = createTestCard(
            interval = 350.0,  // Почти максимум
            consecutiveCorrect = 50,
            reviewStage = 20
        )
        println("------ТЕСТ 9------")
        println("Исходный интервал (LONG_TERM): ${expertCard.interval} дней")
        println("Ожидаем ограничение: максимум 365 дней")
        val prediction = MLPrediction(
            forgettingProbability = 0.05f,  // Очень низкая вероятность забывания
            optimalIntervalDays = 2.5f,     // Большой множитель
            confidence = 0.99f,
            needsMoreData = false
        )
        `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)

        val longTermCard = calculator.calculateNextReviewWithoutSaving(
            expertCard,
            AIContext.create(learningMode = LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            1,
            3000
        )
        println("LONG_TERM результат: ${longTermCard.interval} дней")

        val shortTermCard = expertCard.copy(interval = 6.0)

        println("Исходный интервал (SHORT_TERM): ${shortTermCard.interval} дней")
        println("Ожидаем ограничение: максимум 7 дней")
        val shortTermResult = calculator.calculateNextReviewWithoutSaving(
            shortTermCard,
            AIContext.create(learningMode = LearningMode.SHORT_TERM),
            LearningMode.SHORT_TERM,
            1,
            3000
        )
        println("SHORT_TERM результат: ${shortTermResult.interval} дней")
        println("SHORT_TERM в часах: ${shortTermResult.interval * 24} ч")
        assertTrue(longTermCard.interval <= 365.0)   // LONG_TERM максимум 365 дней
        assertTrue(shortTermCard.interval <= 7.0)    // SHORT_TERM максимум 7 дней
    }

    // 10. Прогрессивный рост интервалов (экспоненциальный)
    @Test
    fun intervalsShouldGrowExponentially() = runBlocking {
        var card = createTestCard(interval = 1.0)
        val intervals = mutableListOf<Double>()
        val prediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 1.6f,  // Постоянный множитель
            confidence = 0.9f,
            needsMoreData = false
        )
        `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)
        println("------ТЕСТ 10------")
        println("Начальный интервал: ${card.interval} дней")
        println("Ожидаем экспоненциальный рост интервалов при одинаковом прогнозе модели.")
        repeat(6) { step ->
            val updated = calculator.calculateNextReviewWithoutSaving(
                card,
                AIContext.create(learningMode = LearningMode.LONG_TERM),
                LearningMode.LONG_TERM,
                1,
                3000
            )
            intervals.add(updated.interval)
            println(
                "Шаг ${step + 1}: " +
                        "старый = ${card.interval} → " +
                                "новый = ${updated.interval} дней"
                                    )
            card = updated
        }

        println("Интервалы: ${intervals.joinToString(" → ") { String.format("%.2f", it) }}")

        // Каждый следующий интервал должен быть больше предыдущего
        for (i in 1 until intervals.size) {
            assertTrue(intervals[i] > intervals[i - 1])
        }

        assertTrue(intervals[5] > intervals[2] * 2)
    }

    // 11. ReviewLog должен создаваться с правильными параметрами
    @Test
    fun reviewLogShouldBeCreatedWithCorrectParameters() = runBlocking {
        val card = createTestCard()
        val context = AIContext.create(
            learningMode = LearningMode.LONG_TERM,
            sessionStartTime = System.currentTimeMillis()
        )

        val argumentCaptor = argumentCaptor<ReviewLog>()

        whenever(mockTfliteModel.predict(argumentCaptor.capture())).thenReturn(
            MLPrediction(0.3f, 1.5f, 0.8f, false)
        )
        println("------ТЕСТ 11------")
        println("Выполняем расчёт следующего интервала...")
        calculator.calculateNextReviewWithoutSaving(
            card,
            context,
            LearningMode.LONG_TERM,
            1,  // quality = 1 (правильно)
            5000  // responseTimeMs = 5 секунд
        )

        val capturedLog = argumentCaptor.firstValue

        println("Проверяем созданный ReviewLog:")
        println(" - cardId:          ${capturedLog.cardId}")
        println(" - quality:         ${capturedLog.quality}")
        println(" - responseTimeMs:  ${capturedLog.responseTimeMs}")
        println(" - timestamp:       ${capturedLog.timestamp}")

        assertEquals(1, capturedLog.quality)
        assertEquals(5000L, capturedLog.responseTimeMs)
        assertEquals(card.id, capturedLog.cardId)
        println("✅ ReviewLog создан корректно: quality=${capturedLog.quality}, responseTime=${capturedLog.responseTimeMs}ms")
    }

    // 12. Усталость пользователя должна уменьшать интервал
    @Test
    fun highFatigueShouldReduceInterval() = runBlocking {
        val card = createTestCard(interval = 3.0, consecutiveCorrect = 3)

        val prediction = MLPrediction(
            forgettingProbability = 0.2f,
            optimalIntervalDays = 1.8f,
            confidence = 0.9f,
            needsMoreData = false
        )
        `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)

        // Создаем контекст с низкой усталостью
        val lowFatigueContext = AIContext.create(learningMode = LearningMode.LONG_TERM)
            .updateWithReview(true, 3000)
            .updateWithReview(true, 3100)

        // Создаем контекст с высокой усталостью (много повторений)
        var highFatigueContext = AIContext.create(learningMode = LearningMode.LONG_TERM)
        repeat(150) {
                index ->
            val responseTime = 3000L + (index * 100L)
            highFatigueContext = highFatigueContext.updateWithReview(
                wasCorrect = index % 3 == 0,
                responseTimeMs = responseTime)
        }

        val lowFatigueResult = calculator.calculateNextReviewWithoutSaving(
            card,
            lowFatigueContext,
            LearningMode.LONG_TERM,
            1,
            3000
        )

        val highFatigueResult = calculator.calculateNextReviewWithoutSaving(
            card,
            highFatigueContext,
            LearningMode.LONG_TERM,
            1,
            5000
        )
        println("------ТЕСТ 12------")
        println("Низкая усталость (${lowFatigueContext.userFatigueLevel}): ${lowFatigueResult.interval} дней")
        println("Высокая усталость (${highFatigueContext.userFatigueLevel}): ${highFatigueResult.interval} дней")

        assertTrue(highFatigueResult.interval <= lowFatigueResult.interval)
    }

    // 13. Модель не готова → используем fallback
    @Test
    fun whenModelNotReady_shouldUseFallback() = runBlocking {
        whenever(mockTfliteModel.isModelReady).thenReturn(MutableStateFlow(false))

        val card = createTestCard(interval = 2.0, consecutiveCorrect = 2)

        val result = calculator.calculateNextReviewWithoutSaving(
            card,
            AIContext.create(learningMode = LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            1,
            3000
        )

        // Fallback интервал должен быть предсказуемым
        assertTrue(result.interval >= 1.0)
        assertTrue(result.interval <= 365.0)
        println("------ТЕСТ 13------")
        println("Модель не готова, fallback интервал: ${result.interval} дней")
    }

    // 14. Последовательность правильных ответов увеличивает easeFactor
    @Test
    fun consecutiveCorrectAnswersShouldIncreaseEaseFactor() = runBlocking {
        var card = createTestCard(easeFactor = 2.5)
        val context = AIContext.create(learningMode = LearningMode.LONG_TERM)

        val prediction = MLPrediction(
            forgettingProbability = 0.1f,  // Низкая вероятность забывания
            optimalIntervalDays = 2.0f,
            confidence = 0.9f,
            needsMoreData = false
        )
        `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)

        val easeFactors = mutableListOf<Double>()
        println("------ТЕСТ 14------")

        repeat(4) { step ->
            val updated = calculator.calculateNextReviewWithoutSaving(
                card,
                context,
                LearningMode.LONG_TERM,
                1,
                3000
            )
            easeFactors.add(updated.easeFactor)
            card = updated
            println("Шаг ${step + 1}: easeFactor = ${String.format("%.2f", updated.easeFactor)}")
        }

        // easeFactor должен расти или оставаться стабильным
        assertTrue(easeFactors.last() >= easeFactors.first())
    }

    // 15. Граничный случай: нулевое время ответа
    @Test
    fun zeroResponseTimeShouldNotCrash() = runBlocking {
        val card = createTestCard()

        val prediction = MLPrediction(0.3f, 1.5f, 0.8f, false)
        `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)

        val result = calculator.calculateNextReviewWithoutSaving(
            card,
            AIContext.create(learningMode = LearningMode.LONG_TERM),
            LearningMode.LONG_TERM,
            1,
            0  // Нулевое время ответа
        )

        assertNotNull(result)
        assertTrue(result.interval > 0)
        println("------ТЕСТ 15------")
        println("Нулевое время ответа: интервал = ${result.interval} дней")
    }
    //16 тест на влияние усталости
    @Test
    fun highFatigueShouldReduceIntervalComparedToLowFatigue() = runBlocking {
        val card = createTestCard(interval = 4.0, consecutiveCorrect = 2)
        val prediction = MLPrediction(0.2f, 2.0f, 0.9f, false)
        whenever(mockTfliteModel.predict(any())).thenReturn(prediction)

        // Контекст 1: Свежий пользователь (начало сессии)
        val freshContext = AIContext.create(learningMode = LearningMode.LONG_TERM)

        // Контекст 2: Уставший пользователь (просмотрел 100 карточек)
        var tiredContext = AIContext.create(learningMode = LearningMode.LONG_TERM)
        repeat(100) { tiredContext = tiredContext.updateWithReview(true, 5000) }

        val freshResult = calculator.calculateNextReviewWithoutSaving(
            card, freshContext, LearningMode.LONG_TERM, 1, 3000
        )
        val tiredResult = calculator.calculateNextReviewWithoutSaving(
            card, tiredContext, LearningMode.LONG_TERM, 1, 3000
        )

        println("------ТЕСТ 16------")

        println("Интервал (свежий): ${freshResult.interval} дн.")
        println("Интервал (уставший): ${tiredResult.interval} дн.")

        // Проверяем, что усталость подрезала интервал
        assertTrue("Уставшему пользователю должен быть назначен меньший интервал",
            tiredResult.interval < freshResult.interval)
    }

    //тест на скорость ответа
    @Test
    fun slowResponseShouldReduceIntervalGrowth() = runBlocking {
        val card = createTestCard(interval = 2.0)
        val prediction = MLPrediction(0.2f, 1.8f, 0.9f, false)
        whenever(mockTfliteModel.predict(any())).thenReturn(prediction)

        // Быстрый ответ (2 сек)
        val fastResult = calculator.calculateNextReviewWithoutSaving(
            card, AIContext.create(), LearningMode.LONG_TERM, 1, 2000
        )

        // Медленный ответ (25 сек)
        val slowResult = calculator.calculateNextReviewWithoutSaving(
            card, AIContext.create(), LearningMode.LONG_TERM, 1, 25000
        )

        println("------ТЕСТ 17------")

        println("Интервал (быстрый ответ): ${fastResult.interval}")
        println("Интервал (медленный ответ): ${slowResult.interval}")

        assertTrue(slowResult.interval <= fastResult.interval)
    }
    // контекст пользователя
    @Test
    fun morningContextShouldGiveBetterIntervalThanEvening() = runBlocking {
        val card = createTestCard(interval = 0.0, reviewStage = 0) // Новая карточка
        val prediction = MLPrediction(0.1f, 2.0f, 0.9f, false)
        whenever(mockTfliteModel.predict(any())).thenAnswer { invocation ->
            val log: ReviewLog = invocation.getArgument(0)
            val mlPredictionDays = if (log.hourOfDay in 6..11) 2.5f else 1.8f

            MLPrediction(
                forgettingProbability = 0.1f,
                optimalIntervalDays = mlPredictionDays,
                confidence = 0.9f,
                needsMoreData = false
            )
        }
        // Утренний контекст (8 утра, нулевая усталость)
        val morningContext = AIContext.create().copy(
            currentHour = 8,
            userFatigueLevel = 0.05f
        )

        // Вечерний контекст (23 часа, высокая усталость)
        val eveningContext = AIContext.create().copy(
            currentHour = 23,
            userFatigueLevel = 0.7f
        )

        val morningResult = calculator.calculateNextReviewWithoutSaving(
            card, morningContext, LearningMode.LONG_TERM, 1, 3000
        )

        val eveningResult = calculator.calculateNextReviewWithoutSaving(
            card, eveningContext, LearningMode.LONG_TERM, 1, 3000
        )
        println("------ТЕСТ 18------")

        println("Утренний интервал: ${morningResult.interval}")
        println("Вечерний интервал: ${eveningResult.interval}")

        // Проверяем, что утром интервал больше или равен вечернему
        assertTrue("Утром мозг свежее, интервал должен быть больше",
            morningResult.interval >= eveningResult.interval)
    }

    //симуляция серии ответов с контекстом
    @Test
    fun simulateIdealLearningProgress()= runBlocking {
        val myPrediction = MLPrediction(
            forgettingProbability = 0.1f,
            optimalIntervalDays = 2.0f, // Базовый множитель
            confidence = 0.9f,
            needsMoreData = false
        )

        whenever(mockTfliteModel.predict(any())).thenReturn(myPrediction)

        var card = createTestCard(interval = 1.0)

        var context = AIContext.create(learningMode = LearningMode.LONG_TERM).copy(
            userFatigueLevel = 0.9f,
            currentHour = 10
        )

        println("--- ЗАПУСК СИМУЛЯЦИИ: 5 идеальных ответов подряд ---")

        repeat(5) { iteration ->
            val previousInterval = card.interval

            val updatedCard = calculator.calculateNextReviewWithoutSaving(
                card, context, context.learningMode, 1, 2000L
            )

            // В симуляции вручную обновляем стрик для следующего шага
            card = updatedCard.copy(
                consecutiveCorrect = card.consecutiveCorrect + 1,
                totalReviews = card.totalReviews + 1
            )

            // Вывод лога для анализа
            println("Шаг ${iteration + 1}: Интервал был ${String.format("%.2f", previousInterval)} -> Стал ${String.format("%.2f", card.interval)}")

            assertTrue("Интервал должен расти при успехе", card.interval >= previousInterval)
        }

        println("--- СИМУЛЯЦИЯ ЗАВЕРШЕНА ---")

    }
    // Helper
    private fun createTestCard(
        interval: Double = 1.0,
        consecutiveCorrect: Int = 0,
        reviewStage: Int = 0,
        easeFactor: Double = 2.5,
        questionType: String = "FACT",
        difficultyScore: Float = 0.5f
    ): Card {
        return Card(
            id = 1,
            deckId = 1,
            front = "Test",
            back = "Test",
            interval = interval,
            nextReview = System.currentTimeMillis(),
            lastReviewed = System.currentTimeMillis(),
            reviewStage = reviewStage,
            consecutiveCorrect = consecutiveCorrect,
            easeFactor = easeFactor,
            totalReviews = consecutiveCorrect,
            successRate = 0.5f,
            difficultyScore = difficultyScore,
            questionType = questionType,
            isFormula = false,
            wordCount = 10,
            averageResponseTimeMs = 3000,
            lastResponseTimeMs = 3000,
            linkedCardIds = "",
            lastFiveResults = "",
            algorithmType = "ML",
            masteryLevel = 0f,
            lastPredictedProbability = 0f
        )
    }
}
//
//package com.example.cardproject.ml
//
//import com.example.cardproject.model.AIContext
//import com.example.cardproject.model.Card
//import com.example.cardproject.model.LearningMode
//import com.example.cardproject.model.MLPrediction
//import com.example.cardproject.model.ReviewLog
//import com.example.cardproject.database.repository.ReviewLogRepository
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.runBlocking
//import org.junit.Assert.*
//import org.junit.Before
//import org.junit.Test
//import org.mockito.ArgumentMatchers.anyLong
//import org.mockito.Mock
//import org.mockito.Mockito.`when`
//import org.mockito.kotlin.*
//import org.mockito.MockitoAnnotations
//
//class MLSpacedRepetitionCalculatorTest {
//
//    @Mock
//    private lateinit var mockTfliteModel: TensorFlowLiteModel
//
//    @Mock
//    private lateinit var mockReviewLogRepository: ReviewLogRepository
//
//    private lateinit var calculator: MLSpacedRepetitionCalculator
//
//    @Before
//    fun setup() = runBlocking {
//        MockitoAnnotations.openMocks(this@MLSpacedRepetitionCalculatorTest)
//
//        // Мокаем StateFlow модели
//        whenever(mockTfliteModel.isModelReady).thenReturn(
//            MutableStateFlow(true)
//        )
//
//        // Мокаем suspend-функцию корректно
//        doReturn(0.5f)
//            .`when`(mockReviewLogRepository)
//            .getCorrectRateForCard(anyLong())
//
//        calculator = MLSpacedRepetitionCalculator(
//            tfliteModel = mockTfliteModel,
//            reviewLogRepository = mockReviewLogRepository
//        )
//    }
//
//    // -------------------------------------------------------------
//    // 1. Интервалы должны расти при правильных ответах
//    // -------------------------------------------------------------
//    @Test
//    fun intervalShouldGrow() = runBlocking {
//        var card = createTestCard(interval = 1.0)
//
//        repeat(5) { step ->
//            val prediction = MLPrediction(
//                forgettingProbability = 0.2f,
//                optimalIntervalDays = 1.3f + step,
//                confidence = 0.9f,
//                needsMoreData = false
//            )
//
//            `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)
//
//            val updated = calculator.calculateNextReviewWithoutSaving(
//                card,
//                AIContext.create(learningMode=LearningMode.LONG_TERM),
//                LearningMode.LONG_TERM,
//                1,
//                3000
//            )
//
//            assertTrue(updated.interval > card.interval)
//            card = updated
//        }
//    }
//
//    // -------------------------------------------------------------
//    // 2. SHORT_TERM должен давать меньшие интервалы
//    // -------------------------------------------------------------
//    @Test
//    fun shortTermShouldBeSmallerThanLongTerm() = runBlocking {
//        val prediction = MLPrediction(
//            forgettingProbability = 0.2f,
//            optimalIntervalDays = 2.0f,
//            confidence = 0.9f,
//            needsMoreData = false
//        )
//
//        `when`(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)
//
//        val longTerm = calculator.calculateNextReviewWithoutSaving(
//            createTestCard(interval = 1.0),
//            AIContext.create(learningMode=LearningMode.LONG_TERM),
//            LearningMode.LONG_TERM,
//            1,
//            3000
//        )
//
//        val shortTerm = calculator.calculateNextReviewWithoutSaving(
//            createTestCard(interval = 0.042),
//            AIContext.create(learningMode=LearningMode.LONG_TERM),
//            LearningMode.SHORT_TERM,
//            1,
//            3000
//        )
//
//        assertTrue(shortTerm.interval < longTerm.interval)
//    }
//
//    // -------------------------------------------------------------
//    // 3. Ошибка должна уменьшать интервал
//    // -------------------------------------------------------------
//    @Test
//    fun incorrectAnswerShouldReduceInterval() = runBlocking {
//        val card = createTestCard(interval = 5.0)
//
//        val updated = calculator.calculateNextReviewWithoutSaving(
//            card,
//            AIContext.create(learningMode=LearningMode.LONG_TERM),
//            LearningMode.LONG_TERM,
//            0,
//            3000
//        )
//
//        assertTrue(updated.interval < card.interval)
//    }
//
//    // -------------------------------------------------------------
//    // 4. Модель должна вызываться
//    // -------------------------------------------------------------
//
//    @Test
//    fun modelShouldBeCalled() {
//        runBlocking {
//            val prediction = MLPrediction(0.3f, 1.5f, 0.8f, false)
//            whenever(mockTfliteModel.predict(argThat { true })).thenReturn(prediction)
//
//            calculator.calculateNextReviewWithoutSaving(
//                createTestCard(),
//                AIContext.create(),
//                LearningMode.LONG_TERM,
//                1,
//                3000
//            )
//
//            verify(mockTfliteModel, atLeastOnce()).predict(any())
//        }
//    }
//
//    // -------------------------------------------------------------
//    // Helper
//    // -------------------------------------------------------------
//    private fun createTestCard(
//        interval: Double = 1.0,
//        consecutiveCorrect: Int = 0,
//        reviewStage: Int = 0
//    ): Card {
//        return Card(
//            id = 1,
//            deckId = 1,
//            front = "Test",
//            back = "Test",
//            interval = interval,
//            nextReview = System.currentTimeMillis(),
//            lastReviewed = System.currentTimeMillis(),
//            reviewStage = reviewStage,
//            consecutiveCorrect = consecutiveCorrect,
//            easeFactor = 2.5,
//            totalReviews = consecutiveCorrect,
//            successRate = 0.5f,
//            difficultyScore = 0.5f,
//            questionType = "FACT",
//            isFormula = false,
//            wordCount = 10,
//            averageResponseTimeMs = 3000,
//            lastResponseTimeMs = 3000,
//            linkedCardIds = "",
//            lastFiveResults = "",
//            algorithmType = "ML",
//            masteryLevel = 0f,
//            lastPredictedProbability = 0f
//        )
//    }
//}
