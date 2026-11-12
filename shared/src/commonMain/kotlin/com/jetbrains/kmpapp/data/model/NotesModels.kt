package com.jetbrains.kmpapp.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Заметка к конкретной паре (в рамках выбранного расписания).
 */
@Serializable
data class LessonNote(
    val targetId: Int,
    val date: LocalDate,
    val bellNumber: Int,
    val text: String
)

/**
 * Заметка к предмету в рамках расписания (общая для всех пар предмета у target).
 */
@Serializable
data class SubjectNote(
    val targetId: Int,
    val subjectKey: String,
    val subjectDisplayName: String = "",
    val text: String
)

fun lessonNoteKey(targetId: Int, date: LocalDate, bellNumber: Int): String =
    "$targetId|$date|$bellNumber"

fun subjectNoteKey(targetId: Int, subject: String): String =
    "$targetId|${subject.trim().lowercase()}"

fun Lesson.lessonNoteKey(targetId: Int): String =
    lessonNoteKey(targetId, date, bellNumber)

fun Lesson.subjectNoteKey(targetId: Int): String =
    subjectNoteKey(targetId, subject)
