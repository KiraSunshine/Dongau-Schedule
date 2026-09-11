package com.jetbrains.kmpapp.data.parser

import com.jetbrains.kmpapp.data.model.DongauScheduleItem
import com.jetbrains.kmpapp.data.model.DongauScheduleResponse
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.defaultBells
import kotlinx.datetime.LocalDateTime

object DongauScheduleParser {

    fun parse(response: DongauScheduleResponse): List<Lesson> {
        return response.rasp.mapNotNull { item ->
            parseItem(item)
        }.sortedWith(compareBy({ it.date }, { it.bellNumber }))
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