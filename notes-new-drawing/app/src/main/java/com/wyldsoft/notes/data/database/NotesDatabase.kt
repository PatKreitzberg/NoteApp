package com.wyldsoft.notes.data.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wyldsoft.notes.data.database.converters.Converters
import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.FolderDao
import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.dao.SyncStateDao
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRefEntity
import com.wyldsoft.notes.data.database.entities.ShapeEntity
import com.wyldsoft.notes.data.database.entities.SyncStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        FolderEntity::class,
        NotebookEntity::class,
        NoteEntity::class,
        NoteNotebookCrossRefEntity::class,
        ShapeEntity::class,
        SyncStateEntity::class,
        DeletedItemEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun notebookDao(): NotebookDao
    abstract fun noteDao(): NoteDao
    abstract fun shapeDao(): ShapeDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun deletedItemDao(): DeletedItemDao

    companion object {
        private const val TAG = "NotesDatabase"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS sync_state (
                        deviceId TEXT NOT NULL PRIMARY KEY,
                        lastSyncTimestamp INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS deleted_items (
                        entityId TEXT NOT NULL PRIMARY KEY,
                        entityType TEXT NOT NULL,
                        deletedAt INTEGER NOT NULL,
                        originalParentId TEXT
                    )"""
                )
            }
        }

        @Volatile
        private var INSTANCE: NotesDatabase? = null

        fun getInstance(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): NotesDatabase {
            Log.d(TAG, "buildDatabase")
            return Room.databaseBuilder(
                context.applicationContext,
                NotesDatabase::class.java,
                "notes_database"
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(SeedCallback())
                .build()
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Log.d(TAG, "SeedCallback.onCreate — inserting root folder")
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT INTO folders (id, name, parentFolderId, createdAt, modifiedAt) VALUES (?, ?, NULL, ?, ?)",
                arrayOf<Any>(FolderEntity.ROOT_ID, "Home", now, now)
            )
        }
    }
}
