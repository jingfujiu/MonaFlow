package com.monaflow.app.data.local.entity

import kotlinx.serialization.Serializable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 笔记。deletedAt 非空表示在回收站；软删除用于回收站与恢复。 */
@Serializable
@Entity(
    tableName = "notes",
    indices = [
        Index("updatedAt"),
        Index("isPinned"),
        Index("deletedAt"),
        Index("categoryId"),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    @ColumnInfo(name = "deletedAt") val deletedAt: Long? = null,
    val categoryId: Long? = null,
)

/** 标签。name 唯一。 */
@Serializable
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long? = null,
)

/** 笔记-标签多对多。 */
@Serializable
@Entity(
    tableName = "note_tag_cross_ref",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long,
)

/** 分类：kind 取 note / task / item / countdown，同一 kind 内 name 唯一。 */
@Serializable
@Entity(
    tableName = "categories",
    indices = [Index(value = ["kind", "name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val sortOrder: Int = 0,
)
