package com.monaflow.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monaflow.app.MonaFlowApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 闹钟触发 → 发通知；习惯提醒在触发后自动顺延到明天。 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val route = intent.getStringExtra(EXTRA_ROUTE) ?: ""
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)

        NotificationHelper.showReminder(context, requestCode, title, text, route)

        // 习惯提醒（请求码 30000-49999）：今天触发过 → 顺延到明天同时刻
        if (requestCode >= ReminderScheduler.habitReminderCode(0) &&
            requestCode < ReminderScheduler.habitReminderCode(0) + 20_000
        ) {
            val habitId = (requestCode - ReminderScheduler.habitReminderCode(0)).toLong()
            val app = context.applicationContext as MonaFlowApp
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val habit = app.database.habitDao().byId(habitId)
                    if (habit != null && habit.reminderMinutes != null && !habit.isPaused && !habit.isArchived) {
                        ReminderScheduler.scheduleHabit(habitId, habit.name, habit.reminderMinutes!!)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_REQUEST_CODE = "requestCode"
    }
}
