package com.jetbrains.kmpapp.data.notifications

import com.jetbrains.kmpapp.data.model.AttendanceRule
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.ReminderSettings

/**
 * Платформенный менеджер локальных напоминаний о парах.
 * На Android планирует уведомления через AlarmManager/NotificationManager.
 */
interface LessonReminderManager {
    /** Перепланировать напоминания с учётом текущих настроек, занятий и отметок посещаемости. */
    suspend fun reschedule(
        lessons: List<Lesson>,
        attendanceRules: List<AttendanceRule>,
        settings: ReminderSettings
    )

    /** Отменить все запланированные напоминания. */
    suspend fun cancelAll()
}

object NoopLessonReminderManager : LessonReminderManager {
    override suspend fun reschedule(
        lessons: List<Lesson>,
        attendanceRules: List<AttendanceRule>,
        settings: ReminderSettings
    ) = Unit

    override suspend fun cancelAll() = Unit
}
