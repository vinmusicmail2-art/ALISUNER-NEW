package ru.alisuner.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import ru.alisuner.app.audio.AcousticGuitarSampler
import ru.alisuner.app.data.ChordFingering
import ru.alisuner.app.data.ChordLibrary
import ru.alisuner.app.data.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(navController: NavController) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }

    var selectedKey by remember { mutableStateOf(prefsManager.learnSelectedKey) }
    val guitarSampler = remember { AcousticGuitarSampler(context) }
    var playingChordId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { guitarSampler.stop() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        // Заголовок
        Text(
            "Самоучитель",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            "Библиотека аккордов с диаграммами грифа",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))

        // ─── Выбор тональности (горизонтальная прокрутка) ───
        Text(
            "Тональность:",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChordLibrary.KEY_ORDER.forEach { key ->
                val isSelected = key == selectedKey
                val ruName = ChordLibrary.KEY_NAMES_RU[key] ?: key
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedKey = key; prefsManager.learnSelectedKey = key },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(key, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(ruName, fontSize = 10.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF2C3E50),
                        labelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ─── Список аккордов для выбранной тональности ───
        val chordGroups = remember(selectedKey) { ChordLibrary.getChordGroupsForKey(selectedKey) }
        val ruKeyName = ChordLibrary.KEY_NAMES_RU[selectedKey] ?: selectedKey

        Text(
            "$selectedKey ($ruKeyName)",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            chordGroups.forEach { (groupName, groupChords) ->
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        groupName,
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            groupName.contains("Мажор") -> Color(0xFF3498DB)
                            groupName.contains("Минор") && !groupName.contains("септ") && !groupName.contains("секст") -> Color(0xFFE67E22)
                            groupName.contains("Септ") || groupName.contains("септ") -> Color(0xFF9B59B6)
                            groupName.contains("Sus") -> Color(0xFF1ABC9C)
                            groupName.contains("Уменьш") -> Color(0xFF8E44AD)
                            groupName.contains("Увелич") -> Color(0xFFE74C3C)
                            groupName.contains("Секст") || groupName.contains("секст") -> Color(0xFFF39C12)
                            else -> Color(0xFF95A5A6)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                items(groupChords) { chord ->
                    ChordCard(
                        chord = chord,
                        isPlaying = playingChordId == "${chord.name}_${chord.position}",
                        onOpenClick = {
                            navController.navigate("chord_detail/${Uri.encode(chord.name)}")
                        },
                        onPlayClick = {
                            val id = "${chord.name}_${chord.position}"
                            if (playingChordId == id) {
                                guitarSampler.stop()
                                playingChordId = null
                            } else {
                                guitarSampler.playChord(chord.getFrequencies()) {
                                    if (playingChordId == id) playingChordId = null
                                }
                                playingChordId = id
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────── Карточка аккорда ───────────────────

@Composable
private fun ChordCard(
    chord: ChordFingering,
    isPlaying: Boolean,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Заголовок: название + позиция + кнопка воспроизведения
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        chord.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        chord.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }

                // Позиция
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (chord.position == 1) Color(0xFF27AE60) else Color(0xFF2980B9)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${chord.position}-я позиция",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Кнопка воспроизведения
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

            // Баррэ
            if (chord.barreFret > 0) {
                Text(
                    "Баррэ на ${chord.barreFret} ладу",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF39C12)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Диаграмма грифа
            ChordFretboardDiagram(chord)
        }
    }
}

// ─────────────────── Диаграмма грифа ───────────────────

@Composable
fun ChordFretboardDiagram(chord: ChordFingering) {
    val strings = 6
    // Определяем диапазон ладов для отображения
    val playedFrets = chord.fingers.filter { it > 0 }
    val minFret = if (playedFrets.isEmpty()) 0 else playedFrets.min()
    val maxFret = if (playedFrets.isEmpty()) 4 else playedFrets.max()
    val startFret = if (minFret <= 3) 0 else minFret - 1
    val displayFrets = maxOf(4, maxFret - startFret + 1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A252F))
    ) {
        val w = size.width
        val h = size.height
        val leftPad = 50f
        val rightPad = 30f
        val topPad = 30f
        val bottomPad = 20f
        val playableW = w - leftPad - rightPad
        val playableH = h - topPad - bottomPad

        val stringSpacing = playableH / (strings - 1)
        val fretSpacing = playableW / displayFrets

        // Номер начального лада (если не с нулевого)
        if (startFret > 0) {
            drawContext.canvas.nativeCanvas.drawText(
                "${startFret + 1}",
                leftPad - 30f,
                topPad + 14f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // Струны (горизонтальные линии) — 0=E4 сверху, 5=E2 снизу
        val stringNames = listOf("E4", "B3", "G3", "D3", "A2", "E2")
        for (i in 0 until strings) {
            val y = topPad + stringSpacing * i
            drawLine(Color(0xFF95A5A6), Offset(leftPad, y), Offset(w - rightPad, y), strokeWidth = if (i == 0) 3f else 2f)

            // Подпись струны слева
            drawContext.canvas.nativeCanvas.drawText(
                stringNames[i],
                leftPad - 30f,
                y + 5f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 20f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // Лады (вертикальные линии)
        for (i in 0..displayFrets) {
            val x = leftPad + fretSpacing * i
            val isNut = (startFret == 0 && i == 0)
            drawLine(
                if (isNut) Color.White else Color(0xFF7F8C8D),
                Offset(x, topPad),
                Offset(x, topPad + playableH),
                strokeWidth = if (isNut) 5f else 2f
            )
        }

        // Индикаторы: X (не играть) и O (открытая)
        for (i in 0 until strings) {
            val y = topPad + stringSpacing * i
            val fret = chord.fingers[i]
            if (fret == -1) {
                // X — не играть
                drawContext.canvas.nativeCanvas.drawText(
                    "X",
                    leftPad + fretSpacing * 0.15f,
                    y + 6f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(231, 76, 60)
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                )
            } else if (fret == 0 && startFret == 0) {
                // O — открытая струна
                val x = leftPad - 6f
                drawCircle(Color(0xFF2ECC71), radius = 8f, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
            }
        }

        // Точки пальцев на грифе
        for (i in 0 until strings) {
            val fret = chord.fingers[i]
            if (fret <= 0) continue

            val y = topPad + stringSpacing * i
            val fretIdx = fret - startFret
            // Центр между ладами
            val x = leftPad + fretSpacing * (fretIdx - 0.5f)

            drawCircle(Color(0xFFE74C3C), radius = 14f, center = Offset(x, y))

            // Номер лада внутри точки
            drawContext.canvas.nativeCanvas.drawText(
                "$fret",
                x,
                y + 6f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 18f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
            )
        }

        // Баррэ (горизонтальная полоса)
        if (chord.barreFret > 0) {
            val barreStrings = mutableListOf<Int>()
            for (i in 0 until strings) {
                if (chord.fingers[i] == chord.barreFret) {
                    barreStrings.add(i)
                }
            }
            if (barreStrings.size >= 2) {
                val fretIdx = chord.barreFret - startFret
                val x = leftPad + fretSpacing * (fretIdx - 0.5f)
                val yStart = topPad + stringSpacing * barreStrings.first()
                val yEnd = topPad + stringSpacing * barreStrings.last()
                drawLine(
                    Color(0xFFE74C3C).copy(alpha = 0.6f),
                    Offset(x, yStart),
                    Offset(x, yEnd),
                    strokeWidth = 12f
                )
            }
        }
    }
}
