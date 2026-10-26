package com.monaflow.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.HabitCheckInEntity
import com.monaflow.app.data.local.entity.HabitEntity
import com.monaflow.app.data.local.entity.ItemEntity
import com.monaflow.app.data.local.entity.NoteEntity
import com.monaflow.app.data.local.entity.TaskEntity
import com.monaflow.app.data.repository.HabitRepository
import com.monaflow.app.data.repository.NoteRepository
import com.monaflow.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13 最高支持 API 34（targetSdk=35 的真机/模拟器行为另行实测）
class DatabaseTest {

    private lateinit var db: MonaFlowDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MonaFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    // ---------- 笔记 ----------

    @Test
    fun `note crud survives reopen`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_reopen.db"
        context.deleteDatabase(dbName)
        val savedId: Long
        val db1 = Room.databaseBuilder(context, MonaFlowDatabase::class.java, dbName)
            .allowMainThreadQueries().build()
        savedId = db1.noteDao().insert(
            NoteEntity(title = "测试笔记", content = "正文内容", createdAt = 1, updatedAt = 1),
        )
        db1.close()

        // 模拟进程重启后重新打开
        val db2 = Room.databaseBuilder(context, MonaFlowDatabase::class.java, dbName)
            .allowMainThreadQueries().build()
        val note = db2.noteDao().byId(savedId)
        assertNotNull(note)
        assertEquals("测试笔记", note!!.title)
        assertEquals("正文内容", note.content)
        db2.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `note search escapes like wildcards`() = runTest {
        val repo = NoteRepository(db)
        repo.upsert(NoteEntity(title = "100%折扣", content = "a_b", createdAt = 0, updatedAt = 0))
        repo.upsert(NoteEntity(title = "普通笔记", content = "内容", createdAt = 0, updatedAt = 0))
        // % 与 _ 应被字面匹配，而不是通配
        assertEquals(1, repo.searchActive("%").first().size)
        assertEquals(1, repo.searchActive("_").first().size)
        assertEquals(0, repo.searchActive("不存在xyz").first().size)
    }

    @Test
    fun `note trash restore and hard delete clears attachments`() = runTest {
        val repo = NoteRepository(db)
        val id = repo.upsert(NoteEntity(title = "n", content = "", createdAt = 0, updatedAt = 0))
        db.attachmentDao().insert(
            com.monaflow.app.data.local.entity.AttachmentEntity(
                ownerType = "note", ownerId = id, uri = "/x/a.jpg", createdAt = 0,
            ),
        )
        repo.moveToTrash(id)
        assertEquals(1, repo.observeTrash().first().size)
        repo.restore(id)
        assertEquals(0, repo.observeTrash().first().size)
        repo.moveToTrash(id)
        repo.hardDelete(id)
        assertNull(db.noteDao().byId(id))
        assertEquals(0, db.attachmentDao().byOwner("note", id).size)
    }

    @Test
    fun `note tags replace correctly`() = runTest {
        val repo = NoteRepository(db)
        val id = repo.upsert(NoteEntity(title = "n", content = "", createdAt = 0, updatedAt = 0), listOf("工作", "灵感"))
        assertEquals(setOf("工作", "灵感"), repo.tagsOf(id).map { it.name }.toSet())
        repo.replaceTags(id, listOf("工作", "生活", "工作")) // 重复名去重
        assertEquals(setOf("工作", "生活"), repo.tagsOf(id).map { it.name }.toSet())
        // 标签全局唯一：同名标签只存一条
        assertEquals(3, db.noteDao().allTags().size)
    }

    // ---------- 习惯打卡 ----------

    @Test
    fun `same habit same local date counts once`() = runTest {
        val repo = HabitRepository(db)
        val habitId = repo.upsert(
            HabitEntity(name = "读书", scheduleRule = "FREQ=DAILY;INTERVAL=1", startDate = LocalDate.of(2026, 1, 1).toEpochDay(), createdAt = 0),
        )
        val date = LocalDate.of(2026, 10, 26)
        assertTrue(repo.checkIn(habitId, date))
        assertFalse("同一天重复打卡必须被唯一索引拒绝", repo.checkIn(habitId, date))
        assertEquals(1, repo.totalCheckIns(habitId))
        // 取消后可重新打卡
        repo.removeCheckIn(habitId, date)
        assertEquals(0, repo.totalCheckIns(habitId))
        assertTrue(repo.checkIn(habitId, date))
    }

    @Test
    fun `streak crosses month and year boundaries`() = runTest {
        val repo = HabitRepository(db)
        val habitId = repo.upsert(
            HabitEntity(name = "跑步", scheduleRule = "FREQ=DAILY;INTERVAL=1", startDate = LocalDate.of(2025, 12, 20).toEpochDay(), createdAt = 0),
        )
        // 2025-12-29 .. 2026-01-05 连续 8 天（跨月跨年）
        var d = LocalDate.of(2025, 12, 29)
        repeat(8) {
            repo.checkIn(habitId, d)
            d = d.plusDays(1)
        }
        val habit = repo.byId(habitId)!!
        val checkIns = repo.checkInsOf(habitId)
        assertEquals(8, repo.streak(habit, checkIns, today = LocalDate.of(2026, 1, 5)))
        // 01-06 之后中断：从 01-10 回看连续数归零（不得虚报）
        assertEquals(0, repo.streak(habit, checkIns, today = LocalDate.of(2026, 1, 10)))
    }

    @Test
    fun `streak ignores today not yet done`() = runTest {
        val repo = HabitRepository(db)
        val habitId = repo.upsert(
            HabitEntity(name = "冥想", scheduleRule = "FREQ=DAILY;INTERVAL=1", startDate = LocalDate.of(2026, 10, 1).toEpochDay(), createdAt = 0),
        )
        val today = LocalDate.of(2026, 10, 26)
        // 10-20 .. 10-25 完成，今天未打卡 → 连续 6 天不被今天打断
        var d = LocalDate.of(2026, 10, 20)
        repeat(6) {
            repo.checkIn(habitId, d)
            d = d.plusDays(1)
        }
        val habit = repo.byId(habitId)!!
        assertEquals(6, repo.streak(habit, repo.checkInsOf(habitId), today = today))
    }

    @Test
    fun `weekly habit month status`() = runTest {
        val repo = HabitRepository(db)
        // 每周一三五
        val habitId = repo.upsert(
            HabitEntity(
                name = "健身",
                scheduleRule = "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE,FR",
                startDate = LocalDate.of(2026, 10, 1).toEpochDay(),
                createdAt = 0,
            ),
        )
        repo.checkIn(habitId, LocalDate.of(2026, 10, 5)) // 周一
        repo.checkIn(habitId, LocalDate.of(2026, 10, 7), status = HabitRepository.STATUS_SKIPPED) // 周三跳过
        val habit = repo.byId(habitId)!!
        val month = repo.monthStatus(habit, repo.checkInsOf(habitId), java.time.YearMonth.of(2026, 10))
        val byDate = month.toMap()
        assertEquals(com.monaflow.app.data.repository.HabitDayState.DONE, byDate[LocalDate.of(2026, 10, 5)])
        assertEquals(com.monaflow.app.data.repository.HabitDayState.SKIPPED, byDate[LocalDate.of(2026, 10, 7)])
        assertEquals(com.monaflow.app.data.repository.HabitDayState.MISSED, byDate[LocalDate.of(2026, 10, 9)]) // 周五漏卡
        assertEquals(com.monaflow.app.data.repository.HabitDayState.IDLE, byDate[LocalDate.of(2026, 10, 6)]) // 周二非执行日
    }

    // ---------- 任务 ----------

    @Test
    fun `task complete uncomplete and double complete guard`() = runTest {
        val repo = TaskRepository(db)
        val id = repo.upsert(TaskEntity(title = "t", createdAt = 0, dueAt = 1000))
        repo.complete(id)
        assertEquals(1, db.taskDao().byId(id)!!.status)
        val completedAt = db.taskDao().byId(id)!!.completedAt
        assertNotNull(completedAt)
        // 快速重复点击不再改变状态/时间戳
        repo.complete(id)
        assertEquals(completedAt, db.taskDao().byId(id)!!.completedAt)
        repo.uncomplete(id)
        assertEquals(0, db.taskDao().byId(id)!!.status)
        assertNull(db.taskDao().byId(id)!!.completedAt)
    }

    @Test
    fun `recurring task completion creates exactly one next instance`() = runTest {
        val repo = TaskRepository(db)
        // 每天重复，截止 2026-10-26 09:00（系统时区）
        val due = LocalDate.of(2026, 10, 26).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val id = repo.upsert(
            TaskEntity(title = "吃药", createdAt = 0, dueAt = due, recurrenceRule = "FREQ=DAILY;INTERVAL=1"),
        )
        repo.complete(id)
        val all = db.taskDao().all()
        assertEquals("完成后应恰好生成一个下一次实例", 2, all.size)
        val next = all.first { it.id != id }
        assertEquals(0, next.status)
        assertEquals(
            LocalDate.of(2026, 10, 27).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            next.dueAt,
        )
        // 对下一次实例再完成 → 又生成一个，无重复
        repo.complete(next.id)
        assertEquals(3, db.taskDao().all().size)
    }

    @Test
    fun `weekly recurring task next instance`() = runTest {
        val repo = TaskRepository(db)
        // 每周一，2026-10-26 是周一
        val due = LocalDate.of(2026, 10, 26).atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val id = repo.upsert(
            TaskEntity(title = "周报", createdAt = 0, dueAt = due, recurrenceRule = "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO"),
        )
        repo.complete(id)
        val next = db.taskDao().all().first { it.id != id }
        assertEquals(
            LocalDate.of(2026, 11, 2).atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            next.dueAt,
        )
    }

    @Test
    fun `deleting parent task cascades subtasks`() = runTest {
        val repo = TaskRepository(db)
        val parentId = repo.upsert(TaskEntity(title = "parent", createdAt = 0))
        repo.addSubTask(parentId, "step1")
        repo.addSubTask(parentId, "step2")
        assertEquals(2, repo.subTasksOf(parentId).size)
        // 子任务完成进度
        val sub = repo.subTasksOf(parentId)[0]
        repo.toggleSubTask(sub)
        assertEquals(1, repo.subTasksOf(parentId).count { it.status == 1 })
        repo.delete(parentId)
        assertEquals(0, db.taskDao().all().size) // 级联删除
    }

    @Test
    fun `overdue detection`() {
        val repo = TaskRepository(db)
        val now = System.currentTimeMillis()
        val overdue = TaskEntity(id = 1, title = "x", createdAt = 0, dueAt = now - 10_000)
        val future = TaskEntity(id = 2, title = "x", createdAt = 0, dueAt = now + 10_000)
        val done = TaskEntity(id = 3, title = "x", status = 1, createdAt = 0, dueAt = now - 10_000, completedAt = now)
        val noDue = TaskEntity(id = 4, title = "x", createdAt = 0)
        assertTrue(repo.isOverdue(overdue, now))
        assertFalse(repo.isOverdue(future, now))
        assertFalse(repo.isOverdue(done, now))
        assertFalse(repo.isOverdue(noDue, now))
    }

    // ---------- 物品 ----------

    @Test
    fun `item saves price warranty and quantity`() = runTest {
        val repo = com.monaflow.app.data.repository.ItemRepository(db)
        val id = repo.upsert(
            ItemEntity(
                name = "降噪耳机",
                category = "数码",
                quantity = 2,
                purchasePriceMinor = 199900,
                currency = "CNY",
                purchaseDate = LocalDate.of(2025, 11, 11).toEpochDay(),
                warrantyEndDate = LocalDate.of(2026, 11, 11).toEpochDay(),
                storageLocation = "书桌抽屉",
                createdAt = 0,
                updatedAt = 0,
            ),
        )
        val item = repo.byId(id)!!
        assertEquals(199900L, item.purchasePriceMinor)
        assertEquals("CNY", item.currency)
        assertEquals(2, item.quantity)
        assertEquals(LocalDate.of(2025, 11, 11), LocalDate.ofEpochDay(item.purchaseDate!!))
        // 未填写的字段保持 null，不自动生成
        val blankId = repo.upsert(ItemEntity(name = "空物品", createdAt = 0, updatedAt = 0))
        val blank = repo.byId(blankId)!!
        assertNull(blank.purchasePriceMinor)
        assertNull(blank.warrantyEndDate)
        // 保修状态
        val ws = repo.warrantyState(item, today = LocalDate.of(2026, 10, 26))
        assertTrue(ws is com.monaflow.app.data.repository.WarrantyState.EXPIRING_SOON)
    }

    // ---------- 索引与唯一约束 ----------

    @Test
    fun `duplicate tag names are unique in table`() = runTest {
        val id1 = db.noteDao().insertTag(com.monaflow.app.data.local.entity.TagEntity(name = "工作"))
        val id2 = db.noteDao().insertTag(com.monaflow.app.data.local.entity.TagEntity(name = "工作"))
        assertTrue(id1 != -1L)
        assertEquals("唯一索引拒绝重复标签", -1L, id2)
    }
}
