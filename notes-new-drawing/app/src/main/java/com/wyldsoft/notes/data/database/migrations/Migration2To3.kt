package com.wyldsoft.notes.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wyldsoft.notes.data.database.entities.FolderEntity

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE folders ADD COLUMN trashedFromId TEXT")
        database.execSQL("ALTER TABLE notebooks ADD COLUMN trashedFromId TEXT")

        val now = System.currentTimeMillis()
        database.execSQL(
            "INSERT OR IGNORE INTO folders (id, name, parentFolderId, createdAt, modifiedAt, trashedFromId) VALUES (?, ?, ?, ?, ?, NULL)",
            arrayOf<Any>(FolderEntity.TRASH_ID, "Trash", FolderEntity.ROOT_ID, now, now)
        )
    }
}
