package com.monaflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.monaflow.app.data.local.entity.AiProviderEntity
import com.monaflow.app.data.local.entity.AiUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiProviderDao {

    @Query("SELECT * FROM ai_providers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers ORDER BY createdAt ASC")
    suspend fun all(): List<AiProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE id = :id")
    suspend fun byId(id: Long): AiProviderEntity?

    @Query("SELECT * FROM ai_providers WHERE isDefault = 1 LIMIT 1")
    suspend fun defaultProvider(): AiProviderEntity?

    @Insert
    suspend fun insert(provider: AiProviderEntity): Long

    @Update
    suspend fun update(provider: AiProviderEntity)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE ai_providers SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setDefault(id: Long)

    @Query("UPDATE ai_providers SET lastTestStatus = :status, lastTestAt = :at WHERE id = :id")
    suspend fun recordTestResult(id: Long, status: String, at: Long)
}

@Dao
interface AiUsageDao {

    @Insert
    suspend fun insert(usage: AiUsageEntity)

    @Query("SELECT * FROM ai_usage ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<AiUsageEntity>>

    @Query("SELECT COUNT(*) FROM ai_usage")
    suspend fun count(): Int

    @Query("DELETE FROM ai_usage")
    suspend fun clear()
}
