package ru.alisuner.app.ui.screens
 
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ru.alisuner.app.audio.Metronome
import ru.alisuner.app.data.PreferencesManager
 
/** Доступные размеры такта */
private data class TimeSignature(
    val label: String,       // "2/4", "3/4", "4/4", "6/8"
    val beatsPerBar: Int     // кол-во визуальных долей
)

private val timeSignatures = listOf(
    TimeSignature("2/4", 2),
    TimeSignature("3/4", 3),
    TimeSignature("4/4", 4),
    TimeSignature("6/8", 6)
)

@Composable
fun MetronomeScreen() {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }

    var bpm by remember { mutableIntStateOf(prefsManager.metronomeBpm) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentBeat by remember { mutableIntStateOf(0) }
    var volume by remember { mutableFloatStateOf(prefsManager.metronomeVolume) }
    var selectedTimeSig by remember { mutableStateOf(prefsManager.metronomeTimeSignature) }

    val currentTimeSig = timeSignatures.find { it.label == selectedTimeSig } ?: timeSignatures[2] // 4/4 по умолчанию
    val metronome = remember { Metronome(bpm, currentTimeSig.beatsPerBar) }

    val scale by animateFloatAsState(
        targetValue = if (currentBeat == 1) 1.2f else 1f,
        animationSpec = tween(50)
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                metronome.stop()
                isPlaying = false
                currentBeat = 0
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            metronome.stop()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(metronome) {
        metronome.setCallback { beat, _ ->
            currentBeat = beat
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            metronome.bpm = bpm
            metronome.start()
        } else {
            metronome.stop()
            currentBeat = 0
        }
    }
    LaunchedEffect(bpm) {
        if (isPlaying) {
            metronome.bpm = bpm
        }
    }
    LaunchedEffect(volume) {
        metronome.volume = volume
    }
    LaunchedEffect(selectedTimeSig) {
        metronome.beatsPerBar = currentTimeSig.beatsPerBar
        // Для 6/8: акцент на 1 и 4 долю (две группы по 3)
        metronome.accentBeats = if (selectedTimeSig == "6/8") setOf(1, 4) else setOf(1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Метроном", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))

        // ── Выбор размера такта ──
        Text("Размер", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            timeSignatures.forEach { ts ->
                val isSelected = ts.label == selectedTimeSig
                Button(
                    onClick = {
                        selectedTimeSig = ts.label
                        prefsManager.metronomeTimeSignature = ts.label
                    },
                    modifier = Modifier.height(40.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF34495E),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(ts.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // BPM контроль
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { bpm = (bpm - 1).coerceIn(40, 240); prefsManager.metronomeBpm = bpm }) {
                Icon(Icons.Default.Remove, contentDescription = "Меньше", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "$bpm",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = { bpm = (bpm + 1).coerceIn(40, 240); prefsManager.metronomeBpm = bpm }) {
                Icon(Icons.Default.Add, contentDescription = "Больше", tint = Color.White)
            }
        }
        Text("BPM", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        // ── Визуализация битов (кол-во зависит от размера) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val beats = currentTimeSig.beatsPerBar
            // Для 6/8: акцент на 1 и 4 долю (две группы по 3)
            val isCompound = selectedTimeSig == "6/8"
            (1..beats).forEach { beat ->
                val isAccentBeat = if (isCompound) (beat == 1 || beat == 4) else (beat == 1)
                val dotSize = if (beats <= 4) 48.dp else 36.dp
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .scale(if (currentBeat == beat) scale else 1f)
                        .clip(CircleShape)
                        .background(
                            when {
                                currentBeat == beat && isAccentBeat -> Color(0xFFE74C3C)
                                currentBeat == beat -> MaterialTheme.colorScheme.primary
                                else -> Color(0xFF34495E)
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Регулятор громкости
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VolumeDown, contentDescription = "Тише", tint = Color.Gray, modifier = Modifier.size(24.dp))
            Slider(
                value = volume,
                onValueChange = { volume = it; prefsManager.metronomeVolume = it },
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color(0xFF34495E)
                )
            )
            Icon(Icons.Default.VolumeUp, contentDescription = "Громче", tint = Color.Gray, modifier = Modifier.size(24.dp))
        }
        Text(
            "Громкость: ${(volume * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Кнопка старт/стоп
        Button(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPlaying) Color(0xFFE74C3C) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (isPlaying) "Стоп" else "Старт", fontSize = 20.sp)
        }
    }
}
