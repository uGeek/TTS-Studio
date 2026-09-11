package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.ui.ExportDialogState

data class FormatItem(
    val format: String,
    val description: String,
    val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportAudioDialog(
    state: ExportDialogState,
    onDismiss: () -> Unit,
    onExport: (title: String, format: String) -> Unit,
    onFormatSelected: (String) -> Unit
) {
    if (!state.isOpen) return

    var titleText by remember(state.defaultTitle) {
        mutableStateOf(state.defaultTitle.ifBlank { "Grabacion_TTS" })
    }

    val availableFormats = listOf(
        FormatItem("MP3", "Comprimido universal", Color(0xFFEF4444)),
        FormatItem("WAV", "PCM sin pérdida", Color(0xFF3B82F6)),
        FormatItem("M4A", "AAC alta compresión", Color(0xFF10B981)),
        FormatItem("OGG", "Vorbis abierto", Color(0xFFF59E0B)),
        FormatItem("OPUS", "Baja latencia voz", Color(0xFF8B5CF6))
    )

    AlertDialog(
        onDismissRequest = {
            if (!state.isExporting) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Exportar",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Exportar Audio TTS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Genera un archivo de audio local para reproducirlo sin conexión, compartirlo o sincronizarlo con WebDAV y Telegram.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Nombre del archivo") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "Formato de audio:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableFormats.forEach { item ->
                        val isSelected = state.selectedFormat.equals(item.format, ignoreCase = true)
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(bgColor)
                                .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                                .clickable { onFormatSelected(item.format) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(item.color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.format,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = item.color
                                    )
                                }
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Error Message if any
                AnimatedVisibility(visible = state.errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (state.isExporting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sintetizando y codificando audio...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExport(titleText, state.selectedFormat)
                },
                enabled = !state.isExporting && titleText.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_export_button")
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Exportar .${state.selectedFormat.lowercase()}")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isExporting
            ) {
                Text("Cancelar")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
