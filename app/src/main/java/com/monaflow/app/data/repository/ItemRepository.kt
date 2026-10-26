package com.monaflow.app.data.repository

import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class ItemRepository(private val db: MonaFlowDatabase) {
    private val dao = db.itemDao()

    fun observeActive(): Flow<List<ItemEntity>> = dao.observeActive()
    fun observeArchived(): Flow<List<ItemEntity>> = dao.observeArchived()
    fun search(q: String): Flow<List<ItemEntity>> = dao.search(NoteRepository.escapeLike(q))
    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()
    fun observeCategories(): Flow<List<String>> = dao.observeCategories()

    suspend fun byId(id: Long): ItemEntity? = dao.byId(id)
    suspend fun all(): List<ItemEntity> = dao.all()

    suspend fun upsert(item: ItemEntity): Long {
        val now = System.currentTimeMillis()
        return if (item.id == 0L) {
            dao.insert(item.copy(createdAt = now, updatedAt = now))
        } else {
            dao.update(item.copy(updatedAt = now))
            item.id
        }
    }

    suspend fun setArchived(id: Long, archived: Boolean) =
        dao.setArchived(id, archived, System.currentTimeMillis())

    suspend fun delete(id: Long) {
        db.attachmentDao().deleteByOwner(OWNER_ITEM, id)
        dao.delete(id)
    }

    /** 保修状态：null 表示未填写保修期。 */
    fun warrantyState(item: ItemEntity, today: LocalDate = MonaDate.today()): WarrantyState? {
        val end = item.warrantyEndDate ?: return null
        val endDate = LocalDate.ofEpochDay(end)
        return when {
            endDate.isBefore(today) -> WarrantyState.EXPIRED(endDate)
            endDate.isBefore(today.plusDays(30)) -> WarrantyState.EXPIRING_SOON(endDate)
            else -> WarrantyState.VALID(endDate)
        }
    }

    companion object {
        const val OWNER_ITEM = "item"
    }
}

sealed class WarrantyState {
    abstract val endDate: LocalDate

    data class VALID(override val endDate: LocalDate) : WarrantyState()
    data class EXPIRING_SOON(override val endDate: LocalDate) : WarrantyState()
    data class EXPIRED(override val endDate: LocalDate) : WarrantyState()
}
