package ru.alisuner.app.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface SunoApi {
    @POST("api/v1/generate")
    suspend fun generate(@Body request: SunoGenerateRequest): SunoGenerateResponse

    @POST("api/v1/generate/upload-cover")
    suspend fun uploadCover(@Body request: SunoUploadCoverRequest): SunoGenerateResponse

    @GET("api/v1/generate/record-info")
    suspend fun getRecordInfo(@Query("taskId") taskId: String): SunoRecordInfoResponse
}

/**
 * API для загрузки файлов на файловый сервер Suno.
 * Базовый URL: https://sunoapiorg.redpandaai.co
 */
interface SunoFileUploadApi {
    @Multipart
    @POST("api/file-stream-upload")
    suspend fun uploadFileStream(
        @Part file: MultipartBody.Part,
        @Part("uploadPath") uploadPath: RequestBody,
        @Part("fileName") fileName: RequestBody
    ): SunoFileUploadResponse
}

// ── Запросы ──

data class SunoGenerateRequest(
    val prompt: String,
    val customMode: Boolean = false,
    val instrumental: Boolean = false,
    val model: String = "V4_5ALL",
    val callBackUrl: String = ""
)

data class SunoUploadCoverRequest(
    val uploadUrl: String,
    val prompt: String,
    val customMode: Boolean = false,
    val instrumental: Boolean = false,
    val model: String = "V4_5ALL",
    val callBackUrl: String = ""
)

// ── Ответы ──

data class SunoGenerateResponse(
    val code: Int,
    val data: SunoTaskData?
)

data class SunoTaskData(
    val taskId: String?
)

data class SunoRecordInfoResponse(
    val code: Int,
    val data: SunoRecordData?
)

data class SunoRecordData(
    val taskId: String?,
    val status: String?,
    val response: SunoRecordResponse?
)

data class SunoRecordResponse(
    val status: String?,
    val sunoData: List<SunoTrackData>?
)

data class SunoTrackData(
    val id: String?,
    @SerializedName("audio_url") val audioUrl: String?,
    val title: String?
)

// ── Ответ загрузки файла ──

data class SunoFileUploadResponse(
    val success: Boolean?,
    val code: Int?,
    val msg: String?,
    val data: SunoFileUploadData?
)

data class SunoFileUploadData(
    val fileId: String?,
    val fileName: String?,
    val originalName: String?,
    val fileSize: Long?,
    val mimeType: String?,
    val uploadPath: String?,
    val fileUrl: String?,
    val downloadUrl: String?,
    val uploadTime: String?,
    val expiresAt: String?
)
