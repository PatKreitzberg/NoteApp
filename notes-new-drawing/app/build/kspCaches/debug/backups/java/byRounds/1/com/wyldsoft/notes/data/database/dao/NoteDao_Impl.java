package com.wyldsoft.notes.data.database.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.wyldsoft.notes.data.database.entities.NoteEntity;
import com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRef;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class NoteDao_Impl implements NoteDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<NoteEntity> __insertionAdapterOfNoteEntity;

  private final EntityInsertionAdapter<NoteNotebookCrossRef> __insertionAdapterOfNoteNotebookCrossRef;

  private final EntityDeletionOrUpdateAdapter<NoteEntity> __deletionAdapterOfNoteEntity;

  private final EntityDeletionOrUpdateAdapter<NoteNotebookCrossRef> __deletionAdapterOfNoteNotebookCrossRef;

  private final EntityDeletionOrUpdateAdapter<NoteEntity> __updateAdapterOfNoteEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public NoteDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfNoteEntity = new EntityInsertionAdapter<NoteEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `notes` (`id`,`title`,`parentNotebookId`,`folderId`,`settings`,`createdAt`,`modifiedAt`,`viewportScale`,`viewportOffsetX`,`viewportOffsetY`,`isPaginationEnabled`,`paperSize`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NoteEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        if (entity.getParentNotebookId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getParentNotebookId());
        }
        if (entity.getFolderId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getFolderId());
        }
        statement.bindString(5, entity.getSettings());
        statement.bindLong(6, entity.getCreatedAt());
        statement.bindLong(7, entity.getModifiedAt());
        statement.bindDouble(8, entity.getViewportScale());
        statement.bindDouble(9, entity.getViewportOffsetX());
        statement.bindDouble(10, entity.getViewportOffsetY());
        final int _tmp = entity.isPaginationEnabled() ? 1 : 0;
        statement.bindLong(11, _tmp);
        statement.bindString(12, entity.getPaperSize());
      }
    };
    this.__insertionAdapterOfNoteNotebookCrossRef = new EntityInsertionAdapter<NoteNotebookCrossRef>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `note_notebook_cross_ref` (`noteId`,`notebookId`) VALUES (?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NoteNotebookCrossRef entity) {
        statement.bindString(1, entity.getNoteId());
        statement.bindString(2, entity.getNotebookId());
      }
    };
    this.__deletionAdapterOfNoteEntity = new EntityDeletionOrUpdateAdapter<NoteEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `notes` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NoteEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__deletionAdapterOfNoteNotebookCrossRef = new EntityDeletionOrUpdateAdapter<NoteNotebookCrossRef>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `note_notebook_cross_ref` WHERE `noteId` = ? AND `notebookId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NoteNotebookCrossRef entity) {
        statement.bindString(1, entity.getNoteId());
        statement.bindString(2, entity.getNotebookId());
      }
    };
    this.__updateAdapterOfNoteEntity = new EntityDeletionOrUpdateAdapter<NoteEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `notes` SET `id` = ?,`title` = ?,`parentNotebookId` = ?,`folderId` = ?,`settings` = ?,`createdAt` = ?,`modifiedAt` = ?,`viewportScale` = ?,`viewportOffsetX` = ?,`viewportOffsetY` = ?,`isPaginationEnabled` = ?,`paperSize` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NoteEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        if (entity.getParentNotebookId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getParentNotebookId());
        }
        if (entity.getFolderId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getFolderId());
        }
        statement.bindString(5, entity.getSettings());
        statement.bindLong(6, entity.getCreatedAt());
        statement.bindLong(7, entity.getModifiedAt());
        statement.bindDouble(8, entity.getViewportScale());
        statement.bindDouble(9, entity.getViewportOffsetX());
        statement.bindDouble(10, entity.getViewportOffsetY());
        final int _tmp = entity.isPaginationEnabled() ? 1 : 0;
        statement.bindLong(11, _tmp);
        statement.bindString(12, entity.getPaperSize());
        statement.bindString(13, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM notes WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final NoteEntity note, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfNoteEntity.insert(note);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertNoteNotebookCrossRef(final NoteNotebookCrossRef crossRef,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfNoteNotebookCrossRef.insert(crossRef);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final NoteEntity note, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfNoteEntity.handle(note);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteNoteNotebookCrossRef(final NoteNotebookCrossRef crossRef,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfNoteNotebookCrossRef.handle(crossRef);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final NoteEntity note, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfNoteEntity.handle(note);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String noteId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, noteId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getNote(final String noteId, final Continuation<? super NoteEntity> $completion) {
    final String _sql = "SELECT * FROM notes WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, noteId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<NoteEntity>() {
      @Override
      @Nullable
      public NoteEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfParentNotebookId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentNotebookId");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfSettings = CursorUtil.getColumnIndexOrThrow(_cursor, "settings");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModifiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAt");
          final int _cursorIndexOfViewportScale = CursorUtil.getColumnIndexOrThrow(_cursor, "viewportScale");
          final int _cursorIndexOfViewportOffsetX = CursorUtil.getColumnIndexOrThrow(_cursor, "viewportOffsetX");
          final int _cursorIndexOfViewportOffsetY = CursorUtil.getColumnIndexOrThrow(_cursor, "viewportOffsetY");
          final int _cursorIndexOfIsPaginationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaginationEnabled");
          final int _cursorIndexOfPaperSize = CursorUtil.getColumnIndexOrThrow(_cursor, "paperSize");
          final NoteEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpParentNotebookId;
            if (_cursor.isNull(_cursorIndexOfParentNotebookId)) {
              _tmpParentNotebookId = null;
            } else {
              _tmpParentNotebookId = _cursor.getString(_cursorIndexOfParentNotebookId);
            }
            final String _tmpFolderId;
            if (_cursor.isNull(_cursorIndexOfFolderId)) {
              _tmpFolderId = null;
            } else {
              _tmpFolderId = _cursor.getString(_cursorIndexOfFolderId);
            }
            final String _tmpSettings;
            _tmpSettings = _cursor.getString(_cursorIndexOfSettings);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpModifiedAt;
            _tmpModifiedAt = _cursor.getLong(_cursorIndexOfModifiedAt);
            final float _tmpViewportScale;
            _tmpViewportScale = _cursor.getFloat(_cursorIndexOfViewportScale);
            final float _tmpViewportOffsetX;
            _tmpViewportOffsetX = _cursor.getFloat(_cursorIndexOfViewportOffsetX);
            final float _tmpViewportOffsetY;
            _tmpViewportOffsetY = _cursor.getFloat(_cursorIndexOfViewportOffsetY);
            final boolean _tmpIsPaginationEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPaginationEnabled);
            _tmpIsPaginationEnabled = _tmp != 0;
            final String _tmpPaperSize;
            _tmpPaperSize = _cursor.getString(_cursorIndexOfPaperSize);
            _result = new NoteEntity(_tmpId,_tmpTitle,_tmpParentNotebookId,_tmpFolderId,_tmpSettings,_tmpCreatedAt,_tmpModifiedAt,_tmpViewportScale,_tmpViewportOffsetX,_tmpViewportOffsetY,_tmpIsPaginationEnabled,_tmpPaperSize);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<NoteEntity> getNoteFlow(final String noteId) {
    final String _sql = "SELECT * FROM notes WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, noteId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notes"}, new Callable<NoteEntity>() {
      @Override
      @Nullable
      public NoteEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfParentNotebookId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentNotebookId");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfSettings = CursorUtil.getColumnIndexOrThrow(_cursor, "settings");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModifiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAt");
          final int _cursorIndexOfViewportScale = CursorUtil.getColumnIndexOrThrow(_cursor, "viewportScale");
          final int _cursorIndexOfViewportOffsetX = CursorUtil.getColumnIndexOrThrow(_cursor, "viewportOffsetX");
          final int _cursorIndexOfViewportOffsetY = CursorUtil.getColumnIndexOrThrow(_cursor, "viewportOffsetY");
          final int _cursorIndexOfIsPaginationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaginationEnabled");
          final int _cursorIndexOfPaperSize = CursorUtil.getColumnIndexOrThrow(_cursor, "paperSize");
          final NoteEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpParentNotebookId;
            if (_cursor.isNull(_cursorIndexOfParentNotebookId)) {
              _tmpParentNotebookId = null;
            } else {
              _tmpParentNotebookId = _cursor.getString(_cursorIndexOfParentNotebookId);
            }
            final String _tmpFolderId;
            if (_cursor.isNull(_cursorIndexOfFolderId)) {
              _tmpFolderId = null;
            } else {
              _tmpFolderId = _cursor.getString(_cursorIndexOfFolderId);
            }
            final String _tmpSettings;
            _tmpSettings = _cursor.getString(_cursorIndexOfSettings);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpModifiedAt;
            _tmpModifiedAt = _cursor.getLong(_cursorIndexOfModifiedAt);
            final float _tmpViewportScale;
            _tmpViewportScale = _cursor.getFloat(_cursorIndexOfViewportScale);
            final float _tmpViewportOffsetX;
            _tmpViewportOffsetX = _cursor.getFloat(_cursorIndexOfViewportOffsetX);
            final float _tmpViewportOffsetY;
            _tmpViewportOffsetY = _cursor.getFloat(_cursorIndexOfViewportOffsetY);
            final boolean _tmpIsPaginationEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPaginationEnabled);
            _tmpIsPaginationEnabled = _tmp != 0;
            final String _tmpPaperSize;
            _tmpPaperSize = _cursor.getString(_cursorIndexOfPaperSize);
            _result = new NoteEntity(_tmpId,_tmpTitle,_tmpParentNotebookId,_tmpFolderId,_tmpSettings,_tmpCreatedAt,_tmpModifiedAt,_tmpViewportScale,_tmpViewportOffsetX,_tmpViewportOffsetY,_tmpIsPaginationEnabled,_tmpPaperSize);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<NoteEntity>> getLooseNotesInFolder(final String folderId) {
    final String _sql = "SELECT * FROM notes WHERE folderId = ? AND parentNotebookId IS NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, folderId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notes"}, new Callable<List<NoteEntity>>() {
      @Override
      @NonNull
      public List<NoteEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfParentNotebookId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentNotebookId");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfSettings = CursorUtil.getColumnIndexOrThrow(_cursor, "settings");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModifiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAt");
          final int _cursorIndexOfViewportScale = CursorUtil.getColumnIndexOrThrow(_cursor, "viewportScale");
          final int _cursorIndexOfViewportOffsetX = CursorUtil.getColumnIndexOrThrow(_cursor, "viewportOffsetX");
          final int _cursorIndexOfViewportOffsetY = CursorUtil.getColumnIndexOrThrow(_cursor, "viewportOffsetY");
          final int _cursorIndexOfIsPaginationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaginationEnabled");
          final int _cursorIndexOfPaperSize = CursorUtil.getColumnIndexOrThrow(_cursor, "paperSize");
          final List<NoteEntity> _result = new ArrayList<NoteEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final NoteEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpParentNotebookId;
            if (_cursor.isNull(_cursorIndexOfParentNotebookId)) {
              _tmpParentNotebookId = null;
            } else {
              _tmpParentNotebookId = _cursor.getString(_cursorIndexOfParentNotebookId);
            }
            final String _tmpFolderId;
            if (_cursor.isNull(_cursorIndexOfFolderId)) {
              _tmpFolderId = null;
            } else {
              _tmpFolderId = _cursor.getString(_cursorIndexOfFolderId);
            }
            final String _tmpSettings;
            _tmpSettings = _cursor.getString(_cursorIndexOfSettings);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpModifiedAt;
            _tmpModifiedAt = _cursor.getLong(_cursorIndexOfModifiedAt);
            final float _tmpViewportScale;
            _tmpViewportScale = _cursor.getFloat(_cursorIndexOfViewportScale);
            final float _tmpViewportOffsetX;
            _tmpViewportOffsetX = _cursor.getFloat(_cursorIndexOfViewportOffsetX);
            final float _tmpViewportOffsetY;
            _tmpViewportOffsetY = _cursor.getFloat(_cursorIndexOfViewportOffsetY);
            final boolean _tmpIsPaginationEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPaginationEnabled);
            _tmpIsPaginationEnabled = _tmp != 0;
            final String _tmpPaperSize;
            _tmpPaperSize = _cursor.getString(_cursorIndexOfPaperSize);
            _item = new NoteEntity(_tmpId,_tmpTitle,_tmpParentNotebookId,_tmpFolderId,_tmpSettings,_tmpCreatedAt,_tmpModifiedAt,_tmpViewportScale,_tmpViewportOffsetX,_tmpViewportOffsetY,_tmpIsPaginationEnabled,_tmpPaperSize);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getNotebooksForNote(final String noteId,
      final Continuation<? super List<String>> $completion) {
    final String _sql = "SELECT notebookId FROM note_notebook_cross_ref WHERE noteId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, noteId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<String>>() {
      @Override
      @NonNull
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item;
            _item = _cursor.getString(0);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
