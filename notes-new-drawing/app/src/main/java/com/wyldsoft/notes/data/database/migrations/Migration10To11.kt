package com.wyldsoft.notes.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE folders ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE notebooks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
    }
}
