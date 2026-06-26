package ru.alisuner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import ru.alisuner.app.audio.AcousticGuitarSampler
import ru.alisuner.app.data.ChordFingering
import ru.alisuner.app.data.ChordLibrary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordDetailScreen(navController: NavController, chordId: String) {
    val context = LocalContext.current
    val chord1 = ChordLibrary.getChord(chordId, 1)
    val chord2 = ChordLibrary.getChord(chordId, 2)
    val chords = listOfNotNull(chord1, chord2)
    val guitarSampler = remember { AcousticGuitarSampler(context) }
    var playingPos by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { guitarSampler.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val displayName = chord1?.displayName ?: chord2?.displayName ?: chordId
                    Text(displayName)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (chords.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Аккорд не найден", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(chords) { chord ->
                    ChordPositionCard(
                        chord = chord,
                        isPlaying = playingPos == chord.position,
                        onPlayClick = {
                            if (playingPos == chord.position) {
                                guitarSampler.stop()
                                playingPos = 0
                            } else {
                                guitarSampler.playChord(chord.getFrequencies()) {
                                    if (playingPos == chord.position) playingPos = 0
                                }
                                playingPos = chord.position
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChordPositionCard(
    chord: ChordFingering,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${chord.position}-я позиция",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (chord.position == 1) Color(0xFF27AE60) else Color(0xFF2980B9),
                        fontWeight = FontWeight.Bold
                    )
                    if (chord.barreFret > 0) {
                        Text(
                            "Баррэ на ${chord.barreFret} ладу",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF39C12)
                        )
                    }
                }
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) Color(0xFFE74C3C) else MaterialTheme.colorScheme.primary
                        )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Стоп" else "Воспроизвести",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Используем диаграмму из LearnScreen
            ChordFretboardDiagram(chord)
        }
    }
}
