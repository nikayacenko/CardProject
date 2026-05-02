package com.example.cardproject.model

import com.example.cardproject.R

data class CalendarDayData(
    val date: Long,                    // timestamp
    val dayOfMonth: Int,               // число месяца
    val cardCount: Int,                // количество карточек
    val successRate: Float,            // процент успеха (0-100)
    val isToday: Boolean = false,
    val isSelected: Boolean = false
) {
    fun getColorResId(): Int {
        return when {
            cardCount == 0 -> R.drawable.calendar_day_empty
            successRate >= 80f -> R.drawable.calendar_day_high
            successRate >= 50f -> R.drawable.calendar_day_medium
            else -> R.drawable.calendar_day_low
        }
    }

    fun getTooltipText(): String {
        return if (cardCount > 0) {
            "$dayOfMonth число • $cardCount карточек • ${successRate.toInt()}% верно"
        } else {
            "$dayOfMonth число • нет занятий"
        }
    }
}