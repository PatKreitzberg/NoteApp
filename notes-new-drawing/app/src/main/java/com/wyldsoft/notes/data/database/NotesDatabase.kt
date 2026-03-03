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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        FolderEntity::class,
        NotebookEntity::class,
        NoteEntity::class,
        NoteNotebookCrossRef::class,
        ShapeEntity::class,
        RecognizedSegmentEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NotesDatabase : RoomDatabase() {
    
    abstract fun folderDao(): FolderDao
    abstract fun notebookDao(): NotebookDao
    abstract fun noteDao(): NoteDao
    abstract fun shapeDao(): ShapeDao
    abstract fun recognizedSegmentDao(): RecognizedSegmentDao
    
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

        fun getDatabase(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)

                        // Run once DB is fully built
                        CoroutineScope(Dispatchers.IO).launch {
                            getDatabase(context).folderDao().insert(
                                FolderEntity(
                                    id = "root",
                                    name = "Root",
                                    parentFolderId = null
                                )
                            )
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}