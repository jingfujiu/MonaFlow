package com.monaflow.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.monaflow.app.MainActivity
import com.monaflow.app.R

/** 统一通知入口：渠道注册、点击跳转路由、通知内容不含敏感正文。 */
object NotificationHelper {

    const val CHANNEL_REMINDERS = "reminders"
    const val EXTRA_ROUTE = "monaflow_route"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_REMINDERS,
                "提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "待办、习惯打卡、倒计时与保修到期提醒"
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** 显示提醒通知。route 为应用内导航路由（如 task_edit?taskId=3）。 */
    fun showReminder(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        route: String,
    ) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(notificationId, notification)
        }
    }
}
