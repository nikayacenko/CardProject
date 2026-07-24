package com.example.cardproject.model

data class DeckWithTags(
    val deck: Deck,
    val tags: List<String>
) {
    // 🔥 ДЛЯ ОТЛАДКИ
    override fun toString(): String {
        return "DeckWithTags(deckId=${deck.id}, userId=${deck.userId}, name='${deck.name}', tags=$tags)"
    }
}