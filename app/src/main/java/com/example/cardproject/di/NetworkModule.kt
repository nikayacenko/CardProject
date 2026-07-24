// app/src/main/java/com/example/cardproject/di/NetworkModule.kt
package com.example.cardproject.di

import com.example.cardproject.data.sync.RetrofitClient
import com.example.cardproject.data.sync.ServerApi
import com.example.cardproject.network.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

//    @Provides
//    @Singleton
//    fun provideServerApi(
//        authInterceptor: AuthInterceptor  // 🔥 ДОБАВЛЯЕМ ЗАВИСИМОСТЬ
//    ): ServerApi {
//        return RetrofitClient.getApi(authInterceptor)  // 🔥 ИСПОЛЬЗУЕМ getApi()
//    }
}