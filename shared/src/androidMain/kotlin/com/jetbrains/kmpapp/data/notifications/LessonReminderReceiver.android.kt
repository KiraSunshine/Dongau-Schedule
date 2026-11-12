package com.jetbrains.kmpapp.data.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jetbrains.kmpapp.data.storage.AndroidContextProvider

class LessonReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra("lesson_subject") ?: "Пара"
        val start = intent.getStringExtra("lesson_start") ?: ""
        val id = intent.getStringExtra("lesson_id")?.hashCode() ?: System.currentTimeMillis().toInt()

        AndroidLessonReminderManager.createChannel(context)
        val notification = NotificationCompat.Builder(context, "lesson_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Скоро пара")
            .setContentText("$subject в $start")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(id and 0x7fffffff, notification)
        } catch (_: SecurityException) {
        }
    }
}
