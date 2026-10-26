package com.monaflow.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.monaflow.app.MonaFlowApp
import com.monaflow.app.data.local.entity.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 提醒调度：精确闹钟（AlarmManager）优先，权限缺失时退化为非精确；
 * WorkManager 每日巡检兜底（保修/倒计时）。
 * WorkManager 不保证分钟级精度 —— 分钟级提醒一律走 AlarmManager。
 */
object ReminderScheduler {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun alarmManager(): AlarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager().canScheduleExactAlarms()

    private fun pendingIntent(requestCode: Int, title: String, text: String, route: String): PendingIntent {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_TEXT, text)
            putExtra(AlarmReceiver.EXTRA_ROUTE, route)
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ---------- 任务提醒 ----------

    fun scheduleTask(task: TaskEntity) {
        if (!::appContext.isInitialized) return
        val at = task.reminderAt ?: return
        if (at <= System.currentTimeMillis()) return
        val requestCode = taskReminderCode(task.id)
        val pi = pendingIntent(
            requestCode,
            "待办提醒",
            task.title,
            "task_edit?taskId=${task.id}",
        )
        runCatching {
            if (canExact()) {
                alarmManager().setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                alarmManager().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun cancelTask(taskId: Long) {
        if (!::appContext.isInitialized) return
        runCatching {
            alarmManager().cancel(
                pendingIntent(taskReminderCode(taskId), "", "", ""),
            )
        }
    }

    // ---------- 习惯提醒（每天固定时刻，触发后顺延一天） ----------

    fun scheduleHabit(habitId: Long, name: String, minutesOfDay: Int) {
        if (!::appContext.isInitialized) return
        val next = ReminderTime.nextOccurrence(minutesOfDay, LocalDateTime.now())
        val millis = next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val requestCode = habitReminderCode(habitId)
        val pi = pendingIntent(
            requestCode,
            "习惯打卡",
            name,
            "habit_detail/$habitId",
        )
        runCatching {
            if (canExact()) {
                alarmManager().setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                alarmManager().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        }
    }

    fun cancelHabit(habitId: Long) {
        if (!::appContext.isInitialized) return
        runCatching {
            alarmManager().cancel(pendingIntent(habitReminderCode(habitId), "", "", ""))
        }
    }

    // ---------- 全量重建（开机/时间变化/设置变更后调用） ----------

    fun rescheduleAll(context: Context? = null) {
        if (!::appContext.isInitialized && context == null) return
        val ctx = (context ?: appContext).applicationContext
        scope.launch {
            runCatching {
                val app = ctx as MonaFlowApp
                val settings = app.settingsRepository.settings.first()
                val db = app.database

                // 任务
                if (settings.reminderTodo) {
                    db.taskDao().all()
                        .filter { it.status == 0 && it.reminderAt != null && it.reminderAt > System.currentTimeMillis() }
                        .forEach { scheduleTask(it) }
                }
                // 习惯
                val habits = db.habitDao().all()
                habits.forEach {
                    if (!settings.reminderHabit || it.isPaused || it.isArchived) {
                        cancelHabit(it.id)
                    } else if (it.reminderMinutes != null) {
                        scheduleHabit(it.id, it.name, it.reminderMinutes!!)
                    }
                }
                // 保修/倒计时每日巡检
                WarrantyCheckWorker.schedule(app, settings.reminderWarranty || settings.reminderCountdown)
            }
        }
    }

    fun taskReminderCode(taskId: Long): Int = 10_000 + (taskId and 0xFFFF).toInt()
    fun habitReminderCode(habitId: Long): Int = 30_000 + (habitId and 0xFFFF).toInt()
}
