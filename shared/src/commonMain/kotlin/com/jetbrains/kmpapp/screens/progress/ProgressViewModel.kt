package com.jetbrains.kmpapp.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.model.AttendanceRule
import com.jetbrains.kmpapp.data.model.AttendanceStatus
import com.jetbrains.kmpapp.data.model.statusFor
import com.jetbrains.kmpapp.data.model.CustomLesson
import com.jetbrains.kmpapp.data.model.ExtraLesson
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.ExportedLesson
import com.jetbrains.kmpapp.data.model.ExportedLessonsFile
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.LessonVisit
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.lessonVisitKey
import com.jetbrains.kmpapp.data.share.PlatformFilePicker
import com.jetbrains.kmpapp.data.share.PlatformShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ProgressViewModel(
    private val repository: ScheduleRepository
) : ViewModel() {
    val selectedTarget: StateFlow<ScheduleTarget?> = repository.selectedTarget
    val currentLessons: StateFlow<List<Lesson>> = repository.currentLessons
    val attendanceRules: StateFlow<List<AttendanceRule>> = repository.attendanceRules
    val lessonVisits: StateFlow<Map<String, LessonVisit>> = repository.lessonVisits
    val showAbbreviatedNames: StateFlow<Boolean> = repository.showAbbreviatedNames
    val subjectColorOverrides: StateFlow<Map<String, String>> = repository.subjectColorOverrides
    val customSubjectColors: StateFlow<List<String>> = repository.customSubjectColors
    val shareAttendanceEnabled: StateFlow<Boolean> = repository.shareAttendanceEnabled


    private val _selectedSubjectKey = MutableStateFlow<String?>(null)
    val selectedSubjectKey: StateFlow<String?> = _selectedSubjectKey.asStateFlow()

    val subjects: StateFlow<List<SubjectProgress>> = combine(
        repository.currentLessons,
        repository.lessonVisits,
        repository.selectedTarget
    ) { lessons, visits, target ->
        val targetId = target?.id ?: return@combine emptyList()
        val trackableLessons = lessons.filterNot { lesson ->
            lesson.lessonType in setOf(
                LessonType.EXAM,
                LessonType.CREDIT,
                LessonType.DIFF_CREDIT,
                LessonType.CONSULTATION
            )
        }
        val bySubject = trackableLessons.groupBy { it.subject.trim().lowercase() }
        bySubject.map { (key, subjectLessons) ->
            val displayName = subjectLessons.firstOrNull()?.subject?.trim().orEmpty().ifBlank { key }
            val sorted = subjectLessons
                .sortedWith(compareBy({ it.date }, { it.bellNumber }, { it.id }))
                .distinctBy { "${it.date}|${it.bellNumber}" }
            val total = sorted.size
            val visited = sorted.count { lesson ->
                val v = visits[lessonVisitKey(targetId, lesson.date, lesson.bellNumber)]
                v?.attended == true
            }
            SubjectProgress(
                key = key,
                displayName = displayName,
                lessons = sorted,
                visitedCount = visited,
                totalCount = total
            )
        }.sortedBy { it.displayName.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectSubject(key: String?) {
        _selectedSubjectKey.value = key
    }

    fun setLessonVisited(targetId: Int, lesson: Lesson, attended: Boolean) {
        repository.setLessonVisit(
            LessonVisit(
                targetId = targetId,
                date = lesson.date,
                bellNumber = lesson.bellNumber,
                subjectKey = lesson.subject.trim().lowercase(),
                attended = attended
            )
        )
    }

    fun removeLessonVisit(targetId: Int, lesson: Lesson) {
        repository.removeLessonVisit(targetId, lesson.date, lesson.bellNumber)
    }

    fun toggleLessonVisited(targetId: Int, lesson: Lesson) {
        val key = lessonVisitKey(targetId, lesson.date, lesson.bellNumber)
        val current = lessonVisits.value[key]?.attended == true
        setLessonVisited(targetId, lesson, !current)
    }

    fun markLessonAttendanceIntent(targetId: Int, lesson: Lesson, attending: Boolean) {
        // При выборе плана убираем факт посещения, чтобы план отображался и мог быть изменён.
        repository.removeLessonVisit(targetId, lesson.date, lesson.bellNumber)

        val status = if (attending) AttendanceStatus.ATTENDING else AttendanceStatus.SKIPPED
        repository.setAttendanceRule(
            AttendanceRule(
                seriesKey = "${lesson.subject.trim().lowercase()}|${lesson.teachers.joinToString(",").trim().lowercase()}|${lesson.bellNumber}|${lesson.date.dayOfWeek.name}",
                status = status,
                anchorDate = lesson.date,
                intervalWeeks = 0,
                subject = lesson.subject,
                teacher = lesson.teachers.firstOrNull().orEmpty(),
                bellNumber = lesson.bellNumber,
                dayOfWeek = DateUtils.isoDayNumber(lesson.date.dayOfWeek)
            )
        )
    }

    private val exportJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @OptIn(ExperimentalUuidApi::class)
    fun exportSelectedSubjects(subjectKeys: Set<String>, share: Boolean) {
        if (subjectKeys.isEmpty()) return
        val target = selectedTarget.value ?: return

        fun teacherKey(teachers: List<String>) = teachers.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.sorted().joinToString(",")
        fun lessonGroupKey(l: Lesson) = l.subject.trim().lowercase() to teacherKey(l.teachers)

        val all = currentLessons.value
        val selectedGroupKeys = all
            .filter { it.subject.trim().lowercase() in subjectKeys }
            .map { lessonGroupKey(it) }
            .toSet()
        val lessons = all
            .filter { lessonGroupKey(it) in selectedGroupKeys }
            .sortedWith(compareBy({ it.subject.trim().lowercase() }, { teacherKey(it.teachers) }, { it.date }, { it.bellNumber }))

        val exported = lessons.map { lesson ->
            ExportedLesson(
                subject = lesson.subject,
                lessonType = lesson.lessonType,
                teachers = lesson.teachers,
                classrooms = lesson.classrooms,
                groups = lesson.groups,
                bellNumber = lesson.bellNumber,
                startTime = lesson.startTime,
                endTime = lesson.endTime,
                isRecurring = false,
                date = lesson.date,
                anchorDate = null,
                dayOfWeek = null,
                intervalWeeks = 1,
                keepEverySemester = false,
                allowOutsideSemester = true
            )
        }
        val payload = ExportedLessonsFile(lessons = exported)
        val text = exportJson.encodeToString(payload)
        val effectiveSubjectKeys = lessons.map { it.subject.trim().lowercase() }.toSet().ifEmpty { subjectKeys }
        val fileName = buildScheduleFileName(effectiveSubjectKeys)
        if (share) {
            PlatformShare.shareTextFile(fileName, text)
        } else {
            PlatformShare.saveTextFile(fileName, text)
        }
    }


    fun setShareAttendanceEnabled(enabled: Boolean) {
        repository.setShareAttendanceEnabled(enabled)
    }

    fun shareAttendancePlans() {
        if (!shareAttendanceEnabled.value) return
        val lessons = currentLessons.value
        val rules = attendanceRules.value
        val text = buildAttendancePlansShareText(lessons, rules)
        PlatformShare.shareText("Планы посещений", text)
    }

    private fun buildAttendancePlansShareText(
        lessons: List<Lesson>,
        rules: List<AttendanceRule>
    ): String {
        if (rules.isEmpty()) return "Нет отмеченных планов посещений."
        val today = DateUtils.today()
        val horizon = today.plus(DatePeriod(days = 60))
        val relevant = lessons
            .filter { it.date in today..horizon }
            .sortedWith(compareBy({ it.date }, { it.bellNumber }))
        val attending = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        relevant.forEach { lesson ->
            val status = rules.statusFor(lesson) ?: return@forEach
            val line = "${lesson.date} ${DateUtils.formatDayOfWeekShort(lesson.date.dayOfWeek)} ${lesson.startTime}-${lesson.endTime}, ${lesson.bellNumber} пара — ${lesson.subject}" +
                (lesson.classrooms.firstOrNull()?.let { " ($it)" } ?: "")
            when (status) {
                AttendanceStatus.ATTENDING -> attending += line
                AttendanceStatus.SKIPPED -> skipped += line
                else -> {}
            }
        }
        if (attending.isEmpty() && skipped.isEmpty()) {
            return "Нет отмеченных планов посещений на ближайшие дни."
        }
        val sb = StringBuilder()
        sb.appendLine("Планы посещений (ближайшие 60 дней):")
        if (attending.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Пойду:")
            attending.forEach { sb.appendLine("• $it") }
        }
        if (skipped.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Не пойду:")
            skipped.forEach { sb.appendLine("• $it") }
        }
        return sb.toString().trim()
    }

    @OptIn(ExperimentalUuidApi::class)
    fun importScheduleFromFile() {
        val target = selectedTarget.value ?: return
        PlatformFilePicker.pickTextFile { _, content ->
            if (content.isNullOrBlank()) return@pickTextFile
            try {
                val payload = exportJson.decodeFromString<ExportedLessonsFile>(content)
                if (payload.lessons.isEmpty()) return@pickTextFile
                val semesterKey = DateUtils.currentSemesterKey()
                val now = Clock.System.now().toEpochMilliseconds()
                val customToAdd = mutableListOf<CustomLesson>()
                val extraToAdd = mutableListOf<ExtraLesson>()
                payload.lessons.forEach { l ->
                    if (l.isExtra || l.allowOutsideSemester) {
                        extraToAdd += ExtraLesson(
                            id = Uuid.random().toString(),
                            targetId = target.id,
                            subject = l.subject.trim().ifBlank { "..." },
                            lessonType = l.lessonType,
                            teachers = l.teachers,
                            classrooms = l.classrooms,
                            groups = l.groups,
                            bellNumber = l.bellNumber.coerceIn(1, 10),
                            startTime = l.startTime,
                            endTime = l.endTime,
                            isRecurring = l.isRecurring,
                            date = if (l.isRecurring) null else l.date,
                            anchorDate = if (l.isRecurring) (l.anchorDate ?: l.date) else null,
                            dayOfWeek = if (l.isRecurring) (l.dayOfWeek ?: l.date?.let { DateUtils.isoDayNumber(it.dayOfWeek) } ?: 1) else null,
                            intervalWeeks = if (l.isRecurring) l.intervalWeeks.coerceAtLeast(1) else 0,
                            createdAt = now
                        )
                    } else {
                        customToAdd += CustomLesson(
                            id = Uuid.random().toString(),
                            targetId = target.id,
                            subject = l.subject.trim().ifBlank { "..." },
                            lessonType = l.lessonType,
                            teachers = l.teachers,
                            classrooms = l.classrooms,
                            groups = l.groups,
                            bellNumber = l.bellNumber.coerceIn(1, 10),
                            startTime = l.startTime,
                            endTime = l.endTime,
                            isRecurring = l.isRecurring,
                            date = if (l.isRecurring) null else l.date,
                            anchorDate = if (l.isRecurring) (l.anchorDate ?: l.date) else null,
                            dayOfWeek = if (l.isRecurring) (l.dayOfWeek ?: l.date?.let { DateUtils.isoDayNumber(it.dayOfWeek) } ?: 1) else null,
                            intervalWeeks = if (l.isRecurring) l.intervalWeeks.coerceAtLeast(1) else 0,
                            keepEverySemester = l.keepEverySemester,
                            createdSemesterKey = semesterKey,
                            allowOutsideSemester = l.allowOutsideSemester,
                            createdAt = now
                        )
                    }
                }
                if (customToAdd.isNotEmpty()) repository.addCustomLessons(customToAdd)
                if (extraToAdd.isNotEmpty()) repository.addExtraLessons(extraToAdd)
            } catch (_: Throwable) {
                // ignore invalid file
            }
        }
    }

    private fun buildScheduleFileName(subjectKeys: Set<String>): String {
        val names = currentLessons.value
            .filter { it.subject.trim().lowercase() in subjectKeys }
            .map { it.subject.trim() }
            .distinct()
        val base = when {
            names.isEmpty() -> "schedule"
            names.size == 1 -> names.first()
            names.size == 2 -> names.joinToString("_")
            else -> "schedule_${names.size}_subjects"
        }
        val safe = base
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(40)
            .ifBlank { "schedule" }
        return "$safe.txt"
    }
}

data class SubjectProgress(
    val key: String,
    val displayName: String,
    val lessons: List<Lesson>,
    val visitedCount: Int,
    val totalCount: Int
)
