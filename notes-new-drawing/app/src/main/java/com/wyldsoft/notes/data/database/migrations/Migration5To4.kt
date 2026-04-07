package com.wyldsoft.notes.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_4 = object : Migration(5, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // SQLite does not support DROP COLUMN reliably on minSdk 29, so rebuild the table.
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS notes_new (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `parentNotebookId` TEXT,
                `folderId` TEXT,
                `settings` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `modifiedAt` INTEGER NOT NULL,
                `viewportScale` REAL NOT NULL,
                `viewportOffsetX` REAL NOT NULL,
                `viewportOffsetY` REAL NOT NULL,
                `isPaginationEnabled` INTEGER NOT NULL,
                `paperSize` TEXT NOT NULL,
                `paperTemplate` TEXT NOT NULL,
                `pdfPath` TEXT,
                `pdfPageCount` INTEGER NOT NULL,
                `pdfPageAspectRatio` REAL NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`parentNotebookId`) REFERENCES `notebooks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())

        database.execSQL("""
            INSERT INTO notes_new (
                id, title, parentNotebookId, folderId, settings,
                createdAt, modifiedAt, viewportScale, viewportOffsetX, viewportOffsetY,
                isPaginationEnabled, paperSize, paperTemplate, pdfPath,
                pdfPageCount, pdfPageAspectRatio
            )
            SELECT
                id, title, parentNotebookId, folderId, settings,
                createdAt, modifiedAt, viewportScale, viewportOffsetX, viewportOffsetY,
                isPaginationEnabled, paperSize, paperTemplate, pdfPath,
                pdfPageCount, pdfPageAspectRatio
            FROM notes
        """.trimIndent())

        database.execSQL("DROP TABLE notes")
        database.execSQL("ALTER TABLE notes_new RENAME TO notes")

        database.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_parentNotebookId` ON `notes` (`parentNotebookId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
    }
}
