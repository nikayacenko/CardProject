// LearningStatusTooltip.kt
package com.example.cardproject.ui.components

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.cardproject.R

class LearningStatusTooltip(context: Context) : PopupWindow(context) {

    private val tooltipView: View
    private val tooltipText: TextView
    init {
        tooltipView = LayoutInflater.from(context).inflate(R.layout.tooltip_learning_status, null)
        tooltipText = tooltipView.findViewById(R.id.tooltipText)
        applyThemeColors(context)
        contentView = tooltipView

        // ВАЖНО: установите эти флаги
        isOutsideTouchable = true
        isFocusable = false // ИЗМЕНИТЕ на false
        isTouchable = true
        setBackgroundDrawable(null) // ИЛИ прозрачный drawable

        width = ViewGroup.LayoutParams.WRAP_CONTENT
        height = ViewGroup.LayoutParams.WRAP_CONTENT

        // ДОБАВЬТЕ: не блокировать касания под подсказкой
        setTouchInterceptor { _, event ->
            // Передаем событие касания под подсказку
            false
        }
    }
    /**
     * Применение цветов из текущей темы
     */
    private fun applyThemeColors(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val themeColor = prefs.getString("theme_color", "purple") ?: "purple"

        // Выбираем drawable для фона в зависимости от темы
        val drawableRes = when (themeColor) {
            "blue" -> R.drawable.bg_tooltip_blue
            "green" -> R.drawable.bg_tooltip_green
            "orange" -> R.drawable.bg_tooltip_orange
            "pink" -> R.drawable.bg_tooltip_pink
            else -> R.drawable.bg_tooltip
        }

        tooltipText.setBackgroundResource(drawableRes)

        // Цвет текста — белый для всех тем (хорошо читается на цветном фоне)
        tooltipText.setTextColor(ContextCompat.getColor(context, R.color.white))
    }

    fun show(target: View, text: String) {

        applyThemeColors(target.context)
        tooltipText.text = text

        tooltipView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val location = IntArray(2)
        target.getLocationInWindow(location)

        val x = location[0] - (contentView.measuredWidth - target.width) / 2
        val y = location[1] - contentView.measuredHeight - 8

        showAtLocation(target, Gravity.NO_GRAVITY, x, y)

        contentView.alpha = 0f
        contentView.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    fun dismissWithAnimation() {
        contentView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                dismiss()
            }
            .start()
    }
}