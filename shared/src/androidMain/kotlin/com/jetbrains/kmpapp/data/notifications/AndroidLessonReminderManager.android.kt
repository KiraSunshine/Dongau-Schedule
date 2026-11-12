package com.jetbrains.kmpapp.data.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jetbrains.kmpapp.data.model.AttendanceRule
import com.jetbrains.kmpapp.data.model.AttendanceStatus
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.ReminderSettings
import com.jetbrains.kmpapp.data.model.statusFor
import com.jetbrains.kmpapp.data.storage.AndroidContextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

private const val CHANNEL_ID = "lesson_reminders"
private const val CHANNEL_NAME = "Напоминания о парах"
private const val EXTRA_LESSON_ID = "lesson_id"
private const val EXTRA_LESSON_SUBJECT = "lesson_subject"
private const val EXTRA_LESSON_START = "lesson_start"

class AndroidLessonReminderManager : LessonReminderManager {

    private fun context(): Context =
        AndroidContextProvider.context
            ?: throw IllegalStateException("Application context is not initialized")

    override suspend fun reschedule(
        lessons: List<Lesson>,
        attendanceRules: List<AttendanceRule>,
        settings: ReminderSettings
    ) = withContext(Dispatchers.Main.immediate) {
        val ctx = context()
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllAlarms(ctx, alarmManager)

        if (!settings.enabled) return@withContext

        val minutesBefore = settings.clampedMinutes
        val nowMillis = System.currentTimeMillis()
        val today = DateUtils.today()
        val upcoming = lessons.filter { lesson -> lesson.date >= today }
            .sortedWith(compareBy({ it.date }, { it.bellNumber }))

        for (lesson in upcoming) {
            val status = attendanceRules.statusFor(lesson)
            if (status == AttendanceStatus.SKIPPED) continue

            val startMinutes = DateUtils.parseTimeToMinutes(lesson.startTime) ?: continue
            val startInstant = LocalDateTime(lesson.date, LocalTime(startMinutes / 60, startMinutes % 60))
                .toInstant(TimeZone.currentSystemDefault())
            val startMillis = startInstant.toEpochMilliseconds()
            val triggerAt = startMillis - minutesBefore * 60_000L
            if (triggerAt <= nowMillis) continue

            val pendingIntent = pendingIntentFor(ctx, lesson)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        60_000L,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            } catch (_: SecurityException) {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    60_000L,
                    pendingIntent
                )
            }
        }
        updateNowNextWidget(ctx)
    }

    override suspend fun cancelAll() = withContext(Dispatchers.Main.immediate) {
        val ctx = context()
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllAlarms(ctx, alarmManager)
        updateNowNextWidget(ctx)
    }

    private fun updateNowNextWidget(ctx: Context) {
        try {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val widgetComponent = ComponentName(ctx.packageName, "com.jetbrains.kmpapp.widget.NowNextWidgetProvider")
            val ids = manager.getAppWidgetIds(widgetComponent)
            if (ids.isEmpty()) return
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = widgetComponent
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            ctx.sendBroadcast(intent)
        } catch (_: Throwable) {
            // widget may not be installed/available
        }
    }

    private fun cancelAllAlarms(ctx: Context, alarmManager: AlarmManager) {
        for (id in 0 until 2000) {
            val intent = Intent(ctx, LessonReminderReceiver::class.java).apply {
                action = "com.jetbrains.kmpapp.LESSON_REMINDER"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                ctx,
                id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun pendingIntentFor(ctx: Context, lesson: Lesson): PendingIntent {
        val intent = Intent(ctx, LessonReminderReceiver::class.java).apply {
            action = "com.jetbrains.kmpapp.LESSON_REMINDER"
            putExtra(EXTRA_LESSON_ID, lesson.id)
            putExtra(EXTRA_LESSON_SUBJECT, lesson.subject)
            putExtra(EXTRA_LESSON_START, lesson.startTime)
        }
        val requestCode = (lesson.id.hashCode() and 0x7fffffff) % 2000
        return PendingIntent.getBroadcast(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Напоминания о начале пар"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
