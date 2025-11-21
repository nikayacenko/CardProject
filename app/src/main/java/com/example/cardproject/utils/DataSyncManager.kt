package com.example.cardproject.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DataSyncManager {
    private val _refreshDecks = MutableSharedFlow<Unit>()
    val refreshDecks: SharedFlow<Unit> = _refreshDecks.asSharedFlow()

    suspend fun notifyDecksChanged() {
        _refreshDecks.emit(Unit)
        println("🔔 DataSyncManager: отправлено уведомление об обновлении колод")
    }
}