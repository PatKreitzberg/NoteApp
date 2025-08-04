package com.wyldsoft.notes.data.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.wyldsoft.notes.data.database.dao.FolderDao;
import com.wyldsoft.notes.data.database.dao.FolderDao_Impl;
import com.wyldsoft.notes.data.database.dao.NoteDao;
import com.wyldsoft.notes.data.database.dao.NoteDao_Impl;
import com.wyldsoft.notes.data.database.dao.NotebookDao;
import com.wyldsoft.notes.data.database.dao.NotebookDao_Impl;
import com.wyldsoft.notes.data.database.dao.ShapeDao;
import com.wyldsoft.notes.data.database.dao.ShapeDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class NotesDatabase_Impl extends NotesDatabase {
  private volatile FolderDao _folderDao;

  private volatile NotebookDao _notebookDao;

  private volatile NoteDao _noteDao;

  private volatile ShapeDao _shapeDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `parentFolderId` TEXT, `createdAt` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`parentFolderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentFolderId` ON `folders` (`parentFolderId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `notebooks` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `folderId` TEXT NOT NULL, `settings` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notebooks_folderId` ON `notebooks` (`folderId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `parentNotebookId` TEXT, `folderId` TEXT, `settings` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, `viewportScale` REAL NOT NULL, `viewportOffsetX` REAL NOT NULL, `viewportOffsetY` REAL NOT NULL, `isPaginationEnabled` INTEGER NOT NULL, `paperSize` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`parentNotebookId`) REFERENCES `notebooks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_parentNotebookId` ON `notes` (`parentNotebookId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `note_notebook_cross_ref` (`noteId` TEXT NOT NULL, `notebookId` TEXT NOT NULL, PRIMARY KEY(`noteId`, `notebookId`), FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`notebookId`) REFERENCES `notebooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_notebook_cross_ref_noteId` ON `note_notebook_cross_ref` (`noteId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_notebook_cross_ref_notebookId` ON `note_notebook_cross_ref` (`notebookId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `shapes` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `type` TEXT NOT NULL, `points` TEXT NOT NULL, `strokeWidth` REAL NOT NULL, `strokeColor` INTEGER NOT NULL, `pressure` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shapes_noteId` ON `shapes` (`noteId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6ad0ec5ed87d9a1e6b7fbd8afb4a1b8f')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `folders`");
        db.execSQL("DROP TABLE IF EXISTS `notebooks`");
        db.execSQL("DROP TABLE IF EXISTS `notes`");
        db.execSQL("DROP TABLE IF EXISTS `note_notebook_cross_ref`");
        db.execSQL("DROP TABLE IF EXISTS `shapes`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsFolders = new HashMap<String, TableInfo.Column>(5);
        _columnsFolders.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolders.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolders.put("parentFolderId", new TableInfo.Column("parentFolderId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolders.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolders.put("modifiedAt", new TableInfo.Column("modifiedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFolders = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysFolders.add(new TableInfo.ForeignKey("folders", "CASCADE", "NO ACTION", Arrays.asList("parentFolderId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesFolders = new HashSet<TableInfo.Index>(1);
        _indicesFolders.add(new TableInfo.Index("index_folders_parentFolderId", false, Arrays.asList("parentFolderId"), Arrays.asList("ASC")));
        final TableInfo _infoFolders = new TableInfo("folders", _columnsFolders, _foreignKeysFolders, _indicesFolders);
        final TableInfo _existingFolders = TableInfo.read(db, "folders");
        if (!_infoFolders.equals(_existingFolders)) {
          return new RoomOpenHelper.ValidationResult(false, "folders(com.wyldsoft.notes.data.database.entities.FolderEntity).\n"
                  + " Expected:\n" + _infoFolders + "\n"
                  + " Found:\n" + _existingFolders);
        }
        final HashMap<String, TableInfo.Column> _columnsNotebooks = new HashMap<String, TableInfo.Column>(6);
        _columnsNotebooks.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebooks.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebooks.put("folderId", new TableInfo.Column("folderId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebooks.put("settings", new TableInfo.Column("settings", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebooks.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebooks.put("modifiedAt", new TableInfo.Column("modifiedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysNotebooks = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysNotebooks.add(new TableInfo.ForeignKey("folders", "CASCADE", "NO ACTION", Arrays.asList("folderId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesNotebooks = new HashSet<TableInfo.Index>(1);
        _indicesNotebooks.add(new TableInfo.Index("index_notebooks_folderId", false, Arrays.asList("folderId"), Arrays.asList("ASC")));
        final TableInfo _infoNotebooks = new TableInfo("notebooks", _columnsNotebooks, _foreignKeysNotebooks, _indicesNotebooks);
        final TableInfo _existingNotebooks = TableInfo.read(db, "notebooks");
        if (!_infoNotebooks.equals(_existingNotebooks)) {
          return new RoomOpenHelper.ValidationResult(false, "notebooks(com.wyldsoft.notes.data.database.entities.NotebookEntity).\n"
                  + " Expected:\n" + _infoNotebooks + "\n"
                  + " Found:\n" + _existingNotebooks);
        }
        final HashMap<String, TableInfo.Column> _columnsNotes = new HashMap<String, TableInfo.Column>(12);
        _columnsNotes.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("parentNotebookId", new TableInfo.Column("parentNotebookId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("folderId", new TableInfo.Column("folderId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("settings", new TableInfo.Column("settings", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("modifiedAt", new TableInfo.Column("modifiedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("viewportScale", new TableInfo.Column("viewportScale", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("viewportOffsetX", new TableInfo.Column("viewportOffsetX", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("viewportOffsetY", new TableInfo.Column("viewportOffsetY", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("isPaginationEnabled", new TableInfo.Column("isPaginationEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotes.put("paperSize", new TableInfo.Column("paperSize", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysNotes = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysNotes.add(new TableInfo.ForeignKey("notebooks", "SET NULL", "NO ACTION", Arrays.asList("parentNotebookId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesNotes = new HashSet<TableInfo.Index>(1);
        _indicesNotes.add(new TableInfo.Index("index_notes_parentNotebookId", false, Arrays.asList("parentNotebookId"), Arrays.asList("ASC")));
        final TableInfo _infoNotes = new TableInfo("notes", _columnsNotes, _foreignKeysNotes, _indicesNotes);
        final TableInfo _existingNotes = TableInfo.read(db, "notes");
        if (!_infoNotes.equals(_existingNotes)) {
          return new RoomOpenHelper.ValidationResult(false, "notes(com.wyldsoft.notes.data.database.entities.NoteEntity).\n"
                  + " Expected:\n" + _infoNotes + "\n"
                  + " Found:\n" + _existingNotes);
        }
        final HashMap<String, TableInfo.Column> _columnsNoteNotebookCrossRef = new HashMap<String, TableInfo.Column>(2);
        _columnsNoteNotebookCrossRef.put("noteId", new TableInfo.Column("noteId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNoteNotebookCrossRef.put("notebookId", new TableInfo.Column("notebookId", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysNoteNotebookCrossRef = new HashSet<TableInfo.ForeignKey>(2);
        _foreignKeysNoteNotebookCrossRef.add(new TableInfo.ForeignKey("notes", "CASCADE", "NO ACTION", Arrays.asList("noteId"), Arrays.asList("id")));
        _foreignKeysNoteNotebookCrossRef.add(new TableInfo.ForeignKey("notebooks", "CASCADE", "NO ACTION", Arrays.asList("notebookId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesNoteNotebookCrossRef = new HashSet<TableInfo.Index>(2);
        _indicesNoteNotebookCrossRef.add(new TableInfo.Index("index_note_notebook_cross_ref_noteId", false, Arrays.asList("noteId"), Arrays.asList("ASC")));
        _indicesNoteNotebookCrossRef.add(new TableInfo.Index("index_note_notebook_cross_ref_notebookId", false, Arrays.asList("notebookId"), Arrays.asList("ASC")));
        final TableInfo _infoNoteNotebookCrossRef = new TableInfo("note_notebook_cross_ref", _columnsNoteNotebookCrossRef, _foreignKeysNoteNotebookCrossRef, _indicesNoteNotebookCrossRef);
        final TableInfo _existingNoteNotebookCrossRef = TableInfo.read(db, "note_notebook_cross_ref");
        if (!_infoNoteNotebookCrossRef.equals(_existingNoteNotebookCrossRef)) {
          return new RoomOpenHelper.ValidationResult(false, "note_notebook_cross_ref(com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRef).\n"
                  + " Expected:\n" + _infoNoteNotebookCrossRef + "\n"
                  + " Found:\n" + _existingNoteNotebookCrossRef);
        }
        final HashMap<String, TableInfo.Column> _columnsShapes = new HashMap<String, TableInfo.Column>(8);
        _columnsShapes.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("noteId", new TableInfo.Column("noteId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("points", new TableInfo.Column("points", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("strokeWidth", new TableInfo.Column("strokeWidth", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("strokeColor", new TableInfo.Column("strokeColor", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("pressure", new TableInfo.Column("pressure", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysShapes = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysShapes.add(new TableInfo.ForeignKey("notes", "CASCADE", "NO ACTION", Arrays.asList("noteId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesShapes = new HashSet<TableInfo.Index>(1);
        _indicesShapes.add(new TableInfo.Index("index_shapes_noteId", false, Arrays.asList("noteId"), Arrays.asList("ASC")));
        final TableInfo _infoShapes = new TableInfo("shapes", _columnsShapes, _foreignKeysShapes, _indicesShapes);
        final TableInfo _existingShapes = TableInfo.read(db, "shapes");
        if (!_infoShapes.equals(_existingShapes)) {
          return new RoomOpenHelper.ValidationResult(false, "shapes(com.wyldsoft.notes.data.database.entities.ShapeEntity).\n"
                  + " Expected:\n" + _infoShapes + "\n"
                  + " Found:\n" + _existingShapes);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "6ad0ec5ed87d9a1e6b7fbd8afb4a1b8f", "9c647957fa595a81f833041bb11a79cb");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "folders","notebooks","notes","note_notebook_cross_ref","shapes");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `folders`");
      _db.execSQL("DELETE FROM `notebooks`");
      _db.execSQL("DELETE FROM `notes`");
      _db.execSQL("DELETE FROM `note_notebook_cross_ref`");
      _db.execSQL("DELETE FROM `shapes`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(FolderDao.class, FolderDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(NotebookDao.class, NotebookDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(NoteDao.class, NoteDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ShapeDao.class, ShapeDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public FolderDao folderDao() {
    if (_folderDao != null) {
      return _folderDao;
    } else {
      synchronized(this) {
        if(_folderDao == null) {
          _folderDao = new FolderDao_Impl(this);
        }
        return _folderDao;
      }
    }
  }

  @Override
  public NotebookDao notebookDao() {
    if (_notebookDao != null) {
      return _notebookDao;
    } else {
      synchronized(this) {
        if(_notebookDao == null) {
          _notebookDao = new NotebookDao_Impl(this);
        }
        return _notebookDao;
      }
    }
  }

  @Override
  public NoteDao noteDao() {
    if (_noteDao != null) {
      return _noteDao;
    } else {
      synchronized(this) {
        if(_noteDao == null) {
          _noteDao = new NoteDao_Impl(this);
        }
        return _noteDao;
      }
    }
  }

  @Override
  public ShapeDao shapeDao() {
    if (_shapeDao != null) {
      return _shapeDao;
    } else {
      synchronized(this) {
        if(_shapeDao == null) {
          _shapeDao = new ShapeDao_Impl(this);
        }
        return _shapeDao;
      }
    }
  }
}
