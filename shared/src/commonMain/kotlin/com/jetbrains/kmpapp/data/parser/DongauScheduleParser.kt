package com.jetbrains.kmpapp.data.parser

import com.jetbrains.kmpapp.data.model.DongauScheduleItem
import com.jetbrains.kmpapp.data.model.DongauScheduleResponse
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.defaultBells
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

object DongauScheduleParser {

    private data class ParsedTemplate(
        val lesson: Lesson,
        val weekType: Int
    )

    fun parse(response: DongauScheduleResponse): List<Lesson> {
        val templates = response.rasp.mapNotNull { parseTemplate(it) }
        if (templates.isEmpty()) return emptyList()
        val lessons = if (response.isCyclical) {
            expandCyclic(templates)
        } else {
            templates.map { it.lesson }
        }
        return lessons.sortedWith(compareBy({ it.date }, { it.bellNumber }))
    }

    private fun parseTemplate(item: DongauScheduleItem): ParsedTemplate? {
        val lesson = parseItem(item) ?: return null
        return ParsedTemplate(lesson, item.weekType)
    }

    private fun expandCyclic(templates: List<ParsedTemplate>): List<Lesson> {
        val anchorDate = templates.minOf { it.lesson.date }
        val anchorWeekStart = mondayOf(anchorDate)
        val anchorType = templates.firstOrNull {
            it.lesson.date == anchorDate && it.weekType != 0
        }?.weekType ?: 0
        val semesterStart = semesterStartFor(anchorDate)
        val semesterEnd = semesterEndFor(anchorDate)
        val startWeekStart = mondayOf(semesterStart)

        val result = mutableListOf<Lesson>()
        var weekOffset = 0
        while (true) {
            val weekStart = startWeekStart.plus(DatePeriod(days = weekOffset * 7))
            if (weekStart > semesterEnd) break

            val diffWeeks = anchorWeekStart.daysUntil(weekStart) / 7
            val targetType = if (anchorType == 0 || diffWeeks % 2 == 0) {
                anchorType
            } else {
                oppositeType(anchorType)
            }

            for (template in templates) {
                if (template.weekType != 0 && template.weekType != targetType) continue
                val original = template.lesson
                val dayOffset = original.date.dayOfWeek.ordinal
                val targetDate = weekStart.plus(DatePeriod(days = dayOffset))
                if (targetDate < semesterStart || targetDate > semesterEnd) continue
                result += original.copy(
                    id = "${original.id}_$targetDate",
                    date = targetDate
                )
            }
            weekOffset++
        }
        return result
    }

    private fun oppositeType(type: Int): Int {
        return if (type == 1) 2 else 1
    }

    private fun mondayOf(date: LocalDate): LocalDate {
        return date.minus(DatePeriod(days = date.dayOfWeek.ordinal))
    }

    private fun semesterStartFor(date: LocalDate): LocalDate {
        val monthNum = date.month.ordinal + 1
        return if (monthNum in 2..8) {
            LocalDate(date.year, 2, 9)
        } else {
            val startYear = if (monthNum == 1) date.year - 1 else date.year
            LocalDate(startYear, 9, 1)
        }
    }

    private fun semesterEndFor(date: LocalDate): LocalDate {
        val monthNum = date.month.ordinal + 1
        return if (monthNum in 2..8) {
            LocalDate(date.year, 6, 30)
        } else {
            val startYear = if (monthNum == 1) date.year - 1 else date.year
            LocalDate(startYear, 12, 31)
        }
    }

    private fun parseItem(item: DongauScheduleItem): Lesson? {
        val dateStart = parseDateTime(item.dateStart) ?: return null
        val dateEnd = parseDateTime(item.dateEnd) ?: return null
        val date = dateStart.date

        val lessonType = LessonType.fromDisciplineName(item.discipline)
        val cleanSubject = cleanDisciplineName(item.discipline)

        val bellNumber = item.lessonNumber.takeIf { it in 1..7 }
            ?: determineBellNumber(item.startTime)

        val bell = defaultBells.firstOrNull { it.number == bellNumber }
        val startTime = item.startTime.ifBlank { bell?.startTime ?: "08:30" }
        val endTime = item.endTime.ifBlank { bell?.endTime ?: "10:05" }

        val teachers = buildList {
            addNotNull(item.teacherFullName)
            addNotNull(item.teacher)
        }.distinct()

        val classrooms = listOfNotNull(item.auditorium.ifBlank { null })

        val groups = listOfNotNull(item.group.ifBlank { null })

        return Lesson(
            id = "${item.code}_$date",
            subject = cleanSubject,
            lessonType = lessonType,
            teachers = teachers,
            classrooms = classrooms,
            bellNumber = bellNumber,
            startTime = startTime,
            endTime = endTime,
            date = date,
            groups = groups,
            isReplacement = item.isReplacement
        )
    }

    private fun cleanDisciplineName(name: String): String {
        val prefixes = listOf("лек ", "лк ", "пр ", "лаб ")
        var result = name
        for (prefix in prefixes) {
            if (result.lowercase().startsWith(prefix)) {
                result = result.substring(prefix.length).trimStart()
                break
            }
        }
        return result.trim()
    }

    private fun parseDateTime(raw: String): LocalDateTime? {
        return try {
            val clean = raw.replace("+03:00", "").replace("Z", "").trim()
            LocalDateTime.parse(clean)
        } catch (_: Exception) {
            null
        }
    }

    private fun determineBellNumber(time: String): Int {
        val parts = time.split(":")
        if (parts.size != 2) return 1
        val hour = parts[0].toIntOrNull() ?: return 1
        val minute = parts[1].toIntOrNull() ?: return 0
        val totalMinutes = hour * 60 + minute

        return when {
            totalMinutes < 10 * 60 -> 1      // до 10:00
            totalMinutes < 12 * 60 + 20 -> 2  // до 12:20
            totalMinutes < 14 * 60 + 20 -> 3  // до 14:20
            totalMinutes < 16 * 60 + 10 -> 4  // до 16:10
            totalMinutes < 18 * 60 -> 5       // до 18:00
            totalMinutes < 20 * 60 -> 6       // до 20:00
            else -> 7
        }
    }

    private fun <T> MutableList<T>.addNotNull(value: T?) {
        if (value != null && value.toString().isNotBlank()) add(value)
    }
}