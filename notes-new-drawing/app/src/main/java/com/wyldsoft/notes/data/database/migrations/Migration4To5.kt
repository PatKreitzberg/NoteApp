package com.wyldsoft.notes.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE notes ADD COLUMN overrideNotebookSettings INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE notebooks ADD COLUMN template TEXT NOT NULL DEFAULT 'BLANK'")
        database.execSQL("ALTER TABLE notebooks ADD COLUMN isPaginationEnabled INTEGER NOT NULL DEFAULT 0")
    }
}
