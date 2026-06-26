package ru.alisuner.app.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object SunoService {
    private const val BASE_URL = "https://api.sunoapi.org/"
    private const val FILE_UPLOAD_BASE_URL = "https://sunoapiorg.redpandaai.co/"

    /**
     * Создаёт клиент для основного Suno API (генерация музыки, опрос статуса).
     */
    fun create(apiKey: String): SunoApi {
        val client = buildClient(apiKey)
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SunoApi::class.java)
    }

    /**
     * Создаёт клиент для File Upload API (загрузка аудиофайлов на сервер Suno).
     * Базовый URL: https://sunoapiorg.redpandaai.co
     */
    fun createFileUpload(apiKey: String): SunoFileUploadApi {
        val client = buildClient(apiKey, readTimeout = 120)
        return Retrofit.Builder()
            .baseUrl(FILE_UPLOAD_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SunoFileUploadApi::class.java)
    }

    private fun buildClient(apiKey: String, readTimeout: Long = 60): OkHttpClient {
        val authInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            chain.proceed(request)
        }
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
