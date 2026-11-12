package com.jetbrains.kmpapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,
    /** За сколько минут до начала пары отправлять уведомление (1..120). */
    val minutesBefore: Int = 15
) {
    val clampedMinutes: Int get() = minutesBefore.coerceIn(1, 120)
}
