package com.jetbrains.kmpapp.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jetbrains.kmpapp.data.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {
    private val repo: ScheduleRepository by inject()
    private val manager: LessonReminderManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                manager.reschedule(
                    repo.currentLessons.value,
                    repo.attendanceRules.value,
                    repo.reminderSettings.value
                )
            } catch (_: Throwable) {
                // ignore
            } finally {
                result.finish()
            }
        }
    }
}
