package com.jetbrains.kmpapp.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScheduleTargetType(val id: Int, val apiParam: String, val displayName: String) {
    GROUP(1, "idGroup", "Группа"),
    TEACHER(2, "idTeacher", "Преподаватель"),
    AUDITORIUM(3, "idAudLine", "Аудитория");

    companion object {
        fun fromId(id: Int): ScheduleTargetType =
            entries.firstOrNull { it.id == id } ?: GROUP
    }
}

@Serializable
data class ScheduleTarget(
    val id: Int,
    val targetTitle: String,
    val fullTitle: String = targetTitle,
    val scheduleTarget: Int = 1
) {
    val type: ScheduleTargetType
        get() = ScheduleTargetType.fromId(scheduleTarget)
}

@Serializable
enum class LessonType(val displayName: String, val shortName: String) {
    LECTURE("Лекция", "лек"),
    PRACTICE("Практика", "пр"),
    LAB("Лабораторная", "лаб"),
    OTHER("Занятие", "другое");

    companion object {
        fun fromDisciplineName(name: String): LessonType {
            val lower = name.lowercase().trim()
            return when {
                lower.startsWith("лек") || lower.startsWith("лк") -> LECTURE
                lower.startsWith("лаб") -> LAB
                lower.startsWith("пр") -> PRACTICE
                else -> OTHER
            }
        }
    }
}

@Serializable
data class LessonBells(
    val number: Int,
    val startTime: String,
    val endTime: String
)

val defaultBells = listOf(
    LessonBells(1, "08:30", "10:05"),
    LessonBells(2, "10:20", "11:55"),
    LessonBells(3, "12:35", "14:10"),
    LessonBells(4, "14:25", "16:00"),
    LessonBells(5, "16:15", "17:50"),
    LessonBells(6, "18:05", "19:40"),
    LessonBells(7, "19:55", "21:30")
)

@Serializable
data class Lesson(
    val id: String,
    val subject: String,
    val lessonType: LessonType,
    val teachers: List<String>,
    val classrooms: List<String>,
    val bellNumber: Int,
    val startTime: String,
    val endTime: String,
    val date: LocalDate,
    val groups: List<String> = emptyList(),
    val isReplacement: Boolean = false
)

data class DaySchedule(
    val date: LocalDate,
    val lessons: List<Lesson>
)

data class SemesterWeekInfo(
    val weekNumber: Int,
    val isEven: Boolean
)

sealed class ScheduleSlot {
    abstract val bellNumber: Int
    abstract val startTime: String
    abstract val endTime: String

    data class Active(
        override val bellNumber: Int,
        override val startTime: String,
        override val endTime: String,
        val lessons: List<Lesson>
    ) : ScheduleSlot()

    data class Empty(
        override val bellNumber: Int,
        override val startTime: String,
        override val endTime: String
    ) : ScheduleSlot()
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("Авто"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

fun calculateBreakMinutes(endPrev: String, startNext: String): Int {
    val endParts = endPrev.split(':')
    val startParts = startNext.split(':')
    if (endParts.size != 2 || startParts.size != 2) return 0
    val endHour = endParts[0].toIntOrNull() ?: return 0
    val endMin = endParts[1].toIntOrNull() ?: return 0
    val startHour = startParts[0].toIntOrNull() ?: return 0
    val startMin = startParts[1].toIntOrNull() ?: return 0
    val endTotal = endHour * 60 + endMin
    val startTotal = startHour * 60 + startMin
    val diff = startTotal - endTotal
    return if (diff > 0) diff else 0
}

// --- Models for edu.dongau.ru API responses ---

@Serializable
data class DongauApiResponse<T>(
    val data: T,
    val state: Int = 0,
    val msg: String = ""
)

@Serializable
data class DongauGroupItem(
    @SerialName("name") val name: String = "",
    @SerialName("id") val id: Int = 0,
    @SerialName("kurs") val kurs: Int = 0,
    @SerialName("facul") val faculty: String = "",
    @SerialName("yearName") val yearName: String = ""
)

@Serializable
data class DongauScheduleResponse(
    @SerialName("isCyclicalSchedule") val isCyclical: Boolean = false,
    @SerialName("rasp") val rasp: List<DongauScheduleItem> = emptyList(),
    @SerialName("info") val info: DongauScheduleInfo = DongauScheduleInfo()
)

@Serializable
data class DongauScheduleItem(
    @SerialName("код") val code: Int = 0,
    @SerialName("дата") val date: String = "",
    @SerialName("начало") val startTime: String = "",
    @SerialName("датаНачала") val dateStart: String = "",
    @SerialName("датаОкончания") val dateEnd: String = "",
    @SerialName("конец") val endTime: String = "",
    @SerialName("деньНедели") val dayOfWeek: Int = 0,
    @SerialName("день_недели") val dayOfWeekName: String = "",
    @SerialName("дисциплина") val discipline: String = "",
    @SerialName("преподаватель") val teacher: String = "",
    @SerialName("аудитория") val auditorium: String = "",
    @SerialName("группа") val group: String = "",
    @SerialName("типНедели") val weekType: Int = 0,
    @SerialName("номерЗанятия") val lessonNumber: Int = 0,
    @SerialName("замена") val isReplacement: Boolean = false,
    @SerialName("кодПреподавателя") val teacherId: Int = 0,
    @SerialName("кодГруппы") val groupId: Int = 0,
    @SerialName("фиоПреподавателя") val teacherFullName: String? = null,
    @SerialName("учебныйГод") val academicYear: String = "",
    @SerialName("тема") val topic: String = ""
)

@Serializable
data class DongauScheduleInfo(
    @SerialName("group") val group: DongauGroupInfo = DongauGroupInfo(),
    @SerialName("year") val year: String = "",
    @SerialName("curWeekNumber") val currentWeekNumber: Int = 0,
    @SerialName("curNumNed") val currentWeekType: Int = 0,
    @SerialName("curSem") val currentSemester: Int = 0,
    @SerialName("typesWeek") val weekTypes: List<DongauWeekType> = emptyList()
)

@Serializable
data class DongauGroupInfo(
    @SerialName("name") val name: String = "",
    @SerialName("groupID") val groupId: Int = 0
)

@Serializable
data class DongauWeekType(
    @SerialName("typeWeekID") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("shortName") val shortName: String = ""
)