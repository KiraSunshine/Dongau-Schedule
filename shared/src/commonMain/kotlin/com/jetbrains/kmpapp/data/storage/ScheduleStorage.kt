package com.jetbrains.kmpapp.data.storage

import com.jetbrains.kmpapp.data.model.AttendanceRule
import com.jetbrains.kmpapp.data.model.CustomLesson
import com.jetbrains.kmpapp.data.model.ExtraLesson
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonNote
import com.jetbrains.kmpapp.data.model.LessonVisit
import com.jetbrains.kmpapp.data.model.ReminderSettings
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.SubjectNote
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.data.model.isExpired
import com.jetbrains.kmpapp.data.model.lessonNoteKey
import com.jetbrains.kmpapp.data.model.lessonVisitKey
import com.jetbrains.kmpapp.data.model.seriesKey
import com.jetbrains.kmpapp.data.model.subjectNoteKey
import com.jetbrains.kmpapp.screens.components.AppTab
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ScheduleStorage(
    private val platformStorage: PlatformStorage
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _savedTargets = MutableStateFlow<List<ScheduleTarget>>(emptyList())
    val savedTargets: StateFlow<List<ScheduleTarget>> = _savedTargets.asStateFlow()

    private val _selectedTarget = MutableStateFlow<ScheduleTarget?>(null)
    val selectedTarget: StateFlow<ScheduleTarget?> = _selectedTarget.asStateFlow()

    private val _cachedLessons = MutableStateFlow<Map<Int, List<Lesson>>>(emptyMap())
    val cachedLessons: StateFlow<Map<Int, List<Lesson>>> = _cachedLessons.asStateFlow()

    private val _showEmptyLessons = MutableStateFlow<Boolean>(true)
    val showEmptyLessons: StateFlow<Boolean> = _showEmptyLessons.asStateFlow()

    private val _showLessonProgress = MutableStateFlow<Boolean>(true)
    val showLessonProgress: StateFlow<Boolean> = _showLessonProgress.asStateFlow()

    private val _autoScrollToCurrentLesson = MutableStateFlow<Boolean>(true)
    val autoScrollToCurrentLesson: StateFlow<Boolean> = _autoScrollToCurrentLesson.asStateFlow()

    private val _showAbbreviatedNames = MutableStateFlow<Boolean>(false)
    val showAbbreviatedNames: StateFlow<Boolean> = _showAbbreviatedNames.asStateFlow()

    private val _attendanceRules = MutableStateFlow<List<AttendanceRule>>(emptyList())
    val attendanceRules: StateFlow<List<AttendanceRule>> = _attendanceRules.asStateFlow()

    private val _themeMode = MutableStateFlow<ThemeMode>(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dockTabs = MutableStateFlow<List<AppTab>>(DEFAULT_DOCK_TABS)
    val dockTabs: StateFlow<List<AppTab>> = _dockTabs.asStateFlow()

    private val _themeOverlay = MutableStateFlow(ThemeOverlay.NONE)
    val themeOverlay: StateFlow<ThemeOverlay> = _themeOverlay.asStateFlow()

    val isSakuraTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.SAKURA }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isCyberpunkTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.CYBERPUNK }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isMatrixTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.MATRIX }
        .stateIn(scope, SharingStarted.Eagerly, false)
    private val _cheatsAgreed = MutableStateFlow<Boolean?>(null)
    val cheatsAgreed: StateFlow<Boolean?> = _cheatsAgreed.asStateFlow()
    private val _cheatsBlocked = MutableStateFlow(false)
    val cheatsBlocked: StateFlow<Boolean> = _cheatsBlocked.asStateFlow()

    private val _betaChannel = MutableStateFlow(false)
    val betaChannel: StateFlow<Boolean> = _betaChannel.asStateFlow()

    private val _shareAttendanceEnabled = MutableStateFlow(false)
    val shareAttendanceEnabled: StateFlow<Boolean> = _shareAttendanceEnabled.asStateFlow()

    private val _lessonNotes = MutableStateFlow<Map<String, LessonNote>>(emptyMap())
    val lessonNotes: StateFlow<Map<String, LessonNote>> = _lessonNotes.asStateFlow()

    private val _subjectNotes = MutableStateFlow<Map<String, SubjectNote>>(emptyMap())
    val subjectNotes: StateFlow<Map<String, SubjectNote>> = _subjectNotes.asStateFlow()

        private val _reminderSettings = MutableStateFlow(ReminderSettings())
    val reminderSettings: StateFlow<ReminderSettings> = _reminderSettings.asStateFlow()

    private val _customLessons = MutableStateFlow<List<CustomLesson>>(emptyList())
    val customLessons: StateFlow<List<CustomLesson>> = _customLessons.asStateFlow()

    private val _extraLessons = MutableStateFlow<List<ExtraLesson>>(emptyList())
    val extraLessons: StateFlow<List<ExtraLesson>> = _extraLessons.asStateFlow()

    /** . */
    private val _lessonVisits = MutableStateFlow<Map<String, LessonVisit>>(emptyMap())
    val lessonVisits: StateFlow<Map<String, LessonVisit>> = _lessonVisits.asStateFlow()

    /** . */
    private val _subjectColorOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val subjectColorOverrides: StateFlow<Map<String, String>> = _subjectColorOverrides.asStateFlow()

    /** . */
    private val _customSubjectColors = MutableStateFlow<List<String>>(emptyList())
    val customSubjectColors: StateFlow<List<String>> = _customSubjectColors.asStateFlow()

    private val lastSyncTimes = mutableMapOf<Int, Long>()

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        try {
            // Restore theme mode setting
            try {
                val themeStr = platformStorage.getString(KEY_APP_THEME)
                if (!themeStr.isNullOrBlank()) {
                    _themeMode.value = try {
                        ThemeMode.valueOf(themeStr)
                    } catch (_: Throwable) {
                        ThemeMode.SYSTEM
                    }
                }
            } catch (_: Throwable) {}

            // Restore show empty lessons setting
            try {
                val showEmptyStr = platformStorage.getString(KEY_SHOW_EMPTY_LESSONS)
                if (!showEmptyStr.isNullOrBlank()) {
                    _showEmptyLessons.value = showEmptyStr.toBooleanStrictOrNull() ?: true
                }
            } catch (_: Throwable) {}

            // Restore show lesson progress setting
            try {
                val showProgressStr = platformStorage.getString(KEY_SHOW_LESSON_PROGRESS)
                if (!showProgressStr.isNullOrBlank()) {
                    _showLessonProgress.value = showProgressStr.toBooleanStrictOrNull() ?: true
                }
            } catch (_: Throwable) {}

            // Restore auto scroll to current lesson setting
            try {
                val autoScrollStr = platformStorage.getString(KEY_AUTO_SCROLL_CURRENT_LESSON)
                if (!autoScrollStr.isNullOrBlank()) {
                    _autoScrollToCurrentLesson.value = autoScrollStr.toBooleanStrictOrNull() ?: true
                }
            } catch (_: Throwable) {}

            // Restore show abbreviated names setting
            try {
                val showAbbreviatedStr = platformStorage.getString(KEY_SHOW_ABBREVIATED_NAMES)
                if (!showAbbreviatedStr.isNullOrBlank()) {
                    _showAbbreviatedNames.value = showAbbreviatedStr.toBooleanStrictOrNull() ?: false
                }
            } catch (_: Throwable) {}

            // Restore attendance rules
            try {
                val rulesJson = platformStorage.getString(KEY_ATTENDANCE_RULES)
                if (!rulesJson.isNullOrBlank()) {
                    _attendanceRules.value = try {
                        json.decodeFromString<List<AttendanceRule>>(rulesJson)
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
            } catch (_: Throwable) {}

            // Restore dock tabs setting
            try {
                val dockTabsStr = platformStorage.getString(KEY_DOCK_TABS)
                if (!dockTabsStr.isNullOrBlank()) {
                    val loaded = dockTabsStr.split(",").mapNotNull { raw ->
                        val name = raw.trim()
                        when (name) {
                            "FREE_ROOMS", "ROOMS" -> AppTab.PROGRESS
                            else -> try { AppTab.valueOf(name) } catch (_: Throwable) { null }
                        }
                    }
                    val previousDefault = listOf(AppTab.SCHEDULE, AppTab.TASKS, AppTab.OTHER)
                    if (loaded == previousDefault) {
                        _dockTabs.value = DEFAULT_DOCK_TABS
                    } else {
                        _dockTabs.value = sanitizeDockTabs(loaded)
                    }
                } else {
                    _dockTabs.value = DEFAULT_DOCK_TABS
                }
            } catch (_: Throwable) {
                _dockTabs.value = DEFAULT_DOCK_TABS
            }

            _themeOverlay.value = loadThemeOverlay()
            _cheatsAgreed.value = platformStorage.getString(KEY_CHEATS_AGREED)?.toBooleanStrictOrNull()
            _cheatsBlocked.value = platformStorage.getString(KEY_CHEATS_BLOCKED)?.toBooleanStrictOrNull() ?: false
            _betaChannel.value =
                platformStorage.getString(KEY_BETA_CHANNEL)?.toBooleanStrictOrNull() ?: false
            _shareAttendanceEnabled.value =
                platformStorage.getString(KEY_SHARE_ATTENDANCE_ENABLED)?.toBooleanStrictOrNull() ?: false

            // Restore notes
            try {
                val lessonNotesJson = platformStorage.getString(KEY_LESSON_NOTES)
                if (!lessonNotesJson.isNullOrBlank()) {
                    _lessonNotes.value = try {
                        json.decodeFromString<Map<String, LessonNote>>(lessonNotesJson)
                    } catch (_: Throwable) { emptyMap() }
                }
            } catch (_: Throwable) {}
            try {
                val subjectNotesJson = platformStorage.getString(KEY_SUBJECT_NOTES)
                if (!subjectNotesJson.isNullOrBlank()) {
                    _subjectNotes.value = try {
                        json.decodeFromString<Map<String, SubjectNote>>(subjectNotesJson)
                    } catch (_: Throwable) { emptyMap() }
                }
            } catch (_: Throwable) {}

            // Restore reminder settings
            try {
                val reminderJson = platformStorage.getString(KEY_REMINDER_SETTINGS)
                if (!reminderJson.isNullOrBlank()) {
                    _reminderSettings.value = try {
                        json.decodeFromString<ReminderSettings>(reminderJson)
                    } catch (_: Throwable) { ReminderSettings() }
                }
            } catch (_: Throwable) {}

            // Restore custom lessons and purge expired by semester
            try {
                val customJson = platformStorage.getString(KEY_CUSTOM_LESSONS)
                if (!customJson.isNullOrBlank()) {
                    val loaded = try {
                        json.decodeFromString<List<CustomLesson>>(customJson)
                    } catch (_: Throwable) { emptyList() }
                    val currentSem = DateUtils.currentSemesterKey()
                    val filtered = loaded.filterNot { it.isExpired(currentSem) }
                    _customLessons.value = filtered
                    if (filtered.size != loaded.size) persistCustomLessons()
                }
            } catch (_: Throwable) {}


            // Restore extra (out-of-schedule) lessons
            try {
                val extraJson = platformStorage.getString(KEY_EXTRA_LESSONS)
                if (!extraJson.isNullOrBlank()) {
                    _extraLessons.value = try {
                        json.decodeFromString<List<ExtraLesson>>(extraJson)
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
            } catch (_: Throwable) {}

            // Restore lesson visits (actual attendance facts)
            try {
                val visitsJson = platformStorage.getString(KEY_LESSON_VISITS)
                if (!visitsJson.isNullOrBlank()) {
                    _lessonVisits.value = try {
                        json.decodeFromString<Map<String, LessonVisit>>(visitsJson)
                    } catch (_: Throwable) {
                        emptyMap()
                    }
                }
            } catch (_: Throwable) {}

            // Restore subject color overrides and custom colors
            try {
                val subjectColorsJson = platformStorage.getString(KEY_SUBJECT_COLOR_OVERRIDES)
                if (!subjectColorsJson.isNullOrBlank()) {
                    _subjectColorOverrides.value = try {
                        json.decodeFromString<Map<String, String>>(subjectColorsJson)
                    } catch (_: Throwable) {
                        emptyMap()
                    }
                }
                val customColorsJson = platformStorage.getString(KEY_CUSTOM_SUBJECT_COLORS)
                if (!customColorsJson.isNullOrBlank()) {
                    _customSubjectColors.value = try {
                        json.decodeFromString<List<String>>(customColorsJson)
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
            } catch (_: Throwable) {}

            // Restore saved targets
            val targets: List<ScheduleTarget> = try {
                val targetsJson = platformStorage.getString(KEY_SAVED_TARGETS)
                if (!targetsJson.isNullOrBlank()) {
                    try { json.decodeFromString(targetsJson) } catch (_: Throwable) { emptyList() }
                } else {
                    emptyList()
                }
            } catch (_: Throwable) {
                emptyList()
            }
            _savedTargets.value = targets

            // IMPORTANT: Restore cached lessons for all targets BEFORE setting selected target!
            val loadedCache = mutableMapOf<Int, List<Lesson>>()
            for (target in targets) {
                try {
                    val lessonsJson = platformStorage.getString(KEY_LESSONS_PREFIX + target.id)
                    if (!lessonsJson.isNullOrBlank()) {
                        try {
                            val lessons: List<Lesson> = json.decodeFromString(lessonsJson)
                            loadedCache[target.id] = lessons
                        } catch (_: Throwable) {}
                    }
                    val syncTimeStr = platformStorage.getString(KEY_LAST_SYNC_PREFIX + target.id)
                    syncTimeStr?.toLongOrNull()?.let { lastSyncTimes[target.id] = it }
                } catch (_: Throwable) {}
            }
            _cachedLessons.value = loadedCache

            // Now that cached lessons and timestamps are ready, restore selected target!
            try {
                val activeIdStr = platformStorage.getString(KEY_SELECTED_TARGET_ID)
                val activeId = activeIdStr?.toIntOrNull()
                val selected = targets.firstOrNull { it.id == activeId } ?: targets.firstOrNull()
                _selectedTarget.value = selected
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            println("ScheduleStorage: failed to load persisted state: ${t.message}")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        scope.launch {
            try {
                platformStorage.saveString(KEY_APP_THEME, mode.name)
            } catch (e: Exception) {
                println("Failed to persist themeMode: ${e.message}")
            }
        }
    }

    fun setShowEmptyLessons(enabled: Boolean) {
        _showEmptyLessons.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_SHOW_EMPTY_LESSONS, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist showEmptyLessons: ${e.message}")
            }
        }
    }

    fun setShowLessonProgress(enabled: Boolean) {
        _showLessonProgress.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_SHOW_LESSON_PROGRESS, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist showLessonProgress: ${e.message}")
            }
        }
    }

    fun setAutoScrollToCurrentLesson(enabled: Boolean) {
        _autoScrollToCurrentLesson.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_AUTO_SCROLL_CURRENT_LESSON, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist autoScrollToCurrentLesson: ${e.message}")
            }
        }
    }

    fun setShowAbbreviatedNames(enabled: Boolean) {
        _showAbbreviatedNames.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_SHOW_ABBREVIATED_NAMES, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist showAbbreviatedNames: ${e.message}")
            }
        }
    }

    /**
     * Сохраняет отметку посещаемости для серии занятий.
     * Старые правила этой же серии заменяются новым.
     */
    fun setAttendanceRule(rule: AttendanceRule) {
        _attendanceRules.update { current ->
            current.filterNot { it.seriesKey == rule.seriesKey } + rule
        }
        persistAttendanceRules()
    }

    /** Убирает все отметки для серии занятия. */
    fun clearAttendanceRule(lesson: Lesson) {
        val key = lesson.seriesKey()
        _attendanceRules.update { current -> current.filterNot { it.seriesKey == key } }
        persistAttendanceRules()
    }

    fun resetAttendanceRules() {
        _attendanceRules.value = emptyList()
        scope.launch {
            try {
                platformStorage.remove(KEY_ATTENDANCE_RULES)
            } catch (_: Throwable) {}
        }
    }

    private fun persistAttendanceRules() {
        scope.launch {
            try {
                platformStorage.saveString(
                    KEY_ATTENDANCE_RULES,
                    json.encodeToString(_attendanceRules.value)
                )
            } catch (e: Exception) {
                println("Failed to persist attendance rules: ${e.message}")
            }
        }
    }

    // --- Notes ---

    fun getLessonNote(targetId: Int, date: kotlinx.datetime.LocalDate, bellNumber: Int): LessonNote? =
        _lessonNotes.value[lessonNoteKey(targetId, date, bellNumber)]

    fun setLessonNote(note: LessonNote) {
        val key = lessonNoteKey(note.targetId, note.date, note.bellNumber)
        _lessonNotes.update { map ->
            if (note.text.isBlank()) map - key else map + (key to note)
        }
        persistLessonNotes()
    }

    fun removeLessonNote(targetId: Int, date: kotlinx.datetime.LocalDate, bellNumber: Int) {
        val key = lessonNoteKey(targetId, date, bellNumber)
        _lessonNotes.update { it - key }
        persistLessonNotes()
    }

    fun getSubjectNote(targetId: Int, subject: String): SubjectNote? =
        _subjectNotes.value[subjectNoteKey(targetId, subject)]

    fun setSubjectNote(note: SubjectNote) {
        val key = subjectNoteKey(note.targetId, note.subjectKey.ifBlank { note.subjectDisplayName })
        val normalized = note.copy(subjectKey = note.subjectKey.ifBlank { note.subjectDisplayName.trim().lowercase() })
        val finalKey = subjectNoteKey(normalized.targetId, normalized.subjectKey)
        _subjectNotes.update { map ->
            if (normalized.text.isBlank()) map - finalKey else map + (finalKey to normalized)
        }
        persistSubjectNotes()
    }

    fun removeSubjectNote(targetId: Int, subject: String) {
        val key = subjectNoteKey(targetId, subject)
        _subjectNotes.update { it - key }
        persistSubjectNotes()
    }

    fun clearNotesForTarget(targetId: Int) {
        _lessonNotes.update { map -> map.filterValues { it.targetId != targetId } }
        _subjectNotes.update { map -> map.filterValues { it.targetId != targetId } }
        _lessonVisits.update { map -> map.filterValues { it.targetId != targetId } }
        persistLessonNotes()
        persistSubjectNotes()
        persistLessonVisits()
    }

    fun clearPastLessonNotes(today: kotlinx.datetime.LocalDate) {
        _lessonNotes.update { map -> map.filterValues { it.date >= today } }
        persistLessonNotes()
    }

    private fun persistLessonNotes() {
        scope.launch {
            try {
                platformStorage.saveString(KEY_LESSON_NOTES, json.encodeToString(_lessonNotes.value))
            } catch (e: Exception) {
                println("Failed to persist lesson notes: ${e.message}")
            }
        }
    }

    private fun persistSubjectNotes() {
        scope.launch {
            try {
                platformStorage.saveString(KEY_SUBJECT_NOTES, json.encodeToString(_subjectNotes.value))
            } catch (e: Exception) {
                println("Failed to persist subject notes: ${e.message}")
            }
        }
    }

    // --- Lesson visits (actual attendance facts) ---

    fun setLessonVisit(visit: LessonVisit) {
        val key = lessonVisitKey(visit.targetId, visit.date, visit.bellNumber)
        _lessonVisits.update { map -> map + (key to visit) }
        persistLessonVisits()
    }

    fun removeLessonVisit(targetId: Int, date: kotlinx.datetime.LocalDate, bellNumber: Int) {
        val key = lessonVisitKey(targetId, date, bellNumber)
        _lessonVisits.update { map -> map - key }
        persistLessonVisits()
    }

    fun clearLessonVisitsForSubject(targetId: Int, subjectKey: String) {
        val normalized = subjectKey.trim().lowercase()
        _lessonVisits.update { map -> map.filterValues { !(it.targetId == targetId && it.subjectKey.trim().lowercase() == normalized) } }
        persistLessonVisits()
    }

    private fun persistLessonVisits() {
        scope.launch {
            try {
                platformStorage.saveString(KEY_LESSON_VISITS, json.encodeToString(_lessonVisits.value))
            } catch (e: Exception) {
                println("Failed to persist lesson visits: ${e.message}")
            }
        }
    }

    // --- Reminders ---

    fun setReminderSettings(settings: ReminderSettings) {
        _reminderSettings.value = settings.copy(minutesBefore = settings.clampedMinutes)
        scope.launch {
            try {
                platformStorage.saveString(KEY_REMINDER_SETTINGS, json.encodeToString(_reminderSettings.value))
            } catch (e: Exception) {
                println("Failed to persist reminder settings: ${e.message}")
            }
        }
    }

    // --- Custom lessons ---

    fun addCustomLesson(lesson: CustomLesson) {
        addCustomLessons(listOf(lesson))
    }

    fun addCustomLessons(lessons: List<CustomLesson>) {
        if (lessons.isEmpty()) return
        _customLessons.update { it + lessons }
        persistCustomLessons()
    }

    fun updateCustomLesson(lesson: CustomLesson) {
        _customLessons.update { list -> list.map { if (it.id == lesson.id) lesson else it } }
        persistCustomLessons()
    }

    fun removeCustomLesson(id: String) {
        _customLessons.update { list -> list.filterNot { it.id == id } }
        persistCustomLessons()
    }

    fun removeCustomLessonsForTarget(targetId: Int) {
        _customLessons.update { list -> list.filterNot { it.targetId == targetId } }
        persistCustomLessons()
    }

    private fun persistCustomLessons() {
        scope.launch {
            try {
                platformStorage.saveString(KEY_CUSTOM_LESSONS, json.encodeToString(_customLessons.value))
            } catch (e: Exception) {
                println("Failed to persist custom lessons: ${e.message}")
            }
        }
    }

    fun setThemeOverlay(overlay: ThemeOverlay) {
        _themeOverlay.value = overlay
        scope.launch {
            try {
                platformStorage.saveString(KEY_THEME_OVERLAY, overlay.name)
                platformStorage.saveString(KEY_SAKURA_THEME, (overlay == ThemeOverlay.SAKURA).toString())
                platformStorage.saveString(KEY_CYBERPUNK_THEME, (overlay == ThemeOverlay.CYBERPUNK).toString())
                platformStorage.saveString(KEY_MATRIX_THEME, (overlay == ThemeOverlay.MATRIX).toString())
            } catch (e: Exception) {
                println("Failed to persist theme overlay: ${e.message}")
            }
        }
    }

    fun setCyberpunkTheme(enabled: Boolean) {
        setThemeOverlay(if (enabled) ThemeOverlay.CYBERPUNK else ThemeOverlay.NONE)
    }

    fun setMatrixTheme(enabled: Boolean) {
        setThemeOverlay(if (enabled) ThemeOverlay.MATRIX else ThemeOverlay.NONE)
    }

    fun setCheatsAgreed(agreed: Boolean?) {
        _cheatsAgreed.value = agreed
        scope.launch {
            if (agreed == null) platformStorage.remove(KEY_CHEATS_AGREED)
            else platformStorage.saveString(KEY_CHEATS_AGREED, agreed.toString())
        }
    }

    fun setCheatsBlocked(blocked: Boolean) {
        _cheatsBlocked.value = blocked
        scope.launch { platformStorage.saveString(KEY_CHEATS_BLOCKED, blocked.toString()) }
    }

    fun setBetaChannel(enabled: Boolean) {
        _betaChannel.value = enabled
        scope.launch { platformStorage.saveString(KEY_BETA_CHANNEL, enabled.toString()) }
    }

    fun setShareAttendanceEnabled(enabled: Boolean) {
        _shareAttendanceEnabled.value = enabled
        scope.launch { platformStorage.saveString(KEY_SHARE_ATTENDANCE_ENABLED, enabled.toString()) }
    }

    fun setSakuraThemeExclusive(enabled: Boolean) {
        setThemeOverlay(if (enabled) ThemeOverlay.SAKURA else ThemeOverlay.NONE)
    }

    private fun loadThemeOverlay(): ThemeOverlay {
        val stored = platformStorage.getString(KEY_THEME_OVERLAY)
            ?.let { runCatching { ThemeOverlay.valueOf(it) }.getOrNull() }
        if (stored != null) return stored
        return when {
            platformStorage.getString(KEY_MATRIX_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.MATRIX
            platformStorage.getString(KEY_CYBERPUNK_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.CYBERPUNK
            platformStorage.getString(KEY_SAKURA_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.SAKURA
            else -> ThemeOverlay.NONE
        }
    }

    fun setDockTabs(tabs: List<AppTab>) {
        val sanitized = sanitizeDockTabs(tabs)
        _dockTabs.value = sanitized
        scope.launch {
            try {
                platformStorage.saveString(KEY_DOCK_TABS, sanitized.joinToString(",") { it.name })
            } catch (e: Exception) {
                println("Failed to persist dock tabs: ${e.message}")
            }
        }
    }

    private fun sanitizeDockTabs(tabs: List<AppTab>): List<AppTab> {
        return try {
            val middle = tabs.filter { !it.isFixed }.distinct().take(3)
            listOf(AppTab.SCHEDULE) + middle + listOf(AppTab.OTHER)
        } catch (_: Throwable) {
            DEFAULT_DOCK_TABS
        }
    }



    fun addTarget(target: ScheduleTarget) {
        _savedTargets.update { list ->
            if (list.any { it.id == target.id }) list
            else list + target
        }
        selectTarget(target)
        persistTargets()
    }

    fun removeTarget(targetId: Int) {
        _savedTargets.update { list -> list.filter { it.id != targetId } }
        if (_selectedTarget.value?.id == targetId) {
            _selectedTarget.value = _savedTargets.value.firstOrNull()
            persistSelectedTargetId(_selectedTarget.value?.id)
        }
        _cachedLessons.update { map -> map - targetId }
        lastSyncTimes.remove(targetId)
        platformStorage.remove(KEY_LESSONS_PREFIX + targetId)
        platformStorage.remove(KEY_LAST_SYNC_PREFIX + targetId)
        clearNotesForTarget(targetId)
        removeCustomLessonsForTarget(targetId)
        removeExtraLessonsForTarget(targetId)
        persistTargets()
    }

    fun selectTarget(target: ScheduleTarget?) {
        _selectedTarget.value = target
        persistSelectedTargetId(target?.id)
    }

    fun selectTargetById(targetId: Int) {
        val target = _savedTargets.value.firstOrNull { it.id == targetId }
        if (target != null) {
            selectTarget(target)
        }
    }

    fun saveLessons(targetId: Int, lessons: List<Lesson>) {
        _cachedLessons.update { map ->
            map + (targetId to lessons)
        }
        scope.launch {
            try {
                platformStorage.saveString(KEY_LESSONS_PREFIX + targetId, json.encodeToString(lessons))
            } catch (e: Exception) {
                println("Failed to persist lessons for $targetId: ${e.message}")
            }
        }
    }

    fun getLessons(targetId: Int): List<Lesson>? {
        return _cachedLessons.value[targetId]
    }

    fun getLastSyncTime(targetId: Int): Long {
        val cached = lastSyncTimes[targetId]
        if (cached != null) return cached
        val str = platformStorage.getString(KEY_LAST_SYNC_PREFIX + targetId)
        val time = str?.toLongOrNull() ?: 0L
        lastSyncTimes[targetId] = time
        return time
    }

    fun setLastSyncTime(targetId: Int, time: Long) {
        lastSyncTimes[targetId] = time
        scope.launch {
            try {
                platformStorage.saveString(KEY_LAST_SYNC_PREFIX + targetId, time.toString())
            } catch (e: Exception) {
                println("Failed to persist lastSyncTime for $targetId: ${e.message}")
            }
        }
    }

    fun clearCache() {
        _cachedLessons.value = emptyMap()
        lastSyncTimes.clear()
        for (target in _savedTargets.value) {
            platformStorage.remove(KEY_LESSONS_PREFIX + target.id)
            platformStorage.remove(KEY_LAST_SYNC_PREFIX + target.id)
        }
    }

    fun resetAllData() {
        val cheatsAgreedBefore = _cheatsAgreed.value
        val cheatsBlockedBefore = _cheatsBlocked.value
        val betaChannelBefore = _betaChannel.value
        val shareAttendanceEnabledBefore = _shareAttendanceEnabled.value
        platformStorage.clearAll()
        _savedTargets.value = emptyList()
        _selectedTarget.value = null
        _cachedLessons.value = emptyMap()
        _showEmptyLessons.value = true
        _showLessonProgress.value = true
        _autoScrollToCurrentLesson.value = true
        _showAbbreviatedNames.value = false
        _attendanceRules.value = emptyList()
        _lessonNotes.value = emptyMap()
        _subjectNotes.value = emptyMap()
        _lessonVisits.value = emptyMap()
        _reminderSettings.value = ReminderSettings()
        _customLessons.value = emptyList()
        _extraLessons.value = emptyList()
        _themeMode.value = ThemeMode.SYSTEM
        _dockTabs.value = DEFAULT_DOCK_TABS
        _themeOverlay.value = ThemeOverlay.NONE
        _cheatsAgreed.value = cheatsAgreedBefore
        _cheatsBlocked.value = cheatsBlockedBefore
        _betaChannel.value = betaChannelBefore
        _shareAttendanceEnabled.value = shareAttendanceEnabledBefore
        lastSyncTimes.clear()
        scope.launch {
            if (cheatsAgreedBefore == null) platformStorage.remove(KEY_CHEATS_AGREED)
            else platformStorage.saveString(KEY_CHEATS_AGREED, cheatsAgreedBefore.toString())
            platformStorage.saveString(KEY_CHEATS_BLOCKED, cheatsBlockedBefore.toString())
            platformStorage.saveString(KEY_BETA_CHANNEL, betaChannelBefore.toString())
            platformStorage.saveString(KEY_SHARE_ATTENDANCE_ENABLED, shareAttendanceEnabledBefore.toString())
        }
    }

    fun getStorageStats(): com.jetbrains.kmpapp.data.model.StorageStats {
        return try {
            var schedulesBytes = 0L
            var totalLessons = 0
            for ((_, lessons) in _cachedLessons.value) {
                totalLessons += lessons.size
            }
            for (target in _savedTargets.value) {
                val str = platformStorage.getString(KEY_LESSONS_PREFIX + target.id)
                if (str != null) {
                    schedulesBytes += str.encodeToByteArray().size
                }
            }

            val targetsStr = platformStorage.getString(KEY_SAVED_TARGETS)
            val targetsBytes = targetsStr?.encodeToByteArray()?.size?.toLong() ?: 0L

            var settingsBytes = 0L
            platformStorage.getString(KEY_SELECTED_TARGET_ID)?.let { settingsBytes += it.encodeToByteArray().size }
            platformStorage.getString(KEY_SHOW_EMPTY_LESSONS)?.let { settingsBytes += it.encodeToByteArray().size }
            platformStorage.getString(KEY_APP_THEME)?.let { settingsBytes += it.encodeToByteArray().size }

            val total = schedulesBytes + targetsBytes + settingsBytes

            com.jetbrains.kmpapp.data.model.StorageStats(
                schedulesSizeBytes = schedulesBytes,
                schedulesCount = _cachedLessons.value.size,
                lessonsCount = totalLessons,
                targetsSizeBytes = targetsBytes,
                targetsCount = _savedTargets.value.size,
                settingsSizeBytes = settingsBytes,
                totalSizeBytes = total
            )
        } catch (_: Throwable) {
            com.jetbrains.kmpapp.data.model.StorageStats()
        }
    }

    private fun persistTargets() {
        scope.launch {
            try {
                platformStorage.saveString(KEY_SAVED_TARGETS, json.encodeToString(_savedTargets.value))
            } catch (e: Exception) {
                println("Failed to persist targets: ${e.message}")
            }
        }
    }

    private fun persistSelectedTargetId(id: Int?) {
        scope.launch {
            if (id != null) {
                platformStorage.saveString(KEY_SELECTED_TARGET_ID, id.toString())
            } else {
                platformStorage.remove(KEY_SELECTED_TARGET_ID)
            }
        }
    }

    fun setSubjectColor(subjectKey: String, colorHex: String?) {
        val key = subjectKey.trim().lowercase()
        if (key.isBlank()) return
        val normalized = colorHex?.trim()?.takeIf { it.isNotBlank() }
        _subjectColorOverrides.update { map ->
            if (normalized == null) map - key else map + (key to normalized)
        }
        persistSubjectColorOverrides()
    }

    fun setSubjectColorOverrides(overrides: Map<String, String>) {
        _subjectColorOverrides.value = overrides.mapKeys { it.key.trim().lowercase() }
            .filterKeys { it.isNotBlank() }
            .mapValues { it.value.trim() }
            .filterValues { it.isNotBlank() }
        persistSubjectColorOverrides()
    }

    fun addCustomSubjectColor(colorHex: String) {
        val normalized = colorHex.trim()
        if (normalized.isBlank()) return
        _customSubjectColors.update { list ->
            if (list.any { it.equals(normalized, ignoreCase = true) }) list else list + normalized
        }
        persistCustomSubjectColors()
    }

    fun removeCustomSubjectColor(colorHex: String) {
        _customSubjectColors.update { list -> list.filterNot { it.equals(colorHex, ignoreCase = true) } }
        persistCustomSubjectColors()
    }

    private fun persistSubjectColorOverrides() {
        scope.launch {
            try {
                platformStorage.saveString(KEY_SUBJECT_COLOR_OVERRIDES, json.encodeToString(_subjectColorOverrides.value))
            } catch (e: Exception) {
                println("Failed to persist subject color overrides: ${e.message}")
            }
        }
    }

    private fun persistCustomSubjectColors() {
        scope.launch {
            try {
                platformStorage.saveString(KEY_CUSTOM_SUBJECT_COLORS, json.encodeToString(_customSubjectColors.value))
            } catch (e: Exception) {
                println("Failed to persist custom subject colors: ${e.message}")
            }
        }
    }


    fun addExtraLesson(lesson: ExtraLesson) {
        addExtraLessons(listOf(lesson))
    }

    fun addExtraLessons(lessons: List<ExtraLesson>) {
        if (lessons.isEmpty()) return
        _extraLessons.update { it + lessons }
        persistExtraLessons()
    }

    fun updateExtraLesson(lesson: ExtraLesson) {
        _extraLessons.update { list -> list.map { if (it.id == lesson.id) lesson else it } }
        persistExtraLessons()
    }

    fun removeExtraLesson(id: String) {
        _extraLessons.update { list -> list.filterNot { it.id == id } }
        persistExtraLessons()
    }

    fun removeExtraLessonsForTarget(targetId: Int) {
        _extraLessons.update { list -> list.filterNot { it.targetId == targetId } }
        persistExtraLessons()
    }

    private fun persistExtraLessons() {
        scope.launch {
            try {
                platformStorage.saveString(KEY_EXTRA_LESSONS, json.encodeToString(_extraLessons.value))
            } catch (e: Exception) {
                println("Failed to persist extra lessons: ${e.message}")
            }
        }
    }


    companion object {
        private const val KEY_SAVED_TARGETS = "dongau_saved_targets"
        private const val KEY_SELECTED_TARGET_ID = "dongau_selected_target_id"
        private const val KEY_LESSONS_PREFIX = "dongau_lessons_"
        private const val KEY_LAST_SYNC_PREFIX = "dongau_last_sync_"
        private const val KEY_SHOW_EMPTY_LESSONS = "dongau_show_empty_lessons"
        private const val KEY_SHOW_LESSON_PROGRESS = "dongau_show_lesson_progress"
        private const val KEY_AUTO_SCROLL_CURRENT_LESSON = "dongau_auto_scroll_current_lesson"
        private const val KEY_SHOW_ABBREVIATED_NAMES = "dongau_show_abbreviated_names"
        private const val KEY_ATTENDANCE_RULES = "dongau_attendance_rules"
        private const val KEY_LESSON_NOTES = "dongau_lesson_notes"
        private const val KEY_SUBJECT_NOTES = "dongau_subject_notes"
        private const val KEY_REMINDER_SETTINGS = "dongau_reminder_settings"
        private const val KEY_CUSTOM_LESSONS = "dongau_custom_lessons"
        private const val KEY_EXTRA_LESSONS = "dongau_extra_lessons"
        private const val KEY_LESSON_VISITS = "dongau_lesson_visits"
        private const val KEY_APP_THEME = "dongau_app_theme"
        private const val KEY_DOCK_TABS = "dongau_dock_tabs_order"
        private const val KEY_SAKURA_THEME = "dongau_sakura_theme_secret"
        private const val KEY_CYBERPUNK_THEME = "dongau_cyberpunk_theme_secret"
        private const val KEY_MATRIX_THEME = "dongau_matrix_theme_secret"
        private const val KEY_THEME_OVERLAY = "dongau_theme_overlay"
        private const val KEY_CHEATS_AGREED = "dongau_cheats_agreed"
        private const val KEY_CHEATS_BLOCKED = "dongau_cheats_blocked"
        private const val KEY_BETA_CHANNEL = "dongau_beta_channel"
        private const val KEY_SHARE_ATTENDANCE_ENABLED = "dongau_share_attendance_enabled"
        private const val KEY_SUBJECT_COLOR_OVERRIDES = "dongau_subject_color_overrides"
        private const val KEY_CUSTOM_SUBJECT_COLORS = "dongau_custom_subject_colors"
        val DEFAULT_DOCK_TABS = listOf(AppTab.SCHEDULE, AppTab.TASKS, AppTab.PROGRESS, AppTab.OTHER)
    }
}


