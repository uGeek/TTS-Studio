package com.example.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.TelegramConfig
import com.example.data.repository.WebDavConfig
import com.example.sync.WebDavItem
import com.example.ui.MainViewModel
import com.example.ui.theme.WebDavSyncColor
import kotlinx.coroutines.launch

@Composable
fun AjustesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val webDavConfig by viewModel.webDavConfig.collectAsStateWithLifecycle()
    val telegramConfig by viewModel.telegramConfig.collectAsStateWithLifecycle()
    val syncState by viewModel.webDavSyncState.collectAsStateWithLifecycle()
    val exportedAudios by viewModel.exportedAudios.collectAsStateWithLifecycle()

    // Telegram State
    var botToken by remember(telegramConfig.botToken) { mutableStateOf(telegramConfig.botToken) }
    var rawUsersCsv by remember(telegramConfig.rawUsersCsv) { mutableStateOf(telegramConfig.rawUsersCsv) }
    var showToken by remember { mutableStateOf(false) }

    // WebDAV State
    var serverUrl by remember(webDavConfig.serverUrl) { mutableStateOf(webDavConfig.serverUrl) }
    var remoteDir by remember(webDavConfig.remoteDirectory) { mutableStateOf(webDavConfig.remoteDirectory) }
    var username by remember(webDavConfig.username) { mutableStateOf(webDavConfig.username) }
    var password by remember(webDavConfig.password) { mutableStateOf(webDavConfig.password) }
    var autoSync by remember(webDavConfig.autoSyncOnExport) { mutableStateOf(webDavConfig.autoSyncOnExport) }
    var isWebDavEnabled by remember(webDavConfig.isEnabled) { mutableStateOf(webDavConfig.isEnabled) }
    var showPassword by remember { mutableStateOf(false) }

    var showDirectoryBrowser by remember { mutableStateOf(false) }

    val unsyncedCount = exportedAudios.count { !it.isSyncedToWebDav }

    // Remote Directory Browser Dialog
    if (showDirectoryBrowser) {
        RemoteDirectoryPickerDialog(
            initialPath = remoteDir.ifBlank { "/" },
            viewModel = viewModel,
            onDismiss = { showDirectoryBrowser = false },
            onSelectDirectory = { selectedPath ->
                remoteDir = selectedPath
                showDirectoryBrowser = false
                val updated = WebDavConfig(
                    serverUrl = serverUrl,
                    remoteDirectory = selectedPath,
                    username = username,
                    password = password,
                    autoSyncOnExport = autoSync,
                    isEnabled = true
                )
                viewModel.updateWebDavConfig(updated)
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Ajustes e Integraciones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ================= SECTION 1: TELEGRAM BOT CONFIG =================
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
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color(0xFF2AABEE),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Integración Telegram Bot",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (telegramConfig.botToken.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2AABEE).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Configurado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2AABEE),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Envía grabaciones de audio generadas directamente a usuarios o grupos de Telegram mediante tu Bot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bot Token
                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it },
                        label = { Text("Token del Bot de Telegram") },
                        placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ") },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showToken) "Ocultar token" else "Mostrar token"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("telegram_bot_token_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Users CSV Textarea
                    OutlinedTextField(
                        value = rawUsersCsv,
                        onValueChange = { rawUsersCsv = it },
                        label = { Text("Usuarios (Nombre,ID)") },
                        placeholder = { Text("angel,584622\nmaria,987654") },
                        supportingText = {
                            Text("Formato CSV: un usuario por línea con 'nombre,id_telegram' (Ejemplo: angel,584622)")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp)
                            .testTag("telegram_users_input"),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testTelegramBot(botToken) },
                            enabled = botToken.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Probar Bot")
                        }

                        Button(
                            onClick = {
                                viewModel.updateTelegramConfig(
                                    TelegramConfig(botToken = botToken, rawUsersCsv = rawUsersCsv)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar")
                        }
                    }
                }
            }
        }

        // ================= SECTION 2: WEBDAV CLOUD SYNC CONFIG =================
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
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = WebDavSyncColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Servidor WebDAV",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = isWebDavEnabled,
                            onCheckedChange = { isWebDavEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WebDavSyncColor,
                                checkedTrackColor = WebDavSyncColor.copy(alpha = 0.4f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sincroniza tus archivos de audio con tu servidor local (ej. HTTP en tu LAN) o Nextcloud/Synology.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Server URL
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("URL del Servidor WebDAV") },
                        placeholder = { Text("http://192.168.1.50:8080/webdav") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lan, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("webdav_url_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Remote Directory with Browse Icon
                    OutlinedTextField(
                        value = remoteDir,
                        onValueChange = { remoteDir = it },
                        label = { Text("Directorio Remoto de Subida") },
                        placeholder = { Text("/TTS_Audios/") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Folder, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (serverUrl.isBlank()) {
                                        viewModel.showSnackbar("Ingresa la URL del servidor WebDAV primero")
                                    } else {
                                        showDirectoryBrowser = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Explorar directorios en el servidor",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("webdav_remote_dir_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Usuario (opcional si es anónimo)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña (opcional)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto sync toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sincronizar automáticamente al exportar audio",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = autoSync,
                            onCheckedChange = { autoSync = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons: Test Connection & Save & Sync All
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val current = WebDavConfig(
                                    serverUrl = serverUrl,
                                    remoteDirectory = remoteDir,
                                    username = username,
                                    password = password,
                                    autoSyncOnExport = autoSync,
                                    isEnabled = isWebDavEnabled
                                )
                                viewModel.updateWebDavConfig(current)
                                viewModel.testWebDavConnection()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Probar")
                        }

                        Button(
                            onClick = {
                                val updated = WebDavConfig(
                                    serverUrl = serverUrl,
                                    remoteDirectory = remoteDir,
                                    username = username,
                                    password = password,
                                    autoSyncOnExport = autoSync,
                                    isEnabled = isWebDavEnabled
                                )
                                viewModel.updateWebDavConfig(updated)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Guardar")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FilledTonalButton(
                        onClick = { viewModel.syncAllAudiosToWebDav() },
                        enabled = !syncState.isSyncing && exportedAudios.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (syncState.isSyncing) "Subiendo a WebDAV..." else "Sincronizar Todas las Grabaciones ($unsyncedCount pendientes)")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun RemoteDirectoryPickerDialog(
    initialPath: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSelectDirectory: (String) -> Unit
) {
    var currentPath by remember { mutableStateOf(initialPath.ifBlank { "/" }) }
    var items by remember { mutableStateOf<List<WebDavItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadPath(path: String) {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = viewModel.listRemoteWebDavDirectories(path)
            result.onSuccess { dirList ->
                items = dirList
                currentPath = path
                isLoading = false
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Error al listar directorios"
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPath(currentPath)
    }

    // New Folder Subdialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Crear Nueva Carpeta") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Nombre de la carpeta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val folderToCreate = newFolderName.trim()
                        if (folderToCreate.isNotBlank()) {
                            showNewFolderDialog = false
                            scope.launch {
                                val res = viewModel.createRemoteWebDavDirectory(currentPath, folderToCreate)
                                if (res.isSuccess) {
                                    loadPath(currentPath)
                                } else {
                                    viewModel.showSnackbar("Error al crear carpeta: ${res.exceptionOrNull()?.localizedMessage}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancelar") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Explorador WebDAV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { showNewFolderDialog = true }) {
                    Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "Nueva carpeta", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Current Path Breadcrumb Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )

                        if (currentPath != "/" && currentPath.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val parent = currentPath.trimEnd('/').substringBeforeLast('/', "")
                                    val target = if (parent.isBlank()) "/" else "$parent/"
                                    loadPath(target)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Subir nivel", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (errorMessage != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { loadPath(currentPath) }) {
                                Text("Reintentar")
                            }
                        }
                    }
                } else if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Text("No hay subcarpetas en este directorio.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val newTarget = if (item.path.endsWith("/")) item.path else "${item.path}/"
                                        loadPath(newTarget)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.name.ifBlank { item.path },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelectDirectory(currentPath) }) {
                Text("Seleccionar esta carpeta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
