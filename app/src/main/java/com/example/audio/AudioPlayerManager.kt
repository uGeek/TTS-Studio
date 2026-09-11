package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.data.local.ExportedAudioEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AudioPlayerState(
    val currentAudioId: Long? = null,
    val currentAudioTitle: String = "",
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

class AudioPlayerManager private constructor(private val context: Context) {
    private val tag = "AudioPlayerManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    fun playAudio(audio: ExportedAudioEntity) {
        val file = File(audio.filePath)
        if (!file.exists()) {
            Log.e(tag, "Audio file not found: ${audio.filePath}")
            return
        }

        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                prepare()
                setOnCompletionListener {
                    stopProgressJob()
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        isPaused = false,
                        currentPositionMs = 0L
                    )
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: what=$what extra=$extra")
                    stop()
                    true
                }
                start()
            }

            val duration = mediaPlayer?.duration?.toLong() ?: audio.durationMs
            _playerState.value = AudioPlayerState(
                currentAudioId = audio.id,
                currentAudioTitle = audio.title,
                isPlaying = true,
                isPaused = false,
                currentPositionMs = 0L,
                durationMs = duration
            )

            startProgressJob()
        } catch (e: Exception) {
            Log.e(tag, "Error playing audio file", e)
            stop()
        }
    }

    fun togglePlayPause(audio: ExportedAudioEntity) {
        if (_playerState.value.currentAudioId == audio.id) {
            if (_playerState.value.isPlaying) {
                pause()
            } else {
                resume()
            }
        } else {
            playAudio(audio)
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                stopProgressJob()
                _playerState.value = _playerState.value.copy(
                    isPlaying = false,
                    isPaused = true
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error pausing playback", e)
        }
    }

    fun resume() {
        try {
            mediaPlayer?.let {
                it.start()
                _playerState.value = _playerState.value.copy(
                    isPlaying = true,
                    isPaused = false
                )
                startProgressJob()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error resuming playback", e)
        }
    }

    fun stop() {
        try {
            stopProgressJob()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping playback", e)
        } finally {
            _playerState.value = AudioPlayerState()
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
        } catch (e: Exception) {
            Log.e(tag, "Error seeking position", e)
        }
    }

    private fun startProgressJob() {
        stopProgressJob()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val current = mediaPlayer?.currentPosition?.toLong() ?: 0L
                _playerState.value = _playerState.value.copy(currentPositionMs = current)
                delay(200)
            }
        }
    }

    private fun stopProgressJob() {
        progressJob?.cancel()
        progressJob = null
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AudioPlayerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
