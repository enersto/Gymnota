package com.example.myfit.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.myfit.R

object NotificationHelper {
    private const val CHANNEL_ID = "timer_channel"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Workout Timer"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateTimerNotification(context: Context, taskName: String?, timeRemaining: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 根据传入参数决定显示“正在计时”还是“暂停”
        val title: String
        val content: String

        if (timeRemaining != null && taskName != null) {
            // 计时中状态
            title = context.getString(R.string.notify_training_title, taskName)
            content = context.getString(R.string.notify_time_remaining, timeRemaining)
        } else {
            // 暂停状态 (当 taskName 为 null 时视为暂停状态的特殊调用)
            title = context.getString(R.string.notify_paused_title)
            content = context.getString(R.string.notify_click_resume)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}