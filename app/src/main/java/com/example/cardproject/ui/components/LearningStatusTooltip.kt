// LearningStatusTooltip.kt
package com.example.cardproject.ui.components

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.example.cardproject.R

class LearningStatusTooltip(context: Context) : PopupWindow(context) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.tooltip_learning_status, null)
        contentView = view

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

    fun show(target: View, text: String) {
        val textView = contentView.findViewById<TextView>(R.id.tooltipText)
        textView.text = text

        contentView.measure(
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