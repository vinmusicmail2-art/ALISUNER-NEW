package ru.alisuner.app.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Instrument-oriented pitch detector.
 *
 * The detector first estimates raw frequency with NSDF/MPM-style normalized
 * autocorrelation, then snaps the result to the closest string in the selected
 * tuning. This keeps the tuner from jumping between chromatic notes when an
 * instrument string has strong overtones.
 */
object PitchDetector {

    private const val SAMPLE_RATE = 44100
    private const val ABSOLUTE_MIN_FREQ = 35.0
    private const val ABSOLUTE_MAX_FREQ = 900.0
    private const val MIN_RMS = 0.0035f

    data class TuningProfile(
        val id: String,
        val instrumentName: String,
        val tuningName: String,
        val strings: List<TuningString>
    ) {
        val displayName: String = "$instrumentName - $tuningName"
        val notesText: String = strings.joinToString(" ") { it.label }
    }

    data class TuningString(
        val noteName: String,
        val octave: Int,
        val frequencyAtA440: Double,
        val stringNumber: Int
    ) {
        val label: String = "$noteName$octave"
    }

    val STANDARD_GUITAR_STRINGS = listOf(
        TuningString("E", 2, 82.41, 6),
        TuningString("A", 2, 110.00, 5),
        TuningString("D", 3, 146.83, 4),
        TuningString("G", 3, 196.00, 3),
        TuningString("B", 3, 246.94, 2),
        TuningString("E", 4, 329.63, 1)
    )

    val TUNING_PROFILES = listOf(
        TuningProfile("guitar_standard", "Гитара 6 струн", "Стандарт", STANDARD_GUITAR_STRINGS),
        TuningProfile(
            "guitar_drop_d",
            "Гитара 6 струн",
            "Drop D",
            listOf(
                TuningString("D", 2, 73.42, 6),
                TuningString("A", 2, 110.00, 5),
                TuningString("D", 3, 146.83, 4),
                TuningString("G", 3, 196.00, 3),
                TuningString("B", 3, 246.94, 2),
                TuningString("E", 4, 329.63, 1)
            )
        ),
        TuningProfile(
            "bass_standard",
            "Бас-гитара 4 струны",
            "Стандарт",
            listOf(
                TuningString("E", 1, 41.20, 4),
                TuningString("A", 1, 55.00, 3),
                TuningString("D", 2, 73.42, 2),
                TuningString("G", 2, 98.00, 1)
            )
        ),
        TuningProfile(
            "ukulele_standard",
            "Укулеле",
            "Стандарт",
            listOf(
                TuningString("G", 4, 392.00, 4),
                TuningString("C", 4, 261.63, 3),
                TuningString("E", 4, 329.63, 2),
                TuningString("A", 4, 440.00, 1)
            )
        ),
        TuningProfile(
            "violin_standard",
            "Скрипка",
            "Стандарт",
            listOf(
                TuningString("G", 3, 196.00, 4),
                TuningString("D", 4, 293.66, 3),
                TuningString("A", 4, 440.00, 2),
                TuningString("E", 5, 659.25, 1)
            )
        ),
        TuningProfile(
            "viola_standard",
            "Альт",
            "Стандарт",
            listOf(
                TuningString("C", 3, 130.81, 4),
                TuningString("G", 3, 196.00, 3),
                TuningString("D", 4, 293.66, 2),
                TuningString("A", 4, 440.00, 1)
            )
        ),
        TuningProfile(
            "cello_standard",
            "Виолончель",
            "Стандарт",
            listOf(
                TuningString("C", 2, 65.41, 4),
                TuningString("G", 2, 98.00, 3),
                TuningString("D", 3, 146.83, 2),
                TuningString("A", 3, 220.00, 1)
            )
        )
    )

    fun tuningById(id: String): TuningProfile =
        TUNING_PROFILES.firstOrNull { it.id == id } ?: TUNING_PROFILES.first()

    val GUITAR_STRINGS: Map<String, Double> = STANDARD_GUITAR_STRINGS.associate { it.label to it.frequencyAtA440 }

    data class PitchResult(
        val frequency: Float,
        val noteName: String,
        val octave: Int,
        val centsOff: Int,
        val inTune: Boolean,
        val confidence: Float = 0f,
        val targetFrequency: Float = 0f,
        val stringNumber: Int = 0,
        val targetLabel: String = "$noteName$octave",
        val rms: Float = 0f
    )

    private data class PitchEstimate(
        val frequency: Double,
        val clarity: Double
    )

    fun analyze(
        samples: ShortArray,
        calibrationHz: Int = 440,
        highAccuracy: Boolean = false,
        tuning: TuningProfile = TUNING_PROFILES.first()
    ): PitchResult? {
        if (samples.size < 4096) return null

        val rms = calculateRMS(samples)
        val rmsThreshold = if (highAccuracy) MIN_RMS * 0.75f else MIN_RMS
        if (rms < rmsThreshold) return null

        val floatSamples = preprocess(samples, highAccuracy)
        val minFreq = (tuning.strings.minOf { it.frequencyAtA440 } * 0.72).coerceAtLeast(ABSOLUTE_MIN_FREQ)
        val maxFreq = (tuning.strings.maxOf { it.frequencyAtA440 } * 1.35).coerceAtMost(ABSOLUTE_MAX_FREQ)
        val estimate = detectPitchNSDF(floatSamples, highAccuracy, minFreq, maxFreq)
        val freq = estimate.frequency
        if (freq < minFreq || freq > maxFreq) return null

        return frequencyToTuningString(
            freq = freq,
            clarity = estimate.clarity,
            rms = rms,
            calibrationHz = calibrationHz.coerceIn(430, 450),
            highAccuracy = highAccuracy,
            tuning = tuning
        )
    }

    /**
     * Removes DC and applies a Hann window. High-accuracy mode also uses a tiny
     * one-pole high-pass to reduce room rumble before autocorrelation.
     */
    private fun preprocess(samples: ShortArray, highAccuracy: Boolean): FloatArray {
        val size = minOf(samples.size, if (highAccuracy) 16384 else 8192)
        var mean = 0.0
        for (i in 0 until size) mean += samples[i] / 32768.0
        mean /= size

        val output = FloatArray(size)
        var previousInput = 0.0
        var previousOutput = 0.0
        val highPass = 0.995

        for (i in 0 until size) {
            val centered = samples[i] / 32768.0 - mean
            val filtered = if (highAccuracy) {
                val y = highPass * (previousOutput + centered - previousInput)
                previousInput = centered
                previousOutput = y
                y
            } else {
                centered
            }
            val window = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / (size - 1))
            output[i] = (filtered * window).toFloat()
        }
        return output
    }

    private fun detectPitchNSDF(
        samples: FloatArray,
        highAccuracy: Boolean,
        minFreq: Double,
        maxFreq: Double
    ): PitchEstimate {
        val size = samples.size
        val minLag = (SAMPLE_RATE / maxFreq).toInt().coerceAtLeast(2)
        val maxLag = minOf((SAMPLE_RATE / minFreq).toInt(), size / 2)
        val threshold = if (highAccuracy) 0.34 else 0.26

        val nsdf = DoubleArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var numerator = 0.0
            var denominator = 0.0
            for (i in 0 until size - lag) {
                val a = samples[i].toDouble()
                val b = samples[i + lag].toDouble()
                numerator += a * b
                denominator += a * a + b * b
            }
            nsdf[lag] = if (denominator > 1e-12) 2.0 * numerator / denominator else 0.0
        }

        val peaks = mutableListOf<Pair<Int, Double>>()
        var lag = minLag
        while (lag <= maxLag) {
            if (nsdf[lag] > threshold) {
                var localLag = lag
                var localValue = nsdf[lag]
                while (lag <= maxLag && nsdf[lag] > 0) {
                    if (nsdf[lag] > localValue) {
                        localLag = lag
                        localValue = nsdf[lag]
                    }
                    lag++
                }
                peaks += localLag to localValue
            }
            lag++
        }

        val bestPeak = if (peaks.isNotEmpty()) {
            val maxValue = peaks.maxOf { it.second }
            peaks.firstOrNull { it.second >= maxValue * 0.82 } ?: peaks.maxBy { it.second }
        } else {
            (minLag..maxLag).maxBy { nsdf[it] }.let { it to nsdf[it] }
        }

        var bestLag = bestPeak.first
        val bestValue = bestPeak.second

        // Guard against octave mistakes: if a lower fundamental is plausible,
        // prefer it over a very strong second/third harmonic.
        for (multiplier in 2..3) {
            val expected = bestLag * multiplier
            if (expected + 2 <= maxLag) {
                val range = (expected - 2).coerceAtLeast(minLag)..(expected + 2).coerceAtMost(maxLag)
                val candidateLag = range.maxBy { nsdf[it] }
                val candidateValue = nsdf[candidateLag]
                if (candidateValue > threshold * 0.85 && candidateValue > bestValue * 0.42) {
                    bestLag = candidateLag
                }
            }
        }

        val refinedLag = if (bestLag > minLag && bestLag < maxLag) {
            val y0 = nsdf[bestLag - 1]
            val y1 = nsdf[bestLag]
            val y2 = nsdf[bestLag + 1]
            val denom = 2.0 * (2.0 * y1 - y2 - y0)
            if (abs(denom) > 1e-10) {
                bestLag + (y2 - y0) / denom
            } else {
                bestLag.toDouble()
            }
        } else {
            bestLag.toDouble()
        }

        return PitchEstimate(
            frequency = SAMPLE_RATE.toDouble() / refinedLag,
            clarity = nsdf[bestLag].coerceIn(0.0, 1.0)
        )
    }

    private fun frequencyToTuningString(
        freq: Double,
        clarity: Double,
        rms: Float,
        calibrationHz: Int,
        highAccuracy: Boolean,
        tuning: TuningProfile
    ): PitchResult {
        val calibrationRatio = calibrationHz / 440.0
        val strings = tuning.strings.map { string ->
            string to string.frequencyAtA440 * calibrationRatio
        }
        val nearest = strings.minBy { (_, targetFreq) ->
            abs(1200.0 * log2(freq / targetFreq))
        }
        val string = nearest.first
        val targetFreq = nearest.second
        val cents = (1200.0 * log2(freq / targetFreq)).roundToInt().coerceIn(-50, 50)
        val tolerance = if (highAccuracy) 3 else 5

        val centsScore = exp(-abs(cents) / 28.0)
        val levelScore = ((rms - MIN_RMS) / 0.045f).coerceIn(0f, 1f)
        val confidence = (clarity * 0.72 + centsScore * 0.18 + levelScore * 0.10).toFloat().coerceIn(0f, 1f)

        return PitchResult(
            frequency = freq.toFloat(),
            noteName = string.noteName,
            octave = string.octave,
            centsOff = cents,
            inTune = abs(cents) <= tolerance && confidence >= if (highAccuracy) 0.62f else 0.52f,
            confidence = confidence,
            targetFrequency = targetFreq.toFloat(),
            stringNumber = string.stringNumber,
            targetLabel = string.label,
            rms = rms
        )
    }

    fun calculateRMS(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) {
            val normalized = sample / 32768.0
            sum += normalized * normalized
        }
        return sqrt(sum / samples.size).toFloat()
    }

    private fun Double.roundToInt(): Int = round(this).toInt()
    private fun log2(x: Double) = ln(x) / ln(2.0)
}
