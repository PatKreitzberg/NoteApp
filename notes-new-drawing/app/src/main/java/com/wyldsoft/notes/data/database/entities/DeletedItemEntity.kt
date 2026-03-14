package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_items")
data class DeletedItemEntity(
    @PrimaryKey
    val entityId: String,
    val entityType: String, // "note", "notebook", "folder"
    val deletedAt: Long = System.currentTimeMillis(),
    val originalParentId: String? = null // folder/notebook id before trash
)
