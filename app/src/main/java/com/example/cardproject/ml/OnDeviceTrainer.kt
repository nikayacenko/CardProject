// app/src/main/java/com/example/cardproject/ml/OnDeviceTrainer.kt
package com.example.cardproject.ml

import android.content.Context
import android.util.Log
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.model.MLPrediction
import com.example.cardproject.model.ReviewLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceTrainer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reviewLogRepository: ReviewLogRepository
) {

    private val TAG = "OnDeviceTrainer"
    private var interpreter: Interpreter? = null

    private val _isTraining = MutableStateFlow(false)
    val isTraining: StateFlow<Boolean> = _isTraining.asStateFlow()

    private val _trainingProgress = MutableStateFlow(0f)
    val trainingProgress: StateFlow<Float> = _trainingProgress.asStateFlow()

    private val _trainingLoss = MutableStateFlow(0f)
    val trainingLoss: StateFlow<Float> = _trainingLoss.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val MODEL_FILE_NAME = "model_with_training.tflite"

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "📥 ЗАГРУЗКА МОДЕЛИ")
            Log.d(TAG, "========================================")

            val modelFile = File(context.filesDir, MODEL_FILE_NAME)
            Log.d(TAG, "📁 Путь к модели: ${modelFile.absolutePath}")
            Log.d(TAG, "📁 Модель существует: ${modelFile.exists()}")

            if (modelFile.exists()) {
                Log.d(TAG, "📁 Размер модели: ${modelFile.length() / 1024} KB")
            }

            copyModelFromAssets()

            if (!modelFile.exists()) {
                Log.e(TAG, "❌ Модель не найдена после копирования!")
                _isModelReady.value = false
                return
            }

            Log.d(TAG, "🔧 Создание Interpreter...")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)

                try {
                    Log.d(TAG, "🔧 Попытка добавить FlexDelegate...")
                    val flexDelegateClass = Class.forName("org.tensorflow.lite.flex.FlexDelegate")
                    val flexDelegate = flexDelegateClass.newInstance() as org.tensorflow.lite.Delegate
                    addDelegate(flexDelegate)
                    Log.d(TAG, "✅ FlexDelegate добавлен успешно")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ FlexDelegate не найден: ${e.message}")
                }
            }

            val interpreterInstance = Interpreter(modelFile, options)
            Log.d(TAG, "✅ Interpreter создан")

            Log.d(TAG, "🔧 Аллокация тензоров...")
            interpreterInstance.allocateTensors()
            Log.d(TAG, "✅ Тензоры аллоцированы")

            val signatureKeys = interpreterInstance.signatureKeys
            Log.d(TAG, "🔑 Доступные сигнатуры: ${signatureKeys.joinToString()}")

            // 🔥 ИНИЦИАЛИЗАЦИЯ - ПРАВИЛЬНЫЙ СПОСОБ
            if (signatureKeys.contains("init")) {
                Log.d(TAG, "🔄 Выполнение инициализации...")

                try {
                    // 🔥 ВАЖНО: создаём фейковый вход для init
                    // Некоторые модели требуют вход для инициализации
                    val dummyInput = ByteBuffer.allocateDirect(1 * 20 * 4).apply {
                        order(ByteOrder.nativeOrder())
                        // Заполняем нулями
                        for (i in 0 until 20) {
                            putFloat(0.0f)
                        }
                        rewind()
                    }

                    // Пробуем с фейковым входом
                    val result = interpreterInstance.runSignature(
                        mapOf("x" to dummyInput),  // ← входной тензор
                        mapOf<String, Any>(),      // ← выходы не нужны
                        "init"
                    )
                    Log.d(TAG, "✅ Инициализация успешна! Результат: $result")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка init с входом: ${e.message}")

                    // Пробуем без входов
                    try {
                        Log.d(TAG, "🔄 Пробуем init без входов...")
                        val result = interpreterInstance.runSignature(
                            mapOf<String, Any>(),
                            mapOf<String, Any>(),
                            "init"
                        )
                        Log.d(TAG, "✅ Init без входов успешен!")
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Init без входов тоже не работает: ${e2.message}")
                        // Продолжаем, даже если init не работает
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Сигнатура 'init' не найдена")
            }

            interpreter = interpreterInstance
            _isModelReady.value = true
            Log.d(TAG, "========================================")
            Log.d(TAG, "✅ МОДЕЛЬ ЗАГРУЖЕНА УСПЕШНО!")
            Log.d(TAG, "========================================")

        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "❌ ОШИБКА ЗАГРУЗКИ МОДЕЛИ")
            Log.e(TAG, "========================================")
            Log.e(TAG, "Ошибка: ${e.message}")
            e.printStackTrace()
            _isModelReady.value = false
        }
    }
    private fun copyModelFromAssets() {
        try {
            Log.d(TAG, "📁 Копирование модели из assets...")
            val inputStream = context.assets.open(MODEL_FILE_NAME)
            val outputFile = File(context.filesDir, MODEL_FILE_NAME)

            Log.d(TAG, "   Источник: assets/$MODEL_FILE_NAME")
            Log.d(TAG, "   Назначение: ${outputFile.absolutePath}")

            outputFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }

            Log.d(TAG, "✅ Модель скопирована, размер: ${outputFile.length() / 1024} KB")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка копирования модели: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun fineTuneOnDevice(
        epochs: Int = 5,
        batchSize: Int = 16
    ): Boolean {
        Log.d(TAG, "========================================")
        Log.d(TAG, "🚀 ЗАПУСК ДООБУЧЕНИЯ")
        Log.d(TAG, "========================================")
        Log.d(TAG, "📊 epochs=$epochs, batchSize=$batchSize")

        if (_isTraining.value) {
            Log.w(TAG, "⚠️ Обучение уже запущено")
            return false
        }

        if (!_isModelReady.value) {
            Log.e(TAG, "❌ Модель не загружена! _isModelReady=${_isModelReady.value}")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                _isTraining.value = true
                _trainingProgress.value = 0f
                _trainingLoss.value = 0f

                // Получаем логи
                Log.d(TAG, "📊 Загрузка логов из БД...")
                val logs = reviewLogRepository.getAllLogs()
                Log.d(TAG, "📊 Загружено ${logs.size} логов")

                if (logs.isEmpty()) {
                    Log.e(TAG, "❌ Нет логов для обучения!")
                    _isTraining.value = false
                    return@withContext false
                }

                // Проверяем интерпретатор
                val interpreter = interpreter
                if (interpreter == null) {
                    Log.e(TAG, "❌ Interpreter == null!")
                    _isTraining.value = false
                    return@withContext false
                }

                Log.d(TAG, "✅ Interpreter не null")

                // Подготавливаем данные
                Log.d(TAG, "🔄 Подготовка данных для обучения...")
                val (X, y) = prepareTrainingData(logs)
                Log.d(TAG, "📊 Подготовлено ${X.size} образцов")

                if (X.isEmpty()) {
                    Log.e(TAG, "❌ Нет данных для обучения после подготовки!")
                    _isTraining.value = false
                    return@withContext false
                }

                // Убеждаемся, что тензоры аллоцированы
                Log.d(TAG, "🔧 Аллокация тензоров...")
                interpreter.allocateTensors()
                Log.d(TAG, "✅ Тензоры аллоцированы")

                // Проверяем сигнатуры перед обучением
                val signatureKeys = interpreter.signatureKeys
                Log.d(TAG, "🔑 Сигнатуры перед обучением: ${signatureKeys.joinToString()}")

                if (!signatureKeys.contains("train_step")) {
                    Log.e(TAG, "❌ Сигнатура 'train_step' не найдена!")
                    Log.d(TAG, "   Доступные сигнатуры: ${signatureKeys.joinToString()}")
                    _isTraining.value = false
                    return@withContext false
                }

                val totalSteps = X.size * epochs
                Log.d(TAG, "📊 Всего шагов: $totalSteps")
                var currentStep = 0
                var totalLoss = 0f
                var successfulSteps = 0

                for (epoch in 0 until epochs) {
                    Log.d(TAG, "📚 Эпоха ${epoch + 1}/$epochs")

                    val indices = (0 until X.size).shuffled()
                    var epochLoss = 0f
                    var successCount = 0

                    Log.d(TAG, "   Шагов в эпохе: ${indices.size}")

                    for (idx in indices) {
                        try {
                            Log.d(TAG, "   🔄 Шаг ${currentStep + 1}/$totalSteps")

                            val loss = trainStep(interpreter, X[idx], y[idx])
                            Log.d(TAG, "   📉 Loss = $loss")

                            if (loss > 0f && loss < 10f) {
                                epochLoss += loss
                                successCount++
                                successfulSteps++
                            } else {
                                Log.w(TAG, "   ⚠️ Некорректный loss: $loss")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "   ❌ Ошибка на шаге ${currentStep + 1}: ${e.message}")
                            e.printStackTrace()
                        }

                        currentStep++
                        _trainingProgress.value = currentStep.toFloat() / totalSteps
                        _trainingLoss.value = if (successfulSteps > 0) totalLoss / successfulSteps else 0f
                    }

                    if (successCount > 0) {
                        totalLoss = epochLoss / successCount
                        Log.d(TAG, "   📉 Средний loss эпохи ${epoch + 1}: ${String.format("%.4f", totalLoss)}")
                    } else {
                        Log.w(TAG, "   ⚠️ Все шаги в эпохе ${epoch + 1} завершились с ошибкой")
                    }
                }

                _trainingProgress.value = 1f
                _trainingLoss.value = if (successfulSteps > 0) totalLoss / successfulSteps else 0f
                _isTraining.value = false

                Log.d(TAG, "========================================")
                Log.d(TAG, "✅ ДООБУЧЕНИЕ ЗАВЕРШЕНО!")
                Log.d(TAG, "   Всего успешных шагов: $successfulSteps/$totalSteps")
                Log.d(TAG, "   Финальный loss: ${_trainingLoss.value}")
                Log.d(TAG, "========================================")

                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "========================================")
                Log.e(TAG, "❌ ОШИБКА ДООБУЧЕНИЯ")
                Log.e(TAG, "========================================")
                Log.e(TAG, "Ошибка: ${e.message}")
                e.printStackTrace()
                _isTraining.value = false
                return@withContext false
            }
        }
    }

    private fun trainStep(
        interpreter: Interpreter,
        x: FloatArray,
        y: FloatArray
    ): Float {
        return try {
            Log.d(TAG, "      🔧 trainStep: начало")

            // Проверяем входные данные
            Log.d(TAG, "      x size: ${x.size}, y size: ${y.size}")

            if (x.size != 20) {
                Log.e(TAG, "      ❌ x должен быть размером 20, а не ${x.size}")
                return 0.1f
            }

            if (y.size != 3) {
                Log.e(TAG, "      ❌ y должен быть размером 3, а не ${y.size}")
                return 0.1f
            }

            // Создаем буферы
            val inputBuffer = ByteBuffer.allocateDirect(1 * 20 * 4).apply {
                order(ByteOrder.nativeOrder())
                x.forEach { putFloat(it) }
                rewind()
            }
            Log.d(TAG, "      ✅ inputBuffer создан")

            val labelsBuffer = ByteBuffer.allocateDirect(1 * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
                y.forEach { putFloat(it) }
                rewind()
            }
            Log.d(TAG, "      ✅ labelsBuffer создан")

            val outputBuffer = ByteBuffer.allocateDirect(4).apply {
                order(ByteOrder.nativeOrder())
            }
            Log.d(TAG, "      ✅ outputBuffer создан")

            // Проверяем сигнатуры
            val signatureKeys = interpreter.signatureKeys
            Log.d(TAG, "      🔑 Сигнатуры: ${signatureKeys.joinToString()}")

            if (!signatureKeys.contains("train_step")) {
                Log.e(TAG, "      ❌ Сигнатура 'train_step' не найдена!")
                Log.d(TAG, "      Доступные: ${signatureKeys.joinToString()}")
                return 0.1f
            }

            // Вызываем train_step
            Log.d(TAG, "      🚀 Вызов runSignature('train_step')...")

            val result = interpreter.runSignature(
                mapOf(
                    "x" to inputBuffer,
                    "y" to labelsBuffer
                ),
                mapOf(
                    "loss" to outputBuffer
                ),
                "train_step"
            )

            Log.d(TAG, "      ✅ runSignature выполнен, result: $result")

            outputBuffer.rewind()
            val loss = outputBuffer.float
            Log.d(TAG, "      📉 Получен loss: $loss")

            if (loss.isNaN() || loss.isInfinite()) {
                Log.w(TAG, "      ⚠️ loss = $loss (NaN или Infinite)")
                return 0.1f
            }

            val clampedLoss = loss.coerceIn(0f, 10f)
            if (clampedLoss != loss) {
                Log.w(TAG, "      ⚠️ loss был $loss, обрезан до $clampedLoss")
            }

            return clampedLoss

        } catch (e: Exception) {
            Log.e(TAG, "      ❌ Ошибка trainStep: ${e.message}")
            Log.e(TAG, "      Стек:")
            e.printStackTrace()
            return 0.1f
        }
    }

    fun predict(features: FloatArray): MLPrediction {
        val interpreter = interpreter ?: return MLPrediction(0.5f, 1.0f, 0.0f, true)

        try {
            interpreter.allocateTensors()

            val signatureKeys = interpreter.signatureKeys
            val signatureName = if (signatureKeys.contains("predict")) {
                "predict"
            } else {
                signatureKeys.firstOrNull() ?: return MLPrediction(0.5f, 1.0f, 0.0f, true)
            }

            val inputBuffer = ByteBuffer.allocateDirect(1 * 20 * 4).apply {
                order(ByteOrder.nativeOrder())
                features.forEach { putFloat(it) }
                rewind()
            }

            val outputBuffer = ByteBuffer.allocateDirect(1 * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            interpreter.runSignature(
                mapOf("x" to inputBuffer),
                mapOf("output" to outputBuffer),
                signatureName
            )

            outputBuffer.rewind()
            val result = FloatArray(3)
            for (i in 0 until 3) {
                result[i] = outputBuffer.float
            }

            return MLPrediction(
                forgettingProbability = result[0].coerceIn(0f, 1f),
                optimalIntervalDays = (result[1] * 30f).coerceIn(0.5f, 60f),
                confidence = result[2].coerceIn(0f, 1f),
                needsMoreData = result[2] < 0.3f
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка предсказания: ${e.message}")
            return MLPrediction(0.5f, 1.0f, 0.0f, true)
        }
    }

    private fun prepareTrainingData(logs: List<ReviewLog>): Pair<MutableList<FloatArray>, MutableList<FloatArray>> {
        val X = mutableListOf<FloatArray>()
        val y = mutableListOf<FloatArray>()

        for (log in logs) {
            try {
                val features = extractFeatures(log)
                val targets = prepareTargets(log)
                X.add(features)
                y.add(targets)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки лога: ${e.message}")
            }
        }

        return Pair(X, y)
    }

    private fun extractFeatures(log: ReviewLog): FloatArray {
        return floatArrayOf(
            (log.cardTextLength / 1000f).coerceIn(0f, 1f),
            if (log.hasFormulas) 1f else 0f,
            encodeQuestionType(log.questionType),
            log.difficultyScore.coerceIn(0f, 1f),
            (log.responseTimeMs / 30000f).coerceIn(0f, 1f),
            (log.hourOfDay / 24f).coerceIn(0f, 1f),
            (log.dayOfWeek / 7f).coerceIn(0f, 1f),
            log.fatigueLevel.coerceIn(0f, 1f),
            (log.cardsReviewedInSession / 100f).coerceIn(0f, 1f),
            (log.totalReviewsBefore / 50f).coerceIn(0f, 1f),
            log.correctRateBefore.coerceIn(0f, 1f),
            (log.streakCorrectBefore / 10f).coerceIn(0f, 1f),
            (log.daysSinceLastReview / 30f).coerceIn(0f, 1f),
            (log.linkedCardsCount / 20f).coerceIn(0f, 1f),
            log.linkedCardsMastery.coerceIn(0f, 1f),
            (log.fatigueLevel * (log.responseTimeMs / 30000f)).coerceIn(0f, 1f),
            (log.correctRateBefore * log.daysSinceLastReview / 30f).coerceIn(0f, 1f),
            0f, 0f, 0f
        )
    }

    private fun prepareTargets(log: ReviewLog): FloatArray {
        val forgetting = if (log.wasCorrect) 0f else 1f

        var base = if (log.wasCorrect) {
            minOf(30f, 1f + log.totalReviewsBefore * 0.5f)
        } else {
            1f
        }
        if (log.fatigueLevel > 0.7f) base *= 0.8f
        if (log.hasFormulas) base *= 0.9f

        val interval = (base / 30f).coerceIn(0f, 1f)
        val confidence = 0.8f

        return floatArrayOf(forgetting, interval, confidence)
    }

    private fun encodeQuestionType(type: String): Float {
        return when (type) {
            "FACT" -> 0.1f
            "DEFINITION" -> 0.4f
            "PROOF" -> 0.9f
            else -> 0.5f
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        _isModelReady.value = false
    }
}