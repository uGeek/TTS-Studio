package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WebDavConfig(
    val serverUrl: String = "http://192.168.1.100:8080/webdav",
    val username: String = "",
    val password: String = "",
    val remoteDirectory: String = "/TTS_Audios",
    val autoSyncOnExport: Boolean = false,
    val isEnabled: Boolean = false
)

data class TelegramUser(
    val name: String,
    val chatId: String
)

data class TelegramConfig(
    val botToken: String = "",
    val rawUsersCsv: String = "angel,584622"
) {
    val users: List<TelegramUser>
        get() {
            if (rawUsersCsv.isBlank()) return emptyList()
            return rawUsersCsv.lines()
                .flatMap { line -> line.split(";") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { item ->
                    val parts = item.split(",")
                    if (parts.size >= 2) {
                        val name = parts[0].trim()
                        val chatId = parts[1].trim()
                        if (name.isNotEmpty() && chatId.isNotEmpty()) {
                            TelegramUser(name, chatId)
                        } else null
                    } else null
                }
        }
}

data class TtsPreferences(
    val selectedEngine: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val languageCode: String = "es-ES",
    val selectedVoiceName: String = "",
    val minBlockChars: Int = 1000,
    val maxBlockChars: Int = 2000
)

class AppSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("tts_studio_prefs", Context.MODE_PRIVATE)

    private val _webDavConfig = MutableStateFlow(loadWebDavConfig())
    val webDavConfig: StateFlow<WebDavConfig> = _webDavConfig.asStateFlow()

    private val _telegramConfig = MutableStateFlow(loadTelegramConfig())
    val telegramConfig: StateFlow<TelegramConfig> = _telegramConfig.asStateFlow()

    private val _ttsPreferences = MutableStateFlow(loadTtsPreferences())
    val ttsPreferences: StateFlow<TtsPreferences> = _ttsPreferences.asStateFlow()

    private val _isFloatingBubbleActive = MutableStateFlow(
        prefs.getBoolean("floating_bubble_active", false)
    )
    val isFloatingBubbleActive: StateFlow<Boolean> = _isFloatingBubbleActive.asStateFlow()

    private val _localAudioDirectory = MutableStateFlow(
        prefs.getString("local_audio_dir", "") ?: ""
    )
    val localAudioDirectory: StateFlow<String> = _localAudioDirectory.asStateFlow()

    private val _lastExportFormat = MutableStateFlow(
        prefs.getString("last_export_format", "MP3") ?: "MP3"
    )
    val lastExportFormat: StateFlow<String> = _lastExportFormat.asStateFlow()

    fun setLastExportFormat(format: String) {
        val safe = if (format.isNotBlank()) format.uppercase() else "MP3"
        prefs.edit().putString("last_export_format", safe).apply()
        _lastExportFormat.value = safe
    }

    fun setLocalAudioDirectory(path: String) {
        prefs.edit().putString("local_audio_dir", path).apply()
        _localAudioDirectory.value = path
    }

    fun loadWebDavConfig(): WebDavConfig {
        return WebDavConfig(
            serverUrl = prefs.getString("webdav_url", "http://192.168.1.100:8080/webdav") ?: "http://192.168.1.100:8080/webdav",
            username = prefs.getString("webdav_user", "") ?: "",
            password = prefs.getString("webdav_pass", "") ?: "",
            remoteDirectory = prefs.getString("webdav_dir", "/TTS_Audios") ?: "/TTS_Audios",
            autoSyncOnExport = prefs.getBoolean("webdav_autosync", false),
            isEnabled = prefs.getBoolean("webdav_enabled", false)
        )
    }

    fun saveWebDavConfig(config: WebDavConfig) {
        prefs.edit()
            .putString("webdav_url", config.serverUrl)
            .putString("webdav_user", config.username)
            .putString("webdav_pass", config.password)
            .putString("webdav_dir", config.remoteDirectory)
            .putBoolean("webdav_autosync", config.autoSyncOnExport)
            .putBoolean("webdav_enabled", config.isEnabled)
            .apply()
        _webDavConfig.value = config
    }

    fun loadTelegramConfig(): TelegramConfig {
        return TelegramConfig(
            botToken = prefs.getString("tg_bot_token", "") ?: "",
            rawUsersCsv = prefs.getString("tg_users_csv", "angel,584622") ?: "angel,584622"
        )
    }

    fun saveTelegramConfig(config: TelegramConfig) {
        prefs.edit()
            .putString("tg_bot_token", config.botToken.trim())
            .putString("tg_users_csv", config.rawUsersCsv.trim())
            .apply()
        _telegramConfig.value = config
    }

    fun loadTtsPreferences(): TtsPreferences {
        return TtsPreferences(
            selectedEngine = prefs.getString("tts_engine", "") ?: "",
            speechRate = prefs.getFloat("tts_rate", 1.0f),
            pitch = prefs.getFloat("tts_pitch", 1.0f),
            languageCode = prefs.getString("tts_lang", "es-ES") ?: "es-ES",
            selectedVoiceName = prefs.getString("tts_voice", "") ?: "",
            minBlockChars = prefs.getInt("tts_min_block", 1000),
            maxBlockChars = prefs.getInt("tts_max_block", 2000)
        )
    }

    fun saveTtsPreferences(prefsData: TtsPreferences) {
        prefs.edit()
            .putString("tts_engine", prefsData.selectedEngine)
            .putFloat("tts_rate", prefsData.speechRate)
            .putFloat("tts_pitch", prefsData.pitch)
            .putString("tts_lang", prefsData.languageCode)
            .putString("tts_voice", prefsData.selectedVoiceName)
            .putInt("tts_min_block", prefsData.minBlockChars)
            .putInt("tts_max_block", prefsData.maxBlockChars)
            .apply()
        _ttsPreferences.value = prefsData
    }

    fun setFloatingBubbleActive(active: Boolean) {
        prefs.edit().putBoolean("floating_bubble_active", active).apply()
        _isFloatingBubbleActive.value = active
    }

    companion object {
        @Volatile
        private var INSTANCE: AppSettingsRepository? = null

        fun getInstance(context: Context): AppSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = AppSettingsRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
