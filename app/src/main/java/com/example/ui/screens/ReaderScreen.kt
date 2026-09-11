package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.TtsPlaybackState
import com.example.ui.MainViewModel
import com.example.ui.theme.StatusPlaying
import com.example.ui.theme.StudioPrimary

@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val isFloatingBubbleActive by viewModel.isFloatingBubbleActive.collectAsStateWithLifecycle()

    var showPermissionDialog by remember { mutableStateOf(false) }
    val paragraphListState = rememberLazyListState()

    // Auto-scroll to currently playing paragraph
    LaunchedEffect(ttsState.currentParagraphIndex) {
        if (ttsState.currentParagraphIndex in ttsState.paragraphs.indices) {
            try {
                paragraphListState.animateScrollToItem(ttsState.currentParagraphIndex)
            } catch (ignored: Exception) {}
        }
    }

    // Overlay Permission Dialog
    if (showPermissionDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permiso de Superposición") },
            text = {
                Text("Para mostrar el círculo flotante sobre otras aplicaciones y leer el portapapeles con un toque, necesitas habilitar el permiso de superposición (Aparecer encima).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Abrir Ajustes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Floating Bubble Activation Card
        item {
            Spacer(modifier = Modifier.height(4.dp))
            FloatingBubbleCard(
                isActive = isFloatingBubbleActive,
                onToggle = { enable ->
                    viewModel.toggleFloatingBubble(enable) {
                        showPermissionDialog = true
                    }
                },
                onReadClipboard = {
                    viewModel.pasteFromClipboard()
                    viewModel.playCurrentText()
                }
            )
        }

        // 2. Text Input & Controls Area
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Texto a Reproducir",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(
                                onClick = { viewModel.pasteFromClipboard() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("paste_clipboard_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Pegar",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.clearInputText() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("clear_text_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpiar",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { viewModel.onInputTextChanged(it) },
                        placeholder = { Text("Escribe o pega aquí el texto que deseas que el TTS lea en voz alta...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag("tts_text_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Character & Paragraph stats
                    val paragraphsCount = inputText.split("\n+").count { it.trim().isNotEmpty() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${inputText.length} caracteres • $paragraphsCount párrafos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        TextButton(
                            onClick = { viewModel.openExportDialog(inputText) },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier.testTag("export_audio_action_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar Audio", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 3. Playback Controls Card
        item {
            PlaybackControlsCard(
                playbackState = ttsState.playbackState,
                currentParagraph = ttsState.currentParagraphIndex,
                totalParagraphs = ttsState.paragraphs.size,
                speechRate = ttsState.speechRate,
                pitch = ttsState.pitch,
                onPlayPause = { viewModel.togglePlayPauseTts() },
                onStop = { viewModel.stopTts() },
                onPrevious = { viewModel.previousParagraph() },
                onNext = { viewModel.nextParagraph() },
                onExport = { viewModel.openExportDialog(inputText) }
            )
        }

        // 4. Real-time Paragraph Flow Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (ttsState.playbackState == TtsPlaybackState.PLAYING) StatusPlaying else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lectura Párrafo a Párrafo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (ttsState.paragraphs.isNotEmpty()) {
                    val currentIdx = if (ttsState.currentParagraphIndex >= 0) ttsState.currentParagraphIndex + 1 else 0
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Párrafo $currentIdx / ${ttsState.paragraphs.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // 5. Paragraphs List Items
        val paragraphs = if (ttsState.paragraphs.isNotEmpty()) {
            ttsState.paragraphs
        } else {
            inputText.split("\n+").map { it.trim() }.filter { it.isNotEmpty() }
        }

        if (paragraphs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Escribe o pega texto arriba para ver el desglose por párrafos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(paragraphs) { index, paragraph ->
                val isCurrentlyPlaying = (ttsState.currentParagraphIndex == index && ttsState.playbackState != TtsPlaybackState.IDLE)
                ParagraphItemCard(
                    index = index,
                    text = paragraph,
                    isPlaying = isCurrentlyPlaying,
                    isPaused = isCurrentlyPlaying && ttsState.playbackState == TtsPlaybackState.PAUSED,
                    onClick = {
                        viewModel.jumpToParagraph(index)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FloatingBubbleCard(
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    onReadClipboard: () -> Unit
) {
    val cardBg by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "card_bg"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isActive) StudioPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .border(
                                2.dp,
                                if (isActive) Color.White else Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Círculo Flotante TTS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isActive) "Activo sobre otras apps" else "Desactivado",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isActive,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("floating_bubble_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Al hacer clic en el círculo flotante en cualquier pantalla o app, leerá inmediatamente el texto copiado en el portapapeles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = isActive) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onReadClipboard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_read_clipboard_btn")
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probar Lectura de Portapapeles Ahora")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackControlsCard(
    playbackState: TtsPlaybackState,
    currentParagraph: Int,
    totalParagraphs: Int,
    speechRate: Float,
    pitch: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Paragraph
                FilledTonalIconButton(
                    onClick = onPrevious,
                    enabled = currentParagraph > 0,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Párrafo Anterior")
                }

                // Play / Pause Button
                Button(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (playbackState == TtsPlaybackState.PLAYING) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("main_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playbackState == TtsPlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Reproducir / Pausa",
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Stop Button
                FilledTonalIconButton(
                    onClick = onStop,
                    enabled = playbackState != TtsPlaybackState.IDLE,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("main_stop_button")
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Detener", tint = MaterialTheme.colorScheme.error)
                }

                // Next Paragraph
                FilledTonalIconButton(
                    onClick = onNext,
                    enabled = totalParagraphs > 0 && currentParagraph < totalParagraphs - 1,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Párrafo Siguiente")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Info Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("Vel: ${String.format("%.2f", speechRate)}x") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text("Tono: ${String.format("%.2f", pitch)}x") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun ParagraphItemCard(
    index: Int,
    text: String,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isPlaying) StatusPlaying else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        label = "border_color"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        label = "bg_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(if (isPlaying) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("paragraph_card_$index"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Index & Status Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (isPlaying) StatusPlaying else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.Pause else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isPlaying) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
