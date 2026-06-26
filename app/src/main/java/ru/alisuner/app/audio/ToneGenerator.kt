package ru.alisuner.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*
import kotlin.random.Random

/**
 * Генератор реалистичного звука акустической гитары.
 *
 * Цепочка обработки:
 * 1. Karplus-Strong -- синтез щипковой струны с параметрами акустической гитары
 * 2. Body resonance -- имитация резонанса корпуса акустической гитары
 *    (усиление характерных частот деки: ~98 Гц, ~204 Гц, ~390 Гц)
 * 3. Мягкий high-shelf rolloff -- приглушение высоких частот для теплого тона
 * 4. Легкий room reverb -- естественная реверберация небольшого помещения
 *
 * Для аккордов используется даунстрок (strum от 6-й струны к 1-й):
 * каждая струна звучит с небольшой задержкой (~30 мс),
 * как при проведении медиатором/пальцами вниз по струнам.
 */
class ToneGenerator(
    private val sampleRate: Int = 44100
) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    // =============== Параметры звучания акустической гитары ===============

    /** Громкость общая (0.0-1.0) */
    private val masterVolume = 0.88f

    /** Room reverb: короткие задержки с малой обратной связью (маленькая комната) */
    private val reverbTaps = listOf(
        ReverbTap((sampleRate * 0.013).toInt(), 0.18f),   // ~13 мс -- ранние отражения стен
        ReverbTap((sampleRate * 0.029).toInt(), 0.12f),   // ~29 мс
        ReverbTap((sampleRate * 0.043).toInt(), 0.08f),   // ~43 мс
        ReverbTap((sampleRate * 0.061).toInt(), 0.05f)    // ~61 мс -- хвост
    )

    /** Резонансные частоты корпуса акустической гитары (дека + обечайка) */
    private val bodyResonances = listOf(
        BodyResonance(98.0, 0.12f),    // ~98 Гц -- основной резонанс деки
        BodyResonance(204.0, 0.08f),   // ~204 Гц -- второй резонанс
        BodyResonance(390.0, 0.05f)    // ~390 Гц -- верхний резонанс корпуса
    )

    /** Задержка между струнами при даунстроке (в семплах). ~30 мс -- естественное бряцание */
    private val strumDelaySamples = (sampleRate * 0.030).toInt()

    // =============== Одиночная нота ===============

    /**
     * Воспроизводит одну ноту акустической гитары.
     */
    fun play(frequencyHz: Double) {
        stop()
        val durationSeconds = 2.5
        val numSamples = (sampleRate * durationSeconds).toInt()

        // 1. Синтез Karplus-Strong (акустическая гитара)
        val raw = karplusStrongAcoustic(frequencyHz, numSamples)

        // 2. Цепочка акустических эффектов
        val processed = applyAcousticChain(raw, numSamples)

        playBuffer(processed)
    }

    // =============== Аккорд -- даунстрок (6-я -> 1-я струна) ===============

    /**
     * Воспроизводит аккорд даунстроком: от 6-й струны (толстая E2) к 1-й (тонкая E4).
     *
     * Входные частоты приходят в порядке: 1-я (тонкая) -> 6-я (толстая),
     * так как ChordFingering.getFrequencies() итерирует от E4 к E2.
     *
     * Для даунстрока реверсируем порядок: сначала толстые (6-я), потом тонкие (1-я).
     *
     * @param frequencies частоты нот аккорда (от 1-й тонкой к 6-й толстой)
     * @param durationSeconds общая длительность звучания
     */
    fun playChord(frequencies: List<Double>, durationSeconds: Double = 3.0) {
        stop()
        if (frequencies.isEmpty()) return

        // Даунстрок: 6 -> 5 -> 4 -> 3 -> 2 -> 1 (толстая -> тонкая)
        val strumOrder = frequencies.reversed()

        val totalDelay = strumDelaySamples * (strumOrder.size - 1)
        val numSamples = (sampleRate * durationSeconds).toInt() + totalDelay
        val mix = FloatArray(numSamples)

        // Генерируем каждую струну и смешиваем со сдвигом по времени
        val rng = Random(System.nanoTime())
        for ((index, freq) in strumOrder.withIndex()) {
            val noteSamples = (sampleRate * durationSeconds).toInt()
            val noteBuffer = karplusStrongAcoustic(freq, noteSamples)
            val offset = index * strumDelaySamples

            // Громкость каждой струны немного варьируется (естественность бряцания)
            val stringVolume = 1.0f / strumOrder.size * (0.9f + 0.2f * rng.nextFloat())

            for (i in 0 until noteSamples) {
                if (offset + i < numSamples) {
                    mix[offset + i] += noteBuffer[i] * stringVolume
                }
            }
        }

        // Цепочка акустических эффектов на микс
        val processed = applyAcousticChain(mix, numSamples)

        playBuffer(processed)
    }

    // =============== Karplus-Strong (акустическая гитара) ===============

    /**
     * Алгоритм Карплюса-Стронга, настроенный для звука акустической гитары:
     * - Фильтрованный начальный шум (мягкая атака, имитация удара пальцем)
     * - Brightness = 0.4 (теплый тон, приглушенные высокие)
     * - Decay = 0.996 (умеренный сустейн стальных акустических струн)
     * - Двухкомпонентная огибающая (быстрая атака + плавный хвост)
     */
    private fun karplusStrongAcoustic(frequencyHz: Double, numSamples: Int): FloatArray {
        val period = (sampleRate / frequencyHz).toInt()
        if (period < 2) return FloatArray(numSamples)

        // Буфер-задержка (ring buffer)
        val delayLine = FloatArray(period)
        val rng = Random(System.nanoTime())

        // Инициализация: фильтрованный шум (имитация удара пальцем по струне)
        // Пропускаем белый шум через однополюсный low-pass для мягкой атаки
        var prevNoise = 0f
        for (i in delayLine.indices) {
            val whiteNoise = rng.nextFloat() * 2f - 1f
            val filtered = 0.3f * whiteNoise + 0.7f * prevNoise
            delayLine[i] = filtered
            prevNoise = filtered
        }

        val output = FloatArray(numSamples)
        var writePos = 0

        // Параметры для акустической гитары
        val decay = 0.996f       // Умеренный сустейн (акустика затухает быстрее электрогитары)
        val brightness = 0.4f    // Теплый, мягкий тон (без резкости электрогитары)

        for (i in 0 until numSamples) {
            val readPos = writePos
            val nextPos = (writePos + 1) % period

            // Low-pass фильтр: среднее текущего и следующего семпла
            val filtered = (delayLine[readPos] + delayLine[nextPos]) * 0.5f
            // Смешиваем прямой и фильтрованный сигнал
            val sample = brightness * delayLine[readPos] + (1f - brightness) * filtered

            output[i] = sample

            // Записываем обратно с затуханием
            delayLine[writePos] = filtered * decay

            writePos = nextPos
        }

        // Огибающая: мягкая атака (5 мс) + естественное двухкомпонентное затухание
        val attackSamples = (sampleRate * 0.005).toInt()  // 5 мс -- мягкая атака пальцем
        for (i in 0 until numSamples) {
            val envelope = when {
                i < attackSamples -> i.toFloat() / attackSamples
                else -> {
                    val t = (i - attackSamples).toFloat() / sampleRate
                    // Быстрое начальное затухание + медленный теплый хвост
                    0.7f * exp(-t * 2.0f) + 0.3f * exp(-t * 0.8f)
                }
            }
            output[i] *= envelope
        }

        return output
    }

    // =============== Цепочка эффектов (акустическая гитара) ===============

    /**
     * Применяет цепочку эффектов для акустической гитары:
     * body resonance -> high-shelf rolloff -> room reverb -> master volume
     *
     * Без overdrive -- чистый акустический звук.
     */
    private fun applyAcousticChain(input: FloatArray, numSamples: Int): ShortArray {
        val buffer = input.copyOf()

        // 1. Резонанс корпуса гитары (дека + обечайка)
        applyBodyResonance(buffer, numSamples)

        // 2. Мягкий high-shelf rolloff (приглушение высоких для теплого тона)
        applyHighShelfRolloff(buffer, numSamples)

        // 3. Легкий room reverb (естественная реверберация)
        applyRoomReverb(buffer, numSamples)

        // 4. Финальная огибающая (плавный fade-out в конце)
        val fadeOutLen = (sampleRate * 0.10).toInt()
        val fadeOutStart = numSamples - fadeOutLen
        for (i in 0 until numSamples) {
            if (i >= fadeOutStart) {
                val fade = (numSamples - i).toFloat() / fadeOutLen
                buffer[i] *= fade.coerceIn(0f, 1f)
            }
        }

        // Конвертируем в PCM16
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val sample = (buffer[i] * masterVolume * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[i] = sample.toShort()
        }

        return pcm
    }

    /**
     * Имитация резонанса корпуса акустической гитары.
     *
     * Корпус гитары усиливает определенные частоты (формантные резонансы деки).
     * Реализуется через набор резонаторных biquad bandpass фильтров.
     * Каждый резонанс добавляет немного энергии на своей частоте.
     */
    private fun applyBodyResonance(buffer: FloatArray, numSamples: Int) {
        for (resonance in bodyResonances) {
            // Коэффициенты biquad bandpass фильтра
            val omega = 2.0 * Math.PI * resonance.freqHz / sampleRate
            val cosOmega = cos(omega).toFloat()
            val sinOmega = sin(omega).toFloat()
            val q = 4.0f  // Добротность (узкий резонанс, характерный для деки)
            val alpha = sinOmega / (2.0f * q)

            val b0 = alpha
            val b2 = -alpha
            val a0 = 1f + alpha
            val a1 = -2f * cosOmega
            val a2 = 1f - alpha

            // Нормализуем коэффициенты
            val nb0 = b0 / a0
            val nb2 = b2 / a0
            val na1 = a1 / a0
            val na2 = a2 / a0

            var x1 = 0f; var x2 = 0f
            var y1 = 0f; var y2 = 0f

            for (i in 0 until numSamples) {
                val x0 = buffer[i]
                val y0 = nb0 * x0 + nb2 * x2 - na1 * y1 - na2 * y2

                // Параллельное смешивание: оригинал + резонанс
                buffer[i] = x0 + y0 * resonance.gain

                x2 = x1; x1 = x0
                y2 = y1; y1 = y0
            }
        }
    }

    /**
     * Мягкий high-shelf rolloff: приглушает высокие частоты (>3 кГц).
     * Придает звуку теплый, мягкий тон, характерный для акустической гитары.
     * Реализуется однополюсным low-pass фильтром, смешанным с оригиналом.
     */
    private fun applyHighShelfRolloff(buffer: FloatArray, numSamples: Int) {
        val cutoffFreq = 3000.0
        val rc = 1.0 / (2.0 * Math.PI * cutoffFreq)
        val dt = 1.0 / sampleRate
        val lpAlpha = (dt / (rc + dt)).toFloat()

        // 70% оригинал + 30% отфильтрованный -- мягкое приглушение верхов
        val mixDry = 0.70f
        val mixWet = 0.30f

        var prev = buffer[0]
        for (i in 1 until numSamples) {
            val filtered = prev + lpAlpha * (buffer[i] - prev)
            buffer[i] = buffer[i] * mixDry + filtered * mixWet
            prev = filtered
        }
    }

    /**
     * Room reverb: легкая естественная реверберация маленького помещения.
     * Короткие задержки с малой обратной связью -- звук не размазывается,
     * но приобретает естественное пространство.
     */
    private fun applyRoomReverb(buffer: FloatArray, numSamples: Int) {
        val delayBuffers = reverbTaps.map { FloatArray(it.delaySamples) }
        val delayPositions = IntArray(reverbTaps.size)

        val dry = buffer.copyOf()

        for (i in 0 until numSamples) {
            var wet = 0f
            for (t in reverbTaps.indices) {
                val tap = reverbTaps[t]
                val pos = delayPositions[t]

                val delayed = delayBuffers[t][pos]
                wet += delayed * tap.feedback

                // Записываем текущий + немного рецикла для диффузии
                delayBuffers[t][pos] = dry[i] + delayed * tap.feedback * 0.2f

                delayPositions[t] = (pos + 1) % tap.delaySamples
            }

            // 80% сухой, 20% влажный -- легкая, естественная реверберация
            buffer[i] = dry[i] * 0.80f + wet * 0.20f
        }
    }

    // =============== Воспроизведение ===============

    private fun playBuffer(pcmSamples: ShortArray) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, pcmSamples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(pcmSamples, 0, pcmSamples.size)
        audioTrack?.setNotificationMarkerPosition(pcmSamples.size)
        audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                if (track != null && audioTrack === track) {
                    isPlaying = false
                    audioTrack = null
                    runCatching { track.stop() }
                    runCatching { track.release() }
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
            // Игнорируем, если трек уже остановлен
        }
        audioTrack?.release()
        audioTrack = null
    }

    fun isActive(): Boolean = isPlaying

    // =============== Вспомогательные структуры ===============

    private data class ReverbTap(
        val delaySamples: Int,
        val feedback: Float
    )

    private data class BodyResonance(
        val freqHz: Double,      // Резонансная частота корпуса (Гц)
        val gain: Float          // Громкость резонанса (0.0-1.0)
    )
}
