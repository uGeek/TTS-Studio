package com.example.service

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.example.audio.AudioExportHelper
import com.example.audio.TtsManager
import com.example.data.local.AppDatabase
import com.example.data.local.ExportedAudioEntity
import com.example.data.repository.AppSettingsRepository
import com.example.sync.WebDavSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Transparent Activity used by FloatingBubbleService to obtain temporary window focus
 * and safely read Android Clipboard data on Android 10+ (API 29+) when the main application
 * is in the background or minimized.
 */
class TransparentClipboardActivity : Activity() {

    companion object {
        const val ACTION_PASTE_AND_PLAY = "com.example.action.PASTE_AND_PLAY"
        const val ACTION_PASTE_AND_RECORD = "com.example.action.PASTE_AND_RECORD"
        private const val TAG = "TransparentClipboard"
    }

    private var hasProcessed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var dummyView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0f)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } catch (e: Exception) {
            Log.w(TAG, "Window setup warning", e)
        }

        dummyView = View(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        setContentView(dummyView)
        dummyView?.requestFocus()

        overridePendingTransition(0, 0)

        // Safety fallback timer in case onWindowFocusChanged is delayed
        mainHandler.postDelayed({
            if (!hasProcessed && !isFinishing) {
                attemptClipboardRead(attempt = 1)
            }
        }, 80)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasProcessed && !isFinishing) {
            mainHandler.post {
                attemptClipboardRead(attempt = 1)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dummyView?.requestFocus()
    }

    private fun attemptClipboardRead(attempt: Int) {
        if (hasProcessed || isFinishing || isDestroyed) return

        var text: String? = null
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    text = clipData.getItemAt(0)?.coerceToText(this)?.toString()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Clipboard attempt $attempt: ${e.message}")
        }

        if (!text.isNullOrBlank()) {
            hasProcessed = true
            handleClipboardSuccess(text)
        } else if (attempt < 8) {
            // Android WindowManager may take 30-50ms to synchronize clipboard focus to the new window
            mainHandler.postDelayed({
                attemptClipboardRead(attempt + 1)
            }, 45)
        } else {
            hasProcessed = true
            handleClipboardEmpty()
        }
    }

    private fun handleClipboardSuccess(text: String) {
        val action = intent?.action ?: ACTION_PASTE_AND_PLAY
        val ttsManager = TtsManager.getInstance(applicationContext)

        if (action == ACTION_PASTE_AND_RECORD) {
            recordTextToAudio(text)
        } else {
            ttsManager.playText(text, startIndex = 0)
            val preview = if (text.length > 30) "${text.take(30)}..." else text
            Toast.makeText(this, "Reproduciendo: \"$preview\"", Toast.LENGTH_SHORT).show()
            finishAndClose()
        }
    }

    private fun handleClipboardEmpty() {
        val action = intent?.action ?: ACTION_PASTE_AND_PLAY
        val ttsManager = TtsManager.getInstance(applicationContext)

        if (action == ACTION_PASTE_AND_RECORD && ttsManager.state.value.fullText.isNotBlank()) {
            recordTextToAudio(ttsManager.state.value.fullText)
        } else {
            Toast.makeText(this, "El portapapeles está vacío o no contiene texto", Toast.LENGTH_SHORT).show()
            finishAndClose()
        }
    }

    private fun recordTextToAudio(textToExport: String) {
        val appSettings = AppSettingsRepository.getInstance(applicationContext)
        val lastFormat = appSettings.lastExportFormat.value.ifBlank { "MP3" }
        Toast.makeText(this, "Grabando audio en formato $lastFormat...", Toast.LENGTH_SHORT).show()

        scope.launch(Dispatchers.IO) {
            try {
                val customPath = appSettings.localAudioDirectory.value.trim()
                val targetDir = if (customPath.isNotEmpty()) {
                    val dir = File(customPath)
                    if (dir.exists() || dir.mkdirs()) dir else File(filesDir, "audio_exports")
                } else {
                    File(filesDir, "audio_exports")
                }
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val firstLine = textToExport.trim().lines().firstOrNull { it.isNotBlank() } ?: "Grabacion_TTS"
                val cleanTitle = firstLine
                    .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
                    .trim()
                    .replace(Regex("\\s+"), " ")
                    .take(50)
                    .ifBlank { "Grabacion_TTS" }

                val ttsManager = TtsManager.getInstance(applicationContext)
                val exportResult = ttsManager.exportToFile(
                    text = textToExport,
                    title = cleanTitle,
                    format = lastFormat,
                    targetDirectory = targetDir
                )

                exportResult.onSuccess { file ->
                    val durationMs = AudioExportHelper.getAudioDurationMs(file)
                    val database = AppDatabase.getInstance(applicationContext)
                    val audioDao = database.exportedAudioDao()
                    val newAudio = ExportedAudioEntity(
                        title = cleanTitle,
                        originalText = textToExport,
                        filePath = file.absolutePath,
                        fileName = file.name,
                        format = lastFormat.uppercase(),
                        durationMs = durationMs,
                        fileSizeBytes = file.length(),
                        createdAt = System.currentTimeMillis()
                    )
                    val id = audioDao.insertAudio(newAudio)
                    val savedAudio = newAudio.copy(id = id)

                    // Auto sync if configured
                    val webDavConf = appSettings.webDavConfig.value
                    if (webDavConf.isEnabled && webDavConf.autoSyncOnExport) {
                        val webDavManager = WebDavSyncManager.getInstance(applicationContext)
                        webDavManager.uploadAudioFile(savedAudio, webDavConf)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "Audio grabado con éxito ($lastFormat): ${file.name}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { err ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "Error al grabar audio: ${err.localizedMessage ?: "Error desconocido"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording audio", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Error al grabar: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    finishAndClose()
                }
            }
        }
    }

    private fun finishAndClose() {
        try {
            finish()
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Error finishing activity", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
