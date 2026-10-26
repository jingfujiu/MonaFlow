package com.monaflow.app.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.monaflow.app.data.local.AttachmentStore
import com.monaflow.app.data.local.MonaFlowDatabase
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 本地 ZIP 备份/恢复。
 * 结构：
 *   manifest.json   格式版本、数据库版本、各表条数、数据校验和
 *   data 目录下各 json  结构化数据（凭证表不导出，API Key 永不出备份）
 *   attachments 目录  笔记/物品图片等内部附件
 * 恢复：先整体解析与校验（格式、条数、校验和、条目名、外键引用），全部通过后单事务覆盖写入；
 *      任一步失败不触碰数据库（不留半恢复状态）。
 */
class BackupManager(
    private val context: Context,
    private val db: MonaFlowDatabase,
) {

    @Serializable
    data class BackupManifest(
        val formatVersion: Int = FORMAT_VERSION,
        val dbVersion: Int,
        val createdAt: Long,
        val appVersion: String,
        val counts: Map<String, Int>,
        val checksum: String,
    )

    @Serializable
    data class BackupData(
        val notes: List<NoteEntity> = emptyList(),
        val tags: List<TagEntity> = emptyList(),
        val noteTagCrossRefs: List<NoteTagCrossRef> = emptyList(),
        val categories: List<CategoryEntity> = emptyList(),
        val habits: List<HabitEntity> = emptyList(),
        val habitCheckIns: List<HabitCheckInEntity> = emptyList(),
        val items: List<ItemEntity> = emptyList(),
        val tasks: List<TaskEntity> = emptyList(),
        val countdowns: List<CountdownEntity> = emptyList(),
        val attachments: List<AttachmentEntity> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---------- 导出 ----------

    suspend fun exportTo(output: OutputStream, appVersion: String): Result<BackupManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                val data = collectData()
                val dataEntries = encodeData(data)
                val manifest = BackupManifest(
                    dbVersion = db.openHelper.readableDatabase.version,
                    createdAt = System.currentTimeMillis(),
                    appVersion = appVersion,
                    counts = countMap(data),
                    checksum = checksumOf(dataEntries),
                )
                ZipOutputStream(output.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                    zip.closeEntry()

                    for ((name, bytes) in dataEntries) {
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(bytes)
                        zip.closeEntry()
                    }

                    val fileNames = data.attachments.map { File(it.uri).name }.toSet()
                    for (fileName in fileNames) {
                        val f = File(attachmentDir(), fileName)
                        if (f.exists() && f.isFile) {
                            zip.putNextEntry(ZipEntry("attachments/$fileName"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                manifest
            }
        }

    private suspend fun collectData(): BackupData = BackupData(
        notes = db.noteDao().allLive() + db.noteDao().trashAll(),
        tags = db.noteDao().allTags(),
        noteTagCrossRefs = db.noteDao().allCrossRefs(),
        categories = db.noteDao().allCategories(),
        habits = db.habitDao().all(),
        habitCheckIns = db.habitDao().allCheckIns(),
        items = db.itemDao().all(),
        tasks = db.taskDao().all(),
        countdowns = db.countdownDao().all(),
        attachments = db.attachmentDao().all(),
    )

    private fun encodeData(data: BackupData): List<Pair<String, ByteArray>> = listOf(
        "data/notes.json" to json.encodeToString(ListSerializer(NoteEntity.serializer()), data.notes).toByteArray(),
        "data/tags.json" to json.encodeToString(ListSerializer(TagEntity.serializer()), data.tags).toByteArray(),
        "data/note_tag_refs.json" to json.encodeToString(ListSerializer(NoteTagCrossRef.serializer()), data.noteTagCrossRefs).toByteArray(),
        "data/categories.json" to json.encodeToString(ListSerializer(CategoryEntity.serializer()), data.categories).toByteArray(),
        "data/habits.json" to json.encodeToString(ListSerializer(HabitEntity.serializer()), data.habits).toByteArray(),
        "data/habit_checkins.json" to json.encodeToString(ListSerializer(HabitCheckInEntity.serializer()), data.habitCheckIns).toByteArray(),
        "data/items.json" to json.encodeToString(ListSerializer(ItemEntity.serializer()), data.items).toByteArray(),
        "data/tasks.json" to json.encodeToString(ListSerializer(TaskEntity.serializer()), data.tasks).toByteArray(),
        "data/countdowns.json" to json.encodeToString(ListSerializer(CountdownEntity.serializer()), data.countdowns).toByteArray(),
        "data/attachments.json" to json.encodeToString(ListSerializer(AttachmentEntity.serializer()), data.attachments).toByteArray(),
    )

    private fun checksumOf(entries: List<Pair<String, ByteArray>>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((name, bytes) in entries.sortedBy { it.first }) {
            digest.update(name.toByteArray())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun countMap(data: BackupData): Map<String, Int> = mapOf(
        "notes" to data.notes.size,
        "tags" to data.tags.size,
        "noteTagCrossRefs" to data.noteTagCrossRefs.size,
        "categories" to data.categories.size,
        "habits" to data.habits.size,
        "habitCheckIns" to data.habitCheckIns.size,
        "items" to data.items.size,
        "tasks" to data.tasks.size,
        "countdowns" to data.countdowns.size,
        "attachments" to data.attachments.size,
    )

    // ---------- 导入 ----------

    /** 预览：解析并校验，返回 manifest 供恢复前展示；不写入任何数据。 */
    suspend fun inspect(input: InputStream): BackupManifest = withContext(Dispatchers.IO) {
        val (manifest, data, _) = readZip(input)
        validate(manifest, data)
        manifest
    }

    /** 覆盖恢复：现有数据全部替换。内部先完整校验再单事务写入。 */
    suspend fun restoreFrom(input: InputStream, appVersion: String): Result<BackupManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (manifest, data, attachmentBytes) = readZip(input)
                validate(manifest, data)

                // 先落附件文件；失败则清理后抛出，不触碰数据库
                val restoredFiles = mutableListOf<File>()
                try {
                    for ((name, bytes) in attachmentBytes) {
                        val safeName = name.removePrefix("attachments/")
                        require(safeName.isNotBlank() && !safeName.contains('/') && !safeName.contains('\\')) {
                            "非法附件名: $name"
                        }
                        val target = File(attachmentDir(), safeName)
                        target.writeBytes(bytes)
                        restoredFiles.add(target)
                    }
                } catch (e: Exception) {
                    restoredFiles.forEach { runCatching { it.delete() } }
                    throw e
                }

                // 单事务覆盖写入（子表在前清空，规避自引用外键约束）
                db.withTransaction {
                    val dao = db.backupDao()
                    dao.clearNoteTagCrossRefs()
                    dao.clearTags()
                    dao.clearNotes()
                    dao.clearCategories()
                    dao.clearHabitCheckIns()
                    dao.clearHabits()
                    dao.clearTaskChildren()
                    dao.clearTasks()
                    dao.clearItems()
                    dao.clearCountdowns()
                    dao.clearAttachments()

                    dao.insertNotes(data.notes)
                    dao.insertTags(data.tags)
                    dao.insertNoteTagCrossRefs(data.noteTagCrossRefs)
                    dao.insertCategories(data.categories)
                    dao.insertHabits(data.habits)
                    dao.insertHabitCheckIns(data.habitCheckIns)
                    dao.insertItems(data.items)
                    dao.insertTasks(data.tasks)
                    dao.insertCountdowns(data.countdowns)
                    dao.insertAttachments(data.attachments)
                }
                manifest
            }
        }

    // ---------- ZIP 读取与校验 ----------

    private data class ZipContents(
        val manifest: BackupManifest,
        val data: BackupData,
        val attachmentBytes: List<Pair<String, ByteArray>>,
    )

    private fun readZip(input: InputStream): ZipContents {
        var manifestBytes: ByteArray? = null
        val dataRaw = mutableMapOf<String, ByteArray>()
        val attachmentBytes = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                requireValidEntryName(name)
                val bytes = zip.readBytes()
                when {
                    name == "manifest.json" -> manifestBytes = bytes
                    name.startsWith("data/") -> dataRaw[name] = bytes
                    name.startsWith("attachments/") -> attachmentBytes.add(name to bytes)
                }
                entry = zip.nextEntry
            }
        }
        val manifest = runCatching {
            json.decodeFromString(
                BackupManifest.serializer(),
                String(manifestBytes ?: throw IllegalArgumentException("缺少 manifest.json，不是 MonaFlow 备份文件")),
            )
        }.getOrElse {
            if (it is IllegalArgumentException) throw it
            throw IllegalArgumentException("manifest.json 解析失败：${it.message}")
        }

        require(manifest.formatVersion == FORMAT_VERSION) {
            "备份格式版本不支持（${manifest.formatVersion}），当前支持 $FORMAT_VERSION"
        }

        val data = BackupData(
            notes = decodeList(dataRaw, "data/notes.json", NoteEntity.serializer()),
            tags = decodeList(dataRaw, "data/tags.json", TagEntity.serializer()),
            noteTagCrossRefs = decodeList(dataRaw, "data/note_tag_refs.json", NoteTagCrossRef.serializer()),
            categories = decodeList(dataRaw, "data/categories.json", CategoryEntity.serializer()),
            habits = decodeList(dataRaw, "data/habits.json", HabitEntity.serializer()),
            habitCheckIns = decodeList(dataRaw, "data/habit_checkins.json", HabitCheckInEntity.serializer()),
            items = decodeList(dataRaw, "data/items.json", ItemEntity.serializer()),
            tasks = decodeList(dataRaw, "data/tasks.json", TaskEntity.serializer()),
            countdowns = decodeList(dataRaw, "data/countdowns.json", CountdownEntity.serializer()),
            attachments = decodeList(dataRaw, "data/attachments.json", AttachmentEntity.serializer()),
        )
        return ZipContents(manifest, data, attachmentBytes)
    }

    private fun <T> decodeList(
        map: Map<String, ByteArray>,
        key: String,
        serializer: KSerializer<T>,
    ): List<T> {
        val bytes = map[key] ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(serializer), String(bytes))
        }.getOrElse { throw IllegalArgumentException("$key 解析失败：${it.message}") }
    }

    private fun validate(manifest: BackupManifest, data: BackupData) {
        val actual = countMap(data)
        for ((k, v) in manifest.counts) {
            val got = actual[k] ?: 0
            require(got == v) { "条目校验失败：$k 期望 $v 实际 $got" }
        }
        val recomputed = checksumOf(encodeData(data))
        require(recomputed == manifest.checksum) { "数据校验和不匹配，备份可能已损坏" }
        val noteIds = data.notes.map { it.id }.toSet()
        val tagIds = data.tags.map { it.id }.toSet()
        require(data.noteTagCrossRefs.all { it.noteId in noteIds && it.tagId in tagIds }) {
            "笔记-标签关联指向不存在的记录"
        }
        val taskIds = data.tasks.map { it.id }.toSet()
        require(data.tasks.all { it.parentTaskId == null || it.parentTaskId in taskIds }) {
            "子任务指向不存在的父任务"
        }
        val habitIds = data.habits.map { it.id }.toSet()
        require(data.habitCheckIns.all { it.habitId in habitIds }) {
            "打卡记录指向不存在的习惯"
        }
    }

    private fun requireValidEntryName(name: String) {
        require(!name.startsWith("/") && !name.contains("..") && !name.contains(':')) {
            "非法 ZIP 条目名（疑似路径穿越）: $name"
        }
        require(
            name == "manifest.json" || name.startsWith("data/") || name.startsWith("attachments/"),
        ) { "未知条目: $name" }
    }

    private fun attachmentDir(): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    companion object {
        const val FORMAT_VERSION = 1
    }
}
