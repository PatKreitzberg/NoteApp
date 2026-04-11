package com.wyldsoft.notes.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS htr_results (
                id TEXT NOT NULL PRIMARY KEY,
                noteId TEXT NOT NULL,
                text TEXT NOT NULL,
                confidence REAL NOT NULL,
                shapeIds TEXT NOT NULL,
                boundingLeft REAL NOT NULL,
                boundingTop REAL NOT NULL,
                boundingRight REAL NOT NULL,
                boundingBottom REAL NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (noteId) REFERENCES notes(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_htr_results_noteId ON htr_results(noteId)")
    }
}
