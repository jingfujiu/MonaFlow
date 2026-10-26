package com.monaflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.monaflow.app.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<ItemEntity>>

    @Query(
        """
        SELECT * FROM items WHERE isArchived = 0
          AND (name LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%'
               OR brand LIKE '%' || :q || '%' OR model LIKE '%' || :q || '%'
               OR storageLocation LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%')
        ORDER BY createdAt DESC
        """
    )
    fun search(q: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: Long): ItemEntity?

    @Query("SELECT * FROM items")
    suspend fun all(): List<ItemEntity>

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("UPDATE items SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM items WHERE isArchived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT DISTINCT category FROM items WHERE category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>
}
