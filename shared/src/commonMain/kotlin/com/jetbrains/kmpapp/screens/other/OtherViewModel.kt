package com.jetbrains.kmpapp.screens.other

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.TaskRepository
import com.jetbrains.kmpapp.data.DebugConfig
import com.jetbrains.kmpapp.data.model.AttendanceStatus
import com.jetbrains.kmpapp.data.model.CustomLesson
import com.jetbrains.kmpapp.data.model.ExtraLesson
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.ReminderSettings
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.ScheduleTargetType
import com.jetbrains.kmpapp.data.model.StorageStats
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.data.model.statusFor
import com.jetbrains.kmpapp.data.share.PlatformShare
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

import com.jetbrains.kmpapp.data.update.AppUpdateChecker
import com.jetbrains.kmpapp.data.update.UpdateCheckResult
import com.jetbrains.kmpapp.screens.components.AppTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

enum class TargetSortOrder(val displayName: String) {
    TITLE_ASC("По названию (А → Я / 0 → 9)"),
    TITLE_DESC("По названию (Я → А / 9 → 0)"),
    RECENT("Сначала новые"),
    OLDEST("Сначала старые")
}

enum class OtherSubScreen(val depth: Int) {
    ROOT(0),
    MANAGE_SCHEDULES(1),
    SETTINGS(1),
    DATA_AND_CACHE(2),
    DOCK_SETTINGS(2),
    TASK_SETTINGS(2),
    RESOURCES(1),
    COMPARE(1),
    ABOUT(1),
    DEBUG_SETTINGS(2),
    EXPERIMENTAL_SETTINGS(3)
}

class OtherViewModel(
    private val repository: ScheduleRepository,
    private val updateChecker: AppUpdateChecker,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val savedTargets: StateFlow<List<ScheduleTarget>> = repository.savedTargets
    val selectedTarget: StateFlow<ScheduleTarget?> = repository.selectedTarget
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val showEmptyLessons: StateFlow<Boolean> = repository.showEmptyLessons
    val showLessonProgress: StateFlow<Boolean> = repository.showLessonProgress
    val autoScrollToCurrentLesson: StateFlow<Boolean> = repository.autoScrollToCurrentLesson
    val showAbbreviatedNames: StateFlow<Boolean> = repository.showAbbreviatedNames
    val themeMode: StateFlow<ThemeMode> = repository.themeMode
    val themeOverlay: StateFlow<ThemeOverlay> = repository.themeOverlay
    val isSakuraTheme: StateFlow<Boolean> = repository.isSakuraTheme
    val isCyberpunkTheme: StateFlow<Boolean> = repository.isCyberpunkTheme
    val isMatrixTheme: StateFlow<Boolean> = repository.isMatrixTheme
    val cheatsAgreed: StateFlow<Boolean?> = repository.cheatsAgreed
    val cheatsBlocked: StateFlow<Boolean> = repository.cheatsBlocked
    val dockTabs: StateFlow<List<AppTab>> = repository.dockTabs
    val betaChannel: StateFlow<Boolean> = repository.betaChannel
    val shareAttendanceEnabled: StateFlow<Boolean> = repository.shareAttendanceEnabled
    val reminderSettings: StateFlow<ReminderSettings> = repository.reminderSettings
    val subjectColorOverrides: StateFlow<Map<String, String>> = repository.subjectColorOverrides
    val customSubjectColors: StateFlow<List<String>> = repository.customSubjectColors
    val lessonNotes = repository.lessonNotes
    val subjectNotes = repository.subjectNotes
    val customLessons = repository.customLessons
    val extraLessons = repository.extraLessons
    val currentSubjectNames: StateFlow<List<String>> = repository.currentLessons
        .combine(repository.subjectColorOverrides) { lessons, _ ->
            lessons.map { it.subject.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val attendanceRules = repository.attendanceRules
    val currentLessons = repository.currentLessons

    fun setShowLessonProgress(enabled: Boolean) {
        repository.setShowLessonProgress(enabled)
    }

    fun setAutoScrollToCurrentLesson(enabled: Boolean) {
        repository.setAutoScrollToCurrentLesson(enabled)
    }

    fun setThemeOverlay(overlay: ThemeOverlay) {
        repository.setThemeOverlay(overlay)
    }

    fun setSakuraTheme(enabled: Boolean) {
        repository.setSakuraTheme(enabled)
    }

    fun setCyberpunkTheme(enabled: Boolean) {
        repository.setCyberpunkTheme(enabled)
    }

    fun setMatrixTheme(enabled: Boolean) = repository.setMatrixTheme(enabled)
    fun setCheatsAgreed(agreed: Boolean?) = repository.setCheatsAgreed(agreed)
    fun setCheatsBlocked(blocked: Boolean) = repository.setCheatsBlocked(blocked)

    fun setDockTabs(tabs: List<AppTab>) {
        repository.setDockTabs(tabs)
    }

    private val _activeSubScreen = MutableStateFlow(OtherSubScreen.ROOT)
    val activeSubScreen: StateFlow<OtherSubScreen> = _activeSubScreen.asStateFlow()

    fun navigateToSubScreen(subScreen: OtherSubScreen) {
        _activeSubScreen.value = subScreen
    }

    fun resetToRoot() {
        _activeSubScreen.value = OtherSubScreen.ROOT
    }

    private val _storageStats = MutableStateFlow(repository.getStorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    fun refreshStorageStats() {
        _storageStats.value = repository.getStorageStats()
    }

    private val _contributors = MutableStateFlow<List<com.jetbrains.kmpapp.data.model.GitHubContributor>>(
        listOf(
            com.jetbrains.kmpapp.data.model.GitHubContributor(
                login = "KiraSunshine",
                htmlUrl = "https://github.com/KiraSunshine",
                avatarUrl = "https://avatars.githubusercontent.com/KiraSunshine",
                contributions = 1,
                role = "Создатель и ведущий разработчик"
            )
        )
    )
    val contributors: StateFlow<List<com.jetbrains.kmpapp.data.model.GitHubContributor>> = _contributors.asStateFlow()

    private val _isLoadingContributors = MutableStateFlow(false)
    val isLoadingContributors: StateFlow<Boolean> = _isLoadingContributors.asStateFlow()

    fun loadContributors() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _isLoadingContributors.value = true
                val fetched = updateChecker.fetchContributors(forceRefresh = true)
                val staticLead = com.jetbrains.kmpapp.data.model.GitHubContributor(
                    login = "KiraSunshine",
                    htmlUrl = "https://github.com/KiraSunshine",
                    avatarUrl = "https://avatars.githubusercontent.com/KiraSunshine",
                    contributions = 1,
                    role = "Создатель и ведущий разработчик"
                )
                val otherContributors = fetched.filterNot {
                    it.login.equals("KiraSunshine", ignoreCase = true)
                }
                _contributors.value = listOf(staticLead) + otherContributors
            } catch (t: Throwable) {
                println("Failed to load contributors: ${t.message}")
            } finally {
                _isLoadingContributors.value = false
            }
        }
    }

    private val _updateResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateResult: StateFlow<UpdateCheckResult?> = _updateResult.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    fun setBetaChannel(enabled: Boolean) {
        repository.setBetaChannel(enabled)
    }

    fun setShareAttendanceEnabled(enabled: Boolean) {
        repository.setShareAttendanceEnabled(enabled)
    }

    fun checkForUpdates() {
        // Update checks are disabled for the Dongau fork.
    }

    fun dismissUpdateDialog() {
        _updateResult.value = null
        _updateStatusMessage.value = null
    }

    // Search, filter, and sort state for ManageSchedulesScreen
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow<ScheduleTargetType?>(null)
    val filterType: StateFlow<ScheduleTargetType?> = _filterType.asStateFlow()

    private val _sortOrder = MutableStateFlow(TargetSortOrder.TITLE_ASC)
    val sortOrder: StateFlow<TargetSortOrder> = _sortOrder.asStateFlow()

    val filteredSavedTargets: StateFlow<List<ScheduleTarget>> = combine(
        repository.savedTargets,
        _searchQuery,
        _filterType,
        _sortOrder
    ) { list, query, filter, sort ->
        var result = list
        if (filter != null) {
            result = result.filter { it.type == filter }
        }
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            result = result.filter {
                it.targetTitle.contains(trimmed, ignoreCase = true) ||
                it.fullTitle.contains(trimmed, ignoreCase = true)
            }
        }
        when (sort) {
            TargetSortOrder.TITLE_ASC -> result.sortedBy { it.targetTitle.lowercase() }
            TargetSortOrder.TITLE_DESC -> result.sortedByDescending { it.targetTitle.lowercase() }
            TargetSortOrder.RECENT -> result
            TargetSortOrder.OLDEST -> result.reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: ScheduleTargetType?) {
        _filterType.value = type
    }

    fun setSortOrder(order: TargetSortOrder) {
        _sortOrder.value = order
    }

    fun toggleSortDirection() {
        _sortOrder.value = when (_sortOrder.value) {
            TargetSortOrder.TITLE_ASC -> TargetSortOrder.TITLE_DESC
            TargetSortOrder.TITLE_DESC -> TargetSortOrder.TITLE_ASC
            TargetSortOrder.RECENT -> TargetSortOrder.OLDEST
            TargetSortOrder.OLDEST -> TargetSortOrder.RECENT
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        repository.setThemeMode(mode)
    }

    fun setShowEmptyLessons(enabled: Boolean) {
        repository.setShowEmptyLessons(enabled)
    }

    fun setShowAbbreviatedNames(enabled: Boolean) {
        repository.setShowAbbreviatedNames(enabled)
    }

    fun selectTarget(target: ScheduleTarget) {
        repository.selectTarget(target)
    }

    fun removeTarget(targetId: Int) {
        repository.removeTarget(targetId)
    }

    fun refreshSchedule() {
        repository.refreshCurrentSchedule()
    }

    fun clearCache() {
        repository.clearCache()
        _storageStats.value = repository.getStorageStats()
    }

    fun resetAllData() {
        taskRepository.clearAllData()
        repository.resetAllData()
        DebugConfig.reset()
        _storageStats.value = repository.getStorageStats()
    }

    fun setReminderSettings(settings: ReminderSettings) {
        repository.setReminderSettings(settings)
    }

    fun setSubjectColor(subjectKey: String, colorHex: String?) {
        repository.setSubjectColor(subjectKey, colorHex)
    }

    fun addCustomSubjectColor(colorHex: String) {
        repository.addCustomSubjectColor(colorHex)
    }

    fun removeCustomSubjectColor(colorHex: String) {
        repository.removeCustomSubjectColor(colorHex)
    }

    fun openUrl(url: String) {
        PlatformShare.openUrl(url)
    }

    fun shareCustomLessons() {
        viewModelScope.launch {
            val custom = repository.customLessons.first()
            val extra = repository.extraLessons.first()
            val targets = repository.savedTargets.first().associateBy { it.id }
            val text = buildCustomLessonsShareText(custom, extra, targets)
            PlatformShare.shareText("...", text)
        }
    }

    fun shareAttendancePlans() {
        viewModelScope.launch {
            if (!repository.shareAttendanceEnabled.first()) return@launch
            val lessons = repository.currentLessons.first()
            val rules = repository.attendanceRules.first()
            val text = buildAttendancePlansShareText(lessons, rules)
            PlatformShare.shareText("Планы посещения", text)
        }
    }

    private fun buildCustomLessonsShareText(
        custom: List<CustomLesson>,
        extra: List<ExtraLesson>,
        targets: Map<Int, ScheduleTarget>
    ): String {
        if (custom.isEmpty() && extra.isEmpty()) return "Мои созданные пары: нет созданных пар."
        val sb = StringBuilder()
        sb.appendLine("Мои созданные пары:")

        if (custom.isNotEmpty()) {
            fun teacherKey(lesson: CustomLesson) = lesson.teachers.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.sorted().joinToString(",")
            val grouped = custom.groupBy { Triple(it.targetId, it.subject.trim().lowercase(), teacherKey(it)) }
                .toList()
                .sortedWith(compareBy({ it.first.first }, { it.first.second }))
            grouped.forEachIndexed { index, (_, lessons) ->
                val first = lessons.first()
                val targetName = targets[first.targetId]?.targetTitle?.let { " [$it]" } ?: ""
                sb.append("${index + 1}. ${first.subject}$targetName")
                if (first.teachers.isNotEmpty()) sb.append(" — преп.: ${first.teachers.joinToString(", ")}")
                if (first.classrooms.isNotEmpty()) sb.append(", ауд.: ${first.classrooms.joinToString(", ")}")
                if (first.groups.isNotEmpty()) sb.append(", гр.: ${first.groups.joinToString(", ")}")
                sb.appendLine()
                val details = lessons.sortedWith(compareBy({ if (it.isRecurring) 0 else 1 }, { it.date ?: it.anchorDate }, { it.dayOfWeek ?: 0 }, { it.bellNumber }))
                    .map { lesson ->
                        val base = "${lesson.lessonType.displayName}, ${lesson.bellNumber} пара ${lesson.startTime}-${lesson.endTime}"
                        if (lesson.isRecurring) {
                            val dow = lesson.dayOfWeek ?: 1
                            val dayName = DateUtils.formatDayOfWeekShort(kotlinx.datetime.DayOfWeek(dow))
                            val period = when (lesson.intervalWeeks.coerceAtLeast(1)) {
                                1 -> "..."
                                2 -> "через 2 недели"
                                else -> "через ${lesson.intervalWeeks} нед."
                            }
                            val sem = DateUtils.semesterBounds(lesson.createdSemesterKey)?.let { (st, en) -> " (${st}-${en})" } ?: ""
                            val keep = if (lesson.keepEverySemester) "" else ""
                            "$base, $dayName, $period$sem$keep"
                        } else {
                            "$base, дата: ${lesson.date ?: "-"}"
                        }
                    }.distinct()
                details.forEach { sb.appendLine("   • $it") }
            }
        }

        if (extra.isNotEmpty()) {
            if (custom.isNotEmpty()) sb.appendLine()
            sb.appendLine("Внеурочные пары (отдельный класс):")
            fun teacherKeyE(lesson: ExtraLesson) = lesson.teachers.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.sorted().joinToString(",")
            val grouped = extra.groupBy { Triple(it.targetId, it.subject.trim().lowercase(), teacherKeyE(it)) }
                .toList()
                .sortedWith(compareBy({ it.first.first }, { it.first.second }))
            grouped.forEachIndexed { index, (_, lessons) ->
                val first = lessons.first()
                val targetName = targets[first.targetId]?.targetTitle?.let { " [$it]" } ?: ""
                sb.append("${index + 1}. ${first.subject}$targetName")
                if (first.teachers.isNotEmpty()) sb.append(" — преп.: ${first.teachers.joinToString(", ")}")
                sb.appendLine()
                val details = lessons.sortedWith(compareBy({ if (it.isRecurring) 0 else 1 }, { it.date ?: it.anchorDate }, { it.dayOfWeek ?: 0 }, { it.bellNumber }))
                    .map { lesson ->
                        val base = "${lesson.lessonType.displayName}, ${lesson.bellNumber} пара ${lesson.startTime}-${lesson.endTime}"
                        if (lesson.isRecurring) {
                            val dow = lesson.dayOfWeek ?: 1
                            val dayName = DateUtils.formatDayOfWeekShort(kotlinx.datetime.DayOfWeek(dow))
                            val period = when (lesson.intervalWeeks.coerceAtLeast(1)) { 1 -> "..."; 2 -> "через 2 недели"; else -> "через ${lesson.intervalWeeks} нед." }
                            "$base, $dayName, $period"
                        } else {
                            "$base, дата: ${lesson.date ?: "-"}"
                        }
                    }.distinct()
                details.forEach { sb.appendLine("   • $it") }
            }
        }
        return sb.toString().trim()
    }

    private fun buildAttendancePlansShareText(
        lessons: List<com.jetbrains.kmpapp.data.model.Lesson>,
        rules: List<com.jetbrains.kmpapp.data.model.AttendanceRule>
    ): String {
        if (rules.isEmpty()) return "Нет отмеченных планов посещения."
        val today = DateUtils.today()
        val horizon = today.plus(DatePeriod(days = 60))
        val relevant = lessons
            .filter { it.date in today..horizon }
            .sortedWith(compareBy({ it.date }, { it.bellNumber }))
        val attending = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        relevant.forEach { lesson ->
            val status = rules.statusFor(lesson) ?: return@forEach
            val line = "${lesson.date} ${DateUtils.formatDayOfWeekShort(lesson.date.dayOfWeek)} ${lesson.startTime}–${lesson.endTime}, ${lesson.bellNumber} пара — ${lesson.subject}" +
                (lesson.classrooms.firstOrNull()?.let { " ($it)" } ?: "")
            when (status) {
                AttendanceStatus.ATTENDING -> attending += "Пойду: $line"
                AttendanceStatus.SKIPPED -> skipped += "Не пойду: $line"
                else -> {}
            }
        }
        if (attending.isEmpty() && skipped.isEmpty()) {
            return "Нет отмеченных планов посещения на ближайшие дни."
        }
        val sb = StringBuilder()
        sb.appendLine("Планы посещения (ближайшие 60 дней):")
        if (attending.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Пойду:")
            attending.forEach { sb.appendLine("• ${it.removePrefix("Пойду: ")}") }
        }
        if (skipped.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Не пойду:")
            skipped.forEach { sb.appendLine("• ${it.removePrefix("Не пойду: ")}") }
        }
        return sb.toString().trim()
    }

    suspend fun fetchLessonsForTarget(target: ScheduleTarget) = repository.fetchLessonsForTarget(target)

    suspend fun search(query: String): List<ScheduleTarget> {
        return repository.search(query)
    }

    fun addAndSelectTarget(target: ScheduleTarget) {
        repository.addAndSelectTarget(target)
    }
}
