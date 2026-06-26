package ru.alisuner.app.audio
 
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
 
/**
 * Метроном с реальным воспроизведением звука (клик).
 * Генерирует короткий синусоидальный «клик» на каждый удар,
 * с акцентом (более высокий тон) на первую долю такта.
 */
class Metronome(
    initialBpm: Int,
    initialBeatsPerBar: Int = 4
) {
    var bpm: Int = initialBpm
        set(value) {
            field = value.coerceIn(40, 240)
            val wasRunning = isRunning
            if (isRunning) stop()
            if (wasRunning) start()
        }

    /** Количество долей в такте (2, 3, 4, 6) */
    var beatsPerBar: Int = initialBeatsPerBar
        set(value) {
            field = value
            val wasRunning = isRunning
            if (isRunning) stop()
            if (wasRunning) start()
        }
 
    /** Громкость от 0.0 до 1.0 */
    var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }
 
    private val handler = Handler(Looper.getMainLooper())
    private var tickCount = 0
    private var isRunning = false
    private var callback: ((beat: Int, isAccent: Boolean) -> Unit)? = null
 
    private val sampleRate = 44100
 
    // Заранее сгенерированные клики (звук удара палочек)
    private val accentClick: ShortArray = generateStickClick(isAccent = true)
    private val normalClick: ShortArray = generateStickClick(isAccent = false)
 
    private val tickIntervalMs: Long
        get() = (60_000.0 / bpm).toLong()
 
    fun setCallback(cb: (beat: Int, isAccent: Boolean) -> Unit) {
        callback = cb
    }
 
    /**
     * Генерирует короткий хлёсткий клик, похожий на удар барабанных палочек.
     * Смесь шума (атака) + высокочастотный тон с мгновенным затуханием.
     */
    private fun generateStickClick(isAccent: Boolean): ShortArray {
        val durationMs = if (isAccent) 25 else 18
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val rng = Random(42)
        val toneFreq = if (isAccent) 3500.0 else 2800.0
        val noiseAmount = if (isAccent) 0.6f else 0.5f

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Мгновенная атака (1 сэмпл), очень быстрое экспоненциальное затухание
            val envelope = exp(-t * 300.0)  // затухание за ~3 мс

            // Высокочастотный тон (щелчок палочки)
            val tone = sin(2.0 * Math.PI * toneFreq * t)
            // Второй обертон
            val overtone = sin(2.0 * Math.PI * toneFreq * 2.3 * t) * 0.4
            // Шумовая компонента (удар)
            val noise = (rng.nextFloat() * 2f - 1f) * noiseAmount

            val sample = (tone + overtone + noise) * envelope
            val value = (sample * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = value.toShort()
        }
        return samples
    }
 
    /**
     * Воспроизводит клик в отдельном потоке (неблокирующе)
     */
    private fun playClick(isAccent: Boolean) {
        val clickSamples = if (isAccent) accentClick else normalClick
        val scaledSamples = ShortArray(clickSamples.size) { i ->
            (clickSamples[i] * volume).toInt().toShort()
        }
 
        Thread {
            try {
                val minBufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBufSize, scaledSamples.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
 
                track.write(scaledSamples, 0, scaledSamples.size)
                track.play()
 
                // Ждём окончания воспроизведения и освобождаем ресурсы
                Thread.sleep((scaledSamples.size * 1000L / sampleRate) + 50)
                track.stop()
                track.release()
            } catch (_: Exception) {
                // Игнорируем ошибки воспроизведения
            }
        }.start()
    }
 
    /**
     * Акцентные доли для составных размеров (6/8 → акцент на 1 и 4).
     * Для простых размеров (2/4, 3/4, 4/4) — акцент только на 1-ю долю.
     */
    var accentBeats: Set<Int> = setOf(1)

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val beat = (tickCount % beatsPerBar) + 1
            val isAccent = beat in accentBeats
            playClick(isAccent)
            callback?.invoke(beat, isAccent)
            tickCount++
            handler.postDelayed(this, tickIntervalMs)
        }
    }
 
    fun start() {
        if (isRunning) return
        isRunning = true
        tickCount = 0
        handler.post(tickRunnable)
    }
 
    fun stop() {
        isRunning = false
        handler.removeCallbacks(tickRunnable)
    }
}
