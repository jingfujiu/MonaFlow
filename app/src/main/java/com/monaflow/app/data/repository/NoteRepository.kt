package com.monaflow.app.data.repository

import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.CategoryEntity
import com.monaflow.app.data.local.entity.NoteEntity
import com.monaflow.app.data.local.entity.TagEntity
import com.monaflow.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val db: MonaFlowDatabase) {
    private val dao = db.noteDao()

    fun observeActive(): Flow<List<NoteEntity>> = dao.observeActive()
    fun observeArchived(): Flow<List<NoteEntity>> = dao.observeArchived()
    fun observeTrash(): Flow<List<NoteEntity>> = dao.observeTrash()
    fun observeAllLive(): Flow<List<NoteEntity>> = dao.observeAllLive()
    fun searchActive(q: String): Flow<List<NoteEntity>> = dao.searchActive(escapeLike(q))
    fun observeTags(): Flow<List<TagEntity>> = dao.observeTags()
    fun observeById(id: Long): Flow<NoteEntity?> = dao.observeById(id)
    fun observeCategories(kind: String = KIND_NOTE): Flow<List<CategoryEntity>> = dao.observeCategories(kind)

    suspend fun byId(id: Long): NoteEntity? = dao.byId(id)
    suspend fun tagsOf(noteId: Long): List<TagEntity> = dao.tagsOf(noteId)

    suspend fun upsert(note: NoteEntity, tagNames: List<String>? = null): Long {
        val now = System.currentTimeMillis()
        return if (note.id == 0L) {
            val id = dao.insert(note.copy(createdAt = now, updatedAt = now))
            tagNames?.let { dao.replaceNoteTags(id, it, now) }
            id
        } else {
            // 保留创建时间等编辑表单不携带的字段，避免被重置
            val existing = dao.byId(note.id)
            dao.update(
                note.copy(
                    updatedAt = now,
                    createdAt = existing?.createdAt ?: now,
                    deletedAt = existing?.deletedAt ?: note.deletedAt,
                ),
            )
            tagNames?.let { dao.replaceNoteTags(note.id, it, now) }
            note.id
        }
    }

    suspend fun replaceTags(noteId: Long, tagNames: List<String>) {
        dao.replaceNoteTags(noteId, tagNames, System.currentTimeMillis())
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned, System.currentTimeMillis())
    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite, System.currentTimeMillis())
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived, System.currentTimeMillis())
    suspend fun moveToTrash(id: Long) = dao.moveToTrash(id, System.currentTimeMillis())
    suspend fun restore(id: Long) = dao.restore(id, System.currentTimeMillis())

    /** 彻底删除：附件记录一并清理，附件文件由 AttachmentManager 删除。 */
    suspend fun hardDelete(id: Long) {
        db.attachmentDao().deleteByOwner(OWNER_NOTE, id)
        dao.hardDelete(id)
    }

    suspend fun emptyTrash(): List<NoteEntity> {
        val all = dao.trashAll()
        for (note in all) {
            db.attachmentDao().deleteByOwner(OWNER_NOTE, note.id)
        }
        dao.emptyTrash()
        return all
    }

    suspend fun ensureCategory(name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        dao.categoryByName(KIND_NOTE, trimmed)?.let { return it.id }
        return dao.insertCategory(CategoryEntity(name = trimmed, kind = KIND_NOTE))
    }

    /** 笔记转待办：生成一条待办任务并返回其 id；笔记本身保持不变。 */
    suspend fun convertToTask(noteId: Long, dueAt: Long? = null): Long {
        val note = dao.byId(noteId) ?: error("笔记不存在")
        val task = TaskEntity(
            title = note.title.ifBlank { note.content.lineSequence().firstOrNull()?.take(20) ?: "来自笔记" },
            description = note.content,
            createdAt = System.currentTimeMillis(),
            dueAt = dueAt,
        )
        return db.taskDao().insert(task)
    }

    companion object {
        const val KIND_NOTE = "note"
        const val OWNER_NOTE = "note"

        /** LIKE 转义，防止 % _ 破坏搜索语义。 */
        fun escapeLike(q: String): String =
            q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
