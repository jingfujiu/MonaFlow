package com.monaflow.app.data.local.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 附件。ownerType: note / item / countdown；uri 优先为应用内部文件路径，
 * 外部 content:// 仅在首次导入时复制进应用私有目录后引用。
 */
@Serializable
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["ownerType", "ownerId"])],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: String,
    val ownerId: Long,
    val uri: String,
    val mimeType: String = "",
    val fileName: String = "",
    val createdAt: Long,
)
