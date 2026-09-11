package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportedAudioDao {

    @Query("SELECT * FROM exported_audios ORDER BY createdAt DESC")
    fun getAllAudios(): Flow<List<ExportedAudioEntity>>

    @Query("""
        SELECT * FROM exported_audios 
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR originalText LIKE '%' || :query || '%')
        AND (:format = 'ALL' OR format = :format)
        ORDER BY 
            CASE WHEN :sortBy = 'DATE_DESC' THEN createdAt END DESC,
            CASE WHEN :sortBy = 'DATE_ASC' THEN createdAt END ASC,
            CASE WHEN :sortBy = 'NAME_ASC' THEN title END ASC,
            CASE WHEN :sortBy = 'SIZE_DESC' THEN fileSizeBytes END DESC
    """)
    fun searchAndFilterAudios(
        query: String,
        format: String,
        sortBy: String
    ): Flow<List<ExportedAudioEntity>>

    @Query("SELECT * FROM exported_audios WHERE id = :id")
    suspend fun getAudioById(id: Long): ExportedAudioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudio(audio: ExportedAudioEntity): Long

    @Update
    suspend fun updateAudio(audio: ExportedAudioEntity)

    @Delete
    suspend fun deleteAudio(audio: ExportedAudioEntity)

    @Query("DELETE FROM exported_audios WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM exported_audios WHERE id IN (:ids)")
    suspend fun deleteMultiple(ids: List<Long>)
}
