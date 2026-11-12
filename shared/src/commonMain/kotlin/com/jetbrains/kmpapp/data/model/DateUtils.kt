package com.jetbrains.kmpapp.data.model

import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

object DateUtils {

    fun today(): LocalDate {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return now.date
    }

    fun getWeekDates(anchorDate: LocalDate): List<LocalDate> {
        val monday = anchorDate.minus(DatePeriod(days = dayOfWeekIndex(anchorDate)))
        return (0..6).map { monday.plus(DatePeriod(days = it)) }
    }

    private fun dayOfWeekIndex(date: LocalDate): Int = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        DayOfWeek.SATURDAY -> 5
        DayOfWeek.SUNDAY -> 6
    }

    fun getWeekInfo(date: LocalDate): SemesterWeekInfo {
        val monthNum = date.month.ordinal + 1
        val semesterStartDate = if (monthNum in 2..8) {
            LocalDate(date.year, 2, 9)
        } else {
            val startYear = if (monthNum == 1) date.year - 1 else date.year
            LocalDate(startYear, 9, 1)
        }
        // Сайт считает недели с понедельника той недели, в которую попадает начало семестра.
        val semesterStart = semesterStartDate.minus(DatePeriod(days = dayOfWeekIndex(semesterStartDate)))

        val daysBetween = semesterStart.daysUntil(date)
        val weekNumber = if (daysBetween >= 0) (daysBetween / 7) + 1 else 1
        val isEven = weekNumber % 2 == 0

        return SemesterWeekInfo(
            weekNumber = weekNumber.coerceAtLeast(1),
            isEven = isEven
        )
    }

    fun formatDayOfWeekShort(dayOfWeek: DayOfWeek): String {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "Пн"
            DayOfWeek.TUESDAY -> "Вт"
            DayOfWeek.WEDNESDAY -> "Ср"
            DayOfWeek.THURSDAY -> "Чт"
            DayOfWeek.FRIDAY -> "Пт"
            DayOfWeek.SATURDAY -> "Сб"
            DayOfWeek.SUNDAY -> "Вс"
        }
    }

    fun formatMonthRu(month: Month): String {
        return when (month) {
            Month.JANUARY -> "января"
            Month.FEBRUARY -> "февраля"
            Month.MARCH -> "марта"
            Month.APRIL -> "апреля"
            Month.MAY -> "мая"
            Month.JUNE -> "июня"
            Month.JULY -> "июля"
            Month.AUGUST -> "августа"
            Month.SEPTEMBER -> "сентября"
            Month.OCTOBER -> "октября"
            Month.NOVEMBER -> "ноября"
            Month.DECEMBER -> "декабря"
        }
    }

    fun formatMonthTitle(month: Month): String {
        return when (month) {
            Month.JANUARY -> "Январь"
            Month.FEBRUARY -> "Февраль"
            Month.MARCH -> "Март"
            Month.APRIL -> "Апрель"
            Month.MAY -> "Май"
            Month.JUNE -> "Июнь"
            Month.JULY -> "Июль"
            Month.AUGUST -> "Август"
            Month.SEPTEMBER -> "Сентябрь"
            Month.OCTOBER -> "Октябрь"
            Month.NOVEMBER -> "Ноябрь"
            Month.DECEMBER -> "Декабрь"
        }
    }
    fun currentTimeMinutes(): Int {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return now.hour * 60 + now.minute
    }

    fun parseTimeToMinutes(timeStr: String): Int? {
        val parts = timeStr.trim().split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    fun currentSemesterKey(date: LocalDate = today()): String {
        val monthNum = date.month.ordinal + 1
        return if (monthNum in 2..8) {
            "${date.year}-2"
        } else {
            val startYear = if (monthNum == 1) date.year - 1 else date.year
            "$startYear-1"
        }
    }

    /** Календарные границы семестра для ключа вида "YYYY-1"/"YYYY-2". */
    fun semesterBounds(key: String): Pair<LocalDate, LocalDate>? {
        val parts = key.split("-")
        if (parts.size != 2) return null
        val year = parts[0].toIntOrNull() ?: return null
        return when (parts[1]) {
            // Чётный семестр: 01.02 — 30.06
            "2" -> LocalDate(year, 2, 1) to LocalDate(year, 6, 30)
            // Нечётный семестр: 01.09 — 29.12
            "1" -> LocalDate(year, 9, 1) to LocalDate(year, 12, 29)
            else -> null
        }
    }

    fun semesterBoundsForDate(date: LocalDate): Pair<LocalDate, LocalDate> {
        val key = currentSemesterKey(date)
        return semesterBounds(key) ?: (LocalDate(date.year, 1, 1) to LocalDate(date.year, 12, 31))
    }

    fun isDateInSemester(date: LocalDate, key: String): Boolean {
        val bounds = semesterBounds(key) ?: return true
        return date >= bounds.first && date <= bounds.second
    }

    fun isoDayNumber(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        DayOfWeek.SUNDAY -> 7
    }

    /**
     * Calculates lesson progress (0.0f..1.0f) if currently within [startTime, endTime].
     * Returns null if lesson hasn't started or has already ended.
     */
    fun getLessonProgress(startTime: String, endTime: String, currentMinutes: Int = currentTimeMinutes()): Float? {
        val start = parseTimeToMinutes(startTime) ?: return null
        val end = parseTimeToMinutes(endTime) ?: return null
        if (end <= start) return null
        if (currentMinutes in start until end) {
            val total = (end - start).toFloat()
            val passed = (currentMinutes - start).toFloat()
            return (passed / total).coerceIn(0.01f, 1.0f)
        }
        return null
    }

    /**
     * Returns remaining minutes for a lesson if it's currently ongoing, or null otherwise.
     */
    fun getRemainingLessonMinutes(endTime: String, currentMinutes: Int = currentTimeMinutes()): Int? {
        val end = parseTimeToMinutes(endTime) ?: return null
        val remaining = end - currentMinutes
        return if (remaining > 0) remaining else null
    }
}

