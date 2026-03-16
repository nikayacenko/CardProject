// File: utils/DataExporter.kt
package com.example.cardproject.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.model.ReviewLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reviewLogRepository: ReviewLogRepository
) {

    @RequiresApi(Build.VERSION_CODES.FROYO)
    suspend fun exportLogsToCsv(): File = withContext(Dispatchers.IO) {
        val logs = reviewLogRepository.getLogsForTraining(10000) // Получаем до 10000 записей

        // Создаем файл с временной меткой
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "review_logs_$timeStamp.csv"
        val file = File(context.getExternalFilesDir(null), fileName)

        FileWriter(file).use { writer ->
            // Заголовки CSV (20 признаков)
            writer.append("cardTextLength,hasFormulas,questionType,responseTimeMs,hourOfDay,dayOfWeek,fatigueLevel,cardsReviewedInSession,totalReviewsBefore,correctRateBefore,streakCorrectBefore,daysSinceLastReview,linkedCardsCount,linkedCardsMastery,wasCorrect\n")

            // Данные
            logs.forEach { log ->
                writer.append(
                    "${log.cardTextLength}," +
                            "${log.hasFormulas}," +
                            "\"${log.questionType}\"," +
                            "${log.responseTimeMs}," +
                            "${log.hourOfDay}," +
                            "${log.dayOfWeek}," +
                            "${log.fatigueLevel}," +
                            "${log.cardsReviewedInSession}," +
                            "${log.totalReviewsBefore}," +
                            "${log.correctRateBefore}," +
                            "${log.streakCorrectBefore}," +
                            "${log.daysSinceLastReview}," +
                            "${log.linkedCardsCount}," +
                            "${log.linkedCardsMastery}," +
                            "${log.wasCorrect}\n"
                )
            }
        }

        return@withContext file
    }

    fun shareFile(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "Review Logs Export")
            putExtra(Intent.EXTRA_TEXT, "Экспорт логов повторений для обучения ML модели")
        }
    }
}