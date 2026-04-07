package com.wyldsoft.notes.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS undo_history (
                id TEXT NOT NULL PRIMARY KEY,
                noteId TEXT NOT NULL,
                actionType TEXT NOT NULL,
                isUndoStack INTEGER NOT NULL,
                sequenceNumber INTEGER NOT NULL,
                shapesJson TEXT NOT NULL,
                dNoteX REAL NOT NULL DEFAULT 0.0,
                dNoteY REAL NOT NULL DEFAULT 0.0
            )"""
        )
    }
}
