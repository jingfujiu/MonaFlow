package com.monaflow.app

import android.app.Application
import com.monaflow.app.core.notifications.NotificationHelper
import com.monaflow.app.core.notifications.ReminderScheduler
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.repository.CountdownRepository
import com.monaflow.app.data.repository.HabitRepository
import com.monaflow.app.data.repository.ItemRepository
import com.monaflow.app.data.repository.NoteRepository
import com.monaflow.app.data.repository.TaskRepository
import com.monaflow.app.data.settings.SettingsRepository

class MonaFlowApp : Application() {

    val database: MonaFlowDatabase by lazy { MonaFlowDatabase.get(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val noteRepository: NoteRepository by lazy { NoteRepository(database) }
    val taskRepository: TaskRepository by lazy { TaskRepository(database) }
    val habitRepository: HabitRepository by lazy { HabitRepository(database) }
    val itemRepository: ItemRepository by lazy { ItemRepository(database) }
    val countdownRepository: CountdownRepository by lazy { CountdownRepository(database) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        ReminderScheduler.init(this)
        // 冷启动重建提醒（覆盖进程被杀场景）
        ReminderScheduler.rescheduleAll()
    }
}
