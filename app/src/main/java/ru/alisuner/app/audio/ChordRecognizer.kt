package ru.alisuner.app.audio

import kotlin.math.*

/**
 * Improved chord recognition from audio spectrum (chromagram + chord templates).
 *
 * Algorithm:
 * 1. Compute DFT spectrum of input audio signal
 * 2. Build chromagram - energy across 12 notes (C, C#, D, ..., B)
 * 3. Normalize and filter chromagram (adaptive threshold, harmonic smoothing)
 * 4. Compare chromagram against all known chord templates
 * 5. Select best matching chord using multi-criteria scoring
 * 6. Apply robust temporal smoothing with majority voting for result stability
 *
 * Supports all 12 tonalities and 38 chord types (456 templates total).
 */
object ChordRecognizer {

    private const val SAMPLE_RATE = 44100
    private const val DEFAULT_CALIBRATION_HZ = 440

    /** Minimum RMS for processing (noise threshold) */
    private const val MIN_RMS = 0.008f

    /** Minimum confidence for a candidate */
    private const val MIN_CONFIDENCE = 0.28f

    /** History buffer size for voting */
    private const val HISTORY_SIZE = 5

    /**
     * Minimum votes in history to switch chord.
     * New chord must get >= SWITCH_VOTES out of HISTORY_SIZE
     * to replace the current stable result.
     */
    private const val SWITCH_VOTES = 3

    /**
     * Minimum score advantage of new chord over current
     * for instant switch (without waiting for votes).
     */
    private const val INSTANT_SWITCH_MARGIN = 0.10f

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val STANDARD_GUITAR_TUNING = PitchDetector.tuningById("guitar_standard")

    data class ChordTemplate(
        val name: String,
        val rootNoteIndex: Int,
        val intervals: List<Int>,
        val type: ChordType
    )

    enum class ChordType(val priority: Int, val russianName: String) {
        MAJOR(10, "\u043c\u0430\u0436\u043e\u0440"),
        MINOR(10, "\u043c\u0438\u043d\u043e\u0440"),
        DIMINISHED(6, "\u0443\u043c\u0435\u043d\u044c\u0448\u0451\u043d\u043d\u044b\u0439"),
        AUGMENTED(6, "\u0443\u0432\u0435\u043b\u0438\u0447\u0435\u043d\u043d\u044b\u0439"),
        SUS2(7, "sus2"),
        SUS4(7, "sus4"),
        DOMINANT_7(8, "\u0434\u043e\u043c\u0438\u043d\u0430\u043d\u0442\u0441\u0435\u043f\u0442\u0430\u043a\u043a\u043e\u0440\u0434"),
        MAJOR_7(7, "\u0431\u043e\u043b\u044c\u0448\u043e\u0439 \u043c\u0430\u0436\u043e\u0440\u043d\u044b\u0439 \u0441\u0435\u043f\u0442\u0430\u043a\u043a\u043e\u0440\u0434"),
        MINOR_7(8, "\u043c\u0430\u043b\u044b\u0439 \u043c\u0438\u043d\u043e\u0440\u043d\u044b\u0439 \u0441\u0435\u043f\u0442\u0430\u043a\u043a\u043e\u0440\u0434"),
        DIMINISHED_7(5, "\u0443\u043c\u0435\u043d\u044c\u0448\u0451\u043d\u043d\u044b\u0439 \u0441\u0435\u043f\u0442\u0430\u043a\u043a\u043e\u0440\u0434"),
        MINOR_7_FLAT5(5, "\u043f\u043e\u043b\u0443\u0443\u043c\u0435\u043d\u044c\u0448\u0451\u043d\u043d\u044b\u0439"),
        MINOR_MAJ7(4, "\u043c\u0438\u043d\u043e\u0440\u043d\u044b\u0439 \u043c\u0430\u0436\u043e\u0440\u043d\u044b\u0439 \u0441\u0435\u043f\u0442\u0430\u043a\u043a\u043e\u0440\u0434"),
        AUGMENTED_7(4, "\u0443\u0432\u0435\u043b\u0438\u0447\u0435\u043d\u043d\u044b\u0439 \u0441\u0435\u043f\u0442\u0430\u043a\u043a\u043e\u0440\u0434"),
        SEVEN_SUS2(5, "7sus2"),
        SEVEN_SUS4(5, "7sus4"),
        SIXTH(6, "\u0441\u0435\u043a\u0441\u0442\u0430\u043a\u043a\u043e\u0440\u0434"),
        MINOR_6(6, "\u043c\u0438\u043d\u043e\u0440\u043d\u044b\u0439 \u0441\u0435\u043a\u0441\u0442\u0430\u043a\u043a\u043e\u0440\u0434"),
        SIX_NINE(4, "\u0441\u0435\u043a\u0441\u0442\u043d\u043e\u043d\u0430\u043a\u043a\u043e\u0440\u0434"),
        MINOR_SIX_NINE(4, "\u043c\u0438\u043d\u043e\u0440\u043d\u044b\u0439 \u0441\u0435\u043a\u0441\u0442\u043d\u043e\u043d\u0430\u043a\u043a\u043e\u0440\u0434"),
        ADD9(5, "add9"),
        MINOR_ADD9(5, "\u043c\u0438\u043d\u043e\u0440\u043d\u044b\u0439 add9"),
        DOMINANT_9(4, "\u043d\u043e\u043d\u0430\u043a\u043a\u043e\u0440\u0434"),
        MINOR_9(4, "\u043c\u0438\u043d\u043e\u0440\u043d\u044b\u0439 \u043d\u043e\u043d\u0430\u043a\u043a\u043e\u0440\u0434"),
        MAJOR_9(4, "\u0431\u043e\u043b\u044c\u0448\u043e\u0439 \u043c\u0430\u0436\u043e\u0440\u043d\u044b\u0439 \u043d\u043e\u043d\u0430\u043a\u043a\u043e\u0440\u0434"),
        SEVEN_FLAT9(3, "7b9"),
        SEVEN_SHARP9(3, "7#9 (Hendrix)"),
        ADD11(4, "add11"),
        DOMINANT_11(3, "\u0443\u043d\u0434\u0435\u0446\u0438\u043c\u0430\u043a\u043a\u043e\u0440\u0434"),
        MINOR_11(3, "\u043c\u0438\u043d\u043e\u0440\u043d\u044b\u0439 \u0443\u043d\u0434\u0435\u0446\u0438\u043c\u0430\u043a\u043a\u043e\u0440\u0434"),
        MAJOR_11(3, "\u0431\u043e\u043b\u044c\u0448\u043e\u0439 \u043c\u0430\u0436\u043e\u0440\u043d\u044b\u0439 \u0443\u043d\u0434\u0435\u0446\u0438\u043c\u0430\u043a\u043a\u043e\u0440\u0434"),
        SEVEN_SHARP11(3, "7#11"),
        DOMINANT_13(2, "\u0442\u0435\u0440\u0446\u0434\u0435\u0446\u0438\u043c\u0430\u043a\u043a\u043e\u0440\u0434"),
        MINOR_13(2, "\u043c\u0438\u043d\u043e\u0440\u043d\u044b\u0439 \u0442\u0435\u0440\u0446\u0434\u0435\u0446\u0438\u043c\u0430\u043a\u043a\u043e\u0440\u0434"),
        MAJOR_13(2, "\u0431\u043e\u043b\u044c\u0448\u043e\u0439 \u043c\u0430\u0436\u043e\u0440\u043d\u044b\u0439 \u0442\u0435\u0440\u0446\u0434\u0435\u0446\u0438\u043c\u0430\u043a\u043a\u043e\u0440\u0434"),
        SEVEN_FLAT5(3, "7b5"),
        SEVEN_SHARP5(3, "7#5"),
        ALTERED(2, "\u0430\u043b\u044c\u0442\u0435\u0440\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u044b\u0439 \u0434\u043e\u043c\u0438\u043d\u0430\u043d\u0442"),
        POWER(9, "\u043a\u0432\u0438\u043d\u0442\u0430\u043a\u043a\u043e\u0440\u0434 (power chord)")
    }

    /**
     * Common chord types get a priority bonus during scoring,
     * since they are most frequently played on guitar.
     */
    private val COMMON_CHORD_TYPES = setOf(
        ChordType.MAJOR,
        ChordType.MINOR,
        ChordType.DOMINANT_7,
        ChordType.MINOR_7,
        ChordType.MAJOR_7,
        ChordType.SUS2,
        ChordType.SUS4,
        ChordType.POWER
    )

    private val CHORD_INTERVALS: Map<ChordType, List<Int>> = mapOf(
        ChordType.MAJOR               to listOf(0, 4, 7),
        ChordType.MINOR               to listOf(0, 3, 7),
        ChordType.DIMINISHED          to listOf(0, 3, 6),
        ChordType.AUGMENTED           to listOf(0, 4, 8),
        ChordType.SUS2                to listOf(0, 2, 7),
        ChordType.SUS4                to listOf(0, 5, 7),
        ChordType.DOMINANT_7          to listOf(0, 4, 7, 10),
        ChordType.MAJOR_7             to listOf(0, 4, 7, 11),
        ChordType.MINOR_7             to listOf(0, 3, 7, 10),
        ChordType.DIMINISHED_7        to listOf(0, 3, 6, 9),
        ChordType.MINOR_7_FLAT5       to listOf(0, 3, 6, 10),
        ChordType.MINOR_MAJ7          to listOf(0, 3, 7, 11),
        ChordType.AUGMENTED_7         to listOf(0, 4, 8, 10),
        ChordType.SEVEN_SUS2          to listOf(0, 2, 7, 10),
        ChordType.SEVEN_SUS4          to listOf(0, 5, 7, 10),
        ChordType.SIXTH               to listOf(0, 4, 7, 9),
        ChordType.MINOR_6             to listOf(0, 3, 7, 9),
        ChordType.SIX_NINE            to listOf(0, 2, 4, 7, 9),
        ChordType.MINOR_SIX_NINE      to listOf(0, 2, 3, 7, 9),
        ChordType.ADD9                to listOf(0, 2, 4, 7),
        ChordType.MINOR_ADD9          to listOf(0, 2, 3, 7),
        ChordType.DOMINANT_9          to listOf(0, 2, 4, 7, 10),
        ChordType.MINOR_9             to listOf(0, 2, 3, 7, 10),
        ChordType.MAJOR_9             to listOf(0, 2, 4, 7, 11),
        ChordType.SEVEN_FLAT9         to listOf(0, 1, 4, 7, 10),
        ChordType.SEVEN_SHARP9        to listOf(0, 3, 4, 7, 10),
        ChordType.ADD11               to listOf(0, 4, 5, 7),
        ChordType.DOMINANT_11         to listOf(0, 2, 4, 5, 7, 10),
        ChordType.MINOR_11            to listOf(0, 2, 3, 5, 7, 10),
        ChordType.MAJOR_11            to listOf(0, 2, 4, 5, 7, 11),
        ChordType.SEVEN_SHARP11       to listOf(0, 4, 6, 7, 10),
        ChordType.DOMINANT_13         to listOf(0, 2, 4, 7, 9, 10),
        ChordType.MINOR_13            to listOf(0, 2, 3, 7, 9, 10),
        ChordType.MAJOR_13            to listOf(0, 2, 4, 7, 9, 11),
        ChordType.SEVEN_FLAT5         to listOf(0, 4, 6, 10),
        ChordType.SEVEN_SHARP5        to listOf(0, 4, 8, 10),
        ChordType.ALTERED             to listOf(0, 1, 3, 4, 6, 8, 10),
        ChordType.POWER               to listOf(0, 7)
    )

    private val CHORD_SUFFIX: Map<ChordType, String> = mapOf(
        ChordType.MAJOR               to "",
        ChordType.MINOR               to "m",
        ChordType.DIMINISHED          to "dim",
        ChordType.AUGMENTED           to "aug",
        ChordType.SUS2                to "sus2",
        ChordType.SUS4                to "sus4",
        ChordType.DOMINANT_7          to "7",
        ChordType.MAJOR_7             to "maj7",
        ChordType.MINOR_7             to "m7",
        ChordType.DIMINISHED_7        to "dim7",
        ChordType.MINOR_7_FLAT5       to "m7b5",
        ChordType.MINOR_MAJ7          to "m(maj7)",
        ChordType.AUGMENTED_7         to "aug7",
        ChordType.SEVEN_SUS2          to "7sus2",
        ChordType.SEVEN_SUS4          to "7sus4",
        ChordType.SIXTH               to "6",
        ChordType.MINOR_6             to "m6",
        ChordType.SIX_NINE            to "6/9",
        ChordType.MINOR_SIX_NINE      to "m6/9",
        ChordType.ADD9                to "add9",
        ChordType.MINOR_ADD9          to "madd9",
        ChordType.DOMINANT_9          to "9",
        ChordType.MINOR_9             to "m9",
        ChordType.MAJOR_9             to "maj9",
        ChordType.SEVEN_FLAT9         to "7b9",
        ChordType.SEVEN_SHARP9        to "7#9",
        ChordType.ADD11               to "add11",
        ChordType.DOMINANT_11         to "11",
        ChordType.MINOR_11            to "m11",
        ChordType.MAJOR_11            to "maj11",
        ChordType.SEVEN_SHARP11       to "7#11",
        ChordType.DOMINANT_13         to "13",
        ChordType.MINOR_13            to "m13",
        ChordType.MAJOR_13            to "maj13",
        ChordType.SEVEN_FLAT5         to "7b5",
        ChordType.SEVEN_SHARP5        to "7#5",
        ChordType.ALTERED             to "alt",
        ChordType.POWER               to "5"
    )

    private val INTERVAL_ROLE_WEIGHT: Map<Int, Float> = mapOf(
        0 to 1.00f,
        1 to 0.70f,
        2 to 0.65f,
        3 to 0.90f,
        4 to 0.90f,
        5 to 0.70f,
        6 to 0.75f,
        7 to 0.85f,
        8 to 0.75f,
        9 to 0.70f,
        10 to 0.80f,
        11 to 0.80f
    )

    private val chordTemplates: List<ChordTemplate> = buildList {
        for (rootIdx in 0 until 12) {
            val rootName = NOTE_NAMES[rootIdx]
            for ((type, intervals) in CHORD_INTERVALS) {
                val suffix = CHORD_SUFFIX[type] ?: ""
                val name = "$rootName$suffix"
                val transposedIntervals = intervals.map { (rootIdx + it) % 12 }
                add(ChordTemplate(name, rootIdx, transposedIntervals, type))
            }
        }
    }

    // ---- Stabilization state ----

    /** Buffer of last N recognized chord names */
    private val recentResults = mutableListOf<String>()

    /** Buffer of last N scores for each recognized chord */
    private val recentScores = mutableListOf<Float>()

    /** Current locked chord (displayed to user) */
    @Volatile
    private var lockedChord: String? = null

    /** Current locked result */
    @Volatile
    private var lockedResult: ChordResult? = null

    /** Consecutive silence frames counter */
    private var silenceFrames = 0
    private const val SILENCE_RESET_FRAMES = 6


    data class ChordResult(
        val chordName: String,
        val confidence: Float,
        val detectedNotes: List<String>,
        val rmsLevel: Float,
        val chordType: ChordType = ChordType.MAJOR,
        val rootNote: String = "",
        val russianDescription: String = ""
    )

    /** Reset internal state (on start/stop recording) */
    fun reset() {
        synchronized(recentResults) {
            recentResults.clear()
            recentScores.clear()
            lockedChord = null
            lockedResult = null
            silenceFrames = 0
        }
    }

    fun recognizeFromAudio(
        samples: ShortArray,
        calibrationHz: Int = DEFAULT_CALIBRATION_HZ
    ): String? {
        val result = recognizeChord(samples, calibrationHz)
        return result?.chordName
    }

    fun recognizeChord(
        samples: ShortArray,
        calibrationHz: Int = DEFAULT_CALIBRATION_HZ
    ): ChordResult? {
        if (samples.size < 4096) return null

        val rms = PitchDetector.calculateRMS(samples)
        if (rms < MIN_RMS) {
            synchronized(recentResults) {
                silenceFrames++
                if (silenceFrames >= SILENCE_RESET_FRAMES) {
                    recentResults.clear()
                    recentScores.clear()
                    lockedChord = null
                    lockedResult = null
                    silenceFrames = 0
                }
            }
            return lockedResult
        }
        silenceFrames = 0

        val floatSamples = FloatArray(samples.size) { samples[it] / 32768f }
        val calibration = calibrationHz.coerceIn(430, 450)

        val chroma = computeChromagram(floatSamples, calibration)

        val maxChroma = chroma.max()
        if (maxChroma < 0.0005f) return lockedResult
        val normalizedChroma = FloatArray(12) { chroma[it] / maxChroma }

        // Compute bass chromagram BEFORE harmonic smoothing so we can use it
        // to distinguish real chord tones from harmonics
        val bassChroma = computeBassChromagram(floatSamples, calibration)
        val bassMax = bassChroma.max()
        val normalizedBass = if (bassMax > 0.0001f) {
            FloatArray(12) { bassChroma[it] / bassMax }
        } else {
            FloatArray(12)
        }
        val bassMaxIdx = normalizedBass.indices.maxByOrNull { normalizedBass[it] } ?: 0

        // Bass-aware harmonic smoothing: only suppress harmonics of notes
        // that are strong in bass, and never suppress notes with their own bass support
        val smoothedChroma = harmonicSmoothing(normalizedChroma, normalizedBass)

        val meanChroma = smoothedChroma.average().toFloat()
        val stdChroma = run {
            val mean = meanChroma.toDouble()
            sqrt(smoothedChroma.map { (it - mean).pow(2) }.average()).toFloat()
        }
        val noteThreshold = maxOf(0.12f, meanChroma + stdChroma * 0.3f)
        val detectedNotes = mutableListOf<String>()
        for (i in 0 until 12) {
            if (smoothedChroma[i] > noteThreshold) {
                detectedNotes.add(NOTE_NAMES[i])
            }
        }

        val trebleChroma = computeTrebleChromagram(floatSamples, calibration)
        val trebleMax = trebleChroma.max()
        val normalizedTreble = if (trebleMax > 0.0001f) {
            FloatArray(12) { trebleChroma[it] / trebleMax }
        } else {
            FloatArray(12)
        }

        val candidates = mutableListOf<Pair<ChordTemplate, Float>>()

        for (template in chordTemplates) {
            val score = matchTemplateAdvanced(
                smoothedChroma, template, normalizedBass, bassMaxIdx, normalizedTreble
            )
            if (score > MIN_CONFIDENCE) {
                candidates.add(template to score)
            }
        }

        if (candidates.isEmpty()) return lockedResult

        candidates.sortWith(
            compareByDescending<Pair<ChordTemplate, Float>> { it.second }
                .thenByDescending { it.first.type.priority }
                .thenBy { CHORD_INTERVALS[it.first.type]?.size ?: 99 }
        )

        val bestCandidate = candidates.first()
        val bestTemplate = bestCandidate.first
        val bestScore = bestCandidate.second

        return applyTemporalSmoothing(bestTemplate, bestScore, detectedNotes, rms)
    }

    /**
     * Improved stabilization:
     * 1. Add current result to history buffer
     * 2. Count votes (majority voting) over history
     * 3. Switch locked chord only if:
     *    - New chord gets >= SWITCH_VOTES votes, OR
     *    - New chord beats current by INSTANT_SWITCH_MARGIN
     * 4. Otherwise keep previous locked chord
     */
    private fun applyTemporalSmoothing(
        bestTemplate: ChordTemplate,
        bestScore: Float,
        detectedNotes: List<String>,
        rms: Float
    ): ChordResult {
        synchronized(recentResults) {
            recentResults.add(bestTemplate.name)
            recentScores.add(bestScore)
            if (recentResults.size > HISTORY_SIZE) {
                recentResults.removeAt(0)
                recentScores.removeAt(0)
            }

            // Majority voting
            val voteCounts = mutableMapOf<String, Int>()
            val voteScoreSums = mutableMapOf<String, Float>()
            for (i in recentResults.indices) {
                val name = recentResults[i]
                voteCounts[name] = (voteCounts[name] ?: 0) + 1
                voteScoreSums[name] = (voteScoreSums[name] ?: 0f) + recentScores[i]
            }

            val leader = voteCounts.maxByOrNull { it.value }
            val leaderName = leader?.key ?: bestTemplate.name
            val leaderVotes = leader?.value ?: 0
            val leaderAvgScore = if (leaderVotes > 0) {
                (voteScoreSums[leaderName] ?: 0f) / leaderVotes
            } else 0f

            val currentLocked = lockedChord

            val shouldSwitch = when {
                currentLocked == null -> leaderVotes >= 1
                leaderName == currentLocked -> false
                leaderVotes >= SWITCH_VOTES -> true
                leaderAvgScore - (lockedResult?.confidence ?: 0f) > INSTANT_SWITCH_MARGIN -> {
                    leaderVotes >= 2
                }
                else -> false
            }

            if (shouldSwitch) {
                val leaderTemplate = chordTemplates.firstOrNull { it.name == leaderName }
                if (leaderTemplate != null) {
                    val rootName = NOTE_NAMES[leaderTemplate.rootNoteIndex]
                    val russianRoot = getRussianNoteName(rootName)
                    val russianType = leaderTemplate.type.russianName
                    val russianDescription = "$russianRoot $russianType"

                    val result = ChordResult(
                        chordName = leaderTemplate.name,
                        confidence = leaderAvgScore.coerceIn(0f, 1f),
                        detectedNotes = detectedNotes,
                        rmsLevel = rms,
                        chordType = leaderTemplate.type,
                        rootNote = rootName,
                        russianDescription = russianDescription
                    )
                    lockedChord = leaderName
                    lockedResult = result
                    return result
                }
            }

            // Update metadata of existing locked result without changing the chord
            val existing = lockedResult
            if (existing != null) {
                val currentVotes = voteCounts[existing.chordName] ?: 0
                val updatedConfidence = if (currentVotes > 0) {
                    ((voteScoreSums[existing.chordName] ?: 0f) / currentVotes).coerceIn(0f, 1f)
                } else {
                    existing.confidence
                }
                val updated = existing.copy(
                    confidence = updatedConfidence,
                    detectedNotes = detectedNotes,
                    rmsLevel = rms
                )
                lockedResult = updated
                return updated
            }

            // No locked result yet and not enough votes - show best but lock if >= 2 votes
            val rootName = NOTE_NAMES[bestTemplate.rootNoteIndex]
            val russianRoot = getRussianNoteName(rootName)
            val russianType = bestTemplate.type.russianName
            val russianDescription = "$russianRoot $russianType"
            val result = ChordResult(
                chordName = bestTemplate.name,
                confidence = bestScore.coerceIn(0f, 1f),
                detectedNotes = detectedNotes,
                rmsLevel = rms,
                chordType = bestTemplate.type,
                rootNote = rootName,
                russianDescription = russianDescription
            )

            if (leaderVotes >= 1) {
                lockedChord = bestTemplate.name
                lockedResult = result
            }

            return result
        }
    }

    private fun getRussianNoteName(note: String): String = when (note) {
        "C" -> "\u0414\u043e"
        "C#" -> "\u0414\u043e-\u0434\u0438\u0435\u0437"
        "D" -> "\u0420\u0435"
        "D#" -> "\u0420\u0435-\u0434\u0438\u0435\u0437"
        "E" -> "\u041c\u0438"
        "F" -> "\u0424\u0430"
        "F#" -> "\u0424\u0430-\u0434\u0438\u0435\u0437"
        "G" -> "\u0421\u043e\u043b\u044c"
        "G#" -> "\u0421\u043e\u043b\u044c-\u0434\u0438\u0435\u0437"
        "A" -> "\u041b\u044f"
        "A#" -> "\u041b\u044f-\u0434\u0438\u0435\u0437"
        "B" -> "\u0421\u0438"
        else -> note
    }

    /**
     * Bass-aware harmonic smoothing.
     *
     * Only suppresses harmonics of notes that have strong bass presence
     * (likely real fundamentals). Never suppresses a note that has its own
     * independent bass support — that note is a real chord tone, not a harmonic.
     *
     * This prevents destroying chord tones that coincide with harmonics.
     * E.g. in C major, G is both a chord tone AND the 3rd harmonic of C.
     * Old code would blindly reduce G; new code checks that G has bass support
     * and leaves it alone.
     */
    private fun harmonicSmoothing(chroma: FloatArray, bassChroma: FloatArray): FloatArray {
        val result = chroma.copyOf()

        // Harmonic series offsets in semitones and reduction factors
        // 3rd harmonic = perfect 5th (+7), 5th = major 3rd (+4),
        // 7th ≈ minor 7th (+10), 9th ≈ major 2nd (+2)
        val harmonicOffsets = listOf(
            7 to 0.18f,
            4 to 0.10f,
            10 to 0.07f,
            2 to 0.05f
        )

        for (fundamental in 0 until 12) {
            // Only suppress harmonics of notes with bass presence (real fundamentals)
            if (bassChroma[fundamental] < 0.15f && chroma[fundamental] < 0.45f) continue
            if (chroma[fundamental] < 0.25f) continue

            for ((offset, reductionFactor) in harmonicOffsets) {
                val harmonicIdx = (fundamental + offset) % 12
                // Don't suppress if the harmonic note has its own bass support —
                // it's likely a real chord tone, not just a harmonic artifact
                if (bassChroma[harmonicIdx] > bassChroma[fundamental] * 0.35f) continue

                val reduction = chroma[fundamental] * reductionFactor
                result[harmonicIdx] = maxOf(0f, result[harmonicIdx] - reduction)
            }
        }

        val maxVal = result.max()
        if (maxVal > 0.001f) {
            for (i in result.indices) {
                result[i] /= maxVal
            }
        }

        return result
    }

    private fun computeChromagram(samples: FloatArray, calibrationHz: Int): FloatArray {
        val chroma = FloatArray(12)
        val n = minOf(samples.size, 16384)
        val windowed = applyBlackmanHarrisWindow(samples, n)
        val minFundamental = calibratedFrequency(STANDARD_GUITAR_TUNING.strings.minOf { it.frequencyAtA440 }, calibrationHz) * 0.72

        for (octave in 2..6) {
            for (noteIdx in 0 until 12) {
                val freq = noteFrequency(octave, noteIdx, calibrationHz)
                if (freq < minFundamental || freq > 4200.0) continue

                val energy = goertzelEnergy(windowed, n, freq)
                val octaveWeight = when (octave) {
                    2 -> 0.85f
                    3 -> 1.0f
                    4 -> 1.0f
                    5 -> 0.40f
                    6 -> 0.15f
                    else -> 0.20f
                }
                chroma[noteIdx] += energy * octaveWeight
            }
        }

        return chroma
    }

    private fun computeBassChromagram(samples: FloatArray, calibrationHz: Int): FloatArray {
        val chroma = FloatArray(12)
        val n = minOf(samples.size, 16384)
        val windowed = applyBlackmanHarrisWindow(samples, n)
        val minFundamental = calibratedFrequency(STANDARD_GUITAR_TUNING.strings.minOf { it.frequencyAtA440 }, calibrationHz) * 0.72
        val maxFundamental = calibratedFrequency(STANDARD_GUITAR_TUNING.strings.maxOf { it.frequencyAtA440 }, calibrationHz) * 1.35

        for (octave in 2..3) {
            for (noteIdx in 0 until 12) {
                val freq = noteFrequency(octave, noteIdx, calibrationHz)
                if (freq < minFundamental || freq > maxFundamental) continue
                chroma[noteIdx] += goertzelEnergy(windowed, n, freq)
            }
        }

        return chroma
    }

    private fun computeTrebleChromagram(samples: FloatArray, calibrationHz: Int): FloatArray {
        val chroma = FloatArray(12)
        val n = minOf(samples.size, 16384)
        val windowed = applyBlackmanHarrisWindow(samples, n)

        for (octave in 4..6) {
            for (noteIdx in 0 until 12) {
                val freq = noteFrequency(octave, noteIdx, calibrationHz)
                if (freq < 250.0 || freq > 4200.0) continue
                val weight = when (octave) {
                    4 -> 1.0f
                    5 -> 0.7f
                    6 -> 0.3f
                    else -> 0.2f
                }
                chroma[noteIdx] += goertzelEnergy(windowed, n, freq) * weight
            }
        }

        return chroma
    }

    private fun noteFrequency(octave: Int, noteIdx: Int, calibrationHz: Int): Double {
        return calibrationHz.toDouble() * 2.0.pow((octave - 4) + (noteIdx - 9) / 12.0)
    }

    private fun calibratedFrequency(frequencyAtA440: Double, calibrationHz: Int): Double {
        return frequencyAtA440 * calibrationHz / DEFAULT_CALIBRATION_HZ
    }

    private fun applyBlackmanHarrisWindow(samples: FloatArray, n: Int): FloatArray {
        val windowed = FloatArray(n)
        val a0 = 0.35875
        val a1 = 0.48829
        val a2 = 0.14128
        val a3 = 0.01168
        for (i in 0 until n) {
            val w = 2.0 * Math.PI * i / (n - 1)
            windowed[i] = samples[i] * (a0 - a1 * cos(w) + a2 * cos(2 * w) - a3 * cos(3 * w)).toFloat()
        }
        return windowed
    }

    private fun goertzelEnergy(samples: FloatArray, n: Int, targetFreq: Double): Float {
        // Use exact target frequency instead of rounding to nearest DFT bin.
        // This gives accurate energy readings for slightly detuned instruments.
        val w = 2.0 * Math.PI * targetFreq / SAMPLE_RATE
        val coeff = (2.0 * cos(w)).toFloat()

        var s1 = 0f
        var s2 = 0f

        for (i in 0 until n) {
            val s0 = samples[i] + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }

        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return maxOf(0f, power)
    }

    private fun matchTemplateAdvanced(
        chroma: FloatArray,
        template: ChordTemplate,
        bassChroma: FloatArray,
        bassMaxIdx: Int,
        trebleChroma: FloatArray
    ): Float {
        val intervals = CHORD_INTERVALS[template.type] ?: return 0f
        val rootIdx = template.rootNoteIndex

        // 1. Build ideal chromagram with weights
        val ideal = FloatArray(12)
        for (interval in intervals) {
            val noteIdx = (rootIdx + interval) % 12
            val weight = INTERVAL_ROLE_WEIGHT[interval] ?: 0.6f
            ideal[noteIdx] = weight
        }

        // 2. Weighted cosine similarity
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in 0 until 12) {
            dotProduct += chroma[i] * ideal[i]
            normA += chroma[i] * chroma[i]
            normB += ideal[i] * ideal[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator < 0.001f) return 0f
        val cosineSim = dotProduct / denominator

        // 3. Template coverage
        var presentCount = 0
        var totalWeight = 0f
        var presentWeight = 0f
        for (interval in intervals) {
            val noteIdx = (rootIdx + interval) % 12
            val weight = INTERVAL_ROLE_WEIGHT[interval] ?: 0.6f
            totalWeight += weight
            if (chroma[noteIdx] > 0.15f) {
                presentCount++
                presentWeight += weight * chroma[noteIdx]
            }
        }
        val coverageRatio = if (intervals.isNotEmpty()) {
            presentCount.toFloat() / intervals.size
        } else 0f
        val weightedCoverage = if (totalWeight > 0) presentWeight / totalWeight else 0f

        // Critical note missing penalty (strengthened)
        var criticalMissing = 0f
        val rootPresent = chroma[rootIdx % 12] > 0.10f
        if (!rootPresent) criticalMissing += 0.22f

        val hasMinorThird = chroma[(rootIdx + 3) % 12] > 0.10f
        val hasMajorThird = chroma[(rootIdx + 4) % 12] > 0.10f
        val templateHasMinorThird = 3 in intervals
        val templateHasMajorThird = 4 in intervals
        if (templateHasMinorThird && !hasMinorThird) criticalMissing += 0.15f
        if (templateHasMajorThird && !hasMajorThird) criticalMissing += 0.15f

        val templateHasFifth = 7 in intervals
        val hasFifth = chroma[(rootIdx + 7) % 12] > 0.10f
        if (templateHasFifth && !hasFifth) criticalMissing += 0.08f

        // 4. Specificity penalty for extra notes
        val templateNoteSet = template.intervals.toSet()
        var extraEnergy = 0f
        var extraCount = 0
        for (i in 0 until 12) {
            if (i !in templateNoteSet && chroma[i] > 0.25f) {
                extraEnergy += (chroma[i] - 0.25f)
                extraCount++
            }
        }
        val specificityPenalty = (extraEnergy * 0.15f) + (extraCount * 0.03f)

        // 5. Bass note (strengthened — bass note is very important for chord identity)
        val bassBonus = if (bassMaxIdx == rootIdx) {
            0.15f
        } else {
            if (bassMaxIdx in templateNoteSet) 0.03f else -0.06f
        }
        val rootBassBonus = bassChroma[rootIdx] * 0.08f

        // 6. Treble notes
        var trebleMatch = 0f
        var trebleTotal = 0f
        for (interval in intervals) {
            if (interval == 0) continue
            val noteIdx = (rootIdx + interval) % 12
            trebleTotal += 1f
            if (trebleChroma[noteIdx] > 0.15f) {
                trebleMatch += 1f
            }
        }
        val trebleBonus = if (trebleTotal > 0) {
            (trebleMatch / trebleTotal) * 0.04f
        } else 0f

        // 7. Complexity regularization
        val complexityPenalty = when (intervals.size) {
            2 -> -0.01f
            3 -> 0.03f
            4 -> 0.01f
            5 -> -0.02f
            6 -> -0.04f
            else -> -0.05f
        }

        // 8. Common chord type bonus
        val commonBonus = if (template.type in COMMON_CHORD_TYPES) 0.03f else -0.02f

        // 9. Differentiation bonus
        val differentiationBonus = computeDifferentiationBonus(chroma, template, rootIdx)

        // Final score (rebalanced: more weight on coverage, less on cosine similarity)
        val score = cosineSim * 0.40f +
                weightedCoverage * 0.26f +
                coverageRatio * 0.12f +
                bassBonus +
                rootBassBonus +
                trebleBonus +
                complexityPenalty +
                commonBonus +
                differentiationBonus -
                specificityPenalty -
                criticalMissing

        return score.coerceIn(0f, 1f)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun computeDifferentiationBonus(
        chroma: FloatArray,
        template: ChordTemplate,
        rootIdx: Int
    ): Float {
        var bonus = 0f

        when (template.type) {
            ChordType.MAJOR -> {
                val b7 = chroma[(rootIdx + 10) % 12]
                val maj7 = chroma[(rootIdx + 11) % 12]
                if (b7 < 0.15f && maj7 < 0.15f) bonus += 0.03f
                val majThird = chroma[(rootIdx + 4) % 12]
                val minThird = chroma[(rootIdx + 3) % 12]
                if (majThird > 0.25f && minThird < 0.15f) bonus += 0.02f
            }

            ChordType.MINOR -> {
                val b7 = chroma[(rootIdx + 10) % 12]
                val maj7 = chroma[(rootIdx + 11) % 12]
                if (b7 < 0.15f && maj7 < 0.15f) bonus += 0.03f
                val majThird = chroma[(rootIdx + 4) % 12]
                val minThird = chroma[(rootIdx + 3) % 12]
                if (minThird > 0.25f && majThird < 0.15f) bonus += 0.02f
            }

            ChordType.DOMINANT_7 -> {
                val b7 = chroma[(rootIdx + 10) % 12]
                val maj7 = chroma[(rootIdx + 11) % 12]
                if (b7 > 0.2f && maj7 < b7 * 0.5f) bonus += 0.03f
            }

            ChordType.MAJOR_7 -> {
                val b7 = chroma[(rootIdx + 10) % 12]
                val maj7 = chroma[(rootIdx + 11) % 12]
                if (maj7 > 0.2f && b7 < maj7 * 0.5f) bonus += 0.03f
            }

            ChordType.MINOR_7 -> {
                val minThird = chroma[(rootIdx + 3) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (minThird > 0.2f && b7 > 0.15f) bonus += 0.02f
            }

            ChordType.SUS2 -> {
                val second = chroma[(rootIdx + 2) % 12]
                val minThird = chroma[(rootIdx + 3) % 12]
                val majThird = chroma[(rootIdx + 4) % 12]
                if (second > 0.2f && minThird < 0.15f && majThird < 0.15f) bonus += 0.04f
            }

            ChordType.SUS4 -> {
                val fourth = chroma[(rootIdx + 5) % 12]
                val minThird = chroma[(rootIdx + 3) % 12]
                val majThird = chroma[(rootIdx + 4) % 12]
                if (fourth > 0.2f && minThird < 0.15f && majThird < 0.15f) bonus += 0.04f
            }

            ChordType.DIMINISHED -> {
                val b5 = chroma[(rootIdx + 6) % 12]
                val p5 = chroma[(rootIdx + 7) % 12]
                if (b5 > 0.2f && p5 < 0.15f) bonus += 0.03f
            }

            ChordType.AUGMENTED -> {
                val sharp5 = chroma[(rootIdx + 8) % 12]
                val p5 = chroma[(rootIdx + 7) % 12]
                if (sharp5 > 0.2f && p5 < 0.15f) bonus += 0.03f
            }

            ChordType.POWER -> {
                val root = chroma[rootIdx]
                val fifth = chroma[(rootIdx + 7) % 12]
                var otherMax = 0f
                for (i in 0 until 12) {
                    if (i != rootIdx && i != (rootIdx + 7) % 12) {
                        otherMax = maxOf(otherMax, chroma[i])
                    }
                }
                if (root > 0.3f && fifth > 0.3f && otherMax < 0.2f) bonus += 0.05f
            }

            ChordType.ADD9 -> {
                val second = chroma[(rootIdx + 2) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                val maj7 = chroma[(rootIdx + 11) % 12]
                if (second > 0.15f && b7 < 0.15f && maj7 < 0.15f) bonus += 0.02f
            }

            ChordType.MINOR_ADD9 -> {
                val second = chroma[(rootIdx + 2) % 12]
                val minThird = chroma[(rootIdx + 3) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (second > 0.15f && minThird > 0.2f && b7 < 0.15f) bonus += 0.02f
            }

            ChordType.DOMINANT_9 -> {
                val second = chroma[(rootIdx + 2) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (second > 0.15f && b7 > 0.15f) bonus += 0.02f
            }

            ChordType.SEVEN_FLAT9 -> {
                val b9 = chroma[(rootIdx + 1) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (b9 > 0.15f && b7 > 0.15f) bonus += 0.03f
            }

            ChordType.SEVEN_SHARP9 -> {
                val minThird = chroma[(rootIdx + 3) % 12]
                val majThird = chroma[(rootIdx + 4) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (minThird > 0.15f && majThird > 0.15f && b7 > 0.15f) bonus += 0.03f
            }

            ChordType.DIMINISHED_7 -> {
                val b5 = chroma[(rootIdx + 6) % 12]
                val bb7 = chroma[(rootIdx + 9) % 12]
                if (b5 > 0.15f && bb7 > 0.15f) bonus += 0.02f
            }

            ChordType.MINOR_7_FLAT5 -> {
                val b5 = chroma[(rootIdx + 6) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                val bb7 = chroma[(rootIdx + 9) % 12]
                if (b5 > 0.15f && b7 > 0.15f && bb7 < b7) bonus += 0.02f
            }

            ChordType.MINOR_MAJ7 -> {
                val minThird = chroma[(rootIdx + 3) % 12]
                val maj7 = chroma[(rootIdx + 11) % 12]
                if (minThird > 0.2f && maj7 > 0.15f) bonus += 0.03f
            }

            ChordType.SEVEN_SUS4 -> {
                val fourth = chroma[(rootIdx + 5) % 12]
                val minThird = chroma[(rootIdx + 3) % 12]
                val majThird = chroma[(rootIdx + 4) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (fourth > 0.2f && minThird < 0.15f && majThird < 0.15f && b7 > 0.15f) bonus += 0.03f
            }

            ChordType.SEVEN_SUS2 -> {
                val second = chroma[(rootIdx + 2) % 12]
                val minThird = chroma[(rootIdx + 3) % 12]
                val majThird = chroma[(rootIdx + 4) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (second > 0.2f && minThird < 0.15f && majThird < 0.15f && b7 > 0.15f) bonus += 0.03f
            }

            ChordType.AUGMENTED_7 -> {
                val sharp5 = chroma[(rootIdx + 8) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (sharp5 > 0.2f && b7 > 0.15f) bonus += 0.02f
            }

            ChordType.SIXTH -> {
                val sixth = chroma[(rootIdx + 9) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                val maj7 = chroma[(rootIdx + 11) % 12]
                if (sixth > 0.15f && b7 < 0.15f && maj7 < 0.15f) bonus += 0.02f
            }

            ChordType.MINOR_6 -> {
                val minThird = chroma[(rootIdx + 3) % 12]
                val sixth = chroma[(rootIdx + 9) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (minThird > 0.2f && sixth > 0.15f && b7 < 0.15f) bonus += 0.02f
            }

            ChordType.SIX_NINE -> {
                val second = chroma[(rootIdx + 2) % 12]
                val sixth = chroma[(rootIdx + 9) % 12]
                if (second > 0.15f && sixth > 0.15f) bonus += 0.02f
            }

            ChordType.SEVEN_FLAT5 -> {
                val b5 = chroma[(rootIdx + 6) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                val p5 = chroma[(rootIdx + 7) % 12]
                if (b5 > 0.15f && b7 > 0.15f && p5 < 0.15f) bonus += 0.03f
            }

            ChordType.SEVEN_SHARP5 -> {
                val sharp5 = chroma[(rootIdx + 8) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                val p5 = chroma[(rootIdx + 7) % 12]
                if (sharp5 > 0.15f && b7 > 0.15f && p5 < 0.15f) bonus += 0.03f
            }

            ChordType.SEVEN_SHARP11 -> {
                val sharp11 = chroma[(rootIdx + 6) % 12]
                val majThird = chroma[(rootIdx + 4) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (sharp11 > 0.15f && majThird > 0.2f && b7 > 0.15f) bonus += 0.03f
            }

            ChordType.ADD11 -> {
                val fourth = chroma[(rootIdx + 5) % 12]
                val majThird = chroma[(rootIdx + 4) % 12]
                val b7 = chroma[(rootIdx + 10) % 12]
                if (fourth > 0.15f && majThird > 0.2f && b7 < 0.15f) bonus += 0.02f
            }

            else -> { }
        }

        return bonus
    }
}
