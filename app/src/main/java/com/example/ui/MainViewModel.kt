package com.example.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExportHelper
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioPlayerState
import com.example.audio.TtsEngineInfo
import com.example.audio.TtsManager
import com.example.audio.TtsPlaybackState
import com.example.audio.TtsState
import com.example.data.local.AppDatabase
import com.example.data.local.ExportedAudioEntity
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.TelegramConfig
import com.example.data.repository.TelegramUser
import com.example.data.repository.WebDavConfig
import com.example.service.FloatingBubbleService
import com.example.sync.TelegramSender
import com.example.sync.WebDavSyncManager
import com.example.sync.WebDavSyncState
import com.example.sync.WebDavTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class AudioSortBy {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    SIZE_DESC
}

data class ExportDialogState(
    val isOpen: Boolean = false,
    val defaultTitle: String = "",
    val textToExport: String = "",
    val selectedFormat: String = "MP3", // "MP3", "WAV", "M4A", "OGG", "OPUS"
    val isExporting: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val appSettings = AppSettingsRepository.getInstance(context)
    private val database = AppDatabase.getInstance(context)
    private val audioDao = database.exportedAudioDao()

    val ttsManager = TtsManager.getInstance(context)
    val audioPlayerManager = AudioPlayerManager.getInstance(context)
    val webDavSyncManager = WebDavSyncManager.getInstance(context)

    // TTS & Player state
    val ttsState: StateFlow<TtsState> = ttsManager.state
    val playerState: StateFlow<AudioPlayerState> = audioPlayerManager.playerState
    val webDavConfig: StateFlow<WebDavConfig> = appSettings.webDavConfig
    val telegramConfig: StateFlow<TelegramConfig> = appSettings.telegramConfig
    val webDavSyncState: StateFlow<WebDavSyncState> = webDavSyncManager.syncState
    val isFloatingBubbleActive: StateFlow<Boolean> = appSettings.isFloatingBubbleActive
    val localAudioDirectory: StateFlow<String> = appSettings.localAudioDirectory

    // Text Reader UI State
    val inputText = MutableStateFlow(
        "Bienvenido a TTS Studio. Esta aplicación lee textos en voz alta con cualquier motor TTS del sistema Android.\n\nPuedes activar el círculo flotante para leer el contenido de tu portapapeles en cualquier momento.\n\nTambién puedes exportar el audio generado a formatos MP3, WAV, M4A, OGG y OPUS para escucharlo sin conexión o enviarlo por Telegram y WebDAV."
    )

    // History Search and Filtering
    val searchQuery = MutableStateFlow("")
    val formatFilter = MutableStateFlow("ALL") // "ALL", "MP3", "WAV", "M4A", "OGG", "OPUS"
    val sortBy = MutableStateFlow(AudioSortBy.DATE_DESC)

    // Selected items for batch deletion
    private val _selectedAudioIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedAudioIds: StateFlow<Set<Long>> = _selectedAudioIds.asStateFlow()

    // Export Dialog State
    private val _exportDialogState = MutableStateFlow(ExportDialogState())
    val exportDialogState: StateFlow<ExportDialogState> = _exportDialogState.asStateFlow()

    // Active bottom navigation tab (0: Reader, 1: Motores, 2: Grabaciones, 3: Ajustes)
    val currentTab = MutableStateFlow(0)

    // Dark mode state: null = system, true = dark, false = light
    val isDarkMode = MutableStateFlow<Boolean?>(null)

    // Snackbar message
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val exportedAudios: StateFlow<List<ExportedAudioEntity>> = combine(
        searchQuery,
        formatFilter,
        sortBy
    ) { query, format, sort ->
        Triple(query, format, sort)
    }.flatMapLatest { (query, format, sort) ->
        audioDao.searchAndFilterAudios(
            query = query.trim(),
            format = format,
            sortBy = sort.name
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onInputTextChanged(newText: String) {
        inputText.value = newText
    }

    fun clearInputText() {
        inputText.value = ""
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrBlank()) {
                inputText.value = text
                showSnackbar("Texto pegado del portapapeles")
            } else {
                showSnackbar("El portapapeles está vacío")
            }
        } else {
            showSnackbar("El portapapeles está vacío")
        }
    }

    fun playCurrentText() {
        val text = inputText.value
        if (text.isBlank()) {
            showSnackbar("Ingresa o pega un texto para reproducir")
            return
        }
        ttsManager.playText(text)
    }

    fun togglePlayPauseTts() {
        val state = ttsState.value
        when (state.playbackState) {
            TtsPlaybackState.PLAYING -> ttsManager.pause()
            TtsPlaybackState.PAUSED -> ttsManager.resume()
            else -> playCurrentText()
        }
    }

    fun stopTts() {
        ttsManager.stop()
    }

    fun nextParagraph() {
        ttsManager.nextParagraph()
    }

    fun previousParagraph() {
        ttsManager.previousParagraph()
    }

    fun jumpToParagraph(index: Int) {
        ttsManager.jumpToParagraph(index)
    }

    fun setSpeechRate(rate: Float) {
        ttsManager.setSpeechRate(rate)
        val currentPrefs = appSettings.ttsPreferences.value
        appSettings.saveTtsPreferences(currentPrefs.copy(speechRate = rate))
    }

    fun setPitch(pitch: Float) {
        ttsManager.setPitch(pitch)
        val currentPrefs = appSettings.ttsPreferences.value
        appSettings.saveTtsPreferences(currentPrefs.copy(pitch = pitch))
    }

    fun selectTtsEngine(enginePackage: String) {
        ttsManager.switchEngine(enginePackage)
        val currentPrefs = appSettings.ttsPreferences.value
        appSettings.saveTtsPreferences(currentPrefs.copy(selectedEngine = enginePackage))
        showSnackbar("Motor TTS seleccionado: $enginePackage")
    }

    fun selectLanguage(locale: Locale) {
        ttsManager.setLanguage(locale)
        val currentPrefs = appSettings.ttsPreferences.value
        appSettings.saveTtsPreferences(currentPrefs.copy(languageCode = locale.toLanguageTag()))
    }

    fun selectVoice(voiceName: String) {
        ttsManager.setVoice(voiceName)
        val currentPrefs = appSettings.ttsPreferences.value
        appSettings.saveTtsPreferences(currentPrefs.copy(selectedVoiceName = voiceName))
    }

    fun setBlockSizeLimits(minChars: Int, maxChars: Int) {
        ttsManager.setBlockSizeLimits(minChars, maxChars)
        val currentPrefs = appSettings.ttsPreferences.value
        appSettings.saveTtsPreferences(currentPrefs.copy(minBlockChars = minChars, maxBlockChars = maxChars))
        showSnackbar("Tamaño de bloques configurado: $minChars - $maxChars caracteres")
    }

    fun testVoiceSpeech(sampleText: String = "Hola, esta es una prueba de voz con el motor TTS configurado.") {
        ttsManager.playText(sampleText)
    }

    // Floating Bubble Controls
    fun toggleFloatingBubble(enable: Boolean, onNeedPermission: () -> Unit) {
        if (enable) {
            if (Settings.canDrawOverlays(context)) {
                FloatingBubbleService.start(context)
                appSettings.setFloatingBubbleActive(true)
                showSnackbar("Círculo flotante activado")
            } else {
                onNeedPermission()
            }
        } else {
            FloatingBubbleService.stop(context)
            appSettings.setFloatingBubbleActive(false)
            showSnackbar("Círculo flotante desactivado")
        }
    }

    fun getEffectiveAudioDirectory(): File {
        val customPath = localAudioDirectory.value.trim()
        if (customPath.isNotEmpty()) {
            val customDir = File(customPath)
            if (customDir.exists() || customDir.mkdirs()) {
                return customDir
            }
        }
        val defaultDir = File(context.filesDir, "audio_exports")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir
    }

    fun setCustomAudioDirectory(path: String) {
        val cleanPath = path.trim()
        if (cleanPath.isNotEmpty()) {
            val dir = File(cleanPath)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        appSettings.setLocalAudioDirectory(cleanPath)
        showSnackbar(if (cleanPath.isEmpty()) "Directorio de grabaciones restaurado al predeterminado" else "Directorio de grabaciones actualizado")
    }

    // Export Dialog & Processing
    fun openExportDialog(text: String? = null) {
        val targetText = text ?: inputText.value
        if (targetText.isBlank()) {
            showSnackbar("No hay texto para exportar")
            return
        }
        // First line clean without weird symbols as requested
        val firstLine = targetText.trim().lines().firstOrNull { it.isNotBlank() } ?: "Grabacion_TTS"
        val cleanTitle = firstLine
            .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(50)
            .ifBlank { "Grabacion_TTS" }

        val lastFormat = appSettings.lastExportFormat.value

        _exportDialogState.value = ExportDialogState(
            isOpen = true,
            defaultTitle = cleanTitle,
            textToExport = targetText,
            selectedFormat = lastFormat
        )
    }

    fun closeExportDialog() {
        _exportDialogState.value = ExportDialogState(isOpen = false)
    }

    fun setExportFormat(format: String) {
        appSettings.setLastExportFormat(format)
        _exportDialogState.value = _exportDialogState.value.copy(selectedFormat = format)
    }

    fun executeExport(title: String, format: String) {
        val text = _exportDialogState.value.textToExport
        if (text.isBlank()) return

        appSettings.setLastExportFormat(format)
        _exportDialogState.value = _exportDialogState.value.copy(isExporting = true, errorMessage = null)

        viewModelScope.launch {
            val targetDir = getEffectiveAudioDirectory()
            val result = ttsManager.exportToFile(text, title, format, targetDir)
            result.onSuccess { file ->
                val durationMs = AudioExportHelper.getAudioDurationMs(file)
                val newAudio = ExportedAudioEntity(
                    title = title.ifBlank { file.nameWithoutExtension },
                    originalText = text,
                    filePath = file.absolutePath,
                    fileName = file.name,
                    format = format.uppercase(),
                    durationMs = durationMs,
                    fileSizeBytes = file.length(),
                    createdAt = System.currentTimeMillis()
                )
                val id = audioDao.insertAudio(newAudio)
                val savedAudio = newAudio.copy(id = id)

                _exportDialogState.value = ExportDialogState(isOpen = false)
                showSnackbar("Archivo exportado exitosamente: ${file.name}")

                // Auto sync if configured
                val config = webDavConfig.value
                if (config.isEnabled && config.autoSyncOnExport) {
                    launch(Dispatchers.IO) {
                        webDavSyncManager.uploadAudioFile(savedAudio, config)
                    }
                }
            }.onFailure { error ->
                _exportDialogState.value = _exportDialogState.value.copy(
                    isExporting = false,
                    errorMessage = error.localizedMessage ?: "Error al exportar audio"
                )
            }
        }
    }

    // Audio Playback in History
    fun toggleAudioItemPlayback(audio: ExportedAudioEntity) {
        audioPlayerManager.togglePlayPause(audio)
    }

    fun stopAudioItemPlayback() {
        audioPlayerManager.stop()
    }

    fun seekAudioPlayer(positionMs: Long) {
        audioPlayerManager.seekTo(positionMs)
    }

    // Delete single audio
    fun deleteAudioItem(audio: ExportedAudioEntity) {
        viewModelScope.launch {
            if (playerState.value.currentAudioId == audio.id) {
                audioPlayerManager.stop()
            }
            try {
                val file = File(audio.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            audioDao.deleteAudio(audio)
            showSnackbar("Grabación eliminada")
        }
    }

    // Batch deletion
    fun toggleSelectAudio(id: Long) {
        val current = _selectedAudioIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedAudioIds.value = current
    }

    fun selectAllAudios(audios: List<ExportedAudioEntity>) {
        _selectedAudioIds.value = audios.map { it.id }.toSet()
    }

    fun clearAudioSelection() {
        _selectedAudioIds.value = emptySet()
    }

    fun deleteSelectedAudios() {
        val ids = _selectedAudioIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            val all = exportedAudios.value.filter { ids.contains(it.id) }
            all.forEach { audio ->
                if (playerState.value.currentAudioId == audio.id) {
                    audioPlayerManager.stop()
                }
                try {
                    val file = File(audio.filePath)
                    if (file.exists()) file.delete()
                } catch (ignored: Exception) {}
            }
            audioDao.deleteMultiple(ids)
            _selectedAudioIds.value = emptySet()
            showSnackbar("${ids.size} grabaciones eliminadas")
        }
    }

    fun shareAudioFile(audio: ExportedAudioEntity) {
        try {
            val file = File(audio.filePath)
            if (!file.exists()) {
                showSnackbar("El archivo no existe")
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = when (audio.format.uppercase()) {
                "MP3" -> "audio/mpeg"
                "WAV" -> "audio/wav"
                "OGG" -> "audio/ogg"
                "OPUS" -> "audio/opus"
                "M4A" -> "audio/mp4"
                else -> "audio/*"
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, audio.title)
                putExtra(Intent.EXTRA_TEXT, "Audio generado con TTS Studio: ${audio.title}")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir audio").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            showSnackbar("Error al compartir: ${e.localizedMessage}")
        }
    }

    // Telegram Actions
    fun updateTelegramConfig(config: TelegramConfig) {
        appSettings.saveTelegramConfig(config)
        showSnackbar("Configuración de Telegram guardada")
    }

    fun testTelegramBot(token: String) {
        viewModelScope.launch {
            val result = TelegramSender.testBotToken(token)
            result.onSuccess { msg ->
                showSnackbar(msg)
            }.onFailure { err ->
                showSnackbar("Error Telegram: ${err.localizedMessage}")
            }
        }
    }

    fun sendAudioViaTelegram(audio: ExportedAudioEntity, user: TelegramUser) {
        val config = telegramConfig.value
        if (config.botToken.isBlank()) {
            showSnackbar("Configura el Token del Bot de Telegram en Ajustes")
            return
        }

        viewModelScope.launch {
            showSnackbar("Enviando '${audio.title}' a ${user.name} en Telegram...")
            val file = File(audio.filePath)
            val result = TelegramSender.sendAudio(
                botToken = config.botToken,
                chatId = user.chatId,
                audioFile = file,
                title = audio.title,
                caption = "Audio TTS: ${audio.title}"
            )
            result.onSuccess { msg ->
                showSnackbar(msg)
            }.onFailure { err ->
                showSnackbar("Fallo al enviar a Telegram: ${err.localizedMessage}")
            }
        }
    }

    // WebDAV Actions
    fun updateWebDavConfig(config: WebDavConfig) {
        appSettings.saveWebDavConfig(config)
        showSnackbar("Configuración WebDAV guardada")
    }

    fun testWebDavConnection() {
        viewModelScope.launch {
            val result = webDavSyncManager.testConnection(webDavConfig.value)
            showSnackbar(result.message)
        }
    }

    fun syncSingleAudioToWebDav(audio: ExportedAudioEntity) {
        viewModelScope.launch {
            showSnackbar("Subiendo ${audio.fileName} a WebDAV...")
            val success = webDavSyncManager.uploadAudioFile(audio, webDavConfig.value)
            if (success) {
                showSnackbar("${audio.fileName} subido con éxito")
            } else {
                showSnackbar("Error al subir ${audio.fileName}")
            }
        }
    }

    fun syncAllAudiosToWebDav() {
        val config = webDavConfig.value
        if (config.serverUrl.isBlank()) {
            showSnackbar("Configura la URL de WebDAV primero")
            return
        }
        viewModelScope.launch {
            val all = exportedAudios.value
            webDavSyncManager.syncAllFiles(all, config)
        }
    }

    suspend fun listRemoteWebDavDirectories(path: String): Result<List<com.example.sync.WebDavItem>> {
        return webDavSyncManager.listRemoteDirectories(webDavConfig.value, path)
    }

    suspend fun createRemoteWebDavDirectory(parentPath: String, dirName: String): Result<Boolean> {
        return webDavSyncManager.createRemoteDirectory(webDavConfig.value, parentPath, dirName)
    }

    fun openAudioFolderInFileManager() {
        try {
            val dir = getEffectiveAudioDirectory()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                dir
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Abrir carpeta de grabaciones").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            showSnackbar("Carpeta de grabaciones: ${getEffectiveAudioDirectory().absolutePath}")
        }
    }

    fun toggleDarkMode() {
        isDarkMode.value = when (isDarkMode.value) {
            null -> true
            true -> false
            false -> null
        }
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.stop()
    }
}
