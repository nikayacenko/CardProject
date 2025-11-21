package com.example.cardproject.di

import android.content.Context
import com.example.cardproject.database.AppDatabase
import com.example.cardproject.database.dao.CardDao
import com.example.cardproject.database.dao.DeckDao
import com.example.cardproject.database.dao.NoteDao
import com.example.cardproject.database.dao.SessionStatsDao
import com.example.cardproject.database.dao.TagDao
import com.example.cardproject.database.repository.CardRepository
import com.example.cardproject.database.repository.DeckRepository
import com.example.cardproject.database.repository.NoteRepository
import com.example.cardproject.database.repository.SessionStatsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideDeckDao(database: AppDatabase): DeckDao {
        return database.deckDao()
    }

    @Provides
    fun provideCardDao(database: AppDatabase): CardDao {
        return database.cardDao()
    }

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao {
        return database.tagDao()
    }

    @Provides
    fun provideNoteDao(database: AppDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    fun provideSessionStatsDao(database: AppDatabase): SessionStatsDao {
        return database.sessionStatsDao()
    }

    @Provides
    @Singleton
    fun provideCardRepository(cardDao: CardDao): CardRepository {
        return CardRepository(cardDao)
    }

    @Provides
    @Singleton
    fun provideDeckRepository(
        deckDao: DeckDao,
        tagDao: TagDao,
        cardRepository: CardRepository
    ): DeckRepository {
        return DeckRepository(deckDao, tagDao, cardRepository)
    }

    @Provides
    fun provideNoteRepository(
        noteDao: NoteDao,
        tagDao: TagDao
    ): NoteRepository {
        return NoteRepository(noteDao, tagDao)
    }

    @Provides
    @Singleton
    fun provideSessionStatsRepository(sessionStatsDao: SessionStatsDao): SessionStatsRepository {
        return SessionStatsRepository(sessionStatsDao)
    }





}