package com.monaflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.monaflow.app.data.local.entity.CategoryEntity
import com.monaflow.app.data.local.entity.NoteEntity
import com.monaflow.app.data.local.entity.NoteTagCrossRef
import com.monaflow.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class NoteWithTags(
    val note: NoteEntity,
    val tags: List<TagEntity>,
)

@Dao
interface NoteDao {

    // ---------- 笔记 ----------

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL AND isArchived = 0
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL AND isArchived = 0
          AND (title LIKE '%' || :q || '%' ESCAPE '\' OR content LIKE '%' || :q || '%' ESCAPE '\')
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    fun searchActive(q: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllLive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    /** 软删除 → 回收站。 */
    @Query("UPDATE notes SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun moveToTrash(id: Long, deletedAt: Long)

    @Query("UPDATE notes SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun trashAll(): List<NoteEntity>

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE notes SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL")
    suspend fun allLive(): List<NoteEntity>

    @Query("SELECT * FROM tags")
    suspend fun allTags(): List<TagEntity>

    @Query("SELECT * FROM note_tag_cross_ref")
    suspend fun allCrossRefs(): List<NoteTagCrossRef>

    @Query("SELECT * FROM categories")
    suspend fun allCategories(): List<CategoryEntity>

    // ---------- 标签 ----------

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun tagByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun tagById(id: Long): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN note_tag_cross_ref ON tags.id = note_tag_cross_ref.tagId
        WHERE note_tag_cross_ref.noteId = :noteId
        ORDER BY tags.name
        """
    )
    suspend fun tagsOf(noteId: Long): List<TagEntity>

    @Query(
        """
        SELECT notes.* FROM notes
        INNER JOIN note_tag_cross_ref r ON notes.id = r.noteId
        WHERE r.tagId = :tagId AND notes.deletedAt IS NULL AND notes.isArchived = 0
        ORDER BY notes.updatedAt DESC
        """
    )
    suspend fun notesOfTag(tagId: Long): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTag(crossRef: NoteTagCrossRef)

    @Query("DELETE FROM note_tag_cross_ref WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun unlinkTag(noteId: Long, tagId: Long)

    @Query("DELETE FROM note_tag_cross_ref WHERE noteId = :noteId")
    suspend fun clearNoteTags(noteId: Long)

    @Transaction
    suspend fun replaceNoteTags(noteId: Long, tagNames: List<String>, now: Long) {
        clearNoteTags(noteId)
        for (raw in tagNames) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            val existing = tagByName(name)
            val tagId = existing?.id ?: insertTag(TagEntity(name = name))
            linkTag(NoteTagCrossRef(noteId, tagId))
        }
    }

    // ---------- 分类 ----------

    @Query("SELECT * FROM categories WHERE kind = :kind ORDER BY sortOrder, name")
    fun observeCategories(kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE kind = :kind AND name = :name LIMIT 1")
    suspend fun categoryByName(kind: String, name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)
}
