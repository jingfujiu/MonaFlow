package com.monaflow.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机/时间/时区变化后重建提醒（阶段3接入具体调度；
 * 现在先保证清单合法，收到广播即转交 ReminderScheduler 重建）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> ReminderScheduler.rescheduleAll(context)
        }
    }
}
