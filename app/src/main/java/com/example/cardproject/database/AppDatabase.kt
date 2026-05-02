package com.example.cardproject.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cardproject.database.dao.CardDao
import com.example.cardproject.database.dao.DeckDao
import com.example.cardproject.database.dao.NoteDao
import com.example.cardproject.database.dao.ReviewLogDao
import com.example.cardproject.database.dao.SessionStatsDao
import com.example.cardproject.database.dao.TagDao
import com.example.cardproject.model.Deck
import com.example.cardproject.model.Card
import com.example.cardproject.model.Note
import com.example.cardproject.model.SessionStats
import com.example.cardproject.model.Tag
import com.example.cardproject.model.ReviewLog

@Database(
    entities = [
        Deck::class,
        Card::class,
        Tag::class,
        SessionStats::class,
        Note::class,
        ReviewLog::class
    ],
    version = 12, // УВЕЛИЧЬТЕ ВЕРСИЮ ДО 7
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun tagDao(): TagDao
    abstract fun sessionStatsDao(): SessionStatsDao
    abstract fun noteDao(): NoteDao

    abstract fun reviewLogDao(): ReviewLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "card_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // ВКЛЮЧАЕМ ПОДДЕРЖКУ ВНЕШНИХ КЛЮЧЕЙ ДЛЯ КАСКАДНОГО УДАЛЕНИЯ
                            db.execSQL("PRAGMA foreign_keys = ON;")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}