package com.jetbrains.kmpapp.screens.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.ExtraLesson
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.defaultBells
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class LessonTemplate(
    val subject: String,
    val lessonType: LessonType,
    val teacher: String,
    val classroom: String,
    val group: String,
    val bellNumber: Int,
    val startTime: String,
    val endTime: String
)

fun buildLessonTemplates(lessons: List<Lesson>): List<LessonTemplate> {
    return lessons
        .asSequence()
        .map { lesson ->
            LessonTemplate(
                subject = lesson.subject.trim(),
                lessonType = lesson.lessonType,
                teacher = lesson.teachers.joinToString(", "),
                classroom = lesson.classrooms.joinToString(", "),
                group = lesson.groups.joinToString(", "),
                bellNumber = lesson.bellNumber,
                startTime = lesson.startTime,
                endTime = lesson.endTime
            )
        }
        .filter { it.subject.isNotBlank() }
        .distinctBy {
            listOf(
                it.subject.lowercase(),
                it.lessonType.name,
                it.teacher.lowercase(),
                it.classroom.lowercase(),
                it.bellNumber.toString(),
                it.startTime,
                it.endTime
            ).joinToString("|")
        }
        .sortedBy { it.subject.lowercase() }
        .toList()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomLessonBottomSheet(
    targetId: Int,
    initialDate: LocalDate,
    templates: List<LessonTemplate> = emptyList(),
    editingLesson: com.jetbrains.kmpapp.data.model.CustomLesson? = null,
    editingExtraLesson: ExtraLesson? = null,
    hasLessonsBeforeToday: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (
        subject: String,
        lessonType: LessonType,
        teacher: String,
        classroom: String,
        group: String,
        bells: List<Pair<Int, Pair<String, String>>>,
        isRecurring: Boolean,
        date: LocalDate?,
        dayOfWeek: Int?,
        intervalWeeks: Int,
        keepEverySemester: Boolean,
        allowOutsideSemester: Boolean,
        saveAsExtra: Boolean
    ) -> Unit
) {
    val isEditMode = editingLesson != null || editingExtraLesson != null
    val editingAnyId = editingLesson?.id ?: editingExtraLesson?.id
    val initialEditDate = editingLesson?.date ?: editingLesson?.anchorDate ?: editingExtraLesson?.date ?: editingExtraLesson?.anchorDate ?: initialDate
    val initialBell = (editingLesson?.bellNumber ?: editingExtraLesson?.bellNumber ?: 1).coerceIn(1, 7)
    var subject by remember(editingAnyId) { mutableStateOf(editingLesson?.subject ?: editingExtraLesson?.subject ?: "") }
    var lessonType by remember(editingAnyId) { mutableStateOf(editingLesson?.lessonType ?: editingExtraLesson?.lessonType ?: LessonType.OTHER) }
    var teacher by remember(editingAnyId) { mutableStateOf((editingLesson?.teachers ?: editingExtraLesson?.teachers ?: emptyList()).joinToString(", ")) }
    var classroom by remember(editingAnyId) { mutableStateOf((editingLesson?.classrooms ?: editingExtraLesson?.classrooms ?: emptyList()).joinToString(", ")) }
    var group by remember(editingAnyId) { mutableStateOf((editingLesson?.groups ?: editingExtraLesson?.groups ?: emptyList()).joinToString(", ")) }
    var showSuggestions by remember(editingAnyId) { mutableStateOf(false) }
    var selectedBells by remember(editingAnyId) { mutableStateOf(setOf(initialBell)) }
    var startTime by remember(editingAnyId) {
        mutableStateOf(
            editingLesson?.startTime ?: editingExtraLesson?.startTime
                ?: defaultBells.getOrElse(initialBell - 1) { com.jetbrains.kmpapp.data.model.LessonBells(1, "08:30", "10:05") }.startTime
        )
    }
    var endTime by remember(editingAnyId) {
        mutableStateOf(
            editingLesson?.endTime ?: editingExtraLesson?.endTime
                ?: defaultBells.getOrElse(initialBell - 1) { com.jetbrains.kmpapp.data.model.LessonBells(1, "08:30", "10:05") }.endTime
        )
    }
    val initialRecurring = editingLesson?.isRecurring ?: editingExtraLesson?.isRecurring ?: false
    var isRecurring by remember(editingAnyId) { mutableStateOf(initialRecurring) }
    var date by remember(editingAnyId) { mutableStateOf(initialEditDate) }
    var dayOfWeek by remember(editingAnyId) {
        mutableIntStateOf(
            editingLesson?.dayOfWeek ?: editingExtraLesson?.dayOfWeek ?: DateUtils.isoDayNumber(initialEditDate.dayOfWeek)
        )
    }
    var intervalWeeks by remember(editingAnyId) { mutableIntStateOf((editingLesson?.intervalWeeks ?: editingExtraLesson?.intervalWeeks ?: 1).coerceAtLeast(1)) }
    var keepEverySemester by remember(editingAnyId) { mutableStateOf(editingLesson?.keepEverySemester ?: false) }

    fun semesterStartFor(d: LocalDate): LocalDate =
        DateUtils.semesterBounds(DateUtils.currentSemesterKey(d))?.first ?: d

    fun firstMatchingDayOnOrAfter(start: LocalDate, dow: Int): LocalDate {
        var d = start
        while (DateUtils.isoDayNumber(d.dayOfWeek) != dow) {
            d = d.plus(DatePeriod(days = 1))
        }
        return d
    }

    val initialStartFromSemester = remember(editingAnyId) {
        val lesson = editingLesson
        if (lesson != null && lesson.isRecurring) {
            val anchor = lesson.anchorDate ?: lesson.date
            if (anchor != null) {
                val semStart = semesterStartFor(anchor)
                val expected = firstMatchingDayOnOrAfter(semStart, lesson.dayOfWeek ?: DateUtils.isoDayNumber(anchor.dayOfWeek))
                anchor == expected
            } else false
        } else false
    }
    var startFromSemester by remember(editingAnyId) { mutableStateOf(initialStartFromSemester) }
    var showOutsideSemesterConfirm by remember(editingAnyId) { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.98f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isEditMode) "Редактировать пару" else "Добавить пару",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = subject,
                onValueChange = {
                    subject = it
                    showSuggestions = true
                },
                label = { Text("Название предмета") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            val suggestions = remember(subject, templates, isEditMode) {
                if (isEditMode) emptyList()
                else {
                    val q = subject.trim().lowercase()
                    if (q.isBlank()) emptyList()
                    else templates.filter { it.subject.lowercase().contains(q) }.take(6)
                }
            }
            if (!isEditMode && showSuggestions && suggestions.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        suggestions.forEach { t ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        subject = t.subject
                                        lessonType = t.lessonType
                                        teacher = t.teacher
                                        classroom = t.classroom
                                        group = t.group
                                        val b = t.bellNumber.coerceIn(1, 7)
                                        selectedBells = setOf(b)
                                        startTime = t.startTime
                                        endTime = t.endTime
                                        showSuggestions = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(t.subject, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                val details = listOfNotNull(
                                    t.lessonType.displayName,
                                    if (t.bellNumber > 0) "${t.bellNumber} пара" else null,
                                    if (t.startTime.isNotBlank() && t.endTime.isNotBlank()) "${t.startTime}–${t.endTime}" else null,
                                    t.classroom.takeIf { it.isNotBlank() },
                                    t.teacher.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                if (details.isNotBlank()) {
                                    Text(
                                        details,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text("Тип занятия", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    LessonType.LECTURE to "Лекция",
                    LessonType.PRACTICE to "Практика",
                    LessonType.LAB to "Лаба",
                    LessonType.OTHER to "Другое"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = lessonType == type,
                        onClick = { lessonType = type },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = teacher,
                onValueChange = { teacher = it },
                label = { Text("Преподаватель (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = classroom,
                onValueChange = { classroom = it },
                label = { Text("Аудитория") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = group,
                onValueChange = { group = it },
                label = { Text("Группа (если нужно)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Пара и время", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (!isEditMode) {
                Text(
                    text = "Можно выбрать несколько пар (например 2 пары в один день)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Измените номер пары и время для этой пары",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..7).forEach { n ->
                    val selected = selectedBells.contains(n)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (isEditMode) {
                                selectedBells = setOf(n)
                                val bell = defaultBells.getOrNull(n - 1)
                                if (bell != null) {
                                    startTime = bell.startTime
                                    endTime = bell.endTime
                                }
                            } else {
                                val next = selectedBells.toMutableSet()
                                if (selected) {
                                    if (next.size > 1) next.remove(n)
                                } else {
                                    next.add(n)
                                }
                                selectedBells = next
                                val first = next.minOrNull() ?: n
                                val bell = defaultBells.getOrNull(first - 1)
                                if (bell != null && next.size == 1) {
                                    startTime = bell.startTime
                                    endTime = bell.endTime
                                }
                            }
                        },
                        label = { Text("$n", fontSize = 12.sp) }
                    )
                }
            }
            val singleBell = selectedBells.size == 1
            if (singleBell) {
                val startValid = DateUtils.parseTimeToMinutes(startTime) != null
                val endValid = DateUtils.parseTimeToMinutes(endTime) != null
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Начало") },
                        isError = startTime.isNotBlank() && !startValid,
                        supportingText = if (startTime.isNotBlank() && !startValid) {
                            { Text("Формат ЧЧ:ММ") }
                        } else null,
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("Конец") },
                        isError = endTime.isNotBlank() && !endValid,
                        supportingText = if (endTime.isNotBlank() && !endValid) {
                            { Text("Формат ЧЧ:ММ") }
                        } else null,
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            } else {
                val summary = selectedBells.sorted().joinToString(", ") { n ->
                    val b = defaultBells.getOrNull(n - 1)
                    if (b != null) "$n (${b.startTime}–${b.endTime})" else "$n"
                }
                Text(
                    text = "Выбрано: $summary",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Повторяющаяся пара", fontWeight = FontWeight.SemiBold)
            }

            if (!isRecurring) {
                Text("...", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { date = date.plus(DatePeriod(days = -1)) }) { Text("-1") }
                    Text(
                        text = date.toString(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(onClick = { date = date.plus(DatePeriod(days = 1)) }) { Text("+1") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { date = date.plus(DatePeriod(days = -7)) }, modifier = Modifier.weight(1f)) { Text("-7 д") }
                    TextButton(onClick = { date = date.plus(DatePeriod(days = 7)) }, modifier = Modifier.weight(1f)) { Text("+7 д") }
                }
                Text(
                    text = "Можно выбрать любую дату, даже вне семестра.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Начало", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = startFromSemester,
                        onClick = { startFromSemester = true },
                        label = { Text("С начала семестра", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = !startFromSemester,
                        onClick = { startFromSemester = false },
                        label = { Text("С текущего дня", fontSize = 12.sp) }
                    )
                }
                val effectiveAnchor = if (startFromSemester) {
                    firstMatchingDayOnOrAfter(semesterStartFor(initialDate), dayOfWeek)
                } else {
                    date
                }
                Text(
                    text = "Начнётся: $effectiveAnchor",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("День недели", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DayOfWeek.entries.forEach { dow ->
                        val dowNum = DateUtils.isoDayNumber(dow)
                        FilterChip(
                            selected = dayOfWeek == dowNum,
                            onClick = {
                                dayOfWeek = dowNum
                                if (!startFromSemester) {
                                    // при "с текущего дня" держим дату согласованной с выбранным днём недели
                                    // только если дата редактируется логически; здесь не меняем date автоматически
                                }
                            },
                            label = { Text(DateUtils.formatDayOfWeekShort(dow), fontSize = 12.sp) }
                        )
                    }
                }
                Text("Периодичность", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..4).forEach { n ->
                        FilterChip(
                            selected = intervalWeeks == n,
                            onClick = { intervalWeeks = n },
                            label = {
                                Text(
                                    when (n) {
                                        1 -> "1 нед."
                                        2 -> "2 нед."
                                        3 -> "3 нед."
                                        4 -> "4 нед."
                                        else -> "$n"
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = keepEverySemester, onCheckedChange = { keepEverySemester = it })
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Оставлять каждый семестр", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Если не отмечено, пара удалится при смене семестра",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val bellsToAdd = selectedBells.sorted().mapNotNull { n ->
                val bell = defaultBells.getOrNull(n - 1)
                val s = if (selectedBells.size == 1) startTime else (bell?.startTime ?: startTime)
                val e = if (selectedBells.size == 1) endTime else (bell?.endTime ?: endTime)
                if (DateUtils.parseTimeToMinutes(s) == null || DateUtils.parseTimeToMinutes(e) == null) null
                else n to (s to e)
            }
            val today = DateUtils.today()
            val effectiveDateForValidation = if (isRecurring && startFromSemester) {
                firstMatchingDayOnOrAfter(semesterStartFor(initialDate), dayOfWeek)
            } else {
                date
            }
            fun semesterOrder(key: String): Int {
                val parts = key.split("-")
                val y = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val sem = parts.getOrNull(1)?.toIntOrNull() ?: 0
                // 2 (весна) раньше 1 (осень) в том же году: ...-2, ...-1, (year+1)-2, ...
                return y * 2 + if (sem == 2) 0 else 1
            }
            val currentSemKey = DateUtils.currentSemesterKey(today)
            val effectiveSemKey = DateUtils.currentSemesterKey(effectiveDateForValidation)
            val currentSemStart = DateUtils.semesterBounds(currentSemKey)?.first
            val isOlderSemesterThanCurrent = when {
                // Если дата не раньше начала текущего семестра — это не "более давний семестр"
                currentSemStart != null && effectiveDateForValidation >= currentSemStart -> false
                else -> semesterOrder(effectiveSemKey) < semesterOrder(currentSemKey)
            }
            val addingBeforeToday = effectiveDateForValidation < today
            // Раньше сегодняшнего дня можно добавлять только если:
            // 1) до сегодня нет ни одной пары, и 2) это не более давний семестр, чем текущий.
            val addingBeforeTodayNotAllowed = addingBeforeToday && (hasLessonsBeforeToday || isOlderSemesterThanCurrent)
            val canAdd = subject.isNotBlank() && selectedBells.isNotEmpty() && bellsToAdd.size == selectedBells.size && !addingBeforeTodayNotAllowed
            if (!canAdd) {
                val missing = buildList {
                    if (subject.isBlank()) add("название предмета")
                    if (selectedBells.isEmpty()) add("номер пары")
                    if (bellsToAdd.size != selectedBells.size) add("корректное время (ЧЧ:ММ)")
                    if (addingBeforeTodayNotAllowed) {
                        if (isOlderSemesterThanCurrent) {
                            add("нельзя добавлять пары в семестр старше текущего")
                        } else {
                            add("нельзя ставить пары раньше сегодняшнего дня, если до сегодня уже есть пары")
                        }
                    }
                }
                Text(
                    text = "Заполните/исправьте: ${missing.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    if (canAdd) {
                        val effectiveDate = if (isRecurring && startFromSemester) {
                            firstMatchingDayOnOrAfter(semesterStartFor(initialDate), dayOfWeek)
                        } else {
                            date
                        }
                        val bounds = DateUtils.semesterBounds(DateUtils.currentSemesterKey(effectiveDate))
                        val outside = bounds != null && (effectiveDate < bounds.first || effectiveDate > bounds.second)
                        val doAdd = {
                            onAdd(
                                subject, lessonType, teacher, classroom, group,
                                bellsToAdd, isRecurring,
                                effectiveDate,
                                if (isRecurring) dayOfWeek else null,
                                intervalWeeks, keepEverySemester,
                                outside,
                                outside
                            )
                        }
                        if (outside) {
                            showOutsideSemesterConfirm = true
                        } else {
                            doAdd()
                        }
                    }
                },
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                val count = bellsToAdd.size
                Text(
                    when {
                        isEditMode -> "Сохранить"
                        count > 1 -> "Добавить ($count пары)"
                        else -> "Добавить"
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showOutsideSemesterConfirm) {
        AlertDialog(
            onDismissRequest = { showOutsideSemesterConfirm = false },
            title = { Text("Внеурочное время") },
            text = { Text("Вы точно хотите добавить пару во внеурочное время (вне границ семестра)?") },
            confirmButton = {
                TextButton(onClick = {
                    val effectiveDate = if (isRecurring && startFromSemester) {
                        firstMatchingDayOnOrAfter(semesterStartFor(initialDate), dayOfWeek)
                    } else {
                        date
                    }
                    val bounds = DateUtils.semesterBounds(DateUtils.currentSemesterKey(effectiveDate))
                    val outside = bounds != null && (effectiveDate < bounds.first || effectiveDate > bounds.second)
                    onAdd(
                        subject, lessonType, teacher, classroom, group,
                        selectedBells.sorted().mapNotNull { n ->
                            val bell = defaultBells.getOrNull(n - 1)
                            val st = if (selectedBells.size == 1) startTime else (bell?.startTime ?: startTime)
                            val en = if (selectedBells.size == 1) endTime else (bell?.endTime ?: endTime)
                            if (DateUtils.parseTimeToMinutes(st) == null || DateUtils.parseTimeToMinutes(en) == null) null
                            else n to (st to en)
                        },
                        isRecurring,
                        effectiveDate,
                        if (isRecurring) dayOfWeek else null,
                        intervalWeeks, keepEverySemester,
                        outside,
                        outside
                    )
                    showOutsideSemesterConfirm = false
                }) { Text("Да, добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showOutsideSemesterConfirm = false }) { Text("Отмена") }
            }
        )
    }
}
