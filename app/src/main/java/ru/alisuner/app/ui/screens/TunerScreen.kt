package ru.alisuner.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ru.alisuner.app.audio.AudioRecorder
import ru.alisuner.app.audio.PitchDetector
import ru.alisuner.app.audio.SimpleTonePlayer
import ru.alisuner.app.data.PreferencesManager
import kotlin.math.abs
import kotlin.math.sin

private enum class TunerMode { AUTO, MANUAL }

@Composable
fun TunerScreen() {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    var selectedTuningId by remember { mutableStateOf(prefsManager.tunerTuningId) }
    val selectedTuning = PitchDetector.tuningById(selectedTuningId)
    var mode by remember {
        mutableStateOf(if (prefsManager.tunerMode == "MANUAL") TunerMode.MANUAL else TunerMode.AUTO)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Тюнер", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        TuningProfileSelector(
            selected = selectedTuning,
            enabled = true,
            onSelect = {
                selectedTuningId = it.id
                prefsManager.tunerTuningId = it.id
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2C3E50))
                .padding(4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            ModeTab(
                text = "Авто",
                selected = mode == TunerMode.AUTO,
                onClick = {
                    mode = TunerMode.AUTO
                    prefsManager.tunerMode = "AUTO"
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            ModeTab(
                text = "Мануал",
                selected = mode == TunerMode.MANUAL,
                onClick = {
                    mode = TunerMode.MANUAL
                    prefsManager.tunerMode = "MANUAL"
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (mode) {
            TunerMode.AUTO -> AutoTunerContent(prefsManager, selectedTuning)
            TunerMode.MANUAL -> ManualTunerContent(selectedTuning)
        }
    }
}

@Composable
private fun TuningProfileSelector(
    selected: PitchDetector.TuningProfile,
    enabled: Boolean,
    onSelect: (PitchDetector.TuningProfile) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A252F)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Инструмент и строй", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(selected.displayName, color = Color.White, fontWeight = FontWeight.Bold)
            Text(selected.notesText, color = Color(0xFF2ECC71), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PitchDetector.TUNING_PROFILES.forEach { profile ->
                    val isSelected = profile.id == selected.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2C3E50))
                            .clickable(enabled = enabled) { onSelect(profile) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            profile.displayName,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (selected) Color.White else Color.Gray

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Medium)
    }
}

private class PitchSmoother(
    private val highAccuracy: Boolean
) {
    private val alpha = if (highAccuracy) 0.10f else 0.18f
    private val freqHistory = ArrayDeque<Float>(5)
    private val stringHistory = ArrayDeque<String>(6)
    private var smoothedFreq = 0f
    private var smoothedCents = 0f
    private var smoothedConfidence = 0f
    private var stableLabel = ""
    private var stableStringNumber = 0
    private var stableTargetFrequency = 0f
    private val inTuneThreshold = if (highAccuracy) 5 else 3

    var inTuneCounter: Int = 0
        private set

    fun process(raw: PitchDetector.PitchResult): PitchDetector.PitchResult {
        freqHistory.addLast(raw.frequency)
        if (freqHistory.size > 5) freqHistory.removeFirst()

        stringHistory.addLast(raw.targetLabel)
        if (stringHistory.size > 6) stringHistory.removeFirst()

        val medianFreq = if (freqHistory.size >= 3) {
            val sorted = freqHistory.toList().sorted()
            sorted[sorted.size / 2]
        } else {
            raw.frequency
        }

        if (smoothedFreq > 0f && abs(medianFreq - smoothedFreq) / smoothedFreq > 0.18f) {
            smoothedFreq = medianFreq
            smoothedCents = raw.centsOff.toFloat()
            smoothedConfidence = raw.confidence
            inTuneCounter = 0
        }

        smoothedFreq = if (smoothedFreq == 0f) medianFreq else smoothedFreq + alpha * (medianFreq - smoothedFreq)
        smoothedCents = if (smoothedCents == 0f) raw.centsOff.toFloat() else smoothedCents + alpha * (raw.centsOff - smoothedCents)
        smoothedConfidence = if (smoothedConfidence == 0f) raw.confidence else smoothedConfidence + alpha * (raw.confidence - smoothedConfidence)

        val mostCommon = stringHistory.groupingBy { it }.eachCount().maxByOrNull { it.value }
        if (mostCommon != null && mostCommon.value >= stringHistory.size / 2) {
            stableLabel = mostCommon.key
            if (stableLabel == raw.targetLabel) {
                stableStringNumber = raw.stringNumber
                stableTargetFrequency = raw.targetFrequency
            }
        }

        val roundedCents = smoothedCents.toInt().coerceIn(-50, 50)
        val tolerance = if (highAccuracy) 3 else 5
        val confidentEnough = smoothedConfidence >= if (highAccuracy) 0.62f else 0.52f
        val rawInTune = abs(roundedCents) <= tolerance && confidentEnough
        inTuneCounter = if (rawInTune) inTuneCounter + 1 else 0
        val confirmedInTune = inTuneCounter >= inTuneThreshold

        val label = stableLabel.ifEmpty { raw.targetLabel }
        val noteName = label.dropLast(1)
        val octave = label.lastOrNull()?.digitToIntOrNull() ?: raw.octave

        return raw.copy(
            frequency = smoothedFreq,
            noteName = noteName,
            octave = octave,
            centsOff = roundedCents,
            inTune = confirmedInTune,
            confidence = smoothedConfidence,
            targetFrequency = if (stableTargetFrequency > 0f) stableTargetFrequency else raw.targetFrequency,
            stringNumber = if (stableStringNumber > 0) stableStringNumber else raw.stringNumber,
            targetLabel = label
        )
    }

    fun reset() {
        freqHistory.clear()
        stringHistory.clear()
        smoothedFreq = 0f
        smoothedCents = 0f
        smoothedConfidence = 0f
        stableLabel = ""
        stableStringNumber = 0
        stableTargetFrequency = 0f
        inTuneCounter = 0
    }
}

@Composable
private fun AutoTunerContent(
    prefsManager: PreferencesManager,
    tuning: PitchDetector.TuningProfile
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recorder = remember { AudioRecorder(highSensitivity = true, gain = 2.5f) }
    val beepPlayer = remember { SimpleTonePlayer() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var calibrationHz by remember { mutableIntStateOf(prefsManager.tunerCalibrationHz) }
    var highAccuracy by remember { mutableStateOf(prefsManager.tunerHighAccuracy) }
    var smoothedResult by remember { mutableStateOf<PitchDetector.PitchResult?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var microphoneMessage by remember { mutableStateOf("") }
    var lastBeepTime by remember { mutableLongStateOf(0L) }
    var wasInTune by remember { mutableStateOf(false) }
    val smoother = remember(highAccuracy) { PitchSmoother(highAccuracy) }

    fun startListening() {
        smoothedResult = null
        wasInTune = false
        microphoneMessage = ""
        smoother.reset()
        val started = recorder.start { samples ->
            val result = PitchDetector.analyze(samples, calibrationHz, highAccuracy, tuning)
            mainHandler.post {
                if (recorder.isActive() && result != null) {
                    smoothedResult = smoother.process(result)
                }
            }
        }
        if (started) {
            isListening = true
        } else {
            microphoneMessage = "Не удалось открыть микрофон. Проверьте разрешение или закройте другое приложение с записью."
        }
    }

    fun stopListening() {
        isListening = false
        recorder.stop()
        smoothedResult = null
        wasInTune = false
        microphoneMessage = ""
        smoother.reset()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recorder.stop()
                beepPlayer.stop()
                isListening = false
                smoothedResult = null
                wasInTune = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            recorder.stop()
            beepPlayer.stop()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(smoothedResult?.inTune) {
        val currentInTune = smoothedResult?.inTune == true
        if (currentInTune && !wasInTune) {
            val now = System.currentTimeMillis()
            if (now - lastBeepTime > 3000L) {
                beepPlayer.playBeep()
                lastBeepTime = now
            }
        }
        wasInTune = currentInTune
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Авто-режим цепляется к ближайшей струне выбранного строя",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        TunerSettingsStrip(
            calibrationHz = calibrationHz,
            highAccuracy = highAccuracy,
            enabled = !isListening,
            onCalibrationChange = {
                calibrationHz = it.coerceIn(430, 450)
                prefsManager.tunerCalibrationHz = calibrationHz
                smoother.reset()
            },
            onHighAccuracyChange = {
                highAccuracy = it
                prefsManager.tunerHighAccuracy = it
                smoother.reset()
            }
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (microphoneMessage.isNotBlank()) {
            Text(
                microphoneMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF39C12),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        if (!isListening) {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startListening()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Начать настройку")
            }
        } else {
            Button(
                onClick = { stopListening() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C))
            ) {
                Text("Стоп")
            }
            Spacer(modifier = Modifier.height(22.dp))

            smoothedResult?.let { result ->
                TunerNeedle(centsOff = result.centsOff, inTune = result.inTune)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    result.targetLabel,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (result.inTune) Color(0xFF2ECC71) else Color.White
                )
                Text(
                    "Струна ${result.stringNumber}  •  ${result.centsOff} cents",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (result.inTune) Color(0xFF2ECC71) else Color.Gray
                )
                Text(
                    "${"%.1f".format(result.frequency)} Hz  →  ${"%.1f".format(result.targetFrequency)} Hz",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                LinearProgressIndicator(
                    progress = result.confidence.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .padding(top = 8.dp),
                    color = if (result.confidence > 0.62f) Color(0xFF2ECC71) else Color(0xFFF39C12),
                    trackColor = Color(0xFF34495E)
                )
                Text(
                    "Уверенность: ${(result.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (result.inTune) {
                    Text("Струна настроена!", color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        if (result.centsOff < 0) "Подтяните струну" else "Ослабьте струну",
                        color = Color(0xFFF39C12),
                        fontWeight = FontWeight.Bold
                    )
                }
            } ?: Text("Слушаю... Дёрните струну", color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(26.dp))
        Text(
            "Строй: ${tuning.notesText}  •  A4=$calibrationHz Hz",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun TunerSettingsStrip(
    calibrationHz: Int,
    highAccuracy: Boolean,
    enabled: Boolean,
    onCalibrationChange: (Int) -> Unit,
    onHighAccuracyChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A252F)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Высокая точность",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = highAccuracy,
                    onCheckedChange = onHighAccuracyChange,
                    enabled = enabled
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Калибровка A4: $calibrationHz Hz", color = Color.Gray, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onCalibrationChange(calibrationHz - 1) },
                    enabled = enabled && calibrationHz > 430
                ) {
                    Text("-1")
                }
                Slider(
                    value = calibrationHz.toFloat(),
                    onValueChange = { onCalibrationChange(it.toInt()) },
                    valueRange = 430f..450f,
                    steps = 19,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onCalibrationChange(calibrationHz + 1) },
                    enabled = enabled && calibrationHz < 450
                ) {
                    Text("+1")
                }
            }
        }
    }
}

@Composable
private fun ManualTunerContent(tuning: PitchDetector.TuningProfile) {
    val simpleTone = remember { SimpleTonePlayer() }
    var playingString by remember { mutableStateOf<PitchDetector.TuningString?>(null) }

    DisposableEffect(Unit) {
        onDispose { simpleTone.stop() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Нажмите на струну — приложение воспроизведёт эталонный звук",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tuning.strings.take((tuning.strings.size + 1) / 2).forEach { string ->
                    StringButton(
                        guitarString = string,
                        isPlaying = playingString == string,
                        onClick = {
                            if (playingString == string) {
                                simpleTone.stop()
                                playingString = null
                            } else {
                                simpleTone.play(string.frequencyAtA440) {
                                    if (playingString == string) playingString = null
                                }
                                playingString = string
                            }
                        }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tuning.strings.drop((tuning.strings.size + 1) / 2).forEach { string ->
                    StringButton(
                        guitarString = string,
                        isPlaying = playingString == string,
                        onClick = {
                            if (playingString == string) {
                                simpleTone.stop()
                                playingString = null
                            } else {
                                simpleTone.play(string.frequencyAtA440) {
                                    if (playingString == string) playingString = null
                                }
                                playingString = string
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        playingString?.let { string ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Воспроизводится:", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Text(
                        string.label,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2ECC71)
                    )
                    Text(
                        "Струна ${string.stringNumber}  -  ${"%.2f".format(string.frequencyAtA440)} Hz",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        } ?: Text("Выберите струну для воспроизведения", color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        if (playingString != null) {
            Button(
                onClick = {
                    simpleTone.stop()
                    playingString = null
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Стоп")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("${tuning.displayName}: ${tuning.notesText}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun StringButton(
    guitarString: PitchDetector.TuningString,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isPlaying) MaterialTheme.colorScheme.primary else Color(0xFF2C3E50)
    val borderColor = if (isPlaying) Color(0xFF2ECC71) else Color(0xFF34495E)

    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${guitarString.stringNumber}", fontSize = 12.sp, color = Color.Gray)
            Text(
                guitarString.label,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "${"%.0f".format(guitarString.frequencyAtA440)} Hz",
                fontSize = 11.sp,
                color = if (isPlaying) Color(0xFF2ECC71) else Color.Gray
            )
        }
    }
}

@Composable
fun TunerNeedle(centsOff: Int, inTune: Boolean = false) {
    val angle = (centsOff / 50f * 45f).coerceIn(-45f, 45f)
    val animatedAngle by animateFloatAsState(
        targetValue = angle,
        animationSpec = tween(420, easing = LinearOutSlowInEasing)
    )
    val needleColor = if (inTune) Color(0xFF2ECC71) else Color(0xFFE74C3C)

    Canvas(
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(Color(0xFF2C3E50))
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = minOf(cx, cy) - 20
        drawCircle(Color(0xFF34495E), radius = radius, center = Offset(cx, cy))
        drawLine(Color.White, Offset(cx, cy), Offset(cx, cy - radius), strokeWidth = 4f)
        val needleAngle = Math.toRadians(animatedAngle.toDouble())
        val nx = cx + (radius * 0.9 * sin(needleAngle)).toFloat()
        val ny = cy - (radius * 0.9 * kotlin.math.cos(needleAngle)).toFloat()
        drawLine(needleColor, Offset(cx, cy), Offset(nx, ny), strokeWidth = 6f)
    }
}
