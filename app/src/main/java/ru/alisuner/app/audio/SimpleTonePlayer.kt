package ru.alisuner.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

/**
 * Простой генератор чистого тона (синусоида) для ручного режима тюнера.
 *
 * Воспроизводит ровный, длинный звук без вибраций, эффектов и затухания —
 * идеально для настройки гитары на слух.
 */
class SimpleTonePlayer(
    private val sampleRate: Int = 44100
) {
    private var audioTrack: AudioTrack? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPlaying = false

    /** Громкость (0.0–1.0) — максимум */
    private val volume = 1.0f

    /** Множитель громкости для усиления (clipping) */
    private val boostFactor = 8.0

    private val channelMask = AudioFormat.CHANNEL_OUT_STEREO
    private val channelCount = 2

    /** Длительность тона в секундах */
    private val durationSeconds = 8.0

    /**
     * Воспроизводит чистую синусоиду заданной частоты.
     * Плавная атака (fade-in 50 мс) и затухание (fade-out 100 мс) в конце,
     * чтобы избежать щелчков.
     */
    fun play(frequencyHz: Double, onComplete: (() -> Unit)? = null) {
        stop()

        val numSamples = (sampleRate * durationSeconds).toInt()
        val pcm = ShortArray(numSamples * channelCount)

        val fadeInSamples = (sampleRate * 0.05).toInt()   // 50 мс
        val fadeOutSamples = (sampleRate * 0.10).toInt()   // 100 мс
        val fadeOutStart = numSamples - fadeOutSamples

        for (i in 0 until numSamples) {
            // Чистая синусоида
            val sample = sin(2.0 * PI * frequencyHz * i / sampleRate)

            // Огибающая: плавная атака и затухание
            val envelope = when {
                i < fadeInSamples -> i.toDouble() / fadeInSamples
                i >= fadeOutStart -> (numSamples - i).toDouble() / fadeOutSamples
                else -> 1.0
            }

            // Усиление + клиппинг: даёт заметно более громкий тон на телефоне
            val raw = sample * envelope * volume * boostFactor
            val clipped = raw.coerceIn(-1.0, 1.0)
            val value = (clipped * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            val idx = i * channelCount
            pcm[idx] = value
            pcm[idx + 1] = value
        }

        playBuffer(pcm, onComplete)
    }

    private fun playBuffer(pcmSamples: ShortArray, onComplete: (() -> Unit)? = null) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, pcmSamples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val frameCount = pcmSamples.size / channelCount
        audioTrack?.write(pcmSamples, 0, pcmSamples.size)
        audioTrack?.setNotificationMarkerPosition(frameCount)
        audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                if (track != null && audioTrack === track) {
                    isPlaying = false
                    audioTrack = null
                    runCatching { track.stop() }
                    runCatching { track.release() }
                    onComplete?.let { callback -> mainHandler.post(callback) }
                }
            }
            override fun onPeriodicNotification(track: AudioTrack?) {}
        })

        isPlaying = true
        audioTrack?.play()
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
        } catch (_: IllegalStateException) {
            // Игнорируем, если уже остановлен
        }
        audioTrack?.release()
        audioTrack = null
    }

    fun isActive(): Boolean = isPlaying

    /**
     * Воспроизводит короткий бип-сигнал (подтверждение настройки).
     * Два коротких сигнала на 1000 Гц с паузой между ними.
     */
    fun playBeep(onComplete: (() -> Unit)? = null) {
        stop()

        val beepFreq = 1000.0
        val beepDuration = 0.12  // 120 мс каждый бип
        val pauseDuration = 0.08 // 80 мс пауза
        val totalDuration = beepDuration * 2 + pauseDuration
        val numSamples = (sampleRate * totalDuration).toInt()
        val pcm = ShortArray(numSamples * channelCount)

        val beep1End = (sampleRate * beepDuration).toInt()
        val pauseEnd = beep1End + (sampleRate * pauseDuration).toInt()
        val beep2End = numSamples

        val fadeLen = (sampleRate * 0.005).toInt() // 5 мс fade

        for (i in 0 until numSamples) {
            val inBeep = (i < beep1End) || (i >= pauseEnd && i < beep2End)
            if (!inBeep) {
                val idx = i * channelCount
                pcm[idx] = 0
                pcm[idx + 1] = 0
                continue
            }

            val sample = sin(2.0 * PI * beepFreq * i / sampleRate)

            // Fade in/out внутри каждого бипа
            val localPos = if (i < beep1End) i else i - pauseEnd
            val localLen = (sampleRate * beepDuration).toInt()
            val envelope = when {
                localPos < fadeLen -> localPos.toDouble() / fadeLen
                localPos >= localLen - fadeLen -> (localLen - localPos).toDouble() / fadeLen
                else -> 1.0
            }

            val raw = (sample * envelope * 0.7).coerceIn(-1.0, 1.0)
            val value = (raw * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            val idx = i * channelCount
            pcm[idx] = value
            pcm[idx + 1] = value
        }

        playBuffer(pcm, onComplete)
    }
}
