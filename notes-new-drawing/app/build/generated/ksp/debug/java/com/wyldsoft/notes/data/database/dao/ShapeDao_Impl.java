package com.wyldsoft.notes.data.database.dao;

import android.database.Cursor;
import android.graphics.PointF;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.wyldsoft.notes.data.database.converters.Converters;
import com.wyldsoft.notes.data.database.entities.ShapeEntity;
import com.wyldsoft.notes.domain.models.ShapeType;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Float;
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
public final class ShapeDao_Impl implements ShapeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ShapeEntity> __insertionAdapterOfShapeEntity;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<ShapeEntity> __deletionAdapterOfShapeEntity;

  private final EntityDeletionOrUpdateAdapter<ShapeEntity> __updateAdapterOfShapeEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllForNote;

  public ShapeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfShapeEntity = new EntityInsertionAdapter<ShapeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `shapes` (`id`,`noteId`,`type`,`points`,`strokeWidth`,`strokeColor`,`pressure`,`timestamp`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ShapeEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getNoteId());
        final String _tmp = __converters.fromShapeType(entity.getType());
        statement.bindString(3, _tmp);
        final String _tmp_1 = __converters.fromPointFList(entity.getPoints());
        statement.bindString(4, _tmp_1);
        statement.bindDouble(5, entity.getStrokeWidth());
        statement.bindLong(6, entity.getStrokeColor());
        final String _tmp_2 = __converters.fromFloatList(entity.getPressure());
        statement.bindString(7, _tmp_2);
        statement.bindLong(8, entity.getTimestamp());
      }
    };
    this.__deletionAdapterOfShapeEntity = new EntityDeletionOrUpdateAdapter<ShapeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `shapes` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ShapeEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__updateAdapterOfShapeEntity = new EntityDeletionOrUpdateAdapter<ShapeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `shapes` SET `id` = ?,`noteId` = ?,`type` = ?,`points` = ?,`strokeWidth` = ?,`strokeColor` = ?,`pressure` = ?,`timestamp` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ShapeEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getNoteId());
        final String _tmp = __converters.fromShapeType(entity.getType());
        statement.bindString(3, _tmp);
        final String _tmp_1 = __converters.fromPointFList(entity.getPoints());
        statement.bindString(4, _tmp_1);
        statement.bindDouble(5, entity.getStrokeWidth());
        statement.bindLong(6, entity.getStrokeColor());
        final String _tmp_2 = __converters.fromFloatList(entity.getPressure());
        statement.bindString(7, _tmp_2);
        statement.bindLong(8, entity.getTimestamp());
        statement.bindString(9, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM shapes WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllForNote = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM shapes WHERE noteId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ShapeEntity shape, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfShapeEntity.insert(shape);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<ShapeEntity> shapes,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfShapeEntity.insert(shapes);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final ShapeEntity shape, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfShapeEntity.handle(shape);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final ShapeEntity shape, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfShapeEntity.handle(shape);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String shapeId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, shapeId);
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
  public Object deleteAllForNote(final String noteId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllForNote.acquire();
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
          __preparedStmtOfDeleteAllForNote.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ShapeEntity>> getShapesForNote(final String noteId) {
    final String _sql = "SELECT * FROM shapes WHERE noteId = ? ORDER BY timestamp";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, noteId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"shapes"}, new Callable<List<ShapeEntity>>() {
      @Override
      @NonNull
      public List<ShapeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "noteId");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfPoints = CursorUtil.getColumnIndexOrThrow(_cursor, "points");
          final int _cursorIndexOfStrokeWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "strokeWidth");
          final int _cursorIndexOfStrokeColor = CursorUtil.getColumnIndexOrThrow(_cursor, "strokeColor");
          final int _cursorIndexOfPressure = CursorUtil.getColumnIndexOrThrow(_cursor, "pressure");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<ShapeEntity> _result = new ArrayList<ShapeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShapeEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNoteId;
            _tmpNoteId = _cursor.getString(_cursorIndexOfNoteId);
            final ShapeType _tmpType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfType);
            _tmpType = __converters.toShapeType(_tmp);
            final List<PointF> _tmpPoints;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfPoints);
            _tmpPoints = __converters.toPointFList(_tmp_1);
            final float _tmpStrokeWidth;
            _tmpStrokeWidth = _cursor.getFloat(_cursorIndexOfStrokeWidth);
            final int _tmpStrokeColor;
            _tmpStrokeColor = _cursor.getInt(_cursorIndexOfStrokeColor);
            final List<Float> _tmpPressure;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfPressure);
            _tmpPressure = __converters.toFloatList(_tmp_2);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new ShapeEntity(_tmpId,_tmpNoteId,_tmpType,_tmpPoints,_tmpStrokeWidth,_tmpStrokeColor,_tmpPressure,_tmpTimestamp);
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
  public Object getShapesForNoteOnce(final String noteId,
      final Continuation<? super List<ShapeEntity>> $completion) {
    final String _sql = "SELECT * FROM shapes WHERE noteId = ? ORDER BY timestamp";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, noteId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ShapeEntity>>() {
      @Override
      @NonNull
      public List<ShapeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "noteId");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfPoints = CursorUtil.getColumnIndexOrThrow(_cursor, "points");
          final int _cursorIndexOfStrokeWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "strokeWidth");
          final int _cursorIndexOfStrokeColor = CursorUtil.getColumnIndexOrThrow(_cursor, "strokeColor");
          final int _cursorIndexOfPressure = CursorUtil.getColumnIndexOrThrow(_cursor, "pressure");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<ShapeEntity> _result = new ArrayList<ShapeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShapeEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNoteId;
            _tmpNoteId = _cursor.getString(_cursorIndexOfNoteId);
            final ShapeType _tmpType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfType);
            _tmpType = __converters.toShapeType(_tmp);
            final List<PointF> _tmpPoints;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfPoints);
            _tmpPoints = __converters.toPointFList(_tmp_1);
            final float _tmpStrokeWidth;
            _tmpStrokeWidth = _cursor.getFloat(_cursorIndexOfStrokeWidth);
            final int _tmpStrokeColor;
            _tmpStrokeColor = _cursor.getInt(_cursorIndexOfStrokeColor);
            final List<Float> _tmpPressure;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfPressure);
            _tmpPressure = __converters.toFloatList(_tmp_2);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new ShapeEntity(_tmpId,_tmpNoteId,_tmpType,_tmpPoints,_tmpStrokeWidth,_tmpStrokeColor,_tmpPressure,_tmpTimestamp);
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
