package com.monaflow.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.HabitEntity
import com.monaflow.app.data.local.entity.ItemEntity
import com.monaflow.app.data.local.entity.NoteEntity
import com.monaflow.app.data.local.entity.TagEntity
import com.monaflow.app.data.local.entity.TaskEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: MonaFlowDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MonaFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupManager(context, db)
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun seed() {
        val noteId = db.noteDao().insert(NoteEntity(title = "笔记A", content = "内容", createdAt = 1, updatedAt = 2))
        val tagId = db.noteDao().insertTag(TagEntity(name = "工作"))
        db.noteDao().linkTag(com.monaflow.app.data.local.entity.NoteTagCrossRef(noteId, tagId))
        db.habitDao().insert(
            HabitEntity(
                name = "喝水",
                scheduleRule = "FREQ=DAILY;INTERVAL=1",
                startDate = LocalDate.of(2026, 1, 1).toEpochDay(),
                createdAt = 0,
            ),
        )
        db.habitDao().insertCheckIn(
            com.monaflow.app.data.local.entity.HabitCheckInEntity(
                habitId = 1,
                localDate = LocalDate.of(2026, 10, 26).toEpochDay(),
                status = 1,
                createdAt = 0,
            ),
        )
        db.taskDao().insert(TaskEntity(title = "任务1", createdAt = 5, priority = 3))
        db.itemDao().insert(
            ItemEntity(name = "键盘", purchasePriceMinor = 50000, currency = "CNY", createdAt = 0, updatedAt = 0),
        )
        db.countdownDao().insert(
            com.monaflow.app.data.local.entity.CountdownEntity(
                title = "元旦",
                targetEpochMillis = 1798761600000,
                timeZoneId = "Asia/Shanghai",
                isRecurring = true,
                recurrenceRule = "FREQ=YEARLY;INTERVAL=1",
                createdAt = 0,
            ),
        )
    }

    @Test
    fun `export then restore restores identical data`() = runTest {
        seed()
        val bytes = ByteArrayOutputStream().use { out ->
            manager.exportTo(out, "test-1.0").getOrThrow()
            out.toByteArray()
        }
        assertTrue(bytes.size > 100)

        // 破坏现有数据（模拟换机/清空后恢复）
        db.clearAllTables()

        val manifest = manager.restoreFrom(ByteArrayInputStream(bytes), "test-1.0").getOrThrow()
        assertEquals(1, manifest.counts["notes"])
        assertEquals(1, manifest.counts["habits"])
        assertEquals(1, manifest.counts["habitCheckIns"])
        assertEquals(1, manifest.counts["tasks"])
        assertEquals(1, manifest.counts["items"])
        assertEquals(1, manifest.counts["countdowns"])

        val note = db.noteDao().byId(noteId())
        assertNotNull(note)
        assertEquals("笔记A", note!!.title)
        val tags = db.noteDao().tagsOf(note.id)
        assertEquals(listOf("工作"), tags.map { it.name })
        assertEquals(1, db.habitDao().allCheckIns().size)
        assertEquals(1, db.taskDao().all().size)
        assertEquals(50000L, db.itemDao().all()[0].purchasePriceMinor)
        assertEquals(true, db.countdownDao().all()[0].isRecurring)
    }

    private fun noteId(): Long = 1L

    @Test
    fun `export excludes nothing but keeps provider table out`() = runTest {
        seed()
        db.aiProviderDao().insert(
            com.monaflow.app.data.local.entity.AiProviderEntity(
                displayName = "deepseek",
                baseUrl = "https://api.deepseek.com",
                modelName = "deepseek-chat",
                encryptedCredentialRef = "enc-secret",
                createdAt = 0,
                updatedAt = 0,
            ),
        )
        val bytes = ByteArrayOutputStream().use { out ->
            manager.exportTo(out, "test-1.0").getOrThrow()
            out.toByteArray()
        }
        val content = String(bytes, Charsets.ISO_8859_1)
        // API Key 密文绝不出现在备份中
        assertTrue("备份不得包含加密凭证", !content.contains("enc-secret"))
        assertTrue(!content.contains("deepseek"))
    }

    @Test
    fun `corrupted checksum rejected without touching data`() = runTest {
        seed()
        val bytes = ByteArrayOutputStream().use { out ->
            manager.exportTo(out, "test-1.0").getOrThrow()
            out.toByteArray()
        }
        // 篡改 notes.json 内容 → 校验和不匹配
        val tampered = tamperZipEntry(bytes, "data/notes.json", byteArrayOf(0x7b, 0x7d))
        val before = db.noteDao().allLive().size
        val result = manager.restoreFrom(ByteArrayInputStream(tampered), "test-1.0")
        assertTrue("损坏备份必须被拒绝", result.isFailure)
        assertEquals("现有数据保持不变", before, db.noteDao().allLive().size)
    }

    @Test
    fun `path traversal entry rejected`() = runTest {
        seed()
        val evil = ByteArrayOutputStream()
        ZipOutputStream(evil).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                """{"formatVersion":1,"dbVersion":1,"createdAt":0,"appVersion":"x","counts":{},"checksum":"0"}""".toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("attachments/..\\..\\evil.txt"))
            zip.write("boom".toByteArray())
            zip.closeEntry()
        }
        val result = manager.restoreFrom(ByteArrayInputStream(evil.toByteArray()), "test-1.0")
        assertTrue("路径穿越条目必须被拒绝", result.isFailure)
    }

    @Test
    fun `not a monaflow backup rejected`() = runTest {
        val notBackup = ByteArrayOutputStream()
        ZipOutputStream(notBackup).use { zip ->
            zip.putNextEntry(ZipEntry("hello.txt"))
            zip.write("hi".toByteArray())
            zip.closeEntry()
        }
        val result = manager.restoreFrom(ByteArrayInputStream(notBackup.toByteArray()), "test-1.0")
        assertTrue(result.isFailure)
    }

    @Test
    fun `inspect returns manifest without writing`() = runTest {
        seed()
        val bytes = ByteArrayOutputStream().use { out ->
            manager.exportTo(out, "test-1.0").getOrThrow()
            out.toByteArray()
        }
        val manifest = manager.inspect(ByteArrayInputStream(bytes))
        assertEquals(BackupManager.FORMAT_VERSION, manifest.formatVersion)
        // notes/tags/crossRefs/habits/checkIns/tasks/items/countdowns 各 1 条 = 8
        assertEquals(8, manifest.counts.values.sum())
    }

    @Test
    fun `attachments roundtrip through internal files`() = runTest {
        val attachmentDir = File(context.filesDir, "attachments").apply { mkdirs() }
        val img = File(attachmentDir, "img1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        db.noteDao().insert(NoteEntity(title = "带图", content = "", createdAt = 0, updatedAt = 0))
        db.attachmentDao().insert(
            com.monaflow.app.data.local.entity.AttachmentEntity(
                ownerType = "note",
                ownerId = 1,
                uri = img.absolutePath,
                mimeType = "image/jpeg",
                fileName = "img1.jpg",
                createdAt = 0,
            ),
        )
        val bytes = ByteArrayOutputStream().use { out ->
            manager.exportTo(out, "test-1.0").getOrThrow()
            out.toByteArray()
        }
        // 删除本地附件模拟丢失
        img.delete()
        db.clearAllTables()

        manager.restoreFrom(ByteArrayInputStream(bytes), "test-1.0").getOrThrow()
        val restored = db.attachmentDao().all()[0]
        val restoredFile = File(restored.uri)
        assertTrue("附件文件应被恢复", restoredFile.exists())
        assertTrue(restoredFile.readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
    }

    /** 在 ZIP 字节流中替换某条目的内容（保持其余不变），用于构造损坏备份。 */
    private fun tamperZipEntry(original: ByteArray, entryName: String, newContent: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val zin = java.util.zip.ZipInputStream(ByteArrayInputStream(original))
            var entry = zin.nextEntry
            while (entry != null) {
                zip.putNextEntry(ZipEntry(entry.name))
                if (entry.name == entryName) {
                    zip.write(newContent)
                } else {
                    zin.copyTo(zip)
                }
                zip.closeEntry()
                entry = zin.nextEntry
            }
        }
        return out.toByteArray()
    }
}
