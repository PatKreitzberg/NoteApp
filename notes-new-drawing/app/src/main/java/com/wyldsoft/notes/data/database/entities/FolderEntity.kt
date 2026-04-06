package com.wyldsoft.notes.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentFolderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentFolderId"])]
)
data class FolderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "parentFolderId")
    val parentFolderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "trashedFromId")
    val trashedFromId: String? = null
) {
    companion object {
        const val ROOT_ID = "root"
        const val TRASH_ID = "trash"
    }
}
