package com.wyldsoft.notes.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS layers (
                id TEXT NOT NULL PRIMARY KEY,
                noteId TEXT NOT NULL,
                position INTEGER NOT NULL,
                name TEXT NOT NULL,
                visible INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (noteId) REFERENCES notes(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_layers_noteId ON layers(noteId)")
    }
}
