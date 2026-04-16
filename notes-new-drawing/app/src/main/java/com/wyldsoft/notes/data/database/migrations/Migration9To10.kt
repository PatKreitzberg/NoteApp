package com.wyldsoft.notes.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS pen_profile_sets (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                slot1Width REAL NOT NULL,
                slot1PenType TEXT NOT NULL,
                slot1ColorArgb INTEGER NOT NULL,
                slot1Alpha REAL NOT NULL,
                slot2Width REAL NOT NULL,
                slot2PenType TEXT NOT NULL,
                slot2ColorArgb INTEGER NOT NULL,
                slot2Alpha REAL NOT NULL,
                slot3Width REAL NOT NULL,
                slot3PenType TEXT NOT NULL,
                slot3ColorArgb INTEGER NOT NULL,
                slot3Alpha REAL NOT NULL,
                slot4Width REAL NOT NULL,
                slot4PenType TEXT NOT NULL,
                slot4ColorArgb INTEGER NOT NULL,
                slot4Alpha REAL NOT NULL,
                slot5Width REAL NOT NULL,
                slot5PenType TEXT NOT NULL,
                slot5ColorArgb INTEGER NOT NULL,
                slot5Alpha REAL NOT NULL
            )"""
        )
    }
}
