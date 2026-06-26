package ru.alisuner.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Plays bundled FreePats acoustic guitar WAV samples as single strummed chords.
 */
class AcousticGuitarSampler(
    context: Context,
    private val sampleRate: Int = 44100
) {
    private val appContext = context.applicationContext
    private var audioTrack: AudioTrack? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private val cachedSamples: List<GuitarSample> by lazy { loadSamples() }

    fun playChord(
        frequencies: List<Double>,
        durationSeconds: Double = 3.0,
        onComplete: (() -> Unit)? = null
    ) {
        stop()
        if (frequencies.isEmpty()) return

        val guitarSamples = cachedSamples
        if (guitarSamples.isEmpty()) return

        val strumOrder = frequencies.reversed()
        val strumDelaySamples = (sampleRate * 0.028).toInt()
        val totalSamples = (sampleRate * durationSeconds).toInt() + strumDelaySamples * (strumOrder.size - 1)
        val mix = DoubleArray(totalSamples)

        strumOrder.forEachIndexed { index, frequency ->
            addSampledNote(
                mix = mix,
                startSample = index * strumDelaySamples,
                targetMidi = midiFromFrequency(frequency),
                velocity = (0.62 / strumOrder.size) * (1.0 - index * 0.035),
                guitarSamples = guitarSamples
            )
        }

        val peak = mix.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001) ?: 1.0
        val fadeOutSamples = (sampleRate * 0.08).toInt()
        val fadeOutStart = (totalSamples - fadeOutSamples).coerceAtLeast(0)
        val pcm = ShortArray(totalSamples) { index ->
            val fade = if (index >= fadeOutStart && fadeOutSamples > 0) {
                (totalSamples - index).toDouble() / fadeOutSamples
            } else {
                1.0
            }
            val softened = tanh(mix[index] / peak * 1.25) * 0.86 * fade.coerceIn(0.0, 1.0)
            (softened * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        playBuffer(pcm, onComplete)
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
        } catch (_: IllegalStateException) {
            // Track can already be stopped by the completion callback.
        }
        audioTrack?.release()
        audioTrack = null
    }

    fun isActive(): Boolean = isPlaying

    private fun addSampledNote(
        mix: DoubleArray,
        startSample: Int,
        targetMidi: Int,
        velocity: Double,
        guitarSamples: List<GuitarSample>
    ) {
        if (startSample !in mix.indices) return

        val sample = guitarSamples.minBy { abs(it.midiNote - targetMidi) }
        val pitchRatio = 2.0.pow((targetMidi - sample.midiNote) / 12.0)
        val sourceStep = pitchRatio * sample.sourceRate / sampleRate
        var sourcePosition = 0.0
        var outputIndex = startSample

        while (outputIndex < mix.size && sourcePosition < sample.pcm.lastIndex) {
            val sourceIndex = sourcePosition.toInt()
            val fraction = sourcePosition - sourceIndex
            val current = sample.pcm[sourceIndex]
            val next = sample.pcm[sourceIndex + 1]
            val interpolated = current + (next - current) * fraction
            val fadeIn = (outputIndex - startSample).coerceAtMost(256) / 256.0
            mix[outputIndex] += interpolated * velocity * fadeIn
            sourcePosition += sourceStep
            outputIndex++
        }
    }

    private fun loadSamples(): List<GuitarSample> {
        return appContext.assets.list("freepats_guitar")
            .orEmpty()
            .filter { it.endsWith(".wav", ignoreCase = true) }
            .map(::readWavSample)
            .sortedBy { it.midiNote }
    }

    private fun readWavSample(fileName: String): GuitarSample {
        val bytes = appContext.assets.open("freepats_guitar/$fileName").use { it.readBytes() }
        var offset = 12
        var sourceRate = 44100
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = readLittleEndianInt(bytes, offset + 4)
            val chunkDataOffset = offset + 8
            when (chunkId) {
                "fmt " -> {
                    channels = readLittleEndianShort(bytes, chunkDataOffset + 2).coerceAtLeast(1)
                    sourceRate = readLittleEndianInt(bytes, chunkDataOffset + 4)
                    bitsPerSample = readLittleEndianShort(bytes, chunkDataOffset + 14)
                }
                "data" -> {
                    dataOffset = chunkDataOffset
                    dataSize = chunkSize
                }
            }
            offset = chunkDataOffset + chunkSize + (chunkSize and 1)
        }

        require(dataOffset >= 0 && bitsPerSample == 16) { "Unsupported WAV sample: $fileName" }

        val frameCount = dataSize / (channels * 2)
        val pcm = DoubleArray(frameCount)
        for (frame in 0 until frameCount) {
            var sum = 0
            for (channel in 0 until channels) {
                val sampleOffset = dataOffset + (frame * channels + channel) * 2
                val raw = readLittleEndianShort(bytes, sampleOffset)
                val signed = if (raw > Short.MAX_VALUE) raw - 65536 else raw
                sum += signed
            }
            pcm[frame] = (sum / channels.toDouble()) / Short.MAX_VALUE
        }
        return GuitarSample(midiFromFileName(fileName), sourceRate, pcm)
    }

    private fun playBuffer(pcmSamples: ShortArray, onComplete: (() -> Unit)? = null) {
        val minBufferSize = AudioTrack.getMinBufferSize(
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
            .setBufferSizeInBytes(maxOf(minBufferSize, pcmSamples.size * 2))
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
                    onComplete?.let { callback -> mainHandler.post(callback) }
                }
            }

            override fun onPeriodicNotification(track: AudioTrack?) = Unit
        })
        isPlaying = true
        audioTrack?.play()
    }

    private fun midiFromFrequency(frequencyHz: Double): Int {
        return (69 + 12 * (ln(frequencyHz / 440.0) / ln(2.0))).roundToInt()
    }

    private fun midiFromFileName(fileName: String): Int {
        val match = Regex("""^([A-G])(#?)(-?\d+)\.wav$""").matchEntire(fileName)
            ?: error("Unknown guitar sample name: $fileName")
        val base = when (match.groupValues[1]) {
            "C" -> 0
            "D" -> 2
            "E" -> 4
            "F" -> 5
            "G" -> 7
            "A" -> 9
            else -> 11
        }
        val accidental = if (match.groupValues[2] == "#") 1 else 0
        val octave = match.groupValues[3].toInt()
        return (octave + 1) * 12 + base + accidental
    }

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readLittleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private data class GuitarSample(
        val midiNote: Int,
        val sourceRate: Int,
        val pcm: DoubleArray
    )
}
