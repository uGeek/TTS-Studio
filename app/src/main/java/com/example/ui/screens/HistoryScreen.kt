package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioPlayerState
import com.example.data.local.ExportedAudioEntity
import com.example.data.repository.TelegramUser
import com.example.ui.AudioSortBy
import com.example.ui.MainViewModel
import com.example.ui.theme.WebDavSyncColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val exportedAudios by viewModel.exportedAudios.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val formatFilter by viewModel.formatFilter.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedAudioIds.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val localAudioDir by viewModel.localAudioDirectory.collectAsStateWithLifecycle()
    val telegramConfig by viewModel.telegramConfig.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ExportedAudioEntity?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showDirectoryConfigDialog by remember { mutableStateOf(false) }
    var customDirInput by remember(localAudioDir) { mutableStateOf(localAudioDir) }
    var shareTelegramAudio by remember { mutableStateOf<ExportedAudioEntity?>(null) }

    // Directory Configuration Dialog
    if (showDirectoryConfigDialog) {
        AlertDialog(
            onDismissRequest = { showDirectoryConfigDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Directorio de Grabaciones")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Especifica la ruta del directorio en el almacenamiento local del dispositivo donde se guardarán las grabaciones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customDirInput,
                        onValueChange = { customDirInput = it },
                        label = { Text("Ruta del directorio") },
                        placeholder = { Text("/storage/emulated/0/Music/TTS_Audios") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        customDirInput = ""
                    }) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restablecer a ruta predeterminada")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setCustomAudioDirectory(customDirInput)
                    showDirectoryConfigDialog = false
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectoryConfigDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Single item delete dialog
    itemToDelete?.let { audio ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Eliminar grabación") },
            text = { Text("¿Deseas eliminar permanentemente '${audio.title}' (${audio.format}) del dispositivo?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAudioItem(audio)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    // Batch delete confirm dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Eliminar selección") },
            text = { Text("¿Deseas eliminar ${selectedIds.size} grabaciones seleccionadas?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedAudios()
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar ${selectedIds.size}")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    // Telegram User Picker for sharing
    shareTelegramAudio?.let { audio ->
        AlertDialog(
            onDismissRequest = { shareTelegramAudio = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color(0xFF2AABEE))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviar por Telegram")
                }
            },
            text = {
                Column {
                    if (telegramConfig.users.isEmpty()) {
                        Text(
                            text = "No tienes usuarios de Telegram configurados. Añade usuarios en la pestaña 'Ajustes' con formato Nombre,ID.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Selecciona el destinatario para enviar '${audio.title}':",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            telegramConfig.users.forEach { user ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.sendAudioViaTelegram(audio, user)
                                            shareTelegramAudio = null
                                        },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = user.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "ID: ${user.chatId}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color(0xFF2AABEE))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { shareTelegramAudio = null }) { Text("Cerrar") }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Title & Sort
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Historial de Grabaciones",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (exportedAudios.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(imageVector = Icons.Default.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Más recientes primero") },
                                onClick = {
                                    viewModel.sortBy.value = AudioSortBy.DATE_DESC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Más antiguos primero") },
                                onClick = {
                                    viewModel.sortBy.value = AudioSortBy.DATE_ASC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Nombre (A-Z)") },
                                onClick = {
                                    viewModel.sortBy.value = AudioSortBy.NAME_ASC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tamaño de archivo") },
                                onClick = {
                                    viewModel.sortBy.value = AudioSortBy.SIZE_DESC
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 1. Local Storage Directory Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Directorio de Grabaciones",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val displayPath = if (localAudioDir.isNotBlank()) localAudioDir else "Almacenamiento interno de la app (/audio_exports)"
                            Text(
                                text = displayPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { viewModel.openAudioFolderInFileManager() }) {
                            Icon(
                                imageVector = Icons.Default.Launch,
                                contentDescription = "Abrir carpeta",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            customDirInput = localAudioDir
                            showDirectoryConfigDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Cambiar directorio",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 2. Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Buscar por título o contenido del texto...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audio_search_input"),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }

        // 3. Format Filters (ALL, MP3, WAV, M4A, OGG, OPUS) & Batch Action Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL" to "Todos", "MP3" to "MP3", "WAV" to "WAV", "M4A" to "M4A", "OGG" to "OGG", "OPUS" to "OPUS").forEach { (code, label) ->
                        FilterChip(
                            selected = formatFilter == code,
                            onClick = { viewModel.formatFilter.value = code },
                            label = { Text(label) }
                        )
                    }
                }

                if (selectedIds.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { showBatchDeleteConfirm = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(${selectedIds.size})")
                    }
                }
            }
        }

        // 4. Audio List
        if (exportedAudios.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No se encontraron audios con ese filtro." else "No hay grabaciones exportadas todavía.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Exporta audios en formatos MP3, WAV, M4A, OGG o OPUS desde el Lector.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(exportedAudios, key = { it.id }) { audio ->
                val isSelected = selectedIds.contains(audio.id)

                AudioHistoryCard(
                    audio = audio,
                    isSelected = isSelected,
                    playerState = playerState,
                    onToggleSelect = { viewModel.toggleSelectAudio(audio.id) },
                    onPlayPause = { viewModel.toggleAudioItemPlayback(audio) },
                    onSeek = { viewModel.seekAudioPlayer(it) },
                    onShareGeneral = { viewModel.shareAudioFile(audio) },
                    onShareTelegram = { shareTelegramAudio = audio },
                    onSyncWebDav = { viewModel.syncSingleAudioToWebDav(audio) },
                    onDelete = { itemToDelete = audio }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AudioHistoryCard(
    audio: ExportedAudioEntity,
    isSelected: Boolean,
    playerState: AudioPlayerState,
    onToggleSelect: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onShareGeneral: () -> Unit,
    onShareTelegram: () -> Unit,
    onSyncWebDav: () -> Unit,
    onDelete: () -> Unit
) {
    val isPlayingThis = playerState.currentAudioId == audio.id
    val dateStr = remember(audio.createdAt) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(audio.createdAt))
    }

    val formatColor = when (audio.format.uppercase()) {
        "MP3" -> Color(0xFFEF4444)
        "WAV" -> Color(0xFF3B82F6)
        "M4A" -> Color(0xFF10B981)
        "OGG" -> Color(0xFFF59E0B)
        "OPUS" -> Color(0xFF8B5CF6)
        else -> MaterialTheme.colorScheme.primary
    }

    var showShareMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                if (isSelected) 2.dp else if (isPlayingThis) 1.5.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else if (isPlayingThis) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(18.dp)
            )
            .testTag("audio_card_${audio.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isPlayingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Selection Checkbox, Title, Format Badge, Synced Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = audio.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$dateStr • ${formatFileSize(audio.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Format badge
                Box(
                    modifier = Modifier
                        .background(formatColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = audio.format,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = formatColor
                    )
                }

                // WebDAV synced icon indicator
                if (audio.isSyncedToWebDav) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Sincronizado en WebDAV",
                        tint = WebDavSyncColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Text Preview Snippet
            if (audio.originalText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = audio.originalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            // Player Seekbar if this audio is playing/active
            if (isPlayingThis) {
                Spacer(modifier = Modifier.height(10.dp))
                Column {
                    val duration = if (playerState.durationMs > 0) playerState.durationMs else maxOf(1L, audio.durationMs)
                    val progress = (playerState.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)

                    Slider(
                        value = progress,
                        onValueChange = { frac ->
                            onSeek((frac * duration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(playerState.currentPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Button
                FilledTonalButton(
                    onClick = onPlayPause,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("play_audio_${audio.id}")
                ) {
                    Icon(
                        imageVector = if (isPlayingThis && playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPlayingThis && playerState.isPlaying) "Pausar" else "Reproducir")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Share icon with options (Any App / Telegram)
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir audio",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Compartir con cualquier app...") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                                },
                                onClick = {
                                    showShareMenu = false
                                    onShareGeneral()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Enviar a Telegram...") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color(0xFF2AABEE))
                                },
                                onClick = {
                                    showShareMenu = false
                                    onShareTelegram()
                                }
                            )
                        }
                    }

                    // WebDAV Upload
                    IconButton(onClick = onSyncWebDav) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Subir a WebDAV",
                            tint = if (audio.isSyncedToWebDav) WebDavSyncColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Telegram Share Icon (Direct Send)
                    IconButton(
                        onClick = onShareTelegram,
                        modifier = Modifier.testTag("telegram_send_button_${audio.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Enviar por Telegram",
                            tint = Color(0xFF2AABEE)
                        )
                    }

                    // Delete
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    } else {
        String.format(Locale.getDefault(), "%.1f KB", kb)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}
