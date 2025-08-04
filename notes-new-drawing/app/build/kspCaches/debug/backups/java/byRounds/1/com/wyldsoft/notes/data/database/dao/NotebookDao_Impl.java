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
import com.wyldsoft.notes.data.database.entities.NotebookEntity;
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
public final class NotebookDao_Impl implements NotebookDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<NotebookEntity> __insertionAdapterOfNotebookEntity;

  private final EntityDeletionOrUpdateAdapter<NotebookEntity> __deletionAdapterOfNotebookEntity;

  private final EntityDeletionOrUpdateAdapter<NotebookEntity> __updateAdapterOfNotebookEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public NotebookDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfNotebookEntity = new EntityInsertionAdapter<NotebookEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `notebooks` (`id`,`name`,`folderId`,`settings`,`createdAt`,`modifiedAt`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NotebookEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getFolderId());
        statement.bindString(4, entity.getSettings());
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindLong(6, entity.getModifiedAt());
      }
    };
    this.__deletionAdapterOfNotebookEntity = new EntityDeletionOrUpdateAdapter<NotebookEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `notebooks` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NotebookEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__updateAdapterOfNotebookEntity = new EntityDeletionOrUpdateAdapter<NotebookEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `notebooks` SET `id` = ?,`name` = ?,`folderId` = ?,`settings` = ?,`createdAt` = ?,`modifiedAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NotebookEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getFolderId());
        statement.bindString(4, entity.getSettings());
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindLong(6, entity.getModifiedAt());
        statement.bindString(7, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM notebooks WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final NotebookEntity notebook,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfNotebookEntity.insert(notebook);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final NotebookEntity notebook,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfNotebookEntity.handle(notebook);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final NotebookEntity notebook,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfNotebookEntity.handle(notebook);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String notebookId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, notebookId);
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
  public Object getNotebook(final String notebookId,
      final Continuation<? super NotebookEntity> $completion) {
    final String _sql = "SELECT * FROM notebooks WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, notebookId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<NotebookEntity>() {
      @Override
      @Nullable
      public NotebookEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfSettings = CursorUtil.getColumnIndexOrThrow(_cursor, "settings");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModifiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAt");
          final NotebookEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpFolderId;
            _tmpFolderId = _cursor.getString(_cursorIndexOfFolderId);
            final String _tmpSettings;
            _tmpSettings = _cursor.getString(_cursorIndexOfSettings);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpModifiedAt;
            _tmpModifiedAt = _cursor.getLong(_cursorIndexOfModifiedAt);
            _result = new NotebookEntity(_tmpId,_tmpName,_tmpFolderId,_tmpSettings,_tmpCreatedAt,_tmpModifiedAt);
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
  public Flow<List<NotebookEntity>> getNotebooksByFolder(final String folderId) {
    final String _sql = "SELECT * FROM notebooks WHERE folderId = ? ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, folderId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notebooks"}, new Callable<List<NotebookEntity>>() {
      @Override
      @NonNull
      public List<NotebookEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfSettings = CursorUtil.getColumnIndexOrThrow(_cursor, "settings");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModifiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAt");
          final List<NotebookEntity> _result = new ArrayList<NotebookEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final NotebookEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpFolderId;
            _tmpFolderId = _cursor.getString(_cursorIndexOfFolderId);
            final String _tmpSettings;
            _tmpSettings = _cursor.getString(_cursorIndexOfSettings);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpModifiedAt;
            _tmpModifiedAt = _cursor.getLong(_cursorIndexOfModifiedAt);
            _item = new NotebookEntity(_tmpId,_tmpName,_tmpFolderId,_tmpSettings,_tmpCreatedAt,_tmpModifiedAt);
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
  public Flow<List<NoteEntity>> getNotesInNotebook(final String notebookId) {
    final String _sql = "\n"
            + "        SELECT n.* FROM notes n\n"
            + "        JOIN note_notebook_cross_ref cr ON n.id = cr.noteId\n"
            + "        WHERE cr.notebookId = ?\n"
            + "        ORDER BY n.createdAt\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, notebookId);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"notes",
        "note_notebook_cross_ref"}, new Callable<List<NoteEntity>>() {
      @Override
      @NonNull
      public List<NoteEntity> call() throws Exception {
        __db.beginTransaction();
        try {
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
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getFirstNoteInNotebook(final String notebookId,
      final Continuation<? super NoteEntity> $completion) {
    final String _sql = "\n"
            + "        SELECT n.* FROM notes n\n"
            + "        JOIN note_notebook_cross_ref cr ON n.id = cr.noteId\n"
            + "        WHERE cr.notebookId = ?\n"
            + "        ORDER BY n.createdAt\n"
            + "        LIMIT 1\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, notebookId);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
