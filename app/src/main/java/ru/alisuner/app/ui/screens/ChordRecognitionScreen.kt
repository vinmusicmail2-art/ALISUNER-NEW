package ru.alisuner.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.alisuner.app.audio.AcousticGuitarSampler
import ru.alisuner.app.audio.AudioRecorder
import ru.alisuner.app.audio.ChordRecognizer
import ru.alisuner.app.audio.PitchDetector
import ru.alisuner.app.data.ChordLibrary
import ru.alisuner.app.data.PreferencesManager

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun parseChordName(chord: String): Pair<String, String> {
    if (chord.isEmpty()) return Pair("", "")
    if (chord.length >= 2 && chord[1] == '#') {
        return Pair(chord.substring(0, 2), chord.substring(2))
    }
    return Pair(chord.substring(0, 1), chord.substring(1))
}

private fun transposeChord(chord: String, semitones: Int): String {
    val (root, suffix) = parseChordName(chord)
    val idx = NOTE_NAMES.indexOf(root)
    if (idx < 0) return chord
    val newIdx = ((idx + semitones) % 12 + 12) % 12
    return NOTE_NAMES[newIdx] + suffix
}

private fun transposeSequence(chords: List<String>, semitones: Int): List<String> =
    chords.map { transposeChord(it, semitones) }

@Composable
fun ChordRecognitionScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val recorder = remember { AudioRecorder(highSensitivity = true, gain = 3.0f) }
    val guitarSampler = remember { AcousticGuitarSampler(context) }
    val prefsManager = remember { PreferencesManager(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var isRecording by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val recordedSamples = remember { mutableListOf<ShortArray>() }

    var isAnalyzing by remember { mutableStateOf(false) }
    var detectedChords by remember { mutableStateOf<List<String>>(emptyList()) }
    var analysisMessage by remember { mutableStateOf("") }

    var transposeSemitones by remember { mutableIntStateOf(0) }
    val transposedChords = remember(detectedChords, transposeSemitones) {
        transposeSequence(detectedChords, transposeSemitones)
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPlayingIndex by remember { mutableIntStateOf(-1) }
    var selectedChordIndex by remember { mutableIntStateOf(-1) }

    val animatedLevel by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = tween(100)
    )

    val startRecording: () -> Unit = {
        recordedSamples.clear()
        detectedChords = emptyList()
        transposeSemitones = 0
        selectedChordIndex = -1
        analysisMessage = ""
        ChordRecognizer.reset()

        val started = recorder.start { samples ->
            val rms = PitchDetector.calculateRMS(samples)
            synchronized(recordedSamples) {
                recordedSamples.add(samples.copyOf())
            }
            mainHandler.post {
                if (recorder.isActive()) {
                    audioLevel = (rms * 8f).coerceIn(0f, 1f)
                }
            }
        }

        if (started) {
            isRecording = true
        } else {
            audioLevel = 0f
            analysisMessage = "Не удалось открыть микрофон. Проверьте разрешение или закройте другое приложение с записью."
        }
    }

    val stopAndAnalyze: () -> Unit = {
        isRecording = false
        recorder.stop()
        audioLevel = 0f

        val samplesSnapshot: List<ShortArray>
        synchronized(recordedSamples) {
            samplesSnapshot = recordedSamples.toList()
        }

        if (samplesSnapshot.isEmpty()) {
            analysisMessage = "Запись пуста. Попробуйте ещё раз."
        } else {
            isAnalyzing = true
            analysisMessage = "Анализирую запись..."

            coroutineScope.launch {
                val calibrationHz = prefsManager.tunerCalibrationHz
                val chords = withContext(Dispatchers.Default) {
                    analyzeRecordedAudio(samplesSnapshot, calibrationHz)
                }
                detectedChords = chords
                isAnalyzing = false
                analysisMessage = if (chords.isEmpty()) {
                    "Аккорды не обнаружены. Попробуйте записать громче и чётче."
                } else {
                    "Найдено: ${chords.size} аккорд(ов)"
                }
            }
        }
    }

    val playSequence: () -> Unit = {
        if (transposedChords.isNotEmpty() && !isPlaying) {
            isPlaying = true
            coroutineScope.launch {
                for ((index, chord) in transposedChords.withIndex()) {
                    if (!isPlaying) break
                    currentPlayingIndex = index

                    val fingering = ChordLibrary.getChord(chord)
                    if (fingering != null) {
                        withContext(Dispatchers.Main) {
                            guitarSampler.playChord(fingering.getFrequencies(), 2.0)
                        }
                        delay(2200L)
                    } else {
                        delay(1000L)
                    }
                }
                currentPlayingIndex = -1
                isPlaying = false
            }
        }
    }

    val stopPlayback: () -> Unit = {
        isPlaying = false
        currentPlayingIndex = -1
        guitarSampler.stop()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recorder.stop()
                guitarSampler.stop()
                isRecording = false
                audioLevel = 0f
                isPlaying = false
                currentPlayingIndex = -1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            recorder.stop()
            guitarSampler.stop()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                if (isRecording) recordingSeconds++
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Распознавание аккордов",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                "Запишите фрагмент на 6-струнной гитаре в стандартном строе E A D G B E",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        // SECTION 1: Recording
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isRecording) Color(0xFFE74C3C) else Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Запись",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (isRecording) {
                        Text(
                            "Запись: ${recordingSeconds}с",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFE74C3C),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Уровень сигнала:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1A252F))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedLevel)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            animatedLevel > 0.8f -> Color(0xFFE74C3C)
                                            animatedLevel > 0.5f -> Color(0xFF2ECC71)
                                            animatedLevel > 0.15f -> Color(0xFFF39C12)
                                            else -> Color(0xFF34495E)
                                        }
                                    )
                            )
                        }
                        Text(
                            when {
                                animatedLevel > 0.5f -> "Отличный сигнал"
                                animatedLevel > 0.15f -> "Поднесите гитару ближе"
                                else -> "Сигнал не обнаружен"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                animatedLevel > 0.5f -> Color(0xFF2ECC71)
                                animatedLevel > 0.15f -> Color(0xFFF39C12)
                                else -> Color.Gray
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    if (!isRecording) {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    startRecording()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = !isAnalyzing
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Записать фрагмент")
                        }
                    } else {
                        Button(
                            onClick = { stopAndAnalyze() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C))
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Остановить и определить аккорды")
                        }
                    }

                    if (isAnalyzing) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(analysisMessage, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    } else if (analysisMessage.isNotEmpty() && !isRecording) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            analysisMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (detectedChords.isEmpty()) Color(0xFFF39C12) else Color(0xFF2ECC71)
                        )
                    }
                }
            }
        }

        // SECTION 2: Detected chords + Transposition
        if (detectedChords.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Последовательность аккордов",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text("Оригинал:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            detectedChords.forEach { chord ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF34495E))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(chord, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Транспонирование:",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = { transposeSemitones-- },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34495E))
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "-1", tint = Color.White)
                            }

                            Spacer(Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A252F))
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when {
                                        transposeSemitones > 0 -> "+$transposeSemitones"
                                        transposeSemitones < 0 -> "$transposeSemitones"
                                        else -> "0"
                                    } + " полутон(ов)",
                                    color = if (transposeSemitones != 0) Color(0xFF3498DB) else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            IconButton(
                                onClick = { transposeSemitones++ },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34495E))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "+1", tint = Color.White)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5).forEach { s ->
                                val isSelected = transposeSemitones == s
                                SuggestionChip(
                                    onClick = { transposeSemitones = s },
                                    label = {
                                        Text(
                                            if (s > 0) "+$s" else "$s",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF34495E),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (transposeSemitones != 0) {
                            Text("Транспонировано:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF3498DB))
                        } else {
                            Text("Текущая тональность:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            transposedChords.forEachIndexed { index, chord ->
                                val isCurrent = index == currentPlayingIndex
                                val isChordSelected = index == selectedChordIndex
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isCurrent -> Color(0xFF2ECC71)
                                                isChordSelected -> Color(0xFF3498DB)
                                                else -> Color(0xFF1A252F)
                                            }
                                        )
                                        .then(
                                            if (isChordSelected) Modifier.border(
                                                2.dp,
                                                Color(0xFF3498DB),
                                                RoundedCornerShape(8.dp)
                                            ) else Modifier
                                        )
                                        .clickable { selectedChordIndex = if (selectedChordIndex == index) -1 else index }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(chord, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isPlaying) {
                                Button(
                                    onClick = { playSequence() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Воспроизвести")
                                }
                            } else {
                                Button(
                                    onClick = { stopPlayback() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C))
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Стоп")
                                }
                            }

                            if (transposeSemitones != 0) {
                                OutlinedButton(
                                    onClick = { transposeSemitones = 0 },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Сбросить", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: Fretboard diagrams
            item {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Piano, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Аппликатуры на грифе",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Нажмите на аккорд выше, чтобы показать отдельно, или листайте ниже",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            if (selectedChordIndex in transposedChords.indices) {
                item {
                    val chord = transposedChords[selectedChordIndex]
                    ChordDiagramCard(
                        chordName = chord,
                        index = selectedChordIndex,
                        isCurrentlyPlaying = selectedChordIndex == currentPlayingIndex,
                        guitarSampler = guitarSampler
                    )
                }
            } else {
                itemsIndexed(transposedChords) { index, chord ->
                    ChordDiagramCard(
                        chordName = chord,
                        index = index,
                        isCurrentlyPlaying = index == currentPlayingIndex,
                        guitarSampler = guitarSampler
                    )
                }
            }
        }

        if (detectedChords.isEmpty() && !isRecording && !isAnalyzing) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A252F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Как пользоваться:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("1. Нажмите \"Записать фрагмент\"", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("2. Сыграйте последовательность аккордов (3—15 секунд)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("3. Нажмите \"Остановить\" — аккорды будут определены", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("4. Транспонируйте в удобную тональность кнопками +/-", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("5. Послушайте результат и изучите аппликатуры на грифе", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("Советы:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("- Поднесите телефон ближе к гитаре (20—30 см)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("- Играйте каждый аккорд чётко, 1—2 секунды каждый", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("- Избегайте фонового шума", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ChordDiagramCard(
    chordName: String,
    index: Int,
    isCurrentlyPlaying: Boolean,
    guitarSampler: AcousticGuitarSampler
) {
    val fingering = ChordLibrary.getChord(chordName)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) Color(0xFF1B4332) else Color(0xFF2C3E50)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentlyPlaying) Color(0xFF2ECC71) else Color(0xFF34495E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(Modifier.width(10.dp))

                Text(
                    chordName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isCurrentlyPlaying) Color(0xFF2ECC71) else Color.White,
                    fontWeight = FontWeight.Bold
                )

                if (fingering != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(fingering.displayName, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }

                Spacer(Modifier.weight(1f))

                if (fingering != null) {
                    IconButton(
                        onClick = { guitarSampler.playChord(fingering.getFrequencies(), 2.0) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play $chordName",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (fingering != null) {
                if (fingering.barreFret > 0) {
                    Text(
                        "Баррэ на ${fingering.barreFret} ладу",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF39C12)
                    )
                    Spacer(Modifier.height(4.dp))
                }
                ChordFretboardDiagram(fingering)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A252F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Аппликатура для $chordName не найдена в библиотеке",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun analyzeRecordedAudio(samples: List<ShortArray>, calibrationHz: Int): List<String> {
    ChordRecognizer.reset()

    val timeline = mutableListOf<String>()

    for (chunk in samples) {
        val result = ChordRecognizer.recognizeChord(chunk, calibrationHz)
        if (result != null) {
            timeline.add(result.chordName)
        } else {
            timeline.add("")
        }
    }

    val sequence = mutableListOf<String>()
    var lastChord = ""
    for (chord in timeline) {
        if (chord.isNotEmpty() && chord != lastChord) {
            sequence.add(chord)
            lastChord = chord
        }
    }

    return sequence
}
