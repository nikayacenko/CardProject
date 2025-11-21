package com.example.cardproject.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "tag",
    foreignKeys = [ForeignKey(
        entity = Deck::class,
        parentColumns = ["id"],
        childColumns = ["deckId"],
        onDelete = ForeignKey.CASCADE
    ),
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val deckId: Long? = null, // Сделаем nullable для тегов конспектов
    val noteId: Long? = null  // Добавим поле для связи с конспектами
)