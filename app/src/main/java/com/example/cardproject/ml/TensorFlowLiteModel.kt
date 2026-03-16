package com.example.cardproject.ml


import android.content.Context
import android.content.res.AssetFileDescriptor
import com.example.cardproject.model.MLPrediction
import com.example.cardproject.model.ReviewLog
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.MappedByteBuffer
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.DataType

@Singleton
class TensorFlowLiteModel @Inject constructor(
    private val context: Context
) {

    private var interpreter: Interpreter? = null
    private val _isModelReady = MutableStateFlow(false)
    val isModelReady = _isModelReady.asStateFlow()

    // Параметры модели
    private val inputSize = 20  // Количество признаков
    private val outputSize = 3   // [forgetting_prob, interval_days, confidence]

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            // Загружаем модель из assets
            val modelFile = FileUtil.loadMappedFile(context, "forgetting_model.tflite")
            interpreter = Interpreter(modelFile)
            _isModelReady.value = true
            println("✅ Модель TensorFlow Lite загружена")
        } catch (e: Exception) {
            println("❌ Ошибка загрузки модели: ${e.message}")
            _isModelReady.value = false
        }
    }

    /**
     * Предсказание вероятности забывания
     * Именно так: model.predict(features) одной строкой!
     */
    suspend fun predict(log: ReviewLog): MLPrediction {
        if (!_isModelReady.value) {
            return MLPrediction(0.5f, 1.0f, 0.1f, true)
        }

        return try {
            // 1. Преобразуем признаки в вектор
            val inputFeatures = extractFeatures(log)

            // 2. Подготавливаем входной тензор
            val inputArray = arrayOf(inputFeatures)
            val inputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, inputSize), DataType.FLOAT32)
            inputBuffer.loadArray(inputFeatures)

            // 3. Подготавливаем выходной тензор
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, outputSize), DataType.FLOAT32)

            // 4. ЗАПУСК ПРЕДСКАЗАНИЯ (одна строка!)
            interpreter?.run(inputBuffer.buffer, outputBuffer.buffer.rewind())

            // 5. Получаем результаты
            val results = outputBuffer.floatArray

            MLPrediction(
                forgettingProbability = results[0],
                optimalIntervalDays = results[1] * 30, // Нормализация обратно
                confidence = results[2],
                needsMoreData = results[2] < 0.3f
            )

        } catch (e: Exception) {
            e.printStackTrace()
            MLPrediction(0.5f, 1.0f, 0.0f, true)
        }
    }

    /**
     * Пакетное предсказание для нескольких карточек
     */
    suspend fun predictBatch(logs: List<ReviewLog>): List<MLPrediction> {
        if (!_isModelReady.value || logs.isEmpty()) return emptyList()

        return try {
            val batchSize = logs.size
            // 1. Создаем массив признаков
            val inputArray = Array(batchSize) { i ->
                extractFeatures(logs[i])
            }

            // 2. Преобразуем в плоский массив Float
            val flatArray = FloatArray(batchSize * inputSize)
            for (i in 0 until batchSize) {
                val features = inputArray[i]
                for (j in features.indices) {
                    flatArray[i * inputSize + j] = features[j]
                }
            }

            // 3. Загружаем в тензор
            val inputBuffer = TensorBuffer.createFixedSize(
                intArrayOf(batchSize, inputSize),
                DataType.FLOAT32
            )
            inputBuffer.loadArray(flatArray)

            val outputBuffer = TensorBuffer.createFixedSize(
                intArrayOf(batchSize, outputSize),
                DataType.FLOAT32
            )

            interpreter?.run(inputBuffer.buffer, outputBuffer.buffer.rewind())

            val results = outputBuffer.floatArray
            val predictions = mutableListOf<MLPrediction>()

            for (i in 0 until batchSize) {
                predictions.add(
                    MLPrediction(
                        forgettingProbability = results[i * outputSize],
                        optimalIntervalDays = results[i * outputSize + 1] * 30,
                        confidence = results[i * outputSize + 2],
                        needsMoreData = results[i * outputSize + 2] < 0.3f
                    )
                )
            }

            predictions

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Извлечение признаков из лога (feature engineering)
     */
    private fun extractFeatures(log: ReviewLog): FloatArray {
        return floatArrayOf(
            // Признаки карточки
            (log.cardTextLength / 1000f).coerceIn(0f, 1f),                   // Нормализованная длина
            if (log.hasFormulas) 1f else 0f,                // Наличие формул
            encodeQuestionType(log.questionType),            // Тип вопроса (0-1)
            log.difficultyScore.coerceIn(0f, 1f),                           // Сложность карточки

            // Контекстные признаки
            (log.responseTimeMs / 30000f).coerceIn(0f, 1f),// Время ответа (0-1)
            (log.hourOfDay / 24f).coerceIn(0f, 1f), // Час дня (0-1)
            (log.dayOfWeek / 7f).coerceIn(0f, 1f), // День недели (0-1)
            log.fatigueLevel.coerceIn(0f, 1f),  // Усталость (0-1)
            (log.cardsReviewedInSession / 100f).coerceIn(0f, 1f), // Позиция в сессии

            // История карточки
            (log.totalReviewsBefore / 50f).coerceIn(0f, 1f), // Всего повторений
            log.correctRateBefore.coerceIn(0f, 1f), // Процент успеха
            (log.streakCorrectBefore / 10f).coerceIn(0f, 1f),  // Серия успехов
            (log.daysSinceLastReview / 30f).coerceIn(0f, 1f), // Дней с прошлого раза

            // Семантические связи
            (log.linkedCardsCount / 20f).coerceIn(0f, 1f), // Количество связей
            log.linkedCardsMastery.coerceIn(0f, 1f), // Выученность связанных

            // Мета-признаки (комбинации)
            (log.fatigueLevel * (log.responseTimeMs / 30000f)).coerceIn(0f, 1f),  // Взаимодействие усталость×время
            (log.correctRateBefore * log.daysSinceLastReview / 30f).coerceIn(0f, 1f), // Прошлая успеваемость×интервал

            // Дополнительные признаки (заполняем до 20)
            0f, 0f, 0f, 0f
        )
    }

    private fun encodeQuestionType(type: String): Float {
        return when (type) {
            "FACT" -> 0.1f
            "DEFINITION" -> 0.3f
            "TRANSLATION" -> 0.4f
            "FORMULA" -> 0.7f
            "PROOF" -> 0.9f
            else -> 0.5f
        }
    }

    /**
     * Обновление модели (загрузка новой версии)
     */
    fun updateModel(newModelFile: MappedByteBuffer) {
        try {
            interpreter?.close()
            interpreter = Interpreter(newModelFile)
            _isModelReady.value = true
            println("✅ Модель обновлена")
        } catch (e: Exception) {
            println("❌ Ошибка обновления модели: ${e.message}")
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}