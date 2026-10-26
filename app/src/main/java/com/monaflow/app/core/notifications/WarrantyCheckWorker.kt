package com.monaflow.app.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.monaflow.app.MonaFlowApp
import com.monaflow.app.core.common.MonaDate
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 每日巡检：物品保修临近到期 + 今日倒计时（兜底提醒，不追求分钟精度）。
 */
class WarrantyCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MonaFlowApp
        val settings = app.settingsRepository.settings.first()
        val today = LocalDate.now()
        val todayEpochDay = today.toEpochDay()

        if (settings.reminderWarranty) {
            app.itemRepository.all().filter { !it.isArchived }.forEach { item ->
                val end = item.warrantyEndDate ?: return@forEach
                val daysLeft = end - todayEpochDay
                if (ReminderTime.warrantyShouldNotify(daysLeft, null, todayEpochDay)) {
                    NotificationHelper.showReminder(
                        app,
                        warrantyCode(item.id),
                        "保修提醒",
                        "“${item.name}”的保修" + if (daysLeft <= 0L) "今天到期" else "还有 $daysLeft 天到期",
                        "item_edit?itemId=${item.id}",
                    )
                }
            }
        }

        if (settings.reminderCountdown) {
            app.countdownRepository.all().forEach { cd ->
                val days = MonaDate.countdownDays(cd.targetEpochMillis, cd.timeZoneId, cd.isRecurring, cd.recurrenceRule, today)
                if (days.isToday) {
                    NotificationHelper.showReminder(
                        app,
                        countdownCode(cd.id),
                        "今天",
                        "「${cd.title}」就是今天！",
                        "countdown_edit?cid=${cd.id}",
                    )
                }
            }
        }
        return Result.success()
    }

    private fun warrantyCode(id: Long): Int = 50_000 + (id and 0xFFFF).toInt()
    private fun countdownCode(id: Long): Int = 60_000 + (id and 0xFFFF).toInt()

    companion object {
        private const val WORK_NAME = "daily_reminder_check"

        /** 每日一次巡检（24h 周期；开机/时间变化后由 rescheduleAll 重建）。 */
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WarrantyCheckWorker>(24, TimeUnit.HOURS).build(),
            )
        }
    }
}
