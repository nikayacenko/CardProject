package com.example.cardproject.ui.card

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TestViewModel @Inject constructor() : ViewModel() {
    init {
        println("✅ TestViewModel создан через Hilt")
    }
}