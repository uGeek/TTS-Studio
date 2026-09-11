package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.repository.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

data class TtsEngineInfo(
    val name: String,
    val label: String,
    val icon: String? = null,
    val isCurrent: Boolean = false
)

data class TtsVoiceInfo(
    val name: String,
    val locale: Locale,
    val quality: Int = 300,
    val isNetworkRequired: Boolean = false,
    val displayLabel: String = ""
)

enum class TtsPlaybackState {
    IDLE,
    PLAYING,
    PAUSED,
    SYNTHESIZING
}

data class TtsState(
    val playbackState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val paragraphs: List<String> = emptyList(),
    val currentParagraphIndex: Int = -1,
    val fullText: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val currentEngine: String = "",
    val availableEngines: List<TtsEngineInfo> = emptyList(),
    val availableLanguages: List<Locale> = emptyList(),
    val selectedLanguage: Locale = Locale.getDefault(),
    val availableVoices: List<TtsVoiceInfo> = emptyList(),
    val selectedVoiceName: String = "",
    val minBlockChars: Int = 1000,
    val maxBlockChars: Int = 2000,
    val isInitialized: Boolean = false,
    val errorMessage: String? = null
)

class TtsManager private constructor(private val context: Context) {
    private val tag = "TtsManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val appSettings = AppSettingsRepository.getInstance(context)

    private var tts: TextToSpeech? = null
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var currentPlayingIndex = 0
    private var queuedParagraphs: List<String> = emptyList()

    init {
        val savedPrefs = appSettings.ttsPreferences.value
        _state.value = _state.value.copy(
            speechRate = savedPrefs.speechRate,
            pitch = savedPrefs.pitch,
            selectedVoiceName = savedPrefs.selectedVoiceName,
            minBlockChars = savedPrefs.minBlockChars,
            maxBlockChars = savedPrefs.maxBlockChars,
            selectedLanguage = parseLocale(savedPrefs.languageCode)
        )
        initTtsEngine(savedPrefs.selectedEngine.ifBlank { null })
    }

    private fun parseLocale(code: String): Locale {
        return try {
            val tag = code.replace('_', '-')
            Locale.forLanguageTag(tag).takeIf { it.language.isNotBlank() } ?: Locale.getDefault()
        } catch (e: Exception) {
            Locale.getDefault()
        }
    }

    fun initTtsEngine(enginePackage: String?) {
        scope.launch {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                Log.w(tag, "Error shutting down previous TTS", e)
            }

            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val availableEngines = loadAvailableEngines(enginePackage)
                    val targetLocale = _state.value.selectedLanguage
                    try {
                        tts?.language = targetLocale
                    } catch (ignored: Exception) {}

                    tts?.setSpeechRate(_state.value.speechRate)
                    tts?.setPitch(_state.value.pitch)

                    val langs = loadAvailableLanguages()
                    val voices = loadAvailableVoices()

                    // Apply saved voice if available
                    val savedVoiceName = _state.value.selectedVoiceName
                    if (savedVoiceName.isNotBlank()) {
                        applyVoiceByName(savedVoiceName)
                    }

                    setupUtteranceListener()

                    _state.value = _state.value.copy(
                        isInitialized = true,
                        currentEngine = enginePackage ?: tts?.defaultEngine ?: "",
                        availableEngines = availableEngines,
                        availableLanguages = langs,
                        availableVoices = voices,
                        errorMessage = null
                    )
                    Log.d(tag, "TTS Initialized successfully with engine: ${enginePackage ?: "default"}")
                } else {
                    _state.value = _state.value.copy(
                        isInitialized = false,
                        errorMessage = "Error al inicializar el motor TTS ($status)"
                    )
                }
            }

            tts = if (!enginePackage.isNullOrBlank()) {
                TextToSpeech(context, listener, enginePackage)
            } else {
                TextToSpeech(context, listener)
            }
        }
    }

    private fun loadAvailableEngines(currentSelectedEngine: String?): List<TtsEngineInfo> {
        val list = mutableListOf<TtsEngineInfo>()
        try {
            val engines = tts?.engines ?: emptyList()
            for (engine in engines) {
                list.add(
                    TtsEngineInfo(
                        name = engine.name,
                        label = engine.label ?: engine.name,
                        isCurrent = if (currentSelectedEngine != null) engine.name == currentSelectedEngine else (engine.name == tts?.defaultEngine)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching TTS engines", e)
        }
        return list
    }

    private fun loadAvailableLanguages(): List<Locale> {
        val set = mutableSetOf<Locale>()
        // Standard presets always supported
        set.add(Locale("es", "ES")) // Español España
        set.add(Locale("es", "MX")) // Español México
        set.add(Locale("ca", "ES")) // Catalán
        set.add(Locale.ENGLISH)
        set.add(Locale.US)
        set.add(Locale.UK)
        set.add(Locale.FRENCH)
        set.add(Locale.GERMAN)
        set.add(Locale.ITALIAN)
        set.add(Locale("pt", "PT"))
        set.add(Locale("pt", "BR"))

        try {
            tts?.availableLanguages?.forEach { loc ->
                if (loc.language.isNotBlank()) set.add(loc)
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not fetch available languages", e)
        }
        return set.toList().sortedBy { it.displayName }
    }

    private fun loadAvailableVoices(): List<TtsVoiceInfo> {
        val list = mutableListOf<TtsVoiceInfo>()
        try {
            val voices = tts?.voices ?: emptySet()
            for (v in voices) {
                val label = buildString {
                    append(v.name.substringAfterLast('#').substringAfterLast('/'))
                    if (v.locale.displayName.isNotBlank()) {
                        append(" (").append(v.locale.displayLanguage)
                        if (v.locale.country.isNotBlank()) append("-").append(v.locale.country)
                        append(")")
                    }
                    if (v.isNetworkConnectionRequired) {
                        append(" [Online]")
                    }
                }
                list.add(
                    TtsVoiceInfo(
                        name = v.name,
                        locale = v.locale,
                        quality = v.quality,
                        isNetworkRequired = v.isNetworkConnectionRequired,
                        displayLabel = label
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Error loading voices", e)
        }
        return list.sortedWith(compareBy({ it.locale.language }, { it.name }))
    }

    private fun applyVoiceByName(voiceName: String): Boolean {
        try {
            val voices = tts?.voices ?: emptySet()
            val match = voices.firstOrNull { it.name == voiceName }
            if (match != null) {
                tts?.voice = match
                return true
            }
        } catch (e: Exception) {
            Log.w(tag, "Error setting voice $voiceName", e)
        }
        return false
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch {
                    if (utteranceId?.startsWith("para_") == true) {
                        val index = utteranceId.removePrefix("para_").toIntOrNull() ?: 0
                        currentPlayingIndex = index
                        _state.value = _state.value.copy(
                            playbackState = TtsPlaybackState.PLAYING,
                            currentParagraphIndex = index
                        )
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    if (utteranceId?.startsWith("para_") == true) {
                        val index = utteranceId.removePrefix("para_").toIntOrNull() ?: 0
                        val nextIndex = index + 1
                        if (nextIndex < queuedParagraphs.size && _state.value.playbackState == TtsPlaybackState.PLAYING) {
                            currentPlayingIndex = nextIndex
                            speakParagraph(nextIndex)
                        } else if (nextIndex >= queuedParagraphs.size) {
                            _state.value = _state.value.copy(
                                playbackState = TtsPlaybackState.IDLE,
                                currentParagraphIndex = queuedParagraphs.size - 1
                            )
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch {
                    Log.e(tag, "TTS Utterance error on: $utteranceId")
                    _state.value = _state.value.copy(
                        playbackState = TtsPlaybackState.IDLE,
                        errorMessage = "Error en la reproducción de voz"
                    )
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                scope.launch {
                    Log.e(tag, "TTS Utterance error on: $utteranceId code: $errorCode")
                    _state.value = _state.value.copy(
                        playbackState = TtsPlaybackState.IDLE,
                        errorMessage = "Error en la reproducción de voz (Código: $errorCode)"
                    )
                }
            }
        })
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.25f, 3.0f)
        tts?.setSpeechRate(clamped)
        _state.value = _state.value.copy(speechRate = clamped)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(clamped)
        _state.value = _state.value.copy(pitch = clamped)
    }

    fun setLanguage(locale: Locale) {
        try {
            tts?.language = locale
        } catch (ignored: Exception) {}
        val voices = loadAvailableVoices()
        _state.value = _state.value.copy(
            selectedLanguage = locale,
            availableVoices = voices
        )
    }

    fun setVoice(voiceName: String) {
        val applied = applyVoiceByName(voiceName)
        if (applied || voiceName.isEmpty()) {
            _state.value = _state.value.copy(selectedVoiceName = voiceName)
        }
    }

    fun setBlockSizeLimits(minChars: Int, maxChars: Int) {
        val minC = maxOf(200, minChars)
        val maxC = maxOf(minC + 100, maxChars)
        _state.value = _state.value.copy(minBlockChars = minC, maxBlockChars = maxC)
    }

    fun switchEngine(enginePackage: String) {
        if (enginePackage != _state.value.currentEngine) {
            initTtsEngine(enginePackage)
        }
    }

    /**
     * Splits text into smart blocks (1,000–2,000 characters / 150–300 words).
     * Respects natural paragraphs and punctuation so playback never cuts or glitches.
     */
    fun splitTextIntoChunks(
        text: String,
        minChars: Int = _state.value.minBlockChars,
        maxChars: Int = _state.value.maxBlockChars
    ): List<String> {
        if (text.isBlank()) return emptyList()
        val clean = text.trim()
        val minLimit = maxOf(200, minChars)
        val maxLimit = maxOf(minLimit + 100, maxChars)

        // Split by natural paragraph breaks first
        val rawParagraphs = clean.split(Regex("(\r?\n){2,}"))
            .flatMap { p -> if (p.contains("\n")) p.split("\n") else listOf(p) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val resultChunks = mutableListOf<String>()
        val currentBuilder = StringBuilder()

        for (para in rawParagraphs) {
            if (para.length <= maxLimit) {
                if (currentBuilder.isNotEmpty() && (currentBuilder.length + 1 + para.length <= maxLimit)) {
                    currentBuilder.append("\n\n").append(para)
                } else {
                    if (currentBuilder.isNotEmpty()) {
                        resultChunks.add(currentBuilder.toString())
                        currentBuilder.clear()
                    }
                    if (para.length >= minLimit) {
                        resultChunks.add(para)
                    } else {
                        currentBuilder.append(para)
                    }
                }
            } else {
                // Paragraph exceeds maxLimit: split by sentence punctuation
                if (currentBuilder.isNotEmpty()) {
                    resultChunks.add(currentBuilder.toString())
                    currentBuilder.clear()
                }
                val sentenceSplitRegex = Regex("(?<=[.!?…;:])\\s+")
                val sentences = para.split(sentenceSplitRegex).map { it.trim() }.filter { it.isNotEmpty() }
                val sentenceBuilder = StringBuilder()

                for (sentence in sentences) {
                    if (sentence.length <= maxLimit) {
                        if (sentenceBuilder.isNotEmpty() && (sentenceBuilder.length + 1 + sentence.length <= maxLimit)) {
                            sentenceBuilder.append(" ").append(sentence)
                        } else {
                            if (sentenceBuilder.isNotEmpty()) {
                                resultChunks.add(sentenceBuilder.toString())
                                sentenceBuilder.clear()
                            }
                            if (sentence.length >= minLimit) {
                                resultChunks.add(sentence)
                            } else {
                                sentenceBuilder.append(sentence)
                            }
                        }
                    } else {
                        // Very long sentence: split by words
                        if (sentenceBuilder.isNotEmpty()) {
                            resultChunks.add(sentenceBuilder.toString())
                            sentenceBuilder.clear()
                        }
                        val words = sentence.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        val wordBuilder = StringBuilder()
                        for (word in words) {
                            if (wordBuilder.isNotEmpty() && (wordBuilder.length + 1 + word.length > maxLimit)) {
                                resultChunks.add(wordBuilder.toString())
                                wordBuilder.clear()
                            }
                            if (wordBuilder.isNotEmpty()) wordBuilder.append(" ")
                            wordBuilder.append(word)
                        }
                        if (wordBuilder.isNotEmpty()) {
                            resultChunks.add(wordBuilder.toString())
                        }
                    }
                }
                if (sentenceBuilder.isNotEmpty()) {
                    resultChunks.add(sentenceBuilder.toString())
                }
            }
        }

        if (currentBuilder.isNotEmpty()) {
            resultChunks.add(currentBuilder.toString())
        }

        return if (resultChunks.isEmpty()) listOf(clean) else resultChunks
    }

    fun playText(text: String, startIndex: Int = 0) {
        if (text.isBlank()) return

        val chunks = splitTextIntoChunks(text)
        queuedParagraphs = chunks
        currentPlayingIndex = startIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))

        _state.value = _state.value.copy(
            paragraphs = chunks,
            fullText = text,
            currentParagraphIndex = currentPlayingIndex,
            playbackState = TtsPlaybackState.PLAYING,
            errorMessage = null
        )

        speakParagraph(currentPlayingIndex)
    }

    fun loadText(text: String) {
        if (text.isBlank()) return
        tts?.stop()
        val chunks = splitTextIntoChunks(text)
        queuedParagraphs = chunks
        currentPlayingIndex = 0
        _state.value = _state.value.copy(
            paragraphs = chunks,
            fullText = text,
            currentParagraphIndex = 0,
            playbackState = TtsPlaybackState.IDLE,
            errorMessage = null
        )
    }

    private fun speakParagraph(index: Int) {
        if (index !in queuedParagraphs.indices) {
            _state.value = _state.value.copy(playbackState = TtsPlaybackState.IDLE)
            return
        }

        val textToSpeak = queuedParagraphs[index]
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "para_$index")
        }

        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "para_$index")
    }

    fun pause() {
        if (_state.value.playbackState == TtsPlaybackState.PLAYING) {
            tts?.stop()
            _state.value = _state.value.copy(playbackState = TtsPlaybackState.PAUSED)
        }
    }

    fun resume() {
        if (queuedParagraphs.isNotEmpty()) {
            _state.value = _state.value.copy(playbackState = TtsPlaybackState.PLAYING)
            speakParagraph(currentPlayingIndex.coerceIn(0, (queuedParagraphs.size - 1).coerceAtLeast(0)))
        } else if (_state.value.fullText.isNotBlank()) {
            playText(_state.value.fullText, startIndex = 0)
        }
    }

    fun stop() {
        tts?.stop()
        _state.value = _state.value.copy(
            playbackState = TtsPlaybackState.IDLE
        )
    }

    fun nextParagraph() {
        if (queuedParagraphs.isNotEmpty() && currentPlayingIndex + 1 < queuedParagraphs.size) {
            currentPlayingIndex++
            _state.value = _state.value.copy(
                currentParagraphIndex = currentPlayingIndex,
                playbackState = TtsPlaybackState.PLAYING
            )
            speakParagraph(currentPlayingIndex)
        }
    }

    fun previousParagraph() {
        if (queuedParagraphs.isNotEmpty() && currentPlayingIndex - 1 >= 0) {
            currentPlayingIndex--
            _state.value = _state.value.copy(
                currentParagraphIndex = currentPlayingIndex,
                playbackState = TtsPlaybackState.PLAYING
            )
            speakParagraph(currentPlayingIndex)
        }
    }

    fun jumpToParagraph(index: Int) {
        if (queuedParagraphs.isNotEmpty() && index in queuedParagraphs.indices) {
            currentPlayingIndex = index
            _state.value = _state.value.copy(
                currentParagraphIndex = currentPlayingIndex,
                playbackState = TtsPlaybackState.PLAYING
            )
            speakParagraph(currentPlayingIndex)
        }
    }

    /**
     * Synthesizes given text directly to an audio file on disk in MP3, WAV, M4A, OGG, or OPUS format.
     */
    suspend fun exportToFile(
        text: String,
        title: String,
        format: String, // MP3, WAV, M4A, OGG, OPUS
        targetDirectory: File? = null
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        if (text.isBlank()) {
            continuation.resume(Result.failure(IllegalArgumentException("El texto está vacío")))
            return@suspendCancellableCoroutine
        }

        val exportDir = targetDirectory ?: File(context.filesDir, "audio_exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val baseName = if (title.isNotBlank()) title else text
        val safeTitle = sanitizeFilename(baseName)
        val ext = format.lowercase()
        val tempRawFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.raw")

        var finalFile = File(exportDir, "$safeTitle.$ext")
        if (finalFile.exists()) {
            finalFile = File(exportDir, "${safeTitle}_${System.currentTimeMillis()}.$ext")
        }

        val utteranceId = "export_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val listener = object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    try {
                        AudioExportHelper.exportToFormat(tempRawFile, finalFile, format)
                        tempRawFile.delete()
                        continuation.resume(Result.success(finalFile))
                    } catch (e: Exception) {
                        Log.e(tag, "Error finalizing export file", e)
                        continuation.resume(Result.failure(e))
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    tempRawFile.delete()
                    continuation.resume(Result.failure(RuntimeException("Error en la síntesis TTS a archivo")))
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    tempRawFile.delete()
                    continuation.resume(Result.failure(RuntimeException("Error en síntesis TTS (Código: $errorCode)")))
                }
            }
        }

        tts?.setOnUtteranceProgressListener(listener)
        val result = tts?.synthesizeToFile(text, params, tempRawFile, utteranceId)

        if (result != TextToSpeech.SUCCESS) {
            setupUtteranceListener()
            continuation.resume(Result.failure(RuntimeException("No se pudo iniciar la síntesis ($result)")))
        }

        continuation.invokeOnCancellation {
            setupUtteranceListener()
            tempRawFile.delete()
        }
    }

    private fun sanitizeFilename(input: String): String {
        val firstLine = input.trim().lines().firstOrNull { it.isNotBlank() } ?: "Grabacion_TTS"
        val clean = firstLine
            .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(50)
        return clean.ifBlank { "Grabacion_TTS" }
    }

    companion object {
        @Volatile
        private var INSTANCE: TtsManager? = null

        fun getInstance(context: Context): TtsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TtsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
