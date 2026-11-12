package com.jetbrains.kmpapp.di

import com.jetbrains.kmpapp.data.notifications.AndroidLessonReminderManager
import com.jetbrains.kmpapp.data.notifications.LessonReminderManager

actual fun provideLessonReminderManager(): LessonReminderManager = AndroidLessonReminderManager()
