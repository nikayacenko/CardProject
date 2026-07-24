// model/DeckPrediction.kt
package com.example.cardproject.model

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Модель прогноза завершения колоды
 */
data class DeckPrediction(
    val deckId: Long,
    val deckName: String,

    // Текущая статистика
    val totalCards: Int,
    val learnedCards: Int,
    val remainingCards: Int,

    // Скорость обучения
    val dailyLearningRate: Float,        // карточек в день (средняя)
    val weeklyLearningRate: Float,       // карточек в неделю
    val monthlyLearningRate: Float,      // карточек в месяц

    // Прогноз
    val predictedDaysToComplete: Int,    // дней до завершения
    val predictedWeeksToComplete: Int,   // недель до завершения
    val predictedCompletionDate: String, // дата завершения

    // Рекомендации
    val recommendedDailyCards: Int,      // рекомендуемое кол-во карточек в день
    val recommendedWeeklyCards: Int,     // рекомендуемое кол-во в неделю
    val confidenceLevel: ConfidenceLevel, // уровень уверенности прогноза

    // Дополнительная информация
    val isOnTrack: Boolean,              // идет ли по плану
    val paceDescription: String,         // описание темпа
    val improvementTip: String,          // совет по улучшению
    val milestones: List<Milestone>      // вехи на пути к завершению
) {
    // Форматированный прогресс
    val progressPercentage: Int
        get() = if (totalCards > 0) (learnedCards.toFloat() / totalCards * 100).toInt() else 0

    // Состояние прогноза
    enum class ConfidenceLevel {
        HIGH,        // достаточно данных для точного прогноза
        MEDIUM,      // есть данные, но нужно больше
        LOW,         // недостаточно данных
        INSUFFICIENT // нет данных для прогноза
    }

    // Веха на пути к завершению
    data class Milestone(
        val percentage: Int,        // 25%, 50%, 75%
        val predictedDate: String,
        val isAchieved: Boolean
    )

    // Получить эмодзи для статуса
    fun getStatusEmoji(): String = when {
        predictedDaysToComplete <= 0 -> "✅"
        predictedDaysToComplete <= 7 -> "🔥"
        predictedDaysToComplete <= 30 -> "⚡"
        predictedDaysToComplete <= 90 -> "📈"
        else -> "🐢"
    }

    // Получить текстовое описание статуса
    fun getStatusText(): String = when {
        predictedDaysToComplete <= 0 -> "Колода завершена! 🎉"
        predictedDaysToComplete <= 7 -> "Вы на финишной прямой!"
        predictedDaysToComplete <= 30 -> "Отличный темп, продолжайте!"
        predictedDaysToComplete <= 90 -> "Хороший прогресс!"
        else -> "Для ускорения увеличьте ежедневную норму"
    }
}

/**
 * Расширение для DeckStats - создание прогноза
 */
fun DeckStats.toPrediction(
    firstSessionDate: Date?,
    sessionsHistory: List<SessionStats>
): DeckPrediction {
    android.util.Log.d("DeckPrediction", "🔮 Создание прогноза для колоды $deckName")
    android.util.Log.d("DeckPrediction", "   totalCards=$totalCards, learnedCards=$learnedCards, inProgressCards=$inProgressCards")

    val now = Calendar.getInstance().time

    val daysSinceFirstSession = if (firstSessionDate != null) {
        val diff = now.time - firstSessionDate.time
        val days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS).toInt()
        android.util.Log.d("DeckPrediction", "   daysSinceFirstSession=$days")
        days
    } else {
        android.util.Log.d("DeckPrediction", "   ⚠️ Нет даты первой сессии")
        0
    }

    // 🔥 УЧИТЫВАЕМ КАРТОЧКИ В ПРОЦЕССЕ ИЗУЧЕНИЯ
    // Считаем, что карточки в процессе - это частично изученные
    val effectiveLearned = learnedCards + (inProgressCards * 0.5f).toInt()
    android.util.Log.d("DeckPrediction", "   effectiveLearned=$effectiveLearned (learned=$learnedCards + inProgress*0.5)")

    // Рассчитываем скорость обучения
    val dailyRate = if (daysSinceFirstSession > 0) {
        effectiveLearned.toFloat() / daysSinceFirstSession
    } else {
        0f
    }
    android.util.Log.d("DeckPrediction", "   dailyRate=$dailyRate")

    // Еженедельная скорость (за последние 7 дней)
    val weeklyRate = calculateWeeklyRate(sessionsHistory)
    val monthlyRate = weeklyRate * 4

    // Прогноз завершения - используем effectiveLearned
    val remaining = totalCards - effectiveLearned
    android.util.Log.d("DeckPrediction", "   remaining=$remaining")

    val daysToComplete = if (dailyRate > 0) {
        (remaining / dailyRate).toInt()
    } else {
        Int.MAX_VALUE
    }
    android.util.Log.d("DeckPrediction", "   daysToComplete=$daysToComplete")

    // 🔥 ДЛЯ ПРОГНОЗА ИСПОЛЬЗУЕМ effectiveLearned
    val completionDate = if (daysToComplete > 0 && daysToComplete < Int.MAX_VALUE) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysToComplete)
        calendar.time
    } else {
        null
    }

    // Уровень уверенности
    val confidenceLevel = when {
        daysSinceFirstSession < 3 -> DeckPrediction.ConfidenceLevel.INSUFFICIENT
        daysSinceFirstSession < 7 -> DeckPrediction.ConfidenceLevel.LOW
        daysSinceFirstSession < 30 -> DeckPrediction.ConfidenceLevel.MEDIUM
        else -> DeckPrediction.ConfidenceLevel.HIGH
    }

    // Рекомендации
    val recommendedDaily = if (daysToComplete > 0 && daysToComplete < Int.MAX_VALUE) {
        maxOf(1, (remaining / maxOf(daysToComplete, 1)))
    } else {
        maxOf(1, (remaining / 30)) // 30 дней как разумный срок
    }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // 🔥 ВЕХИ РАССЧИТЫВАЕМ НА ОСНОВЕ effectiveLearned
    val totalForMilestones = maxOf(totalCards, 1)

    return DeckPrediction(
        deckId = deckId,
        deckName = deckName,
        totalCards = totalCards,
        learnedCards = learnedCards, // оставляем как есть для отображения
        remainingCards = remaining,
        dailyLearningRate = dailyRate,
        weeklyLearningRate = weeklyRate,
        monthlyLearningRate = monthlyRate,
        predictedDaysToComplete = if (daysToComplete < Int.MAX_VALUE) daysToComplete else -1,
        predictedWeeksToComplete = if (daysToComplete < Int.MAX_VALUE) daysToComplete / 7 else -1,
        predictedCompletionDate = completionDate?.let { dateFormatter.format(it) } ?: "Неизвестно",
        recommendedDailyCards = recommendedDaily,
        recommendedWeeklyCards = recommendedDaily * 7,
        confidenceLevel = confidenceLevel,
        isOnTrack = dailyRate >= 5 || (dailyRate > 0 && remaining < 20),
        paceDescription = when {
            dailyRate >= 20 -> "Молниеносный 🚀"
            dailyRate >= 10 -> "Быстрый ⚡"
            dailyRate >= 5 -> "Оптимальный ✅"
            dailyRate >= 2 -> "Медленный 🐢"
            dailyRate > 0 -> "Начинающий 🌱"
            else -> "Требуется начало 🎯"
        },
        improvementTip = when {
            dailyRate <= 0 -> "Начните изучать карточки ежедневно"
            dailyRate < 5 -> "Попробуйте увеличить до $recommendedDaily карточек в день"
            dailyRate < 10 -> "Отличный темп! Добавьте ещё немного"
            else -> "Вы на правильном пути! 🎉"
        },
        milestones = listOf(
            DeckPrediction.Milestone(
                percentage = 25,
                predictedDate = calculateMilestoneDate(0.25f, dailyRate, totalCards - effectiveLearned),
                isAchieved = effectiveLearned >= totalForMilestones * 0.25
            ),
            DeckPrediction.Milestone(
                percentage = 50,
                predictedDate = calculateMilestoneDate(0.5f, dailyRate, totalCards - effectiveLearned),
                isAchieved = effectiveLearned >= totalForMilestones * 0.5
            ),
            DeckPrediction.Milestone(
                percentage = 75,
                predictedDate = calculateMilestoneDate(0.75f, dailyRate, totalCards - effectiveLearned),
                isAchieved = effectiveLearned >= totalForMilestones * 0.75
            )
        )
    )
}
// Вспомогательная функция для расчета вех
private fun calculateMilestoneDate(
    targetPercentage: Float,
    dailyRate: Float,
    remaining: Int
): String {
    if (dailyRate <= 0) return "Неизвестно"
    val daysToMilestone = (remaining * targetPercentage / dailyRate).toInt()
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, daysToMilestone)
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)
}

// Расчет еженедельной скорости с использованием существующей SessionStats
private fun calculateWeeklyRate(sessionsHistory: List<SessionStats>): Float {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -7) // 7 дней назад
    val lastWeek = calendar.time

    // Фильтруем сессии за последнюю неделю
    val weeklySessions = sessionsHistory.filter { session ->
        val sessionDate = Date(session.date) // используем Long timestamp из SessionStats
        sessionDate.after(lastWeek)
    }

    // Считаем общее количество изученных карточек (правильные + неправильные)
    val weeklyLearned = weeklySessions.sumOf { it.totalCards }

    return weeklyLearned / 7f
}