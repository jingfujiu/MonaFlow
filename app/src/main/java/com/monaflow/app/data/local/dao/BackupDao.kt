package com.monaflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.monaflow.app.data.local.entity.AttachmentEntity
import com.monaflow.app.data.local.entity.CategoryEntity
import com.monaflow.app.data.local.entity.CountdownEntity
import com.monaflow.app.data.local.entity.HabitCheckInEntity
import com.monaflow.app.data.local.entity.HabitEntity
import com.monaflow.app.data.local.entity.ItemEntity
import com.monaflow.app.data.local.entity.NoteEntity
import com.monaflow.app.data.local.entity.NoteTagCrossRef
import com.monaflow.app.data.local.entity.TagEntity
import com.monaflow.app.data.local.entity.TaskEntity

/** 仅备份/恢复使用：整表清空 + 批量插入，全部在外层 Room 事务中执行。 */
@Dao
interface BackupDao {

    // ---------- 清空（顺序：子表在前） ----------
    @Query("DELETE FROM note_tag_cross_ref")
    suspend fun clearNoteTagCrossRefs()

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM habit_checkins")
    suspend fun clearHabitCheckIns()

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Query("DELETE FROM tasks WHERE parentTaskId IS NOT NULL")
    suspend fun clearTaskChildren()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM countdowns")
    suspend fun clearCountdowns()

    @Query("DELETE FROM attachments")
    suspend fun clearAttachments()

    // ---------- 插入 ----------
    @Insert suspend fun insertNotes(notes: List<NoteEntity>)
    @Insert suspend fun insertTags(tags: List<TagEntity>)
    @Insert suspend fun insertNoteTagCrossRefs(refs: List<NoteTagCrossRef>)
    @Insert suspend fun insertCategories(categories: List<CategoryEntity>)
    @Insert suspend fun insertHabits(habits: List<HabitEntity>)
    @Insert suspend fun insertHabitCheckIns(checkIns: List<HabitCheckInEntity>)
    @Insert suspend fun insertItems(items: List<ItemEntity>)
    @Insert suspend fun insertTasks(tasks: List<TaskEntity>)
    @Insert suspend fun insertCountdowns(countdowns: List<CountdownEntity>)
    @Insert suspend fun insertAttachments(attachments: List<AttachmentEntity>)
}
