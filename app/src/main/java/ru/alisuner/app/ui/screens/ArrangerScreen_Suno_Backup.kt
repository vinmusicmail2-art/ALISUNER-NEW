package ru.alisuner.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.alisuner.app.api.SunoService
import ru.alisuner.app.api.SunoUploadCoverRequest
import ru.alisuner.app.data.PreferencesManager
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Константы
private const val MAX_FILE_SIZE_MB = 50L
private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024
private const val MAX_POLL_ATTEMPTS = 100  // 5 минут
private const val POLL_INTERVAL_MS = 3000L

/**
 * Аранжировщик с Suno API.
 *
 * Возможности:
 * - Генерация трека по текстовому описанию
 * - Запись мелодии голосом через микрофон (WAV)
 * - Загрузка аудиофайла из файлового менеджера / диктофона
 * - Превью выбранного файла с возможностью удаления (крестик)
 * - API ключ скрыт под иконкой "код для Suno"
 * - Увеличенные таймауты для стабильной работы
 */
@Composable
fun ArrangerScreenSunoBackup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Сохранение настроек ──
    val prefsManager = remember { PreferencesManager(context) }

    // ── API ключ (загружается из зашифрованного хранилища) ──
    var apiKey by remember { mutableStateOf(prefsManager.sunoApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeyExpanded by remember { mutableStateOf(false) }

    // ── Промпт ──
    var melodyPrompt by remember { mutableStateOf("") }

    // ── Статус ──
    var status by remember { mutableStateOf("") }
    var audioUrl by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }

    // ── Разрешение на микрофон ──
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // ── Запись голоса ──
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordedFilePath by remember { mutableStateOf<String?>(null) }
    var recordedFileName by remember { mutableStateOf<String?>(null) }

    // ── Загрузка файла ──
    var uploadedFileUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedFileName by remember { mutableStateOf<String?>(null) }

    // Лаунчер для выбора аудиофайла
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            uploadedFileUri = it
            // Получаем имя файла
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        uploadedFileName = c.getString(nameIndex)
                    }
                }
            }
            if (uploadedFileName == null) {
                uploadedFileName = it.lastPathSegment ?: "audio_file"
            }
        }
    }

    // ── Функция записи WAV ──
    fun startRecording() {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        isRecordingVoice = true
        recordedFilePath = null
        recordedFileName = null
        scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val bufSize = maxOf(
                AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2,
                8192
            )

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                withContext(Dispatchers.Main) {
                    status = "Ошибка: не удалось инициализировать микрофон"
                    isRecordingVoice = false
                }
                recorder.release()
                return@launch
            }

            val outputFile = File(context.cacheDir, "melody_recording_${System.currentTimeMillis()}.wav")
            val fos = FileOutputStream(outputFile)

            // Записываем WAV заголовок (заполним размеры позже)
            val wavHeader = ByteArray(44)
            fos.write(wavHeader)

            recorder.startRecording()
            val buffer = ShortArray(bufSize / 2)
            var totalSamplesWritten = 0L

            while (isRecordingVoice) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val byteBuffer = ByteBuffer.allocate(read * 2)
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until read) {
                        byteBuffer.putShort(buffer[i])
                    }
                    fos.write(byteBuffer.array())
                    totalSamplesWritten += read
                }
            }

            recorder.stop()
            recorder.release()
            fos.close()

            // Обновляем WAV заголовок с правильными размерами
            val dataSize = totalSamplesWritten * 2  // 16-bit = 2 байта на семпл
            val raf = RandomAccessFile(outputFile, "rw")
            raf.seek(0)

            val header = ByteBuffer.allocate(44)
            header.order(ByteOrder.LITTLE_ENDIAN)
            // RIFF chunk
            header.put("RIFF".toByteArray())
            header.putInt((36 + dataSize).toInt())
            header.put("WAVE".toByteArray())
            // fmt sub-chunk
            header.put("fmt ".toByteArray())
            header.putInt(16)            // Sub-chunk size
            header.putShort(1)           // PCM format
            header.putShort(1)           // Mono
            header.putInt(sampleRate)    // Sample rate
            header.putInt(sampleRate * 2) // Byte rate
            header.putShort(2)           // Block align
            header.putShort(16)          // Bits per sample
            // data sub-chunk
            header.put("data".toByteArray())
            header.putInt(dataSize.toInt())

            raf.write(header.array())
            raf.close()

            withContext(Dispatchers.Main) {
                recordedFilePath = outputFile.absolutePath
                recordedFileName = outputFile.name
                status = "Запись сохранена: ${outputFile.name}"
            }
        }
    }

    fun stopRecording() {
        isRecordingVoice = false
    }

    // ── Функция загрузки аудиофайла на сервер Suno ──
    // Возвращает URL загруженного файла или null при ошибке
    suspend fun uploadAudioFile(audioFile: File): String? {
        return withContext(Dispatchers.IO) {
            try {
                val uploadApi = SunoService.createFileUpload(apiKey)
                val mediaType = "audio/wav".toMediaTypeOrNull()
                val requestFile = audioFile.asRequestBody(mediaType)
                val filePart = MultipartBody.Part.createFormData(
                    "file", audioFile.name, requestFile
                )
                val uploadPath = "audio".toRequestBody("text/plain".toMediaTypeOrNull())
                val fileName = audioFile.name.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = uploadApi.uploadFileStream(filePart, uploadPath, fileName)
                if (response.code == 200 && response.data?.fileUrl != null) {
                    response.data.fileUrl
                } else {
                    withContext(Dispatchers.Main) {
                        status = "Ошибка загрузки файла: ${response.msg ?: "код ${response.code}"}"
                    }
                    null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status = "Ошибка загрузки файла: ${e.message}"
                }
                null
            }
        }
    }

    // ── Получение файла из Uri или записи ──
    fun getAudioFile(): File? {
        // Приоритет: записанный файл > загруженный файл
        recordedFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                if (file.length() > MAX_FILE_SIZE_BYTES) {
                    status = "Файл слишком большой (макс $MAX_FILE_SIZE_MB МБ)"
                    return null
                }
                return file
            }
        }
        uploadedFileUri?.let { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.wav")
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()

                if (tempFile.length() > MAX_FILE_SIZE_BYTES) {
                    tempFile.delete()
                    status = "Файл слишком большой (макс $MAX_FILE_SIZE_MB МБ)"
                    return null
                }

                return tempFile
            } catch (e: Exception) {
                status = "Ошибка загрузки файла: ${e.message}"
                return null
            }
        }
        return null
    }

    // ── Функция отправки в Suno ──
    fun sendToSuno() {
        if (apiKey.isBlank()) {
            status = "Введите API ключ (нажмите на иконку кода)"
            return
        }
        if (melodyPrompt.isBlank()) {
            status = "Опишите мелодию"
            return
        }
        isGenerating = true
        status = "Отправка в Suno..."
        generationJob = scope.launch {
            try {
                val api = SunoService.create(apiKey)
                val prompt = "Acoustic guitar, folk style, melodic: $melodyPrompt"
                val audioFile = getAudioFile()

                val resp = if (audioFile != null) {
                    // ── Режим с аудиофайлом: загружаем файл → получаем URL → upload-cover ──
                    status = "Загрузка аудиофайла на сервер..."
                    val uploadedUrl = uploadAudioFile(audioFile)
                    if (uploadedUrl == null) {
                        isGenerating = false
                        return@launch
                    }
                    status = "Файл загружен. Генерация трека..."
                    api.uploadCover(
                        SunoUploadCoverRequest(
                            uploadUrl = uploadedUrl,
                            prompt = prompt,
                            customMode = false,
                            instrumental = false
                        )
                    )
                } else {
                    // ── Режим без файла: только текстовый промпт ──
                    api.generate(
                        ru.alisuner.app.api.SunoGenerateRequest(
                            prompt = prompt,
                            customMode = false,
                            instrumental = false
                        )
                    )
                }

                if (resp.code == 200 && resp.data?.taskId != null) {
                    status = "Генерация... Ожидание ~60 сек"
                    var attempts = 0
                    while (attempts < MAX_POLL_ATTEMPTS) {
                        delay(POLL_INTERVAL_MS)
                        try {
                            val fetch = api.getRecordInfo(resp.data.taskId)
                            val recordData = fetch.data
                            val sunoData = recordData?.response?.sunoData
                            val done = sunoData?.firstOrNull()?.audioUrl
                            if (!done.isNullOrBlank()) {
                                audioUrl = done
                                status = "Готово!"
                                break
                            }
                        } catch (pollError: Exception) {
                            // Ошибка при опросе — продолжаем
                        }
                        status = "Генерация... ${(attempts + 1) * 3} сек"
                        attempts++
                    }
                    if (audioUrl == null) status = "Превышено время ожидания (${MAX_POLL_ATTEMPTS * POLL_INTERVAL_MS / 60000} мин)"
                } else if (resp.code == 401) {
                    status = "Ошибка: неверный API ключ. Получите ключ на sunoapi.org"
                } else {
                    status = "Ошибка: код ${resp.code}"
                }
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                status = when (code) {
                    401 -> "Ошибка 401: Неверный API ключ. Получите ключ на sunoapi.org"
                    404 -> "Ошибка 404: Эндпоинт не найден. Проверьте API ключ и баланс на sunoapi.org"
                    429 -> "Ошибка 429: Недостаточно кредитов. Пополните баланс на sunoapi.org"
                    else -> "Ошибка $code: ${e.message()}"
                }
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
            }
            isGenerating = false
        }
    }

    // ── Функция отмены генерации ──
    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        isGenerating = false
        status = "Генерация отменена"
    }

    // ═══════════════ UI ═══════════════

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Аранжировщик с Suno",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            "Опишите мелодию, напойте или загрузите аудио — получите трек через Suno AI",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ─── API ключ (скрытый под иконкой) ───
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Код для Suno",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { apiKeyExpanded = !apiKeyExpanded }) {
                        Text(
                            if (apiKeyExpanded) "Скрыть" else "Показать",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (apiKeyExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; prefsManager.sunoApiKey = it },
                        label = { Text("API ключ Suno") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showApiKey = !showApiKey }) {
                                Text(
                                    if (showApiKey) "Скрыть" else "Показать",
                                    fontSize = 11.sp
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Описание мелодии ───
        OutlinedTextField(
            value = melodyPrompt,
            onValueChange = { melodyPrompt = it },
            label = { Text("Опишите мелодию (текстом)") },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            placeholder = { Text("Например: Спокойная акустическая гитара, фолк, минорная мелодия") }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ─── Секция: Напеть мелодию ───
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isRecordingVoice) Color.Red else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Напеть мелодию",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isRecordingVoice) {
                        Button(
                            onClick = { startRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Записать")
                        }
                    } else {
                        Button(
                            onClick = { stopRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF95A5A6)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Остановить")
                        }
                    }
                }

                // Превью записанного файла
                recordedFileName?.let { name ->
                    Spacer(modifier = Modifier.height(8.dp))
                    FilePreviewChip(
                        fileName = name,
                        onDelete = {
                            recordedFilePath?.let { path ->
                                File(path).delete()
                            }
                            recordedFilePath = null
                            recordedFileName = null
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ─── Секция: Загрузить аудиофайл ───
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Загрузить аудиофайл",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { filePickerLauncher.launch("audio/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Из файлов")
                    }
                }

                // Превью загруженного файла
                uploadedFileName?.let { name ->
                    Spacer(modifier = Modifier.height(8.dp))
                    FilePreviewChip(
                        fileName = name,
                        onDelete = {
                            uploadedFileUri = null
                            uploadedFileName = null
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ─── Разрешение микрофона ───
        if (!hasPermission) {
            OutlinedButton(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Разрешить доступ к микрофону")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ─── Кнопка отправки ───
        Button(
            onClick = { sendToSuno() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isGenerating,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (isGenerating) "Генерация..." else "Отправить в Suno",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ─── Статус ───
        if (status.isNotBlank()) {
            Text(
                status,
                color = when {
                    status.startsWith("Ошибка") -> Color.Red
                    status == "Готово!" -> Color(0xFF2ECC71)
                    else -> Color.Gray
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // ─── Результат ───
        audioUrl?.let { url ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A3A2A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Ваш трек готов!",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF2ECC71),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(Intent.createChooser(i, "Открыть аудио"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Открыть / Скачать")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─── Компонент превью файла с крестиком удаления ───

@Composable
private fun FilePreviewChip(
    fileName: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A252F)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = Color(0xFF3498DB),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                fileName,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Удалить",
                    tint = Color(0xFFE74C3C),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
