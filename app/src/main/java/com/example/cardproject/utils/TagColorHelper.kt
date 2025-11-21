package com.example.cardproject.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.cardproject.R

object TagColorHelper {
    private val strokeColorIds = listOf(
        R.color.tag_stroke_color_1,
        R.color.tag_stroke_color_2,
        R.color.tag_stroke_color_3,
        R.color.tag_stroke_color_4,
        R.color.tag_stroke_color_5,
        R.color.tag_stroke_color_6
    )

    fun getTagStrokeColor(context: Context, position: Int): Int {
        val colorIndex = position % strokeColorIds.size
        return ContextCompat.getColor(context, strokeColorIds[colorIndex])
    }

    fun getTagStrokeColorRes(position: Int): Int {
        val colorIndex = position % strokeColorIds.size
        return strokeColorIds[colorIndex]
    }
}