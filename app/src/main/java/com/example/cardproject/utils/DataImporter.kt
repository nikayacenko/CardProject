package com.example.cardproject.utils


import android.content.Context
import com.example.cardproject.database.AppDatabase
import com.example.cardproject.model.Card
import com.example.cardproject.model.Deck
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.Tag
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataImporter(private val context: Context) {

    data class ImportCard(
        val front: String,
        val back: String
    )

    data class ImportDeck(
        val name: String,
        val description: String,
        val coverColor: String,
        val learningMode: String,
        val tags: List<String>,
        val cards: List<ImportCard>
    )

    data class ImportData(
        val decks: List<ImportDeck>
    )

    suspend fun importSampleData(): String = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("sample_decks.json").bufferedReader().use { it.readText() }
            val gson = Gson()
            val data = gson.fromJson(json, ImportData::class.java)

            val db = AppDatabase.getInstance(context)

            var imported = 0

            data.decks.forEach { importDeck ->
                // Создаем колоду
                val deck = Deck(
                    name = importDeck.name,
                    description = importDeck.description,
                    coverColor = importDeck.coverColor,
                    learningMode = LearningMode.valueOf(importDeck.learningMode),
                    cardCount = importDeck.cards.size
                )
                val deckId = db.deckDao().insertDeck(deck)

                // Добавляем теги
                importDeck.tags.forEach { tagName ->
                    db.tagDao().insertTag(Tag(name = tagName, deckId = deckId))
                }

                // Добавляем карточки
                importDeck.cards.forEach { importCard ->
                    val card = Card(
                        deckId = deckId,
                        front = importCard.front,
                        back = importCard.back,
                        wordCount = importCard.front.split(" ").size + importCard.back.split(" ").size,
                        questionType = "FACT"
                    )
                    db.cardDao().insertCard(card)
                }

                imported++
            }

            "✅ Импортировано $imported колод"
        } catch (e: Exception) {
            "❌ Ошибка: ${e.message}"
        }
    }
}