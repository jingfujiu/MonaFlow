package com.monaflow.app.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 附件文件管理：把外部 content:// 复制进应用私有目录，
 * 避免源 URI 失效；删除记录时同步删文件。
 */
class AttachmentStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    /** 返回内部文件绝对路径；失败返回 null（调用方提示，不静默）。 */
    fun importFrom(src: Uri, mimeType: String): String? {
        return runCatching {
            val ext = when {
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                mimeType.contains("gif") -> "gif"
                else -> "jpg"
            }
            val target = File(dir, "${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(src)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        }.getOrNull()
    }

    fun delete(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    fun allAttachmentFiles(): List<File> = dir.listFiles()?.toList() ?: emptyList()

    fun totalBytes(): Long = allAttachmentFiles().sumOf { it.length() }
}
