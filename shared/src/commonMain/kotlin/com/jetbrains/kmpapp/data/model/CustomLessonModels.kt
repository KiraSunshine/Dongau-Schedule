package com.jetbrains.kmpapp.data.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Пользовательская (созданная вручную) пара.
 * [isRecurring]=false -> одноразовая на [date].
 * [isRecurring]=true  -> повторяющаяся: день недели [dayOfWeek], якорь [anchorDate],
 * периодичность [intervalWeeks] (1 = каждую неделю, 2 = раз в 2 недели и т.д.).
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CustomLesson(
    val id: String = Uuid.random().toString(),
    val targetId: Int,
    val subject: String,
    val lessonType: LessonType = LessonType.OTHER,
    val teachers: List<String> = emptyList(),
    val classrooms: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val bellNumber: Int,
    val startTime: String,
    val endTime: String,
    val isRecurring: Boolean = false,
    /** . */
    val date: LocalDate? = null,
    /** . */
    val anchorDate: LocalDate? = null,
    /** . */
    val dayOfWeek: Int? = null,
    /** 1 = , 2 = 2 ? ?.?. */
    val intervalWeeks: Int = 1,
    /** . */
    val keepEverySemester: Boolean = false,
    /** . */
    val createdSemesterKey: String = "",
    /** . */
    val allowOutsideSemester: Boolean = false,
    val createdAt: Long = 0L
)

/** Преобразовать пользовательскую пару в отображаемую Lesson для конкретной даты, если она попадает. */
fun CustomLesson.toLessonOn(date: LocalDate): Lesson? {
    if (targetId == 0) return null
    val occurs = when {
        !isRecurring -> this.date == date
        else -> {
            val anchor = anchorDate
            val dow = dayOfWeek
            if (anchor == null || dow == null) {
                false
            } else {
                val dateDow = DateUtils.isoDayNumber(date.dayOfWeek)
                if (dateDow != dow) {
                    false
                } else if (date < anchor) {
                    false
                } else {
                    val days = anchor.daysUntil(date)
                    if (days % 7 != 0) {
                        false
                    } else {
                        val weeks = days / 7
                        val interval = intervalWeeks.coerceAtLeast(1)
                        weeks % interval == 0
                    }
                }
            }
        }
    }
    if (!occurs) return null
    // Пара действует в пределах семестра, в котором была добавлена (1 семестр с момента добавления).
    val semesterKey = createdSemesterKey.ifBlank { DateUtils.currentSemesterKey(date) }
    val (semStart, semEnd) = DateUtils.semesterBounds(semesterKey) ?: run {
        val fallbackKey = DateUtils.currentSemesterKey(date)
        DateUtils.semesterBounds(fallbackKey) ?: (date to date)
    }
    if (date < semStart || date > semEnd) return null

    if (!allowOutsideSemester) {
        val semesterKey = createdSemesterKey.ifBlank { DateUtils.currentSemesterKey(date) }
        val (semStart, semEnd) = DateUtils.semesterBounds(semesterKey) ?: run {
            val fallbackKey = DateUtils.currentSemesterKey(date)
            DateUtils.semesterBounds(fallbackKey) ?: (date to date)
        }
        if (date < semStart || date > semEnd) return null
    }
    return Lesson(
        id = "custom_${id}_$date",
        subject = subject,
        lessonType = lessonType,
        teachers = teachers,
        classrooms = classrooms,
        bellNumber = bellNumber,
        startTime = startTime,
        endTime = endTime,
        date = date,
        groups = groups,
        isReplacement = false,
        weekType = 0
    )
}

fun List<CustomLesson>.expandForTarget(targetId: Int, from: LocalDate, to: LocalDate): List<Lesson> {
    if (from > to) return emptyList()
    val result = mutableListOf<Lesson>()
    val relevant = filter { it.targetId == targetId }
    var d = from
    while (d <= to) {
        for (c in relevant) {
            c.toLessonOn(d)?.let { result.add(it) }
        }
        d = d.plus(DatePeriod(days = 1))
    }
    return result
}

fun CustomLesson.isExpired(currentSemester: String): Boolean =
    !allowOutsideSemester && !keepEverySemester && createdSemesterKey.isNotBlank() && createdSemesterKey != currentSemester


/**
 * / ( , ?? ? ).
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class ExtraLesson(
    val id: String = Uuid.random().toString(),
    val targetId: Int,
    val subject: String,
    val lessonType: LessonType = LessonType.OTHER,
    val teachers: List<String> = emptyList(),
    val classrooms: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val bellNumber: Int,
    val startTime: String,
    val endTime: String,
    val isRecurring: Boolean = false,
    val date: LocalDate? = null,
    val anchorDate: LocalDate? = null,
    val dayOfWeek: Int? = null,
    val intervalWeeks: Int = 1,
    val createdAt: Long = 0L
)

fun ExtraLesson.toLessonOn(date: LocalDate): Lesson? {
    if (targetId == 0) return null
    val occurs = when {
        !isRecurring -> this.date == date
        else -> {
            val anchor = anchorDate
            val dow = dayOfWeek
            if (anchor == null || dow == null) {
                false
            } else {
                val dateDow = DateUtils.isoDayNumber(date.dayOfWeek)
                if (dateDow != dow) {
                    false
                } else if (date < anchor) {
                    false
                } else {
                    val days = anchor.daysUntil(date)
                    if (days % 7 != 0) {
                        false
                    } else {
                        val weeks = days / 7
                        val interval = intervalWeeks.coerceAtLeast(1)
                        weeks % interval == 0
                    }
                }
            }
        }
    }
    if (!occurs) return null
    return Lesson(
        id = "extra_${id}_$date",
        subject = subject,
        lessonType = lessonType,
        teachers = teachers,
        classrooms = classrooms,
        bellNumber = bellNumber,
        startTime = startTime,
        endTime = endTime,
        date = date,
        groups = groups,
        isReplacement = false,
        weekType = 0
    )
}

fun List<ExtraLesson>.expandExtraForTarget(targetId: Int, from: LocalDate, to: LocalDate): List<Lesson> {
    if (from > to) return emptyList()
    val result = mutableListOf<Lesson>()
    val relevant = filter { it.targetId == targetId }
    var d = from
    while (d <= to) {
        for (c in relevant) {
            c.toLessonOn(d)?.let { result.add(it) }
        }
        d = d.plus(DatePeriod(days = 1))
    }
    return result
}


@Serializable
data class ExportedLessonsFile(
    val type: String = "dongau_schedule_lessons",
    val version: Int = 1,
    val lessons: List<ExportedLesson> = emptyList()
)

@Serializable
data class ExportedLesson(
    val subject: String,
    val lessonType: LessonType = LessonType.OTHER,
    val teachers: List<String> = emptyList(),
    val classrooms: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val bellNumber: Int,
    val startTime: String,
    val endTime: String,
    val isRecurring: Boolean = false,
    val date: LocalDate? = null,
    val anchorDate: LocalDate? = null,
    val dayOfWeek: Int? = null,
    val intervalWeeks: Int = 1,
    val keepEverySemester: Boolean = false,
    val allowOutsideSemester: Boolean = false,
    val isExtra: Boolean = false
)
