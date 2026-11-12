package com.jetbrains.kmpapp.data

import com.jetbrains.kmpapp.data.api.DongauScheduleApi
import com.jetbrains.kmpapp.data.model.AttendanceRule
import com.jetbrains.kmpapp.data.model.CustomLesson
import com.jetbrains.kmpapp.data.model.ExtraLesson
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonDiffItem
import com.jetbrains.kmpapp.data.model.LessonDiffType
import com.jetbrains.kmpapp.data.model.LessonNote
import com.jetbrains.kmpapp.data.model.LessonVisit
import com.jetbrains.kmpapp.data.model.ReminderSettings
import com.jetbrains.kmpapp.data.model.ScheduleDiff
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.SubjectNote
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.data.model.expandForTarget
import com.jetbrains.kmpapp.data.model.expandExtraForTarget
import com.jetbrains.kmpapp.data.parser.DongauScheduleParser
import com.jetbrains.kmpapp.data.storage.ScheduleStorage
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ScheduleRepository(
    private val api: DongauScheduleApi,
    private val storage: ScheduleStorage,
    private val powerManager: com.jetbrains.kmpapp.data.power.PlatformPowerManager,
    private val reminderManager: com.jetbrains.kmpapp.data.notifications.LessonReminderManager = com.jetbrains.kmpapp.data.notifications.NoopLessonReminderManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val isLowPowerMode: StateFlow<Boolean> = powerManager.isLowPowerMode
    val savedTargets: StateFlow<List<ScheduleTarget>> = storage.savedTargets
    val selectedTarget: StateFlow<ScheduleTarget?> = storage.selectedTarget
    val showEmptyLessons: StateFlow<Boolean> = storage.showEmptyLessons
    val themeMode: StateFlow<ThemeMode> = storage.themeMode
    val dockTabs: StateFlow<List<com.jetbrains.kmpapp.screens.components.AppTab>> = storage.dockTabs
    val lessonNotes: StateFlow<Map<String, LessonNote>> = storage.lessonNotes
    val subjectNotes: StateFlow<Map<String, SubjectNote>> = storage.subjectNotes
    val reminderSettings: StateFlow<ReminderSettings> = storage.reminderSettings
    val customLessons: StateFlow<List<CustomLesson>> = storage.customLessons
    val extraLessons: StateFlow<List<ExtraLesson>> = storage.extraLessons
    val subjectColorOverrides: StateFlow<Map<String, String>> = storage.subjectColorOverrides
    val customSubjectColors: StateFlow<List<String>> = storage.customSubjectColors
    val lessonVisits: StateFlow<Map<String, LessonVisit>> = storage.lessonVisits

    fun setDockTabs(tabs: List<com.jetbrains.kmpapp.screens.components.AppTab>) {
        storage.setDockTabs(tabs)
    }

    private val _activeDiff = MutableStateFlow<ScheduleDiff?>(null)
    val activeDiff: StateFlow<ScheduleDiff?> = _activeDiff.asStateFlow()

    fun dismissDiff() {
        _activeDiff.value = null
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _refreshStatus = MutableStateFlow<com.jetbrains.kmpapp.data.model.RefreshStatus?>(null)
    val refreshStatus: StateFlow<com.jetbrains.kmpapp.data.model.RefreshStatus?> = _refreshStatus.asStateFlow()

    fun clearRefreshStatus() {
        _refreshStatus.value = null
    }

    fun getStorageStats(): com.jetbrains.kmpapp.data.model.StorageStats = storage.getStorageStats()

    fun setShowEmptyLessons(enabled: Boolean) {
        storage.setShowEmptyLessons(enabled)
    }

    val showAbbreviatedNames: StateFlow<Boolean> = storage.showAbbreviatedNames

    fun setShowAbbreviatedNames(enabled: Boolean) {
        storage.setShowAbbreviatedNames(enabled)
    }

    val showLessonProgress: StateFlow<Boolean> = storage.showLessonProgress

    fun setShowLessonProgress(enabled: Boolean) {
        storage.setShowLessonProgress(enabled)
    }

    val autoScrollToCurrentLesson: StateFlow<Boolean> = storage.autoScrollToCurrentLesson

    fun setAutoScrollToCurrentLesson(enabled: Boolean) {
        storage.setAutoScrollToCurrentLesson(enabled)
    }

    val attendanceRules: StateFlow<List<AttendanceRule>> = storage.attendanceRules

    fun setAttendanceRule(rule: AttendanceRule) = storage.setAttendanceRule(rule)

    fun clearAttendanceRule(lesson: Lesson) = storage.clearAttendanceRule(lesson)

    fun resetAttendanceRules() = storage.resetAttendanceRules()

    fun getLessonNote(targetId: Int, date: LocalDate, bellNumber: Int): LessonNote? =
        storage.getLessonNote(targetId, date, bellNumber)

    fun setLessonNote(note: LessonNote) = storage.setLessonNote(note)

    fun removeLessonNote(targetId: Int, date: LocalDate, bellNumber: Int) =
        storage.removeLessonNote(targetId, date, bellNumber)

    fun getSubjectNote(targetId: Int, subject: String): SubjectNote? =
        storage.getSubjectNote(targetId, subject)

    fun setSubjectNote(note: SubjectNote) = storage.setSubjectNote(note)

    fun removeSubjectNote(targetId: Int, subject: String) =
        storage.removeSubjectNote(targetId, subject)

    fun clearPastLessonNotes(today: LocalDate = DateUtils.today()) =
        storage.clearPastLessonNotes(today)

    fun setReminderSettings(settings: ReminderSettings) = storage.setReminderSettings(settings)

    fun addCustomLesson(lesson: CustomLesson) = storage.addCustomLesson(lesson)

    fun addCustomLessons(lessons: List<CustomLesson>) = storage.addCustomLessons(lessons)

    fun updateCustomLesson(lesson: CustomLesson) = storage.updateCustomLesson(lesson)

    fun removeCustomLesson(id: String) = storage.removeCustomLesson(id)

    fun removeCustomLessonsForTarget(targetId: Int) = storage.removeCustomLessonsForTarget(targetId)

    fun addExtraLesson(lesson: ExtraLesson) = storage.addExtraLesson(lesson)
    fun addExtraLessons(lessons: List<ExtraLesson>) = storage.addExtraLessons(lessons)
    fun updateExtraLesson(lesson: ExtraLesson) = storage.updateExtraLesson(lesson)
    fun removeExtraLesson(id: String) = storage.removeExtraLesson(id)
    fun removeExtraLessonsForTarget(targetId: Int) = storage.removeExtraLessonsForTarget(targetId)

    fun setSubjectColor(subjectKey: String, colorHex: String?) = storage.setSubjectColor(subjectKey, colorHex)
    fun addCustomSubjectColor(colorHex: String) = storage.addCustomSubjectColor(colorHex)
    fun removeCustomSubjectColor(colorHex: String) = storage.removeCustomSubjectColor(colorHex)

    fun setLessonVisit(visit: LessonVisit) = storage.setLessonVisit(visit)

    fun removeLessonVisit(targetId: Int, date: LocalDate, bellNumber: Int) =
        storage.removeLessonVisit(targetId, date, bellNumber)

    fun clearLessonVisitsForSubject(targetId: Int, subjectKey: String) =
        storage.clearLessonVisitsForSubject(targetId, subjectKey)

    fun setThemeMode(mode: ThemeMode) {
        storage.setThemeMode(mode)
    }

    val themeOverlay: StateFlow<ThemeOverlay> = storage.themeOverlay
    val isSakuraTheme: StateFlow<Boolean> = storage.isSakuraTheme
    val isCyberpunkTheme: StateFlow<Boolean> = storage.isCyberpunkTheme
    val isMatrixTheme: StateFlow<Boolean> = storage.isMatrixTheme
    val cheatsAgreed: StateFlow<Boolean?> = storage.cheatsAgreed
    val cheatsBlocked: StateFlow<Boolean> = storage.cheatsBlocked
    val betaChannel: StateFlow<Boolean> = storage.betaChannel
    val shareAttendanceEnabled: StateFlow<Boolean> = storage.shareAttendanceEnabled

    fun setThemeOverlay(overlay: ThemeOverlay) = storage.setThemeOverlay(overlay)
    fun setMatrixTheme(enabled: Boolean) = storage.setMatrixTheme(enabled)
    fun setCheatsAgreed(agreed: Boolean?) = storage.setCheatsAgreed(agreed)
    fun setCheatsBlocked(blocked: Boolean) = storage.setCheatsBlocked(blocked)
    fun setBetaChannel(enabled: Boolean) = storage.setBetaChannel(enabled)
    fun setShareAttendanceEnabled(enabled: Boolean) = storage.setShareAttendanceEnabled(enabled)

    fun setSakuraTheme(enabled: Boolean) {
        storage.setSakuraThemeExclusive(enabled)
    }

    fun setCyberpunkTheme(enabled: Boolean) {
        storage.setCyberpunkTheme(enabled)
    }

    val currentLessons: StateFlow<List<Lesson>> = combine(
        storage.selectedTarget,
        storage.cachedLessons,
        storage.customLessons,
        storage.extraLessons
    ) { selected, cache, custom, extra ->
        if (selected == null) {
            emptyList()
        } else {
            val serverLessons = cache[selected.id] ?: emptyList()
            val today = DateUtils.today()
            val from = today.plus(DatePeriod(days = -90))
            val serverFuture = serverLessons.filter { it.date >= today }
            val lastServerDate = serverFuture.maxOfOrNull { it.date }
            val to = maxOf(today.plus(DatePeriod(days = 365)), lastServerDate ?: today)
            val customExpanded = custom.expandForTarget(selected.id, from, to)
            val extraExpanded = extra.expandExtraForTarget(selected.id, from, to)
            (serverLessons + customExpanded + extraExpanded)
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.date }, { it.bellNumber }, { it.subject }))
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())


    init {
        scope.launch {
            storage.selectedTarget.collect { target ->
                if (target != null) {
                    val cached = storage.getLessons(target.id)
                    val hasCached = cached != null && cached.isNotEmpty()
                    val lastSync = storage.getLastSyncTime(target.id)
                    val now = Clock.System.now().toEpochMilliseconds()
                    val isFresh = (now - lastSync) < 30 * 60 * 1000L // 30 minutes TTL

                    if (!hasCached) {
                        // Первая загрузка для этой цели: обязательно тянем с сервера
                        refreshSchedule(target, silent = false)
                    } else if (!isFresh) {
                        // Политика энергосбережения:
                        // если включён режим энергосбережения или приложение в фоне —
                        // не будим сеть без явной необходимости, используем кэш.
                        val shouldSkipSilentSync = powerManager.isLowPowerMode.value || !powerManager.isAppInForeground.value
                        if (!shouldSkipSilentSync) {
                            refreshSchedule(target, silent = true)
                        }
                    }
                    // Иначе кэш свежий (< 30 мин) — сетевой запрос не нужен.
                }
            }
        }

        // Перепланируем напоминания при изменении пар, посещаемости или настроек.
        // Debounce, чтобы не нагружать систему при быстрой отметке посещений.
        scope.launch {
            combine(
                currentLessons,
                storage.attendanceRules,
                storage.reminderSettings
            ) { lessons, attendance, settings ->
                Triple(lessons, attendance, settings)
            }.collectLatest { (lessons, attendance, settings) ->
                try {
                    kotlinx.coroutines.delay(600)
                    reminderManager.reschedule(lessons, attendance, settings)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    println("Reminder reschedule failed: ${t.message}")
                }
            }
        }
    }

    suspend fun search(query: String): List<ScheduleTarget> {
        return try {
            api.searchGroups(query)
        } catch (e: Exception) {
            println("DongauScheduleApi.search failed: ${e.message}")
            emptyList()
        }
    }

    /** Загрузить расписание цели без сохранения (для сравнения и т.п.). */
    suspend fun fetchLessonsForTarget(target: ScheduleTarget): List<Lesson> {
        return try {
            val response = api.getSchedule(target.type, target.id)
            DongauScheduleParser.parse(response)
        } catch (e: Exception) {
            println("fetchLessonsForTarget error for ${target.targetTitle}: ${e.message}")
            emptyList()
        }
    }

    fun addAndSelectTarget(target: ScheduleTarget) {
        storage.addTarget(target)
        scope.launch {
            refreshSchedule(target, silent = false)
        }
    }

    fun selectTarget(target: ScheduleTarget) {
        storage.selectTarget(target)
    }

    fun removeTarget(targetId: Int) {
        storage.removeTarget(targetId)
    }

    fun refreshCurrentSchedule() {
        val current = selectedTarget.value ?: return
        scope.launch {
            refreshSchedule(current, silent = false)
        }
    }

    private suspend fun refreshSchedule(target: ScheduleTarget, silent: Boolean = false) {
        if (!silent) {
            _isLoading.value = true
            _errorMessage.value = null
        }
        try {
            val response = api.getSchedule(target.type, target.id)
            val parsedLessons = DongauScheduleParser.parse(response)
            val oldLessons = storage.getLessons(target.id).orEmpty()
            val hasCachedLessons = oldLessons.isNotEmpty()
            val today = DateUtils.today()

            // Сервер вернул пустой ответ, но у нас уже есть сохранённое расписание.
            // Не затираем кэш пустым списком в этом случае.
            if (parsedLessons.isEmpty() && hasCachedLessons) {
                storage.setLastSyncTime(target.id, Clock.System.now().toEpochMilliseconds())
                _errorMessage.value = null
                if (!silent) {
                    _refreshStatus.value = com.jetbrains.kmpapp.data.model.RefreshStatus.Success(
                        message = "Сервер вернул пустой ответ — сохранён кэш"
                    )
                }
                return
            }

            // Если расписания нет и в кэше — показываем понятную ошибку.
            if (parsedLessons.isEmpty()) {
                _errorMessage.value =
                    "Сервер не вернул расписание для этой группы (пустой ответ). Попробуйте позже."
                if (!silent) {
                    _refreshStatus.value = com.jetbrains.kmpapp.data.model.RefreshStatus.Error(
                        com.jetbrains.kmpapp.data.model.AppErrorCode.ERR_NOT_FOUND,
                        message = "Сервер вернул пустое расписание"
                    )
                }
                return
            }

            // Прошедшие пары (< today) сохраняем из кэша и не затираем серверным ответом.
            // С сегодня и далее доверяем серверу (актуальные замены, переносы, отмены/добавления).
            val preservedPast = oldLessons.filter { it.date < today }
            val serverCurrent = parsedLessons.filter { it.date >= today }
            val mergedLessons = (preservedPast + serverCurrent).distinctBy { it.id }

            storage.saveLessons(target.id, mergedLessons)
            val now = Clock.System.now().toEpochMilliseconds()
            storage.setLastSyncTime(target.id, now)
            _errorMessage.value = null
            if (!silent) {
                _refreshStatus.value = com.jetbrains.kmpapp.data.model.RefreshStatus.Success()
            }

            if (hasCachedLessons) {
                val diff = computeDiff(
                    oldLessons.filter { it.date >= today },
                    serverCurrent,
                    target
                )
                if (diff != null) {
                    _activeDiff.value = diff
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            val detail = buildString {
                append(e::class.simpleName ?: "Error")
                val msg = e.message?.trim().orEmpty()
                if (msg.isNotEmpty()) append(": ").append(msg)
                val cause = e.cause
                if (cause != null && cause !== e) {
                    append(" | cause: ").append(cause::class.simpleName ?: "Cause")
                    val cmsg = cause.message?.trim().orEmpty()
                    if (cmsg.isNotEmpty()) append(": ").append(cmsg)
                }
            }
            println("refreshSchedule error for ${target.targetTitle} (${target.type}/${target.id}): $detail")
            val code = com.jetbrains.kmpapp.data.model.AppErrorCode.fromException(e)
            _refreshStatus.value = com.jetbrains.kmpapp.data.model.RefreshStatus.Error(
                code = code,
                message = "Ошибка ${code.code}: ${code.shortTitle}. $detail"
            )
            // Only show user-facing full-screen error if there is NO cached data at all
            val cached = storage.getLessons(target.id)
            val hasCache = cached != null && cached.isNotEmpty()
            if (!hasCache) {
                _errorMessage.value = "Ошибка ${code.code}: ${code.shortTitle}\n$detail"
            }
        } finally {
            if (!silent) {
                _isLoading.value = false
            }
        }
    }

    private fun computeDiff(
        oldLessons: List<Lesson>,
        newLessons: List<Lesson>,
        target: ScheduleTarget
    ): ScheduleDiff? {
        val oldMap = oldLessons.groupBy { "${it.date}_${it.bellNumber}_${it.subject.trim().lowercase()}" }
        val newMap = newLessons.groupBy { "${it.date}_${it.bellNumber}_${it.subject.trim().lowercase()}" }

        val items = mutableListOf<LessonDiffItem>()

        for ((key, newGroup) in newMap) {
            val oldGroup = oldMap[key]
            if (oldGroup == null) {
                for (lesson in newGroup) {
                    items.add(
                        LessonDiffItem(
                            type = LessonDiffType.ADDED,
                            date = lesson.date,
                            bellNumber = lesson.bellNumber,
                            subject = lesson.subject,
                            description = "Новая пара (${lesson.bellNumber} пара, ауд. ${lesson.classrooms.joinToString().ifEmpty { "—" }})"
                        )
                    )
                }
            } else {
                val oldFirst = oldGroup.first()
                val newFirst = newGroup.first()
                val changes = mutableListOf<String>()
                if (oldFirst.classrooms != newFirst.classrooms) {
                    changes.add("ауд: ${oldFirst.classrooms.joinToString().ifEmpty { "—" }} → ${newFirst.classrooms.joinToString().ifEmpty { "—" }}")
                }
                if (oldFirst.teachers != newFirst.teachers) {
                    changes.add("преп: ${oldFirst.teachers.joinToString().ifEmpty { "—" }} → ${newFirst.teachers.joinToString().ifEmpty { "—" }}")
                }
                if (oldFirst.startTime != newFirst.startTime) {
                    changes.add("время: ${oldFirst.startTime} → ${newFirst.startTime}")
                }
                if (changes.isNotEmpty()) {
                    items.add(
                        LessonDiffItem(
                            type = LessonDiffType.MODIFIED,
                            date = newFirst.date,
                            bellNumber = newFirst.bellNumber,
                            subject = newFirst.subject,
                            description = "${newFirst.bellNumber} пара: ${changes.joinToString(", ")}"
                        )
                    )
                }
            }
        }

        for ((key, oldGroup) in oldMap) {
            if (!newMap.containsKey(key)) {
                for (lesson in oldGroup) {
                    items.add(
                        LessonDiffItem(
                            type = LessonDiffType.CANCELLED,
                            date = lesson.date,
                            bellNumber = lesson.bellNumber,
                            subject = lesson.subject,
                            description = "Отменена (${lesson.bellNumber} пара)"
                        )
                    )
                }
            }
        }

        return if (items.isNotEmpty()) {
            ScheduleDiff(
                targetId = target.id,
                targetTitle = target.targetTitle,
                items = items.sortedWith(compareBy({ it.date }, { it.bellNumber }))
            )
        } else null
    }

    fun clearCache() {
        storage.clearCache()
        selectedTarget.value?.let { current ->
            scope.launch {
                refreshSchedule(current, silent = false)
            }
        }
    }

    fun resetAllData() {
        storage.resetAllData()
    }
}
