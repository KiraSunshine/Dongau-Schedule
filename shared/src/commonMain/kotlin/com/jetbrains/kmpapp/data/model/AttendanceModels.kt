package com.jetbrains.kmpapp.data.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

/**
 * Отметка посещаемости занятия.
 *
 * ATTENDING — пользователь планирует пойти (карточка остаётся обычной, появляется галочка).
 * SKIPPED   — пользователь не пойдёт (карточка становится почти прозрачной).
 */
@Serializable
enum class AttendanceStatus(val displayName: String) {
    ATTENDING("Буду ходить"),
    SKIPPED("Не буду")
}

/**
 * Правило отметки для серии одинаковых занятий.
 *
 * Серия определяется предметом, преподавателем, номером пары и днём недели.
 * [anchorDate] — первая дата, к которой применяется правило.
 * [intervalWeeks] — периодичность повторения:
 *   0 — только [anchorDate],
 *   1 — каждую неделю,
 *   2 — каждые 2 недели и т. д.
 */
@Serializable
data class AttendanceRule(
    val seriesKey: String,
    val status: AttendanceStatus,
    val anchorDate: LocalDate,
    val intervalWeeks: Int = 0,
    val subject: String = "",
    val teacher: String = "",
    val bellNumber: Int = 0,
    val dayOfWeek: Int = 0
) {
    fun matches(lesson: Lesson): Boolean {
        if (lesson.seriesKey() != seriesKey) return false
        if (lesson.date < anchorDate) return false
        if (intervalWeeks <= 0) return lesson.date == anchorDate
        val days = anchorDate.daysUntil(lesson.date)
        if (days < 0) return false
        // Даты серии всегда отличаются на целое число недель.
        if (days % 7 != 0) return false
        return (days / 7) % intervalWeeks == 0
    }
}

/** Уникальный ключ серии занятий: предмет + преподаватель + номер пары + день недели. */
fun Lesson.seriesKey(): String {
    val subjectPart = subject.trim().lowercase()
    val teacherPart = teachers.joinToString(",").trim().lowercase()
    return "$subjectPart|$teacherPart|$bellNumber|${date.dayOfWeek.name}"
}

fun List<AttendanceRule>.statusFor(lesson: Lesson): AttendanceStatus? {
    // Сначала ищем явную отметку «не буду» — она важнее.
    val matched = filter { it.matches(lesson) }
    if (matched.isEmpty()) return null
    return matched.firstOrNull { it.status == AttendanceStatus.SKIPPED }?.status
        ?: matched.first().status
}

/** Подпись периодичности для UI. */
fun intervalWeeksLabel(weeks: Int): String = when (weeks) {
    0 -> "Только этот день"
    1 -> "Каждую неделю"
    2 -> "Каждые 2 недели"
    3 -> "Каждые 3 недели"
    4 -> "Каждые 4 недели"
    else -> "Каждые $weeks нед."
}
