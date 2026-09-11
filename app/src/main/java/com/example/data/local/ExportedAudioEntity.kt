package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exported_audios")
data class ExportedAudioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val originalText: String,
    val filePath: String,
    val fileName: String,
    val format: String, // "MP3" or "WAV"
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val isSyncedToWebDav: Boolean = false,
    val lastSyncedAt: Long? = null,
    val remoteWebDavPath: String? = null
)
