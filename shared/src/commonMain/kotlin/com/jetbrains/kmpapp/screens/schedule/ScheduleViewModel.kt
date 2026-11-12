package com.jetbrains.kmpapp.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.model.AttendanceRule
import com.jetbrains.kmpapp.data.model.AttendanceStatus
import com.jetbrains.kmpapp.data.model.CustomLesson
import com.jetbrains.kmpapp.data.model.ExtraLesson
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonNote
import com.jetbrains.kmpapp.data.model.ReminderSettings
import com.jetbrains.kmpapp.data.model.ScheduleSlot
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.ScheduleTargetType
import com.jetbrains.kmpapp.data.model.SubjectNote
import com.jetbrains.kmpapp.data.model.defaultBells
import com.jetbrains.kmpapp.data.model.seriesKey
import com.jetbrains.kmpapp.data.model.statusFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class ScheduleViewModel(
    private val repository: ScheduleRepository
) : ViewModel() {

    val savedTargets: StateFlow<List<ScheduleTarget>> = repository.savedTargets
    val selectedTarget: StateFlow<ScheduleTarget?> = repository.selectedTarget
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val errorMessage: StateFlow<String?> = repository.errorMessage
    val activeDiff: StateFlow<com.jetbrains.kmpapp.data.model.ScheduleDiff?> = repository.activeDiff
    val refreshStatus: StateFlow<com.jetbrains.kmpapp.data.model.RefreshStatus?> = repository.refreshStatus
    val showLessonProgress: StateFlow<Boolean> = repository.showLessonProgress
    val autoScrollToCurrentLesson: StateFlow<Boolean> = repository.autoScrollToCurrentLesson
    val showAbbreviatedNames: StateFlow<Boolean> = repository.showAbbreviatedNames
    val attendanceRules: StateFlow<List<AttendanceRule>> = repository.attendanceRules
    val lessonVisits: StateFlow<Map<String, com.jetbrains.kmpapp.data.model.LessonVisit>> = repository.lessonVisits
    val lessonNotes: StateFlow<Map<String, LessonNote>> = repository.lessonNotes
    val subjectNotes: StateFlow<Map<String, SubjectNote>> = repository.subjectNotes
    val reminderSettings: StateFlow<ReminderSettings> = repository.reminderSettings
    val customLessons: StateFlow<List<CustomLesson>> = repository.customLessons
    val extraLessons: StateFlow<List<ExtraLesson>> = repository.extraLessons

    private var lastAutoScrolledDate: LocalDate? = null
    private var lastAutoScrolledTargetId: Int? = null

    fun canAutoScroll(date: LocalDate, targetId: Int?): Boolean {
        return date != lastAutoScrolledDate || targetId != lastAutoScrolledTargetId
    }

    fun markAutoScrolled(date: LocalDate, targetId: Int?) {
        lastAutoScrolledDate = date
        lastAutoScrolledTargetId = targetId
    }

    fun resetAutoScroll() {
        lastAutoScrolledDate = null
        lastAutoScrolledTargetId = null
    }

    private val _currentMinutes = MutableStateFlow(DateUtils.currentTimeMinutes())
    val currentMinutes: StateFlow<Int> = _currentMinutes.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refreshStatus.collect { status ->
                if (status != null) {
                    val delayMs = if (status is com.jetbrains.kmpapp.data.model.RefreshStatus.Error) 3500L else 2500L
                    kotlinx.coroutines.delay(delayMs)
                    repository.clearRefreshStatus()
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                // Power-saving tick: pause or sleep longer when in background
                val isForeground = repository.isLowPowerMode.value.let { lowPower ->
                    // Update current minute
                    _currentMinutes.value = DateUtils.currentTimeMinutes()
                    val sleepTime = if (lowPower) 60_000L else 30_000L
                    kotlinx.coroutines.delay(sleepTime)
                }
            }
        }
    }

    fun dismissStatusBadge() {
        repository.clearRefreshStatus()
    }

    fun dismissDiff() {
        repository.dismissDiff()
    }

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _selectedLessonForDetail = MutableStateFlow<Lesson?>(null)
    val selectedLessonForDetail: StateFlow<Lesson?> = _selectedLessonForDetail.asStateFlow()

    val datesWithLessons: StateFlow<Set<LocalDate>> = repository.currentLessons
        .combine(MutableStateFlow(Unit)) { lessons, _ ->
            lessons.map { it.date }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val dayLessonSummaries: StateFlow<Map<LocalDate, DayLessonSummary>> = repository.currentLessons
        .combine(MutableStateFlow(Unit)) { lessons, _ ->
            lessons.groupBy { it.date }.mapValues { (_, dayLessons) ->
                val orderedTypes = dayLessons.groupBy { it.bellNumber }
                    .entries.sortedBy { it.key }
                    .map { (_, slotLessons) -> slotLessons.first().lessonType }
                DayLessonSummary(lessonTypes = orderedTypes)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val daySlots: StateFlow<List<ScheduleSlot>> = combine(
        repository.currentLessons,
        _selectedDate,
        repository.showEmptyLessons
    ) { lessons, date, showEmpty -> slotsForDate(lessons, date, showEmpty) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentLessons: StateFlow<List<Lesson>> = repository.currentLessons
    val showEmptyLessons: StateFlow<Boolean> = repository.showEmptyLessons

    fun slotsForDate(
        date: LocalDate,
        lessons: List<Lesson> = currentLessons.value,
        showEmpty: Boolean = showEmptyLessons.value
    ): List<ScheduleSlot> = slotsForDate(lessons, date, showEmpty)

    private fun slotsForDate(
        lessons: List<Lesson>,
        date: LocalDate,
        showEmpty: Boolean
    ): List<ScheduleSlot> {
        val forDay = lessons.filter { it.date == date }
        if (forDay.isEmpty()) {
            return emptyList()
        }

        val bellMap = forDay.groupBy { it.bellNumber }
        if (!showEmpty) {
            return bellMap.entries.sortedBy { it.key }.map { (bell, items) ->
                val first = items.first()
                ScheduleSlot.Active(bell, first.startTime, first.endTime, items)
            }
        }

        val result = mutableListOf<ScheduleSlot>()
        val upperBell = maxOf(forDay.maxOfOrNull { it.bellNumber } ?: 7, 7)
        for (bell in 1..upperBell) {
            val items = bellMap[bell]
            if (!items.isNullOrEmpty()) {
                val first = items.first()
                result += ScheduleSlot.Active(bell, first.startTime, first.endTime, items)
            } else {
                val bellInfo = defaultBells.firstOrNull { it.number == bell }
                result += ScheduleSlot.Empty(bell, bellInfo?.startTime ?: "—", bellInfo?.endTime ?: "—")
            }
        }
        return result
    }

    fun selectDate(date: LocalDate) {
        if (_selectedDate.value != date) {
            _selectedDate.value = date
            resetAutoScroll()
        }
    }

    fun nextDay() {
        selectDate(_selectedDate.value.plus(DatePeriod(days = 1)))
    }

    fun previousDay() {
        selectDate(_selectedDate.value.minus(DatePeriod(days = 1)))
    }

    fun selectLessonForDetail(lesson: Lesson?) {
        _selectedLessonForDetail.value = lesson
    }

    fun setAttendance(lesson: Lesson, status: AttendanceStatus, intervalWeeks: Int) {
        // Убираем факт посещения для этой пары, чтобы выбранный план был виден.
        selectedTarget.value?.id?.let { targetId ->
            repository.removeLessonVisit(targetId, lesson.date, lesson.bellNumber)
        }
        repository.setAttendanceRule(
            AttendanceRule(
                seriesKey = lesson.seriesKey(),
                status = status,
                anchorDate = lesson.date,
                intervalWeeks = intervalWeeks,
                subject = lesson.subject,
                teacher = lesson.teachers.joinToString(", "),
                bellNumber = lesson.bellNumber,
                dayOfWeek = DateUtils.isoDayNumber(lesson.date.dayOfWeek)
            )
        )
    }

    fun clearAttendance(lesson: Lesson) {
        // Сброс плана к умолчанию («пойду»): убираем факт, чтобы было видно план по умолчанию.
        selectedTarget.value?.id?.let { targetId ->
            repository.removeLessonVisit(targetId, lesson.date, lesson.bellNumber)
        }
        repository.clearAttendanceRule(lesson)
    }

    fun setLessonNote(targetId: Int, lesson: Lesson, text: String) {
        if (text.isBlank()) repository.removeLessonNote(targetId, lesson.date, lesson.bellNumber)
        else repository.setLessonNote(LessonNote(targetId, lesson.date, lesson.bellNumber, text.trim()))
    }

    fun setSubjectNote(targetId: Int, lesson: Lesson, text: String) {
        if (text.isBlank()) repository.removeSubjectNote(targetId, lesson.subject)
        else repository.setSubjectNote(
            SubjectNote(
                targetId = targetId,
                subjectKey = lesson.subject.trim().lowercase(),
                subjectDisplayName = lesson.subject.trim(),
                text = text.trim()
            )
        )
    }

    suspend fun resolveAndOpenTarget(name: String, type: ScheduleTargetType): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val saved = savedTargets.value.firstOrNull { it.type == type && it.targetTitle.equals(trimmed, ignoreCase = true) }
        val target = saved ?: if (type == ScheduleTargetType.GROUP) {
            val results = repository.search(trimmed)
            results.firstOrNull { it.targetTitle.equals(trimmed, ignoreCase = true) }
                ?: results.firstOrNull { it.type == type }
        } else null
        return if (target != null) {
            addAndSelectTarget(target)
            selectLessonForDetail(null)
            true
        } else false
    }

    fun setReminderSettings(settings: ReminderSettings) {
        repository.setReminderSettings(settings)
    }

    fun addCustomLesson(
        targetId: Int,
        subject: String,
        lessonType: com.jetbrains.kmpapp.data.model.LessonType,
        teacher: String,
        classroom: String,
        group: String,
        bellNumber: Int,
        startTime: String,
        endTime: String,
        isRecurring: Boolean,
        date: kotlinx.datetime.LocalDate?,
        dayOfWeek: Int?,
        intervalWeeks: Int,
        keepEverySemester: Boolean,
        allowOutsideSemester: Boolean = false
    ) {
        addCustomLessons(
            targetId = targetId,
            subject = subject,
            lessonType = lessonType,
            teacher = teacher,
            classroom = classroom,
            group = group,
            bells = listOf(bellNumber to (startTime to endTime)),
            isRecurring = isRecurring,
            date = date,
            dayOfWeek = dayOfWeek,
            intervalWeeks = intervalWeeks,
            keepEverySemester = keepEverySemester,
            allowOutsideSemester = allowOutsideSemester
        )
    }

    fun addCustomLessons(
        targetId: Int,
        subject: String,
        lessonType: com.jetbrains.kmpapp.data.model.LessonType,
        teacher: String,
        classroom: String,
        group: String,
        bells: List<Pair<Int, Pair<String, String>>>,
        isRecurring: Boolean,
        date: kotlinx.datetime.LocalDate?,
        dayOfWeek: Int?,
        intervalWeeks: Int,
        keepEverySemester: Boolean,
        allowOutsideSemester: Boolean = false
    ) {
        if (subject.isBlank() || bells.isEmpty()) return
        val baseDate = date
        val semesterKey = baseDate?.let { DateUtils.currentSemesterKey(it) } ?: DateUtils.currentSemesterKey()
        val bounds = DateUtils.semesterBounds(semesterKey)
        val clampedDate = if (!allowOutsideSemester && baseDate != null && bounds != null) {
            when {
                baseDate < bounds.first -> bounds.first
                baseDate > bounds.second -> bounds.second
                else -> baseDate
            }
        } else baseDate

        val teachers = teacher.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val classrooms = classroom.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val groups = group.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val createdAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val lessons = bells.map { (bellNumber, times) ->
            CustomLesson(
                targetId = targetId,
                subject = subject.trim(),
                lessonType = lessonType,
                teachers = teachers,
                classrooms = classrooms,
                groups = groups,
                bellNumber = bellNumber.coerceIn(1, 10),
                startTime = times.first,
                endTime = times.second,
                isRecurring = isRecurring,
                date = if (isRecurring) null else clampedDate,
                anchorDate = if (isRecurring) clampedDate else null,
                dayOfWeek = if (isRecurring) (dayOfWeek ?: clampedDate?.let { DateUtils.isoDayNumber(it.dayOfWeek) } ?: 1) else null,
                intervalWeeks = if (isRecurring) intervalWeeks.coerceIn(1, 12) else 0,
                keepEverySemester = keepEverySemester,
                createdSemesterKey = semesterKey,
                allowOutsideSemester = allowOutsideSemester,
                createdAt = createdAt
            )
        }
        repository.addCustomLessons(lessons)
    }

    fun removeCustomLesson(id: String) {
        repository.removeCustomLesson(id)
    }

    fun updateCustomLesson(
        existing: CustomLesson,
        subject: String,
        lessonType: com.jetbrains.kmpapp.data.model.LessonType,
        teacher: String,
        classroom: String,
        group: String,
        bellNumber: Int,
        startTime: String,
        endTime: String,
        isRecurring: Boolean,
        date: kotlinx.datetime.LocalDate?,
        dayOfWeek: Int?,
        intervalWeeks: Int,
        keepEverySemester: Boolean,
        allowOutsideSemester: Boolean = false
    ) {
        if (subject.isBlank()) return
        val baseDate = date
        val semesterKey = baseDate?.let { DateUtils.currentSemesterKey(it) } ?: existing.createdSemesterKey
        val bounds = DateUtils.semesterBounds(semesterKey)
        val clampedDate = if (!allowOutsideSemester && baseDate != null && bounds != null) {
            when {
                baseDate < bounds.first -> bounds.first
                baseDate > bounds.second -> bounds.second
                else -> baseDate
            }
        } else baseDate

        val updated = existing.copy(
            subject = subject.trim(),
            lessonType = lessonType,
            teachers = teacher.split(",").map { it.trim() }.filter { it.isNotBlank() },
            classrooms = classroom.split(",").map { it.trim() }.filter { it.isNotBlank() },
            groups = group.split(",").map { it.trim() }.filter { it.isNotBlank() },
            bellNumber = bellNumber.coerceIn(1, 10),
            startTime = startTime,
            endTime = endTime,
            isRecurring = isRecurring,
            date = if (isRecurring) null else clampedDate,
            anchorDate = if (isRecurring) clampedDate else null,
            dayOfWeek = if (isRecurring) (dayOfWeek ?: clampedDate?.let { DateUtils.isoDayNumber(it.dayOfWeek) } ?: 1) else null,
            intervalWeeks = if (isRecurring) intervalWeeks.coerceIn(1, 12) else 0,
            keepEverySemester = keepEverySemester,
            createdSemesterKey = semesterKey,
            allowOutsideSemester = allowOutsideSemester
        )
        repository.updateCustomLesson(updated)
    }

    fun selectTarget(target: ScheduleTarget) {
        repository.selectTarget(target)
        resetAutoScroll()
    }

    fun addAndSelectTarget(target: ScheduleTarget) {
        repository.addAndSelectTarget(target)
        resetAutoScroll()
    }

    fun refresh() {
        repository.refreshCurrentSchedule()
    }

    suspend fun search(query: String): List<ScheduleTarget> {
        return repository.search(query)
    }

    fun addExtraLessons(
        targetId: Int,
        subject: String,
        lessonType: com.jetbrains.kmpapp.data.model.LessonType,
        teacher: String,
        classroom: String,
        group: String,
        bells: List<Pair<Int, Pair<String, String>>>,
        isRecurring: Boolean,
        date: kotlinx.datetime.LocalDate?,
        dayOfWeek: Int?,
        intervalWeeks: Int
    ) {
        if (subject.isBlank() || bells.isEmpty()) return
        val teachers = teacher.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val classrooms = classroom.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val groups = group.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val createdAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val lessons = bells.map { (bellNumber, times) ->
            ExtraLesson(
                targetId = targetId,
                subject = subject.trim(),
                lessonType = lessonType,
                teachers = teachers,
                classrooms = classrooms,
                groups = groups,
                bellNumber = bellNumber.coerceIn(1, 10),
                startTime = times.first,
                endTime = times.second,
                isRecurring = isRecurring,
                date = if (isRecurring) null else date,
                anchorDate = if (isRecurring) date else null,
                dayOfWeek = if (isRecurring) (dayOfWeek ?: date?.let { DateUtils.isoDayNumber(it.dayOfWeek) } ?: 1) else null,
                intervalWeeks = if (isRecurring) intervalWeeks.coerceIn(1, 12) else 0,
                createdAt = createdAt
            )
        }
        repository.addExtraLessons(lessons)
    }

    fun removeExtraLesson(id: String) {
        repository.removeExtraLesson(id)
    }

    fun updateExtraLesson(
        existing: ExtraLesson,
        subject: String,
        lessonType: com.jetbrains.kmpapp.data.model.LessonType,
        teacher: String,
        classroom: String,
        group: String,
        bellNumber: Int,
        startTime: String,
        endTime: String,
        isRecurring: Boolean,
        date: kotlinx.datetime.LocalDate?,
        dayOfWeek: Int?,
        intervalWeeks: Int
    ) {
        if (subject.isBlank()) return
        val updated = existing.copy(
            subject = subject.trim(),
            lessonType = lessonType,
            teachers = teacher.split(",").map { it.trim() }.filter { it.isNotBlank() },
            classrooms = classroom.split(",").map { it.trim() }.filter { it.isNotBlank() },
            groups = group.split(",").map { it.trim() }.filter { it.isNotBlank() },
            bellNumber = bellNumber.coerceIn(1, 10),
            startTime = startTime,
            endTime = endTime,
            isRecurring = isRecurring,
            date = if (isRecurring) null else date,
            anchorDate = if (isRecurring) date else null,
            dayOfWeek = if (isRecurring) (dayOfWeek ?: date?.let { DateUtils.isoDayNumber(it.dayOfWeek) } ?: 1) else null,
            intervalWeeks = if (isRecurring) intervalWeeks.coerceIn(1, 12) else 0
        )
        repository.updateExtraLesson(updated)
    }

}

data class DayLessonSummary(
    val lessonTypes: List<com.jetbrains.kmpapp.data.model.LessonType> = emptyList()
) {
    val hasLessons: Boolean get() = lessonTypes.isNotEmpty()
}

