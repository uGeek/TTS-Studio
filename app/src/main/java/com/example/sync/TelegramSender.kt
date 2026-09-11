package com.example.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object TelegramSender {
    private const val TAG = "TelegramSender"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Validates bot token via getMe.
     */
    suspend fun testBotToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("El token del bot no puede estar vacío"))
        }

        val url = "https://api.telegram.org/bot$cleanToken/getMe"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val result = json.optJSONObject("result")
                    val botName = result?.optString("first_name") ?: "Bot"
                    val username = result?.optString("username") ?: ""
                    Result.success("Bot conectado: $botName (@$username)")
                } else {
                    val json = try { JSONObject(body) } catch (e: Exception) { null }
                    val desc = json?.optString("description") ?: "Error HTTP ${response.code}"
                    Result.failure(RuntimeException(desc))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error testing bot token", e)
            Result.failure(e)
        }
    }

    /**
     * Sends an audio file to a specific Telegram chat/user.
     */
    suspend fun sendAudio(
        botToken: String,
        chatId: String,
        audioFile: File,
        caption: String? = null,
        title: String? = null,
        performer: String? = "TTS Studio"
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()

        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Token del bot no configurado"))
        }
        if (cleanChatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("ID de usuario/chat no válido"))
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("El archivo de audio no existe o está vacío"))
        }

        val url = "https://api.telegram.org/bot$cleanToken/sendAudio"

        val mimeType = when (audioFile.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "m4a" -> "audio/mp4"
            else -> "audio/*"
        }

        val fileBody = audioFile.asRequestBody(mimeType.toMediaTypeOrNull())

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", cleanChatId)
            .addFormDataPart("audio", audioFile.name, fileBody)

        if (!caption.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("caption", caption.take(1024))
        }
        if (!title.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("title", title.take(64))
        }
        if (!performer.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("performer", performer)
        }

        val request = Request.Builder()
            .url(url)
            .post(multipartBuilder.build())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("Audio enviado con éxito a Telegram ($cleanChatId)")
                } else {
                    val json = try { JSONObject(body) } catch (e: Exception) { null }
                    val desc = json?.optString("description") ?: "Error HTTP ${response.code}"
                    Result.failure(RuntimeException(desc))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio to Telegram", e)
            Result.failure(e)
        }
    }
}
