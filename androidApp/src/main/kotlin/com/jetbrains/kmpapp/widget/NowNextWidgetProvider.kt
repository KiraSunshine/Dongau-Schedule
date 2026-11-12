package com.jetbrains.kmpapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.jetbrains.kmpapp.MainActivity
import com.jetbrains.kmpapp.R
import com.jetbrains.kmpapp.data.model.AttendanceRule
import com.jetbrains.kmpapp.data.model.AttendanceStatus
import com.jetbrains.kmpapp.data.model.CustomLesson
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.expandForTarget
import com.jetbrains.kmpapp.data.model.statusFor
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private val widgetJson = Json { ignoreUnknownKeys = true; isLenient = true }

class NowNextWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NowNextWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, NowNextWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NowNextWidgetProvider::class.java))
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }
    }
}

private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.widget_now_next)

    val openIntent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    val state = loadWidgetState(context)
    views.setTextViewText(R.id.widget_status, state.status)
    views.setTextViewText(R.id.widget_subject, state.subject)
    views.setTextViewText(R.id.widget_datetime, state.dateTime)
    views.setTextViewText(R.id.widget_room, state.room)

    if (state.showProgress) {
        views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
        views.setProgressBar(R.id.widget_progress, 100, state.progress.coerceIn(0, 100), false)
    } else {
        views.setViewVisibility(R.id.widget_progress, View.GONE)
        views.setProgressBar(R.id.widget_progress, 100, 0, false)
    }

    if (state.extra.isNullOrBlank()) {
        views.setViewVisibility(R.id.widget_extra, View.GONE)
    } else {
        views.setViewVisibility(R.id.widget_extra, View.VISIBLE)
        views.setTextViewText(R.id.widget_extra, state.extra)
    }

    applyWidgetTheme(context, views)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private data class WidgetState(
    val status: String,
    val subject: String,
    val dateTime: String,
    val room: String,
    val showProgress: Boolean,
    val progress: Int,
    val extra: String?
)

private fun loadWidgetState(context: Context): WidgetState {
    return try {
        val prefs = context.getSharedPreferences("dongau_schedule_cache", Context.MODE_PRIVATE)
        val selectedIdStr = prefs.getString("dongau_selected_target_id", null)
        val selectedId = selectedIdStr?.toIntOrNull()
        if (selectedId == null) {
            return WidgetState(
                status = "",
                subject = "Выберите расписание",
                dateTime = "Откройте приложение",
                room = "",
                showProgress = false,
                progress = 0,
                extra = null
            )
        }

        val lessonsJson = prefs.getString("dongau_lessons_$selectedId", null)
        val serverLessons = if (!lessonsJson.isNullOrBlank()) {
            try { widgetJson.decodeFromString(ListSerializer(Lesson.serializer()), lessonsJson) } catch (_: Exception) { emptyList() }
        } else emptyList()

        val customJson = prefs.getString("dongau_custom_lessons", null)
        val customLessons = if (!customJson.isNullOrBlank()) {
            try { widgetJson.decodeFromString(ListSerializer(CustomLesson.serializer()), customJson) } catch (_: Exception) { emptyList() }
        } else emptyList()

        val attendanceJson = prefs.getString("dongau_attendance_rules", null)
        val attendanceRules = if (!attendanceJson.isNullOrBlank()) {
            try { widgetJson.decodeFromString(ListSerializer(AttendanceRule.serializer()), attendanceJson) } catch (_: Exception) { emptyList() }
        } else emptyList()

        val today = DateUtils.today()
        val from = today.plus(DatePeriod(days = -1))
        val lastServerDate = serverLessons.maxOfOrNull { it.date }
        val to = maxOf(today.plus(DatePeriod(days = 30)), lastServerDate ?: today)
        val customExpanded = customLessons.expandForTarget(selectedId, from, to)
        val all = (serverLessons + customExpanded)
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.date }, { it.bellNumber }, { it.startTime }))

        val nowMillis = System.currentTimeMillis()
        val tz = TimeZone.currentSystemDefault()

        fun startEndMillis(lesson: Lesson): Pair<Long, Long>? {
            val s = DateUtils.parseTimeToMinutes(lesson.startTime) ?: return null
            val e = DateUtils.parseTimeToMinutes(lesson.endTime) ?: (s + 90)
            val start = LocalDateTime(lesson.date, LocalTime(s / 60, s % 60))
                .toInstant(tz).toEpochMilliseconds()
            val end = LocalDateTime(lesson.date, LocalTime(e / 60, e % 60))
                .toInstant(tz).toEpochMilliseconds()
            return start to end
        }

        fun isSkipped(lesson: Lesson): Boolean =
            attendanceRules.statusFor(lesson) == AttendanceStatus.SKIPPED

        val current = all.firstOrNull { lesson ->
            if (isSkipped(lesson)) return@firstOrNull false
            val se = startEndMillis(lesson) ?: return@firstOrNull false
            nowMillis in se.first until se.second
        }

        if (current != null) {
            val se = startEndMillis(current)!!
            val total = (se.second - se.first).coerceAtLeast(1L)
            val progress = (((nowMillis - se.first).toDouble() / total.toDouble()) * 100.0)
                .roundToInt().coerceIn(0, 100)
            return WidgetState(
                status = formatDate(current.date),
                subject = current.subject.ifBlank { "Пара" },
                dateTime = formatTimeRange(current),
                room = formatRoom(current),
                showProgress = true,
                progress = progress,
                extra = null
            )
        }

        val next = all.firstOrNull { lesson ->
            if (isSkipped(lesson)) return@firstOrNull false
            val se = startEndMillis(lesson) ?: return@firstOrNull false
            se.first > nowMillis
        }

        if (next != null) {
            val startsIn = formatStartsIn(next, nowMillis, tz)
            return WidgetState(
                status = formatDate(next.date),
                subject = next.subject.ifBlank { "Пара" },
                dateTime = formatTimeRange(next),
                room = formatRoom(next),
                showProgress = false,
                progress = 0,
                extra = startsIn
            )
        }

        WidgetState(
            status = "",
            subject = "Нет занятий",
            dateTime = "",
            room = "",
            showProgress = false,
            progress = 0,
            extra = null
        )
    } catch (_: Exception) {
        WidgetState(
            status = "",
            subject = "Нет данных",
            dateTime = "Откройте приложение",
            room = "",
            showProgress = false,
            progress = 0,
            extra = null
        )
    }
}

private fun formatTimeRange(lesson: Lesson): String =
    "${lesson.startTime}–${lesson.endTime}"

private fun formatRoom(lesson: Lesson): String {
    val room = lesson.classrooms.joinToString(", ").trim()
    return if (room.isNotBlank()) "Аудитория: $room" else ""
}

private fun formatDate(date: LocalDate): String {
    val dayName = DateUtils.formatDayOfWeekShort(date.dayOfWeek)
    val monthNum = date.month.ordinal + 1
    val day = date.day.toString().padStart(2, '0')
    val month = monthNum.toString().padStart(2, '0')
    return "$dayName $day.$month"
}

private fun formatStartsIn(lesson: Lesson, nowMillis: Long, tz: TimeZone): String? {
    val s = DateUtils.parseTimeToMinutes(lesson.startTime) ?: return null
    val start = LocalDateTime(lesson.date, LocalTime(s / 60, s % 60))
        .toInstant(tz).toEpochMilliseconds()
    val diffMin = ((start - nowMillis) / 60_000L).toInt()
    if (diffMin <= 0) return null
    val hours = diffMin / 60
    val mins = diffMin % 60
    val rel = when {
        hours <= 0 -> "$mins мин"
        mins == 0 -> "$hours ч"
        else -> "$hours ч $mins мин"
    }
    return "До пары $rel"
}

private data class WidgetPalette(
    val bgRes: Int,
    val accent: Int,
    val primaryText: Int,
    val secondaryText: Int
)

private fun applyWidgetTheme(context: Context, views: RemoteViews) {
    val prefs = context.getSharedPreferences("dongau_schedule_cache", Context.MODE_PRIVATE)
    val themeMode = prefs.getString("dongau_app_theme", "SYSTEM") ?: "SYSTEM"
    val overlay = prefs.getString("dongau_theme_overlay", "NONE") ?: "NONE"
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val dark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> night
    }

    val palette = when (overlay) {
        "SAKURA" -> if (dark) WidgetPalette(
            bgRes = R.drawable.widget_bg_sakura_dark,
            accent = Color.parseColor("#FFB1C8"),
            primaryText = Color.parseColor("#FFF7FA"),
            secondaryText = Color.parseColor("#E8C9D6")
        ) else WidgetPalette(
            bgRes = R.drawable.widget_bg_sakura,
            accent = Color.parseColor("#B84D73"),
            primaryText = Color.parseColor("#3E252E"),
            secondaryText = Color.parseColor("#6D4A58")
        )
        "CYBERPUNK" -> if (dark) WidgetPalette(
            bgRes = R.drawable.widget_bg_cyber_dark,
            accent = Color.parseColor("#00E5FF"),
            primaryText = Color.parseColor("#F4F7FF"),
            secondaryText = Color.parseColor("#AAB4E6")
        ) else WidgetPalette(
            bgRes = R.drawable.widget_bg_cyber,
            accent = Color.parseColor("#006D7A"),
            primaryText = Color.parseColor("#141A33"),
            secondaryText = Color.parseColor("#46507A")
        )
        "MATRIX" -> if (dark) WidgetPalette(
            bgRes = R.drawable.widget_bg_matrix_dark,
            accent = Color.parseColor("#00FF66"),
            primaryText = Color.parseColor("#D8FFE1"),
            secondaryText = Color.parseColor("#8BCF97")
        ) else WidgetPalette(
            bgRes = R.drawable.widget_bg_matrix,
            accent = Color.parseColor("#007A33"),
            primaryText = Color.parseColor("#102516"),
            secondaryText = Color.parseColor("#2F5E39")
        )
        else -> if (dark) WidgetPalette(
            bgRes = R.drawable.widget_bg_dark,
            accent = Color.parseColor("#D0BCFF"),
            primaryText = Color.parseColor("#E6E0E9"),
            secondaryText = Color.parseColor("#CAC4D0")
        ) else WidgetPalette(
            bgRes = R.drawable.widget_bg,
            accent = Color.parseColor("#6750A4"),
            primaryText = Color.parseColor("#1D1B20"),
            secondaryText = Color.parseColor("#49454F")
        )
    }

    views.setInt(R.id.widget_root, "setBackgroundResource", palette.bgRes)
    views.setTextColor(R.id.widget_status, palette.accent)
    views.setTextColor(R.id.widget_subject, palette.primaryText)
    views.setTextColor(R.id.widget_datetime, palette.secondaryText)
    views.setTextColor(R.id.widget_room, palette.secondaryText)
    views.setTextColor(R.id.widget_extra, palette.accent)
}
