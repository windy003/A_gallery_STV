package com.example.photogallery.data

import androidx.room.*

@Entity(tableName = "change_logs")
data class ChangeLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val action: String, // "CREATE", "DELETE", "ADD_ITEM", "REMOVE_ITEM", "UPLOAD", "DOWNLOAD"
    val description: String, // Human readable description
    val collectionName: String? = null,
    val itemPath: String? = null
)

@Dao
interface ChangeLogDao {
    @Insert
    suspend fun insertChangeLog(changeLog: ChangeLog)

    @Query("SELECT * FROM change_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestChange(): ChangeLog?

    @Query("SELECT * FROM change_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentChanges(limit: Int): List<ChangeLog>

    @Query("DELETE FROM change_logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldLogs(beforeTimestamp: Long)
}