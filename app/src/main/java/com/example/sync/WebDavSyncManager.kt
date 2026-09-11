package com.example.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.ExportedAudioEntity
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class WebDavItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val contentLength: Long = 0,
    val lastModified: String = ""
)

data class WebDavTestResult(
    val isSuccess: Boolean,
    val statusCode: Int = 0,
    val responseTimeMs: Long = 0,
    val message: String
)

data class WebDavSyncState(
    val isSyncing: Boolean = false,
    val progress: Float = 0f,
    val currentFileName: String = "",
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val lastSyncMessage: String? = null,
    val lastTestResult: WebDavTestResult? = null
)

class WebDavSyncManager(private val context: Context) {
    private val tag = "WebDavSyncManager"
    private val database = AppDatabase.getInstance(context)
    private val audioDao = database.exportedAudioDao()
    private val settingsRepository = AppSettingsRepository.getInstance(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _syncState = MutableStateFlow(WebDavSyncState())
    val syncState: StateFlow<WebDavSyncState> = _syncState.asStateFlow()

    private fun getAuthHeader(config: WebDavConfig): String? {
        if (config.username.isBlank() && config.password.isBlank()) return null
        val credentials = "${config.username}:${config.password}"
        val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    private fun normalizeUrl(baseUrl: String, remoteDir: String, fileName: String = ""): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanDir = remoteDir.trim().trim('/')
        return buildString {
            append(cleanBase)
            if (cleanDir.isNotEmpty()) {
                append("/").append(cleanDir)
            }
            if (fileName.isNotEmpty()) {
                append("/").append(fileName)
            }
        }
    }

    suspend fun testConnection(config: WebDavConfig): WebDavTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            if (config.serverUrl.isBlank()) {
                val res = WebDavTestResult(false, 0, 0, "URL del servidor no configurada")
                _syncState.value = _syncState.value.copy(lastTestResult = res)
                return@withContext res
            }

            val targetUrl = normalizeUrl(config.serverUrl, config.remoteDirectory)
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .method("PROPFIND", null)
                .header("Depth", "0")

            getAuthHeader(config)?.let {
                reqBuilder.header("Authorization", it)
            }

            var response: Response = client.newCall(reqBuilder.build()).execute()
            val elapsed = System.currentTimeMillis() - startTime

            // If PROPFIND is not allowed or 405, fallback to standard GET or OPTIONS
            if (response.code == 405 || response.code == 501) {
                val fallbackReq = Request.Builder()
                    .url(config.serverUrl)
                    .get()
                getAuthHeader(config)?.let { fallbackReq.header("Authorization", it) }
                response = client.newCall(fallbackReq.build()).execute()
            }

            val isSuccess = response.isSuccessful || response.code == 207 // 207 Multi-Status for WebDAV
            val msg = if (isSuccess) {
                "Conexión exitosa WebDAV (${response.code} en ${elapsed}ms)"
            } else if (response.code == 401) {
                "Error 401: Usuario o contraseña incorrectos"
            } else if (response.code == 404) {
                "El servidor respondió (404). El directorio remoto puede crearse al subir archivos."
            } else {
                "Servidor respondió con código ${response.code} (${response.message})"
            }

            val result = WebDavTestResult(
                isSuccess = isSuccess || response.code == 404,
                statusCode = response.code,
                responseTimeMs = elapsed,
                message = msg
            )
            _syncState.value = _syncState.value.copy(lastTestResult = result)
            result
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(tag, "WebDAV connection error", e)
            val result = WebDavTestResult(
                isSuccess = false,
                statusCode = -1,
                responseTimeMs = elapsed,
                message = "Error de red: ${e.localizedMessage ?: "No se pudo conectar"}"
            )
            _syncState.value = _syncState.value.copy(lastTestResult = result)
            result
        }
    }

    suspend fun listRemoteDirectories(config: WebDavConfig, remotePath: String = "/"): Result<List<WebDavItem>> = withContext(Dispatchers.IO) {
        try {
            if (config.serverUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("URL del servidor no configurada"))
            }

            val targetUrl = normalizeUrl(config.serverUrl, remotePath)
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .method("PROPFIND", null)
                .header("Depth", "1")

            getAuthHeader(config)?.let {
                reqBuilder.header("Authorization", it)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 207) {
                return@withContext Result.failure(RuntimeException("Error del servidor: ${response.code} ${response.message}"))
            }

            val responseBody = response.body?.string() ?: ""
            if (responseBody.isBlank()) {
                return@withContext Result.success(emptyList())
            }

            val items = parseWebDavMultistatusXml(responseBody, config.serverUrl, remotePath)
            Result.success(items)
        } catch (e: Exception) {
            Log.e(tag, "Error listing remote directories", e)
            Result.failure(e)
        }
    }

    private fun parseWebDavMultistatusXml(xmlContent: String, baseUrl: String, currentRemotePath: String): List<WebDavItem> {
        val list = mutableListOf<WebDavItem>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
            doc.documentElement.normalize()

            val responses = doc.getElementsByTagNameNS("*", "response")
            val cleanBaseUri = URI(baseUrl.trimEnd('/'))
            val cleanCurrentPath = currentRemotePath.trim('/')

            for (i in 0 until responses.length) {
                val respNode = responses.item(i) as? Element ?: continue
                val hrefNodes = respNode.getElementsByTagNameNS("*", "href")
                if (hrefNodes.length == 0) continue
                val rawHref = hrefNodes.item(0).textContent.trim()

                // Extract path from href (handle full URL or relative URL)
                val itemPath = try {
                    val uri = URI(rawHref)
                    val p = uri.path ?: rawHref
                    // Remove server base path prefix if present
                    val basePath = cleanBaseUri.path?.trimEnd('/') ?: ""
                    if (basePath.isNotEmpty() && p.startsWith(basePath)) {
                        p.removePrefix(basePath)
                    } else {
                        p
                    }
                } catch (e: Exception) {
                    rawHref
                }.trim('/')

                // Check if collection (directory)
                val resourceTypeNodes = respNode.getElementsByTagNameNS("*", "resourcetype")
                var isCollection = false
                if (resourceTypeNodes.length > 0) {
                    val resTypeEl = resourceTypeNodes.item(0) as? Element
                    val collectionNodes = resTypeEl?.getElementsByTagNameNS("*", "collection")
                    if (collectionNodes != null && collectionNodes.length > 0) {
                        isCollection = true
                    }
                }

                // Display name
                val displayNameNodes = respNode.getElementsByTagNameNS("*", "displayname")
                val displayName = if (displayNameNodes.length > 0 && displayNameNodes.item(0).textContent.isNotBlank()) {
                    displayNameNodes.item(0).textContent.trim()
                } else {
                    itemPath.substringAfterLast('/').ifBlank { itemPath }
                }

                // Skip the current folder itself (self reference in Depth: 1)
                if (itemPath.equals(cleanCurrentPath, ignoreCase = true) || itemPath.isEmpty()) {
                    continue
                }

                list.add(
                    WebDavItem(
                        name = displayName.ifBlank { itemPath },
                        path = "/$itemPath",
                        isDirectory = isCollection
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing WebDAV XML response", e)
        }
        // Sort: directories first, then alphabetically
        return list.sortedWith(compareByDescending<WebDavItem> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun createRemoteDirectory(config: WebDavConfig, parentPath: String, newDirName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanParent = parentPath.trim().trim('/')
            val cleanDirName = newDirName.trim().trim('/')
            val fullPath = if (cleanParent.isEmpty()) cleanDirName else "$cleanParent/$cleanDirName"
            val dirUrl = normalizeUrl(config.serverUrl, fullPath)

            val reqBuilder = Request.Builder()
                .url(dirUrl)
                .method("MKCOL", null)

            getAuthHeader(config)?.let {
                reqBuilder.header("Authorization", it)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val success = response.isSuccessful || response.code == 201 || response.code == 405
            if (success) {
                Result.success(true)
            } else {
                Result.failure(RuntimeException("Error creando carpeta: ${response.code} ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error creating remote directory", e)
            Result.failure(e)
        }
    }

    private suspend fun ensureRemoteDirectoryExists(config: WebDavConfig): Boolean = withContext(Dispatchers.IO) {
        if (config.remoteDirectory.isBlank() || config.remoteDirectory == "/") return@withContext true
        try {
            val dirUrl = normalizeUrl(config.serverUrl, config.remoteDirectory)
            val reqBuilder = Request.Builder()
                .url(dirUrl)
                .method("MKCOL", null)

            getAuthHeader(config)?.let {
                reqBuilder.header("Authorization", it)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            // 201 Created or 405 Method Not Allowed (means already exists)
            response.isSuccessful || response.code == 405 || response.code == 200 || response.code == 207
        } catch (e: Exception) {
            Log.w(tag, "MKCOL check error, continuing anyway", e)
            true
        }
    }

    suspend fun uploadAudioFile(audio: ExportedAudioEntity, config: WebDavConfig): Boolean = withContext(Dispatchers.IO) {
        val file = File(audio.filePath)
        if (!file.exists()) {
            Log.e(tag, "Local file not found: ${audio.filePath}")
            return@withContext false
        }

        try {
            ensureRemoteDirectoryExists(config)

            val targetUrl = normalizeUrl(config.serverUrl, config.remoteDirectory, file.name)
            val mimeType = if (audio.format.equals("MP3", ignoreCase = true)) "audio/mpeg" else "audio/wav"
            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())

            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .put(requestBody)

            getAuthHeader(config)?.let {
                reqBuilder.header("Authorization", it)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val success = response.isSuccessful || response.code == 201 || response.code == 204

            if (success) {
                audioDao.updateAudio(
                    audio.copy(
                        isSyncedToWebDav = true,
                        lastSyncedAt = System.currentTimeMillis(),
                        remoteWebDavPath = targetUrl
                    )
                )
                Log.d(tag, "File ${file.name} uploaded successfully to $targetUrl")
            } else {
                Log.e(tag, "Failed to upload file ${file.name}: ${response.code} ${response.message}")
            }
            success
        } catch (e: Exception) {
            Log.e(tag, "Error uploading audio to WebDAV", e)
            false
        }
    }

    suspend fun syncAllFiles(audios: List<ExportedAudioEntity>, config: WebDavConfig) = withContext(Dispatchers.IO) {
        if (_syncState.value.isSyncing) return@withContext
        val filesToSync = audios.filter { !it.isSyncedToWebDav }

        if (filesToSync.isEmpty()) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncMessage = "Todos los archivos ya están sincronizados"
            )
            return@withContext
        }

        _syncState.value = WebDavSyncState(
            isSyncing = true,
            progress = 0f,
            totalFiles = filesToSync.size,
            completedFiles = 0,
            lastSyncMessage = "Iniciando sincronización con ${config.serverUrl}..."
        )

        var successCount = 0
        filesToSync.forEachIndexed { index, audio ->
            _syncState.value = _syncState.value.copy(
                currentFileName = audio.fileName,
                progress = (index.toFloat() / filesToSync.size)
            )

            val success = uploadAudioFile(audio, config)
            if (success) successCount++

            _syncState.value = _syncState.value.copy(
                completedFiles = index + 1,
                progress = ((index + 1).toFloat() / filesToSync.size)
            )
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = false,
            currentFileName = "",
            lastSyncMessage = "Sincronización finalizada: $successCount/${filesToSync.size} archivos subidos"
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: WebDavSyncManager? = null

        fun getInstance(context: Context): WebDavSyncManager {
            return INSTANCE ?: synchronized(this) {
                val instance = WebDavSyncManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
