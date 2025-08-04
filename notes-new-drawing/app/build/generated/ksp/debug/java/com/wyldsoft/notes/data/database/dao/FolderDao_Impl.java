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
import com.wyldsoft.notes.data.database.entities.FolderEntity;
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
public final class FolderDao_Impl implements FolderDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<FolderEntity> __insertionAdapterOfFolderEntity;

  private final EntityDeletionOrUpdateAdapter<FolderEntity> __deletionAdapterOfFolderEntity;

  private final EntityDeletionOrUpdateAdapter<FolderEntity> __updateAdapterOfFolderEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public FolderDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfFolderEntity = new EntityInsertionAdapter<FolderEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `folders` (`id`,`name`,`parentFolderId`,`createdAt`,`modifiedAt`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FolderEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        if (entity.getParentFolderId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getParentFolderId());
        }
        statement.bindLong(4, entity.getCreatedAt());
        statement.bindLong(5, entity.getModifiedAt());
      }
    };
    this.__deletionAdapterOfFolderEntity = new EntityDeletionOrUpdateAdapter<FolderEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `folders` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FolderEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__updateAdapterOfFolderEntity = new EntityDeletionOrUpdateAdapter<FolderEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `folders` SET `id` = ?,`name` = ?,`parentFolderId` = ?,`createdAt` = ?,`modifiedAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FolderEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        if (entity.getParentFolderId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getParentFolderId());
        }
        statement.bindLong(4, entity.getCreatedAt());
        statement.bindLong(5, entity.getModifiedAt());
        statement.bindString(6, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM folders WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final FolderEntity folder, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfFolderEntity.insert(folder);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final FolderEntity folder, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfFolderEntity.handle(folder);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final FolderEntity folder, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfFolderEntity.handle(folder);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String folderId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, folderId);
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
  public Object getFolder(final String folderId,
      final Continuation<? super FolderEntity> $completion) {
    final String _sql = "SELECT * FROM folders WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, folderId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<FolderEntity>() {
      @Override
      @Nullable
      public FolderEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfParentFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentFolderId");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModifiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAt");
          final FolderEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpParentFolderId;
            if (_cursor.isNull(_cursorIndexOfParentFolderId)) {
              _tmpParentFolderId = null;
            } else {
              _tmpParentFolderId = _cursor.getString(_cursorIndexOfParentFolderId);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpModifiedAt;
            _tmpModifiedAt = _cursor.getLong(_cursorIndexOfModifiedAt);
            _result = new FolderEntity(_tmpId,_tmpName,_tmpParentFolderId,_tmpCreatedAt,_tmpModifiedAt);
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
  public Flow<List<FolderEntity>> getSubfolders(final String parentId) {
    final String _sql = "SELECT * FROM folders WHERE parentFolderId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (parentId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, parentId);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"folders"}, new Callable<List<FolderEntity>>() {
      @Override
      @NonNull
      public List<FolderEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfParentFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentFolderId");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModifiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAt");
          final List<FolderEntity> _result = new ArrayList<FolderEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FolderEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpParentFolderId;
            if (_cursor.isNull(_cursorIndexOfParentFolderId)) {
              _tmpParentFolderId = null;
            } else {
              _tmpParentFolderId = _cursor.getString(_cursorIndexOfParentFolderId);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpModifiedAt;
            _tmpModifiedAt = _cursor.getLong(_cursorIndexOfModifiedAt);
            _item = new FolderEntity(_tmpId,_tmpName,_tmpParentFolderId,_tmpCreatedAt,_tmpModifiedAt);
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
  public Object getRootFolder(final Continuation<? super FolderEntity> $completion) {
    final String _sql = "SELECT * FROM folders WHERE parentFolderId IS NULL LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<FolderEntity>() {
      @Override
      @Nullable
      public FolderEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfParentFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentFolderId");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModifiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAt");
          final FolderEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpParentFolderId;
            if (_cursor.isNull(_cursorIndexOfParentFolderId)) {
              _tmpParentFolderId = null;
            } else {
              _tmpParentFolderId = _cursor.getString(_cursorIndexOfParentFolderId);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpModifiedAt;
            _tmpModifiedAt = _cursor.getLong(_cursorIndexOfModifiedAt);
            _result = new FolderEntity(_tmpId,_tmpName,_tmpParentFolderId,_tmpCreatedAt,_tmpModifiedAt);
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
  public Object getFolderPath(final String folderId,
      final Continuation<? super List<FolderEntity>> $completion) {
    final String _sql = "\n"
            + "        WITH RECURSIVE folder_path AS (\n"
            + "            SELECT * FROM folders WHERE id = ?\n"
            + "            UNION ALL\n"
            + "            SELECT f.* FROM folders f\n"
            + "            JOIN folder_path fp ON f.id = fp.parentFolderId\n"
            + "        )\n"
            + "        SELECT * FROM folder_path ORDER BY parentFolderId\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, folderId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FolderEntity>>() {
      @Override
      @NonNull
      public List<FolderEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfParentFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentFolderId");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModifiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAt");
          final List<FolderEntity> _result = new ArrayList<FolderEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FolderEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpParentFolderId;
            if (_cursor.isNull(_cursorIndexOfParentFolderId)) {
              _tmpParentFolderId = null;
            } else {
              _tmpParentFolderId = _cursor.getString(_cursorIndexOfParentFolderId);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpModifiedAt;
            _tmpModifiedAt = _cursor.getLong(_cursorIndexOfModifiedAt);
            _item = new FolderEntity(_tmpId,_tmpName,_tmpParentFolderId,_tmpCreatedAt,_tmpModifiedAt);
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
