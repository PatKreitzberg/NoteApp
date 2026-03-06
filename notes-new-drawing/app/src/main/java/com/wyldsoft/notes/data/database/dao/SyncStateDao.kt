package com.wyldsoft.notes.data.database.dao

import androidx.room.*
import com.wyldsoft.notes.data.database.entities.SyncStateEntity

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getByDeviceId(deviceId: String): SyncStateEntity?
}
