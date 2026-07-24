// app/src/main/java/com/example/cardproject/data/sync/RetrofitClient.kt
package com.example.cardproject.data.sync

import com.example.cardproject.network.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
object RetrofitClient {

    // ============================================================
    // БАЗОВЫЙ URL (изменить под свои нужды)
    // ============================================================

    // Для эмулятора Android
    private const val BASE_URL = "http://10.0.2.2:8000/"

    // Для реального телефона в той же Wi-Fi сети (замените на свой IP)
    // private const val BASE_URL = "http://192.168.1.100:8000/"

    // Для продакшена на Render
    // private const val BASE_URL = "https://your-app-name.onrender.com/"

    // ============================================================
    // НАСТРОЙКИ
    // ============================================================

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Всегда включаем логирование для отладки
        level = HttpLoggingInterceptor.Level.BODY
    }

    fun getClient(authInterceptor: AuthInterceptor? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        authInterceptor?.let {
            builder.addInterceptor(it)
        }

        return builder.build()
    }

    fun getApi(authInterceptor: AuthInterceptor? = null): ServerApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(getClient(authInterceptor))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ServerApi::class.java)
    }
}