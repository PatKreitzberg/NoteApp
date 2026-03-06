package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    val deviceId: String,
    val lastSyncTimestamp: Long,
    val updatedAt: Long = System.currentTimeMillis()
)
