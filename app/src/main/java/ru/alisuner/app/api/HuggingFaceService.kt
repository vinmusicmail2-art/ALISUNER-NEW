package ru.alisuner.app.api

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Hugging Face Inference API для генерации музыки.
 * Использует модель facebook/musicgen-small (бесплатно).
 */
object HuggingFaceService {
    private const val BASE_URL = "https://api-inference.huggingface.co/"

    /**
     * Создаёт клиент для Hugging Face API.
     * @param apiToken Опциональный токен для приоритетного доступа (можно получить бесплатно на huggingface.co)
     */
    fun create(apiToken: String? = null): HuggingFaceApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)  // 3 минуты на генерацию
            .writeTimeout(60, TimeUnit.SECONDS)
            .apply {
                if (apiToken != null) {
                    addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer $apiToken")
                            .build()
                        chain.proceed(request)
                    }
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HuggingFaceApi::class.java)
    }
}

interface HuggingFaceApi {
    /**
     * Генерирует музыку по текстовому описанию.
     * Модель: facebook/musicgen-small
     * Возвращает WAV файл.
     */
    @POST("models/facebook/musicgen-small")
    suspend fun generateMusic(@Body request: MusicGenRequest): Response<ResponseBody>

    /**
     * Альтернативная модель (лучше качество, но медленнее).
     */
    @POST("models/facebook/musicgen-medium")
    suspend fun generateMusicMedium(@Body request: MusicGenRequest): Response<ResponseBody>
}

/**
 * Запрос для генерации музыки.
 *
 * @param inputs Текстовое описание музыки (например: "acoustic guitar, folk, calm melody")
 * @param parameters Параметры генерации
 */
data class MusicGenRequest(
    val inputs: String,
    val parameters: MusicGenParameters = MusicGenParameters()
)

data class MusicGenParameters(
    /**
     * Длительность в токенах (256 токенов ≈ 8 секунд).
     * Максимум: 1500 токенов ≈ 30 секунд.
     */
    val max_new_tokens: Int = 256,

    /**
     * Использовать sampling (рекомендуется true для разнообразия).
     */
    val do_sample: Boolean = true,

    /**
     * Температура (0.0-1.0). Выше = более креативно, но менее предсказуемо.
     */
    val temperature: Float = 1.0f,

    /**
     * Top-k sampling. Ограничивает выбор k наиболее вероятных токенов.
     */
    val top_k: Int = 250,

    /**
     * Top-p sampling. Альтернатива top-k.
     */
    val top_p: Float = 0.0f
)
