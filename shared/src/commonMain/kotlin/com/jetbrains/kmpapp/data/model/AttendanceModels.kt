package com.jetbrains.kmpapp.data.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

/**
 * Отметка посещаемости/намерения для серии занятий.
 *
 * ATTENDING — пользователь планирует пойти.
 * SKIPPED   — пользователь не пойдёт.
 */
@Serializable
enum class AttendanceStatus(val displayName: String) {
    ATTENDING("Буду ходить"),
    SKIPPED("Не буду")
}

/** Фактическая отметка посещения конкретной пары. */
@Serializable
data class LessonVisit(
    val targetId: Int,
    val date: LocalDate,
    val bellNumber: Int,
    val subjectKey: String = "",
    val attended: Boolean = true
)

fun lessonVisitKey(targetId: Int, date: LocalDate, bellNumber: Int): String =
    "$targetId|$date|$bellNumber"

fun Lesson.visitKey(targetId: Int): String = lessonVisitKey(targetId, date, bellNumber)

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
    if (isEmpty()) return null

    fun AttendanceRule.isSameSeriesSlot(l: Lesson): Boolean =
        l.seriesKey() == seriesKey && l.date >= anchorDate &&
            anchorDate.daysUntil(l.date).let { days -> days >= 0 && days % 7 == 0 }

    fun AttendanceRule.complementStatus(): AttendanceStatus =
        if (status == AttendanceStatus.ATTENDING) AttendanceStatus.SKIPPED else AttendanceStatus.ATTENDING

    // 1) Точечная отметка для конкретной даты — наивысший приоритет.
    val exact = filter { it.intervalWeeks <= 0 && it.matches(lesson) }
    if (exact.isNotEmpty()) {
        return exact.firstOrNull { it.status == AttendanceStatus.SKIPPED }?.status ?: exact.first().status
    }

    // 2) Серийные правила: совпадение по интервалу.
    val seriesDirect = filter { it.intervalWeeks >= 1 && it.matches(lesson) }
    if (seriesDirect.isNotEmpty()) {
        return seriesDirect.firstOrNull { it.status == AttendanceStatus.SKIPPED }?.status ?: seriesDirect.first().status
    }

    // 3) Чередование: для правил "каждые N недель" (N>=2) неделя вне интервала
    //    означает противоположный статус (пойду раз в 2 недели => остальные — пропуск).
    val seriesComplement = filter { rule ->
        rule.intervalWeeks >= 2 && rule.isSameSeriesSlot(lesson) && !rule.matches(lesson)
    }
    if (seriesComplement.isNotEmpty()) {
        val complements = seriesComplement.map { it.complementStatus() }
        return if (complements.any { it == AttendanceStatus.SKIPPED }) AttendanceStatus.SKIPPED else AttendanceStatus.ATTENDING
    }

    return null
}

/** Подпись периодичности для UI. */


/** Эффективный статус: по умолчанию «пойду», если нет явной отметки. */
fun List<AttendanceRule>.effectiveStatusFor(lesson: Lesson): AttendanceStatus =
    statusFor(lesson) ?: AttendanceStatus.ATTENDING

fun intervalWeeksLabel(weeks: Int): String = when (weeks) {
    0 -> "Только этот день"
    1 -> "Каждую неделю"
    2 -> "Каждые 2 недели"
    3 -> "Каждые 3 недели"
    4 -> "Каждые 4 недели"
    else -> "Каждые $weeks нед."
}
