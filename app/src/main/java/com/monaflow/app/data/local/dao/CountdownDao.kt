package com.monaflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.monaflow.app.data.local.entity.AttachmentEntity
import com.monaflow.app.data.local.entity.CountdownEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {

    @Query("SELECT * FROM countdowns ORDER BY targetEpochMillis ASC")
    fun observeAll(): Flow<List<CountdownEntity>>

    @Query("SELECT * FROM countdowns WHERE id = :id")
    suspend fun byId(id: Long): CountdownEntity?

    @Query("SELECT * FROM countdowns")
    suspend fun all(): List<CountdownEntity>

    @Insert
    suspend fun insert(countdown: CountdownEntity): Long

    @Update
    suspend fun update(countdown: CountdownEntity)

    @Query("DELETE FROM countdowns WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt ASC")
    fun observeByOwner(ownerType: String, ownerId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt ASC")
    suspend fun byOwner(ownerType: String, ownerId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    suspend fun all(): List<AttachmentEntity>

    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteByOwner(ownerType: String, ownerId: Long)
}
