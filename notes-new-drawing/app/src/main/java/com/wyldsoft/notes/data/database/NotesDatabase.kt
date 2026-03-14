package com.wyldsoft.notes.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wyldsoft.notes.data.database.converters.Converters
import com.wyldsoft.notes.data.database.dao.*
import com.wyldsoft.notes.data.database.entities.*
import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.SyncStateDao
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity
import com.wyldsoft.notes.data.database.entities.SyncStateEntity
import java.util.UUID

@Database(
    entities = [
        FolderEntity::class,
        NotebookEntity::class,
        NoteEntity::class,
        NoteNotebookCrossRef::class,
        ShapeEntity::class,
        RecognizedSegmentEntity::class,
        DeletedItemEntity::class,
        SyncStateEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun notebookDao(): NotebookDao
    abstract fun noteDao(): NoteDao
    abstract fun shapeDao(): ShapeDao
    abstract fun recognizedSegmentDao(): RecognizedSegmentDao
    abstract fun deletedItemDao(): DeletedItemDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        @Volatile
        private var INSTANCE: NotesDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shapes ADD COLUMN penType TEXT NOT NULL DEFAULT 'BALLPEN'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN paperTemplate TEXT NOT NULL DEFAULT 'BLANK'")
            }
        }

        // Convert viewport offsets (old: negative screen-pixel values) to scroll positions
        // (new: positive NoteCoordinate values). scrollX = -offsetX/scale, scrollY = -offsetY/scale.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE notes SET
                        viewportOffsetX = CASE WHEN viewportScale > 0 THEN -viewportOffsetX / viewportScale ELSE 0 END,
                        viewportOffsetY = CASE WHEN viewportScale > 0 THEN -viewportOffsetY / viewportScale ELSE 0 END
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shapes ADD COLUMN text TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shapes ADD COLUMN fontSize REAL NOT NULL DEFAULT 32.0")
                db.execSQL("ALTER TABLE shapes ADD COLUMN fontFamily TEXT NOT NULL DEFAULT 'sans-serif'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE deleted_items ADD COLUMN originalParentId TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS deleted_items (
                        entityId TEXT NOT NULL PRIMARY KEY,
                        entityType TEXT NOT NULL,
                        deletedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_state (
                        deviceId TEXT NOT NULL PRIMARY KEY,
                        lastSyncTimestamp INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("INSERT OR IGNORE INTO folders (id, name, parentFolderId) VALUES ('root', 'Root', NULL)")
                        db.execSQL("INSERT OR IGNORE INTO folders (id, name, parentFolderId) VALUES ('trash', 'Trash', 'root')")
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}