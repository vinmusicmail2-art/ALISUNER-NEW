package ru.alisuner.app.audio
 
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
 
/**
 * Рекордер с микрофона. Поддерживает два режима:
 * - Обычный (MIC) для тюнера
 * - Высокочувствительный (VOICE_RECOGNITION) для распознавания аккордов —
 *   обходит системное шумоподавление и АРУ, даёт «сырой» сигнал с микрофона.
 *
 * @param sampleRate  частота дискретизации (44100 по умолчанию)
 * @param bufferSize  размер буфера в семплах (8192 — хорошая частотная резолюция для аккордов)
 * @param highSensitivity  если true — используется VOICE_RECOGNITION + программное усиление
 * @param gain  коэффициент усиления (1.0 = без усиления, 2.0-4.0 для тихих гитар)
 */
class AudioRecorder(
    private val sampleRate: Int = 44100,
    private val bufferSize: Int = 8192,
    private val highSensitivity: Boolean = false,
    private val gain: Float = 1.0f
) {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRecording = false
 
    @SuppressLint("MissingPermission")
    fun start(onSamples: (ShortArray) -> Unit): Boolean {
        if (isRecording) return true
 
        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) return false

        val bufSize = maxOf(bufferSize * 2, minBufSize * 2)
 
        // VOICE_RECOGNITION отключает системное шумоподавление и АРУ —
        // микрофон получает максимально чистый сигнал
        val audioSource = if (highSensitivity) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.MIC
        }
 
        audioRecord = try {
            AudioRecord(
                audioSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
        } catch (_: SecurityException) {
            return false
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return false
        }

        val record = audioRecord ?: return false
        try {
            record.startRecording()
        } catch (_: SecurityException) {
            stop()
            return false
        } catch (_: IllegalStateException) {
            stop()
            return false
        }

        isRecording = true
        recordingThread = Thread {
            val buffer = ShortArray(bufferSize)
            while (isRecording && audioRecord === record) {
                val read = try {
                    record.read(buffer, 0, buffer.size)
                } catch (_: IllegalStateException) {
                    break
                } catch (_: RuntimeException) {
                    break
                }
                if (read > 0) {
                    val samples = buffer.copyOf(read)
                    // Программное усиление сигнала
                    if (gain > 1.0f) {
                        for (i in samples.indices) {
                            val amplified = (samples[i] * gain).toInt()
                            samples[i] = amplified.coerceIn(
                                Short.MIN_VALUE.toInt(),
                                Short.MAX_VALUE.toInt()
                            ).toShort()
                        }
                    }
                    try {
                        onSamples(samples)
                    } catch (_: RuntimeException) {
                        break
                    }
                } else break
            }
        }.apply {
            name = "AlisunerAudioRecorder"
            isDaemon = true
            start()
        }

        return true
    }
 
    fun stop() {
        isRecording = false
        val record = audioRecord
        audioRecord = null
        try {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        } catch (_: IllegalStateException) {
            // Игнорируем, если уже остановлен
        }
        record?.release()
        recordingThread = null
    }
 
    fun isActive() = isRecording
}
