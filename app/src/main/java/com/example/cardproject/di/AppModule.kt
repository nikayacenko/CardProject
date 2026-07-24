package com.example.cardproject.di

import android.content.Context
import com.example.cardproject.data.repository.AuthRepository
import com.example.cardproject.data.repository.UserRepository
import com.example.cardproject.data.sync.RetrofitClient
import com.example.cardproject.data.sync.ServerApi
import com.example.cardproject.data.sync.SyncManager
import com.example.cardproject.database.AppDatabase
import com.example.cardproject.database.dao.CardDao
import com.example.cardproject.database.dao.DeckDao
import com.example.cardproject.database.dao.NoteDao
import com.example.cardproject.database.dao.ReviewLogDao
import com.example.cardproject.database.dao.SessionStatsDao
import com.example.cardproject.database.dao.TagDao
import com.example.cardproject.database.dao.UserDao
import com.example.cardproject.database.repository.CardRepository
import com.example.cardproject.database.repository.DeckRepository
import com.example.cardproject.database.repository.DeckStatsRepository
import com.example.cardproject.database.repository.NoteRepository
import com.example.cardproject.database.repository.ReviewLogRepository
import com.example.cardproject.database.repository.SessionStatsRepository
import com.example.cardproject.ml.TensorFlowLiteModel
import com.example.cardproject.network.AuthInterceptor
import com.example.cardproject.utils.SessionManager
import com.example.cardproject.utils.TokenManager
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
        cardRepository: CardRepository,
        tokenManager: TokenManager
    ): DeckRepository {
        return DeckRepository(deckDao, tagDao, cardRepository, tokenManager)
    }

    @Provides
    @Singleton
    fun provideNoteRepository(
        noteDao: NoteDao,
        tagDao: TagDao,
        tokenManager: TokenManager  // 🔥 ДОБАВЛЯЕМ
    ): NoteRepository {
        return NoteRepository(noteDao, tagDao, tokenManager)  // 🔥 ПЕРЕДАЁМ
    }

    @Provides
    @Singleton
    fun provideSessionStatsRepository(sessionStatsDao: SessionStatsDao): SessionStatsRepository {
        return SessionStatsRepository(sessionStatsDao)
    }


    @Provides
    @Singleton
    fun provideReviewLogDao(database: AppDatabase): ReviewLogDao = database.reviewLogDao()
    @Provides
    @Singleton
    fun provideDeckStatsRepository(
        deckDao: com.example.cardproject.database.dao.DeckDao,
        cardDao: com.example.cardproject.database.dao.CardDao,
        sessionStatsDao: com.example.cardproject.database.dao.SessionStatsDao,
        reviewLogDao: com.example.cardproject.database.dao.ReviewLogDao  // Добавили
    ): DeckStatsRepository {
        return DeckStatsRepository(deckDao, cardDao, sessionStatsDao, reviewLogDao)
    }

    @Provides
    @Singleton
    fun provideReviewLogRepository(
        reviewLogDao: com.example.cardproject.database.dao.ReviewLogDao
    ): ReviewLogRepository {
        return ReviewLogRepository(reviewLogDao)
    }

    @Provides
    @Singleton
    fun provideUserDao(appDatabase: AppDatabase): UserDao {
        return appDatabase.userDao()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        api: ServerApi,
        tokenManager: TokenManager
    ): AuthRepository {
        return AuthRepository(api, tokenManager)
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        reviewLogRepository: ReviewLogRepository,
        @ApplicationContext context: Context,
        tfliteModel: TensorFlowLiteModel,
        tokenManager: TokenManager,
        api: ServerApi
    ): SyncManager {
        return SyncManager(reviewLogRepository, context, tfliteModel, tokenManager)
    }

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): AuthInterceptor {
        return AuthInterceptor(tokenManager)
    }

    @Provides
    @Singleton
    fun provideServerApi(authInterceptor: AuthInterceptor): ServerApi {
        return RetrofitClient.getApi(authInterceptor)
    }


    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        tokenManager: TokenManager
    ): UserRepository {
        return UserRepository(userDao, tokenManager)
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        tokenManager: TokenManager,
        userRepository: UserRepository
    ): SessionManager {
        return SessionManager(tokenManager, userRepository)
    }
}