package com.wyldsoft.notes.data.database.dao

import androidx.room.*
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity

@Dao
interface DeletedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DeletedItemEntity)

    @Query("SELECT * FROM deleted_items")
    suspend fun getAll(): List<DeletedItemEntity>

    @Query("SELECT * FROM deleted_items WHERE entityId = :entityId")
    suspend fun getByEntityId(entityId: String): DeletedItemEntity?

    @Query("DELETE FROM deleted_items WHERE deletedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
