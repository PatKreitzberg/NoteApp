package com.wyldsoft.notes.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE notebooks ADD COLUMN drawOutsideBounds INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE notes ADD COLUMN drawOutsideBounds INTEGER NOT NULL DEFAULT 0")
    }
}
